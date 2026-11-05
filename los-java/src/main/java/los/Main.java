package los;

import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import los.record.RecordFormatException;

/**
 * Entry point: {@code java -jar los-java.jar <PROGRAM>} runs one step of the chain with
 * its datasets taken from the {@code DD_<name>} environment variables.
 * Return codes follow the batch convention: 0 ok, 12 the step failed, 16 bad invocation.
 */
public final class Main {
    public static final int RC_FAILED = 12;
    public static final int RC_USAGE = 16;

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, Dd.fromEnvironment(), System.out, System.err));
    }

    static int run(String[] args, Dd dd, PrintStream sysout, PrintStream err) {
        String names = Programs.JOB_ORDER.stream().map(BatchProgram::name).collect(Collectors.joining("|"));
        if (args.length != 1) {
            err.println("usage: los-java <" + names + ">");
            return RC_USAGE;
        }
        Optional<BatchProgram> found = Programs.find(args[0]);
        if (found.isEmpty()) {
            err.println("unknown program " + args[0] + "; expected one of " + names);
            return RC_USAGE;
        }
        BatchProgram program = found.get();
        List<String> problems = new ArrayList<>();
        for (String in : program.inputs()) {
            if (!dd.isDefined(in)) {
                problems.add(Dd.variable(in) + " is not set");
            } else if (!Files.isRegularFile(dd.path(in))) {
                problems.add(Dd.variable(in) + " " + dd.path(in) + " does not exist");
            }
        }
        for (String out : program.outputs()) {
            if (!dd.isDefined(out)) {
                problems.add(Dd.variable(out) + " is not set");
            }
        }
        if (!problems.isEmpty()) {
            problems.forEach(p -> err.println(program.name() + " " + p));
            return RC_USAGE;
        }
        try {
            return program.run(dd, sysout);
        } catch (java.io.IOException | RecordFormatException | UnsupportedOperationException e) {
            err.println(program.name() + " ABEND " + e.getMessage());
            return RC_FAILED;
        }
    }
}
