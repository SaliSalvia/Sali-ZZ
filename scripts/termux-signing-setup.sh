#!/usr/bin/env bash
#
# Create the Sali-ZZai release keystore and store the four signing secrets for
# this repository, from Termux (or any machine with keytool + gh).
#
# Nothing secret is ever passed on a command line or kept on disk longer than
# the run: passwords reach keytool through environment variables, the Base64
# blob reaches `gh` through stdin from a 0700 temp dir that is wiped on exit.
#
#   curl -fsSL ... | bash        # not recommended; copy this file instead
#   bash scripts/termux-signing-setup.sh
#   bash scripts/termux-signing-setup.sh --dry-run     # no gh calls at all
#
set -euo pipefail

readonly WORKFLOW_NAME="Build Sali-ZZai APK"
readonly SECRET_B64="SIGNING_KEYSTORE_BASE64"
readonly SECRET_STORE_PASS="SALI_KEYSTORE_PASSWORD"
readonly SECRET_ALIAS="SALI_KEY_ALIAS"
readonly SECRET_KEY_PASS="SALI_KEY_PASSWORD"

REPO="${SALI_REPO:-SaliSalvia/Sali-ZZ}"
KEYSTORE="${HOME}/sali-signing/sali-release.jks"
ALIAS="${SALI_KEY_ALIAS:-sali}"
DNAME=""
VALIDITY="${SALI_VALIDITY:-10000}"
ASSUME_YES=0
DRY_RUN=0
FORCE_NEW=0
TRIGGER=0

if [[ -t 1 ]]; then
  C_RESET=$'\033[0m'; C_B=$'\033[1m'; C_DIM=$'\033[2m'
  C_OK=$'\033[32m'; C_WARN=$'\033[33m'; C_ERR=$'\033[31m'; C_ACC=$'\033[36m'
else
  C_RESET=""; C_B=""; C_DIM=""; C_OK=""; C_WARN=""; C_ERR=""; C_ACC=""
fi

step()  { printf '\n%s==>%s %s%s%s\n' "$C_ACC" "$C_RESET" "$C_B" "$*" "$C_RESET"; }
info()  { printf '    %s\n' "$*"; }
ok()    { printf '    %s✔%s %s\n' "$C_OK" "$C_RESET" "$*"; }
warn()  { printf '    %s!%s %s\n' "$C_WARN" "$C_RESET" "$*" >&2; }
die()   { printf '\n%s✖ %s%s\n' "$C_ERR" "$*" "$C_RESET" >&2; exit 1; }

usage() {
  cat <<EOF
${C_B}Usage:${C_RESET} bash scripts/termux-signing-setup.sh [options]

  --repo OWNER/NAME   Repository to store secrets in (default: ${REPO})
  --keystore PATH     Where the keystore lives   (default: ~/sali-signing/sali-release.jks)
  --alias NAME        Keystore alias             (default: ${ALIAS})
  --dname "DN"        Skip the DN prompts, e.g. "CN=Sali ZZ, O=Sali-ZZ, C=IR"
  --validity DAYS     Certificate validity       (default: ${VALIDITY})
  --new               Create a new keystore even if one already exists
  --trigger           Run the release workflow when the secrets are stored
  --dry-run           Do everything except talking to GitHub
  -y, --yes           Do not ask for confirmations
  -h, --help          This help

Environment:
  SALI_REPO, SALI_KEY_ALIAS, SALI_VALIDITY, SALI_DN
  SALI_KS_PASSWORD    Keystore password (skips the hidden prompt; for CI only)
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo)     REPO="${2:?--repo needs a value}"; shift 2 ;;
    --keystore) KEYSTORE="${2:?--keystore needs a value}"; shift 2 ;;
    --alias)    ALIAS="${2:?--alias needs a value}"; shift 2 ;;
    --dname)    DNAME="${2:?--dname needs a value}"; shift 2 ;;
    --validity) VALIDITY="${2:?--validity needs a value}"; shift 2 ;;
    --new)      FORCE_NEW=1; shift ;;
    --trigger)  TRIGGER=1; shift ;;
    --dry-run)  DRY_RUN=1; shift ;;
    -y|--yes)   ASSUME_YES=1; shift ;;
    -h|--help)  usage; exit 0 ;;
    *)          usage; die "unknown option: $1" ;;
  esac
