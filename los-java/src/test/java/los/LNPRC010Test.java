package los;

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
import los.program.LNPRC010;
import los.record.Copybooks;
import los.record.FixedRecord;
import los.record.LineMode;
import los.record.RecordFormatException;
import los.record.RecordReader;
import los.record.RecordWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LNPRC010Test {
    private static final Path LEGACY = Path.of(System.getProperty("legacy.los.dir", "../legacy-los"));
    @TempDir Path tmp;
    private final ByteArrayOutputStream sysout = new ByteArrayOutputStream();

    private Dd dd(Path apps, Path rates) {
        return new Dd(Map.of("DD_APPSIN", apps.toString(), "DD_RATESIN", rates.toString(),
                "DD_PRICED", tmp.resolve("PRICED.dat").toString()));
    }

    private void run(Dd dd) throws IOException {
        assertEquals(0, new LNPRC010().run(dd, new PrintStream(sysout, true, StandardCharsets.ISO_8859_1)));
    }

    @Test
    void goldenBytesAndSysout() throws IOException {
        run(dd(LEGACY.resolve("data/input/APPS.dat"), LEGACY.resolve("data/input/RATESHT.dat")));
        assertEquals(-1, Files.mismatch(LEGACY.resolve("golden/PRICED.dat"), tmp.resolve("PRICED.dat")));
        assertArrayEquals(Files.readAllBytes(LEGACY.resolve("golden/PRICED.dat")),
                Files.readAllBytes(tmp.resolve("PRICED.dat")));
        String expected = String.join("\n", Files.readAllLines(LEGACY.resolve("golden/SYSOUT.txt"))
                .stream().filter(s -> s.startsWith("LNPRC010 ")).toList()) + "\n";
        assertEquals(expected, sysout.toString(StandardCharsets.ISO_8859_1));
    }

    private static FixedRecord app() {
        return FixedRecord.blank(Copybooks.LNAPPREC).initialize().set("LOAN-TYPE", "CNV")
                .set("TERM-MONTHS", 360).set("CREDIT-SCORE", 720).set("LOCK-DAYS", 30)
                .set("LOAN-AMT", 100000);
    }

    private static FixedRecord rate(String product, String base) {
        return FixedRecord.blank(Copybooks.LNRATE).initialize().set("RATE-PRODUCT", product)
                .set("RATE-LOCK-DAYS", 30).set("RATE-BASE", new BigDecimal(base));
    }

    private FixedRecord price(FixedRecord app, FixedRecord... rates) throws IOException {
        Path apps = tmp.resolve("apps");
        Path sheet = tmp.resolve("rates");
        try (RecordWriter writer = RecordWriter.open(apps, LineMode.FIXED)) {
            writer.write(app);
        }
        try (RecordWriter writer = RecordWriter.open(sheet, LineMode.FIXED)) {
            for (FixedRecord rate : rates) { writer.write(rate); }
        }
        run(dd(apps, sheet));
        FixedRecord result = RecordReader.readAll(tmp.resolve("PRICED.dat"), Copybooks.LNPRCREC).getFirst();
        assertEquals(app.toLine(), result.getString("PRC-APP"));
        assertEquals(201, Files.size(tmp.resolve("PRICED.dat")));
        return result;
    }

    @ParameterizedTest
    @CsvSource({"999,-02500", "760,-02500", "759,+00000", "720,+00000", "719,+05000",
            "680,+05000", "679,+10000", "640,+10000", "639,+17500", "620,+17500", "619,+25000", "0,+25000"})
    void scoreThresholdsAndSeparateSigns(int score, String adjustment) throws IOException {
        assertEquals(adjustment, price(app().set("CREDIT-SCORE", score), rate("CNV30", "6"))
                .getString("PRC-RISK-ADJ"));
    }

    @ParameterizedTest
    @CsvSource({"0,0000", "1,0125", "2,0250", "3,0375", "4,0495", "5,0615", "6,0735", "83,9975", "84,0095", "99,1895"})
    void extensionTableTruncationAndOverflow(int extensions, String fee) throws IOException {
        assertEquals(fee, price(app().set("LOCK-EXTS", extensions), rate("CNV30", "6"))
                .getString("PRC-EXT-FEE-PCT"));
    }

    @ParameterizedTest
    @CsvSource({"6.1234,100000,360,607.51", "0.0001,999999999.99,360,2777819.63",
            "99.9999,999999999.99,180,3343807.03", "0,100000,360,0.00"})
    void paymentTruncatesIntermediatesRoundsCentsAndDropsHighDigits(
            String base, String amount, int term, String payment) throws IOException {
        FixedRecord result = price(app().set("TERM-MONTHS", term).set("LOAN-AMT", new BigDecimal(amount)),
                rate(term == 180 ? "CNV15" : "CNV30", base));
        assertEquals(new BigDecimal(payment), result.getDecimal("PRC-MONTHLY-PMT"));
    }

    @Test
    void unsignedFinalRateStoresMagnitude() throws IOException {
        FixedRecord result = price(app().set("CREDIT-SCORE", 760), rate("CNV30", "0.1"));
        assertEquals(new BigDecimal("0.1500"), result.getDecimal("PRC-FINAL-RATE"));
    }

    @Test
    void feeRoundsHalfUpAndUsesStoredPercent() throws IOException {
        FixedRecord result = price(app().set("LOAN-AMT", 4).set("LOCK-EXTS", 1), rate("CNV30", "6"));
        assertEquals(new BigDecimal("0.01"), result.getDecimal("PRC-EXT-FEE-AMT"));
        result = price(app().set("LOAN-AMT", 100000).set("LOCK-EXTS", 84), rate("CNV30", "6"));
        assertEquals(new BigDecimal("95.00"), result.getDecimal("PRC-EXT-FEE-AMT"));
    }

    @Test
    void productSelectionFirstRateAndIgnoredEffectiveDate() throws IOException {
        for (String product : List.of("CNV30", "CNV15", "FHA30")) {
            FixedRecord result = price(app().set("LOAN-TYPE", product.substring(0, 3))
                    .set("TERM-MONTHS", product.equals("CNV15") ? 180 : 360),
                    rate("APOR", "1"), rate(product, "6.1234"), rate(product, "8"));
            assertEquals(product, result.getString("PRC-PRODUCT"));
            assertEquals(new BigDecimal("6.1234"), result.getDecimal("PRC-BASE-RATE"));
        }
    }

    @Test
    void rejectsRetainAppAndInitializeAllNumericFields() throws IOException {
        for (FixedRecord input : List.of(app().set("LOAN-TYPE", "VA"),
                app().set("LOAN-TYPE", "FHA").set("TERM-MONTHS", 180), app().set("LOCK-DAYS", 90))) {
            FixedRecord result = price(input, rate("CNV30", "6"));
            assertEquals(input.getInt("LOCK-DAYS") == 90 ? "E2" : "E1", result.getString("PRC-STATUS"));
            for (String field : List.of("PRC-BASE-RATE", "PRC-RISK-ADJ", "PRC-FINAL-RATE",
                    "PRC-MONTHLY-PMT", "PRC-EXT-FEE-PCT", "PRC-EXT-FEE-AMT")) {
                assertEquals(0, result.getDecimal(field).signum());
            }
            assertEquals("+00000", result.getString("PRC-RISK-ADJ"));
        }
    }

    @Test
    void emptyInputsAndMissingRates() throws IOException {
        Path empty = Files.createFile(tmp.resolve("empty"));
        run(dd(empty, empty));
        assertEquals(0, Files.size(tmp.resolve("PRICED.dat")));
        assertEquals("LNPRC010 READ     0000000\nLNPRC010 PRICED   0000000\nLNPRC010 REJECTED 0000000\n",
                sysout.toString(StandardCharsets.ISO_8859_1));
        assertEquals("E2", price(app()).getString("PRC-STATUS"));
    }

    @Test
    void ioAndMalformedNumericErrorsPropagate() throws IOException {
        Path empty = Files.createFile(tmp.resolve("empty"));
        assertThrows(IOException.class, () -> run(dd(empty, tmp.resolve("missing"))));
        assertThrows(RecordFormatException.class, () -> price(app().set("LOAN-TYPE", "CNV"),
                FixedRecord.blank(Copybooks.LNRATE).set("RATE-PRODUCT", "CNV30")));
    }
}
