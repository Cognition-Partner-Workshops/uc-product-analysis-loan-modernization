# Java LOS batch

Requires Java 21. Build with `JAVA_HOME=/path/to/jdk-21 ./mvnw -q -B verify`.
Run `./run-chain.sh [output-dir] [input-dir]` (defaults: `out/java`, git-ignored, and
`../legacy-los/data/input`). The runner uses only original input files and
Java-generated intermediates, derives extract filenames from RUNDATE, and writes
the LOSNIGHT header and program output to SYSOUT.txt. Per-step SYSOUT goes to
`sysout/`, RCs to `steps.tsv`, port readiness to `ports.tsv` and diagnostics to
`stderr.txt`. These feed `make parity`. The highest RC is propagated. After a
ported step ends with RC > 4, the remaining steps are skipped and recorded as RC 12.

The full chain requires all five program ports. An unported step returns
RC 12 (it is non-gating) and its output is replaced with the golden dataset, so
downstream steps still run. `steps.tsv` marks it `golden-substituted`. To run pricing alone,
invoke `java -jar target/los-java.jar LNPRC010` with DD_APPSIN, DD_RATESIN and
DD_PRICED set to the application input, the rate sheet and the output path.
