#!/usr/bin/env bash
# Ermittelt aus Conventional Commits seit dem letzten v-Tag die naechste
# SemVer-Version, aktualisiert version.properties + CHANGELOG.md und
# erstellt Commit und Tag.
#
#   scripts/prepare-release.sh --dry-run   nur berechnen, nichts aendern
#   scripts/prepare-release.sh --apply      Dateien, Commit und Tag erstellen
#
# Aufruf aus Repo-Root. Der Push (main + Tag) bleibt beim Caller (CI-Job),
# damit dry-run und Review sauber getrennt sind.
set -euo pipefail

MODE="dry-run"
if [[ "${1:-}" == "--apply" ]]; then
  MODE="apply"
elif [[ "${1:-}" != "--dry-run" ]]; then
  printf 'Usage: %s --dry-run|--apply\n' "$0" >&2
  exit 2
fi

VERSION_FILE="version.properties"
CHANGELOG="CHANGELOG.md"

head_subject="$(git log -1 --pretty=%s)"
if [[ "$head_subject" =~ ^chore\(release\) ]]; then
  printf 'SKIP: HEAD ist bereits ein Release-Commit (%s)\n' "$head_subject"
  exit 0
fi

last_tag="$(git describe --tags --match 'v[0-9]*' --abbrev=0 2>/dev/null || true)"
if [[ -n "$last_tag" ]]; then
  current="${last_tag#v}"
  range="$last_tag..HEAD"
else
  current="$(grep '^VERSION_NAME=' "$VERSION_FILE" | cut -d= -f2)"
  range="HEAD"
fi

log_text="$(git log "$range" --pretty='%s%n%b')"
if [[ -z "${log_text//[[:space:]]/}" ]]; then
  printf 'SKIP: keine Commits im Bereich %s\n' "$range"
  exit 0
fi

bump="none"
if grep -qiE 'breaking change' <<<"$log_text" || grep -qE '^[a-zA-Z]+(\([^)]*\))?!:' <<<"$log_text"; then
  bump="major"
elif grep -qE '^feat(\([^)]*\))?:' <<<"$log_text"; then
  bump="minor"
elif grep -qE '^fix(\([^)]*\))?:' <<<"$log_text"; then
  bump="patch"
fi

if [[ "$bump" == "none" ]]; then
  printf 'SKIP: keine release-wuerdigen Commits (feat/fix/BREAKING) in %s\n' "$range"
  exit 0
fi

IFS='.' read -r major minor patch <<<"$current"
case "$bump" in
  major) major=$((major + 1)); minor=0; patch=0 ;;
  minor) minor=$((minor + 1)); patch=0 ;;
  patch) patch=$((patch + 1)) ;;
esac
next="$major.$minor.$patch"
code="$(grep '^VERSION_CODE=' "$VERSION_FILE" | cut -d= -f2)"
next_code=$((code + 1))
today="$(date +%F)"
tag="v$next"

printf 'BUMP=%s CURRENT=%s NEXT=%s CODE=%s TAG=%s\n' "$bump" "$current" "$next" "$next_code" "$tag"

if [[ "$MODE" == "dry-run" ]]; then
  printf 'DRY-RUN: keine Dateien geaendert\n'
  exit 0
fi

python3 - "$VERSION_FILE" "$next" "$next_code" "$CHANGELOG" "$today" <<'EOF'
import re
import sys

version_file, version, code, changelog, today = sys.argv[1:6]

lines = open(version_file).read().splitlines(keepends=False)
updated = []
for line in lines:
    if line.startswith("VERSION_NAME="):
        updated.append(f"VERSION_NAME={version}")
    elif line.startswith("VERSION_CODE="):
        updated.append(f"VERSION_CODE={code}")
    else:
        updated.append(line)
open(version_file, "w").write("\n".join(updated) + "\n")

text = open(changelog).read()
marker = re.search(r"^## \[Unreleased\]\s*\n", text, re.M)
if not marker:
    sys.exit("CHANGELOG.md enthaelt keinen ## [Unreleased]-Abschnitt")
start = marker.end()
following = re.search(r"^## \[[0-9]", text[start:], re.M)
end = start + (following.start() if following else len(text[start:]))
body = text[start:end].strip("\n")
entry = f"## [{version}] – {today}\n\n"
entry += (body + "\n") if body else "Keine relevanten Aenderungen.\n"
text = text[:start] + "\n" + entry + "\n" + text[end:]
open(changelog, "w").write(text)
EOF

git add "$VERSION_FILE" "$CHANGELOG"
git commit -m "chore(release): $tag" -m "Bump $current -> $next (VERSION_CODE $next_code)."
git tag -a "$tag" -m "Release $tag"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  {
    printf 'created=true\n'
    printf 'version=%s\n' "$next"
    printf 'tag=%s\n' "$tag"
  } >>"$GITHUB_OUTPUT"
fi

printf 'RELEASE-READY: %s (jetzt: git push origin main %s)\n' "$tag" "$tag"
