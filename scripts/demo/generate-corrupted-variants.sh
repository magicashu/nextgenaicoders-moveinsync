#!/usr/bin/env sh
# Generates V1-V5 degraded copies under data/corrupted/generated (git-ignored). Never touches the originals.
set -eu
OFFICIAL="${MOBILITY_OFFICIAL_DATA_DIR:-outputs/official dataset/MoveInSync - Anonymised Trip-Log Dataset}"
python3 evals/corrupted/generate_variants.py "$OFFICIAL" data/corrupted/generated
