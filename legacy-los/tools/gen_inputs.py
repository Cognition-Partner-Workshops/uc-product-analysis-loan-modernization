"""Writes the recorded nightly inputs for the legacy LOS batch (deterministic).

The application file mixes ordinary traffic with the cases the decision
matrix and the pricing quirks care about: score tiers on both sides of every
cut-off, a DTI exactly on 43.00, jumbo conventional loans, FHA scores in the
580-619 tier, stale credit scores, short employment and up to six lock
extensions. Rerun only when the recorded inputs are meant to change.
"""
import random
from datetime import date, timedelta
from pathlib import Path

RUN_DATE = date(2026, 11, 5)
OUT = Path(__file__).resolve().parent.parent / "data" / "input"

LAST = ["OKAFOR", "LINDQVIST", "MARTINEZ", "NGUYEN", "PATEL", "BRENNAN",
        "KOWALSKI", "HASSAN", "TANAKA", "OYELARAN", "DUBOIS", "FERREIRA",
        "MCALLISTER", "ROSSI", "ABERNATHY", "CHEN", "WOJCIK", "SANTOS",
        "HARGREAVES", "IBRAHIM", "MORALES", "PETROV", "GALLAGHER", "SATO"]
FIRST = ["ADAEZE", "ERIK", "LUCIA", "MINH", "PRIYA", "SEAN", "MAGDA",
         "OMAR", "YUKI", "TUNDE", "CLAIRE", "JOAO", "FIONA", "MARCO",
         "GRACE", "WEI", "PIOTR", "ANA", "HELEN", "AMINA", "DIEGO",
         "IRINA", "KEVIN", "HARUTO"]
STATES = ["CA", "TX", "FL", "NY", "IL", "WA", "GA", "NC", "AZ", "CO"]

# (loan type, term, amount, score, score age days, income, monthly debt,
#  employment years, lock days, extensions, purpose)
EDGE = [
    ("CNV", 360, 320000.00, 781, 3, 145000, 450.00, 6.0, 30, 0, "P"),
    ("CNV", 360, 410000.00, 760, 10, 168000, 620.00, 9.5, 45, 1, "P"),
    ("CNV", 360, 289500.00, 759, 2, 121000, 380.00, 4.0, 30, 0, "R"),
    ("CNV", 360, 355000.00, 740, 5, 132000, 515.00, 3.0, 60, 2, "P"),
    ("CNV", 360, 362000.00, 739, 8, 140000, 400.00, 7.0, 30, 0, "P"),
    ("CNV", 360, 248000.00, 720, 1, 98000, 1210.00, 5.0, 45, 3, "R"),
    ("CNV", 360, 301000.00, 719, 4, 104000, 900.00, 12.0, 30, 4, "P"),
    ("CNV", 360, 276000.00, 680, 12, 89000, 760.00, 2.0, 60, 5, "P"),
    ("CNV", 360, 199000.00, 679, 20, 76000, 640.00, 1.5, 30, 0, "P"),
    ("CNV", 360, 215000.00, 640, 6, 81000, 1020.00, 3.5, 45, 6, "R"),
    ("CNV", 360, 187000.00, 639, 9, 70000, 850.00, 8.0, 30, 1, "P"),
    ("CNV", 360, 176500.00, 620, 14, 66000, 700.00, 2.5, 30, 0, "P"),
    ("CNV", 360, 168000.00, 619, 3, 72000, 300.00, 10.0, 30, 0, "P"),
    ("CNV", 360, 650000.00, 785, 2, 260000, 900.00, 11.0, 60, 1, "P"),
    ("CNV", 360, 525000.00, 765, 7, 190000, 1400.00, 6.0, 45, 0, "R"),
    ("CNV", 360, 580000.00, 700, 5, 210000, 800.00, 4.0, 30, 2, "P"),
    ("CNV", 360, 515000.00, 675, 11, 205000, 600.00, 9.0, 30, 0, "P"),
    ("CNV", 360, 500000.00, 745, 4, 175000, 700.00, 5.0, 30, 0, "P"),
    ("CNV", 180, 240000.00, 772, 6, 150000, 500.00, 7.0, 30, 0, "R"),
    ("CNV", 180, 198000.00, 701, 2, 99000, 650.00, 3.0, 45, 1, "R"),
    ("FHA", 360, 265000.00, 702, 5, 92000, 640.00, 4.0, 30, 0, "P"),
    ("FHA", 360, 238000.00, 680, 9, 84000, 700.00, 2.0, 45, 0, "P"),
    ("FHA", 360, 221000.00, 655, 3, 71000, 720.00, 6.0, 30, 3, "P"),
    ("FHA", 360, 205000.00, 601, 13, 64000, 650.00, 5.0, 60, 4, "P"),
    ("FHA", 360, 192000.00, 580, 1, 61000, 580.00, 3.0, 30, 0, "P"),
    ("FHA", 360, 184000.00, 579, 2, 60000, 500.00, 8.0, 30, 0, "P"),
    ("FHA", 360, 230000.00, 690, 4, 52000, 1100.00, 4.0, 30, 0, "P"),
    ("CNV", 360, 330000.00, 750, 31, 150000, 400.00, 6.0, 30, 0, "P"),
    ("CNV", 360, 270000.00, 712, 45, 98000, 600.00, 5.0, 45, 1, "R"),
    ("FHA", 360, 210000.00, 688, 30, 78000, 500.00, 3.0, 30, 0, "P"),
    ("CNV", 360, 300000.00, 790, 15, 160000, 350.00, 1.0, 30, 0, "P"),
    ("CNV", 360, 285000.00, 748, 22, 118000, 500.00, 1.9, 30, 2, "P"),
    ("CNV", 240, 200000.00, 760, 3, 120000, 300.00, 5.0, 30, 0, "P"),
    ("CNV", 360, 260000.00, 742, 5, 90000, 1800.00, 6.0, 90, 0, "P"),
]


