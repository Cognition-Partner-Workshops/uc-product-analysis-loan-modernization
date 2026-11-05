package los.program;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import los.BatchProgram;
import los.Dd;
import los.record.Copybooks;
import los.record.FixedRecord;
import los.record.LineMode;
import los.record.RecordReader;
import los.record.RecordWriter;

/** STEP030 core banking account feed: reads RUNDATE, PRICED, DECISION, writes FISOUT. */
public final class LNFIS030 implements BatchProgram {
    private static final long LOAN_NUMBER_BASE = 40_000_000_000L;
    private static final long WRITTEN_MODULUS = 10_000_000L;
    private static final int RUN_DATE_LENGTH = 8;

    @Override
    public String name() {
        return "LNFIS030";
    }

    @Override
    public List<String> inputs() {
        return List.of("RUNDATE", "PRICED", "DECISION");
    }

    @Override
    public List<String> outputs() {
        return List.of("FISOUT");
    }

    @Override
    public int run(Dd dd, PrintStream sysout) throws IOException {
        long runDate = readRunDate(dd);
        long fundDate = fundingDate(runDate);
        long pmtDate = firstPaymentDate(fundDate);
        long written = 0;
        try (RecordReader priced = RecordReader.open(dd.path("PRICED"), Copybooks.LNPRCREC);
                RecordReader decisions = RecordReader.open(dd.path("DECISION"), Copybooks.LNDECREC);
                RecordWriter out = RecordWriter.open(dd.path("FISOUT"), LineMode.FIXED)) {
            FixedRecord prc = null;
            for (FixedRecord dec = decisions.read(); dec != null; dec = decisions.read()) {
                FixedRecord next = priced.read();
                if (next != null) {
                    prc = next;
                }
                if ("A".equals(dec.getString("DEC-CODE"))) {
                    if (prc == null) {
                        throw new IOException("PRICED has no record for DECISION record " + decisions.recordNumber());
                    }
                    out.write(buildMessage(dec, prc, pmtDate, fundDate));
                    written = (written + 1) % WRITTEN_MODULUS;
                }
            }
        }
        sysout.println("LNFIS030 WRITTEN  " + String.format("%07d", written));
        return 0;
    }

    /** FUNDING-DATE: run date plus 30 calendar days (INTEGER-OF-DATE arithmetic). */
    static long fundingDate(long runDate) {
        LocalDate d = LocalDate.parse(String.format("%08d", runDate), DateTimeFormatter.BASIC_ISO_DATE);
        return Long.parseLong(d.plusDays(30).format(DateTimeFormatter.BASIC_ISO_DATE));
    }

    /** FIRST-PMT-DATE: day 01 of the funding month plus two, rolling the year past 12. */
    static long firstPaymentDate(long fundDate) {
        long yyyy = fundDate / 10_000;
        long mm = fundDate / 100 % 100 + 2;
        if (mm > 12) {
            mm -= 12;
            yyyy = (yyyy + 1) % 10_000;
        }
        return yyyy * 10_000 + mm * 100 + 1;
    }

    static FixedRecord buildMessage(FixedRecord dec, FixedRecord prc, long pmtDate, long fundDate) {
        FixedRecord app = FixedRecord.parse(Copybooks.LNAPPREC, prc.getString("PRC-APP"));
        BigDecimal loanNumber = BigDecimal.valueOf(LOAN_NUMBER_BASE).add(dec.getDecimal("DEC-APP-ID"));
        return FixedRecord.blank(Copybooks.FISACCT)
                .set("MSG-TYPE", "ACCT")
                .set("LOAN-NUMBER", loanNumber)
                .set("BORROWER-SSN", app.getDecimal("BORR-TIN"))
                .set("FIS-LOAN-TYPE", dec.getString("DEC-LOAN-TYPE"))
                .set("FIS-LOAN-AMOUNT", dec.getDecimal("DEC-LOAN-AMT"))
                .set("INTEREST-RATE", dec.getDecimal("DEC-FINAL-RATE"))
                .set("FIS-TERM-MONTHS", app.getDecimal("TERM-MONTHS"))
                .set("FIRST-PMT-DATE", pmtDate)
                .set("FUNDING-DATE", fundDate);
    }

    private static long readRunDate(Dd dd) throws IOException {
        String line = java.nio.file.Files.readString(dd.path("RUNDATE"), java.nio.charset.StandardCharsets.ISO_8859_1);
        if (line.length() < RUN_DATE_LENGTH) {
            throw new IOException("RUNDATE record is shorter than " + RUN_DATE_LENGTH + " bytes");
        }
        return Long.parseLong(line.substring(0, RUN_DATE_LENGTH));
    }
}
