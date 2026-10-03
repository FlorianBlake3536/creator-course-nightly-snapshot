#!/usr/bin/env sh
set -eu
BUILD_DIR="${TMPDIR:-/tmp}/creator-nightly-snapshot-classes"
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" src/main/java/*.java src/test/java/*.java
java -cp "$BUILD_DIR" NightlySnapshotServiceTest

