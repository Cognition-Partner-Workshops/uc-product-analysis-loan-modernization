package los.record;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The elementary fields of one copybook 01 level, in order, with their offsets. */
public final class Layout {
    private final String name;
    private final List<Field> fields;
    private final Map<String, Field> byName;
    private final int length;

    private Layout(String name, List<Field> fields) {
        this.name = name;
        this.fields = List.copyOf(fields);
        Map<String, Field> map = new LinkedHashMap<>();
        for (Field f : fields) {
            if (!f.filler() && map.put(f.name(), f) != null) {
                throw new IllegalArgumentException(name + ": duplicate field " + f.name());
            }
        }
        this.byName = Collections.unmodifiableMap(map);
        this.length = fields.isEmpty() ? 0 : fields.get(fields.size() - 1).end();
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String name() {
        return name;
    }

    public int length() {
        return length;
    }

    public List<Field> fields() {
        return fields;
    }

    public Field field(String fieldName) {
        Field f = byName.get(fieldName);
        if (f == null) {
            throw new IllegalArgumentException(name + " has no field " + fieldName);
        }
        return f;
    }

    public static final class Builder {
        private final String name;
        private final List<Field> fields = new ArrayList<>();
        private int offset;

        private Builder(String name) {
            this.name = name;
        }

        public Builder field(String fieldName, String picture) {
            Pic pic = Pic.parse(picture);
            return field(fieldName, picture, pic.signed() ? Sign.TRAILING : Sign.NONE);
        }

        public Builder field(String fieldName, String picture, Sign sign) {
            Pic pic = Pic.parse(picture);
            if (pic.signed() == (sign == Sign.NONE)) {
                throw new IllegalArgumentException(fieldName + ": sign " + sign + " does not match PIC " + picture);
            }
            int len = pic.digits() + (sign.separate() ? 1 : 0);
            fields.add(new Field(fieldName, offset, len, pic, sign));
            offset += len;
            return this;
        }

        public Builder filler(String picture) {
            return field(Field.FILLER, picture);
        }

        /** Builds the layout and checks it against the record length the copybook states. */
        public Layout build(int expectedLength) {
            if (offset != expectedLength) {
                throw new IllegalStateException(name + " is " + offset + " bytes, copybook says " + expectedLength);
            }
            return new Layout(name, fields);
        }
    }
}
