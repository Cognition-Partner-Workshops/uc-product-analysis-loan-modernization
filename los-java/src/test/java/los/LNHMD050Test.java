package los;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import los.record.Copybooks;
import los.record.FixedRecord;
import los.record.LineMode;
import los.record.RecordWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LNHMD050Test {
    private static final String HEADER = "ApplicationID|ActionTaken|ActionDate|LoanType|"
            + "LoanPurpose|LoanAmount|PropertyType|PropertyState|ApplicantRace|ApplicantSex|"
            + "ApplicantIncome|RateSpread\n";

    @TempDir
    Path tmp;

    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

    private int run(Path rates, Path priced, Path decisions) {
        try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.ISO_8859_1);
             PrintStream err = new PrintStream(stderr, true, StandardCharsets.ISO_8859_1)) {
            return Main.run(new String[] {"LNHMD050"}, new Dd(Map.of(
                    "DD_RATESIN", rates.toString(), "DD_PRICED", priced.toString(),
                    "DD_DECISION", decisions.toString(), "DD_HMDAOUT", tmp.resolve("hmda").toString())),
                    out, err);
        }
    }

    private String fixture(List<FixedRecord> rates, List<FixedRecord> prices,
                           List<FixedRecord> decisions) throws IOException {
        write("rates", rates);
        write("priced", prices);
        write("decisions", decisions);
        assertEquals(0, run(tmp.resolve("rates"), tmp.resolve("priced"), tmp.resolve("decisions")),
                stderr::toString);
        assertEquals("", stderr.toString(StandardCharsets.ISO_8859_1));
        return Files.readString(tmp.resolve("hmda"), StandardCharsets.ISO_8859_1);
    }

    private void write(String name, List<FixedRecord> records) throws IOException {
        try (RecordWriter writer = RecordWriter.open(tmp.resolve(name), LineMode.FIXED)) {
            for (FixedRecord record : records) {
                writer.write(record);
            }
        }
    }

    private static FixedRecord price(String amount, String income) {
        FixedRecord app = FixedRecord.blank(Copybooks.LNAPPREC).initialize()
                .set("APP-ID", 999).set("LOAN-TYPE", "CNV").set("LOAN-PURPOSE", "P")
                .set("LOAN-AMT", new BigDecimal(amount)).set("ANNUAL-INCOME", new BigDecimal(income))
                .set("PROP-TYPE", "1").set("PROP-STATE", "CA").set("APPL-RACE", "5").set("APPL-SEX", "2");
        return FixedRecord.blank(Copybooks.LNPRCREC).initialize().set("PRC-APP", app.toLine());
    }

    private static FixedRecord decision(long id, String code, String rate) {
        return FixedRecord.blank(Copybooks.LNDECREC).initialize().set("DEC-APP-ID", id)
                .set("DEC-CODE", code).set("DEC-REASON", "PRICE").set("DEC-DATE", 20261105)
                .set("DEC-FINAL-RATE", new BigDecimal(rate));
    }

    private static FixedRecord rate(String product, int days, String value) {
        return FixedRecord.blank(Copybooks.LNRATE).initialize().set("RATE-PRODUCT", product)
                .set("RATE-LOCK-DAYS", days).set("RATE-BASE", new BigDecimal(value));
    }

    @Test
    void goldenInputsProduceExactBytesAndOnlyTheLegacyControlTotal() throws IOException {
        Path legacy = Path.of(System.getProperty("legacy.los.dir"));
        Path golden = legacy.resolve("golden");
        assertEquals(0, run(legacy.resolve("data/input/RATESHT.dat"),
                golden.resolve("PRICED.dat"), golden.resolve("DECISION.dat")), stderr::toString);
        assertArrayEquals(Files.readAllBytes(golden.resolve("LOS_HMDA_202611.csv")),
                Files.readAllBytes(tmp.resolve("hmda")));
        String expected = Files.readString(golden.resolve("SYSOUT.txt"), StandardCharsets.ISO_8859_1)
                .lines().filter(line -> line.startsWith("LNHMD050 ")).findFirst().orElseThrow() + "\n";
        assertArrayEquals(expected.getBytes(StandardCharsets.ISO_8859_1), stdout.toByteArray());
        assertEquals("LNHMD050 ROWS     0000031\n", stdout.toString(StandardCharsets.ISO_8859_1));
        assertEquals(32, Files.readAllLines(tmp.resolve("hmda")).size());
        assertEquals(0, stderr.size());
    }

    @Test
    void thousandsRoundAmountButTruncateIncomeAndUsePositionalPairing() throws IOException {
        String actual = fixture(List.of(), List.of(price("286499.99", "99999.99"),
                price("286500.00", "100000.00"), price("0", "0")),
                List.of(decision(1, "A", "0"), decision(2, "A", "0"), decision(3, "D", "0")));
        assertEquals(HEADER
                + "0000000001|1|2026-11-05|1|1|286|1|CA|5|2|99|0.000\n"
                + "0000000002|1|2026-11-05|1|1|287|1|CA|5|2|100|0.000\n"
                + "0000000003|3|2026-11-05|1|1|0|1|CA|5|2|0|NA\n", actual);
    }

    @Test
    void spreadRoundsBothSignsAndLastZeroDayAporWins() throws IOException {
        List<FixedRecord> rates = List.of(rate("APOR", 0, "9"), rate("CNV30", 0, "8"),
                rate("APOR", 0, "6.0430"), rate("APOR", 30, "7"));
        String actual = fixture(rates, List.of(price("0", "0"), price("0", "0"),
                price("0", "0"), price("0", "0"), price("0", "0")),
                List.of(decision(1, "A", "6.0434"), decision(2, "A", "6.0435"),
                        decision(3, "A", "6.0425"), decision(4, "A", "6.0426"),
                        decision(5, "A", "5.8750")));
        assertEquals(HEADER
                + "0000000001|1|2026-11-05|1|1|0|1|CA|5|2|0|0.000\n"
                + "0000000002|1|2026-11-05|1|1|0|1|CA|5|2|0|0.001\n"
                + "0000000003|1|2026-11-05|1|1|0|1|CA|5|2|0|-0.001\n"
                + "0000000004|1|2026-11-05|1|1|0|1|CA|5|2|0|0.000\n"
                + "0000000005|1|2026-11-05|1|1|0|1|CA|5|2|0|-0.168\n", actual);
    }

    @Test
    void onlyFinalActionsReportAndFieldsAreRawNotCsvEscaped() throws IOException {
        FixedRecord fha = FixedRecord.parse(Copybooks.LNAPPREC, price("500", "1500").getString("PRC-APP"))
                .set("LOAN-TYPE", "FHA").set("LOAN-PURPOSE", "R").set("PROP-TYPE", "\"")
                .set("PROP-STATE", "|,").set("APPL-RACE", "3").set("APPL-SEX", "1");
        FixedRecord unknown = FixedRecord.parse(Copybooks.LNAPPREC, fha.toLine()).set("LOAN-TYPE", "XYZ");
        String actual = fixture(List.of(rate("APOR", 30, "9")),
                List.of(price("0", "0"), FixedRecord.blank(Copybooks.LNPRCREC).set("PRC-APP", fha.toLine()),
                        price("0", "0"), FixedRecord.blank(Copybooks.LNPRCREC).set("PRC-APP", unknown.toLine())),
                List.of(decision(1, "M", "0"), decision(2, "A", "1.2345"),
                        decision(3, "R", "0"), decision(4, "D", "0")));
        assertEquals(HEADER + "0000000002|1|2026-11-05|2|31|1|\"||,|3|1|1|1.235\n"
                + "0000000004|3|2026-11-05|1|31|1|\"||,|3|1|1|NA\n", actual);
        assertEquals("LNHMD050 ROWS     0000002\n", stdout.toString(StandardCharsets.ISO_8859_1));
    }

    @Test
    void emptyFilesStillWriteHeaderAndZeroRows() throws IOException {
        assertEquals(HEADER, fixture(List.of(), List.of(), List.of()));
        assertEquals("LNHMD050 ROWS     0000000\n", stdout.toString(StandardCharsets.ISO_8859_1));
    }

    @Test
    void malformedReportedAmountFailsWithoutSuccessSysout() throws IOException {
        write("rates", List.of());
        write("priced", List.of(FixedRecord.blank(Copybooks.LNPRCREC)));
        write("decisions", List.of(decision(1, "A", "0")));
        assertEquals(12, run(tmp.resolve("rates"), tmp.resolve("priced"), tmp.resolve("decisions")));
        assertEquals(0, stdout.size());
        assertTrue(stderr.toString(StandardCharsets.ISO_8859_1).startsWith("LNHMD050 ABEND "));
    }
}
