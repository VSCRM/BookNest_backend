#!/usr/bin/env bash
#
# pack-release.sh — builds everything to attach to a GitHub Release:
#   1. source archive (.tar.gz, tracked files only — no .env, no build output)
#   2. Docker image archive (.tar.gz, loadable with `docker load`)
#   3. SHA-256 checksums for both
#
# Run from the repository root (next to docker-compose.yml):
#   ./pack-release.sh 1.0
#
# Optional: build for a specific CPU architecture (e.g. on an Apple Silicon Mac
# when the target is a regular Linux server):
#   PLATFORM=linux/amd64 ./pack-release.sh 1.0

set -euo pipefail

VERSION="${1:-1.0}"
IMAGE="booknest-backend:${VERSION}"
OUT_DIR="release-assets"
SRC_ARCHIVE="BookNest-backend-v${VERSION}-source.tar.gz"
IMG_ARCHIVE="booknest-backend-${VERSION}-docker-image.tar.gz"
MAX_BYTES=$((2 * 1024 * 1024 * 1024 - 1))   # GitHub release asset limit: 2 GiB

# ── Preconditions ───────────────────────────────────────────────────────────
for tool in git docker gzip; do
  command -v "$tool" >/dev/null 2>&1 || { echo "ERROR: '$tool' is not installed." >&2; exit 1; }
done
[ -f combined/Dockerfile ] || { echo "ERROR: run this from the repository root (combined/Dockerfile not found)." >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "ERROR: the Docker daemon is not running." >&2; exit 1; }

sha256() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$@"; else shasum -a 256 "$@"; fi
}
size_of() { wc -c < "$1" | tr -d ' '; }

if [ -n "$(git status --porcelain)" ]; then
  echo "WARNING: you have uncommitted changes. The source archive contains only committed files," >&2
  echo "         but the Docker image is built from your working directory." >&2
fi

mkdir -p "$OUT_DIR"

# ── 1. Source archive ───────────────────────────────────────────────────────
# Uses the tag v<VERSION> if it already exists, otherwise the current commit (HEAD).
REF="HEAD"
if git rev-parse --verify --quiet "refs/tags/v${VERSION}" >/dev/null; then REF="v${VERSION}"; fi
echo "==> [1/3] Source archive from ${REF}"
git archive --format=tar.gz --prefix="BookNest_backend-${VERSION}/" \
  -o "${OUT_DIR}/${SRC_ARCHIVE}" "$REF"

# ── 2. Docker image ─────────────────────────────────────────────────────────
echo "==> [2/3] Building Docker image ${IMAGE}"
if [ -n "${PLATFORM:-}" ]; then
  docker buildx build --platform "$PLATFORM" -f combined/Dockerfile -t "$IMAGE" --load .
else
  docker build -f combined/Dockerfile -t "$IMAGE" .
fi

# Safety check: a real .env must never be baked into the image.
if docker run --rm --entrypoint sh "$IMAGE" -c 'test -f /rails/.env'; then
  echo "ERROR: /rails/.env exists inside the image — it would leak your secrets." >&2
  echo "       Remove BookNest/backend/.env (or add it to .dockerignore) and run again." >&2
  exit 1
fi

echo "==> Saving image to ${IMG_ARCHIVE} (this can take a minute)"
docker save "$IMAGE" | gzip -9 > "${OUT_DIR}/${IMG_ARCHIVE}"

if [ "$(size_of "${OUT_DIR}/${IMG_ARCHIVE}")" -gt "$MAX_BYTES" ]; then
  echo "WARNING: the image archive is larger than GitHub's 2 GiB limit per release asset." >&2
  echo "         Publish the image to ghcr.io instead of attaching it." >&2
fi

# ── 3. Checksums ────────────────────────────────────────────────────────────
echo "==> [3/3] Checksums"
( cd "$OUT_DIR" && sha256 "$SRC_ARCHIVE" "$IMG_ARCHIVE" > SHA256SUMS.txt )

echo
echo "Done. Attach these files to the release:"
ls -lh "$OUT_DIR"
echo
echo "Verify later with:  cd ${OUT_DIR} && sha256sum -c SHA256SUMS.txt"
echo "Load the image with: docker load -i ${IMG_ARCHIVE}"
