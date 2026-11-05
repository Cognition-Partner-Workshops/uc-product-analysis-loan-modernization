import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from parity import HERE, LEGACY, PROGRAMS


class ParityGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.out = Path(self.temp.name)
        for source in (LEGACY / "golden").iterdir():
            if source.is_file():
                shutil.copyfile(source, self.out / source.name)
        self.status(True, 0)
        (self.out / "sysout").mkdir()
        golden_lines = (self.out / "SYSOUT.txt").read_bytes().splitlines(keepends=True)
        for program in PROGRAMS:
            (self.out / "sysout" / f"{program}.txt").write_bytes(
                b"".join(line for line in golden_lines
                         if line.startswith(program.encode() + b" ")))

    def status(self, ported, rc):
        (self.out / "ports.tsv").write_text(
            "".join(f"{p}\t{str(ported).lower()}\n" for p in PROGRAMS))
        (self.out / "steps.tsv").write_text("".join(f"{p}\t{rc}\n" for p in PROGRAMS))

    def gate(self):
        return subprocess.run(
            [sys.executable, str(HERE / "parity.py"), str(self.out), "--chain-rc", "0"],
            capture_output=True, text=True, check=False).returncode

    def test_matching_ported_chain(self):
        self.assertEqual(0, self.gate())

    def pricing_only(self):
        self.status(False, 12)
        (self.out / "ports.tsv").write_text(
            "".join(f"{p}\t{str(p == 'LNPRC010').lower()}\n" for p in PROGRAMS))
        (self.out / "steps.tsv").write_text(
            "".join(f"{p}\t{0 if p == 'LNPRC010' else 12}\n" for p in PROGRAMS))
        captured = self.out / "sysout/LNPRC010.txt"
        (self.out / "SYSOUT.txt").write_bytes(
            b"LOSNIGHT RUN DATE 20261105\n" + captured.read_bytes())
        return captured

    def test_matching_partially_ported_sysout(self):
        self.pricing_only()
        self.assertEqual(0, self.gate())

    def test_partially_ported_extra_unprefixed_sysout(self):
        captured = self.pricing_only()
        captured.write_bytes(captured.read_bytes() + b"warning\n")
        with (self.out / "SYSOUT.txt").open("ab") as stream:
            stream.write(b"warning\n")
        self.assertEqual(1, self.gate())
        self.assertIn("| SYSOUT.txt (LNPRC010) | LNPRC010 | FAIL | enforced |",
                      (self.out / "parity.md").read_text())

    def test_partially_ported_invalid_captured_sysout(self):
        captured = self.pricing_only()
        correct = captured.read_bytes()
        for invalid in [b"", b"".join(reversed(correct.splitlines(keepends=True))),
                        correct.replace(b"\n", b"\r\n")]:
            with self.subTest(stdout=invalid):
                captured.write_bytes(invalid)
                self.assertEqual(1, self.gate())
        captured.unlink()
        self.assertEqual(1, self.gate())

    def test_unported_extra_sysout_is_non_gating(self):
        self.pricing_only()
        (self.out / "sysout/LNUWR020.txt").write_bytes(b"warning\n")
        with (self.out / "SYSOUT.txt").open("ab") as stream:
            stream.write(b"warning\n")
        self.assertEqual(0, self.gate())

    def test_empty_unported_chain(self):
        self.status(False, 12)
        for name in ["PRICED.dat", "DECISION.dat", "FISACCT.dat",
                     "LOS_GL_20261105.dat", "LOS_HMDA_202611.csv"]:
            (self.out / name).unlink()
        (self.out / "SYSOUT.txt").write_bytes(b"LOSNIGHT RUN DATE 20261105\n")
        self.assertEqual(0, self.gate())
        self.assertIn("not ported (non-gating)", (self.out / "parity.md").read_text())

    def test_missing_ported_output(self):
        (self.out / "PRICED.dat").unlink()
        self.assertEqual(1, self.gate())

    def test_wrong_ported_output(self):
        (self.out / "PRICED.dat").write_bytes(b"wrong\n")
        self.assertEqual(1, self.gate())

    def test_sysout_bytes(self):
        with (self.out / "SYSOUT.txt").open("ab") as stream:
            stream.write(b"unexpected\n")
        self.assertEqual(1, self.gate())

    def test_ported_abend_even_with_matching_output(self):
        self.status(True, 12)
        self.assertEqual(1, self.gate())


if __name__ == "__main__":
    unittest.main()
