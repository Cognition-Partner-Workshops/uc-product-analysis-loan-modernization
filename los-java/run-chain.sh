#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$HERE/out/java}"
mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"
IN="$HERE/../legacy-los/data/input"
RUN_DATE="$(cut -c1-8 "$IN/RUNDATE.dat")"
export DD_APPSIN="$IN/APPS.dat" DD_RATESIN="$IN/RATESHT.dat" DD_RUNDATE="$IN/RUNDATE.dat"
export DD_PRICED="$OUT/PRICED.dat" DD_DECISION="$OUT/DECISION.dat"
export DD_FISOUT="$OUT/FISACCT.dat" DD_GLOUT="$OUT/LOS_GL_${RUN_DATE}.dat"
export DD_HMDAOUT="$OUT/LOS_HMDA_${RUN_DATE:0:6}.csv"
# Remove only this chain's products so a previous successful run cannot mask missing output.
rm -f "$DD_PRICED" "$DD_DECISION" "$DD_FISOUT" "$DD_GLOUT" "$DD_HMDAOUT"
java -cp "$HERE/target/los-java.jar" los.program.PortStatus > "$OUT/ports.tsv"
: > "$OUT/steps.tsv"
: > "$OUT/stderr.txt"
echo "LOSNIGHT RUN DATE $RUN_DATE" > "$OUT/SYSOUT.txt"
mkdir -p "$OUT/sysout"
failed=0
for prog in LNPRC010 LNUWR020 LNFIS030 LNGL040 LNHMD050; do
  rc=0
  java -jar "$HERE/target/los-java.jar" "$prog" > "$OUT/sysout/$prog.txt" 2>> "$OUT/stderr.txt" || rc=$?
  cat "$OUT/sysout/$prog.txt" >> "$OUT/SYSOUT.txt"
  printf '%s\t%s\n' "$prog" "$rc" >> "$OUT/steps.tsv"
  echo "$prog RC=$rc"
  if (( rc != 0 )); then failed=1; fi
done
cat "$OUT/SYSOUT.txt"
exit "$failed"
