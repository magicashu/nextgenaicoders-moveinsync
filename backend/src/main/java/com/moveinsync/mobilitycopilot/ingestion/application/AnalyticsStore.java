package com.moveinsync.mobilitycopilot.ingestion.application;

import com.moveinsync.mobilitycopilot.ingestion.domain.DataQualityReport;
import com.moveinsync.mobilitycopilot.ingestion.domain.DatasetCatalog;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Read-only analytical plane. One loaded DuckDB database per data version; callers borrow
 * short-lived connections and never mutate the normalised tables.
 */
public interface AnalyticsStore {

    /** Loads the configured directory on first use and returns the catalog with data version. */
    DatasetCatalog catalog();

    /** Deterministic quality profile for the loaded data version. */
    DataQualityReport qualityReport();

    /** Borrow a connection to the loaded database. The caller must close it. */
    Connection borrow() throws SQLException;

    /** Drop the loaded database so the next call reloads (used by tests and seed/reset scripts). */
    void reset();
}
