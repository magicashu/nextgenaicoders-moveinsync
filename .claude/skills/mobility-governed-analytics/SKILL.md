---
name: mobility-governed-analytics
description: Build or change governed DuckDB ingestion, metric contracts M01-M18, capability matrix, contribution tools and anomaly detection for the Mobility Decision Copilot (packet 01, feat/governed-analytics). Use for any metric, SQL, fixture, data-quality or anomaly-rule work.
---

# Governed analytics (WS1)

Owned: `backend/.../ingestion/**`, `metrics/**`, `anomaly/**`, `backend/src/main/resources/sql/**`, matching tests, `data/fixtures/**`, `data/corrupted/**`.

## Invariants
- Every table carries `business_unit`; every join is `(business_unit, trip_id)`; 6,753 trip ids collide across `orbit-Slc`/`vanta-Aus`.
- Metric formulas live only in `sql/metrics/mNN_*.sql` (metrics-v1.1, D-039). Templates use tokens `{{bu}} {{start}} {{end}} {{filters}} {{dimension}}` plus named selectors (`{{delay_reason}}`); `GovernedSqlTemplate` binds everything as parameters; dimension columns come from the `Dimension` enum only. Never concatenate caller text.
- Nulls in the raw files include the literal `NA`; severity `False` is UNCLASSIFIED; delays cap at 600 and quarantine above 1,440; negative bills are adjustments; marshal 0 means no marshal; occupancy caps at 100%.
- M04/M05 denominators are boarded valid-rider legs with both epochs (contract) - the profile document's illustrative late-pickup shares (22.0/17.9, 9.9/3.2) used all legs and are superseded by 25.77/21.29 and 11.82/4.09.
- Unsupported metrics return `MetricStatus.UNSUPPORTED` with a typed caveat; capability comes from data (`DuckDbCapabilityMatrixService`), never hard-coded tenant names.
- Anomaly rule: >=300 in population, +3 pp and +25% relative vs prior four complete weeks; per-1,000 metrics use a doubling rule; a single alert type stepping to <=5% of its prior two-week average while others stay stable is a DATA_REGIME_CHANGE note (G3), never an action.

## Fast iteration
Use the scratch DuckDB harness pattern: load raw CSVs with `read_csv(..., all_varchar=true, union_by_name=true)`, run `sql/schema/*.sql`, then render metric templates in Python before touching Java. Official reconciliation targets: G1 M01 4357/19913 = 21.88 vs 12.28; Clearwater 24.07 (51.1% share); LOGIN 23.91; shift 10:30 47.38; 14 qualified vendors all rose (17.15-28.37); M09 vanta-Sea May 1390.34 (158 negative lines); dedupe 708 legs / 72 bills; Pooja 77 capped, 4 quarantined; marshal zero 473,692.

## Tests
`SevenFileFixtureMetricsTest`, `SevenFileFixtureAnomalyTest`, `CorruptedVariantTest` (V1-V5 from fixture copies) always run; `OfficialDatasetGateTest` runs when the official directory is found (canonical checkout or `MOBILITY_OFFICIAL_DATA_DIR`). Fixture expectations are in `data/fixtures/seven-file-sample/MANIFEST.md`.
