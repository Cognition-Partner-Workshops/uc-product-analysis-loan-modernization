# Java LOS batch

Requires Java 21. Build with `JAVA_HOME=/path/to/jdk-21 ./mvnw -q -B verify`.
Run `./run-chain.sh [output-dir] [input-dir]` (defaults: `out/java` and
`../legacy-los/data/input`). The runner uses only original input files and
Java-generated intermediates, derives extract filenames from RUNDATE, and writes
the LOSNIGHT header and program output to SYSOUT.txt. Diagnostics go to stderr.
The highest return code is propagated; RC > 4 skips all remaining steps.

Only LNPRC010 is ported. The runner produces PRICED.dat and pricing totals, then
LNUWR020 fails with RC 12 and later steps are skipped. For pricing alone, invoke
`java -jar target/los-java.jar LNPRC010` with DD_APPSIN, DD_RATESIN and DD_PRICED
set to the application input, rate sheet and desired output paths respectively.