done

[[ -n "${SALI_DN:-}" && -z "$DNAME" ]] && DNAME="$SALI_DN"

# ---------------------------------------------------------------- utilities

TMP_DIR=""
cleanup() {
  local rc=$?
  unset KS_PASS KS_ALIAS 2>/dev/null || true
  [[ -n "$TMP_DIR" && -d "$TMP_DIR" ]] && rm -rf "$TMP_DIR"
  return $rc
}
trap cleanup EXIT INT TERM

confirm() { # confirm "question" [default-yes]
  local answer
  if (( ASSUME_YES )); then return 0; fi
  if [[ "${2:-no}" == "yes" ]]; then
    printf '    %s [Y/n] ' "$1"; read -r answer || answer=""
    [[ -z "$answer" || "$answer" =~ ^[Yy] ]]
  else
    printf '    %s [y/N] ' "$1"; read -r answer || answer=""
    [[ "$answer" =~ ^[Yy] ]]
  fi
}

ask() { # ask "prompt" "default" -> echoes the answer
  local prompt="$1" default="${2:-}" answer=""
  if [[ -n "$default" ]]; then
    printf '    %s [%s]: ' "$prompt" "$default" >&2
  else
    printf '    %s: ' "$prompt" >&2
  fi
  read -r answer || answer=""
  printf '%s' "${answer:-$default}"
}

ask_secret() { # ask_secret "prompt" -> echoes the hidden answer
  local prompt="$1" answer=""
  printf '    %s' "$prompt" >&2
  IFS= read -rs answer || { printf '\n' >&2; die "aborted at password prompt"; }
  printf '\n' >&2
  printf '%s' "$answer"
}

b64_encode() { # b64_encode file -> single line, no wrapping
  if base64 -w0 </dev/null >/dev/null 2>&1; then
    base64 -w0 "$1"
  elif command -v openssl >/dev/null 2>&1; then
    openssl base64 -A -in "$1"
  else
    die "neither 'base64 -w0' nor openssl is available; run: pkg install coreutils openssl-tool"
  fi
}

# ------------------------------------------------------------------- steps

require_tools() {
  step "Checking tools"
  command -v keytool >/dev/null 2>&1 \
    || die "keytool not found — install a JDK:  pkg install openjdk-17"
  ok "keytool: $(keytool 2>&1 | head -n1)"
  command -v bash >/dev/null 2>&1 && ok "bash ${BASH_VERSION}"

  if (( ! DRY_RUN )); then
    command -v gh >/dev/null 2>&1 || die "gh not found — install it:  pkg install gh"
    gh auth status >/dev/null 2>&1 \
      || die "gh is not signed in — run:  gh auth login"
    ok "gh: authenticated ($(gh api user --jq .login 2>/dev/null || echo unknown))"
    gh repo view "$REPO" --json nameWithOwner --jq .nameWithOwner >/dev/null 2>&1 \
      || die "cannot access $REPO with this gh login (needs admin to set secrets)"
    ok "repository: $REPO"
  else
    warn "dry run: GitHub is not contacted"
  fi

  TMP_DIR="$(mktemp -d 2>/dev/null || mktemp -d -t sali-signing)"
  chmod 700 "$TMP_DIR"
}

