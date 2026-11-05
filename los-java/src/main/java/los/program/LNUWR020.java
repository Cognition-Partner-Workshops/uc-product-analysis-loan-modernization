package los.program;

import java.util.List;

/** STEP020 underwriting: reads RUNDATE, PRICED, writes DECISION. */
public final class LNUWR020 extends NotYetPorted {
    public LNUWR020() {
        super(List.of("RUNDATE", "PRICED"), List.of("DECISION"));
    }
}
