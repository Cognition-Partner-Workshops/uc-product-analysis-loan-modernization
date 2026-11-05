package los.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import los.Dd;
import los.record.Copybooks;
import los.record.FixedRecord;
import los.record.RecordFormatException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LNGL040Test {
    private static final long RUN_DATE = 20261105L;

    private static FixedRecord decision(String code, long appId, String loanType, String product,
            String loanAmt, String feeAmt) {
        return FixedRecord.blank(Copybooks.LNDECREC).initialize()
                .set("DEC-APP-ID", appId)
                .set("DEC-CODE", code)
                .set("DEC-REASON", "AUTO")
                .set("DEC-LOAN-TYPE", loanType)
                .set("DEC-PRODUCT", product)
                .set("DEC-LOAN-AMT", new BigDecimal(loanAmt))
                .set("DEC-EXT-FEE-AMT", new BigDecimal(feeAmt))
                .set("DEC-DATE", RUN_DATE);
    }

    private static List<String> lines(FixedRecord dec) {
        return LNGL040.postingLines(dec, RUN_DATE).stream().map(FixedRecord::toLine).toList();
    }

    private static Path legacy() {
        return Path.of(System.getProperty("legacy.los.dir"));
    }

    // --- GL line generation per decision ---

    @ParameterizedTest
    @ValueSource(strings = {"M", "D", "R", " ", "a"})
    void onlyAutoApprovedDecisionsPost(String code) {
        assertEquals(List.of(), lines(decision(code, 2611050001L, "CNV", "CNV30", "320000.00", "512.50")));
    }

    @Test
    void approvedLoanWithoutFeePostsFundingPairOnly() {
        assertEquals(List.of(
                "20261105140100000000DR0000000320000000042611050001LOAN FUNDING CNV30            10010",
                "20261105210500000000CR0000000320000000042611050001LOAN FUNDING CNV30            10010"),
                lines(decision("A", 2611050001L, "CNV", "CNV30", "320000.00", "0")));
    }

    @Test
    void approvedLoanWithFeePostsFundingThenFeePair() {
        assertEquals(List.of(
                "20261105140100000000DR0000000410000000042611050002LOAN FUNDING CNV30            10010",
                "20261105210500000000CR0000000410000000042611050002LOAN FUNDING CNV30            10010",
                "20261105120300000000DR0000000000512500042611050002RATE LOCK EXTENSION FEE       10010",
                "20261105410200000000CR0000000000512500042611050002RATE LOCK EXTENSION FEE       10010"),
                lines(decision("A", 2611050002L, "CNV", "CNV30", "410000.00", "512.50")));
    }

    @Test
    void smallestFeeStillPostsTheFeePair() {
        List<String> out = lines(decision("A", 1L, "CNV", "CNV15", "100000.00", "0.01"));
        assertEquals(4, out.size());
        assertEquals("000000000000001", out.get(2).substring(22, 37));
        assertEquals("000000000000001", out.get(3).substring(22, 37));
    }

    @Test
    void zeroFeePostsNoFeePair() {
        assertEquals(2, lines(decision("A", 1L, "CNV", "CNV15", "100000.00", "0.00")).size());
    }

    // --- loan number = 040000000000 + app id, in 9(13) ---

    @Test
    void loanNumberIsBasePlusAppId() {
        assertEquals(40_000_000_000L, LNGL040.loanNumber(0));
        assertEquals(42_611_050_001L, LNGL040.loanNumber(2_611_050_001L));
        assertEquals(49_999_999_999L, LNGL040.loanNumber(9_999_999_999L));
    }

    @Test
    void loanNumberIsWrittenAsThirteenDigitsLeading004() {
        assertEquals("0040000000000", lines(decision("A", 0L, "CNV", "CNV30", "1.00", "0")).get(0).substring(37, 50));
        assertEquals("0049999999999",
                lines(decision("A", 9_999_999_999L, "CNV", "CNV30", "1.00", "0")).get(0).substring(37, 50));
    }

    // --- cost center ---

    @Test
    void costCenterIs10020OnlyForFha() {
        assertEquals(10020, LNGL040.costCenter("FHA"));
        assertEquals(10010, LNGL040.costCenter("CNV"));
        assertEquals(10010, LNGL040.costCenter("fha"));
        assertEquals(10010, LNGL040.costCenter("   "));
        List<String> fha = lines(decision("A", 7L, "FHA", "FHA30", "250000.00", "100.00"));
        fha.forEach(l -> assertEquals("10020", l.substring(80, 85)));
    }

    // --- debit/credit legs ---

    @Test
    void debitAndCreditLegsUseTheirAccountsAndShareAmountAndDescription() {
        List<FixedRecord> out = LNGL040.postingLines(
                decision("A", 3L, "CNV", "CNV30", "123456.78", "99.99"), RUN_DATE);
        assertEquals(List.of("140100000000", "210500000000", "120300000000", "410200000000"),
                out.stream().map(r -> r.getString("GL-ACCOUNT")).toList());
        assertEquals(List.of("DR", "CR", "DR", "CR"), out.stream().map(r -> r.getString("GL-DRCR")).toList());
        assertEquals(out.get(0).getDecimal("GL-AMOUNT"), out.get(1).getDecimal("GL-AMOUNT"));
        assertEquals(out.get(2).getDecimal("GL-AMOUNT"), out.get(3).getDecimal("GL-AMOUNT"));
        assertEquals(new BigDecimal("123456.78"), out.get(0).getDecimal("GL-AMOUNT"));
        assertEquals(new BigDecimal("99.99"), out.get(2).getDecimal("GL-AMOUNT"));
        assertEquals(out.get(0).getString("GL-DESC"), out.get(1).getString("GL-DESC"));
        out.forEach(r -> assertEquals(RUN_DATE, r.getLong("GL-POST-DATE")));
    }

    @Test
    void fundingDescriptionIsProductPaddedToThirtyEvenWhenProductIsBlank() {
        FixedRecord dr = LNGL040.postingLines(decision("A", 3L, "CNV", "     ", "1.00", "0"), RUN_DATE).get(0);
        assertEquals("LOAN FUNDING" + " ".repeat(18), dr.getString("GL-DESC"));
        FixedRecord fee = LNGL040.postingLines(decision("A", 3L, "CNV", "CNV30", "1.00", "1.00"), RUN_DATE).get(2);
        assertEquals("RATE LOCK EXTENSION FEE       ", fee.getString("GL-DESC"));
    }

    // --- amount formatting: GL-AMOUNT is unsigned 9(13)V99; the leg, not a sign, carries direction ---

    @Test
    void amountIsFifteenUnsignedZonedDigitsWithImpliedDecimals() {
        assertEquals("000000000000100", lines(decision("A", 1L, "CNV", "CNV30", "1.00", "0")).get(1).substring(22, 37));
        assertEquals("000099999999999",
                lines(decision("A", 1L, "CNV", "CNV30", "999999999.99", "9999999.99")).get(1).substring(22, 37));
        assertEquals("000000999999999",
                lines(decision("A", 1L, "CNV", "CNV30", "999999999.99", "9999999.99")).get(3).substring(22, 37));
        for (String line : lines(decision("A", 1L, "CNV", "CNV30", "0.01", "0.01"))) {
            assertEquals(85, line.length());
            assertTrue(line.substring(22, 37).chars().allMatch(Character::isDigit), line);
        }
    }

    // --- control totals and their edited-numeric layout ---

    @Test
    void editedTotalIsSixteenCharactersZ12NineDotNineNine() {
        assertEquals("            0.00", LNGL040.editTotal(new BigDecimal("0.00")));
        assertEquals("            0.05", LNGL040.editTotal(new BigDecimal("0.05")));
        assertEquals("            1.00", LNGL040.editTotal(new BigDecimal("1.00")));
        assertEquals("      7134818.75", LNGL040.editTotal(new BigDecimal("7134818.75")));
        assertEquals("9999999999999.99", LNGL040.editTotal(new BigDecimal("9999999999999.99")));
    }

    @Test
    void sysoutLinesForAnEmptyNight() {
        assertEquals(List.of(
                "LNGL040  LINES    0000000",
                "LNGL040  TOTAL DR             0.00",
                "LNGL040  TOTAL CR             0.00"), new LNGL040.Totals().sysoutLines());
    }

    @Test
    void totalsCountEveryLineAndSplitByLeg() {
        LNGL040.Totals t = new LNGL040.Totals();
        LNGL040.postingLines(decision("A", 1L, "CNV", "CNV30", "320000.00", "512.50"), RUN_DATE).forEach(t::add);
        LNGL040.postingLines(decision("A", 2L, "FHA", "FHA30", "100000.01", "0"), RUN_DATE).forEach(t::add);
        assertEquals(6, t.lines());
        assertEquals(new BigDecimal("420512.51"), t.debit());
        assertEquals(new BigDecimal("420512.51"), t.credit());
        assertTrue(t.balanced());
        assertEquals(List.of(
                "LNGL040  LINES    0000006",
                "LNGL040  TOTAL DR        420512.51",
                "LNGL040  TOTAL CR        420512.51"), t.sysoutLines());
    }

    @Test
    void unbalancedTotalsPrintOutOfBalance() {
        LNGL040.Totals t = new LNGL040.Totals();
        t.add(LNGL040.postingLines(decision("A", 1L, "CNV", "CNV30", "10.00", "0"), RUN_DATE).get(0));
        assertFalse(t.balanced());
        assertEquals(List.of(
                "LNGL040  LINES    0000001",
                "LNGL040  TOTAL DR            10.00",
                "LNGL040  TOTAL CR             0.00",
                "LNGL040  OUT OF BALANCE"), t.sysoutLines());
    }

    @Test
    void totalsKeepLowOrderDigitsOnOverflowLikeGnuCobolAdd() {
        // Verified with cobc 3.1.2: ADD without ON SIZE ERROR into 9(2) holding 99 leaves 00.
        LNGL040.Totals t = new LNGL040.Totals();
        FixedRecord big = LNGL040.postingLines(
                decision("A", 1L, "CNV", "CNV30", "999999999.99", "0"), RUN_DATE).get(0);
        for (int i = 0; i < 10_001; i++) {
            t.add(big);
        }
        // 10,001 x 999,999,999.99 = 10,000,999,999,899.99 -> 13 integer digits kept: 0,000,999,999,899.99
        assertEquals(new BigDecimal("999999899.99"), t.debit());
        assertEquals("LNGL040  TOTAL DR     999999899.99", t.sysoutLines().get(1));
    }

    // --- program run ---

    private static int run(Path tmp, String decisions, ByteArrayOutputStream sysout) throws IOException {
        Path rundate = tmp.resolve("RUNDATE.dat");
        Path dec = tmp.resolve("DECISION.dat");
        Files.writeString(rundate, "20261105" + " ".repeat(72) + "\n", StandardCharsets.ISO_8859_1);
        Files.writeString(dec, decisions, StandardCharsets.ISO_8859_1);
        Dd dd = new Dd(Map.of("DD_RUNDATE", rundate.toString(), "DD_DECISION", dec.toString(),
                "DD_GLOUT", tmp.resolve("GL.dat").toString()));
        return new LNGL040().run(dd, new PrintStream(sysout, true, StandardCharsets.ISO_8859_1));
    }

    @Test
    void emptyDecisionFileWritesEmptyGlAndZeroTotals(@TempDir Path tmp) throws IOException {
        ByteArrayOutputStream sysout = new ByteArrayOutputStream();
        assertEquals(0, run(tmp, "", sysout));
        assertEquals(0, Files.size(tmp.resolve("GL.dat")));
        assertEquals("LNGL040  LINES    0000000\nLNGL040  TOTAL DR             0.00\n"
                + "LNGL040  TOTAL CR             0.00\n", sysout.toString(StandardCharsets.ISO_8859_1));
    }

    @Test
    void malformedDecisionAbends(@TempDir Path tmp) {
        assertThrows(RecordFormatException.class, () -> run(tmp, "X".repeat(81) + "\n", new ByteArrayOutputStream()));
    }

    @Test
    void emptyRunDateAbends(@TempDir Path tmp) throws IOException {
        Path rundate = tmp.resolve("RUNDATE.dat");
        Files.writeString(rundate, "");
        assertThrows(RecordFormatException.class, () -> LNGL040.readRunDate(rundate));
    }

    @Test
    void goldenNightSysoutMatchesLegacyControlTotals(@TempDir Path tmp) throws IOException {
        Path golden = legacy().resolve("golden");
        Dd dd = new Dd(Map.of(
                "DD_RUNDATE", legacy().resolve("data/input/RUNDATE.dat").toString(),
                "DD_DECISION", golden.resolve("DECISION.dat").toString(),
                "DD_GLOUT", tmp.resolve("LOS_GL_20261105.dat").toString()));
        ByteArrayOutputStream sysout = new ByteArrayOutputStream();
        assertEquals(0, new LNGL040().run(dd, new PrintStream(sysout, true, StandardCharsets.ISO_8859_1)));
        String expected = Files.readAllLines(golden.resolve("SYSOUT.txt"), StandardCharsets.ISO_8859_1).stream()
                .filter(l -> l.startsWith("LNGL040 ")).map(l -> l + "\n").collect(Collectors.joining());
        assertEquals("LNGL040  LINES    0000054\nLNGL040  TOTAL DR       7134818.75\n"
                + "LNGL040  TOTAL CR       7134818.75\n", expected);
        assertEquals(expected, sysout.toString(StandardCharsets.ISO_8859_1));
    }
}
