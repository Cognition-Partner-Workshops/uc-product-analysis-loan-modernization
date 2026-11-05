"""Legacy dataset report plus port-aware gating and byte-exact SYSOUT checks."""
import argparse
import runpy
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
LEGACY = HERE.parent / "legacy-los"
PROGRAMS = ["LNPRC010", "LNUWR020", "LNFIS030", "LNGL040", "LNHMD050"]


def check(out, chain_rc):
    report = out / "parity.md"
    legacy = runpy.run_path(str(LEGACY / "tools/parity.py"))
    ports = dict(line.split("\t") for line in (out / "ports.tsv").read_text().splitlines())
    rows = [line.split("\t") for line in (out / "steps.tsv").read_text().splitlines()]
    steps = {row[0]: row[1] for row in rows}
    sources = {row[0]: row[3] if len(row) == 4 else "java" for row in rows}
    date = (LEGACY / "data/input/RUNDATE.dat").read_text()[:8]
    names = ["PRICED.dat", "DECISION.dat", "FISACCT.dat",
             f"LOS_GL_{date}.dat", f"LOS_HMDA_{date[:6]}.csv"]
    comparison = ["## Dataset comparison", "",
                  "| Dataset | Records | Match | Mismatch | Source |",
                  "| --- | --- | --- | --- | --- |"]
    lines = ["", "## Java port-aware gate", "",
             "| Dataset | Producer | Result | Gate |",
             "| --- | --- | --- | --- |"]
    failed = False
    details = []
    for program, name in zip(PROGRAMS, names):
        ported = ports[program] == "true"
        substituted = sources[program] == "golden-substituted"
        if substituted:
            comparison.append(f"| {name} | — | — | — | golden-substituted |")
        else:
            total, matched, bad, samples = legacy["compare"](LEGACY / "golden" / name, out / name)
            comparison.append(f"| {name} | {total} | {matched} | {bad} | java |")
            for record, expected, actual_record in samples:
                details.append(f"- {name} record {record}: legacy `{expected!r}`, "
                               f"candidate `{actual_record!r}`")
        matches = (not substituted and (out / name).is_file()
                   and (out / name).read_bytes() == (LEGACY / "golden" / name).read_bytes())
        ok = matches and steps[program] == "0"
        failed |= ported and not ok
        status = "golden-substituted" if substituted else ("PASS" if ok and ported else "FAIL")
        gate = "enforced" if ported else "not ported (non-gating)"
        lines.append(f"| {name} | {program} (RC {steps[program]}) | {status} | {gate} |")

    golden = (LEGACY / "golden/SYSOUT.txt").read_bytes()
    actual = (out / "SYSOUT.txt").read_bytes()
    matches = actual == golden
    # Whole-job equality is reported even while individual unported sections are absent.
    lines.append(f"| SYSOUT.txt | whole chain | {'PASS' if matches else 'FAIL'} | "
                 "ported sections enforced; whole file enforced when all ported |")
    expected_lines = golden.splitlines(keepends=True)
    actual_lines = actual.splitlines(keepends=True)
    header_ok = actual_lines[:1] == expected_lines[:1]
    failed |= not header_ok
    for program in PROGRAMS:
        if ports[program] == "true":
            prefix = program.encode() + b" "
            expected = b"".join(line for line in expected_lines if line.startswith(prefix))
            captured = out / "sysout" / f"{program}.txt"
            ok = captured.is_file() and captured.read_bytes() == expected
            failed |= not ok
            lines.append(f"| SYSOUT.txt ({program}) | {program} | "
                         f"{'PASS' if ok else 'FAIL'} | enforced |")
    if all(ports[p] == "true" for p in PROGRAMS):
        failed |= not matches or chain_rc != 0
    lines += ["", f"Java parity gate: {'FAIL' if failed else 'PASS'}",
              "Unported datasets are non-gating; golden-substituted datasets are not compared as Java output."]
    addition = "\n".join(comparison + ([""] + details if details else []) + lines) + "\n"
    report.write_text(addition)
    print(addition)
    return int(failed)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("out", type=Path)
    parser.add_argument("--chain-rc", type=int, required=True)
    args = parser.parse_args()
    sys.exit(check(args.out, args.chain_rc))
