package los.record;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A DISPLAY picture string as written in the copybooks: {@code X(15)}, {@code 9(9)V99},
 * {@code S9(1)V9999}, {@code 9V999}. Only the symbols the LOS copybooks use are accepted.
 */
public record Pic(boolean alphanumeric, boolean signed, int integerDigits, int scale) {

    private static final Pattern SYMBOL = Pattern.compile("([SX9V])(?:\\((\\d+)\\))?");

    public static Pic parse(String picture) {
        String p = picture.trim().toUpperCase();
        Matcher m = SYMBOL.matcher(p);
        int pos = 0;
        int x = 0;
        int intDigits = 0;
        int decDigits = 0;
        boolean signed = false;
        boolean afterV = false;
        while (pos < p.length()) {
            if (!m.find(pos) || m.start() != pos) {
                throw new IllegalArgumentException("unsupported PIC " + picture);
            }
            char symbol = m.group(1).charAt(0);
            int count = m.group(2) == null ? 1 : Integer.parseInt(m.group(2));
            switch (symbol) {
                case 'X' -> x += count;
                case 'S' -> {
                    if (pos != 0 || count != 1) {
                        throw new IllegalArgumentException("S must lead PIC " + picture);
                    }
                    signed = true;
                }
                case 'V' -> {
                    if (afterV || count != 1) {
                        throw new IllegalArgumentException("one V allowed in PIC " + picture);
                    }
                    afterV = true;
                }
                default -> {
                    if (afterV) {
                        decDigits += count;
                    } else {
                        intDigits += count;
                    }
                }
            }
            pos = m.end();
        }
        boolean numeric = intDigits + decDigits > 0;
        if (x > 0 && (numeric || signed || afterV)) {
            throw new IllegalArgumentException("mixed alphanumeric/numeric PIC " + picture);
        }
        if (x == 0 && !numeric) {
            throw new IllegalArgumentException("empty PIC " + picture);
        }
        return x > 0 ? new Pic(true, false, x, 0) : new Pic(false, signed, intDigits, decDigits);
    }

    /** Number of digit positions, or characters for an alphanumeric picture. */
    public int digits() {
        return integerDigits + scale;
    }
}
