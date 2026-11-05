package los.program;

import los.BatchProgram;
import los.Programs;

/** Port readiness comes from the running implementation, not a manually maintained allowlist. */
public final class PortStatus {
    private PortStatus() {
    }

    public static boolean isPorted(BatchProgram program) {
        return !(program instanceof NotYetPorted);
    }

    public static void main(String[] args) {
        Programs.JOB_ORDER.forEach(p -> System.out.println(p.name() + "\t" + isPorted(p)));
    }
}
