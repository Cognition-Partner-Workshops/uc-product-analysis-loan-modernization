package los.record;

/** The legacy datasets are ISO-8859-1: one byte per character, U+0000 to U+00FF. */
final class Latin1 {
    private Latin1() {
    }

    /** Index of the first char outside ISO-8859-1, or -1 when every char fits. */
    static int firstUnencodable(CharSequence s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 0xFF) {
                return i;
            }
        }
        return -1;
    }

    static String describe(int codePoint) {
        return String.format("U+%04X '%s'", codePoint, Character.toString(codePoint));
    }
}
