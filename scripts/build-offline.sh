#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAVEN_REPO="${MAVEN_REPO:-${ROOT_DIR}/offline-deps/maven-repository}"
NPM_CACHE="${NPM_CACHE:-${ROOT_DIR}/offline-deps/npm-cache}"
SKIP_FRONTEND="${SKIP_FRONTEND:-false}"
SKIP_MAVEN="${SKIP_MAVEN:-false}"
RUN_TESTS="${RUN_TESTS:-false}"

log() {
    printf '[offline-build] %s\n' "$*"
}

require_cmd() {
    if ! command -v "$1" >/dev/null 2>&1; then
        printf 'Required command not found: %s\n' "$1" >&2
        exit 1
    fi
}

require_dir() {
    if [ ! -d "$1" ]; then
        printf 'Required offline directory not found: %s\n' "$1" >&2
        exit 1
    fi
}

require_cmd mvn
require_cmd npm
require_dir "$MAVEN_REPO"
require_dir "$NPM_CACHE"

if [ "$SKIP_FRONTEND" != "true" ]; then
    log "Installing frontend dependencies from offline npm cache"
    cd "$ROOT_DIR/frontend"
    npm ci --offline --cache "$NPM_CACHE" --legacy-peer-deps

    log "Building frontend"
    npm run build

    static_dir="${ROOT_DIR}/supervisor-agent/src/main/resources/static"
    log "Copying frontend dist to $static_dir"
    rm -rf "$static_dir"
    mkdir -p "$static_dir"
    cp -R dist/. "$static_dir/"
fi

if [ "$SKIP_MAVEN" != "true" ]; then
    log "Building Maven modules from offline repository"
    cd "$ROOT_DIR"
    test_arg="-DskipTests"
    if [ "$RUN_TESTS" = "true" ]; then
        test_arg=""
    fi
    mvn -o -B -Dmaven.repo.local="$MAVEN_REPO" clean package $test_arg
fi

log "Offline build complete"
