package com.moveinsync.mobilitycopilot.observability;

/** Non-blocking export boundary. Implementations must never throw into the workflow or block it. */
public interface TraceExporter {

    void export(TraceRecorder.Trace trace);

    /** Diagnostic state for the trust panel: queued, exported, dropped and whether the backend is reachable. */
    ExporterStatus status();

    record ExporterStatus(String target, boolean enabled, long queued, long exported, long dropped, long failures, String lastError, boolean degraded) {
    }

    /** Keeps traces in memory only; the default when no Langfuse endpoint is configured. */
    final class InMemory implements TraceExporter {
        private final java.util.List<TraceRecorder.Trace> traces = new java.util.concurrent.CopyOnWriteArrayList<>();

        @Override
        public void export(TraceRecorder.Trace trace) {
            traces.add(trace);
        }

        @Override
        public ExporterStatus status() {
            return new ExporterStatus("in-memory", true, 0, traces.size(), 0, 0, null, false);
        }

        public java.util.List<TraceRecorder.Trace> traces() {
            return java.util.List.copyOf(traces);
        }
    }
}
