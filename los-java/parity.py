"""Legacy dataset report plus port-aware gating and byte-exact SYSOUT checks."""
import argparse
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
LEGACY = HERE.parent / "legacy-los"
PROGRAMS = ["LNPRC010", "LNUWR020", "LNFIS030", "LNGL040", "LNHMD050"]


def check(out, chain_rc):
    report = out / "parity.md"
    result = subprocess.run(
        [sys.executable, str(LEGACY / "tools/parity.py"), str(out),
         "--report", str(report)], check=False)
    if result.returncode not in (0, 1):
        return result.returncode
    ports = dict(line.split("\t") for line in (out / "ports.tsv").read_text().splitlines())
    steps = dict(line.split("\t") for line in (out / "steps.tsv").read_text().splitlines())
    date = (LEGACY / "data/input/RUNDATE.dat").read_text()[:8]
    names = ["PRICED.dat", "DECISION.dat", "FISACCT.dat",
             f"LOS_GL_{date}.dat", f"LOS_HMDA_{date[:6]}.csv"]
    lines = ["", "## Java port-aware gate", "",
             "| Dataset | Producer | Result | Gate |",
             "| --- | --- | --- | --- |"]
    failed = False
    for program, name in zip(PROGRAMS, names):
        ported = ports[program] == "true"
        matches = ((out / name).is_file()
                   and (out / name).read_bytes() == (LEGACY / "golden" / name).read_bytes())
        ok = matches and steps[program] == "0"
        failed |= ported and not ok
        status = "PASS" if ok else "FAIL"
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
            ok = ([line for line in actual_lines if line.startswith(prefix)]
                  == [line for line in expected_lines if line.startswith(prefix)])
            failed |= not ok
            lines.append(f"| SYSOUT.txt ({program}) | {program} | "
                         f"{'PASS' if ok else 'FAIL'} | enforced |")
    if all(ports[p] == "true" for p in PROGRAMS):
        failed |= not matches or chain_rc != 0
    lines += ["", f"Java parity gate: {'FAIL' if failed else 'PASS'}",
              "Unported datasets remain FAIL until implemented; their mismatches do not fail CI."]
    addition = "\n".join(lines) + "\n"
    with report.open("a") as stream:
        stream.write(addition)
    print(addition)
    return int(failed)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("out", type=Path)
    parser.add_argument("--chain-rc", type=int, required=True)
    args = parser.parse_args()
    sys.exit(check(args.out, args.chain_rc))
