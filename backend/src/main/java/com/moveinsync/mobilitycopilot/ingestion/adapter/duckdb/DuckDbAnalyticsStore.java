package com.moveinsync.mobilitycopilot.ingestion.adapter.duckdb;

import com.moveinsync.mobilitycopilot.config.MobilityDataProperties;
import com.moveinsync.mobilitycopilot.ingestion.application.AnalyticsStore;
import com.moveinsync.mobilitycopilot.ingestion.application.DatasetFileCatalog;
import com.moveinsync.mobilitycopilot.ingestion.domain.DataQualityReport;
import com.moveinsync.mobilitycopilot.ingestion.domain.DatasetCatalog;
import org.duckdb.DuckDBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Loads the seven organizer files once into an in-memory DuckDB database and hands out
 * duplicated connections. Loading is idempotent per data directory.
 */
@Component
public class DuckDbAnalyticsStore implements AnalyticsStore, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DuckDbAnalyticsStore.class);

    private final Path directory;
    private final DatasetFileCatalog fileCatalog;
    private final Object lock = new Object();
    private DuckDBConnection root;
    private DuckDbDatasetLoader.LoadResult loaded;

    @org.springframework.beans.factory.annotation.Autowired
    public DuckDbAnalyticsStore(MobilityDataProperties properties, DatasetFileCatalog fileCatalog) {
        this(DatasetFileCatalog.resolveDirectory(properties.directory()), fileCatalog);
    }

    public DuckDbAnalyticsStore(Path directory, DatasetFileCatalog fileCatalog) {
        this.directory = directory;
        this.fileCatalog = fileCatalog;
    }

    @Override
    public DatasetCatalog catalog() {
        return ensureLoaded().catalog();
    }

    @Override
    public DataQualityReport qualityReport() {
        return ensureLoaded().qualityReport();
    }

    @Override
    public Connection borrow() throws SQLException {
        ensureLoaded();
        synchronized (lock) {
            return root.duplicate();
        }
    }

    @Override
    public void reset() {
        synchronized (lock) {
            closeQuietly();
            loaded = null;
        }
    }

    private DuckDbDatasetLoader.LoadResult ensureLoaded() {
        synchronized (lock) {
            if (loaded == null) {
                try {
                    long started = System.nanoTime();
                    root = (DuckDBConnection) DriverManager.getConnection("jdbc:duckdb:");
                    loaded = new DuckDbDatasetLoader(fileCatalog).load(root, directory);
                    log.info("Loaded dataset {} from {} in {} ms (missing files: {})",
                            loaded.catalog().dataVersion(), directory,
                            (System.nanoTime() - started) / 1_000_000, loaded.catalog().missingFiles());
                } catch (SQLException e) {
                    closeQuietly();
                    throw new IllegalStateException("Unable to load dataset from " + directory, e);
                }
            }
            return loaded;
        }
    }

    private void closeQuietly() {
        if (root != null) {
            try {
                root.close();
            } catch (SQLException ignored) {
                // closing a DuckDB in-memory database cannot fail in a way we can recover from
            }
            root = null;
        }
    }

    @Override
    public void close() {
        reset();
    }
}
