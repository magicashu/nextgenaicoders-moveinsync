#!/usr/bin/env python3
"""Generate degraded-data variants V1-V5 from COPIES of the official files (D-033).

Usage: python3 evals/corrupted/generate_variants.py "<official dir>" data/corrupted/generated
Never writes inside the official directory. Offline Python is permitted for dataset validation (D-028).
"""
import csv, os, random, shutil, sys

RIDE_FILES = ["Ride_data _trip-may_2026.csv", "Ride_data _trip-June_2026.csv", "Ride_data _trip-July_2026.csv"]
OTHER = ["emp_Data.csv", "bill_data.csv", "trip_feedback.csv", "alerts_data.csv"]

def present(src, names):
    return [n for n in names if os.path.exists(os.path.join(src, n))]

def copy_all(src, dst, skip=()):
    os.makedirs(dst, exist_ok=True)
    for name in present(src, RIDE_FILES + OTHER):
        if name in skip:
            continue
        shutil.copyfile(os.path.join(src, name), os.path.join(dst, name))

def main(src, out):
    if os.path.abspath(out).startswith(os.path.abspath(src)):
        raise SystemExit("refusing to write inside the official directory")
    rnd = random.Random(20260905)
    # V1: no employee legs
    copy_all(src, os.path.join(out, "V1-missing-legs"), skip=("emp_Data.csv",))
    # V2: no bills
    copy_all(src, os.path.join(out, "V2-missing-bills"), skip=("bill_data.csv",))
    # V3: shuffle 5% of feedback trip ids
    v3 = os.path.join(out, "V3-shuffled-feedback")
    copy_all(src, v3)
    with open(os.path.join(src, "trip_feedback.csv"), newline="", encoding="utf-8") as f:
        rows = list(csv.reader(f))
    header, body = rows[0], rows[1:]
    idx = header.index("trip_id")
    ids = [r[idx] for r in body]
    picks = rnd.sample(range(len(body)), max(1, len(body) // 20))
    shuffled = [ids[i] for i in picks]
    rnd.shuffle(shuffled)
    for i, new in zip(picks, shuffled):
        body[i][idx] = new
    with open(os.path.join(v3, "trip_feedback.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f); w.writerow(header); w.writerows(body)
    # V4: inject 2,000 duplicate ride rows across two tenants
    v4 = os.path.join(out, "V4-duplicate-rides")
    copy_all(src, v4)
    ride_file = present(src, RIDE_FILES)[-1]
    with open(os.path.join(src, ride_file), newline="", encoding="utf-8") as f:
        rows = list(csv.reader(f))
    header, body = rows[0], rows[1:]
    bu = header.index("business_unit")
    orbit = [r for r in body if r[bu] == "orbit-Slc"][:1000]
    vanta = [r for r in body if r[bu] == "vanta-Aus"][:1000] or [r for r in body if r[bu] != "orbit-Slc"][:1000]
    with open(os.path.join(v4, ride_file), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f); w.writerow(header); w.writerows(body + orbit + vanta)
    # V5: blank severity on all alerts
    v5 = os.path.join(out, "V5-blank-severity")
    copy_all(src, v5)
    with open(os.path.join(src, "alerts_data.csv"), newline="", encoding="utf-8") as f:
        rows = list(csv.reader(f))
    header, body = rows[0], rows[1:]
    sev = header.index("severity")
    for r in body:
        r[sev] = ""
    with open(os.path.join(v5, "alerts_data.csv"), "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f); w.writerow(header); w.writerows(body)
    print("generated V1-V5 under", out)

if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2])
