package los.program;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import los.BatchProgram;
import los.Dd;
import los.record.Copybooks;
import los.record.FixedRecord;
import los.record.Layout;
import los.record.LineMode;
import los.record.RecordReader;
import los.record.RecordWriter;

/** STEP020 underwriting: reads RUNDATE, PRICED, writes DECISION (docs/legacy-spec.md section 5). */
public final class LNUWR020 implements BatchProgram {

    /** RUNDATE-REC, declared inline in LNUWR020.cbl. */
    static final Layout RUNDATE_REC = Layout.builder("RUNDATE-REC")
            .field("RD-DATE", "9(8)")
            .filler("X(72)")
            .build(80);

    /** WS-CACHE-DAYS: a score older than this many days is re-pulled. */
    static final int CACHE_DAYS = 30;

    static final BigDecimal JUMBO_CUT = new BigDecimal("500000.00");
    static final BigDecimal DTI_SENTINEL = new BigDecimal("999.99");
    static final BigDecimal MIN_EMP_YEARS = new BigDecimal("2.0");
    private static final BigDecimal DTI_LIMIT = new BigDecimal("1000");
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final LocalDate INTEGER_OF_DATE_ORIGIN = LocalDate.of(1600, 12, 31);

    /** One row of the 2200-MATRIX table. */
    record Thresholds(int autoScore, BigDecimal autoDti, int declineScore, BigDecimal declineDti) {
    }

    static final Thresholds FHA = new Thresholds(680, new BigDecimal("40.00"), 580, new BigDecimal("50.00"));
    static final Thresholds CONFORMING = new Thresholds(740, new BigDecimal("36.00"), 620, new BigDecimal("43.00"));
    static final Thresholds JUMBO = new Thresholds(760, new BigDecimal("33.00"), 680, new BigDecimal("43.00"));

