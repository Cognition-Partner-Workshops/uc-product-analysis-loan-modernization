#!/usr/bin/env bash
# Runs the LOS nightly chain (jcl/LOSNIGHT.jcl) on GnuCOBOL.
# Usage: ./run-chain.sh [OUT_DIR]   default out/legacy
# Each DD name in the JCL is an environment variable here (GnuCOBOL maps
# ASSIGN TO <name> to $DD_<name>).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="${1:-$HERE/out/legacy}"
BIN="$HERE/out/bin"
IN="$HERE/data/input"
mkdir -p "$OUT" "$BIN"
RUN_DATE="$(cut -c1-8 "$IN/RUNDATE.dat")"

for prog in LNPRC010 LNUWR020 LNFIS030 LNGL040 LNHMD050; do
  cobc -x -I "$HERE/copybooks" -o "$BIN/$prog" "$HERE/src/$prog.cbl"
done

export DD_APPSIN="$IN/APPS.dat" DD_RATESIN="$IN/RATESHT.dat" DD_RUNDATE="$IN/RUNDATE.dat"
export DD_PRICED="$OUT/PRICED.dat" DD_DECISION="$OUT/DECISION.dat"
export DD_FISOUT="$OUT/FISACCT.dat" DD_GLOUT="$OUT/LOS_GL_${RUN_DATE}.dat"
export DD_HMDAOUT="$OUT/LOS_HMDA_${RUN_DATE:0:6}.csv"

{
  echo "LOSNIGHT RUN DATE $RUN_DATE"
  # fixed-width datasets keep their trailing filler, as on the mainframe
  COB_LS_FIXED=TRUE "$BIN/LNPRC010"
  COB_LS_FIXED=TRUE "$BIN/LNUWR020"
  COB_LS_FIXED=TRUE "$BIN/LNFIS030"
  COB_LS_FIXED=TRUE "$BIN/LNGL040"
  "$BIN/LNHMD050"
} | tee "$OUT/SYSOUT.txt"
