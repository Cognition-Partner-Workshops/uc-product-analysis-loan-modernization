package los.program;

import java.util.List;

/** STEP030 core banking account feed: reads RUNDATE, PRICED, DECISION, writes FISOUT. */
public final class LNFIS030 extends NotYetPorted {
    public LNFIS030() {
        super(List.of("RUNDATE", "PRICED", "DECISION"), List.of("FISOUT"));
    }
}
