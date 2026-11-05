package los.program;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import los.BatchProgram;
import los.Dd;
import los.Main;
import los.record.Copybooks;
import los.record.FixedRecord;
import los.record.Layout;
import los.record.LineMode;
import los.record.RecordFormatException;
import los.record.RecordReader;
import los.record.RecordWriter;

/**
 * STEP040 GL extract: reads RUNDATE, DECISION, writes GLOUT. For each auto-approved loan a
 * funding DR/CR pair and, when a lock extension fee was charged, a fee DR/CR pair; the step
 * ends RC 12 if the control totals do not balance.
 */
public final class LNGL040 implements BatchProgram {

    /** RUNDATE-REC, declared inline in LNGL040:19-22. */
    static final Layout RUNDATE_REC = Layout.builder("RUNDATE-REC")
            .field("RD-DATE", "9(8)")
            .filler("X(72)")
            .build(80);

    static final long LOAN_NUMBER_BASE = 40_000_000_000L;
    static final long FUNDING_DR_ACCOUNT = 140_100_000_000L;
    static final long FUNDING_CR_ACCOUNT = 210_500_000_000L;
    static final long FEE_DR_ACCOUNT = 120_300_000_000L;
    static final long FEE_CR_ACCOUNT = 410_200_000_000L;
    static final int FHA_COST_CENTER = 10020;
    static final int DEFAULT_COST_CENTER = 10010;
    static final String FEE_DESC = "RATE LOCK EXTENSION FEE";

    private static final BigInteger LINES_MODULUS = BigInteger.TEN.pow(7);
    private static final BigDecimal TOTAL_MODULUS = BigDecimal.TEN.pow(13);

    @Override
    public String name() {
        return "LNGL040";
    }

    @Override
    public List<String> inputs() {
        return List.of("RUNDATE", "DECISION");
    }

    @Override
    public List<String> outputs() {
        return List.of("GLOUT");
    }

    @Override
    public int run(Dd dd, PrintStream sysout) throws IOException {
        long runDate = readRunDate(dd.path("RUNDATE"));
        Totals totals = new Totals();
        try (RecordReader in = RecordReader.open(dd.path("DECISION"), Copybooks.LNDECREC);
                RecordWriter out = RecordWriter.open(dd.path("GLOUT"), LineMode.FIXED)) {
            for (FixedRecord dec = in.read(); dec != null; dec = in.read()) {
                for (FixedRecord gl : postingLines(dec, runDate)) {
                    totals.add(gl);
                    out.write(gl);
                }
            }
        }
        for (String line : totals.sysoutLines()) {
            sysout.print(line + "\n");
        }
        sysout.flush();
        return totals.balanced() ? 0 : Main.RC_FAILED;
    }

    static long readRunDate(Path path) throws IOException {
        try (RecordReader in = RecordReader.open(path, RUNDATE_REC)) {
            FixedRecord rec = in.read();
            if (rec == null) {
                throw new RecordFormatException("RUNDATE " + path + " is empty");
            }
            return rec.getLong("RD-DATE");
        }
    }

    /** 2000-POST-LOAN: the GL lines one DECISION record posts, in write order; none unless DEC-CODE is A. */
    static List<FixedRecord> postingLines(FixedRecord dec, long runDate) {
        List<FixedRecord> lines = new ArrayList<>(4);
        if (!"A".equals(dec.getString("DEC-CODE"))) {
            return lines;
        }
        long loanNumber = loanNumber(dec.getLong("DEC-APP-ID"));
        int costCenter = costCenter(dec.getString("DEC-LOAN-TYPE"));
        BigDecimal loanAmt = dec.getDecimal("DEC-LOAN-AMT");
        String fundingDesc = "LOAN FUNDING " + dec.getString("DEC-PRODUCT");
        lines.add(line(runDate, FUNDING_DR_ACCOUNT, "DR", loanAmt, loanNumber, fundingDesc, costCenter));
        lines.add(line(runDate, FUNDING_CR_ACCOUNT, "CR", loanAmt, loanNumber, fundingDesc, costCenter));
        BigDecimal fee = dec.getDecimal("DEC-EXT-FEE-AMT");
        if (fee.signum() > 0) {
            lines.add(line(runDate, FEE_DR_ACCOUNT, "DR", fee, loanNumber, FEE_DESC, costCenter));
            lines.add(line(runDate, FEE_CR_ACCOUNT, "CR", fee, loanNumber, FEE_DESC, costCenter));
        }
        return lines;
    }

    /** COMPUTE WS-LOAN-NUMBER = 040000000000 + DEC-APP-ID into 9(13): 004 followed by the 10-digit app id. */
    static long loanNumber(long appId) {
        return LOAN_NUMBER_BASE + appId;
    }

    static int costCenter(String loanType) {
        return "FHA".equals(loanType) ? FHA_COST_CENTER : DEFAULT_COST_CENTER;
    }

    private static FixedRecord line(long runDate, long account, String drcr, BigDecimal amount,
            long loanNumber, String desc, int costCenter) {
        return FixedRecord.blank(Copybooks.LNGLREC)
                .set("GL-POST-DATE", runDate)
                .set("GL-ACCOUNT", account)
                .set("GL-DRCR", drcr)
                .set("GL-AMOUNT", amount)
                .set("GL-LOAN-NUMBER", loanNumber)
                .set("GL-DESC", desc)
                .set("GL-COST-CENTER", costCenter);
    }

    /** {@code PIC Z(12)9.99}: 16 characters, leading zeros as spaces except the units digit. */
    static String editTotal(BigDecimal total) {
        String plain = total.setScale(2, RoundingMode.DOWN).remainder(TOTAL_MODULUS).abs().toPlainString();
        return " ".repeat(16 - plain.length()) + plain;
    }

    /**
     * WS-LINES 9(7) and WS-TOTAL-DR/CR 9(13)V99. ADD has no ON SIZE ERROR, so an overflow keeps
     * the low-order digits, as GnuCOBOL does.
     */
    static final class Totals {
        private long lines;
        private BigDecimal debit = BigDecimal.ZERO.setScale(2);
        private BigDecimal credit = BigDecimal.ZERO.setScale(2);

        /** 3000-WRITE-LINE: GL-DRCR = DR adds to the debit total, anything else to the credit total. */
        void add(FixedRecord gl) {
            BigDecimal amount = gl.getDecimal("GL-AMOUNT");
            if ("DR".equals(gl.getString("GL-DRCR"))) {
                debit = debit.add(amount).remainder(TOTAL_MODULUS);
            } else {
                credit = credit.add(amount).remainder(TOTAL_MODULUS);
            }
            lines = BigInteger.valueOf(lines + 1).mod(LINES_MODULUS).longValueExact();
        }

        long lines() {
            return lines;
        }

        BigDecimal debit() {
            return debit;
        }

        BigDecimal credit() {
            return credit;
        }

        boolean balanced() {
            return debit.compareTo(credit) == 0;
        }

        /** The DISPLAY lines of LNGL040:62-67. */
        List<String> sysoutLines() {
            List<String> out = new ArrayList<>(4);
            out.add(String.format(Locale.ROOT, "LNGL040  LINES    %07d", lines));
            out.add("LNGL040  TOTAL DR " + editTotal(debit));
            out.add("LNGL040  TOTAL CR " + editTotal(credit));
            if (!balanced()) {
                out.add("LNGL040  OUT OF BALANCE");
            }
            return out;
        }
    }
}
