#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

if [[ -f "$repo_root/.env.backend.local" ]]; then
  set -a
  source "$repo_root/.env.backend.local"
  set +a
fi

: "${SARVAM_API_KEY:?Export SARVAM_API_KEY in this terminal first}"
: "${LANGFUSE_PUBLIC_KEY:?Export LANGFUSE_PUBLIC_KEY in this terminal first}"
: "${LANGFUSE_SECRET_KEY:?Export LANGFUSE_SECRET_KEY in this terminal first}"

export LANGUAGE_MODEL=sarvam
export SARVAM_MODEL="${SARVAM_MODEL:-sarvam-105b}"
export SARVAM_TIMEOUT="${SARVAM_TIMEOUT:-30s}"
export LANGFUSE_HOST="${LANGFUSE_BASE_URL:-${LANGFUSE_HOST:-https://cloud.langfuse.com}}"
export ANALYTICS_GATEWAY=governed
export DECISION_RUN_GATEWAY=workflow
export ACTOR_RESOLVER=governance
export CONTROL_PLANE="${CONTROL_PLANE:-in-memory}"

if [[ -z "${JAVA_HOME:-}" ]]; then
  local_jdk="/Users/miniorange/Library/Java/JavaVirtualMachines/jbr-21.0.10/Contents/Home"
  if [[ -x "$local_jdk/bin/java" ]]; then
    export JAVA_HOME="$local_jdk"
  else
    export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
  fi
fi
export PATH="$JAVA_HOME/bin:$PATH"

if [[ -z "${MOBILITY_DATA_DIR:-}" ]]; then
  if [[ -d "$repo_root/outputs/MoveInSync - Anonymised Trip-Log Dataset" ]]; then
    export MOBILITY_DATA_DIR="$repo_root/outputs/MoveInSync - Anonymised Trip-Log Dataset"
  else
    export MOBILITY_DATA_DIR="$repo_root/outputs/official dataset/MoveInSync - Anonymised Trip-Log Dataset"
  fi
fi
if [[ ! -d "$MOBILITY_DATA_DIR" ]]; then
  echo "Set MOBILITY_DATA_DIR to the existing official dataset directory." >&2
  exit 1
fi

# Clean removes stale classes from the deleted state-machine package. Tests are skipped as requested.
./mvnw -pl backend clean package -DskipTests
exec "$JAVA_HOME/bin/java" -jar backend/target/mobility-decision-copilot-backend-0.1.0-SNAPSHOT.jar
