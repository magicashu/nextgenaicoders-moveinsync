#!/usr/bin/env sh
# Starts the backend, waits for health, verifies the scaffold endpoint and runs the smoke test.
# MOBILITY_DATA_DIR selects the dataset: the tiny fixture (default), data/fixtures/seven-file-sample,
# or the immutable official directory. Expected M01 values follow the dataset.
set -eu
if [ -x /usr/libexec/java_home ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 21)"; export JAVA_HOME; PATH="$JAVA_HOME/bin:$PATH"; export PATH
fi
PORT="${SERVER_PORT:-8080}"
log_file="${TMPDIR:-/tmp}/mobility-copilot-backend.log"
SERVER_PORT="$PORT" ./mvnw -q -pl backend spring-boot:run >"$log_file" 2>&1 &
backend_pid=$!
trap 'kill "$backend_pid" 2>/dev/null || true' EXIT INT TERM
attempt=0
until curl --fail --silent "http://localhost:$PORT/actuator/health" >/dev/null 2>&1; do
  attempt=$((attempt + 1)); if [ "$attempt" -ge 90 ]; then tail -80 "$log_file"; exit 1; fi; sleep 1
done
response="$(curl --fail --silent --show-error -H 'X-Business-Unit: pinnacle-Slc' "http://localhost:$PORT/api/v1/demo/brief?asOf=2026-06-08")"
printf '%s' "$response" | python3 -c '
import json, os, sys
brief = json.load(sys.stdin)
official = "official dataset" in os.environ.get("MOBILITY_DATA_DIR", "")
expected = (21.88, 12.28) if official else (30.0, 10.0)
assert abs(float(brief["metric"]["value"]) - expected[0]) < 0.01, brief["metric"]["value"]
assert abs(float(brief["metric"]["baselineValue"]) - expected[1]) < 0.01, brief["metric"]["baselineValue"]
assert brief["metric"]["unit"] == "PERCENT"
assert brief["metric"]["contractVersion"] == "metrics-v1.1"
assert brief["status"] in ("AWAITING_APPROVAL", "REPORT_ONLY", "HEALTHY"), brief["status"]
print("API valid: M01=%s%%, baseline=%s%%, status=%s" % (brief["metric"]["value"], brief["metric"]["baselineValue"], brief["status"]))
'
MOBILITY_API="http://localhost:$PORT" sh scripts/demo/smoke.sh
