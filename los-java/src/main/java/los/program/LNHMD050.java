package los.program;

import java.util.List;

/** STEP050 HMDA extract: reads RATESIN, PRICED, DECISION, writes HMDAOUT. */
public final class LNHMD050 extends NotYetPorted {
    public LNHMD050() {
        super(List.of("RATESIN", "PRICED", "DECISION"), List.of("HMDAOUT"));
    }
}
