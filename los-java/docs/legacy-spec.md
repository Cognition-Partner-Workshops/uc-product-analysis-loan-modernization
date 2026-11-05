# Legacy LOS nightly batch: field and rule specification

Reference for the Java migration of `legacy-los/`. Every rule cites the COBOL
source as `PROGRAM:line` (line numbers are those of `legacy-los/src/<PROGRAM>.cbl`
or `legacy-los/copybooks/<COPYBOOK>.cpy` on branch `legacy-los`). The golden
files in `legacy-los/golden/` are the contract; where this document and the
golden files disagree, the golden files win and this document is wrong.

Behaviours marked **DELIBERATE** are listed in
`.agents/skills/legacy-los-parity/SKILL.md` and must be kept even where they
look like bugs. Behaviours marked **KEEP** are further truncation, rounding,
filler or ordering effects found in the source; they are also part of the
byte-for-byte contract.

Contents

1. [Chain, files and DD mapping](#1-chain-files-and-dd-mapping)
2. [Common conventions](#2-common-conventions)
3. [Record layouts](#3-record-layouts)
4. [LNPRC010 pricing](#4-lnprc010--step010-risk-based-pricing)
5. [LNUWR020 underwriting](#5-lnuwr020--step020-automated-underwriting)
6. [LNFIS030 core banking](#6-lnfis030--step030-core-banking-account-create)
7. [LNGL040 GL extract](#7-lngl040--step040-general-ledger-extract)
8. [LNHMD050 HMDA extract](#8-lnhmd050--step050-hmda-extract)
9. [SYSOUT control totals](#9-sysout-control-totals)
10. [Deliberate behaviours: checklist](#10-deliberate-behaviours-checklist)
11. [How these rules were checked](#11-how-these-rules-were-checked)

---

## 1. Chain, files and DD mapping

Order and DD names come from `jcl/LOSNIGHT.jcl`; `run-chain.sh` maps each DD
name to an environment variable `DD_<name>` (GnuCOBOL resolves
`ASSIGN TO <name>` through `$DD_<name>`).

| Step | Program | DD (env var) | Dir | Dataset (run-chain.sh) | Record | JCL DCB |
| --- | --- | --- | --- | --- | --- | --- |
| 010 | LNPRC010 | `APPSIN` (`DD_APPSIN`) | in | `data/input/APPS.dat` | LNAPPREC 150 | |
| | | `RATESIN` (`DD_RATESIN`) | in | `data/input/RATESHT.dat` | LNRATE 40 | |
| | | `PRICED` (`DD_PRICED`) | out | `<out>/PRICED.dat` | LNPRCREC 200 | FB 200 |
| 020 | LNUWR020 | `RUNDATE` (`DD_RUNDATE`) | in | `data/input/RUNDATE.dat` | RUNDATE-REC 80 | |
| | | `PRICED` | in | `<out>/PRICED.dat` | LNPRCREC 200 | |
| | | `DECISION` (`DD_DECISION`) | out | `<out>/DECISION.dat` | LNDECREC 80 | FB 80 |
| 030 | LNFIS030 | `RUNDATE`, `PRICED`, `DECISION` | in | as above | | |
| | | `FISOUT` (`DD_FISOUT`) | out | `<out>/FISACCT.dat` | FISACCT 114 | FB 114 |
| 040 | LNGL040 | `RUNDATE`, `DECISION` | in | as above | | |
| | | `GLOUT` (`DD_GLOUT`) | out | `<out>/LOS_GL_<YYYYMMDD>.dat` | LNGLREC 85 | FB 85 |
| 050 | LNHMD050 | `RATESIN`, `PRICED`, `DECISION` | in | as above | | |
| | | `HMDAOUT` (`DD_HMDAOUT`) | out | `<out>/LOS_HMDA_<YYYYMM>.csv` | HMDA-LINE 160, variable | VB 164 |

- `<YYYYMMDD>` is columns 1-8 of `RUNDATE.dat`; `<YYYYMM>` its first six
  characters (`run-chain.sh` lines 12, 20-21). For the recorded night that is
  `LOS_GL_20261105.dat` and `LOS_HMDA_202611.csv`.
- Steps 020-050 run with `COND=(4,LT)`: a step is skipped if any prior step
  ended with RC > 4. Only LNGL040 sets a non-zero RC (12, section 7).
- SYSOUT of all five programs, preceded by the line
  `LOSNIGHT RUN DATE <YYYYMMDD>` written by `run-chain.sh` (line 24), is
  `SYSOUT.txt` (section 9).

## 2. Common conventions

These hold for every dataset unless a section says otherwise.

- **Encoding.** All files are ASCII, one record per line, terminated by a
  single LF (`\n`); no CR. Parity (`tools/parity.py`) compares raw bytes
  including the terminator.
- **Fixed width (KEEP).** Steps 010-040 run with `COB_LS_FIXED=TRUE`
  (`run-chain.sh` lines 25-29), so PRICED, DECISION, FISACCT and GL records
  are written at full record length, trailing spaces included. LNHMD050 runs
  without it (line 30), so each HMDA line has its trailing spaces stripped.
- **Unsigned numeric `PIC 9(n)V9(m)`.** Zoned display, ASCII digits, no sign
  character, implied decimal point (no `.` in the data), right-justified,
  left-padded with `0`. A value of 3,200.00 in `9(9)V99` is `00000320000`.
- **Signed `PIC S9(n)V9(m) SIGN LEADING SEPARATE`.** One leading sign
  character (`+` or `-`; zero is written `+`) followed by the digits. Only
  `PRC-RISK-ADJ` uses it.
- **Alphanumeric `PIC X(n)`.** Left-justified, right-padded with spaces;
  longer values are cut on the right.
- **MOVE truncation.** A numeric MOVE or COMPUTE into a smaller field drops
  excess high-order integer digits and excess low-order decimal digits
  (decimals are truncated, not rounded) unless `ROUNDED` is coded. No
  COMPUTE in the chain has `ON SIZE ERROR` except the DTI (LNUWR020:116).
- **ROUNDED.** GnuCOBOL's default rounding is NEAREST-AWAY-FROM-ZERO, i.e.
  Java `RoundingMode.HALF_UP` on `BigDecimal` (also for negative values).
- **FILLER (KEEP).** Output records are blanked with `MOVE SPACES` before
  `INITIALIZE` (LNPRC010:90-91, LNUWR020:75-76) or `MOVE SPACES` alone
  (LNFIS030:86), so every FILLER is spaces and `INITIALIZE` sets numeric
  fields to zeros and alphanumerics to spaces. GL records have no FILLER.
- **Dates** are `PIC 9(8)` `YYYYMMDD`. Day arithmetic uses
  `FUNCTION INTEGER-OF-DATE` / `DATE-OF-INTEGER` (proleptic Gregorian day
  numbers), i.e. `java.time.LocalDate` day arithmetic.

## 3. Record layouts

Offsets are 1-based byte positions. "Dec" is the number of implied decimals.

### 3.1 LNAPPREC: application intake (APPSIN), 150 bytes

`copybooks/LNAPPREC.cpy`. Also the first 150 bytes of every PRICED record
(`PRC-APP`), and the WORKING-STORAGE copy LNUWR020, LNFIS030 and LNHMD050
fill from it.

| Field | From | To | Len | PIC | Dec | Sign | Padding / notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| APP-ID | 1 | 10 | 10 | 9(10) | 0 | none | zero-left |
| BORR-ID | 11 | 19 | 9 | 9(9) | 0 | none | zero-left; not used downstream |
| BORR-TIN | 20 | 28 | 9 | 9(9) | 0 | none | zero-left; becomes FIS `BORROWER-SSN` |
| BORR-LAST-NAME | 29 | 43 | 15 | X(15) | | | space-right |
| BORR-FIRST-NAME | 44 | 55 | 12 | X(12) | | | space-right |
| LOAN-TYPE | 56 | 58 | 3 | X(3) | | | `CNV` (88 LOAN-IS-CNV), `FHA` (88 LOAN-IS-FHA) |
| LOAN-PURPOSE | 59 | 59 | 1 | X(1) | | | `P` purchase, `R` refinance in the data |
| LOAN-AMT | 60 | 70 | 11 | 9(9)V99 | 2 | none | zero-left |
| TERM-MONTHS | 71 | 73 | 3 | 9(3) | 0 | none | |
| PROP-STATE | 74 | 75 | 2 | X(2) | | | |
| PROP-TYPE | 76 | 76 | 1 | X(1) | | | passed to HMDA as is |
| ANNUAL-INCOME | 77 | 87 | 11 | 9(9)V99 | 2 | none | |
| MONTHLY-DEBT | 88 | 96 | 9 | 9(7)V99 | 2 | none | |
| CREDIT-SCORE | 97 | 99 | 3 | 9(3) | 0 | none | |
| SCORE-DATE | 100 | 107 | 8 | 9(8) | 0 | none | YYYYMMDD of the bureau pull |
| EMP-YEARS | 108 | 110 | 3 | 9(2)V9 | 1 | none | `019` = 1.9 years |
| LOCK-DAYS | 111 | 113 | 3 | 9(3) | 0 | none | must match a rate-sheet lock period |
| LOCK-EXTS | 114 | 115 | 2 | 9(2) | 0 | none | number of lock extensions, 0-99 |
| APP-DATE | 116 | 123 | 8 | 9(8) | 0 | none | not used downstream |
| APPL-SEX | 124 | 124 | 1 | X(1) | | | HMDA code, passed as is |
| APPL-RACE | 125 | 125 | 1 | X(1) | | | HMDA code, passed as is |
| FILLER | 126 | 150 | 25 | X(25) | | | spaces in input; copied byte for byte into PRICED |

### 3.2 LNRATE: daily rate sheet (RATESIN), 40 bytes

`copybooks/LNRATE.cpy`.

| Field | From | To | Len | PIC | Dec | Sign | Padding / notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| RATE-PRODUCT | 1 | 5 | 5 | X(5) | | | `CNV30`, `CNV15`, `FHA30`, or `APOR ` (trailing space) |
| RATE-LOCK-DAYS | 6 | 8 | 3 | 9(3) | 0 | none | 30/45/60 for products, 000 for APOR |
| RATE-BASE | 9 | 14 | 6 | 9(2)V9999 | 4 | none | percent, `061250` = 6.1250% |
| RATE-EFF-DATE | 15 | 22 | 8 | 9(8) | 0 | none | read but never used |
| FILLER | 23 | 40 | 18 | X(18) | | | |

### 3.3 RUNDATE-REC: run date (RUNDATE), 80 bytes

Declared inline in LNUWR020:22-25, LNFIS030:22-25, LNGL040:19-22.
`RD-DATE` 1-8 `9(8)` YYYYMMDD, `FILLER` 9-80 `X(72)`. Only the first record is
read.

### 3.4 LNPRCREC: priced application (PRICED), 200 bytes

`copybooks/LNPRCREC.cpy`. Written by LNPRC010, read by 020, 030, 050.

| Field | From | To | Len | PIC | Dec | Sign | Padding / notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| PRC-APP | 1 | 150 | 150 | X(150) | | | the LNAPPREC record exactly as read |
| PRC-PRODUCT | 151 | 155 | 5 | X(5) | | | `CNV30`/`CNV15`/`FHA30`; spaces when status `E1` |
| PRC-BASE-RATE | 156 | 161 | 6 | 9(2)V9999 | 4 | none | zeros when rejected |
| PRC-RISK-ADJ | 162 | 167 | 6 | S9(1)V9999 SIGN LEADING SEPARATE | 4 | leading `+`/`-` | `-02500` = -0.25, `+00000` = 0 (also on rejects) |
| PRC-FINAL-RATE | 168 | 173 | 6 | 9(2)V9999 | 4 | none | |
| PRC-MONTHLY-PMT | 174 | 182 | 9 | 9(7)V99 | 2 | none | |
| PRC-EXT-FEE-PCT | 183 | 186 | 4 | 9(1)V999 | 3 | none | percent, `0495` = 0.495% |
| PRC-EXT-FEE-AMT | 187 | 195 | 9 | 9(7)V99 | 2 | none | |
| PRC-STATUS | 196 | 197 | 2 | X(2) | | | `00` priced, `E1` no product, `E2` no rate |
| FILLER | 198 | 200 | 3 | X(3) | | | spaces |

### 3.5 LNDECREC: underwriting decision (DECISION), 80 bytes

`copybooks/LNDECREC.cpy`. Written by LNUWR020, read by 030, 040, 050. One
record per PRICED record, same order.

| Field | From | To | Len | PIC | Dec | Sign | Padding / notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| DEC-APP-ID | 1 | 10 | 10 | 9(10) | 0 | none | |
| DEC-CODE | 11 | 11 | 1 | X(1) | | | `A` auto-approve, `M` manual review, `D` decline, `R` credit re-pull |
| DEC-REASON | 12 | 16 | 5 | X(5) | | | `AUTO `, `RVW  `, `EMPL `, `SCORE`, `DTI  `, `PRICE`, `STALE` |
| DEC-LOAN-TYPE | 17 | 19 | 3 | X(3) | | | from LOAN-TYPE |
| DEC-PRODUCT | 20 | 24 | 5 | X(5) | | | from PRC-PRODUCT (spaces for `E1`) |
| DEC-LOAN-AMT | 25 | 35 | 11 | 9(9)V99 | 2 | none | |
| DEC-FINAL-RATE | 36 | 41 | 6 | 9(2)V9999 | 4 | none | |
| DEC-MONTHLY-PMT | 42 | 50 | 9 | 9(7)V99 | 2 | none | |
| DEC-DTI | 51 | 55 | 5 | 9(3)V99 | 2 | none | percent; `99999` = 999.99 sentinel |
| DEC-EXT-FEE-AMT | 56 | 64 | 9 | 9(7)V99 | 2 | none | |
| DEC-DATE | 65 | 72 | 8 | 9(8) | 0 | none | run date |
| FILLER | 73 | 80 | 8 | X(8) | | | spaces |

### 3.6 FISACCT: core banking account create request (FISOUT), 114 bytes

`copybooks/FISACCT.cpy` ("layout owned by the core banking team, do not
change").

| Field | From | To | Len | PIC | Dec | Sign | Padding / notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| MSG-TYPE | 1 | 4 | 4 | X(4) | | | always `ACCT` |
| LOAN-NUMBER | 5 | 16 | 12 | 9(12) | 0 | none | `04` + APP-ID |
| BORROWER-SSN | 17 | 25 | 9 | 9(9) | 0 | none | from BORR-TIN |
| FIS-LOAN-TYPE | 26 | 28 | 3 | X(3) | | | |
| FIS-LOAN-AMOUNT | 29 | 39 | 11 | 9(9)V99 | 2 | none | |
| INTEREST-RATE | 40 | 45 | 6 | 9(2)V9999 | 4 | none | |
| FIS-TERM-MONTHS | 46 | 48 | 3 | 9(3) | 0 | none | |
| FIRST-PMT-DATE | 49 | 56 | 8 | 9(8) | 0 | none | |
| FUNDING-DATE | 57 | 64 | 8 | 9(8) | 0 | none | |
| FILLER | 65 | 114 | 50 | X(50) | | | spaces |

**DELIBERATE: ASCII image.** `FISACCT.dat` is the ASCII image with LF line
ends exactly as the program writes it. On the mainframe path the MQ channel
converts it to EBCDIC and strips the line ends; a new implementation does
**not** do that conversion. Parity is against the ASCII image.

### 3.7 LNGLREC: GL posting line (GLOUT), 85 bytes

`copybooks/LNGLREC.cpy`. No FILLER.

| Field | From | To | Len | PIC | Dec | Sign | Padding / notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| GL-POST-DATE | 1 | 8 | 8 | 9(8) | 0 | none | run date |
| GL-ACCOUNT | 9 | 20 | 12 | 9(12) | 0 | none | |
| GL-DRCR | 21 | 22 | 2 | X(2) | | | `DR` or `CR` |
| GL-AMOUNT | 23 | 37 | 15 | 9(13)V99 | 2 | none | |
| GL-LOAN-NUMBER | 38 | 50 | 13 | 9(13) | 0 | none | `004` + APP-ID (one more leading zero than FIS) |
| GL-DESC | 51 | 80 | 30 | X(30) | | | space-right |
| GL-COST-CENTER | 81 | 85 | 5 | 9(5) | 0 | none | |

### 3.8 HMDA-LINE (HMDAOUT), variable, pipe delimited

LNHMD050:30 declares `HMDA-LINE PIC X(160)`; because the step runs without
`COB_LS_FIXED`, each line is written without trailing spaces. Line 1 is the
header, then one row per `A` or `D` decision:

```
ApplicationID|ActionTaken|ActionDate|LoanType|LoanPurpose|LoanAmount|PropertyType|PropertyState|ApplicantRace|ApplicantSex|ApplicantIncome|RateSpread
```

| # | Column | Source | Format |
| --- | --- | --- | --- |
| 1 | ApplicationID | DEC-APP-ID | 10 digits, zero-left |
| 2 | ActionTaken | DEC-CODE | `1` for A, `3` for D |
| 3 | ActionDate | DEC-DATE | `YYYY-MM-DD` |
| 4 | LoanType | LOAN-TYPE | `2` if FHA, else `1` |
| 5 | LoanPurpose | LOAN-PURPOSE | `31` if `R`, else `1` |
| 6 | LoanAmount | LOAN-AMT / 1000 | integer, rounded half up, no leading zeros |
| 7 | PropertyType | PROP-TYPE | 1 char as is |
| 8 | PropertyState | PROP-STATE | 2 chars as is |
| 9 | ApplicantRace | APPL-RACE | 1 char as is |
| 10 | ApplicantSex | APPL-SEX | 1 char as is |
| 11 | ApplicantIncome | ANNUAL-INCOME / 1000 | integer, truncated, no leading zeros |
| 12 | RateSpread | DEC-FINAL-RATE - APOR | A: 3 decimals, rounded half up, e.g. `-0.168`, `0.082`; D: `NA` |

## 4. LNPRC010 - STEP010 risk-based pricing

**Inputs:** APPSIN (LNAPPREC), RATESIN (LNRATE). **Output:** PRICED
(LNPRCREC), one record for **every** application read, priced or rejected
(LNPRC010:116), in input order.

### 4.1 Rate table load

- R1. The whole rate sheet is loaded before the first application into a
  table of product, lock days and base rate (LNPRC010:73-86, table
  LNPRC010:35-40). RATE-EFF-DATE is ignored. The `APOR ` row is loaded too
  but never matches a product.
- R2. **KEEP:** the table holds 50 entries (`OCCURS 50`, LNPRC010:37) and the
  load does not check the bound; the recorded sheet has 10 rows.

### 4.2 Per application (2000-PRICE-APP, LNPRC010:88-116)

- R3. Output record is blanked then initialised (LNPRC010:90-91), the 150
  input bytes copied to PRC-APP unchanged, filler included (LNPRC010:92), and
  PRC-STATUS set to `00` (LNPRC010:93).
- R4. Product selection (LNPRC010:94-103), first match wins:

  | LOAN-TYPE | TERM-MONTHS | PRC-PRODUCT |
  | --- | --- | --- |
  | CNV | 360 | CNV30 |
  | CNV | 180 | CNV15 |
  | FHA | 360 | FHA30 |
  | anything else | | status `E1`, product stays spaces |

  There is no FHA 15-year and no jumbo product; jumbo is only an
  underwriting distinction (R15).
- R5. Base rate (2100-FIND-BASE, LNPRC010:118-130): the first table row whose
  product equals PRC-PRODUCT **and** whose lock days equal LOCK-DAYS gives
  PRC-BASE-RATE. No match (e.g. a 90-day lock) sets status `E2`; the product
  stays filled in.
- R6. Rejected records (`E1`/`E2`) are written with base, adjustment, final
  rate, payment, fee percent and fee amount all zero (adjustment as `+00000`)
  and counted in REJECTED (LNPRC010:113-114). Priced records are counted in
  PRICED (LNPRC010:112).
- R7. Credit-tier risk adjustment (2200-RISK-ADJ, LNPRC010:132-146), same for
  every product:

  | CREDIT-SCORE | PRC-RISK-ADJ |
  | --- | --- |
  | >= 760 | -0.25 |
  | 720-759 | 0.00 |
  | 680-719 | +0.50 |
  | 640-679 | +1.00 |
  | 620-639 | +1.75 |
  | < 620 | +2.50 |

  The 2011 change log (LNPRC010:9) calls the +2.50 an FHA 580-619 tier that
  is not in the BRD; the code applies it to every score below 620 of any loan
  type.
- R8. `PRC-FINAL-RATE = PRC-BASE-RATE + PRC-RISK-ADJ` (LNPRC010:109); exact,
  both have 4 decimals.
- R9. Monthly payment (2300-PAYMENT, LNPRC010:148-152):
  1. `WS-MONTH-RATE = PRC-FINAL-RATE / 1200` into `PIC 9V9(12)`
     (LNPRC010:49, 149). **DELIBERATE: truncated (not rounded) to 12
     decimals.**
  2. `WS-FACTOR = (1 + WS-MONTH-RATE) ** TERM-MONTHS` into `PIC 9(4)V9(12)`
     (LNPRC010:50, 150). **KEEP: also truncated to 12 decimals.**
  3. `PRC-MONTHLY-PMT ROUNDED = LOAN-AMT * WS-MONTH-RATE * WS-FACTOR /
     (WS-FACTOR - 1)` (LNPRC010:151-152). **DELIBERATE: rounded half up to
     cents.** Evaluate step 3 at high precision (e.g. `BigDecimal` with
     `MathContext(40)` or more) from the truncated step 1 and 2 values, then
     round once.
- R10. Lock extension fee (2400-EXT-FEE, LNPRC010:154-166). Fee table
  `WS-EXT-FEE` = 0.125, 0.250, 0.375 (LNPRC010:41-46).

  | LOCK-EXTS | PRC-EXT-FEE-PCT |
  | --- | --- |
  | 0 | 0 |
  | 1-3 | `WS-EXT-FEE(LOCK-EXTS)`: 0.125 / 0.250 / 0.375 |
  | > 3 | `0.375 + (LOCK-EXTS - 3) * WS-ADDON-PCT` |

  **DELIBERATE: 0.12% add-on past the third extension.** The code moves
  0.125 into `WS-ADDON-PCT` (LNPRC010:155), but that field is `PIC 9V99`
  (LNPRC010:48), so it holds 0.12. Four extensions give 0.495, five 0.615,
  six 0.735 (the BRD's "rounding issue").
- R11. **KEEP:** PRC-EXT-FEE-PCT is `PIC 9V999`; the COMPUTE at
  LNPRC010:162-163 has no `ON SIZE ERROR`, so a result of 10.000 or more
  (LOCK-EXTS >= 84) would lose its tens digit. Not exercised by the recorded
  data (max 6 extensions); implement as high-order truncation mod 10 if the
  migration keeps the field width.
- R12. `PRC-EXT-FEE-AMT ROUNDED = LOAN-AMT * PRC-EXT-FEE-PCT / 100`
  (LNPRC010:165-166): **KEEP: rounded half up to cents.**

### 4.3 Control totals

LNPRC010:68-70, counts `PIC 9(7)` printed with leading zeros:
`LNPRC010 READ     nnnnnnn`, `LNPRC010 PRICED   nnnnnnn`,
`LNPRC010 REJECTED nnnnnnn`. READ = PRICED + REJECTED.

## 5. LNUWR020 - STEP020 automated underwriting

**Inputs:** RUNDATE (first record, RD-DATE), PRICED. **Output:** DECISION,
one record per PRICED record in the same order (LNUWR020:106).

- U1. Run date = RD-DATE of the first RUNDATE record (LNUWR020:52-55).
- U2. Per record (2000-DECIDE, LNUWR020:72-106): PRC-APP is moved to the
  LNAPPREC working copy (LNUWR020:74); the output is blanked and initialised
  (LNUWR020:75-76); DEC-APP-ID, DEC-LOAN-TYPE, DEC-PRODUCT, DEC-LOAN-AMT,
  DEC-FINAL-RATE, DEC-MONTHLY-PMT, DEC-EXT-FEE-AMT are copied straight from
  the application / PRICED fields and DEC-DATE is the run date
  (LNUWR020:77-84).
- U3. DTI is computed for **every** record, rejected ones included, before
  any decision (LNUWR020:85; 2100-DTI LNUWR020:108-118):
  1. `WS-MONTHLY-INC ROUNDED = ANNUAL-INCOME / 12` into `9(9)V99`
     (LNUWR020:109). **KEEP: monthly income is itself rounded half up to
     cents before the division.**
  2. If WS-MONTHLY-INC = 0, DEC-DTI = 999.99 (LNUWR020:110-111).
  3. Else `DEC-DTI ROUNDED = (MONTHLY-DEBT + PRC-MONTHLY-PMT) * 100 /
     WS-MONTHLY-INC` (LNUWR020:113-115). **DELIBERATE: rounded half up to
     2 decimals.** `ON SIZE ERROR` (result >= 1000) also gives 999.99
     (LNUWR020:116).

  For a rejected record PRC-MONTHLY-PMT is 0, so DTI is debt only.
- U4. Pricing reject: PRC-STATUS not `00` gives `D` / `PRICE`
  (LNUWR020:86-88). No credit or matrix check is made.
- U5. **DELIBERATE: 30-day credit-score window.**
  `WS-SCORE-AGE = INTEGER-OF-DATE(run date) - INTEGER-OF-DATE(SCORE-DATE)`
  in days (LNUWR020:90-92). If the age is **greater than** `WS-CACHE-DAYS`
  = 30 (LNUWR020:37, 93) the decision is `R` / `STALE` (LNUWR020:94-95)
  and the matrix is skipped, whatever the score. Exactly 30 days is still
  fresh; a score date after the run date gives a negative age and is fresh.
- U6. Decision matrix thresholds (2200-MATRIX, LNUWR020:120-137), first match
  wins:

  | Case | Condition | Auto score >= | Auto DTI <= | Decline score < | Decline DTI > |
  | --- | --- | --- | --- | --- | --- |
  | FHA | LOAN-IS-FHA | 680 | 40.00 | 580 | 50.00 |
  | Conforming | otherwise and LOAN-AMT < 500,000.00 | 740 | 36.00 | 620 | 43.00 |
  | Jumbo | otherwise (LOAN-AMT >= 500,000.00) | 760 | 33.00 | 680 | 43.00 |

  **DELIBERATE: jumbo cut at exactly 500,000.** The test is
  `LOAN-AMT < 500000` (LNUWR020:127), so a 500,000.00 loan is jumbo. FHA
  loans use the FHA row at any amount.
- U7. Decision order (LNUWR020:138-152), first match wins:
  1. CREDIT-SCORE < decline score: `D` / `SCORE`
  2. DEC-DTI > decline DTI: `D` / `DTI`
  3. CREDIT-SCORE >= auto score **and** DEC-DTI <= auto DTI: `A` / `AUTO`
  4. otherwise `M` / `RVW`
- U8. Employment override (LNUWR020:153-156): an `A` with EMP-YEARS < 2.0 is
  changed to `M` / `EMPL`. It applies only to auto-approvals.
- U9. DEC-REASON values are left-justified in 5 bytes: `AUTO `, `RVW  `,
  `EMPL `, `DTI  `, `SCORE`, `PRICE`, `STALE`.

### 5.1 Control totals

LNUWR020:65-69: `LNUWR020 READ     `, `APPROVED `, `REVIEW   `,
`DECLINED `, `REPULL   ` each followed by a `9(7)` count (counted by DEC-CODE
A/M/D/R, LNUWR020:100-105). READ = sum of the four.

## 6. LNFIS030 - STEP030 core banking account create

**Inputs:** RUNDATE, PRICED, DECISION. **Output:** FISOUT, one record per
`A` decision.

- F1. Dates are computed once per run (LNFIS030:58-67), not per loan:
  - FUNDING-DATE = run date + 30 calendar days (LNFIS030:58-59).
  - FIRST-PMT-DATE = day 01 of the month two months after the funding
    month, rolling the year when the month passes 12 (LNFIS030:60-67).
    Run 2026-11-05 gives funding 2026-12-05 and first payment 2027-02-01.
- F2. **KEEP: lockstep read.** For each DECISION record one PRICED record is
  read (LNFIS030:71-74); records are paired by position, not by APP-ID.
  Both files must have the same count and order (they do, since LNUWR020
  writes one decision per priced record).
- F3. Only DEC-CODE = `A` produces a message (LNFIS030:75-76).
- F4. Message build (2000-BUILD-MSG, LNFIS030:84-97): record blanked to spaces
  (LNFIS030:86); MSG-TYPE `ACCT` (87); LOAN-NUMBER = 040000000000 +
  DEC-APP-ID into `9(12)`, i.e. `04` followed by the 10-digit APP-ID (88);
  BORROWER-SSN = BORR-TIN from the PRICED application image (89);
  FIS-LOAN-TYPE = DEC-LOAN-TYPE (90); FIS-LOAN-AMOUNT = DEC-LOAN-AMT (91);
  INTEREST-RATE = DEC-FINAL-RATE (92); FIS-TERM-MONTHS = TERM-MONTHS from
  PRICED (93); FIRST-PMT-DATE and FUNDING-DATE from F1 (94-95); FILLER stays
  50 spaces.
- F5. **DELIBERATE:** written as ASCII with LF (section 3.6).

### 6.1 Control totals

LNFIS030:81: `LNFIS030 WRITTEN  nnnnnnn`, the number of messages (= LNUWR020
APPROVED).

## 7. LNGL040 - STEP040 general ledger extract

**Inputs:** RUNDATE, DECISION. **Output:** GLOUT, posting lines for each `A`
decision in DECISION order.

- G1. Only DEC-CODE = `A` posts (LNGL040:54-56).
- G2. Per loan (2000-POST-LOAN, LNGL040:71-97):
  - Loan number = 040000000000 + DEC-APP-ID into `9(13)` (LNGL040:72), so
    `004` + APP-ID. **KEEP:** 13 digits here vs 12 in FISACCT.
  - Cost center 10020 if DEC-LOAN-TYPE = `FHA`, else 10010 (LNGL040:73-77).
  - Funding pair (LNGL040:78-87): `DR` account 140100000000, then `CR`
    account 210500000000, both for DEC-LOAN-AMT with description
    `LOAN FUNDING ` + DEC-PRODUCT (e.g. `LOAN FUNDING CNV30`, space-padded to
    30). The CR line reuses the DR line's amount and description.
  - Fee pair, only if DEC-EXT-FEE-AMT > 0 (LNGL040:88-97): `DR` account
    120300000000, then `CR` account 410200000000, both for DEC-EXT-FEE-AMT
    with description `RATE LOCK EXTENSION FEE`.
- G3. Every line (3000-WRITE-LINE, LNGL040:99-109) gets GL-POST-DATE = run
  date, the loan number and cost center, adds its amount to the DR or CR
  total and to the line count. Line order per loan: funding DR, funding CR,
  fee DR, fee CR.
- G4. Balance check (LNGL040:65-68): if total DR != total CR the program
  prints `LNGL040  OUT OF BALANCE` and ends RC=12, which skips STEP050 in the
  JCL. By construction the totals always balance.

### 7.1 Control totals

LNGL040:62-64. Totals are `PIC 9(13)V99` (LNGL040:38-39) edited through
`Z(12)9.99` (LNGL040:40-41), i.e. 16 characters, leading spaces, decimal
point shown:

```
LNGL040  LINES    0000054
LNGL040  TOTAL DR       7134818.75
LNGL040  TOTAL CR       7134818.75
```

Note the two spaces after `LNGL040` in the program name.

## 8. LNHMD050 - STEP050 HMDA extract

**Inputs:** RATESIN, PRICED, DECISION. **Output:** HMDAOUT (section 3.8).

- H1. **DELIBERATE: HMDA spread against the 0-day APOR row.** The rate sheet
  is scanned for `RATE-PRODUCT = 'APOR '` and `RATE-LOCK-DAYS = 0`
  (LNHMD050:56-67). **KEEP:** the scan does not stop, so the last such row
  wins; if there is none WS-APOR stays 0 (LNHMD050:39).
- H2. The header line is written first (LNHMD050:70-76).
- H3. **KEEP: lockstep read**, one PRICED record per DECISION record, by
  position (LNHMD050:78-81), as in F2.
- H4. Only DEC-CODE `A` or `D` get a row (LNHMD050:82); `M` and `R` are left
  for a later run. `D` / `PRICE` rejects are included.
- H5. Row fields (2000-BUILD-ROW, LNHMD050:91-139):
  - ActionTaken `1` for A, `3` for D (LNHMD050:93-97).
  - ActionDate = DEC-DATE as `YYYY-MM-DD` (LNHMD050:98-100).
  - LoanType `2` if FHA else `1` (LNHMD050:101-105); any non-FHA type,
    including a rejected unknown type, reports `1`.
  - LoanPurpose `31` if `R` else `1` (LNHMD050:106-110; stored as `1 ` and
    trimmed at LNHMD050:128).
  - LoanAmount: `WS-AMT-K ROUNDED = LOAN-AMT / 1000` (LNHMD050:111). **KEEP:
    rounded half up** (286,500.00 gives `287`).
  - ApplicantIncome: `WS-INC-K = ANNUAL-INCOME / 1000` without ROUNDED
    (LNHMD050:112). **KEEP: truncated.**
  - Both are edited through `Z(6)9` and trimmed (LNHMD050:113-114, 129, 134):
    no leading zeros or spaces; zero prints as `0`.
  - RateSpread for A: `WS-SPREAD ROUNDED = DEC-FINAL-RATE - WS-APOR` into
    `S9(2)V999` (LNHMD050:116). **KEEP: rounded half up (away from zero) to
    3 decimals.** Edited through `-(2)9.999` and trimmed
    (LNHMD050:117-118): a `-` only when negative, no `+`, at least one
    integer digit (`-0.168`, `0.082`, `0.000`). For D: `NA`
    (LNHMD050:120).
  - PropertyType, PropertyState, ApplicantRace, ApplicantSex are copied raw
    (LNHMD050:130-133). Note the column order puts PropertyType before
    PropertyState and Race before Sex.
- H6. Each row is built with STRING into a space-filled `X(160)`
  (LNHMD050:122-137) and written; trailing spaces are stripped on write
  (section 2).

### 8.1 Control totals

LNHMD050:88: `LNHMD050 ROWS     nnnnnnn`, data rows excluding the header
(= APPROVED + DECLINED from LNUWR020).

## 9. SYSOUT control totals

`golden/SYSOUT.txt` for the recorded night (run date 2026-11-05, 60
applications), 14 lines. `tools/parity.py` does not compare SYSOUT; check it with
`cmp <out>/SYSOUT.txt legacy-los/golden/SYSOUT.txt`.

```
LOSNIGHT RUN DATE 20261105
LNPRC010 READ     0000060
LNPRC010 PRICED   0000058
LNPRC010 REJECTED 0000002
LNUWR020 READ     0000060
LNUWR020 APPROVED 0000019
LNUWR020 REVIEW   0000027
LNUWR020 DECLINED 0000012
LNUWR020 REPULL   0000002
LNFIS030 WRITTEN  0000019
LNGL040  LINES    0000054
LNGL040  TOTAL DR       7134818.75
LNGL040  TOTAL CR       7134818.75
LNHMD050 ROWS     0000031
```

Cross-checks a migration can assert: PRC READ = PRICED + REJECTED; UWR READ
= PRC READ = A + M + D + R; FIS WRITTEN = APPROVED; GL LINES = 2 x APPROVED
+ 2 x (approved loans with a fee) (19 x 2 + 8 x 2 = 54); HMDA ROWS =
APPROVED + DECLINED.

## 10. Deliberate behaviours: checklist

| # | Behaviour | Where | Evidence in the recorded night |
| --- | --- | --- | --- |
| D1 | Monthly rate cut (not rounded) to 12 decimals before the payment formula | LNPRC010:49, 149 | Does not change any of the 58 payments this night (rounding it would match too), so golden parity alone will not catch a regression; unit-test it. |
| D2 | Payment rounded half up to cents | LNPRC010:151 | Truncating instead would change 29 of 58 payments. |
| D3 | DTI rounded half up to 2 decimals | LNUWR020:113 | All 60 DTIs reproduce with half up (after monthly income rounded, U3). |
| D4 | 30-day credit-score window, stale (> 30 days) to `R` | LNUWR020:37, 90-95 | 2611050028 (31 days) and 2611050029 (45 days) are `R STALE`. |
| D5 | Jumbo cut at exactly 500,000.00 | LNUWR020:127 | 2611050018 (CNV 500,000.00, score 745, DTI 25.63) is `M RVW`; under conforming rules it would be `A`. |
| D6 | 0.12% add-on past the third extension (`WS-ADDON-PCT PIC 9V99`) | LNPRC010:48, 155, 162-163 | 2611050007, 008, 010, 024 (4-6 extensions); at 0.125% their fees would be 1,505.00 / 1,725.00 / 1,612.50 / 1,025.00 instead of 1,489.95 / 1,697.40 / 1,580.25 / 1,014.75. |
| D7 | HMDA rate spread against the 0-day APOR row | LNHMD050:61-64 | APOR 6.0430; e.g. 2611050001 5.8750 gives `-0.168`. |
| D8 | FISACCT written as ASCII with LF, no EBCDIC | FISACCT.cpy, LNFIS030:18-19 | `golden/FISACCT.dat`, 19 x 114 bytes + LF. |

Other KEEP behaviours, by rule: R2, R4 (no FHA15 / no jumbo product), R5
(first match), R6 (rejects written with zeros and `+00000`), R7 (+2.50 for
every score < 620), R9.2 (factor truncated), R11 (fee-percent overflow),
R12 (fee rounded), U3 (monthly income rounded first, 999.99 sentinel, DTI on
rejects), U5 (exactly 30 days fresh), U8 (EMPL only on A), F1 (dates per
run), F2/H3 (positional pairing), G2 (13-digit GL loan number), H1 (last
APOR row wins, default 0), H5 (amount rounded, income truncated, spread
format), section 2 (filler, fixed width except HMDA).

## 11. How these rules were checked

- `cd legacy-los && make golden-check` (GnuCOBOL 3.1.2) reproduces all five
  golden datasets: PASS.
- An independent decimal re-implementation of R3-R12 and U1-U9 (Python
  `decimal`, scratch, not committed) reproduced PRC-STATUS,
  PRC-MONTHLY-PMT, PRC-EXT-FEE-AMT, DEC-CODE, DEC-REASON and DEC-DTI for all
  60 golden records, and was used for the sensitivity figures in section 10.
