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
import java.util.Locale;
import java.util.Map;
import los.Dd;
import los.record.Copybooks;
import los.record.FixedRecord;
import los.record.RecordFormatException;
import los.record.RecordReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LNGL040Test {

    static final Path LEGACY = Path.of(System.getProperty("legacy.los.dir", "../legacy-los"));
    private static final String RUNDATE = "20261105" + " ".repeat(72) + "\n";

    private final ByteArrayOutputStream sysout = new ByteArrayOutputStream();

    private int run(Path runDate, Path decision, Path gl) throws IOException {
        Dd dd = new Dd(Map.of("DD_RUNDATE", runDate.toString(), "DD_DECISION", decision.toString(),
                "DD_GLOUT", gl.toString()));
        return new LNGL040().run(dd, new PrintStream(sysout, true, StandardCharsets.ISO_8859_1));
    }

    private List<String> sysoutLines() {
        return sysout.toString(StandardCharsets.ISO_8859_1).lines().toList();
    }

    @Test
    void recordedNightMatchesGoldenGlAndSysoutByteForByte(@TempDir Path tmp) throws IOException {
        Path gl = tmp.resolve("LOS_GL_20261105.dat");
        assertEquals(0, run(LEGACY.resolve("data/input/RUNDATE.dat"), LEGACY.resolve("golden/DECISION.dat"), gl));

        Path golden = LEGACY.resolve("golden/LOS_GL_20261105.dat");
        assertEquals(-1L, Files.mismatch(golden, gl), "first differing byte offset");

        List<String> expected = Files.readAllLines(LEGACY.resolve("golden/SYSOUT.txt"), StandardCharsets.ISO_8859_1)
                .stream().filter(l -> l.startsWith("LNGL040 ")).toList();
        assertEquals(3, expected.size());
        assertEquals(expected, sysoutLines());
    }

    private static String decision(long appId, char code, String loanType, String product, String loanAmt,
            String fee) {
        FixedRecord r = FixedRecord.blank(Copybooks.LNDECREC).initialize();
        r.set("DEC-APP-ID", appId);
        r.set("DEC-CODE", String.valueOf(code));
        r.set("DEC-LOAN-TYPE", loanType);
        r.set("DEC-PRODUCT", product);
        r.set("DEC-LOAN-AMT", new BigDecimal(loanAmt));
        r.set("DEC-EXT-FEE-AMT", new BigDecimal(fee));
        return r.toLine() + "\n";
    }

    private List<FixedRecord> runOn(Path tmp, String... decisions) throws IOException {
        Path rd = Files.writeString(tmp.resolve("RUNDATE.dat"), RUNDATE, StandardCharsets.ISO_8859_1);
        Path dec = Files.writeString(tmp.resolve("DECISION.dat"), String.join("", decisions),
                StandardCharsets.ISO_8859_1);
        Path gl = tmp.resolve("GL.dat");
        assertEquals(0, run(rd, dec, gl));
        return RecordReader.readAll(gl, Copybooks.LNGLREC);
    }

    @Test
    void onlyAutoApprovedLoansPost(@TempDir Path tmp) throws IOException {
        List<FixedRecord> lines = runOn(tmp,
                decision(1, 'M', "CNV", "CNV30", "100000.00", "0"),
                decision(2, 'D', "CNV", "CNV30", "100000.00", "0"),
                decision(3, 'R', "CNV", "CNV30", "100000.00", "0"),
                decision(4, 'a', "CNV", "CNV30", "100000.00", "0"));
        assertEquals(List.of(), lines);
        assertEquals(List.of("LNGL040  LINES    0000000", "LNGL040  TOTAL DR             0.00",
                "LNGL040  TOTAL CR             0.00"), sysoutLines());
    }

    @Test
    void fundingPairAndFeePairOnlyWhenFeeAboveZero(@TempDir Path tmp) throws IOException {
        List<FixedRecord> lines = runOn(tmp,
                decision(1000000001L, 'A', "CNV", "CNV30", "350000.00", "0"),
                decision(1000000002L, 'A', "FHA", "FHA30", "200000.50", "0.01"));
        assertEquals(6, lines.size());
        assertEquals(List.of("140100000000DR", "210500000000CR", "140100000000DR", "210500000000CR",
                "120300000000DR", "410200000000CR"),
                lines.stream().map(l -> l.getString("GL-ACCOUNT") + l.getString("GL-DRCR")).toList());
        FixedRecord first = lines.get(0);
        assertEquals("20261105", first.getString("GL-POST-DATE"));
        assertEquals("0041000000001", first.getString("GL-LOAN-NUMBER"));
        assertEquals("10010", first.getString("GL-COST-CENTER"));
        assertEquals("LOAN FUNDING CNV30" + " ".repeat(12), first.getString("GL-DESC"));
        assertEquals("000000035000000", first.getString("GL-AMOUNT"));
        assertEquals(first.getString("GL-DESC"), lines.get(1).getString("GL-DESC"));
        FixedRecord fee = lines.get(4);
        assertEquals("10020", fee.getString("GL-COST-CENTER"));
        assertEquals("0041000000002", fee.getString("GL-LOAN-NUMBER"));
        assertEquals("RATE LOCK EXTENSION FEE" + " ".repeat(7), fee.getString("GL-DESC"));
        assertEquals("000000000000001", fee.getString("GL-AMOUNT"));
        assertEquals(List.of("LNGL040  LINES    0000006", "LNGL040  TOTAL DR        550000.51",
                "LNGL040  TOTAL CR        550000.51"), sysoutLines());
    }

    @Test
    void blankProductKeepsTrailingSpaceInDescription(@TempDir Path tmp) throws IOException {
        List<FixedRecord> lines = runOn(tmp, decision(5, 'A', "VA ", "     ", "1.00", "0"));
        assertEquals("LOAN FUNDING" + " ".repeat(18), lines.get(0).getString("GL-DESC"));
        assertEquals("10010", lines.get(0).getString("GL-COST-CENTER"));
    }

    @Test
    void recordsAreFixed85BytesWithLfOnly(@TempDir Path tmp) throws IOException {
        runOn(tmp, decision(7, 'A', "CNV", "CNV15", "12.34", "5.00"));
        byte[] bytes = Files.readAllBytes(tmp.resolve("GL.dat"));
        assertEquals(4 * 86, bytes.length);
        for (int i = 85; i < bytes.length; i += 86) {
            assertEquals('\n', bytes[i]);
        }
    }

    @Test
    void sysoutIsAsciiDigitsAndLfUnderNonLatinDefaultLocale(@TempDir Path tmp) throws IOException {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"));
            runOn(tmp, decision(8, 'A', "CNV", "CNV30", "1234567.89", "0"));
        } finally {
            Locale.setDefault(saved);
        }
        assertEquals("LNGL040  LINES    0000002\nLNGL040  TOTAL DR       1234567.89\n"
                + "LNGL040  TOTAL CR       1234567.89\n", sysout.toString(StandardCharsets.ISO_8859_1));
        assertEquals("            1.00", LNGL040.edit(new BigDecimal("1.00")));
    }

    @Test
    void loanNumberIsThirteenDigitsWithLeadingZeroes() {
        assertEquals(49999999999L, LNGL040.loanNumber(9999999999L));
        assertEquals(40000000000L, LNGL040.loanNumber(0));
    }

    @Test
    void costCenterIsFhaOnlyForExactFha() {
        assertEquals(10020, LNGL040.costCenter("FHA"));
        assertEquals(10010, LNGL040.costCenter("fha"));
        assertEquals(10010, LNGL040.costCenter("CNV"));
    }

    @Test
    void totalsAreEditedZ12Nine99AndWrapAt13IntegerDigits() {
        assertEquals("            0.00", LNGL040.edit(new BigDecimal("0.00")));
        assertEquals("            0.05", LNGL040.edit(new BigDecimal("0.05")));
        assertEquals("9999999999999.99", LNGL040.edit(new BigDecimal("9999999999999.99")));
        assertEquals(new BigDecimal("0.01"),
                LNGL040.addTotal(new BigDecimal("9999999999999.99"), new BigDecimal("0.02")));
    }

    @Test
    void emptyRunDateIsRejected(@TempDir Path tmp) throws IOException {
        Path empty = Files.createFile(tmp.resolve("empty"));
        assertThrows(RecordFormatException.class, () -> run(empty, empty, tmp.resolve("gl")));
    }
}
