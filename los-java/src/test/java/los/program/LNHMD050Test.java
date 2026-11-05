package los.program;

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
import los.record.LineMode;
import los.record.RecordFormatException;
import los.record.RecordWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LNHMD050Test {
    private static final Path LEGACY = Path.of(System.getProperty("legacy.los.dir", "../legacy-los"));
    private static final String HEADER = "ApplicationID|ActionTaken|ActionDate|LoanType|"
            + "LoanPurpose|LoanAmount|PropertyType|PropertyState|"
            + "ApplicantRace|ApplicantSex|ApplicantIncome|RateSpread\n";

    @TempDir
    Path tmp;

    @Test
    void recordedNightMatchesGoldenBytesAndSysout() throws IOException {
        Path output = tmp.resolve("LOS_HMDA_202611.csv");
        String sysout = run(LEGACY.resolve("data/input/RATESHT.dat"),
                LEGACY.resolve("golden/PRICED.dat"), LEGACY.resolve("golden/DECISION.dat"), output);
        assertEquals(-1L, Files.mismatch(LEGACY.resolve("golden/LOS_HMDA_202611.csv"), output));
        String expected = String.join("\n", Files.readAllLines(LEGACY.resolve("golden/SYSOUT.txt"),
                StandardCharsets.US_ASCII).stream().filter(line -> line.startsWith("LNHMD050 ")).toList()) + "\n";
        assertEquals(expected, sysout);
    }

    @ParameterizedTest
    @CsvSource({"6.0430,-0.168", "5.8745,0.001", "5.8755,-0.001",
            "5.8746,0.000", "5.8754,0.000", "5.8750,0.000", "0,5.875", "16,-10.125"})
    void spreadRoundsAwayFromZeroWithoutPlusSign(String apor, String expected) throws IOException {
        prepare(List.of(rate("APOR", 0, apor)), List.of(price("FHA", "R", "286500", "999.99")),
                List.of(decision(7, "A", "5.8750")));
        execute();
        assertEquals(HEADER + "0000000007|1|2026-11-05|2|31|287|2|NY|5|1|0|" + expected + "\n", output());
    }

    @Test
    void lastZeroDayAporWinsAndOtherProductsAndTermsDoNotMatch() throws IOException {
        prepare(List.of(rate("APOR", 0, "1"), rate("APOR", 0, "5.793"),
                rate("APOR", 30, "9"), rate("CNV30", 0, "8")),
                List.of(price("CNV", "P", "286499.99", "12999.99")), List.of(decision(9, "A", "5.875")));
        execute();
        assertEquals(HEADER + "0000000009|1|2026-11-05|1|1|286|2|NY|5|1|12|0.082\n", output());
    }

    @Test
    void absentAporDefaultsToZero() throws IOException {
        prepare(List.of(rate("APOR", 45, "9")), List.of(price("CNV", "P", "0", "0")),
                List.of(decision(1, "A", "0")));
        execute();
        assertEquals(HEADER + "0000000001|1|2026-11-05|1|1|0|2|NY|5|1|0|0.000\n", output());
    }

    @Test
    void filtersOpenAndUnknownActionsButConsumesPricedInPosition() throws IOException {
        FixedRecord declined = price("???", "?", "500", "1999.99");
        FixedRecord app = FixedRecord.parse(Copybooks.LNAPPREC, declined.getString("PRC-APP"))
                .set("PROP-TYPE", " ").set("PROP-STATE", " X").set("APPL-RACE", " ").set("APPL-SEX", "2");
        declined.set("PRC-APP", app.toLine()).set("PRC-STATUS", "99");
        prepare(List.of(), List.of(price("FHA", "R", "1000", "0"),
                price("FHA", "R", "2000", "0"), price("FHA", "R", "3000", "0"), declined),
                List.of(decision(1, "M", "0"), decision(2, "R", "0"), decision(3, "?", "0"),
                        decision(99, "D", "0").set("DEC-REASON", "PRICE")));
        assertEquals("LNHMD050 ROWS     0000001\n", execute());
        assertEquals(HEADER + "0000000099|3|2026-11-05|1|1|1| | X| |2|1|NA\n", output());
    }

    @Test
    void emptyInputsWriteHeaderAndZeroCount() throws IOException {
        prepare(List.of(), List.of(), List.of());
        assertEquals("LNHMD050 ROWS     0000000\n", execute());
        assertEquals(HEADER, output());
    }

    @Test
    void missingPricedPartnerFailsWithoutSuccessTotals() throws IOException {
        prepare(List.of(), List.of(), List.of(decision(1, "A", "0")));
        assertThrows(IOException.class, this::execute);
    }

    @Test
    void malformedUsedNumericFieldIsRejected() throws IOException {
        FixedRecord bad = FixedRecord.parse(Copybooks.LNRATE, "APOR 000xxxxxx" + " ".repeat(26));
        prepare(List.of(bad), List.of(), List.of());
        assertThrows(RecordFormatException.class, this::execute);
    }

    private void prepare(List<FixedRecord> rates, List<FixedRecord> priced, List<FixedRecord> decisions)
            throws IOException {
        write("rates.dat", rates);
        write("priced.dat", priced);
        write("decisions.dat", decisions);
    }

    private void write(String file, List<FixedRecord> records) throws IOException {
        try (RecordWriter writer = RecordWriter.open(tmp.resolve(file), LineMode.FIXED)) {
            for (FixedRecord record : records) {
                writer.write(record);
            }
        }
    }

    private String execute() throws IOException {
        return run(tmp.resolve("rates.dat"), tmp.resolve("priced.dat"), tmp.resolve("decisions.dat"),
                tmp.resolve("LOS_HMDA_202611.csv"));
    }

    private static String run(Path rates, Path priced, Path decisions, Path output) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream sysout = new PrintStream(bytes, true, StandardCharsets.US_ASCII)) {
            assertEquals(0, new LNHMD050().run(new Dd(Map.of("DD_RATESIN", rates.toString(),
                    "DD_PRICED", priced.toString(), "DD_DECISION", decisions.toString(),
                    "DD_HMDAOUT", output.toString())), sysout));
        }
        return bytes.toString(StandardCharsets.US_ASCII);
    }

    private String output() throws IOException {
        return Files.readString(tmp.resolve("LOS_HMDA_202611.csv"), StandardCharsets.US_ASCII);
    }

    private static FixedRecord rate(String product, int days, String base) {
        return FixedRecord.blank(Copybooks.LNRATE).initialize().set("RATE-PRODUCT", product)
                .set("RATE-LOCK-DAYS", days).set("RATE-BASE", new BigDecimal(base));
    }

    private static FixedRecord price(String type, String purpose, String amount, String income) {
        FixedRecord app = FixedRecord.blank(Copybooks.LNAPPREC).initialize().set("APP-ID", 123)
                .set("LOAN-TYPE", type).set("LOAN-PURPOSE", purpose).set("LOAN-AMT", new BigDecimal(amount))
                .set("ANNUAL-INCOME", new BigDecimal(income)).set("PROP-TYPE", "2").set("PROP-STATE", "NY")
                .set("APPL-RACE", "5").set("APPL-SEX", "1");
        return FixedRecord.blank(Copybooks.LNPRCREC).initialize().set("PRC-APP", app.toLine());
    }

    private static FixedRecord decision(long id, String code, String rate) {
        return FixedRecord.blank(Copybooks.LNDECREC).initialize().set("DEC-APP-ID", id)
                .set("DEC-CODE", code).set("DEC-FINAL-RATE", new BigDecimal(rate)).set("DEC-DATE", 20261105);
    }
}
