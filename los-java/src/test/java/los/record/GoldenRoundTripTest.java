package los.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every recorded input and golden fixed-width dataset decodes field by field through its
 * copybook layout and re-encodes from the typed values to the same bytes, line ends included.
 */
class GoldenRoundTripTest {

    static final Path LEGACY = Path.of(System.getProperty("legacy.los.dir", "../legacy-los"));

    static Stream<Arguments> datasets() {
        return Stream.of(
                Arguments.of("data/input/APPS.dat", Copybooks.LNAPPREC),
                Arguments.of("data/input/RATESHT.dat", Copybooks.LNRATE),
                Arguments.of("golden/PRICED.dat", Copybooks.LNPRCREC),
                Arguments.of("golden/DECISION.dat", Copybooks.LNDECREC),
                Arguments.of("golden/FISACCT.dat", Copybooks.FISACCT),
                Arguments.of("golden/LOS_GL_20261105.dat", Copybooks.LNGLREC));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("datasets")
    void rebuildsEachRecordFromItsFields(String file, Layout layout, @TempDir Path tmp)
            throws IOException {
        Path source = LEGACY.resolve(file);
        List<FixedRecord> records = RecordReader.readAll(source, layout);
        assertFalse(records.isEmpty(), file);

        Path out = tmp.resolve("out.dat");
        try (RecordWriter w = RecordWriter.open(out, LineMode.FIXED)) {
            for (FixedRecord in : records) {
                w.write(rebuild(in));
            }
        }
        assertEquals(new String(Files.readAllBytes(source), StandardCharsets.ISO_8859_1),
                new String(Files.readAllBytes(out), StandardCharsets.ISO_8859_1), file);
    }

    @Test
    void pricedRecordsCarryAParseableApplication() throws IOException {
        for (FixedRecord prc : RecordReader.readAll(LEGACY.resolve("golden/PRICED.dat"), Copybooks.LNPRCREC)) {
            FixedRecord app = FixedRecord.parse(Copybooks.LNAPPREC, prc.getString("PRC-APP"));
            assertEquals(10, String.valueOf(app.getLong("APP-ID")).length());
        }
    }

    private static FixedRecord rebuild(FixedRecord in) {
        FixedRecord copy = FixedRecord.blank(in.layout());
        for (Field f : in.layout().fields()) {
            if (f.filler()) {
                continue;
            }
            if (f.numeric()) {
                copy.set(f.name(), in.getDecimal(f.name()));
            } else {
                copy.set(f.name(), in.getString(f.name()));
            }
        }
        return copy;
    }
}
