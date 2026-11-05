package los;

import java.nio.file.Path;
import java.util.Map;

/**
 * DD name to dataset path, the way run-chain.sh passes them: DD name {@code APPSIN}
 * is the environment variable {@code DD_APPSIN} (GnuCOBOL's mapping of ASSIGN TO).
 */
public final class Dd {
    private final Map<String, String> env;

    public Dd(Map<String, String> env) {
        this.env = Map.copyOf(env);
    }

    public static Dd fromEnvironment() {
        return new Dd(System.getenv());
    }

    public static String variable(String ddName) {
        return "DD_" + ddName;
    }

    public boolean isDefined(String ddName) {
        String v = env.get(variable(ddName));
        return v != null && !v.isBlank();
    }

    public Path path(String ddName) {
        if (!isDefined(ddName)) {
            throw new IllegalStateException(variable(ddName) + " is not set");
        }
        return Path.of(env.get(variable(ddName)));
    }
}
