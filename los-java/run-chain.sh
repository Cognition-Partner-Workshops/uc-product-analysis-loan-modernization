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
halt_rc=0
products=("$DD_PRICED" "$DD_DECISION" "$DD_FISOUT" "$DD_GLOUT" "$DD_HMDAOUT")
index=0
for prog in LNPRC010 LNUWR020 LNFIS030 LNGL040 LNHMD050; do
  rc=0
  product="${products[$index]}"
  ported="$(awk -F '\t' -v p="$prog" '$1 == p {print $2}' "$OUT/ports.tsv")"
  if (( halt_rc > 4 )); then
    # JCL COND: a step after a ported step's RC > 4 does not run; recorded as RC 12 with empty SYSOUT.
    rc=12
    source=skipped
    : > "$OUT/sysout/$prog.txt"
    echo "$prog SKIPPED (prior RC $halt_rc)" >> "$OUT/stderr.txt"
  else
    "$JAVA" -jar "$HERE/target/los-java.jar" "$prog" > "$OUT/sysout/$prog.txt" 2>> "$OUT/stderr.txt" || rc=$?
    source=java
    if [[ "$ported" == false ]]; then
      # Unported producers are fed downstream from the immutable golden dataset.
      cp "$HERE/../legacy-los/golden/$(basename "$product")" "$product"
      source=golden-substituted
    elif (( rc > halt_rc )); then
      halt_rc=$rc
    fi
  fi
  cat "$OUT/sysout/$prog.txt" >> "$OUT/SYSOUT.txt"
  printf '%s\t%s\t%s\t%s\n' "$prog" "$rc" "$(basename "$product")" "$source" >> "$OUT/steps.tsv"
  index=$((index + 1))
  echo "$prog RC=$rc"
  if (( rc > chain_rc )); then chain_rc=$rc; fi
done
cat "$OUT/SYSOUT.txt"
exit "$chain_rc"
