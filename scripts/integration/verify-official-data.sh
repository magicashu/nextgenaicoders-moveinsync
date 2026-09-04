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
  echo "Official dataset is required at: $dataset_dir" >&2
  exit 1
fi

./mvnw -pl backend -Dtest=OfficialDatasetReconciliationTest,OfficialMetricFixtureExporter test
