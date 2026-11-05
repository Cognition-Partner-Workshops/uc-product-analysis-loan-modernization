package los.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PicTest {

    @Test
    void parsesThePicturesTheCopybooksUse() {
        assertEquals(new Pic(true, false, 15, 0), Pic.parse("X(15)"));
        assertEquals(new Pic(false, false, 9, 2), Pic.parse("9(9)V99"));
        assertEquals(new Pic(false, true, 1, 4), Pic.parse("S9(1)V9999"));
        assertEquals(new Pic(false, false, 1, 3), Pic.parse("9V999"));
        assertEquals(new Pic(false, false, 2, 1), Pic.parse("9(2)V9"));
        assertEquals(new Pic(false, true, 7, 0), Pic.parse("S9(7)"));
    }

    @Test
    void rejectsPicturesOutsideTheSubset() {
        assertThrows(IllegalArgumentException.class, () -> Pic.parse("Z(6)9"));
        assertThrows(IllegalArgumentException.class, () -> Pic.parse("9V9V9"));
        assertThrows(IllegalArgumentException.class, () -> Pic.parse("X(3)9"));
        assertThrows(IllegalArgumentException.class, () -> Pic.parse("9S9"));
    }
}
