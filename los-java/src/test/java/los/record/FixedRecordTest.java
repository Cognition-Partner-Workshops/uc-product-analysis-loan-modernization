package los.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

/**
 * Expected bytes come from GnuCOBOL 3.1.2 (the legacy compiler) moving the same values
 * into the same pictures and DISPLAYing the record.
 */
class FixedRecordTest {

    private static final Layout T = Layout.builder("T")
            .field("A", "S9(3)V99")
            .field("B", "S9(3)V99")
            .field("C", "S9(3)V99", Sign.LEADING)
            .field("D", "S9(1)V9999", Sign.LEADING_SEPARATE)
            .field("E", "S9(1)V9999", Sign.TRAILING_SEPARATE)
            .field("F", "9(3)V99")
            .field("G", "S9(3)V99", Sign.LEADING_SEPARATE)
            .field("H", "S9(3)")
            .field("NAME", "X(6)")
            .filler("X(4)")
            .build(51);

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    /** GnuCOBOL 3.1.2 DISPLAY of the record after the moves in encodesLikeGnuCobolMove. */
    private static final String GNUCOBOL = "0123t01234p1230-0250002500-23456+0000012p";

    @Test
    void encodesLikeGnuCobolMove() {
        FixedRecord r = FixedRecord.blank(T)
                .set("A", d("-12.34"))
                .set("B", d("12.34"))
                .set("C", d("-12.30"))
                .set("D", d("-0.25"))
                .set("E", d("-0.25"))
                .set("F", d("-1234.567"))
                .set("G", BigDecimal.ZERO)
                .set("H", -120)
                .set("NAME", "AB");
        assertEquals(GNUCOBOL + "AB        ", r.toLine());
    }

    @Test
    void decodesWhatItEncodes() {
        FixedRecord r = FixedRecord.parse(T, GNUCOBOL + "AB        ");
        assertEquals(d("-12.34"), r.getDecimal("A"));
        assertEquals(d("12.34"), r.getDecimal("B"));
        assertEquals(d("-12.30"), r.getDecimal("C"));
        assertEquals(d("-0.2500"), r.getDecimal("D"));
        assertEquals(d("-0.2500"), r.getDecimal("E"));
        assertEquals(d("234.56"), r.getDecimal("F"));
        assertEquals(d("0.00"), r.getDecimal("G"));
        assertEquals(-120, r.getLong("H"));
        assertEquals("AB    ", r.getString("NAME"));
    }

    @Test
    void decimalsKeepThePictureScale() {
        FixedRecord r = FixedRecord.blank(T).set("D", d("1"));
        assertEquals(4, r.getDecimal("D").scale());
        assertEquals("+10000", r.getString("D"));
    }

    @Test
    void negativeZeroKeepsItsSignLikeGnuCobol() {
        FixedRecord r = FixedRecord.blank(T).set("A", d("-0.001")).set("G", d("-0.001"));
        assertEquals("0000p", r.getString("A"));
        assertEquals("-00000", r.getString("G"));
    }

    @Test
    void roundedIsHalfUpAwayFromZeroAndPlainMoveTruncates() {
        FixedRecord r = FixedRecord.blank(T);
        r.set("H", d("-12.5"), RoundingMode.HALF_UP);
        assertEquals("01s", r.getString("H"));
        r.set("B", d("1.235"), RoundingMode.HALF_UP);
        assertEquals(d("1.24"), r.getDecimal("B"));
        r.set("B", d("1.239"));
        assertEquals(d("1.23"), r.getDecimal("B"));
    }

    @Test
    void highOrderDigitsAreDroppedOnOverflow() {
        FixedRecord r = FixedRecord.blank(T).set("B", d("-1234.5"));
        assertEquals("2345p", r.getString("B"));
    }

    @Test
    void alphanumericsAreLeftJustifiedPaddedAndCutOnTheRight() {
        FixedRecord r = FixedRecord.blank(T).set("NAME", "ABCDEFGH");
        assertEquals("ABCDEF", r.getString("NAME"));
        r.set("NAME", "");
        assertEquals("      ", r.getString("NAME"));
    }

    @Test
    void initializeZeroesNumericsAndLeavesFiller() {
        FixedRecord r = FixedRecord.parse(T, "x".repeat(51)).initialize();
        assertEquals("000000000000000+0000000000+00000+00000000      xxxx", r.toLine());
    }

    @Test
    void readsEbcdicStyleOverpunch() {
        Layout l = Layout.builder("O").field("P", "S9(3)").field("N", "S9(3)").build(6);
        FixedRecord r = FixedRecord.parse(l, "12C12L");
        assertEquals(123, r.getLong("P"));
        assertEquals(-123, r.getLong("N"));
    }

    @Test
    void rejectsBadNumericBytes() {
        FixedRecord r = FixedRecord.blank(T);
        RecordFormatException e = assertThrows(RecordFormatException.class, () -> r.getDecimal("F"));
        assertEquals(true, e.getMessage().contains("column 28"));
        assertThrows(RecordFormatException.class, () -> FixedRecord.parse(T, "short"));
    }

    @Test
    void typeMismatchesFail() {
        FixedRecord r = FixedRecord.blank(T);
        assertThrows(IllegalArgumentException.class, () -> r.set("A", "12"));
        assertThrows(IllegalArgumentException.class, () -> r.getDecimal("NAME"));
        assertThrows(IllegalArgumentException.class, () -> r.getLong("A"));
    }

    @Test
    void latin1NamesAreKeptAndOthersAreRejectedNotReplaced() {
        FixedRecord r = FixedRecord.blank(T).set("NAME", "Zoë");
        assertEquals("Zoë   ", r.getString("NAME"));

        UnencodableCharacterException e = assertThrows(UnencodableCharacterException.class,
                () -> r.set("NAME", "Łukasz"));
        assertTrue(e.getMessage().contains("NAME") && e.getMessage().contains("U+0141"), e.getMessage());
        assertEquals("Zoë   ", r.getString("NAME"));
        assertThrows(UnencodableCharacterException.class, () -> r.set("NAME", "王芳"));
    }
}
