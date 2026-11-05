package los.program;

import java.util.List;

/** STEP040 GL extract: reads RUNDATE, DECISION, writes GLOUT. */
public final class LNGL040 extends NotYetPorted {
    public LNGL040() {
        super(List.of("RUNDATE", "DECISION"), List.of("GLOUT"));
    }
}
