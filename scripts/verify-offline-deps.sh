#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAVEN_REPO="${MAVEN_REPO:-${ROOT_DIR}/offline-deps/maven-repository}"
NPM_CACHE="${NPM_CACHE:-${ROOT_DIR}/offline-deps/npm-cache}"

log() {
    printf '[offline-verify] %s\n' "$*"
}

require_file() {
    if [ ! -f "$1" ]; then
        printf 'Missing required offline artifact: %s\n' "$1" >&2
        exit 1
    fi
}

if [ ! -d "$MAVEN_REPO" ]; then
    printf 'Missing Maven offline repository: %s\n' "$MAVEN_REPO" >&2
    exit 1
fi

if [ ! -d "$NPM_CACHE" ]; then
    printf 'Missing npm offline cache: %s\n' "$NPM_CACHE" >&2
    exit 1
fi

require_file "$MAVEN_REPO/kr/motd/maven/os-maven-plugin/1.7.1/os-maven-plugin-1.7.1.jar"
require_file "$MAVEN_REPO/org/codehaus/plexus/plexus-utils/1.1/plexus-utils-1.1.jar"
require_file "$MAVEN_REPO/org/codehaus/plexus/plexus-utils/3.1.0/plexus-utils-3.1.0.jar"
require_file "$MAVEN_REPO/org/springframework/boot/spring-boot-dependencies/4.0.4/spring-boot-dependencies-4.0.4.pom"
require_file "$MAVEN_REPO/io/agentscope/agentscope-dependencies-bom/1.0.12/agentscope-dependencies-bom-1.0.12.pom"
require_file "$MAVEN_REPO/io/agentscope/agentscope-bom/1.0.12/agentscope-bom-1.0.12.pom"

if find "$MAVEN_REPO" -name "*.lastUpdated" | grep -q .; then
    printf 'Found Maven .lastUpdated marker(s) in offline repository. Regenerate offline-deps before copying.\n' >&2
    find "$MAVEN_REPO" -name "*.lastUpdated" | head -20 >&2
    exit 1
fi

if find "$MAVEN_REPO" -name "_remote.repositories" | grep -q .; then
    printf 'Found Maven _remote.repositories metadata in offline repository. Remove it before copying across machines.\n' >&2
    find "$MAVEN_REPO" -name "_remote.repositories" | head -20 >&2
    exit 1
fi

cd "$ROOT_DIR"
log "Verifying Maven offline clean package"
mvn -o -B -DskipTests -Dmaven.repo.local="$MAVEN_REPO" clean package

cd "$ROOT_DIR/frontend"
log "Verifying npm offline install"
npm ci --offline --cache "$NPM_CACHE" --legacy-peer-deps

log "Offline dependencies verified"
