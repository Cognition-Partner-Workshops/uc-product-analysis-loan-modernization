package los.record;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Encodes and decodes DISPLAY (zoned) numerics with an implied decimal point the way
 * GnuCOBOL does on an ASCII host: one byte per digit, a negative overpunched sign adds
 * 0x40 to the digit ('0'..'9' becomes 'p'..'y'), a separate sign is '+' or '-'.
 * Decoding also accepts the EBCDIC-style overpunch letters ('{', 'A'..'I', '}', 'J'..'R').
 */
final class Zoned {
    private Zoned() {
    }

    static BigDecimal decode(Field f, CharSequence text) {
        int digits = f.pic().digits();
        char[] d = new char[digits];
        boolean negative = false;
        int start = f.sign() == Sign.LEADING_SEPARATE ? 1 : 0;
        if (f.sign().separate()) {
            char s = text.charAt(f.sign() == Sign.LEADING_SEPARATE ? 0 : digits);
            if (s == '-') {
                negative = true;
            } else if (s != '+') {
                throw bad(f, text);
            }
        }
        for (int i = 0; i < digits; i++) {
            char c = text.charAt(start + i);
            boolean signByte = (f.sign() == Sign.TRAILING && i == digits - 1)
                    || (f.sign() == Sign.LEADING && i == 0);
            if (c >= '0' && c <= '9') {
                d[i] = c;
            } else if (signByte && c >= 'p' && c <= 'y') {
                d[i] = (char) (c - 0x40);
                negative = true;
            } else if (signByte && (c == '{' || (c >= 'A' && c <= 'I'))) {
                d[i] = c == '{' ? '0' : (char) ('1' + (c - 'A'));
            } else if (signByte && (c == '}' || (c >= 'J' && c <= 'R'))) {
                d[i] = c == '}' ? '0' : (char) ('1' + (c - 'J'));
                negative = true;
            } else {
                throw bad(f, text);
            }
        }
        BigInteger unscaled = new BigInteger(new String(d));
        return new BigDecimal(negative ? unscaled.negate() : unscaled, f.pic().scale());
    }

    /**
     * COBOL MOVE/COMPUTE into the field: decimals beyond the scale go by {@code mode}
     * (DOWN without ROUNDED, HALF_UP with it), high-order digits that do not fit are
     * dropped, an unsigned field stores the absolute value, and a negative source keeps
     * its sign even when the stored digits are all zero (GnuCOBOL does the same).
     */
    static String encode(Field f, BigDecimal value, RoundingMode mode) {
        int digits = f.pic().digits();
        boolean negative = f.pic().signed() && value.signum() < 0;
        String all = value.abs().setScale(f.pic().scale(), mode).unscaledValue().toString();
        String kept = all.length() > digits ? all.substring(all.length() - digits) : all;
        char[] d = ("0".repeat(digits - kept.length()) + kept).toCharArray();
        return switch (f.sign()) {
            case NONE -> new String(d);
            case TRAILING -> {
                if (negative) {
                    d[digits - 1] += 0x40;
                }
                yield new String(d);
            }
            case LEADING -> {
                if (negative) {
                    d[0] += 0x40;
                }
                yield new String(d);
            }
            case LEADING_SEPARATE -> (negative ? "-" : "+") + new String(d);
            case TRAILING_SEPARATE -> new String(d) + (negative ? "-" : "+");
        };
    }

    private static RecordFormatException bad(Field f, CharSequence text) {
        return new RecordFormatException("field " + f.name() + " at column " + (f.offset() + 1)
                + " is not a valid " + f.pic() + " value: '" + text + "'");
    }
}
