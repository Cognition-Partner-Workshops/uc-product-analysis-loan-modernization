package los;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import los.program.PortStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProgramParityTest {
    @TempDir
    Path out;

    private void parity(String name, String outputDd, String filename) throws IOException {
        BatchProgram program = Programs.find(name).orElseThrow();
        assumeTrue(PortStatus.isPorted(program), name + " is not ported");
        Path legacy = Path.of(System.getProperty("legacy.los.dir"));
        Path golden = legacy.resolve("golden");
        Map<String, String> env = new HashMap<>();
        env.put("DD_APPSIN", legacy.resolve("data/input/APPS.dat").toString());
        env.put("DD_RATESIN", legacy.resolve("data/input/RATESHT.dat").toString());
        env.put("DD_RUNDATE", legacy.resolve("data/input/RUNDATE.dat").toString());
        env.put("DD_PRICED", golden.resolve("PRICED.dat").toString());
        env.put("DD_DECISION", golden.resolve("DECISION.dat").toString());
        Path candidate = out.resolve(filename);
        env.put(Dd.variable(outputDd), candidate.toString());
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        try (PrintStream stream = new PrintStream(diagnostics)) {
            assertEquals(0, Main.run(new String[] {name}, new Dd(env), stream, stream),
                    diagnostics::toString);
        }
        assertArrayEquals(Files.readAllBytes(golden.resolve(filename)), Files.readAllBytes(candidate));
    }

    @Test
    void pricing() throws IOException {
        parity("LNPRC010", "PRICED", "PRICED.dat");
    }

    @Test
    void underwriting() throws IOException {
        parity("LNUWR020", "DECISION", "DECISION.dat");
    }

    @Test
    void coreBanking() throws IOException {
        parity("LNFIS030", "FISOUT", "FISACCT.dat");
    }

    @Test
    void generalLedger() throws IOException {
        parity("LNGL040", "GLOUT", "LOS_GL_20261105.dat");
    }

    @Test
    void hmda() throws IOException {
        parity("LNHMD050", "HMDAOUT", "LOS_HMDA_202611.csv");
    }
}
