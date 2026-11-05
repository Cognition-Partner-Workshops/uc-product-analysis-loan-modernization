package los.record;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

/**
 * One fixed-width record laid out by a {@link Layout}. The bytes are kept as ISO-8859-1
 * characters; every field read or write goes through its PIC, so amounts are always
 * {@link BigDecimal} with the field's scale and never floating point.
 */
public final class FixedRecord {
    private final Layout layout;
    private final char[] data;

    private FixedRecord(Layout layout, char[] data) {
        this.layout = layout;
        this.data = data;
    }

    /** A record of spaces, like {@code MOVE SPACES TO rec}. */
    public static FixedRecord blank(Layout layout) {
        char[] d = new char[layout.length()];
        Arrays.fill(d, ' ');
        return new FixedRecord(layout, d);
    }

    /** A record holding exactly {@code line}, which must be the layout's length. */
    public static FixedRecord parse(Layout layout, String line) {
        if (line.length() != layout.length()) {
            throw new RecordFormatException(layout.name() + " record is " + line.length()
                    + " bytes, expected " + layout.length());
        }
        return new FixedRecord(layout, line.toCharArray());
    }

    public Layout layout() {
        return layout;
    }

    /**
     * {@code INITIALIZE rec}: numeric fields to zero, alphanumeric fields to spaces,
     * FILLER left as it is.
     */
    public FixedRecord initialize() {
        for (Field f : layout.fields()) {
            if (f.filler()) {
                continue;
            }
            if (f.numeric()) {
                put(f, Zoned.encode(f, BigDecimal.ZERO, RoundingMode.UNNECESSARY));
            } else {
                Arrays.fill(data, f.offset(), f.end(), ' ');
            }
        }
        return this;
    }

    /** The raw bytes of a field: an alphanumeric value with its padding, or numeric digits as stored. */
    public String getString(String name) {
        Field f = layout.field(name);
        return new String(data, f.offset(), f.length());
    }

    /** A numeric field as a BigDecimal whose scale is the PIC's implied decimals. */
    public BigDecimal getDecimal(String name) {
        Field f = numeric(name);
        return Zoned.decode(f, new String(data, f.offset(), f.length()));
    }

    /** A numeric field with no decimals, e.g. a date or a count. */
    public long getLong(String name) {
        Field f = numeric(name);
        if (f.pic().scale() != 0) {
            throw new IllegalArgumentException(name + " has " + f.pic().scale() + " decimals; use getDecimal");
        }
        return getDecimal(name).longValueExact();
    }

    public int getInt(String name) {
        return Math.toIntExact(getLong(name));
    }

    /** {@code MOVE value TO field} for an alphanumeric field: left-justified, space-padded, cut on the right. */
    public FixedRecord set(String name, String value) {
        Field f = layout.field(name);
        if (f.numeric()) {
            throw new IllegalArgumentException(name + " is numeric; set it from a BigDecimal or long");
        }
        for (int i = 0; i < f.length(); i++) {
            data[f.offset() + i] = i < value.length() ? value.charAt(i) : ' ';
        }
        return this;
    }

    /** {@code MOVE value TO field} for a numeric field: extra decimals are truncated. */
    public FixedRecord set(String name, BigDecimal value) {
        return set(name, value, RoundingMode.DOWN);
    }

    /** {@code COMPUTE field [ROUNDED] = value}: pass HALF_UP for ROUNDED, DOWN otherwise. */
    public FixedRecord set(String name, BigDecimal value, RoundingMode mode) {
        Field f = numeric(name);
        put(f, Zoned.encode(f, value, mode));
        return this;
    }

    public FixedRecord set(String name, long value) {
        return set(name, BigDecimal.valueOf(value), RoundingMode.UNNECESSARY);
    }

    /** The record as written to a fixed-width dataset, exactly {@code layout().length()} bytes. */
    public String toLine() {
        return new String(data);
    }

    @Override
    public String toString() {
        return layout.name() + "[" + toLine() + "]";
    }

    private Field numeric(String name) {
        Field f = layout.field(name);
        if (!f.numeric()) {
            throw new IllegalArgumentException(name + " is alphanumeric");
        }
        return f;
    }

    private void put(Field f, String encoded) {
        encoded.getChars(0, f.length(), data, f.offset());
    }
}
