package los.program;

import java.util.List;

/** STEP010 risk-based pricing: reads APPSIN, RATESIN, writes PRICED. */
public final class LNPRC010 extends NotYetPorted {
    public LNPRC010() {
        super(List.of("APPSIN", "RATESIN"), List.of("PRICED"));
    }
}
