package los.program;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import los.BatchProgram;
import los.Dd;
import los.record.Copybooks;
import los.record.FixedRecord;
import los.record.Layout;
import los.record.LineMode;
import los.record.RecordFormatException;
import los.record.RecordReader;
import los.record.RecordWriter;

/**
 * STEP040 GL extract: reads RUNDATE, DECISION, writes GLOUT. Every auto-approved loan posts
 * a funding DR/CR pair and, when a lock extension fee was charged, a fee DR/CR pair.
 * Control totals that do not balance end the step with RC 12.
 */
public final class LNGL040 implements BatchProgram {
    public static final int RC_OUT_OF_BALANCE = 12;

    static final Layout RUNDATE_REC = Layout.builder("RUNDATE-REC")
            .field("RD-DATE", "9(8)")
            .filler("X(72)")
            .build(80);

    static final long FUNDING_DR_ACCOUNT = 140100000000L;
    static final long FUNDING_CR_ACCOUNT = 210500000000L;
    static final long FEE_DR_ACCOUNT = 120300000000L;
    static final long FEE_CR_ACCOUNT = 410200000000L;
    static final long LOAN_NUMBER_BASE = 40000000000L;
    static final long LOAN_NUMBER_MODULUS = 10_000_000_000_000L;
    static final long FHA_COST_CENTER = 10020;
    static final long OTHER_COST_CENTER = 10010;
    static final long LINES_MODULUS = 10_000_000L;
    static final BigInteger TOTAL_MODULUS = BigInteger.TEN.pow(15);

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
        long runDate = readRunDate(dd);
        Totals totals = new Totals();
        try (RecordReader in = RecordReader.open(dd.path("DECISION"), Copybooks.LNDECREC);
                RecordWriter out = RecordWriter.open(dd.path("GLOUT"), LineMode.FIXED)) {
            FixedRecord gl = FixedRecord.blank(Copybooks.LNGLREC);
            for (FixedRecord dec = in.read(); dec != null; dec = in.read()) {
                if (dec.getString("DEC-CODE").equals("A")) {
                    postLoan(dec, runDate, gl, out, totals);
                }
            }
        }
        sysout.println("LNGL040  LINES    " + String.format("%07d", totals.lines));
        sysout.println("LNGL040  TOTAL DR " + edit(totals.dr));
        sysout.println("LNGL040  TOTAL CR " + edit(totals.cr));
        if (totals.dr.compareTo(totals.cr) != 0) {
            sysout.println("LNGL040  OUT OF BALANCE");
            return RC_OUT_OF_BALANCE;
        }
        return 0;
    }

    private static long readRunDate(Dd dd) throws IOException {
        try (RecordReader in = RecordReader.open(dd.path("RUNDATE"), RUNDATE_REC)) {
            FixedRecord rec = in.read();
            if (rec == null) {
                throw new RecordFormatException("RUNDATE is empty");
            }
            return rec.getLong("RD-DATE");
        }
    }

    private static void postLoan(FixedRecord dec, long runDate, FixedRecord gl, RecordWriter out,
            Totals totals) throws IOException {
        long loanNumber = loanNumber(dec.getLong("DEC-APP-ID"));
        long costCenter = costCenter(dec.getString("DEC-LOAN-TYPE"));
        gl.set("GL-ACCOUNT", FUNDING_DR_ACCOUNT);
        gl.set("GL-DRCR", "DR");
        gl.set("GL-AMOUNT", dec.getDecimal("DEC-LOAN-AMT"));
        gl.set("GL-DESC", "LOAN FUNDING " + dec.getString("DEC-PRODUCT"));
        writeLine(gl, runDate, loanNumber, costCenter, out, totals);
        gl.set("GL-ACCOUNT", FUNDING_CR_ACCOUNT);
        gl.set("GL-DRCR", "CR");
        writeLine(gl, runDate, loanNumber, costCenter, out, totals);
        BigDecimal fee = dec.getDecimal("DEC-EXT-FEE-AMT");
        if (fee.signum() > 0) {
            gl.set("GL-ACCOUNT", FEE_DR_ACCOUNT);
            gl.set("GL-DRCR", "DR");
            gl.set("GL-AMOUNT", fee);
            gl.set("GL-DESC", "RATE LOCK EXTENSION FEE");
            writeLine(gl, runDate, loanNumber, costCenter, out, totals);
            gl.set("GL-ACCOUNT", FEE_CR_ACCOUNT);
            gl.set("GL-DRCR", "CR");
            writeLine(gl, runDate, loanNumber, costCenter, out, totals);
        }
    }

    private static void writeLine(FixedRecord gl, long runDate, long loanNumber, long costCenter,
            RecordWriter out, Totals totals) throws IOException {
        gl.set("GL-POST-DATE", runDate);
        gl.set("GL-LOAN-NUMBER", loanNumber);
        gl.set("GL-COST-CENTER", costCenter);
        BigDecimal amount = gl.getDecimal("GL-AMOUNT");
        if (gl.getString("GL-DRCR").equals("DR")) {
            totals.dr = addTotal(totals.dr, amount);
        } else {
            totals.cr = addTotal(totals.cr, amount);
        }
        totals.lines = (totals.lines + 1) % LINES_MODULUS;
        out.write(gl);
    }

    /** {@code COMPUTE WS-LOAN-NUMBER = 040000000000 + DEC-APP-ID} into 9(13): "004" + APP-ID. */
    static long loanNumber(long appId) {
        return (LOAN_NUMBER_BASE + appId) % LOAN_NUMBER_MODULUS;
    }

    static long costCenter(String loanType) {
        return loanType.equals("FHA") ? FHA_COST_CENTER : OTHER_COST_CENTER;
    }

    /** {@code ADD amount TO total} into 9(13)V99 without ON SIZE ERROR: high-order digits are lost. */
    static BigDecimal addTotal(BigDecimal total, BigDecimal amount) {
        BigInteger cents = total.add(amount).setScale(2).unscaledValue().mod(TOTAL_MODULUS);
        return new BigDecimal(cents, 2);
    }

    /** A 9(13)V99 total edited through {@code Z(12)9.99}: 16 characters, leading zeros as spaces. */
    static String edit(BigDecimal total) {
        BigInteger[] parts = total.setScale(2).unscaledValue().divideAndRemainder(BigInteger.valueOf(100));
        return String.format("%13d.%02d", parts[0], parts[1]);
    }

    private static final class Totals {
        long lines;
        BigDecimal dr = new BigDecimal("0.00");
        BigDecimal cr = new BigDecimal("0.00");
    }
}
