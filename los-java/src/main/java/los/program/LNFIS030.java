package los.program;

import java.io.IOException;
import java.io.PrintStream;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import los.BatchProgram;
import los.Dd;
import los.record.Copybooks;
import los.record.FixedRecord;
import los.record.Layout;
import los.record.LineMode;
import los.record.RecordFormatException;
import los.record.RecordReader;
import los.record.RecordWriter;

/** STEP030 core banking account feed: reads RUNDATE, PRICED, DECISION, writes FISOUT. */
public final class LNFIS030 implements BatchProgram {
    private static final Layout RUNDATE = Layout.builder("RUNDATE-REC")
            .field("RD-DATE", "9(8)").filler("X(72)").build(80);

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
        LocalDate funding;
        try (RecordReader dates = RecordReader.open(dd.path("RUNDATE"), RUNDATE)) {
            FixedRecord date = dates.read();
            if (date == null) {
                throw new RecordFormatException("RUNDATE is empty");
            }
            int value = date.getInt("RD-DATE");
            try {
                funding = LocalDate.of(value / 10000, value / 100 % 100, value % 100).plusDays(30);
            } catch (DateTimeException e) {
                throw new RecordFormatException("Invalid RUNDATE " + value);
            }
        }
        LocalDate payment = funding.withDayOfMonth(1).plusMonths(2);
        long written = 0;
        try (RecordReader priced = RecordReader.open(dd.path("PRICED"), Copybooks.LNPRCREC);
                RecordReader decisions = RecordReader.open(dd.path("DECISION"), Copybooks.LNDECREC);
                RecordWriter output = RecordWriter.open(dd.path("FISOUT"), LineMode.FIXED)) {
            for (FixedRecord decision = decisions.read(); decision != null; decision = decisions.read()) {
                FixedRecord price = priced.read();
                if (price == null) {
                    throw new RecordFormatException("PRICED ended before DECISION");
                }
                if (!decision.getString("DEC-CODE").equals("A")) {
                    continue;
                }
                FixedRecord app = FixedRecord.parse(Copybooks.LNAPPREC, price.getString("PRC-APP"));
                output.write(FixedRecord.blank(Copybooks.FISACCT)
                        .set("MSG-TYPE", "ACCT")
                        .set("LOAN-NUMBER", 40_000_000_000L + decision.getLong("DEC-APP-ID"))
                        .set("BORROWER-SSN", app.getLong("BORR-TIN"))
                        .set("FIS-LOAN-TYPE", decision.getString("DEC-LOAN-TYPE"))
                        .set("FIS-LOAN-AMOUNT", decision.getDecimal("DEC-LOAN-AMT"))
                        .set("INTEREST-RATE", decision.getDecimal("DEC-FINAL-RATE"))
                        .set("FIS-TERM-MONTHS", app.getLong("TERM-MONTHS"))
                        .set("FIRST-PMT-DATE", dateNumber(payment))
                        .set("FUNDING-DATE", dateNumber(funding)));
                written++;
            }
        }
        sysout.printf(Locale.ROOT, "LNFIS030 WRITTEN  %07d%n", written % 10_000_000);
        return 0;
    }

    private static long dateNumber(LocalDate date) {
        return date.getYear() * 10000L + date.getMonthValue() * 100L + date.getDayOfMonth();
    }
}
