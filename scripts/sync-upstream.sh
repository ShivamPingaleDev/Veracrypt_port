#!/bin/sh
# Merge a new VeraCrypt release without throwing away VC Port work.
#
# Official source (hardcoded in ports/version.json):
#   https://github.com/veracrypt/VeraCrypt.git
# Published releases:
#   https://api.github.com/repos/veracrypt/VeraCrypt/releases/latest
#
# The Android/iOS apps never run this script. They can only *report* that a
# newer official tag exists. A maintainer merges, then rebuilds VC Port.
#
# Owned files (ports/overlay/owned.txt) are restored after the merge.
# Patched upstream files (ports/overlay/patched.txt) are left to git's
# 3-way merge. Restoring those from a pre-merge copy would drop VeraCrypt
# changes in the same files (FUSE, translations, CoreService, …).
#
#   scripts/sync-upstream.sh --check     # fetch; exit 2 if upstream moved
#   scripts/sync-upstream.sh             # merge + restore owned files
#   scripts/sync-upstream.sh --offline   # merge a commit you already fetched
#
# After a successful merge: review, run scripts/refresh-overlay.sh, commit.

set -eu

ROOT=$(CDPATH= git rev-parse --show-toplevel)
cd "$ROOT"

REMOTE=${UPSTREAM_REMOTE:-upstream}
BRANCH=${UPSTREAM_BRANCH:-master}
PIN_FILE=ports/UPSTREAM_COMMIT
OWNED=ports/overlay/owned.txt
VERSION_JSON=ports/version.json
PORT_VERSION_H=src/Main/PortVersion.h

read_list() {
	grep -v '^#' "$1" | grep -v '^$' || true
}

if ! git remote get-url "$REMOTE" >/dev/null 2>&1; then
	git remote add "$REMOTE" https://github.com/veracrypt/VeraCrypt.git
fi

CHECK_ONLY=0
OFFLINE=0
for arg in "$@"; do
	case "$arg" in
		--check) CHECK_ONLY=1 ;;
		--offline) OFFLINE=1 ;;
		-h|--help)
			sed -n '2,14p' "$0"
			exit 0
			;;
	esac
done

if [ "$OFFLINE" != 1 ]; then
	echo "Fetching $REMOTE/$BRANCH (temporary network)..."
	git fetch --quiet "$REMOTE" "$BRANCH"
	echo "Fetch finished. No connection is kept open."
	NEW=$(git rev-parse "$REMOTE/$BRANCH")
else
	NEW=$(git rev-parse "$REMOTE/$BRANCH")
fi

OLD=$(tr -d '[:space:]' < "$PIN_FILE" 2>/dev/null || git merge-base HEAD "$REMOTE/$BRANCH")

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

if [ -n "$(git status --porcelain)" ]; then
	echo "Working tree is dirty. Commit or stash, then re-run."
	exit 1
fi

if [ ! -f "$OWNED" ]; then
	echo "Missing $OWNED. Run scripts/refresh-overlay.sh first."
	exit 1
fi

BACKUP=$(mktemp -d)
trap 'rm -rf "$BACKUP"' EXIT

while IFS= read -r f; do
	[ -z "$f" ] && continue
	if [ -e "$f" ]; then
		mkdir -p "$BACKUP/$(dirname "$f")"
		cp "$f" "$BACKUP/$f"
	fi
done <<EOF
$(read_list "$OWNED")
EOF

echo "Merging $REMOTE/$BRANCH (3-way; patched src files are not overwritten)..."
merge_rc=0
git merge --no-edit "$REMOTE/$BRANCH" || merge_rc=$?

while IFS= read -r f; do
	[ -z "$f" ] && continue
	if [ -f "$BACKUP/$f" ]; then
		mkdir -p "$(dirname "$f")"
		cp "$BACKUP/$f" "$f"
		git add "$f" 2>/dev/null || true
	fi
done <<EOF
$(read_list "$OWNED")
EOF

# ports/ is not in upstream VeraCrypt. Keep it unless git deleted it.
if [ -d "$BACKUP/ports" ]; then
	:
fi

printf '%s\n' "$NEW" > "$PIN_FILE"
git add "$PIN_FILE" 2>/dev/null || true

if [ -f "$VERSION_JSON" ]; then
	python3 - "$VERSION_JSON" "$NEW" <<'PY' || true
import json, sys
path, commit = sys.argv[1], sys.argv[2]
try:
    data = json.load(open(path))
except Exception:
    data = {}
data["upstream_commit"] = commit
open(path, "w").write(json.dumps(data, indent=2) + "\n")
PY
	git add "$VERSION_JSON" 2>/dev/null || true
fi

if [ -f "$PORT_VERSION_H" ]; then
	python3 - "$PORT_VERSION_H" "$NEW" <<'PY' || true
import re, sys
path, commit = sys.argv[1], sys.argv[2]
text = open(path).read()
text, n = re.subn(
    r'(#define VC_PORT_UPSTREAM_COMMIT\t")[^"]+(")',
    r'\g<1>' + commit + r'\2',
    text,
    count=1,
)
if n:
    open(path, "w").write(text)
PY
	git add "$PORT_VERSION_H" 2>/dev/null || true
fi

conflicts=$(git diff --name-only --diff-filter=U || true)
if [ -n "$conflicts" ]; then
	echo "Remaining conflicts (patched upstream files — resolve by hand, do not copy old versions over new VeraCrypt code):"
	echo "$conflicts"
	echo "Hint: ports/overlay/src-port.patch is the last snapshot of our hunks."
	exit 1
fi

if [ "$merge_rc" != 0 ]; then
	echo "Merge failed even after restoring owned files."
	exit "$merge_rc"
fi

echo "Upstream merge complete. Owned overlay restored; patched files used git's 3-way merge."
echo "Next: scripts/refresh-overlay.sh && scripts/check-upstream-layout.sh"
echo "Then review git diff and commit."
