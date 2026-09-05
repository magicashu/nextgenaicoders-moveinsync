#!/usr/bin/env sh
set -eu

curl --fail --silent --show-error \
  -H 'X-Business-Unit: pinnacle-Slc' \
  'http://localhost:8080/api/v1/demo/brief?asOf=2026-06-08'
