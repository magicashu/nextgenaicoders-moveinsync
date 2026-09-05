# Data boundary

`fixtures/` contains tiny synthetic inputs for tests and UI development. It is not evidence for the final submission.

Set `MOBILITY_DATA_DIR` to the organizer directory when running against official data. The application reads `Ride_data _trip-*.csv` without rewriting it.

Generated degraded-data variants V1-V5 belong under `corrupted/generated/`. Never generate them inside `outputs/official dataset/`.
