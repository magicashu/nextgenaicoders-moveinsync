#!/usr/bin/env sh
set -eu

script_dir="$(CDPATH= cd "$(dirname "$0")" && pwd)"
repo_root="$(CDPATH= cd "$script_dir/../.." && pwd)"
cd "$repo_root"

dataset_dir="$repo_root/outputs/official dataset/MoveInSync - Anonymised Trip-Log Dataset"
checksum_manifest="$repo_root/contracts/data/official-checksums.sha256"

if [ ! -d "$dataset_dir" ]; then
  echo "Official dataset not found: $dataset_dir" >&2
  exit 1
fi

echo "[1/5] Verifying immutable organizer files"
if command -v sha256sum >/dev/null 2>&1; then
  (cd "$dataset_dir" && sha256sum --check "$checksum_manifest")
else
  (cd "$dataset_dir" && shasum -a 256 -c "$checksum_manifest")
fi

echo "[2/5] Running backend, frontend, contract and fixture tests"
./scripts/verify.sh

echo "[3/5] Reconciling governed metric against official data"
./scripts/integration/verify-official-data.sh

echo "[4/5] Exercising fixture-backed HTTP workflow"
./scripts/demo/verify-api.sh

echo "[5/5] Exercising official-data HTTP workflow"
./scripts/integration/verify-official-api.sh

echo "Release verification passed"
