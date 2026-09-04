#!/usr/bin/env python3
import csv
from pathlib import Path

fixture = Path(__file__).resolve().parents[2] / "data" / "fixtures" / "Ride_data _trip-sample.csv"
with fixture.open(newline="", encoding="utf-8") as handle:
    rows = list(csv.DictReader(handle))

current = [row for row in rows if "2026-06-01" <= row["trip_date"] <= "2026-06-07"]
baseline = [row for row in rows if "2026-05-04" <= row["trip_date"] <= "2026-05-31"]
current_rate = 100 * sum(float(row["delay_minutes"]) > 0 for row in current) / len(current)
baseline_rate = 100 * sum(float(row["delay_minutes"]) > 0 for row in baseline) / len(baseline)

assert (len(current), current_rate) == (10, 30.0)
assert (len(baseline), baseline_rate) == (10, 10.0)
print("fixture valid: current=30.0%, baseline=10.0%")
