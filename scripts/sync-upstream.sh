#!/bin/sh
# Merge a new VeraCrypt upstream release without dropping VC Port overlay files.
# The application itself stays offline. This script is for developers who
# explicitly choose to update the source tree.
#
# Usage:
#   scripts/sync-upstream.sh
#   scripts/sync-upstream.sh --check   # report whether upstream moved (needs network)

set -eu

ROOT=$(CDPATH= git rev-parse --show-toplevel)
cd "$ROOT"

REMOTE=${UPSTREAM_REMOTE:-upstream}
BRANCH=${UPSTREAM_BRANCH:-master}
OVERLAY="$ROOT/ports/OVERLAY.files"
PIN="$ROOT/ports/UPSTREAM_COMMIT"
VERSION_JSON="$ROOT/ports/version.json"

if ! git remote get-url "$REMOTE" >/dev/null 2>&1; then
	git remote add "$REMOTE" https://github.com/veracrypt/VeraCrypt.git
fi

CHECK_ONLY=0
if [ "${1:-}" = "--check" ]; then
	CHECK_ONLY=1
fi

echo "Fetching $REMOTE/$BRANCH (temporary network)..."
git fetch --quiet "$REMOTE" "$BRANCH"
echo "Fetch finished. No connection is kept open."

NEW=$(git rev-parse "$REMOTE/$BRANCH")
OLD=$(cat "$PIN" 2>/dev/null || git merge-base HEAD "$REMOTE/$BRANCH")

if [ "$NEW" = "$OLD" ]; then
	echo "Already in sync with upstream $NEW"
	exit 0
fi

echo "Upstream moved:"
echo "  from $OLD"
echo "  to   $NEW"
git log --oneline "$OLD".."$NEW" | head -20

if [ "$CHECK_ONLY" = 1 ]; then
	exit 2
fi

STASH_REF=""
if [ -n "$(git status --porcelain)" ]; then
	echo "Working tree is dirty. Stash, then re-run."
	exit 1
fi

BACKUP=$(mktemp -d)
trap 'rm -rf "$BACKUP"' EXIT

# Preserve overlay files so a merge cannot silently drop port work.
while IFS= read -r f; do
	[ -z "$f" ] && continue
	[ "${f#\#}" != "$f" ] && continue
	if [ -e "$f" ]; then
		mkdir -p "$BACKUP/$(dirname "$f")"
		cp "$f" "$BACKUP/$f"
	fi
done < "$OVERLAY"

if [ -d ports ]; then
	cp -R ports "$BACKUP/ports"
fi

echo "Merging $REMOTE/$BRANCH..."
if ! git merge --no-edit "$REMOTE/$BRANCH"; then
	echo "Merge reported conflicts. Restoring overlay files, then you can git add."
fi

while IFS= read -r f; do
	[ -z "$f" ] && continue
	[ "${f#\#}" != "$f" ] && continue
	if [ -f "$BACKUP/$f" ]; then
		mkdir -p "$(dirname "$f")"
		cp "$BACKUP/$f" "$f"
		git add "$f" 2>/dev/null || true
	fi
done < "$OVERLAY"

if [ -d "$BACKUP/ports" ]; then
	rm -rf ports
	cp -R "$BACKUP/ports" ports
	git add ports 2>/dev/null || true
fi

# Refresh the pin and version metadata.
printf '%s\n' "$NEW" > "$PIN"
python3 - "$VERSION_JSON" "$NEW" <<'PY' || true
import json, sys, datetime
path, commit = sys.argv[1], sys.argv[2]
try:
    data = json.load(open(path))
except Exception:
    data = {}
data["upstream_commit"] = commit
open(path, "w").write(json.dumps(data, indent=2) + "\n")
PY
git add "$PIN" "$VERSION_JSON" 2>/dev/null || true

if git diff --name-only --diff-filter=U | grep -q .; then
	echo "Remaining conflicts:"
	git diff --name-only --diff-filter=U
	echo "Overlay files were restored. Resolve any remaining conflicts, then commit."
	exit 1
fi

echo "Upstream merge complete. Overlay preserved."
echo "Review the diff, then commit. The app remains offline until a user checks for updates."
