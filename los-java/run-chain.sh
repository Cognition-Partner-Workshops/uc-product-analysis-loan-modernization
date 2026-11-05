#!/usr/bin/env bash
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$HERE/target/out/java}"
IN="${2:-$HERE/../legacy-los/data/input}"
mkdir -p "$OUT"
RUN_DATE="$(cut -c1-8 "$IN/RUNDATE.dat")"
export DD_APPSIN="$IN/APPS.dat" DD_RATESIN="$IN/RATESHT.dat" DD_RUNDATE="$IN/RUNDATE.dat"
export DD_PRICED="$OUT/PRICED.dat" DD_DECISION="$OUT/DECISION.dat"
export DD_FISOUT="$OUT/FISACCT.dat" DD_GLOUT="$OUT/LOS_GL_${RUN_DATE}.dat"
export DD_HMDAOUT="$OUT/LOS_HMDA_${RUN_DATE:0:6}.csv"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
{
  echo "LOSNIGHT RUN DATE $RUN_DATE"
  rc=0
  for prog in LNPRC010 LNUWR020 LNFIS030 LNGL040 LNHMD050; do
    step_rc=0
    "$JAVA" -jar "$HERE/target/los-java.jar" "$prog" || step_rc=$?
    if (( step_rc > rc )); then rc=$step_rc; fi
    if (( rc > 4 )); then break; fi
  done
  exit "$rc"
} | tee "$OUT/SYSOUT.txt"
