#!/usr/bin/env sh
set -eu

if [ -x /usr/libexec/java_home ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 21)"
  export JAVA_HOME
  PATH="$JAVA_HOME/bin:$PATH"
  export PATH
fi

./mvnw -pl backend test
npm run build
npm test
python3 scripts/data/validate-fixture.py
