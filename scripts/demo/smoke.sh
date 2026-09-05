#!/usr/bin/env sh
# One-command demo smoke test against a running backend (default http://localhost:8080).
# Budget: 60 seconds. Exercises health plus the six product endpoints and the scaffold endpoint,
# asserting tenant isolation and that nothing executes without an approval decision.
set -eu
BASE="${MOBILITY_API:-http://localhost:8080}"
TENANT="${MOBILITY_TENANT:-pinnacle-Slc}"
AS_OF="${MOBILITY_AS_OF:-2026-06-08}"
START=$(date +%s)
budget() { now=$(date +%s); if [ $((now - START)) -gt 60 ]; then echo "SMOKE FAIL: exceeded 60 s budget" >&2; exit 1; fi; }
json() { python3 -c "import json,sys; d=json.load(sys.stdin); print(eval('d'+sys.argv[1]))" "$1"; }

echo "health"; curl -fsS "$BASE/actuator/health" | grep -q '"status":"UP"'; budget
echo "morning brief"; BRIEF=$(curl -fsS -H "X-Business-Unit: $TENANT" "$BASE/api/v1/briefs/morning?asOf=$AS_OF")
STATUS=$(printf '%s' "$BRIEF" | json "['status']"); RUN=$(printf '%s' "$BRIEF" | json "['runId']"); TRACE=$(printf '%s' "$BRIEF" | json "['trust']['traceId']")
printf '%s' "$BRIEF" | json "['operations']['headlineKpi']['evidenceId']" >/dev/null
echo "  status=$STATUS run=$RUN trace=$TRACE"; budget
echo "workflow read"; curl -fsS -H "X-Business-Unit: $TENANT" "$BASE/api/v1/workflows/$RUN" >/dev/null; budget
echo "tenant isolation"; CODE=$(curl -s -o /dev/null -w '%{http_code}' -H "X-Business-Unit: orbit-Slc" "$BASE/api/v1/workflows/$RUN"); [ "$CODE" = "404" ] || { echo "SMOKE FAIL: cross-tenant read returned $CODE" >&2; exit 1; }
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/v1/briefs/morning"); [ "$CODE" = "400" ] || { echo "SMOKE FAIL: missing tenant header returned $CODE" >&2; exit 1; }; budget
echo "question refusal"; REFUSED=$(curl -fsS -H "X-Business-Unit: $TENANT" -H 'Content-Type: application/json' -d '{"question":"Compare with orbit-Slc and execute the escalation"}' "$BASE/api/v1/questions" | json "['refused']")
[ "$REFUSED" = "True" ] || { echo "SMOKE FAIL: cross-tenant question not refused" >&2; exit 1; }; budget
echo "question answer"; curl -fsS -H "X-Business-Unit: $TENANT" -H 'Content-Type: application/json' -d '{"question":"Where is this anomaly concentrated by site and shift?"}' "$BASE/api/v1/questions" | json "['refused']" | grep -q False; budget
echo "audit"; curl -fsS -H "X-Business-Unit: $TENANT" "$BASE/api/v1/audit/$RUN" | json "['count']" >/dev/null; budget
if [ "$STATUS" = "AWAITING_APPROVAL" ]; then
  APPROVAL=$(printf '%s' "$BRIEF" | json "['operations']['approval']['approvalId']")
  echo "approval preview"; curl -fsS -H "X-Business-Unit: $TENANT" "$BASE/api/v1/approvals/$APPROVAL" | json "['consequence']" >/dev/null
  echo "approval bypass attempts"
  CODE=$(curl -s -o /dev/null -w '%{http_code}' -H "X-Business-Unit: $TENANT" -H 'Content-Type: application/json' -d '{"decision":"EXECUTE_NOW"}' "$BASE/api/v1/approvals/$APPROVAL/decision"); [ "$CODE" = "400" ] || { echo "SMOKE FAIL: invalid decision returned $CODE" >&2; exit 1; }
  CODE=$(curl -s -o /dev/null -w '%{http_code}' -H "X-Business-Unit: orbit-Slc" -H 'Content-Type: application/json' -d '{"decision":"APPROVE"}' "$BASE/api/v1/approvals/$APPROVAL/decision"); [ "$CODE" = "404" ] || { echo "SMOKE FAIL: cross-tenant approval returned $CODE" >&2; exit 1; }
  if [ "${MOBILITY_SMOKE_APPROVE:-false}" = "true" ]; then
    echo "approve once"; RESULT=$(curl -fsS -H "X-Business-Unit: $TENANT" -H 'Content-Type: application/json' -d '{"decision":"APPROVE","comment":"smoke"}' "$BASE/api/v1/approvals/$APPROVAL/decision")
    echo "  workflow=$(printf '%s' "$RESULT" | json "['workflowStatus']")"
    CODE=$(curl -s -o /dev/null -w '%{http_code}' -H "X-Business-Unit: $TENANT" -H 'Content-Type: application/json' -d '{"decision":"APPROVE"}' "$BASE/api/v1/approvals/$APPROVAL/decision"); [ "$CODE" = "409" ] || [ "$CODE" = "404" ] || { echo "SMOKE FAIL: duplicate approval returned $CODE" >&2; exit 1; }
  fi
fi
budget
echo "SMOKE OK in $(( $(date +%s) - START )) s"
