# Java LOS batch

Requires Java 21. Build with `JAVA_HOME=/path/to/jdk-21 ./mvnw -q -B verify`.
Run `./run-chain.sh [output-dir] [input-dir]` (defaults: `target/out/java` and
`../legacy-los/data/input`). The runner uses only original input files and
Java-generated intermediates, derives extract filenames from RUNDATE, and writes
the LOSNIGHT header and program output to SYSOUT.txt. Diagnostics go to stderr.
The highest return code is propagated; RC > 4 skips all remaining steps.

The full chain requires all five program ports. Any step that is not yet
ported returns RC 12, and the steps after it are skipped. To run pricing alone,
invoke `java -jar target/los-java.jar LNPRC010` with DD_APPSIN, DD_RATESIN and
DD_PRICED set to the application input, the rate sheet and the output path.
