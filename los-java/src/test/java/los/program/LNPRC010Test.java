package los.program;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import los.Dd;
import los.record.Copybooks;
import los.record.FixedRecord;
import los.record.RecordFormatException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LNPRC010Test {
    @TempDir
    Path out;

    private static FixedRecord app(String type, int term) {
        return FixedRecord.blank(Copybooks.LNAPPREC).initialize().set("LOAN-TYPE", type)
                .set("TERM-MONTHS", term).set("LOCK-DAYS", 30).set("CREDIT-SCORE", 720)
                .set("LOAN-AMT", new BigDecimal("100000.00"));
    }

    private static FixedRecord rate(String product, int lock, String base) {
        return FixedRecord.blank(Copybooks.LNRATE).initialize().set("RATE-PRODUCT", product)
                .set("RATE-LOCK-DAYS", lock).set("RATE-BASE", new BigDecimal(base));
    }

    @ParameterizedTest
    @CsvSource({"CNV,360,CNV30,6.0000", "CNV,180,CNV15,5.0000", "FHA,360,FHA30,7.0000"})
    void productAndFirstMatchingLockRate(String type, int term, String product, String base) {
        var rates = List.of(rate("CNV30", 60, "9"), rate("CNV30", 30, "6"),
                rate("CNV15", 30, "5"), rate("FHA30", 30, "7"), rate(product, 30, "8"));
        FixedRecord result = LNPRC010.price(app(type, term), rates);
        assertEquals(product, result.getString("PRC-PRODUCT"));
        assertEquals(new BigDecimal(base), result.getDecimal("PRC-BASE-RATE"));
        assertEquals("00", result.getString("PRC-STATUS"));
    }

    @ParameterizedTest
    @CsvSource({"0,2.50", "579,2.50", "580,2.50", "619,2.50", "620,1.75", "639,1.75",
            "640,1.00", "679,1.00", "680,0.50", "719,0.50", "720,0.00", "759,0.00",
            "760,-0.25", "999,-0.25"})
    void riskTierBoundaries(int score, String adjustment) {
        assertEquals(new BigDecimal(adjustment), LNPRC010.riskAdjustment(score));
        for (String type : List.of("CNV", "FHA")) {
            FixedRecord result = LNPRC010.price(app(type, 360).set("CREDIT-SCORE", score),
                    List.of(rate(type.equals("CNV") ? "CNV30" : "FHA30", 30, "6")));
            assertEquals(new BigDecimal(adjustment).setScale(4), result.getDecimal("PRC-RISK-ADJ"));
            assertEquals(new BigDecimal("6").add(new BigDecimal(adjustment)).setScale(4),
                    result.getDecimal("PRC-FINAL-RATE"));
        }
    }

    @Test
    void truncatesMonthlyRateAndFactorBeforeRoundingPayment() {
        assertEquals(new BigDecimal("0.005083333333"), LNPRC010.monthlyRate(new BigDecimal("6.1")));
        assertEquals(new BigDecimal("0.005166666666"), LNPRC010.monthlyRate(new BigDecimal("6.2")));
        assertEquals(new BigDecimal("1.010192506943"),
                LNPRC010.factor(new BigDecimal("0.005083333333"), 2));
        FixedRecord result = LNPRC010.price(app("CNV", 360), List.of(rate("CNV30", 30, "6")));
        assertEquals(new BigDecimal("599.55"), result.getDecimal("PRC-MONTHLY-PMT"));
    }

    @ParameterizedTest
    @CsvSource({"0,0.000", "1,0.125", "2,0.250", "3,0.375", "4,0.495", "6,0.735",
            "83,9.975", "84,0.095", "99,1.895"})
    void extensionTruncationAndHighDigitLoss(int extensions, String percent) {
        assertEquals(0, new BigDecimal(percent).compareTo(LNPRC010.extensionPercent(extensions)));
        FixedRecord result = LNPRC010.price(app("CNV", 360).set("LOCK-EXTS", extensions)
                .set("LOAN-AMT", new BigDecimal("100001")), List.of(rate("CNV30", 30, "6")));
        assertEquals(new BigDecimal(percent), result.getDecimal("PRC-EXT-FEE-PCT"));
        assertEquals(new BigDecimal("100001").multiply(new BigDecimal(percent)).movePointLeft(2)
                .setScale(2, java.math.RoundingMode.HALF_UP), result.getDecimal("PRC-EXT-FEE-AMT"));
    }

    @ParameterizedTest
    @CsvSource({"FHA,180,30,E1", "XYZ,360,30,E1", "CNV,359,30,E1", "CNV,360,90,E2"})
    void rejectsKeepInputAndZeroPricing(String type, int term, int lock, String status) {
        FixedRecord input = app(type, term).set("LOCK-DAYS", lock).set("CREDIT-SCORE", 0);
        FixedRecord result = LNPRC010.price(input, List.of(rate("CNV30", 30, "6")));
        assertEquals(input.toLine(), result.getString("PRC-APP"));
        assertEquals(status, result.getString("PRC-STATUS"));
        assertEquals(status.equals("E1") ? "     " : "CNV30", result.getString("PRC-PRODUCT"));
        assertEquals("+00000", result.getString("PRC-RISK-ADJ"));
        for (String field : List.of("PRC-BASE-RATE", "PRC-FINAL-RATE", "PRC-MONTHLY-PMT",
                "PRC-EXT-FEE-PCT", "PRC-EXT-FEE-AMT")) {
            assertEquals(0, result.getDecimal(field).signum());
        }
        assertEquals("   ", result.toLine().substring(197));
    }

    private Dd dd(Path apps, Path rates) {
        return new Dd(Map.of("DD_APPSIN", apps.toString(), "DD_RATESIN", rates.toString(),
                "DD_PRICED", out.resolve("PRICED.dat").toString()));
    }

    @Test
    void recordedNightBytesAndControlTotals() throws IOException {
        Path legacy = Path.of(System.getProperty("legacy.los.dir"));
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        assertEquals(0, new LNPRC010().run(dd(legacy.resolve("data/input/APPS.dat"),
                legacy.resolve("data/input/RATESHT.dat")), new PrintStream(log)));
        assertArrayEquals(Files.readAllBytes(legacy.resolve("golden/PRICED.dat")),
                Files.readAllBytes(out.resolve("PRICED.dat")));
        String expected = String.join("\n", Files.readAllLines(legacy.resolve("golden/SYSOUT.txt"))
                .stream().filter(s -> s.startsWith("LNPRC010 ")).toList()) + "\n";
        assertEquals(expected, log.toString(StandardCharsets.UTF_8));
        assertEquals("LNPRC010 READ     0000060\nLNPRC010 PRICED   0000058\n"
                + "LNPRC010 REJECTED 0000002\n", expected);
    }

    @Test
    void emptyFilesAndMissingRates() throws IOException {
        Path apps = Files.createFile(out.resolve("apps"));
        Path rates = Files.createFile(out.resolve("rates"));
        ByteArrayOutputStream log = new ByteArrayOutputStream();
        assertEquals(0, new LNPRC010().run(dd(apps, rates), new PrintStream(log)));
        assertEquals(0, Files.size(out.resolve("PRICED.dat")));
        assertEquals("LNPRC010 READ     0000000\nLNPRC010 PRICED   0000000\n"
                + "LNPRC010 REJECTED 0000000\n", log.toString(StandardCharsets.UTF_8));
        assertEquals("E2", LNPRC010.price(app("CNV", 360), List.of()).getString("PRC-STATUS"));
        assertThrows(IOException.class, () -> new LNPRC010().run(dd(apps, out.resolve("missing")),
                new PrintStream(log)));
        Files.writeString(apps, "x".repeat(151) + "\n");
        assertThrows(RecordFormatException.class, () -> new LNPRC010().run(dd(apps, rates),
                new PrintStream(log)));
    }
}
