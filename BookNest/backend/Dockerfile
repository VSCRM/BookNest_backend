# syntax = docker/dockerfile:1
ARG RUBY_VERSION=3.2.11
# NOTE: no custom registry prefix here — "registry.docker.com" is not a
# valid Docker Hub hostname and made every build fail on the FROM step
# with "pull access denied" / "not found". Docker Hub's official images
# are addressed as just "ruby:...".
FROM ruby:$RUBY_VERSION-slim

WORKDIR /rails

# Speeds up `bundle install` and makes it resilient to transient network
# hiccups during `docker compose build` / repeated `--build` runs.
ENV BUNDLE_JOBS=4 \
    BUNDLE_RETRY=3

RUN apt-get update -qq && \
    apt-get install --no-install-recommends -y build-essential git libsqlite3-0 libsqlite3-dev libpq-dev pkg-config curl && \
    rm -rf /var/lib/apt/lists/*

COPY Gemfile Gemfile.lock* ./
RUN bundle install

COPY . .

# The zip/tarball this project ships as does not always preserve the
# executable bit on bin/* (bin/rails, bin/setup) — without it, both
# `CMD ["bin/rails", ...]` below and docker-compose's runtime command
# (`bin/rails db:prepare && ...`) fail immediately with "Permission
# denied" on every fresh build. Fixing it here means a working container
# no matter how the source was checked out or unpacked.
RUN chmod +x bin/*

EXPOSE 3000

# Lets `docker compose ps` / `docker inspect` report real readiness
# instead of just "container is running" — useful when scripting
# repeated up/down cycles and waiting for the app to actually respond.
HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=5 \
  CMD curl -f http://localhost:3000/up || exit 1

CMD ["bin/rails", "server", "-b", "0.0.0.0"]
