package los;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(Map<String, String> env, String... args) {
        return Main.run(args, new Dd(env), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private String err() {
        return err.toString(StandardCharsets.UTF_8);
    }

    @Test
    void oneEntryPointPerProgramInJobOrder() {
        assertEquals(List.of("LNPRC010", "LNUWR020", "LNFIS030", "LNGL040", "LNHMD050"),
                Programs.JOB_ORDER.stream().map(BatchProgram::name).toList());
    }

    @Test
    void ddNamesMatchTheCobolSelectClauses() {
        BatchProgram p = Programs.find("LNHMD050").orElseThrow();
        assertEquals(List.of("RATESIN", "PRICED", "DECISION"), p.inputs());
        assertEquals(List.of("HMDAOUT"), p.outputs());
    }

    @Test
    void usageAndUnknownProgramReturn16() {
        assertEquals(Main.RC_USAGE, run(Map.of()));
        assertEquals(Main.RC_USAGE, run(Map.of(), "LNXXX999"));
        assertTrue(err().contains("LNPRC010|LNUWR020|LNFIS030|LNGL040|LNHMD050"), err());
    }

    @Test
    void missingDdsAreReportedByEnvironmentVariable() {
        assertEquals(Main.RC_USAGE, run(Map.of(), "LNPRC010"));
        assertTrue(err().contains("LNPRC010 DD_APPSIN is not set"), err());
        assertTrue(err().contains("LNPRC010 DD_PRICED is not set"), err());
    }

    @Test
    void missingInputDatasetIsReported(@TempDir Path tmp) {
        Map<String, String> env = new HashMap<>();
        env.put("DD_APPSIN", tmp.resolve("nope.dat").toString());
        env.put("DD_RATESIN", tmp.resolve("nope2.dat").toString());
        env.put("DD_PRICED", tmp.resolve("PRICED.dat").toString());
        assertEquals(Main.RC_USAGE, run(env, "LNPRC010"));
        assertTrue(err().contains("DD_APPSIN " + tmp.resolve("nope.dat") + " does not exist"), err());
    }

    @Test
    void glExtractAbendsWithRc12OnMalformedDecision(@TempDir Path tmp) throws IOException {
        Path runDate = Files.writeString(tmp.resolve("RUNDATE.dat"), "20261105\n");
        Path decision = Files.writeString(tmp.resolve("DECISION.dat"), "X".repeat(81) + "\n");
        Map<String, String> env = Map.of("DD_RUNDATE", runDate.toString(), "DD_DECISION", decision.toString(),
                "DD_GLOUT", tmp.resolve("gl").toString());
        assertEquals(Main.RC_FAILED, run(env, "lngl040"));
        assertTrue(err().contains("LNGL040 ABEND LN-DEC-REC record 1 is 81 bytes, longer than 80"), err());
    }
}
