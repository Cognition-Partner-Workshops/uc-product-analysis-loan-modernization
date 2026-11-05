package los.program;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
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

/** STEP020 underwriting: reads RUNDATE, PRICED, writes DECISION. */
public final class LNUWR020 implements BatchProgram {

    /** RUNDATE-REC as declared inline in LNUWR020. */
    static final Layout RUNDATE_REC = Layout.builder("RUNDATE-REC")
            .field("RD-DATE", "9(8)")
            .filler("X(72)")
            .build(80);

    static final int CACHE_DAYS = 30;
    static final BigDecimal JUMBO_FROM = new BigDecimal("500000");
    static final BigDecimal MIN_EMP_YEARS = new BigDecimal("2.0");
    static final BigDecimal DTI_NO_INCOME = new BigDecimal("999.99");
    static final BigDecimal DTI_LIMIT = new BigDecimal("1000");
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final DateTimeFormatter YYYYMMDD =
            DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);

    /** One row of the 2200-MATRIX threshold table. */
    record Thresholds(int autoScore, BigDecimal autoDti, int declScore, BigDecimal declDti) {
    }

    static final Thresholds FHA = new Thresholds(680, new BigDecimal("40.00"), 580, new BigDecimal("50.00"));
    static final Thresholds CONFORMING = new Thresholds(740, new BigDecimal("36.00"), 620, new BigDecimal("43.00"));
    static final Thresholds JUMBO = new Thresholds(760, new BigDecimal("33.00"), 680, new BigDecimal("43.00"));

    @Override
    public String name() {
        return "LNUWR020";
    }

    @Override
    public List<String> inputs() {
        return List.of("RUNDATE", "PRICED");
    }

    @Override
    public List<String> outputs() {
        return List.of("DECISION");
    }

    @Override
    public int run(Dd dd, PrintStream sysout) throws IOException {
        long runDate = readRunDate(dd);
        long read = 0;
        long approved = 0;
        long review = 0;
        long declined = 0;
        long repull = 0;
        try (RecordReader in = RecordReader.open(dd.path("PRICED"), Copybooks.LNPRCREC);
                RecordWriter out = RecordWriter.open(dd.path("DECISION"), LineMode.FIXED)) {
            for (FixedRecord prc = in.read(); prc != null; prc = in.read()) {
                read++;
                FixedRecord dec = decide(prc, runDate);
                switch (dec.getString("DEC-CODE")) {
                    case "A" -> approved++;
                    case "M" -> review++;
                    case "D" -> declined++;
                    case "R" -> repull++;
                    default -> {
                    }
                }
                out.write(dec);
            }
        }
        display(sysout, "LNUWR020 READ     ", read);
        display(sysout, "LNUWR020 APPROVED ", approved);
        display(sysout, "LNUWR020 REVIEW   ", review);
        display(sysout, "LNUWR020 DECLINED ", declined);
        display(sysout, "LNUWR020 REPULL   ", repull);
        return 0;
    }

    private static long readRunDate(Dd dd) throws IOException {
        try (RecordReader r = RecordReader.open(dd.path("RUNDATE"), RUNDATE_REC)) {
            FixedRecord rec = r.read();
            if (rec == null) {
                throw new RecordFormatException("RUNDATE is empty");
            }
            return rec.getLong("RD-DATE");
        }
    }

    private static void display(PrintStream sysout, String label, long count) {
        sysout.print(label + String.format(Locale.ROOT, "%07d", count % 10_000_000L) + "\n");
    }

    /** 2000-DECIDE: one DECISION record for one PRICED record. */
    static FixedRecord decide(FixedRecord prc, long runDate) {
        FixedRecord app = FixedRecord.parse(Copybooks.LNAPPREC, prc.getString("PRC-APP"));
        FixedRecord dec = FixedRecord.blank(Copybooks.LNDECREC).initialize();
        dec.set("DEC-APP-ID", app.getDecimal("APP-ID"));
        dec.set("DEC-LOAN-TYPE", app.getString("LOAN-TYPE"));
        dec.set("DEC-PRODUCT", prc.getString("PRC-PRODUCT"));
        dec.set("DEC-LOAN-AMT", app.getDecimal("LOAN-AMT"));
        dec.set("DEC-FINAL-RATE", prc.getDecimal("PRC-FINAL-RATE"));
        dec.set("DEC-MONTHLY-PMT", prc.getDecimal("PRC-MONTHLY-PMT"));
        dec.set("DEC-EXT-FEE-AMT", prc.getDecimal("PRC-EXT-FEE-AMT"));
        dec.set("DEC-DATE", runDate);
        dec.set("DEC-DTI", dti(app.getDecimal("ANNUAL-INCOME"), app.getDecimal("MONTHLY-DEBT"),
                prc.getDecimal("PRC-MONTHLY-PMT")), RoundingMode.UNNECESSARY);
        if (!"00".equals(prc.getString("PRC-STATUS"))) {
            decision(dec, "D", "PRICE");
        } else if (scoreAge(runDate, app.getLong("SCORE-DATE")) > CACHE_DAYS) {
            decision(dec, "R", "STALE");
        } else {
            matrix(app, dec);
        }
        return dec;
    }

    /** 2100-DTI: back-end DTI in percent, 9(3)V99, with 999.99 for no income or size error. */
    static BigDecimal dti(BigDecimal annualIncome, BigDecimal monthlyDebt, BigDecimal monthlyPmt) {
        BigDecimal monthlyInc = annualIncome.divide(TWELVE, 2, RoundingMode.HALF_UP);
        if (monthlyInc.signum() == 0) {
            return DTI_NO_INCOME;
        }
        BigDecimal dti = monthlyDebt.add(monthlyPmt).multiply(HUNDRED)
                .divide(monthlyInc, 2, RoundingMode.HALF_UP);
        return dti.compareTo(DTI_LIMIT) >= 0 ? DTI_NO_INCOME : dti;
    }

    /** INTEGER-OF-DATE(run date) - INTEGER-OF-DATE(score date), in days. */
    static long scoreAge(long runDate, long scoreDate) {
        return ChronoUnit.DAYS.between(date(scoreDate), date(runDate));
    }

    private static LocalDate date(long yyyymmdd) {
        try {
            return LocalDate.parse(String.format(Locale.ROOT, "%08d", yyyymmdd), YYYYMMDD);
        } catch (DateTimeParseException e) {
            throw new RecordFormatException("invalid date " + yyyymmdd + ": " + e.getMessage());
        }
    }

    static Thresholds thresholds(String loanType, BigDecimal loanAmt) {
        if ("FHA".equals(loanType)) {
            return FHA;
        }
        return loanAmt.compareTo(JUMBO_FROM) < 0 ? CONFORMING : JUMBO;
    }

    /** 2200-MATRIX: decision by loan type and size, then the employment override. */
    private static void matrix(FixedRecord app, FixedRecord dec) {
        Thresholds t = thresholds(app.getString("LOAN-TYPE"), app.getDecimal("LOAN-AMT"));
        int score = app.getInt("CREDIT-SCORE");
        BigDecimal dti = dec.getDecimal("DEC-DTI");
        if (score < t.declScore()) {
            decision(dec, "D", "SCORE");
        } else if (dti.compareTo(t.declDti()) > 0) {
            decision(dec, "D", "DTI");
        } else if (score >= t.autoScore() && dti.compareTo(t.autoDti()) <= 0) {
            decision(dec, "A", "AUTO");
        } else {
            decision(dec, "M", "RVW");
        }
        if ("A".equals(dec.getString("DEC-CODE")) && app.getDecimal("EMP-YEARS").compareTo(MIN_EMP_YEARS) < 0) {
            decision(dec, "M", "EMPL");
        }
    }

    private static void decision(FixedRecord dec, String code, String reason) {
        dec.set("DEC-CODE", code);
        dec.set("DEC-REASON", reason);
    }
}
