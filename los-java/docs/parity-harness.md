# Java parity harness

From `los-java/`:

```sh
./mvnw -B verify
make build
make run OUT=out/java
make parity OUT=out/java
```

On hosts requiring the Google Maven mirror, configure Maven Central's mirror in
`~/.m2/settings.xml` to `https://maven-central.storage-download.googleapis.com/maven2`
and prefix Maven/make commands with
`MVNW_REPOURL=https://maven-central.storage-download.googleapis.com/maven2`.

The runner also works from any directory:
`/absolute/path/to/los-java/run-chain.sh /absolute/output/directory`.
Build the jar first. Relative output paths are relative to the caller's cwd.
The runner exits nonzero if any step fails, but attempts all five steps.
The parity target always follows the runner with
`python3 parity.py OUT --chain-rc RC`, which calls the unchanged legacy
`tools/parity.py OUT --report OUT/parity.md` and appends the Java gate.

All steps receive these DD environment variables (paths resolved absolutely):

| DD | Path |
| --- | --- |
| DD_APPSIN | legacy-los/data/input/APPS.dat |
| DD_RATESIN | legacy-los/data/input/RATESHT.dat |
| DD_RUNDATE | legacy-los/data/input/RUNDATE.dat |
| DD_PRICED | OUT/PRICED.dat |
| DD_DECISION | OUT/DECISION.dat |
| DD_FISOUT | OUT/FISACCT.dat |
| DD_GLOUT | OUT/LOS_GL_<RUNDATE first 8 bytes>.dat |
| DD_HMDAOUT | OUT/LOS_HMDA_<RUNDATE first 6 bytes>.csv |

| Invocation (in order) | Input DDs | Output DD |
| --- | --- | --- |
| java -jar target/los-java.jar LNPRC010 | APPSIN, RATESIN | PRICED |
| java -jar target/los-java.jar LNUWR020 | RUNDATE, PRICED | DECISION |
| java -jar target/los-java.jar LNFIS030 | RUNDATE, PRICED, DECISION | FISOUT |
| java -jar target/los-java.jar LNGL040 | RUNDATE, DECISION | GLOUT |
| java -jar target/los-java.jar LNHMD050 | RATESIN, PRICED, DECISION | HMDAOUT |

SYSOUT contains only the run-date header and program stdout; stderr and return
codes go to stderr.txt and steps.tsv. Previous chain datasets are removed before
running, preventing stale files from masking failures.

PortStatus derives readiness from the actual jar's implementations: a program
extending NotYetPorted is unported; replacing that base with BatchProgram enables
the gate and its isolated JUnit test automatically. ports.tsv records this
classification, independently of return code (a real RC 12 cannot bypass gating).
Downstream stubs can return RC 16 because upstream output is absent; they remain
unported. Ported outputs must match bytes and return RC 0 even if their upstream
is not yet ported. Isolated JUnit tests use golden upstream data to distinguish
implementation failures from chain dependency failures.

The report always shows byte equality for all five datasets and whole SYSOUT.
Unported mismatches are explicitly FAIL/non-gating. For partially ported chains,
SYSOUT lines prefixed by each ported program name plus a space are compared
byte-for-byte, including line ends and spacing; the header is always enforced.
Once all programs are ported, whole-file SYSOUT equality is enforced too, catching
unexpected lines or ordering changes. SYSOUT is checked by the chain harness,
not by the five isolated dataset tests.

`make parity` also runs six Python gate regression tests. CI runs Maven verify,
then parity, and uploads the whole output directory even when parity fails.
