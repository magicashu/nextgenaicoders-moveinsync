package com.moveinsync.mobilitycopilot.ingestion.application;

import com.moveinsync.mobilitycopilot.ingestion.domain.DatasetProfile;
import java.nio.file.Path;

/** WS1: immutable official data, strict normalization, idempotent import and reconciliation. */
public interface DatasetProfileService {
    DatasetProfile profile(Path sourceDirectory);
}
