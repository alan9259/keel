#!/usr/bin/env bash
#
# Controlled CloudKit schema deploys via `cktool`.
#
# The CloudKit *Development* schema is created automatically by SwiftData's
# mirroring whenever a signed build writes data (see cloudkit/README.md). This
# script turns that into a governed flow:
#
#   1) export   snapshot the Development schema into a committed file
#   2) (review the git diff, commit it)
#   3) validate  dry-run the committed file against Production
#   4) deploy    import the committed file into Production (the controlled gate)
#
# So Production only ever changes from the reviewed, version-controlled file, not
# from whatever a Debug build happened to leave in Development.
#
# Auth: cktool needs a *management* token from the CloudKit Console
# (Settings -> Tokens). Either save it once:
#     ./scripts/cloudkit-schema.sh save-token
# or export it for a single run:
#     CLOUDKIT_MANAGEMENT_TOKEN=xxxx ./scripts/cloudkit-schema.sh deploy
#
# Overridable via env: CLOUDKIT_TEAM_ID, CLOUDKIT_CONTAINER_ID, CLOUDKIT_SCHEMA_FILE.

set -euo pipefail

TEAM_ID="${CLOUDKIT_TEAM_ID:-UPY445K892}"
CONTAINER_ID="${CLOUDKIT_CONTAINER_ID:-iCloud.com.keel}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCHEMA_FILE="${CLOUDKIT_SCHEMA_FILE:-$REPO_ROOT/cloudkit/schema.ckdb}"

die() { printf 'error: %s\n' "$*" >&2; exit 1; }

command -v xcrun >/dev/null 2>&1 || die "xcrun not found (install Xcode command line tools)."
xcrun --find cktool >/dev/null 2>&1 || die "cktool not found (comes with Xcode)."

# Run cktool, injecting --token after the subcommand when CLOUDKIT_MANAGEMENT_TOKEN
# is set; otherwise rely on a token saved earlier via 'save-token' (keychain).
# Done in the wrapper (not an array at the call site) so it works on macOS's
# bash 3.2, where an empty "${array[@]}" under 'set -u' errors "unbound variable".
ck() {
  local sub="$1"; shift
  if [[ -n "${CLOUDKIT_MANAGEMENT_TOKEN:-}" && "$sub" != "save-token" ]]; then
    xcrun cktool "$sub" --token "$CLOUDKIT_MANAGEMENT_TOKEN" "$@"
  else
    xcrun cktool "$sub" "$@"
  fi
}

need_schema() {
  [[ -f "$SCHEMA_FILE" ]] || die "no schema file at $SCHEMA_FILE — run '$0 export' first (after the Development schema exists)."
}

usage() {
  cat <<EOF
CloudKit schema deploys (team=$TEAM_ID container=$CONTAINER_ID)
file: $SCHEMA_FILE

Usage: $0 <command>

  export       Export the DEVELOPMENT schema into the committed file (then review + commit).
  validate     Validate the committed file against PRODUCTION (dry-run, no changes).
  deploy       Import the committed file into PRODUCTION (validates first).
  apply-dev    Import the committed file into DEVELOPMENT (e.g. after a reset, to match the file).
  diff         Show how the live DEVELOPMENT schema differs from the committed file.
  save-token   Save a management token (prompts securely) for future runs.
  help         Show this help.
EOF
}

case "${1:-help}" in
  export)
    mkdir -p "$(dirname "$SCHEMA_FILE")"
    ck export-schema --team-id "$TEAM_ID" --container-id "$CONTAINER_ID" \
      --environment development --output-file "$SCHEMA_FILE"
    echo "Exported DEVELOPMENT schema -> $SCHEMA_FILE"
    echo "Review 'git diff -- $SCHEMA_FILE' and commit it as the schema of record."
    ;;
  validate)
    need_schema
    ck validate-schema --team-id "$TEAM_ID" --container-id "$CONTAINER_ID" \
      --environment production --file "$SCHEMA_FILE"
    echo "Committed schema validates against PRODUCTION."
    ;;
  deploy)
    need_schema
    echo "Deploying $SCHEMA_FILE -> PRODUCTION ($CONTAINER_ID) ..."
    ck import-schema --team-id "$TEAM_ID" --container-id "$CONTAINER_ID" \
      --environment production --validate --file "$SCHEMA_FILE"
    echo "Deployed to PRODUCTION."
    ;;
  apply-dev)
    need_schema
    ck import-schema --team-id "$TEAM_ID" --container-id "$CONTAINER_ID" \
      --environment development --validate --file "$SCHEMA_FILE"
    echo "Applied committed schema to DEVELOPMENT."
    ;;
  diff)
    need_schema
    tmp="$(mktemp -t keel-ck-dev)"
    trap 'rm -f "$tmp"' EXIT
    ck export-schema --team-id "$TEAM_ID" --container-id "$CONTAINER_ID" \
      --environment development --output-file "$tmp"
    if diff -u "$SCHEMA_FILE" "$tmp"; then
      echo "DEVELOPMENT matches the committed schema."
    fi
    ;;
  save-token)
    shift || true
    ck save-token --type management "$@"
    ;;
  help|-h|--help)
    usage
    ;;
  *)
    usage; exit 1 ;;
esac
