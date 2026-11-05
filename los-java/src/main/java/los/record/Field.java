package los.record;

/** One elementary item of a record layout: name, 0-based offset and byte length. */
public record Field(String name, int offset, int length, Pic pic, Sign sign) {

    public static final String FILLER = "FILLER";

    public boolean numeric() {
        return !pic.alphanumeric();
    }

    public boolean filler() {
        return FILLER.equals(name);
    }

    public int end() {
        return offset + length;
    }
}