    /** DEC-CODE and DEC-REASON of one decision. */
    record Decision(char code, String reason) {
    }

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
        long runDate;
        try (RecordReader in = RecordReader.open(dd.path("RUNDATE"), RUNDATE_REC)) {
            FixedRecord rd = in.read();
            runDate = rd == null ? 0 : rd.getLong("RD-DATE");
        }
        long read = 0;
        long approved = 0;
        long review = 0;
        long declined = 0;
        long repull = 0;
        try (RecordReader in = RecordReader.open(dd.path("PRICED"), Copybooks.LNPRCREC);
                RecordWriter out = RecordWriter.open(dd.path("DECISION"), LineMode.FIXED)) {
            for (FixedRecord priced = in.read(); priced != null; priced = in.read()) {
                read++;
                FixedRecord dec = decide(priced, runDate);
                switch (dec.getString("DEC-CODE").charAt(0)) {
                    case 'A' -> approved++;
                    case 'M' -> review++;
                    case 'D' -> declined++;
                    case 'R' -> repull++;
                    default -> { }
                }
                out.write(dec);
            }
        }
        sysout.print("LNUWR020 READ     " + count(read) + "\n");
        sysout.print("LNUWR020 APPROVED " + count(approved) + "\n");
        sysout.print("LNUWR020 REVIEW   " + count(review) + "\n");
        sysout.print("LNUWR020 DECLINED " + count(declined) + "\n");
        sysout.print("LNUWR020 REPULL   " + count(repull) + "\n");
        sysout.flush();
        return 0;
    }

    /** 2000-DECIDE: the DECISION record for one PRICED record. */
    static FixedRecord decide(FixedRecord priced, long runDate) {
        FixedRecord app = FixedRecord.parse(Copybooks.LNAPPREC, priced.getString("PRC-APP"));
        FixedRecord dec = FixedRecord.blank(Copybooks.LNDECREC).initialize();
        dec.set("DEC-APP-ID", app.getDecimal("APP-ID"));
        dec.set("DEC-LOAN-TYPE", app.getString("LOAN-TYPE"));
        dec.set("DEC-PRODUCT", priced.getString("PRC-PRODUCT"));
        dec.set("DEC-LOAN-AMT", app.getDecimal("LOAN-AMT"));
        dec.set("DEC-FINAL-RATE", priced.getDecimal("PRC-FINAL-RATE"));
        dec.set("DEC-MONTHLY-PMT", priced.getDecimal("PRC-MONTHLY-PMT"));
        dec.set("DEC-EXT-FEE-AMT", priced.getDecimal("PRC-EXT-FEE-AMT"));
        dec.set("DEC-DATE", runDate);
        BigDecimal dti = dti(app.getDecimal("ANNUAL-INCOME"), app.getDecimal("MONTHLY-DEBT"),
                priced.getDecimal("PRC-MONTHLY-PMT"));
        dec.set("DEC-DTI", dti);
        Decision d;
        if (!"00".equals(priced.getString("PRC-STATUS"))) {
            d = new Decision('D', "PRICE");
        } else if (isStale(scoreAge(runDate, app.getLong("SCORE-DATE")))) {
            d = new Decision('R', "STALE");
        } else {
            d = matrix(app.getString("LOAN-TYPE"), app.getDecimal("LOAN-AMT"), app.getInt("CREDIT-SCORE"),
                    dti, app.getDecimal("EMP-YEARS"));
        }
        dec.set("DEC-CODE", String.valueOf(d.code()));
        dec.set("DEC-REASON", d.reason());
        return dec;
    }

    /** {@code WS-MONTHLY-INC ROUNDED = ANNUAL-INCOME / 12} into 9(9)V99. */
    static BigDecimal monthlyIncome(BigDecimal annualIncome) {
        return annualIncome.divide(TWELVE, 2, RoundingMode.HALF_UP);
    }

    /** 2100-DTI: back-end DTI percent rounded half up to 2 decimals; 999.99 for no income or overflow. */
    static BigDecimal dti(BigDecimal annualIncome, BigDecimal monthlyDebt, BigDecimal monthlyPayment) {
        BigDecimal income = monthlyIncome(annualIncome);
        if (income.signum() == 0) {
            return DTI_SENTINEL;
        }
        BigDecimal dti = monthlyDebt.add(monthlyPayment).multiply(HUNDRED)
                .divide(income, 2, RoundingMode.HALF_UP);
        return dti.compareTo(DTI_LIMIT) >= 0 ? DTI_SENTINEL : dti;
    }

    /** {@code FUNCTION INTEGER-OF-DATE}: day 1 is 1601-01-01; an invalid date gives 0, as GnuCOBOL does. */
    static long integerOfDate(long yyyymmdd) {
        int year = (int) (yyyymmdd / 10000);
        int month = (int) (yyyymmdd / 100 % 100);
        int day = (int) (yyyymmdd % 100);
        if (year < 1601 || year > 9999) {
            return 0;
        }
        try {
            return ChronoUnit.DAYS.between(INTEGER_OF_DATE_ORIGIN, LocalDate.of(year, month, day));
        } catch (DateTimeException e) {
            return 0;
        }
    }

    /** WS-SCORE-AGE: run date minus score date in days; negative when the score is dated after the run. */
    static long scoreAge(long runDate, long scoreDate) {
        return integerOfDate(runDate) - integerOfDate(scoreDate);
    }

    /** The 30-day credit cache window: exactly 30 days is still fresh. */
    static boolean isStale(long scoreAge) {
        return scoreAge > CACHE_DAYS;
    }

    /** The 2200-MATRIX row: FHA at any amount, otherwise conforming below 500,000.00 and jumbo from it. */
    static Thresholds thresholds(String loanType, BigDecimal loanAmt) {
        if ("FHA".equals(loanType)) {
            return FHA;
        }
        return loanAmt.compareTo(JUMBO_CUT) < 0 ? CONFORMING : JUMBO;
    }

    /** 2200-MATRIX: decline on score, then on DTI, auto-approve, else review; then the employment override. */
    static Decision matrix(String loanType, BigDecimal loanAmt, int creditScore, BigDecimal dti,
            BigDecimal empYears) {
        Thresholds t = thresholds(loanType, loanAmt);
        Decision d;
        if (creditScore < t.declineScore()) {
            d = new Decision('D', "SCORE");
        } else if (dti.compareTo(t.declineDti()) > 0) {
            d = new Decision('D', "DTI");
        } else if (creditScore >= t.autoScore() && dti.compareTo(t.autoDti()) <= 0) {
            d = new Decision('A', "AUTO");
        } else {
            d = new Decision('M', "RVW");
        }
        if (d.code() == 'A' && empYears.compareTo(MIN_EMP_YEARS) < 0) {
            d = new Decision('M', "EMPL");
        }
        return d;
    }

    private static String count(long n) {
        String digits = Long.toString(n % 10_000_000L);
        return "0".repeat(7 - digits.length()) + digits;
    }
}
