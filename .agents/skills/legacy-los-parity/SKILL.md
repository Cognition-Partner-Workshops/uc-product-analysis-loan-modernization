---
name: legacy-los-parity
description: Build, run and parity-check the legacy loan origination nightly batch in legacy-los/. Use when migrating any of its COBOL programs or when proving a new implementation matches the legacy output.
---

# Legacy LOS nightly batch

`legacy-los/` is the loan origination nightly chain as it runs today: five
COBOL programs (`src/`), their record layouts (`copybooks/`), the job that
orders them (`jcl/LOSNIGHT.jcl`), one recorded night of inputs
(`data/input/`) and the output that night produced (`golden/`).

| Step | Program | Reads | Writes |
| --- | --- | --- | --- |
| 010 | LNPRC010 pricing | APPS, RATESHT | PRICED |
| 020 | LNUWR020 underwriting | RUNDATE, PRICED | DECISION |
| 030 | LNFIS030 core banking | RUNDATE, PRICED, DECISION | FISACCT |
| 040 | LNGL040 GL extract | RUNDATE, DECISION | LOS_GL_<date> |
| 050 | LNHMD050 HMDA extract | RATESHT, PRICED, DECISION | LOS_HMDA_<yyyymm>.csv |

## Commands

- `sudo apt-get install -y gnucobol` once per machine.
- `cd legacy-los && make golden-check` compiles and runs the chain and must
  report PASS on all five datasets. CI runs the same target.
- `make parity CANDIDATE=<dir>` compares a new implementation's output
  directory with `golden/` record by record and writes `<dir>/parity.md`.

## Rules for a migration

- The golden files are the contract. A new implementation reads the same
  `data/input/` files and writes the same five datasets byte for byte.
- Keep the legacy behaviour, including the odd parts, unless a ticket says
  otherwise: the monthly rate is cut (not rounded) to 12 decimals before the
  payment formula while the payment and DTI are rounded half up, the
  30-day credit-score window that sends stale scores to `R`, the jumbo cut at
  exactly 500,000, extensions past the third adding 0.12% instead of 0.125%
  because `WS-ADDON-PCT` holds two decimals (the BRD's rounding issue), and
  the HMDA rate spread taken against the 0-day APOR row.
- `FISACCT.dat` is the ASCII image the program writes; the MQ channel
  converts it to EBCDIC and strips the line ends on the way to core banking.
  Parity is against the ASCII image, so a new implementation writes ASCII too.
- Never edit `golden/` or `data/input/` to make parity pass. If the legacy
  output is wrong, say so in the PR and leave the file alone.
- Leave `legacy-los/` itself unchanged; new code goes in its own folder.
