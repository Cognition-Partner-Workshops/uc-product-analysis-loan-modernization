#!/usr/bin/env bash
# Usage: ./run-chain.sh [OUT_DIR] [IN_DIR]   defaults: out/java, ../legacy-los/data/input
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$HERE/out/java}"
mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"
IN="$(cd "${2:-$HERE/../legacy-los/data/input}" && pwd)"
RUN_DATE="$(cut -c1-8 "$IN/RUNDATE.dat")"
export DD_APPSIN="$IN/APPS.dat" DD_RATESIN="$IN/RATESHT.dat" DD_RUNDATE="$IN/RUNDATE.dat"
export DD_PRICED="$OUT/PRICED.dat" DD_DECISION="$OUT/DECISION.dat"
export DD_FISOUT="$OUT/FISACCT.dat" DD_GLOUT="$OUT/LOS_GL_${RUN_DATE}.dat"
export DD_HMDAOUT="$OUT/LOS_HMDA_${RUN_DATE:0:6}.csv"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
# Remove only this chain's products so a previous successful run cannot mask missing output.
rm -f "$DD_PRICED" "$DD_DECISION" "$DD_FISOUT" "$DD_GLOUT" "$DD_HMDAOUT"
"$JAVA" -cp "$HERE/target/los-java.jar" los.program.PortStatus > "$OUT/ports.tsv"
: > "$OUT/steps.tsv"
: > "$OUT/stderr.txt"
echo "LOSNIGHT RUN DATE $RUN_DATE" > "$OUT/SYSOUT.txt"
mkdir -p "$OUT/sysout"
chain_rc=0
for prog in LNPRC010 LNUWR020 LNFIS030 LNGL040 LNHMD050; do
  rc=0
  if (( chain_rc > 4 )); then
    # JCL COND: a step after RC > 4 does not run; recorded as RC 12 with empty SYSOUT.
    rc=12
    : > "$OUT/sysout/$prog.txt"
    echo "$prog SKIPPED (prior RC $chain_rc)" >> "$OUT/stderr.txt"
  else
    "$JAVA" -jar "$HERE/target/los-java.jar" "$prog" > "$OUT/sysout/$prog.txt" 2>> "$OUT/stderr.txt" || rc=$?
  fi
  cat "$OUT/sysout/$prog.txt" >> "$OUT/SYSOUT.txt"
  printf '%s\t%s\n' "$prog" "$rc" >> "$OUT/steps.tsv"
  echo "$prog RC=$rc"
  if (( rc > chain_rc )); then chain_rc=$rc; fi
done
cat "$OUT/SYSOUT.txt"
exit "$chain_rc"
