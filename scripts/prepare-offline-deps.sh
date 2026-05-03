#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAVEN_REPO="${MAVEN_REPO:-${ROOT_DIR}/offline-deps/maven-repository}"
NPM_CACHE="${NPM_CACHE:-${ROOT_DIR}/offline-deps/npm-cache}"
PACK_NODE_MODULES="${PACK_NODE_MODULES:-false}"
TARGET_LINUX_CLASSIFIERS="${TARGET_LINUX_CLASSIFIERS:-linux-x86_64 linux-aarch_64}"
TARGET_WINDOWS_CLASSIFIERS="${TARGET_WINDOWS_CLASSIFIERS:-windows-x86_64}"
NETTY_NATIVE_VERSION="${NETTY_NATIVE_VERSION:-4.2.10.Final}"
NETTY_TCNATIVE_VERSION="${NETTY_TCNATIVE_VERSION:-2.0.75.Final}"
SPRING_BOOT_VERSION="${SPRING_BOOT_VERSION:-4.0.4}"
MAVEN_COMPILER_PLUGIN_VERSION="${MAVEN_COMPILER_PLUGIN_VERSION:-3.15.0}"
MAVEN_SUREFIRE_PLUGIN_VERSION="${MAVEN_SUREFIRE_PLUGIN_VERSION:-3.5.5}"
MAVEN_DEPENDENCY_PLUGIN_VERSION="${MAVEN_DEPENDENCY_PLUGIN_VERSION:-3.6.0}"
MAVEN_CLEAN_PLUGIN_VERSION="${MAVEN_CLEAN_PLUGIN_VERSION:-3.2.0}"
MAVEN_RESOURCES_PLUGIN_VERSION="${MAVEN_RESOURCES_PLUGIN_VERSION:-3.3.1}"
MAVEN_JAR_PLUGIN_VERSION="${MAVEN_JAR_PLUGIN_VERSION:-3.3.0}"
MAVEN_INSTALL_PLUGIN_VERSION="${MAVEN_INSTALL_PLUGIN_VERSION:-3.1.1}"
MAVEN_DEPLOY_PLUGIN_VERSION="${MAVEN_DEPLOY_PLUGIN_VERSION:-3.1.1}"
MAVEN_SITE_PLUGIN_VERSION="${MAVEN_SITE_PLUGIN_VERSION:-3.12.1}"
OS_MAVEN_PLUGIN_VERSION="${OS_MAVEN_PLUGIN_VERSION:-1.7.1}"
AGENTSCOPE_VERSION="${AGENTSCOPE_VERSION:-1.0.12}"
VERIFY_MAVEN_OFFLINE="${VERIFY_MAVEN_OFFLINE:-true}"

log() {
    printf '[offline-deps] %s\n' "$*"
}

require_cmd() {
    if ! command -v "$1" >/dev/null 2>&1; then
        printf 'Required command not found: %s\n' "$1" >&2
        exit 1
    fi
}

require_cmd mvn
require_cmd node
require_cmd npm

mkdir -p "$MAVEN_REPO" "$NPM_CACHE"

TMP_MAVEN_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_MAVEN_DIR"' EXIT
cat > "$TMP_MAVEN_DIR/pom.xml" <<'POM'
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>offline.bootstrap</groupId>
    <artifactId>offline-bootstrap</artifactId>
    <version>1.0.0</version>
</project>
POM

maven_get() {
    mvn -B -f "$TMP_MAVEN_DIR/pom.xml" -Dmaven.repo.local="$MAVEN_REPO" dependency:get "$@"
}

log "Preparing Maven offline repository: $MAVEN_REPO"
log "Pre-fetching early Maven extensions and imported BOMs"
for early_artifact in \
    "kr.motd.maven:os-maven-plugin:${OS_MAVEN_PLUGIN_VERSION}" \
    "org.codehaus.plexus:plexus-utils:1.1" \
    "org.codehaus.plexus:plexus-utils:3.1.0" \
    "org.springframework.boot:spring-boot-dependencies:${SPRING_BOOT_VERSION}:pom" \
    "io.agentscope:agentscope-dependencies-bom:${AGENTSCOPE_VERSION}:pom" \
    "io.agentscope:agentscope-bom:${AGENTSCOPE_VERSION}:pom"; do
    maven_get \
        -Dartifact="$early_artifact" \
        -Dtransitive=true
done

cd "$ROOT_DIR"
mvn -B -DskipTests -Dmaven.repo.local="$MAVEN_REPO" validate
mvn -B -DskipTests -Dmaven.repo.local="$MAVEN_REPO" dependency:go-offline
mvn -B -DskipTests -Dmaven.repo.local="$MAVEN_REPO" dependency:resolve dependency:resolve-plugins

