#!/usr/bin/env sh
set -eu

if [ -x /usr/libexec/java_home ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 21)"
  export JAVA_HOME
  PATH="$JAVA_HOME/bin:$PATH"
  export PATH
fi

dataset_dir="outputs/official dataset/MoveInSync - Anonymised Trip-Log Dataset"
if [ ! -d "$dataset_dir" ]; then
  echo "Official dataset not found: $dataset_dir" >&2
  exit 1
fi

log_file="/tmp/mobility-copilot-official-backend.log"
MOBILITY_DATA_DIR="$dataset_dir" ./mvnw -q -pl backend spring-boot:run >"$log_file" 2>&1 &
backend_pid=$!
trap 'kill "$backend_pid" 2>/dev/null || true' EXIT INT TERM

attempt=0
until curl --fail --silent 'http://localhost:8080/actuator/health' >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 30 ]; then
    tail -80 "$log_file"
    exit 1
  fi
  sleep 1
done

response="$(curl --fail --silent --show-error \
  -H 'X-Business-Unit: pinnacle-Slc' \
  'http://localhost:8080/api/v1/briefs/morning?asOf=2026-06-08')"

printf '%s' "$response" | python3 -c '
import json, sys
brief = json.load(sys.stdin)
metric = brief["operations"]["headlineKpi"]["metric"]
assert metric["metricId"] == "M01_DELAYED_TRIP_RATE"
assert metric["status"] == "SUPPORTED"
assert float(metric["value"]) == 21.88
assert float(metric["baselineValue"]) == 12.28
assert float(metric["delta"]) == 9.60
assert float(metric["numerator"]) == 4357.0
assert float(metric["denominator"]) == 19913.0
assert metric["unit"] == "PERCENT"
assert metric["contractVersion"] == "metrics-v1.1"
assert brief["status"] == "AWAITING_APPROVAL"
assert brief["trust"]["finalStep"] == "AWAITING_APPROVAL"
assert brief["operations"]["approval"]["status"] == "PENDING"
assert len(brief["trust"]["transitions"]) >= 16
print("Official product API valid: M01=21.88%, baseline=12.28%, delta=9.60pp, approval=PENDING")
'

MOBILITY_API='http://localhost:8080' MOBILITY_SMOKE_APPROVE=true sh scripts/demo/smoke.sh
MOBILITY_API='http://localhost:8080' sh scripts/demo/scorecard.sh
