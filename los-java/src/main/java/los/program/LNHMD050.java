package los.program;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import los.BatchProgram;
import los.Dd;
import los.record.Copybooks;
import los.record.FixedRecord;
import los.record.LineMode;
import los.record.RecordReader;
import los.record.RecordWriter;

/** STEP050 HMDA extract: reads RATESIN, PRICED, DECISION, writes HMDAOUT. */
public final class LNHMD050 implements BatchProgram {
    private static final String HEADER = "ApplicationID|ActionTaken|ActionDate|LoanType|"
            + "LoanPurpose|LoanAmount|PropertyType|PropertyState|ApplicantRace|ApplicantSex|"
            + "ApplicantIncome|RateSpread";

    @Override
    public String name() {
        return "LNHMD050";
    }

    @Override
    public List<String> inputs() {
        return List.of("RATESIN", "PRICED", "DECISION");
    }

    @Override
    public List<String> outputs() {
        return List.of("HMDAOUT");
    }

    @Override
    public int run(Dd dd, PrintStream sysout) throws IOException {
        BigDecimal apor = BigDecimal.ZERO;
        try (RecordReader rates = RecordReader.open(dd.path("RATESIN"), Copybooks.LNRATE)) {
            for (FixedRecord rate; (rate = rates.read()) != null;) {
                if (rate.getString("RATE-PRODUCT").equals("APOR ")
                        && rate.getInt("RATE-LOCK-DAYS") == 0) {
                    apor = rate.getDecimal("RATE-BASE");
                }
            }
        }
        long rows = 0;
        try (RecordReader priced = RecordReader.open(dd.path("PRICED"), Copybooks.LNPRCREC);
             RecordReader decisions = RecordReader.open(dd.path("DECISION"), Copybooks.LNDECREC);
             RecordWriter output = RecordWriter.open(dd.path("HMDAOUT"), LineMode.TRIMMED)) {
            output.write(HEADER);
            FixedRecord price = FixedRecord.blank(Copybooks.LNPRCREC);
            for (FixedRecord decision; (decision = decisions.read()) != null;) {
                FixedRecord next = priced.read();
                if (next != null) {
                    price = next;
                }
                String code = decision.getString("DEC-CODE");
                if (code.equals("A") || code.equals("D")) {
                    output.write(row(price, decision, apor));
                    rows++;
                }
            }
        }
        sysout.printf(Locale.ROOT, "LNHMD050 ROWS     %07d\n", rows);
        return 0;
    }

    private static String row(FixedRecord price, FixedRecord decision, BigDecimal apor) {
        FixedRecord app = FixedRecord.parse(Copybooks.LNAPPREC, price.getString("PRC-APP"));
        boolean approved = decision.getString("DEC-CODE").equals("A");
        String date = decision.getString("DEC-DATE");
        String spread = approved ? decision.getDecimal("DEC-FINAL-RATE").subtract(apor)
                .setScale(3, RoundingMode.HALF_UP).toPlainString() : "NA";
        return String.join("|", decision.getString("DEC-APP-ID"), approved ? "1" : "3",
                date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6, 8),
                app.getString("LOAN-TYPE").equals("FHA") ? "2" : "1",
                app.getString("LOAN-PURPOSE").equals("R") ? "31" : "1",
                app.getDecimal("LOAN-AMT").movePointLeft(3).setScale(0, RoundingMode.HALF_UP).toPlainString(),
                app.getString("PROP-TYPE"), app.getString("PROP-STATE"),
                app.getString("APPL-RACE"), app.getString("APPL-SEX"),
                app.getDecimal("ANNUAL-INCOME").movePointLeft(3).setScale(0, RoundingMode.DOWN).toPlainString(),
                spread);
    }
}
