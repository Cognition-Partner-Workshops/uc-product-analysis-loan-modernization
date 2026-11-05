package los.program;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import los.Dd;
import los.record.Copybooks;
import los.record.FixedRecord;
import los.record.LineMode;
import los.record.RecordReader;
import los.record.RecordWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LNFIS030Test {

    static final Path LEGACY = Path.of(System.getProperty("legacy.los.dir", "../legacy-los"));

    private static String run(Path rundate, Path priced, Path decision, Path out) throws IOException {
        Dd dd = new Dd(Map.of(
                "DD_RUNDATE", rundate.toString(),
                "DD_PRICED", priced.toString(),
                "DD_DECISION", decision.toString(),
                "DD_FISOUT", out.toString()));
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (PrintStream sysout = new PrintStream(buf, true, StandardCharsets.ISO_8859_1)) {
            assertEquals(0, new LNFIS030().run(dd, sysout));
        }
        return buf.toString(StandardCharsets.ISO_8859_1);
    }

    @Test
    void recordedNightMatchesGoldenFisacctAndSysout(@TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("FISACCT.dat");
        String sysout = run(LEGACY.resolve("data/input/RUNDATE.dat"), LEGACY.resolve("golden/PRICED.dat"),
                LEGACY.resolve("golden/DECISION.dat"), out);

        assertArrayEquals(Files.readAllBytes(LEGACY.resolve("golden/FISACCT.dat")), Files.readAllBytes(out));
        assertEquals(-1L, Files.mismatch(LEGACY.resolve("golden/FISACCT.dat"), out));
        List<String> golden = Files.readAllLines(LEGACY.resolve("golden/SYSOUT.txt"), StandardCharsets.ISO_8859_1)
                .stream().filter(l -> l.startsWith("LNFIS030 ")).toList();
        assertEquals(golden, sysout.lines().toList());
        assertEquals("LNFIS030 WRITTEN  0000019\n", sysout);
    }

    @Test
    void outputIsLocaleIndependent(@TempDir Path tmp) throws IOException {
        Locale saved = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"));
        try {
            assertEquals(20261205L, LNFIS030.fundingDate(20261105L));
            recordedNightMatchesGoldenFisacctAndSysout(tmp);
        } finally {
            Locale.setDefault(saved);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "20261105, 20261205, 20270201",
        "20261031, 20261130, 20270101",
        "20261001, 20261031, 20261201",
        "20261201, 20261231, 20270201",
        "20240130, 20240229, 20240401",
        "20230130, 20230301, 20230501",
        "20251215, 20260114, 20260301"
    })
    void fundingIsRunPlus30AndFirstPaymentIsDay1TwoMonthsLater(long run, long fund, long pmt) {
        assertEquals(fund, LNFIS030.fundingDate(run));
        assertEquals(pmt, LNFIS030.firstPaymentDate(fund));
    }

    @Test
    void lockstepPairsByPositionAndOnlyApprovedProduceMessages(@TempDir Path tmp) throws IOException {
        List<FixedRecord> priced = RecordReader.readAll(LEGACY.resolve("golden/PRICED.dat"), Copybooks.LNPRCREC);
        List<FixedRecord> decisions = RecordReader.readAll(LEGACY.resolve("golden/DECISION.dat"), Copybooks.LNDECREC);
        FixedRecord prc = priced.get(0);
        FixedRecord app = FixedRecord.parse(Copybooks.LNAPPREC, prc.getString("PRC-APP"));

        FixedRecord approved = FixedRecord.parse(Copybooks.LNDECREC, decisions.get(0).toLine())
                .set("DEC-CODE", "A").set("DEC-APP-ID", 9_999_999_999L).set("DEC-LOAN-TYPE", "FHA");
        FixedRecord review = FixedRecord.parse(Copybooks.LNDECREC, approved.toLine()).set("DEC-CODE", "R");

        Path rd = tmp.resolve("RUNDATE.dat");
        Files.writeString(rd, "20261215" + " ".repeat(72) + "\n", StandardCharsets.ISO_8859_1);
        Path p = write(tmp.resolve("PRICED.dat"), List.of(prc, priced.get(1)));
        Path d = write(tmp.resolve("DECISION.dat"), List.of(review, approved));
        Path out = tmp.resolve("FISACCT.dat");

        assertEquals("LNFIS030 WRITTEN  0000001\n", run(rd, p, d, out));
        List<FixedRecord> msgs = RecordReader.readAll(out, Copybooks.FISACCT);
        assertEquals(1, msgs.size());
        FixedRecord m = msgs.get(0);
        FixedRecord app2 = FixedRecord.parse(Copybooks.LNAPPREC, priced.get(1).getString("PRC-APP"));
        assertEquals("ACCT", m.getString("MSG-TYPE"));
        assertEquals("049999999999", m.getString("LOAN-NUMBER"));
        assertEquals(app2.getString("BORR-TIN"), m.getString("BORROWER-SSN"));
        assertEquals(app2.getString("TERM-MONTHS"), m.getString("FIS-TERM-MONTHS"));
        assertEquals("FHA", m.getString("FIS-LOAN-TYPE"));
        assertEquals(approved.getString("DEC-LOAN-AMT"), m.getString("FIS-LOAN-AMOUNT"));
        assertEquals(approved.getString("DEC-FINAL-RATE"), m.getString("INTEREST-RATE"));
        assertEquals(20270114L, m.getLong("FUNDING-DATE"));
        assertEquals(20270301L, m.getLong("FIRST-PMT-DATE"));
        assertEquals(" ".repeat(50), m.toLine().substring(64));
        assertEquals(115, Files.size(out));
        assertEquals('\n', Files.readAllBytes(out)[114]);
        assertEquals(10, String.valueOf(app.getLong("APP-ID")).length());
    }

    @Test
    void approvedDecisionWithoutAnyPricedRecordAbends(@TempDir Path tmp) throws IOException {
        FixedRecord approved = FixedRecord.parse(Copybooks.LNDECREC,
                RecordReader.readAll(LEGACY.resolve("golden/DECISION.dat"), Copybooks.LNDECREC).get(0).toLine())
                .set("DEC-CODE", "A");
        Path p = tmp.resolve("PRICED.dat");
        Files.writeString(p, "");
        Path d = write(tmp.resolve("DECISION.dat"), List.of(approved));
        assertThrows(IOException.class,
                () -> run(LEGACY.resolve("data/input/RUNDATE.dat"), p, d, tmp.resolve("FISACCT.dat")));
    }

    private static Path write(Path path, List<FixedRecord> records) throws IOException {
        try (RecordWriter w = RecordWriter.open(path, LineMode.FIXED)) {
            for (FixedRecord r : records) {
                w.write(r);
            }
        }
        return path;
    }
}
