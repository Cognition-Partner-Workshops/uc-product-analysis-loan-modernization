package los.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CopybooksTest {

    @Test
    void recordLengthsMatchTheCopybookHeaders() {
        assertEquals(150, Copybooks.LNAPPREC.length());
        assertEquals(40, Copybooks.LNRATE.length());
        assertEquals(200, Copybooks.LNPRCREC.length());
        assertEquals(80, Copybooks.LNDECREC.length());
        assertEquals(85, Copybooks.LNGLREC.length());
        assertEquals(114, Copybooks.FISACCT.length());
    }

    @Test
    void offsetsFollowTheFieldOrder() {
        Field risk = Copybooks.LNPRCREC.field("PRC-RISK-ADJ");
        assertEquals(161, risk.offset());
        assertEquals(6, risk.length());
        assertEquals(Sign.LEADING_SEPARATE, risk.sign());
        assertEquals(58, Copybooks.LNAPPREC.field("LOAN-PURPOSE").offset());
        assertEquals(0, Copybooks.LNAPPREC.field("APP-ID").offset());
    }

    @Test
    void builderRejectsALengthThatDisagreesWithTheCopybook() {
        Layout.Builder b = Layout.builder("T").field("A", "X(3)");
        assertThrows(IllegalStateException.class, () -> b.build(4));
    }

    @Test
    void unknownFieldNamesFail() {
        assertThrows(IllegalArgumentException.class, () -> Copybooks.LNRATE.field("FILLER"));
        assertThrows(IllegalArgumentException.class, () -> Copybooks.LNRATE.field("NOPE"));
    }
}
