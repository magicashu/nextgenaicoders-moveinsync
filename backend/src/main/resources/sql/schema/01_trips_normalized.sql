-- Documentation template. Runtime substitutes only the trusted configured file glob.
CREATE OR REPLACE VIEW trips_normalized AS
SELECT
    trim(business_unit) AS business_unit,
    CAST(replace(CAST(trip_id AS VARCHAR), ',', '') AS BIGINT) AS trip_id,
    CAST(trip_date AS DATE) AS trip_date,
    CAST(replace(CAST(delay_minutes AS VARCHAR), ',', '') AS DOUBLE) AS delay_minutes
FROM read_csv_auto('${TRIP_FILE_GLOB}', union_by_name = true, header = true);
