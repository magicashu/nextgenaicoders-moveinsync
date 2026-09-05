#!/usr/bin/env sh
set -eu

if [ -x /usr/libexec/java_home ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 21)"
  export JAVA_HOME
  PATH="$JAVA_HOME/bin:$PATH"
  export PATH
fi

log_file="/tmp/mobility-copilot-backend.log"
./mvnw -q -pl backend spring-boot:run >"$log_file" 2>&1 &
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
  'http://localhost:8080/api/v1/demo/brief?asOf=2026-06-08')"

printf '%s' "$response" | python3 -c '
import json, sys
brief = json.load(sys.stdin)
assert float(brief["metric"]["valuePercent"]) == 30.0
assert float(brief["metric"]["baselinePercent"]) == 10.0
assert brief["status"] == "AWAITING_APPROVAL"
print("API valid: M01=30.0%, baseline=10.0%, status=AWAITING_APPROVAL")
'
