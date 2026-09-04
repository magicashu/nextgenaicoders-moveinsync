# Corrupted variants

Generate V1-V5 here (under `generated/`, git-ignored) from copies of the official data. This directory
must never contain modified organizer originals.

| Variant | Construction | Expected behaviour (tested against the seven-file fixture in `CorruptedVariantTest`) |
|---|---|---|
| V1 | remove `emp_Data.csv` | M04-M07 return `UNSUPPORTED` with the missing-file reason; trip-level metrics unchanged |
| V2 | remove `bill_data.csv` | M09/M10 return `UNSUPPORTED`; cost branch disabled in the capability matrix |
| V3 | shuffle 5% of `trip_id` in `trip_feedback.csv` | unmatched rows keep `trip_matched = false`; join coverage drops and is reported |
| V4 | inject duplicate ride rows across two tenants | duplicates removed by `(business_unit, trip_id)`; `Q0-DUPLICATE-TRIPS` reports the count |
| V5 | blank `severity` on all alerts | M14/M15 unsupported or zero; M13 and M16 still count alerts |

The evaluation workstream owns the generator for the official-size variants (`evals/`); this
workstream owns the loader behaviour they exercise.
