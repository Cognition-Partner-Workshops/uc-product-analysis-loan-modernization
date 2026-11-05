package los.record;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a LINE SEQUENTIAL dataset as GnuCOBOL does: records end at '\n', a short line is
 * padded with spaces to the record length. A line longer than the layout is an error.
 */
public final class RecordReader implements AutoCloseable {
    private final BufferedReader in;
    private final Layout layout;
    private final StringBuilder line = new StringBuilder();
    private long recordNumber;

    private RecordReader(BufferedReader in, Layout layout) {
        this.in = in;
        this.layout = layout;
    }

    public static RecordReader open(Path path, Layout layout) throws IOException {
        return new RecordReader(Files.newBufferedReader(path, StandardCharsets.ISO_8859_1), layout);
    }

    public static List<FixedRecord> readAll(Path path, Layout layout) throws IOException {
        List<FixedRecord> out = new ArrayList<>();
        try (RecordReader r = open(path, layout)) {
            for (FixedRecord rec = r.read(); rec != null; rec = r.read()) {
                out.add(rec);
            }
        }
        return out;
    }

    /** The next record, or null at end of file (READ ... AT END). */
    public FixedRecord read() throws IOException {
        line.setLength(0);
        int c;
        while ((c = in.read()) != -1 && c != '\n') {
            line.append((char) c);
        }
        if (c == -1 && line.isEmpty()) {
            return null;
        }
        recordNumber++;
        if (line.length() > layout.length()) {
            throw new RecordFormatException(layout.name() + " record " + recordNumber + " is "
                    + line.length() + " bytes, longer than " + layout.length());
        }
        while (line.length() < layout.length()) {
            line.append(' ');
        }
        return FixedRecord.parse(layout, line.toString());
    }

    public long recordNumber() {
        return recordNumber;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
