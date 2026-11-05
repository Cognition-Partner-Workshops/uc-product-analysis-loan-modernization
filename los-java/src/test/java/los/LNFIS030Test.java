package los;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import los.program.LNFIS030;
import los.program.PortStatus;
import los.record.Copybooks;
import los.record.FixedRecord;
import los.record.RecordFormatException;
import los.record.RecordReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LNFIS030Test {
    @TempDir
    Path dir;
    private final ByteArrayOutputStream sysout = new ByteArrayOutputStream();

    private Dd inputs(String date, String priced, String decisions) throws IOException {
        Files.writeString(dir.resolve("date"), date, StandardCharsets.US_ASCII);
        Files.writeString(dir.resolve("priced"), priced, StandardCharsets.US_ASCII);
        Files.writeString(dir.resolve("decisions"), decisions, StandardCharsets.US_ASCII);
        return new Dd(Map.of("DD_RUNDATE", dir.resolve("date").toString(),
                "DD_PRICED", dir.resolve("priced").toString(),
                "DD_DECISION", dir.resolve("decisions").toString(),
                "DD_FISOUT", dir.resolve("fis").toString()));
    }

    private String priced(long id, long tin) {
        FixedRecord app = FixedRecord.blank(Copybooks.LNAPPREC).initialize()
                .set("APP-ID", id).set("BORR-TIN", tin).set("TERM-MONTHS", 360);
        return FixedRecord.blank(Copybooks.LNPRCREC).initialize()
                .set("PRC-APP", app.toLine()).toLine() + "\n";
    }

    private String decision(long id, String code) {
        return FixedRecord.blank(Copybooks.LNDECREC).initialize()
                .set("DEC-APP-ID", id).set("DEC-CODE", code).set("DEC-LOAN-TYPE", "FHA")
                .set("DEC-LOAN-AMT", new BigDecimal("123456.78"))
                .set("DEC-FINAL-RATE", new BigDecimal("6.1250")).toLine() + "\n";
    }

    private List<FixedRecord> run(Dd dd) throws IOException {
        assertEquals(0, new LNFIS030().run(dd, new PrintStream(sysout, true, StandardCharsets.US_ASCII)));
        return RecordReader.readAll(dir.resolve("fis"), Copybooks.FISACCT);
    }

    @Test
    void onlyApprovalsAndPositionalPairing() throws IOException {
        String prices = "";
        String decisions = "";
        String[] codes = {"D", "M", "R", "A", "A"};
        for (int i = 0; i < codes.length; i++) {
            prices += priced(100 + i, 123456780 + i);
            decisions += decision(i == 4 ? 9999999999L : i, codes[i]);
        }
        List<FixedRecord> records = run(inputs("20261105\n", prices, decisions));
        assertEquals(2, records.size());
        assertEquals("040000000003", records.get(0).getString("LOAN-NUMBER"));
        assertEquals("049999999999", records.get(1).getString("LOAN-NUMBER"));
        assertEquals("123456783", records.get(0).getString("BORROWER-SSN"));
        assertEquals("ACCT040000000003123456783FHA000123456780612503602027020120261205"
                + " ".repeat(50) + "\n", records.get(0).toLine() + "\n");
        assertEquals(230, Files.size(dir.resolve("fis")));
        assertEquals("LNFIS030 WRITTEN  0000002\n", sysout.toString(StandardCharsets.US_ASCII));
        assertTrue(PortStatus.isPorted(new LNFIS030()));
    }

    @ParameterizedTest
    @CsvSource({"20260131,20260302,20260501", "20240131,20240301,20240501",
            "20240201,20240302,20240501", "20231215,20240114,20240301",
            "20260901,20261001,20261201", "20261002,20261101,20270101",
            "20261101,20261201,20270201", "20240228,20240329,20240501"})
    void calendarArithmetic(String date, long funding, long payment) throws IOException {
        FixedRecord record = run(inputs(date + "\n", priced(1, 1), decision(1, "A"))).get(0);
        assertEquals(funding, record.getLong("FUNDING-DATE"));
        assertEquals(payment, record.getLong("FIRST-PMT-DATE"));
    }

    @Test
    void emptyDecisionsTruncateOutputAndReportZero() throws IOException {
        Files.writeString(dir.resolve("fis"), "old output");
        assertTrue(run(inputs("20261105\n", "", "")).isEmpty());
        assertEquals(0, Files.size(dir.resolve("fis")));
        assertEquals("LNFIS030 WRITTEN  0000000\n", sysout.toString(StandardCharsets.US_ASCII));
    }

    @Test
    void malformedInputsDoNotReportSuccess() throws IOException {
        Dd emptyDate = inputs("", "", "");
        assertThrows(RecordFormatException.class, () -> run(emptyDate));
        Dd invalidDate = inputs("20260230\n", "", "");
        assertThrows(RecordFormatException.class, () -> run(invalidDate));
        Dd missingPrice = inputs("20261105\n", "", decision(1, "A"));
        assertThrows(RecordFormatException.class, () -> run(missingPrice));
        assertEquals(0, sysout.size());
    }

    @Test
    void isolatedGoldenDatasetAndSysout() throws IOException {
        Path legacy = Path.of(System.getProperty("legacy.los.dir"));
        Dd dd = new Dd(Map.of("DD_RUNDATE", legacy.resolve("data/input/RUNDATE.dat").toString(),
                "DD_PRICED", legacy.resolve("golden/PRICED.dat").toString(),
                "DD_DECISION", legacy.resolve("golden/DECISION.dat").toString(),
                "DD_FISOUT", dir.resolve("fis").toString()));
        run(dd);
        assertArrayEquals(Files.readAllBytes(legacy.resolve("golden/FISACCT.dat")),
                Files.readAllBytes(dir.resolve("fis")));
        String expected = Files.readString(legacy.resolve("golden/SYSOUT.txt")).lines()
                .filter(line -> line.startsWith("LNFIS030 ")).reduce("", (a, b) -> a + b + "\n");
        assertEquals(expected, sysout.toString(StandardCharsets.US_ASCII));
    }
}
