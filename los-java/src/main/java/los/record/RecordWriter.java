package los.record;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes a LINE SEQUENTIAL dataset: ISO-8859-1, one record per line, '\n' line ends.
 * A character ISO-8859-1 cannot hold is rejected before anything of its line is written;
 * it is never replaced.
 */
public final class RecordWriter implements AutoCloseable {
    private final BufferedWriter out;
    private final LineMode mode;
    private long written;

    private RecordWriter(BufferedWriter out, LineMode mode) {
        this.out = out;
        this.mode = mode;
    }

    public static RecordWriter open(Path path, LineMode mode) throws IOException {
        var encoder = StandardCharsets.ISO_8859_1.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return new RecordWriter(
                new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(path), encoder)), mode);
    }

    public void write(FixedRecord record) throws IOException {
        write(record.toLine());
    }

    /** Writes a line that is not a copybook record, e.g. a CSV row. */
    public void write(String line) throws IOException {
        int bad = Latin1.firstUnencodable(line);
        if (bad >= 0) {
            throw new UnencodableCharacterException("record " + (written + 1) + " column " + (bad + 1)
                    + " holds " + Latin1.describe(line.codePointAt(bad))
                    + ", which ISO-8859-1 cannot encode");
        }
        out.write(mode == LineMode.TRIMMED ? trimSpaces(line) : line);
        out.write('\n');
        written++;
    }

    private static String trimSpaces(String line) {
        int end = line.length();
        while (end > 0 && line.charAt(end - 1) == ' ') {
            end--;
        }
        return line.substring(0, end);
    }

    public long written() {
        return written;
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
