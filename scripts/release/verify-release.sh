#!/usr/bin/env sh
set -eu

if [ -x /usr/libexec/java_home ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 21)"
  export JAVA_HOME
  PATH="$JAVA_HOME/bin:$PATH"
  export PATH
fi

script_dir="$(CDPATH= cd "$(dirname "$0")" && pwd)"
repo_root="$(CDPATH= cd "$script_dir/../.." && pwd)"
cd "$repo_root"

dataset_dir="$repo_root/outputs/official dataset/MoveInSync - Anonymised Trip-Log Dataset"
checksum_manifest="$repo_root/contracts/data/official-checksums.sha256"

if [ ! -d "$dataset_dir" ]; then
  echo "Official dataset not found: $dataset_dir" >&2
  exit 1
fi

echo "[1/6] Verifying immutable organizer files"
if command -v sha256sum >/dev/null 2>&1; then
  (cd "$dataset_dir" && sha256sum --check "$checksum_manifest")
else
  (cd "$dataset_dir" && shasum -a 256 -c "$checksum_manifest")
fi

echo "[2/6] Running backend, frontend, contract and fixture tests"
./scripts/verify.sh

echo "[3/6] Reconciling governed metric against official data"
./scripts/integration/verify-official-data.sh

echo "[4/6] Exercising fixture-backed HTTP workflow"
./scripts/demo/verify-api.sh

echo "[5/6] Exercising official-data HTTP workflow, approval and adversarial scorecard"
./scripts/integration/verify-official-api.sh

echo "[6/6] Validating generated evaluation artifacts"
./mvnw -pl backend -Dtest=MetricFixtureGateTest,ScorecardTest,AdversarialCorpusTest test

echo "Release verification passed"