resolve_repo_from_git() {
  # Prefer the clone this script sits in, so forks do not push to the wrong repo.
  local remote url
  remote="$(git -C "${SCRIPT_DIR:-.}" remote get-url origin 2>/dev/null || true)"
  [[ -z "$remote" ]] && return 0
  url="${remote%.git}"
  url="${url#*github.com[:/]}"
  [[ "$url" == */* ]] && REPO="$url"
}

collect_password() {
  step "Keystore password"
  if [[ -n "${SALI_KS_PASSWORD:-}" ]]; then
    KS_PASS="$SALI_KS_PASSWORD"
    ok "password taken from SALI_KS_PASSWORD"
    return 0
  fi
  local first second
  while :; do
    info "Use the same password for the store and the key (PKCS12 requirement)."
    first="$(ask_secret 'Keystore password (hidden, min 6 chars): ')"
    [[ ${#first} -ge 6 ]] || { warn "too short (${#first} chars)"; continue; }
    second="$(ask_secret 'Repeat the password: ')"
    [[ "$first" == "$second" ]] || { warn "passwords do not match"; continue; }
    KS_PASS="$first"
    break
  done
  ok "password accepted (${#KS_PASS} chars)"
}

create_keystore() {
  local dir; dir="$(dirname "$KEYSTORE")"
  mkdir -p "$dir"
  chmod 700 "$dir"

  local cn ou org city state country
  if [[ -z "$DNAME" ]]; then
    step "Certificate details"
    info "These end up in the APK certificate; defaults are fine for personal releases."
    cn="$(ask 'Common Name (CN)' 'Sali ZZ')"
    ou="$(ask 'Organizational Unit (OU)' 'Android')"
    org="$(ask 'Organization (O)' 'Sali-ZZ')"
    city="$(ask 'City (L)' 'Tehran')"
    state="$(ask 'State (ST)' 'Tehran')"
    country="$(ask 'Country code (C)' 'IR')"
    DNAME="CN=${cn}, OU=${ou}, O=${org}, L=${city}, ST=${state}, C=${country}"
  fi

  step "Creating the keystore"
  info "file:     $KEYSTORE"
  info "alias:    $ALIAS"
  info "name:     $DNAME"
  info "validity: $VALIDITY days"

  export KS_PASS
  if [[ -e "$KEYSTORE" ]]; then
    local backup="${KEYSTORE}.bak.$(date +%Y%m%d-%H%M%S)"
    warn "an existing keystore will be kept as: $backup"
    mv "$KEYSTORE" "$backup"
  fi

  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storetype PKCS12 \
    -alias "$ALIAS" \
    -keyalg RSA -keysize 2048 -validity "$VALIDITY" \
    -storepass:env KS_PASS \
    -keypass:env KS_PASS \
    -dname "$DNAME" >/dev/null 2>&1 \
    || die "keytool failed to create the keystore"

  chmod 600 "$KEYSTORE"
  ok "keystore created ($(wc -c <"$KEYSTORE" | tr -d ' ') bytes)"
}

verify_keystore() {
  step "Verifying the keystore"
  export KS_PASS
  local out
  out="$(keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" -storepass:env KS_PASS 2>/dev/null)" \
    || die "the alias '$ALIAS' is missing or the password is wrong"
  ok "alias '$ALIAS' is present"
  printf '%s\n' "$out" | sed -n 's/^[[:space:]]*\(Valid from:.*\)/    \1/p'
  FINGERPRINT="$(printf '%s\n' "$out" | sed -n 's/^[[:space:]]*SHA256: \(.*\)/\1/p' | head -n1)"
  [[ -n "$FINGERPRINT" ]] && ok "certificate SHA-256: $FINGERPRINT"
}

backup_keystore() {
  step "Backing up the keystore"
  local shared="${HOME}/storage/shared"
  if [[ -d "$shared" ]]; then
    local dest="${shared}/Sali-Signing"
    mkdir -p "$dest" && cp -f "$KEYSTORE" "$dest/" && ok "copied to $dest/"
  else
    warn "shared storage not set up; run 'termux-setup-storage' and copy the file yourself"
  fi
  warn "keep this file safe: losing it means users must uninstall to update the app"
}

prepare_secret_value() {
  step "Encoding the keystore as Base64"
  local b64="$TMP_DIR/keystore.b64"
  b64_encode "$KEYSTORE" > "$b64"
  chmod 600 "$b64"

  local lines; lines="$(wc -l <"$b64" | tr -d ' ')"
  (( lines <= 1 )) || die "Base64 output is wrapped over $lines lines; the secret must be a single line"

  base64 -d "$b64" > "$TMP_DIR/roundtrip.jks" 2>/dev/null \
    || openssl base64 -d -A -in "$b64" -out "$TMP_DIR/roundtrip.jks" 2>/dev/null \
    || die "could not decode the Base64 back for verification"
  cmp -s "$KEYSTORE" "$TMP_DIR/roundtrip.jks" \
    || die "Base64 round-trip does not match the keystore; refusing to upload it"

  B64_BYTES="$(wc -c <"$b64" | tr -d ' ')"
  ok "single line, ${B64_BYTES} bytes, decode round-trip identical"
}

list_secrets() {
  gh secret list --repo "$REPO" 2>/dev/null | awk 'NF{print $1}'
}

store_secrets() {
  step "Storing the four secrets in $REPO"
  local existing name
  existing="$(list_secrets || true)"

  local clash=0
  for name in "$SECRET_B64" "$SECRET_STORE_PASS" "$SECRET_ALIAS" "$SECRET_KEY_PASS"; do
    if grep -qx "$name" <<<"$existing"; then clash=1; fi
  done
  if (( clash )); then
    warn "some of these secrets already exist and will be overwritten"
    confirm "Overwrite them?" || die "nothing was changed"
  fi

  gh secret set "$SECRET_B64" --repo "$REPO" < "$TMP_DIR/keystore.b64" \
    && ok "$SECRET_B64"
  printf '%s' "$KS_PASS" | gh secret set "$SECRET_STORE_PASS" --repo "$REPO" \
    && ok "$SECRET_STORE_PASS"
  printf '%s' "$ALIAS" | gh secret set "$SECRET_ALIAS" --repo "$REPO" \
    && ok "$SECRET_ALIAS"
  printf '%s' "$KS_PASS" | gh secret set "$SECRET_KEY_PASS" --repo "$REPO" \
    && ok "$SECRET_KEY_PASS (same value as $SECRET_STORE_PASS)"
}

verify_secrets() {
  step "Checking that all four secrets are readable by the workflow"
  local existing missing=""
  existing="$(list_secrets || true)"
  local name
  for name in "$SECRET_B64" "$SECRET_STORE_PASS" "$SECRET_ALIAS" "$SECRET_KEY_PASS"; do
    grep -qx "$name" <<<"$existing" || missing+=" $name"
  done
  [[ -z "$missing" ]] || die "still missing:$missing"
  ok "$SECRET_B64"
  ok "$SECRET_STORE_PASS"
  ok "$SECRET_ALIAS"
  ok "$SECRET_KEY_PASS"
}

trigger_release() {
  step "Starting the release workflow"
  gh workflow run "$WORKFLOW_NAME" --repo "$REPO" --ref master \
    || die "could not start '$WORKFLOW_NAME' (is it on master?)"
  ok "run queued"
  cat <<EOF

    Watch it and download the APK when it finishes:

      gh run list --repo $REPO --limit 3
      gh run watch --repo $REPO
      gh release download v2.2.1 --repo $REPO -p '*.apk' --clobber -D ~/sali-signing/out
      apksigner verify --print-certs ~/sali-signing/out/Sali-ZZai-release.apk

EOF
}

summary() {
  cat <<EOF

${C_B}Done.${C_RESET}
  keystore : $KEYSTORE
  alias    : $ALIAS
  sha256   : ${FINGERPRINT:-unknown}
  repo     : $REPO
  secrets  : $SECRET_B64, $SECRET_STORE_PASS, $SECRET_ALIAS, $SECRET_KEY_PASS

  The released APK's certificate must show this same SHA-256 fingerprint.
  Never commit the keystore; .gitignore already excludes *.jks / *.keystore.
EOF
}

# -------------------------------------------------------------------- main

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
resolve_repo_from_git

printf '%s\n' "${C_B}Sali-ZZai release signing setup${C_RESET}"
info "repository: $REPO"
info "keystore:   $KEYSTORE"

require_tools
collect_password

if [[ -e "$KEYSTORE" && "$FORCE_NEW" -eq 0 ]]; then
  step "Existing keystore found"
  info "$KEYSTORE"
  if confirm "Reuse it instead of creating a new one?" yes; then
    verify_keystore
    prepare_secret_value
    if (( DRY_RUN )); then
      warn "dry run: skipping the GitHub secret update"
    else
      store_secrets
      verify_secrets
    fi
    backup_keystore
    summary
    if (( ! DRY_RUN && TRIGGER )); then trigger_release; fi
    exit 0
  fi
fi

create_keystore
verify_keystore
prepare_secret_value
backup_keystore

if (( DRY_RUN )); then
  warn "dry run: skipping the GitHub secret update for $REPO"
  summary
  exit 0
fi

store_secrets
verify_secrets
summary
if (( TRIGGER )); then trigger_release; fi
