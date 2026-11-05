package los.program;

import java.io.PrintStream;
import java.util.List;
import los.BatchProgram;
import los.Dd;

/** Base for a step whose port has not landed yet: declares its DDs and fails with RC 12. */
abstract class NotYetPorted implements BatchProgram {
    private final List<String> inputs;
    private final List<String> outputs;

    NotYetPorted(List<String> inputs, List<String> outputs) {
        this.inputs = inputs;
        this.outputs = outputs;
    }

    @Override
    public String name() {
        return getClass().getSimpleName();
    }

    @Override
    public List<String> inputs() {
        return inputs;
    }

    @Override
    public List<String> outputs() {
        return outputs;
    }

    @Override
    public int run(Dd dd, PrintStream sysout) {
        throw new UnsupportedOperationException(name() + " is not ported to Java yet");
    }
}
