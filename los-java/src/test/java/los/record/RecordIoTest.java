package los.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordIoTest {

    private static final Layout L = Layout.builder("L").field("K", "X(3)").filler("X(5)").build(8);

    @Test
    void fixedModeKeepsTrailingFillerAndTrimmedModeDropsIt(@TempDir Path tmp) throws IOException {
        Path fixed = tmp.resolve("fixed");
        Path trimmed = tmp.resolve("trimmed");
        try (RecordWriter f = RecordWriter.open(fixed, LineMode.FIXED);
             RecordWriter t = RecordWriter.open(trimmed, LineMode.TRIMMED)) {
            FixedRecord r = FixedRecord.blank(L).set("K", "AB");
            f.write(r);
            t.write(r);
            t.write("a|b|\t  ");
            assertEquals(2, t.written());
        }
        assertEquals("AB      \n", Files.readString(fixed, StandardCharsets.ISO_8859_1));
        assertEquals("AB\na|b|\t\n", Files.readString(trimmed, StandardCharsets.ISO_8859_1));
    }

    @Test
    void shortLinesArePaddedAndTheLastLineNeedsNoTerminator(@TempDir Path tmp) throws IOException {
        Path p = tmp.resolve("in");
        Files.writeString(p, "AB\nXYZ12345\nQ", StandardCharsets.ISO_8859_1);
        try (RecordReader r = RecordReader.open(p, L)) {
            assertEquals("AB      ", r.read().toLine());
            assertEquals("XYZ12345", r.read().toLine());
            assertEquals("Q       ", r.read().toLine());
            assertNull(r.read());
            assertEquals(3, r.recordNumber());
        }
    }

    @Test
    void longLinesAreRejected(@TempDir Path tmp) throws IOException {
        Path p = tmp.resolve("in");
        Files.writeString(p, "123456789\n", StandardCharsets.ISO_8859_1);
        try (RecordReader r = RecordReader.open(p, L)) {
            assertThrows(RecordFormatException.class, r::read);
        }
    }

    @Test
    void bytesAboveAsciiSurviveARoundTrip(@TempDir Path tmp) throws IOException {
        Path p = tmp.resolve("in");
        Files.write(p, new byte[] {(byte) 0xC9, 'A', 'B', '\n'});
        FixedRecord rec = RecordReader.readAll(p, L).get(0);
        Path out = tmp.resolve("out");
        try (RecordWriter w = RecordWriter.open(out, LineMode.TRIMMED)) {
            w.write(rec);
        }
        assertEquals(4, Files.readAllBytes(out).length);
        assertEquals((byte) 0xC9, Files.readAllBytes(out)[0]);
    }
}
