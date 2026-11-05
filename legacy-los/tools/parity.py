"""Compares a candidate run of the LOS nightly chain with the legacy golden files.

Usage: python3 tools/parity.py CANDIDATE_DIR [--golden DIR] [--report FILE]

Every golden dataset is compared record by record and byte for byte. The
report lists one row per dataset (records, matches, mismatches) and the first
differing records with the byte columns that differ, so a reviewer can see
which field moved. Exit status is 0 only when every dataset matches.
"""
import argparse
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent.parent
DATASETS = ["PRICED.dat", "DECISION.dat", "FISACCT.dat",
            "LOS_GL_20261105.dat", "LOS_HMDA_202611.csv"]


def diff_cols(a, b):
    width = max(len(a), len(b))
    cols = [i + 1 for i in range(width)
            if (a[i] if i < len(a) else None) != (b[i] if i < len(b) else None)]
    if not cols:
        return ""
    return f"{cols[0]}-{cols[-1]}" if len(cols) > 1 else str(cols[0])


def compare(golden, candidate):
    g = golden.read_text().splitlines()
    if not candidate.exists():
        return len(g), 0, len(g), [("missing", "", "")]
    c = candidate.read_text().splitlines()
    matches, samples = 0, []
    for i in range(max(len(g), len(c))):
        gl = g[i] if i < len(g) else "<no record>"
        cl = c[i] if i < len(c) else "<no record>"
        if gl == cl:
            matches += 1
        elif len(samples) < 5:
            samples.append((i + 1, gl, cl))
    total = max(len(g), len(c))
    return total, matches, total - matches, samples


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("candidate")
    ap.add_argument("--golden", default=str(HERE / "golden"))
    ap.add_argument("--report", default=None)
    args = ap.parse_args()
    gdir, cdir = Path(args.golden), Path(args.candidate)
    lines = ["| Dataset | Records | Match | Mismatch |", "| --- | --- | --- | --- |"]
    details, failed = [], False
    for name in DATASETS:
        total, ok, bad, samples = compare(gdir / name, cdir / name)
        failed |= bad > 0
        lines.append(f"| {name} | {total} | {ok} | {bad} |")
        for s in samples:
            if s[0] == "missing":
                details.append(f"- {name}: candidate file missing")
            else:
                details.append(f"- {name} record {s[0]}, columns {diff_cols(s[1], s[2])}\n"
                               f"  - legacy:    `{s[1]}`\n  - candidate: `{s[2]}`")
    verdict = "FAIL" if failed else "PASS"
    report = "\n".join([f"Parity against legacy golden files: {verdict}", ""] + lines
                       + ([""] + details if details else [])) + "\n"
    print(report)
    if args.report:
        Path(args.report).write_text(report)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
