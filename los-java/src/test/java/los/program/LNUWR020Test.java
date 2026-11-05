package los.program;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import los.Dd;
import los.record.Copybooks;
import los.record.FixedRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LNUWR020Test {

    static final Path LEGACY = Path.of(System.getProperty("legacy.los.dir", "../legacy-los"));
    static final long RUN_DATE = 20261105L;

    @Test
    void recordedNightMatchesGoldenDecisionAndSysout(@TempDir Path tmp) throws IOException {
        Path decision = tmp.resolve("DECISION.dat");
        String sysout = run(LEGACY.resolve("data/input/RUNDATE.dat"), LEGACY.resolve("golden/PRICED.dat"), decision);

        assertEquals(-1L, Files.mismatch(LEGACY.resolve("golden/DECISION.dat"), decision));
        String golden = Files.readString(LEGACY.resolve("golden/SYSOUT.txt"), StandardCharsets.ISO_8859_1);
        String expected = golden.lines().filter(l -> l.startsWith("LNUWR020 "))
                .map(l -> l + "\n").reduce("", String::concat);
        assertEquals(5, expected.lines().count());
        assertEquals(expected, sysout);
    }

    @Test
    void writesOneFixedWidthRecordPerPricedRecordWithBlankFiller(@TempDir Path tmp) throws IOException {
        Path priced = tmp.resolve("PRICED.dat");
        Files.writeString(priced, priced(app()).toLine() + "\n"
                + priced(app().set("CREDIT-SCORE", 500)).toLine() + "\n", StandardCharsets.ISO_8859_1);
        Path decision = tmp.resolve("DECISION.dat");
        String sysout = run(runDate(tmp), priced, decision);

        byte[] bytes = Files.readAllBytes(decision);
        assertEquals(2 * 81, bytes.length);
        assertEquals('\n', bytes[80]);
        assertEquals('\n', bytes[161]);
        String first = new String(bytes, 0, 80, StandardCharsets.ISO_8859_1);
        assertEquals("        ", first.substring(72));
        assertEquals("LNUWR020 READ     0000002\nLNUWR020 APPROVED 0000001\nLNUWR020 REVIEW   0000000\n"
                + "LNUWR020 DECLINED 0000001\nLNUWR020 REPULL   0000000\n", sysout);
    }

    @Test
    void copiesFieldsFromTheApplicationAndPricing() {
        FixedRecord dec = LNUWR020.decide(priced(app()), RUN_DATE);
        assertEquals("0000012345", dec.getString("DEC-APP-ID"));
        assertEquals("CNV", dec.getString("DEC-LOAN-TYPE"));
        assertEquals("C30F ", dec.getString("DEC-PRODUCT"));
        assertEquals(new BigDecimal("300000.00"), dec.getDecimal("DEC-LOAN-AMT"));
        assertEquals(new BigDecimal("6.8750"), dec.getDecimal("DEC-FINAL-RATE"));
        assertEquals(new BigDecimal("1970.79"), dec.getDecimal("DEC-MONTHLY-PMT"));
        assertEquals(new BigDecimal("123.45"), dec.getDecimal("DEC-EXT-FEE-AMT"));
        assertEquals(RUN_DATE, dec.getLong("DEC-DATE"));
        assertEquals("A", dec.getString("DEC-CODE"));
        assertEquals("AUTO ", dec.getString("DEC-REASON"));
    }

    @ParameterizedTest(name = "score date {0}: {1}")
    @CsvSource({
        "20261006, A",   // 30 days: still fresh
        "20261005, R",   // 31 days: stale
        "20261231, A",   // after run date: negative age is fresh
        "20250101, R"})
    void creditCacheWindowIsThirtyDays(long scoreDate, String code) {
        FixedRecord dec = LNUWR020.decide(priced(app().set("SCORE-DATE", scoreDate)), RUN_DATE);
        assertEquals(code, dec.getString("DEC-CODE"));
        if (code.equals("R")) {
            assertEquals("STALE", dec.getString("DEC-REASON"));
        }
    }

    @Test
    void staleScoreSkipsTheMatrixWhateverTheScore() {
        FixedRecord dec = LNUWR020.decide(priced(app().set("SCORE-DATE", 20250101L).set("CREDIT-SCORE", 300)),
                RUN_DATE);
        assertEquals("R", dec.getString("DEC-CODE"));
    }

    @Test
    void pricingRejectDeclinesBeforeCacheAndMatrixAndStillComputesDti() {
        FixedRecord prc = priced(app().set("SCORE-DATE", 20250101L).set("CREDIT-SCORE", 300));
        prc.set("PRC-STATUS", "E2").set("PRC-MONTHLY-PMT", BigDecimal.ZERO);
        FixedRecord dec = LNUWR020.decide(prc, RUN_DATE);
        assertEquals("D", dec.getString("DEC-CODE"));
        assertEquals("PRICE", dec.getString("DEC-REASON"));
        // debt only: 500.00 * 100 / 10000.00
        assertEquals(new BigDecimal("5.00"), dec.getDecimal("DEC-DTI"));
    }

    @ParameterizedTest(name = "{0} {1}: {2}")
    @CsvSource({
        "CNV, 499999.99, 740",
        "CNV, 500000.00, 760",
        "CNV, 900000.00, 760",
        "FHA, 900000.00, 680",
        "FHA, 100000.00, 680"})
    void jumboCutIsAtExactlyFiveHundredThousandAndFhaIgnoresAmount(String type, String amt, int autoScore) {
        assertEquals(autoScore, LNUWR020.thresholds(type, new BigDecimal(amt)).autoScore());
    }

    @ParameterizedTest(name = "{0} {1} score {2} dti {3}: {4} {5}")
    @CsvSource({
        // conforming: auto 740 / 36.00, decline 620 / 43.00
        "CNV, 300000.00, 740, 36.00, A, AUTO ",
        "CNV, 300000.00, 739, 36.00, M, RVW  ",
        "CNV, 300000.00, 740, 36.01, M, RVW  ",
        "CNV, 300000.00, 620, 43.00, M, RVW  ",
        "CNV, 300000.00, 619, 10.00, D, SCORE",
        "CNV, 300000.00, 619, 60.00, D, SCORE",
        "CNV, 300000.00, 800, 43.01, D, DTI  ",
        // jumbo at exactly 500,000: auto 760 / 33.00, decline 680 / 43.00
        "CNV, 500000.00, 750, 30.00, M, RVW  ",
        "CNV, 500000.00, 760, 33.00, A, AUTO ",
        "CNV, 500000.00, 679, 30.00, D, SCORE",
        "CNV, 499999.99, 750, 30.00, A, AUTO ",
        // FHA: auto 680 / 40.00, decline 580 / 50.00
        "FHA, 900000.00, 680, 40.00, A, AUTO ",
        "FHA, 300000.00, 580, 50.00, M, RVW  ",
        "FHA, 300000.00, 579, 20.00, D, SCORE",
        "FHA, 300000.00, 700, 50.01, D, DTI  "})
    void decisionMatrix(String type, String amt, int score, String dti, String code, String reason) {
        // income 120,000.00 => monthly 10,000.00; payment carries the whole DTI
        BigDecimal pmt = new BigDecimal(dti).multiply(BigDecimal.valueOf(100));
        FixedRecord prc = priced(app().set("LOAN-TYPE", type).set("LOAN-AMT", new BigDecimal(amt))
                .set("CREDIT-SCORE", score).set("MONTHLY-DEBT", BigDecimal.ZERO));
        prc.set("PRC-MONTHLY-PMT", pmt);
        FixedRecord dec = LNUWR020.decide(prc, RUN_DATE);
        assertEquals(new BigDecimal(dti), dec.getDecimal("DEC-DTI"));
        assertEquals(code, dec.getString("DEC-CODE"));
        assertEquals(String.format("%-5s", reason), dec.getString("DEC-REASON"));
    }

    @ParameterizedTest(name = "emp {0} score {1}: {2} {3}")
    @CsvSource({
        "1.9, 800, M, EMPL ",
        "2.0, 800, A, AUTO ",
        "0.0, 700, M, RVW  ",
        "0.0, 500, D, SCORE"})
    void employmentOverrideOnlyTurnsAutoApprovalsIntoReview(String years, int score, String code, String reason) {
        FixedRecord dec = LNUWR020.decide(priced(app().set("EMP-YEARS", new BigDecimal(years))
                .set("CREDIT-SCORE", score)), RUN_DATE);
        assertEquals(code, dec.getString("DEC-CODE"));
        assertEquals(String.format("%-5s", reason), dec.getString("DEC-REASON"));
    }

    @ParameterizedTest(name = "income {0} debt {1} pmt {2}: {3}")
    @CsvSource({
        // monthly income rounded half up to cents first: 100000.00/12 = 8333.33
        "100000.00, 0.00, 1000.00, 12.00",
        // 2999.99*100/8333.33 = 35.99989...
        "100000.00, 0.00, 2999.99, 36.00",
        "120.00, 0.00, 1.23, 12.30",
        "120.00, 1.00, 0.0, 10.00",
        // 0.06 / 12 = 0.005 rounds up to 0.01: tiny income still divides
        "0.06, 0.00, 0.01, 100.00",
        // 0.05 / 12 = 0.00416 rounds to 0.00: no income
        "0.05, 100.00, 0.00, 999.99",
        "0.00, 0.00, 0.00, 999.99",
        // size error: >= 1000.00 becomes 999.99
        // monthly 10.00, so DTI = debt * 10
        "120.00, 9.99, 0.00, 99.90",
        "120.00, 99.99, 0.00, 999.90",
        "120.00, 100.00, 0.00, 999.99",
        "120.00, 99.9995, 0.00, 999.99"})
    void dtiRoundingAndSizeError(String income, String debt, String pmt, String expected) {
        assertEquals(new BigDecimal(expected),
                LNUWR020.dti(new BigDecimal(income), new BigDecimal(debt), new BigDecimal(pmt)));
    }

    @Test
    void dtiRoundsHalfUp() {
        // monthly 3.00: 100/3 = 33.333 -> 33.33, 200/3 = 66.667 -> 66.67
        assertEquals(new BigDecimal("33.33"), LNUWR020.dti(new BigDecimal("36.00"), BigDecimal.ONE, BigDecimal.ZERO));
        assertEquals(new BigDecimal("66.67"), LNUWR020.dti(new BigDecimal("36.00"), new BigDecimal("2"), BigDecimal.ZERO));
        // monthly 0.08: 0.0001 * 100 / 0.08 = 0.125 -> 0.13
        assertEquals(new BigDecimal("0.13"),
                LNUWR020.dti(new BigDecimal("0.96"), new BigDecimal("0.0001"), BigDecimal.ZERO));
    }

    @Test
    void outputDoesNotDependOnTheDefaultLocale(@TempDir Path tmp) throws IOException {
        Locale saved = Locale.getDefault();
        try {
            // Arabic-Egypt formats %07d with Arabic-Indic digits unless Locale.ROOT is used
            Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"));
            Path decision = tmp.resolve("DECISION.dat");
            String sysout = run(LEGACY.resolve("data/input/RUNDATE.dat"), LEGACY.resolve("golden/PRICED.dat"),
                    decision);
            assertEquals(-1L, Files.mismatch(LEGACY.resolve("golden/DECISION.dat"), decision));
            assertEquals("LNUWR020 READ     0000060", sysout.lines().findFirst().orElseThrow());
            assertEquals(30L, LNUWR020.scoreAge(RUN_DATE, 20261006L));
        } finally {
            Locale.setDefault(saved);
        }
    }

    @Test
    void declaresTheCobolDdNames() {
        LNUWR020 p = new LNUWR020();
        assertEquals("LNUWR020", p.name());
        assertEquals(List.of("RUNDATE", "PRICED"), p.inputs());
        assertEquals(List.of("DECISION"), p.outputs());
    }

    private static String run(Path rundate, Path priced, Path decision) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Dd dd = new Dd(Map.of("DD_RUNDATE", rundate.toString(), "DD_PRICED", priced.toString(),
                "DD_DECISION", decision.toString()));
        assertEquals(0, new LNUWR020().run(dd, new PrintStream(buf, true, StandardCharsets.ISO_8859_1)));
        return buf.toString(StandardCharsets.ISO_8859_1);
    }

    private static Path runDate(Path tmp) throws IOException {
        Path p = tmp.resolve("RUNDATE.dat");
        Files.writeString(p, RUN_DATE + " ".repeat(72) + "\n", StandardCharsets.ISO_8859_1);
        return p;
    }

    /** A conforming application that auto-approves on the run date: DTI 24.71, score 760. */
    private static FixedRecord app() {
        return FixedRecord.blank(Copybooks.LNAPPREC).initialize()
                .set("APP-ID", 12345L)
                .set("BORR-LAST-NAME", "TESTER")
                .set("LOAN-TYPE", "CNV")
                .set("LOAN-PURPOSE", "P")
                .set("LOAN-AMT", new BigDecimal("300000.00"))
                .set("TERM-MONTHS", 360L)
                .set("ANNUAL-INCOME", new BigDecimal("120000.00"))
                .set("MONTHLY-DEBT", new BigDecimal("500.00"))
                .set("CREDIT-SCORE", 760L)
                .set("SCORE-DATE", 20261101L)
                .set("EMP-YEARS", new BigDecimal("5.0"))
                .set("LOCK-DAYS", 30L)
                .set("APP-DATE", 20261101L);
    }

    private static FixedRecord priced(FixedRecord app) {
        return FixedRecord.blank(Copybooks.LNPRCREC).initialize()
                .set("PRC-APP", app.toLine())
                .set("PRC-PRODUCT", "C30F")
                .set("PRC-BASE-RATE", new BigDecimal("6.8750"))
                .set("PRC-FINAL-RATE", new BigDecimal("6.8750"))
                .set("PRC-MONTHLY-PMT", new BigDecimal("1970.79"))
                .set("PRC-EXT-FEE-AMT", new BigDecimal("123.45"))
                .set("PRC-STATUS", "00");
    }
}