log "Pre-fetching Maven build plugins"
for plugin_artifact in \
    "org.apache.maven.plugins:maven-clean-plugin:${MAVEN_CLEAN_PLUGIN_VERSION}" \
    "org.apache.maven.plugins:maven-resources-plugin:${MAVEN_RESOURCES_PLUGIN_VERSION}" \
    "org.springframework.boot:spring-boot-maven-plugin:${SPRING_BOOT_VERSION}" \
    "org.apache.maven.plugins:maven-compiler-plugin:${MAVEN_COMPILER_PLUGIN_VERSION}" \
    "org.apache.maven.plugins:maven-surefire-plugin:${MAVEN_SUREFIRE_PLUGIN_VERSION}" \
    "org.apache.maven.plugins:maven-jar-plugin:${MAVEN_JAR_PLUGIN_VERSION}" \
    "org.apache.maven.plugins:maven-install-plugin:${MAVEN_INSTALL_PLUGIN_VERSION}" \
    "org.apache.maven.plugins:maven-deploy-plugin:${MAVEN_DEPLOY_PLUGIN_VERSION}" \
    "org.apache.maven.plugins:maven-site-plugin:${MAVEN_SITE_PLUGIN_VERSION}" \
    "org.apache.maven.plugins:maven-dependency-plugin:${MAVEN_DEPENDENCY_PLUGIN_VERSION}"; do
    maven_get \
        -Dartifact="$plugin_artifact" \
        -Dtransitive=true
done

log "Pre-fetching Linux native artifacts for target classifiers: $TARGET_LINUX_CLASSIFIERS"
for classifier in $TARGET_LINUX_CLASSIFIERS; do
    for artifact in \
        "io.netty:netty-transport-native-unix-common:${NETTY_NATIVE_VERSION}:jar:${classifier}" \
        "io.netty:netty-transport-native-epoll:${NETTY_NATIVE_VERSION}:jar:${classifier}" \
        "io.netty:netty-transport-native-io_uring:${NETTY_NATIVE_VERSION}:jar:${classifier}" \
        "io.netty:netty-codec-native-quic:${NETTY_NATIVE_VERSION}:jar:${classifier}" \
        "io.netty:netty-tcnative-boringssl-static:${NETTY_TCNATIVE_VERSION}:jar:${classifier}"; do
        maven_get \
            -Dartifact="$artifact" \
            -Dtransitive=false
    done
done

if printf '%s\n' "$TARGET_LINUX_CLASSIFIERS" | grep -qw "linux-x86_64"; then
    maven_get \
        -Dartifact="io.netty:netty-tcnative:${NETTY_TCNATIVE_VERSION}:jar:linux-x86_64" \
        -Dtransitive=false
    maven_get \
        -Dartifact="io.netty:netty-tcnative:${NETTY_TCNATIVE_VERSION}:jar:linux-x86_64-fedora" \
        -Dtransitive=false
fi

log "Pre-fetching Windows native artifacts for validation classifiers: $TARGET_WINDOWS_CLASSIFIERS"
for classifier in $TARGET_WINDOWS_CLASSIFIERS; do
    for artifact in \
        "io.netty:netty-codec-native-quic:${NETTY_NATIVE_VERSION}:jar:${classifier}" \
        "io.netty:netty-tcnative-boringssl-static:${NETTY_TCNATIVE_VERSION}:jar:${classifier}"; do
        maven_get \
            -Dartifact="$artifact" \
            -Dtransitive=false
    done
done

log "Removing Maven transfer markers and remote-origin metadata for portability"
find "$MAVEN_REPO" -name "*.lastUpdated" -delete
find "$MAVEN_REPO" -name "_remote.repositories" -delete

if [ "$VERIFY_MAVEN_OFFLINE" = "true" ]; then
    log "Verifying Maven can clean package offline from $MAVEN_REPO"
    mvn -o -B -DskipTests -Dmaven.repo.local="$MAVEN_REPO" clean package
fi

log "Preparing npm offline cache: $NPM_CACHE"
cd "$ROOT_DIR/frontend"
node -e '
const lock = require("./package-lock.json");
const urls = new Set();
for (const pkg of Object.values(lock.packages || {})) {
  if (pkg && typeof pkg.resolved === "string" && /^https?:\/\//.test(pkg.resolved)) {
    urls.add(pkg.resolved);
  }
}
for (const url of urls) {
  console.log(url);
}
' | while IFS= read -r tarball_url; do
    npm cache add "$tarball_url" --cache "$NPM_CACHE"
done
npm ci --cache "$NPM_CACHE" --legacy-peer-deps --prefer-offline
npm cache verify --cache "$NPM_CACHE"

if [ "$PACK_NODE_MODULES" = "true" ]; then
    os_name="$(uname -s | tr '[:upper:]' '[:lower:]')"
    arch_name="$(uname -m)"
    archive="${ROOT_DIR}/offline-deps/node_modules-${os_name}-${arch_name}.tar.gz"
    log "Packing platform-specific node_modules fallback: $archive"
    tar -czf "$archive" node_modules
fi

log "Offline dependencies are ready under ${ROOT_DIR}/offline-deps"
