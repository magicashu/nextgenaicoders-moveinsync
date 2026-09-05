package com.moveinsync.mobilitycopilot.observability;

import java.util.List;

/** Fan out to Langfuse and a local OTLP collector without coupling their availability. */
public final class CompositeTraceExporter implements TraceExporter, AutoCloseable {
    private final List<TraceExporter> exporters;
    public CompositeTraceExporter(List<TraceExporter> exporters) { this.exporters = List.copyOf(exporters); }
    @Override public void export(TraceRecorder.Trace trace) { exporters.forEach(exporter -> exporter.export(trace)); }
    public List<ExporterStatus> targets() { return exporters.stream().map(TraceExporter::status).toList(); }
    @Override public ExporterStatus status() {
        var values = targets();
        return new ExporterStatus(values.stream().map(ExporterStatus::target).reduce((a,b) -> a + ", " + b).orElse("none"),
                true, values.stream().mapToLong(ExporterStatus::queued).sum(), values.stream().mapToLong(ExporterStatus::exported).sum(),
                values.stream().mapToLong(ExporterStatus::dropped).sum(), values.stream().mapToLong(ExporterStatus::failures).sum(),
                values.stream().filter(v -> v.lastError() != null).map(ExporterStatus::lastError).findFirst().orElse(null),
                values.stream().anyMatch(ExporterStatus::degraded));
    }
    @Override public void close() {
        for (TraceExporter exporter : exporters) {
            if (exporter instanceof AutoCloseable closeable) {
                try { closeable.close(); }
                catch (Exception ignored) { /* Shutdown is best effort after each exporter flush. */ }
            }
        }
    }
}
