package los.record;

/** Where a signed DISPLAY numeric keeps its sign, as declared by the SIGN clause. */
public enum Sign {
    /** Unsigned picture (no S). */
    NONE,
    /** {@code S9..} with no SIGN clause: overpunched into the last digit. */
    TRAILING,
    /** {@code SIGN LEADING}: overpunched into the first digit. */
    LEADING,
    /** {@code SIGN LEADING SEPARATE}: a '+' or '-' byte before the digits. */
    LEADING_SEPARATE,
    /** {@code SIGN TRAILING SEPARATE}: a '+' or '-' byte after the digits. */
    TRAILING_SEPARATE;

    boolean separate() {
        return this == LEADING_SEPARATE || this == TRAILING_SEPARATE;
    }
}
