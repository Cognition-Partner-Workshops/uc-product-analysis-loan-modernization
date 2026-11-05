package los;

import java.util.List;
import java.util.Optional;
import los.program.LNFIS030;
import los.program.LNGL040;
import los.program.LNHMD050;
import los.program.LNPRC010;
import los.program.LNUWR020;

/** The chain's programs in job order (jcl/LOSNIGHT.jcl). */
public final class Programs {
    private Programs() {
    }

    public static final List<BatchProgram> JOB_ORDER = List.of(
            new LNPRC010(), new LNUWR020(), new LNFIS030(), new LNGL040(), new LNHMD050());

    public static Optional<BatchProgram> find(String name) {
        return JOB_ORDER.stream().filter(p -> p.name().equalsIgnoreCase(name)).findFirst();
    }
}
