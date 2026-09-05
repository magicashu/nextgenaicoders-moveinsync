#!/usr/bin/env sh
set -eu

npm install
mvn wrapper:wrapper -Dtype=only-script
chmod +x mvnw scripts/dev/start.sh scripts/demo/smoke.sh scripts/demo/verify-api.sh scripts/data/validate-fixture.py scripts/verify.sh
