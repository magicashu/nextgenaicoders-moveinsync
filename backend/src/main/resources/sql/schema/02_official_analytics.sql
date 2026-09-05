-- Versioned materialized facts. Raw files are immutable and only read during import.
CREATE OR REPLACE MACRO number_value(x) AS CASE
 WHEN regexp_full_match(trim(x), '-?([0-9]+|[0-9]{1,3}(,[0-9]{3})+)(\.[0-9]+)?')
 THEN try_cast(replace(trim(x), ',', '') AS DOUBLE) ELSE NULL END;
CREATE OR REPLACE MACRO date_value(x) AS cast(coalesce(
 try_strptime(trim(x), '%B %d, %Y, %I:%M %p'),
 try_strptime(trim(x), '%B %d, %Y'), try_strptime(trim(x), '%Y-%m-%d')) AS DATE);
CREATE OR REPLACE MACRO time_value(x) AS coalesce(
 try_strptime(trim(x), '%B %d, %Y, %I:%M %p'), try_strptime(trim(x), '%Y-%m-%d %H:%M:%S'));

CREATE OR REPLACE TABLE trips AS
 SELECT DISTINCT trim(business_unit) business_unit, cast(number_value(trip_id) AS BIGINT) trip_id,
 date_value(trip_date) trip_date, trim(vendor_id) vendor_id, trim(office) site_id,
 trim(shift_type) shift_id, trim(trip_direction) direction, trim(product_type) AS "mode",
 trim(actual_cab_registration) vehicle_id, trim(actual_cab_fuel_type) fuel_type,
 number_value(delay_minutes) delay_minutes, trim(delay_reason) delay_reason,
 number_value(actualemployee_cnt) actualemployee_cnt, number_value(actual_cab_capacity) capacity,
 try_cast(actual_escort AS BOOLEAN) escort_present
 FROM read_csv('$SOURCE/Ride_data _trip-*.csv', header=true, all_varchar=true, union_by_name=true)
 WHERE number_value(trip_id) IS NOT NULL AND date_value(trip_date) IS NOT NULL AND nullif(trim(business_unit),'') IS NOT NULL
 ORDER BY business_unit, trip_date, trip_id;
-- Unique ART index establishes identity. Ordered storage/zonemaps serve range aggregates.
CREATE UNIQUE INDEX IF NOT EXISTS trips_tenant_trip ON trips(business_unit, trip_id);

CREATE OR REPLACE TABLE legs AS
 SELECT e.* FROM (
 SELECT trim(business_unit) business_unit, cast(number_value(trip_id) AS BIGINT) trip_id,
 cast(number_value(stwid) AS BIGINT) rider_id, number_value(planned_pickup_epoch) planned_pickup,
 number_value(actual_pickup_epoch) actual_pickup, number_value(planned_drop_epoch) planned_drop,
 number_value(actual_drop_epoch) actual_drop, trim(boarding_status) boarding_status,
 try_cast(is_no_show AS BOOLEAN) no_show, trim(not_boarding_reason) not_boarding_reason
 FROM (SELECT DISTINCT * FROM read_csv('$SOURCE/emp_Data.csv', header=true, all_varchar=true))
 WHERE number_value(stwid) IS NOT NULL AND number_value(stwid) <> 0) e
 JOIN trips t USING(business_unit,trip_id) ORDER BY business_unit,trip_id;

CREATE OR REPLACE TABLE feedback AS
 SELECT f.* FROM (
 SELECT trim(business_unit) business_unit, cast(number_value(trip_id) AS BIGINT) trip_id,
 number_value(stwid) rider_id, number_value(driver_rating) driver_rating,
 number_value(safety_rating) safety_rating
 FROM (SELECT DISTINCT * FROM read_csv('$SOURCE/trip_feedback.csv', header=true, all_varchar=true))
 WHERE number_value(stwid) IS NOT NULL AND number_value(stwid) <> 0) f
 JOIN trips t USING(business_unit,trip_id) ORDER BY business_unit,trip_id;

CREATE OR REPLACE TABLE bills AS
 SELECT b.* FROM (
 SELECT trim(business_unit) business_unit, cast(number_value(trip_id) AS BIGINT) trip_id,
 date_value(cycle_start) cycle_start, date_value(cycle_end) cycle_end,
 number_value(trip_cost) AS cost, number_value(total_trip_km) km
 FROM (SELECT DISTINCT * FROM read_csv('$SOURCE/bill_data.csv', header=true, all_varchar=true))
 WHERE number_value(trip_cost) >= 0 AND number_value(trip_id) IS NOT NULL) b
 JOIN trips t USING(business_unit,trip_id) ORDER BY business_unit,cycle_start,trip_id;

CREATE OR REPLACE TABLE alerts AS
 SELECT a.* FROM (
 SELECT trim(business_unit) business_unit, cast(number_value(trip_id) AS BIGINT) trip_id,
 trim(event_id) event_id, trim(event_type) event_type, trim(severity) severity,
 time_value(start_time) started, time_value(acknowledge_time) acknowledged
 FROM read_csv('$SOURCE/alerts_data.csv', header=true, all_varchar=true)) a
 JOIN trips t USING(business_unit,trip_id) ORDER BY business_unit,started,trip_id;

-- Exact additive statistics. Arbitrary windows sum counts rather than average daily rates.
CREATE OR REPLACE TABLE trip_daily AS
 SELECT business_unit, trip_date, vendor_id, site_id, shift_id, direction, "mode", fuel_type,
 count(*) trip_count, count(*) FILTER(WHERE delay_minutes > 0) delayed_count,
 count(*) FILTER(WHERE fuel_type='Electric') electric_count
 FROM trips GROUP BY ALL ORDER BY business_unit,trip_date;
ANALYZE;