def score_date(age):
    return (RUN_DATE - timedelta(days=age)).strftime("%Y%m%d")


def money(v, whole, frac=2):
    return f"{round(v * 10 ** frac):0{whole + frac}d}"


def record(i, row, rnd):
    (ltype, term, amt, score, age, income, debt, emp, lock, exts, purpose) = row
    app_id = 2611050000 + i
    borr_id = 700100000 + i * 7
    tin = 900000000 + rnd.randrange(10000000, 99999999)
    last = LAST[i % len(LAST)].ljust(15)[:15]
    first = FIRST[(i * 5) % len(FIRST)].ljust(12)[:12]
    state = STATES[(i * 3) % len(STATES)]
    ptype = "2" if i % 7 == 3 else "1"
    app_date = (RUN_DATE - timedelta(days=1 + i % 9)).strftime("%Y%m%d")
    sex = "12"[i % 2]
    race = "12345"[i % 5]
    rec = (f"{app_id:010d}{borr_id:09d}{tin:09d}{last}{first}{ltype}"
           f"{purpose}{money(amt, 9)}{term:03d}{state}{ptype}"
           f"{money(income, 9)}{money(debt, 7)}{score:03d}"
           f"{score_date(age)}{money(emp, 2, 1)}{lock:03d}{exts:02d}"
           f"{app_date}{sex}{race}")
    return rec.ljust(150)


def main():
    rnd = random.Random(20261105)
    rows = list(EDGE)
    for _ in range(26):
        ltype = rnd.choice(["CNV", "CNV", "CNV", "FHA"])
        term = 360 if ltype == "FHA" else rnd.choice([360, 360, 360, 180])
        amt = rnd.randrange(120, 720) * 1000 + rnd.choice([0, 250, 500])
        score = rnd.randrange(600, 815)
        income = rnd.randrange(55, 260) * 1000
        debt = rnd.randrange(150, 1900) + 0.0
        rows.append((ltype, term, float(amt), score, rnd.randrange(0, 29),
                     income, debt, rnd.randrange(10, 150) / 10,
                     rnd.choice([30, 45, 60]), rnd.choice([0, 0, 0, 1, 2]),
                     rnd.choice(["P", "P", "R"])))
    OUT.mkdir(parents=True, exist_ok=True)
    recs = [record(i + 1, r, rnd) for i, r in enumerate(rows)]
    assert all(len(r) == 150 for r in recs)
    (OUT / "APPS.dat").write_text("\n".join(recs) + "\n")
    eff = RUN_DATE.strftime("%Y%m%d")
    sheet = [("CNV30", 30, 6.1250), ("CNV30", 45, 6.2500), ("CNV30", 60, 6.3750),
             ("CNV15", 30, 5.5000), ("CNV15", 45, 5.6250), ("CNV15", 60, 5.7500),
             ("FHA30", 30, 5.8750), ("FHA30", 45, 6.0000), ("FHA30", 60, 6.1250),
             ("APOR ", 0, 6.0430)]
    lines = [f"{p}{d:03d}{round(r * 10000):06d}{eff}".ljust(40) for p, d, r in sheet]
    (OUT / "RATESHT.dat").write_text("\n".join(lines) + "\n")
    (OUT / "RUNDATE.dat").write_text(eff.ljust(80) + "\n")
    print(f"{len(recs)} applications, {len(lines)} rate sheet rows, run date {eff}")


if __name__ == "__main__":
    main()
