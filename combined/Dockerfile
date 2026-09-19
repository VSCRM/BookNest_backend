# syntax = docker/dockerfile:1
#
# Build context for this file MUST be the repo root (see docker-compose.yml:
# `context: .`, `dockerfile: combined/Dockerfile`), because it needs to COPY
# from both auth-service/ and BookNest/backend/.
#
# Runs auth-service (Spring Boot, :9000) and BookNest's Rails app (:3000) as
# two processes in ONE container (see combined/entrypoint.sh for why: it
# eliminates clock skew between them, which was causing Rails to reject
# freshly-issued JWTs as "expired").

# ─── Stage 1: build the auth-service jar ───────────────────────────────────
# Image JDK major version MUST match <java.version> in auth-service/pom.xml
# (currently 25).
FROM maven:3.9-eclipse-temurin-25 AS auth-build
WORKDIR /build
COPY auth-service/pom.xml ./
RUN mvn -B dependency:go-offline
COPY auth-service/src ./src
RUN mvn -B clean package -DskipTests

# ─── Stage 2: runtime image with both Ruby (Rails) and a JRE (auth-service) ─
# (hardcoded, not an ARG: Docker Compose's newer "bake" build driver has a
# bug where an ARG's default value doesn't get substituted into a non-first
# stage's FROM line — it resolves to an empty string and fails with
# "invalid reference format" (`ruby:-slim`). Simplest robust fix: no ARG here.)
FROM ruby:3.2.11-slim

WORKDIR /rails

ENV BUNDLE_JOBS=4 \
    BUNDLE_RETRY=3

RUN apt-get update -qq && \
    apt-get install --no-install-recommends -y \
        build-essential git libsqlite3-0 libsqlite3-dev pkg-config curl \
        wget gnupg ca-certificates htpdate && \
    rm -rf /var/lib/apt/lists/*

# Eclipse Adoptium's apt repo — Debian bookworm (the ruby:*-slim base) is a
# supported distro. Installs a JRE 25 to match auth-service's build target.
RUN wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public \
        | gpg --dearmor -o /usr/share/keyrings/adoptium.gpg && \
    echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print $2}' /etc/os-release) main" \
        > /etc/apt/sources.list.d/adoptium.list && \
    apt-get update -qq && \
    apt-get install --no-install-recommends -y temurin-25-jre && \
    rm -rf /var/lib/apt/lists/*

COPY BookNest/backend/Gemfile BookNest/backend/Gemfile.lock* ./
RUN bundle install

COPY BookNest/backend/. .
RUN chmod +x bin/*

RUN mkdir -p /app
COPY --from=auth-build /build/target/auth-service-*.jar /app/auth-service.jar

COPY combined/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 3000 9000

# No `-f`: auth-service doesn't have Spring Actuator wired in, so a plain
# 401/404 from it is still proof the process is up and accepting
# connections — only an actual connection failure (refused/timeout) should
# fail this check.
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
  CMD curl -sf http://localhost:3000/up >/dev/null && curl -s -o /dev/null http://localhost:9000/ || exit 1

ENTRYPOINT ["/entrypoint.sh"]
