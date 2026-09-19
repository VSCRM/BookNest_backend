#!/bin/bash
# ============================================================================
# Combined entrypoint: runs auth-service (Spring, :9000) and the Rails app
# (:3000) as two processes INSIDE THE SAME CONTAINER.
#
# Why: they used to run in separate containers/hosts, each with its own
# clock. auth-service signs JWTs using its own "now"; Rails verifies the
# `exp` claim using ITS "now". If those two clocks drift apart (very common
# with Docker Desktop's VM on Windows/Mac, especially after the laptop
# sleeps), Rails starts rejecting freshly-issued tokens as "expired" —
# 401s on /orders, /admin/books, etc. even though nothing is actually wrong
# with the token. Putting both processes in one container means they share
# one kernel clock, so that specific class of skew becomes impossible.
#
# On top of that, this container syncs its own clock against real-world
# time on startup (via HTTP, so it works even where outbound NTP/UDP-123 is
# blocked), so an absolute drift between the Docker VM and reality gets
# corrected too, not just the relative one between the two services.
# ============================================================================
set -u

echo "[entrypoint] clock before sync: $(date)"

if command -v htpdate >/dev/null 2>&1; then
	# -s: set the clock immediately (one-shot, not a daemon).
	# Best-effort — if outbound network is blocked/unavailable, don't fail
	# the whole container over it; just keep whatever clock we have.
	htpdate -s google.com cloudflare.com github.com 2>&1 || \
		echo "[entrypoint] WARNING: htpdate sync failed (no network / blocked?) — continuing with existing clock"
else
	echo "[entrypoint] WARNING: htpdate not installed — skipping clock sync"
fi

echo "[entrypoint] clock after sync:  $(date)"

cd /rails

# The bind-mounted ./BookNest/backend:/rails volume (see docker-compose.yml)
# replaces the image's files with the host copy at container start,
# permission bits included — so if bin/rails ever loses its executable bit
# on the host, the image's own `chmod +x` from build time is silently
# overwritten. Redo it here so the container starts reliably regardless of
# host-side file permissions.
chmod +x bin/* 2>/dev/null || true
rm -f tmp/pids/server.pid

(bundle check || bundle install) || {
	echo "[entrypoint] bundle install failed" >&2
	exit 1
}

bin/rails db:prepare || {
	echo "[entrypoint] rails db:prepare failed" >&2
	exit 1
}

echo "[entrypoint] starting auth-service (Spring Boot) on :9000 ..."
java -jar /app/auth-service.jar &
AUTH_PID=$!

echo "[entrypoint] starting Rails on :3000 ..."
bundle exec rails s -b 0.0.0.0 &
RAILS_PID=$!

# Forward termination signals to both children and wait for a clean exit
# (otherwise `docker compose down` / `docker stop` has to wait out the full
# kill-timeout and SIGKILL both processes instead of shutting down nicely).
term_handler() {
	echo "[entrypoint] caught signal, stopping both processes..."
	kill -TERM "$AUTH_PID" "$RAILS_PID" 2>/dev/null
	wait "$AUTH_PID" "$RAILS_PID" 2>/dev/null
	exit 0
}
trap term_handler TERM INT

# If either process dies on its own, bring the whole container down instead
# of silently limping along with only one service reachable.
wait -n "$AUTH_PID" "$RAILS_PID"
EXIT_CODE=$?
echo "[entrypoint] one of the processes exited (code $EXIT_CODE) — stopping the other"
kill -TERM "$AUTH_PID" "$RAILS_PID" 2>/dev/null
wait "$AUTH_PID" "$RAILS_PID" 2>/dev/null
exit "$EXIT_CODE"
