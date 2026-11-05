package los;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

/** One step of the LOS nightly chain, replacing the COBOL program of the same name. */
public interface BatchProgram {

    /** The legacy program name, e.g. LNPRC010; also the argument that selects it. */
    String name();

    /** DD names the program opens for input. */
    List<String> inputs();

    /** DD names the program opens for output. */
    List<String> outputs();

    /** Runs the step; {@code sysout} gets what the COBOL program DISPLAYs. Returns the step return code. */
    int run(Dd dd, PrintStream sysout) throws IOException;
}
