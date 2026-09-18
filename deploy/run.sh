#!/usr/bin/env bash
# Convenience script to run the packaged jar, locally or on a cloud instance.
# The listening port is configurable through the PORT environment variable
# (or a positional argument), matching WebFramework#resolvePort.
#
# Usage:
#   ./run.sh              # listens on 8080 (default)
#   ./run.sh 8081          # listens on 8081
#   PORT=8081 ./run.sh     # equivalent, via environment variable
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="${JAR_PATH:-$SCRIPT_DIR/../target/maintainable-application-server-1.0.0.jar}"
export PORT="${1:-${PORT:-8080}}"

echo "Starting maintainable-application-server on port $PORT using $JAR_PATH"
exec java -jar "$JAR_PATH"
