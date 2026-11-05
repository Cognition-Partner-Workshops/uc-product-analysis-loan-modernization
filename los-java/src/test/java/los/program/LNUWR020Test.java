package los.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import los.Dd;
import los.record.Copybooks;
import los.record.FixedRecord;
import los.record.RecordReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LNUWR020Test {
    private static final long RUN_DATE = 20261105L;
    private static final Path LEGACY = Path.of(System.getProperty("legacy.los.dir"));

    @TempDir
    Path out;

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static LNUWR020.Decision decision(char code, String reason) {
        return new LNUWR020.Decision(code, reason);
    }

    // U3.1 monthly income = annual / 12, rounded half up to cents

    @Test
    void monthlyIncomeIsRoundedHalfUpToCents() {
        assertEquals(d("8333.34"), LNUWR020.monthlyIncome(d("100000.06")));
        assertEquals(d("8.33"), LNUWR020.monthlyIncome(d("100.00")));
        assertEquals(d("0.01"), LNUWR020.monthlyIncome(d("0.06")));
        assertEquals(d("0.00"), LNUWR020.monthlyIncome(d("0.05")));
        assertEquals(d("12000.00"), LNUWR020.monthlyIncome(d("144000.00")));
    }

    @Test
    void dtiDividesByTheRoundedMonthlyIncome() {
        // 0.06 / 12 is 0.005, rounded to 0.01; unrounded the DTI would be 200.00
        assertEquals(d("100.00"), LNUWR020.dti(d("0.06"), d("0.01"), d("0.00")));
    }

    // U3.2-3 DTI rounded half up, 999.99 sentinel

    @Test
    void dtiIsRoundedHalfUpToTwoDecimals() {
        assertEquals(d("12.35"), LNUWR020.dti(d("12000.00"), d("100.00"), d("23.45")));
        assertEquals(d("12.34"), LNUWR020.dti(d("12000.00"), d("100.00"), d("23.44")));
        // 1/3 of a percent stays below the half: 0.333.. -> 0.33, 2/3 -> 0.67
        assertEquals(d("33.33"), LNUWR020.dti(d("36.00"), d("1.00"), d("0.00")));
        assertEquals(d("66.67"), LNUWR020.dti(d("36.00"), d("2.00"), d("0.00")));
    }

    @Test
    void dtiOfARejectIsDebtOnly() {
        assertEquals(d("25.00"), LNUWR020.dti(d("12000.00"), d("250.00"), d("0.00")));
    }

    @Test
    void dtiIs999_99WithoutIncomeOrOnSizeError() {
        assertEquals(d("999.99"), LNUWR020.dti(d("0.00"), d("100.00"), d("0.00")));
        assertEquals(d("999.99"), LNUWR020.dti(d("0.05"), d("100.00"), d("0.00")));
        assertEquals(d("999.99"), LNUWR020.dti(d("12000.00"), d("10000.00"), d("0.00")));
        assertEquals(d("999.99"), LNUWR020.dti(d("12000.00"), d("9999.95"), d("0.00")));
        assertEquals(d("999.99"), LNUWR020.dti(d("12000.00"), d("9999.94"), d("0.00")));
        assertEquals(d("999.98"), LNUWR020.dti(d("12000.00"), d("9999.84"), d("0.00")));
    }

    // U5 credit-score age against the run date, 30-day window

    @Test
    void scoreAgeIsDaysBeforeTheRunDate() {
        assertEquals(0, LNUWR020.scoreAge(RUN_DATE, 20261105L));
        assertEquals(30, LNUWR020.scoreAge(RUN_DATE, 20261006L));
        assertEquals(31, LNUWR020.scoreAge(RUN_DATE, 20261005L));
        assertEquals(45, LNUWR020.scoreAge(RUN_DATE, 20260921L));
        assertEquals(-30, LNUWR020.scoreAge(RUN_DATE, 20261205L));
        assertEquals(30, LNUWR020.scoreAge(20280315L, 20280214L));
        assertEquals(366, LNUWR020.scoreAge(20250101L, 20240101L));
    }

    @Test
    void exactly30DaysIsFreshAnd31IsStale() {
        assertFalse(LNUWR020.isStale(30));
        assertTrue(LNUWR020.isStale(31));
        assertFalse(LNUWR020.isStale(0));
        assertFalse(LNUWR020.isStale(-30));
    }

    @Test
    void integerOfDateMatchesGnuCobol() {
        assertEquals(1, LNUWR020.integerOfDate(16010101L));
        assertEquals(155537, LNUWR020.integerOfDate(RUN_DATE));
        // invalid dates give 0, so the age is the whole run-date day number: stale
        assertEquals(0, LNUWR020.integerOfDate(0L));
        assertEquals(0, LNUWR020.integerOfDate(20261399L));
        assertEquals(0, LNUWR020.integerOfDate(20260230L));
        assertEquals(0, LNUWR020.integerOfDate(16001231L));
        assertTrue(LNUWR020.isStale(LNUWR020.scoreAge(RUN_DATE, 0L)));
    }

    // U6 jumbo cut at exactly 500,000.00

    @Test
    void jumboStartsAtExactly500000() {
        assertSame(LNUWR020.CONFORMING, LNUWR020.thresholds("CNV", d("499999.99")));
        assertSame(LNUWR020.JUMBO, LNUWR020.thresholds("CNV", d("500000.00")));
        assertSame(LNUWR020.JUMBO, LNUWR020.thresholds("CNV", d("500000.01")));
        assertSame(LNUWR020.FHA, LNUWR020.thresholds("FHA", d("100000.00")));
        assertSame(LNUWR020.FHA, LNUWR020.thresholds("FHA", d("651500.00")));
    }

    @Test
    void a500000LoanWithConformingApprovalIsReviewedAsJumbo() {
        // 2611050018: CNV 500,000.00, score 745, DTI 25.63
        assertEquals(decision('M', "RVW"), LNUWR020.matrix("CNV", d("500000.00"), 745, d("25.63"), d("5.0")));
        assertEquals(decision('A', "AUTO"), LNUWR020.matrix("CNV", d("499999.99"), 745, d("25.63"), d("5.0")));
    }

    // U6-U7 approve / review / decline thresholds, per row, at the boundaries

    @Test
    void conformingThresholds() {
        BigDecimal amt = d("300000.00");
        BigDecimal emp = d("5.0");
        assertEquals(decision('D', "SCORE"), LNUWR020.matrix("CNV", amt, 619, d("10.00"), emp));
        assertEquals(decision('M', "RVW"), LNUWR020.matrix("CNV", amt, 620, d("10.00"), emp));
        assertEquals(decision('D', "DTI"), LNUWR020.matrix("CNV", amt, 800, d("43.01"), emp));
        assertEquals(decision('M', "RVW"), LNUWR020.matrix("CNV", amt, 800, d("43.00"), emp));
        assertEquals(decision('A', "AUTO"), LNUWR020.matrix("CNV", amt, 740, d("36.00"), emp));
        assertEquals(decision('M', "RVW"), LNUWR020.matrix("CNV", amt, 739, d("36.00"), emp));
        assertEquals(decision('M', "RVW"), LNUWR020.matrix("CNV", amt, 740, d("36.01"), emp));
    }

    @Test
    void jumboThresholds() {
        BigDecimal amt = d("650000.00");
        BigDecimal emp = d("5.0");
        assertEquals(decision('D', "SCORE"), LNUWR020.matrix("CNV", amt, 679, d("10.00"), emp));
        assertEquals(decision('M', "RVW"), LNUWR020.matrix("CNV", amt, 680, d("10.00"), emp));
        assertEquals(decision('D', "DTI"), LNUWR020.matrix("CNV", amt, 800, d("43.01"), emp));
        assertEquals(decision('M', "RVW"), LNUWR020.matrix("CNV", amt, 800, d("43.00"), emp));
        assertEquals(decision('A', "AUTO"), LNUWR020.matrix("CNV", amt, 760, d("33.00"), emp));
        assertEquals(decision('M', "RVW"), LNUWR020.matrix("CNV", amt, 759, d("33.00"), emp));
        assertEquals(decision('M', "RVW"), LNUWR020.matrix("CNV", amt, 760, d("33.01"), emp));
    }

    @Test
    void fhaThresholds() {
        BigDecimal amt = d("250000.00");
        BigDecimal emp = d("5.0");
        assertEquals(decision('D', "SCORE"), LNUWR020.matrix("FHA", amt, 579, d("10.00"), emp));
        assertEquals(decision('M', "RVW"), LNUWR020.matrix("FHA", amt, 580, d("10.00"), emp));
        assertEquals(decision('D', "DTI"), LNUWR020.matrix("FHA", amt, 800, d("50.01"), emp));
        assertEquals(decision('M', "RVW"), LNUWR020.matrix("FHA", amt, 800, d("50.00"), emp));
        assertEquals(decision('A', "AUTO"), LNUWR020.matrix("FHA", amt, 680, d("40.00"), emp));
        assertEquals(decision('M', "RVW"), LNUWR020.matrix("FHA", amt, 679, d("40.00"), emp));
        assertEquals(decision('M', "RVW"), LNUWR020.matrix("FHA", amt, 680, d("40.01"), emp));
    }

    @Test
    void scoreDeclineIsCheckedBeforeDtiDecline() {
        assertEquals(decision('D', "SCORE"), LNUWR020.matrix("CNV", d("300000.00"), 600, d("60.00"), d("5.0")));
    }

    // U8 employment override

    @Test
    void underTwoYearsEmploymentRefersOnlyAnAutoApproval() {
        BigDecimal amt = d("300000.00");
        assertEquals(decision('M', "EMPL"), LNUWR020.matrix("CNV", amt, 790, d("15.93"), d("1.9")));
        assertEquals(decision('A', "AUTO"), LNUWR020.matrix("CNV", amt, 790, d("15.93"), d("2.0")));
        assertEquals(decision('M', "RVW"), LNUWR020.matrix("CNV", amt, 700, d("15.93"), d("0.5")));
        assertEquals(decision('D', "SCORE"), LNUWR020.matrix("CNV", amt, 600, d("15.93"), d("0.5")));
        assertEquals(decision('D', "DTI"), LNUWR020.matrix("CNV", amt, 790, d("50.00"), d("0.5")));
    }

    // U2, U4, U5 on whole records

    private static FixedRecord goldenPriced(String appId) throws IOException {
        return RecordReader.readAll(LEGACY.resolve("golden/PRICED.dat"), Copybooks.LNPRCREC).stream()
                .filter(r -> r.getString("PRC-APP").startsWith(appId))
                .findFirst().orElseThrow();
    }

    private static FixedRecord withScoreDate(FixedRecord priced, long scoreDate) {
        FixedRecord app = FixedRecord.parse(Copybooks.LNAPPREC, priced.getString("PRC-APP"));
        app.set("SCORE-DATE", scoreDate);
        return FixedRecord.parse(Copybooks.LNPRCREC, priced.toLine()).set("PRC-APP", app.toLine());
    }

    @Test
    void staleScoreIsRepulledWhateverTheScore() throws IOException {
        FixedRecord approved = goldenPriced("2611050001");
        assertEquals("AAUTO ", LNUWR020.decide(withScoreDate(approved, 20261006L), RUN_DATE)
                .toLine().substring(10, 16));
        assertEquals("RSTALE", LNUWR020.decide(withScoreDate(approved, 20261005L), RUN_DATE)
                .toLine().substring(10, 16));
    }

    @Test
    void pricingRejectIsDeclinedBeforeTheScoreWindowAndKeepsItsDti() throws IOException {
        FixedRecord reject = withScoreDate(goldenPriced("2611050034"), 20200101L);
        FixedRecord dec = LNUWR020.decide(reject, RUN_DATE);
        assertEquals("D", dec.getString("DEC-CODE"));
        assertEquals("PRICE", dec.getString("DEC-REASON"));
        assertEquals(d("24.00"), dec.getDecimal("DEC-DTI"));
    }

    @Test
    void decisionRecordCarriesThePricedFieldsAndRunDate() throws IOException {
        FixedRecord dec = LNUWR020.decide(goldenPriced("2611050002"), RUN_DATE);
        assertEquals("2611050002AAUTO CNVCNV30000410000000600000002458160219900005125020261105        ",
                dec.toLine());
    }

    // 5.1 control totals on the recorded night

    @Test
    void recordedNightCountsAndSysout() throws IOException {
        Map<String, String> env = new HashMap<>();
        env.put("DD_RUNDATE", LEGACY.resolve("data/input/RUNDATE.dat").toString());
        env.put("DD_PRICED", LEGACY.resolve("golden/PRICED.dat").toString());
        Path decision = out.resolve("DECISION.dat");
        env.put("DD_DECISION", decision.toString());
        ByteArrayOutputStream sysout = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(sysout, true, StandardCharsets.ISO_8859_1)) {
            assertEquals(0, new LNUWR020().run(new Dd(env), ps));
        }
        String expected = Files.readAllLines(LEGACY.resolve("golden/SYSOUT.txt"), StandardCharsets.ISO_8859_1)
                .stream().filter(l -> l.startsWith("LNUWR020 ")).map(l -> l + "\n").collect(Collectors.joining());
        assertEquals(expected, sysout.toString(StandardCharsets.ISO_8859_1));
        assertEquals("LNUWR020 READ     0000060\n"
                + "LNUWR020 APPROVED 0000019\n"
                + "LNUWR020 REVIEW   0000027\n"
                + "LNUWR020 DECLINED 0000012\n"
                + "LNUWR020 REPULL   0000002\n", expected);

        List<FixedRecord> decisions = RecordReader.readAll(decision, Copybooks.LNDECREC);
        Map<String, Long> byCode = decisions.stream()
                .collect(Collectors.groupingBy(r -> r.getString("DEC-CODE"), Collectors.counting()));
        assertEquals(Map.of("A", 19L, "M", 27L, "D", 12L, "R", 2L), byCode);
        List<String> stale = decisions.stream().filter(r -> r.getString("DEC-CODE").equals("R"))
                .map(r -> r.getString("DEC-APP-ID") + " " + r.getString("DEC-REASON")).toList();
        assertEquals(List.of("2611050028 STALE", "2611050029 STALE"), stale);
    }
}
