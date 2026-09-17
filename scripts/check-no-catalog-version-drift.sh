#!/usr/bin/env bash
# =============================================================================
# check-no-catalog-version-drift.sh
# =============================================================================
#
# GUARDS: a version literal hardcoded in a build script or test source that
# DUPLICATES a value already declared in gradle/libs.versions.toml.
#
# WHY THIS EXISTS (regression guard, 2026-09-17):
#   ConfigCacheCompatTest injected a consumer build with
#       id("org.jetbrains.kotlin.multiplatform") version "2.3.21"
#   hardcoded. When the catalog moved to Kotlin 2.4.20, that test kept
#   compiling against the OLD compiler while still reporting PASSED — the guard
#   silently stopped guarding what actually shipped. A duplicated literal is
#   indistinguishable from a correct one until it drifts, so the only reliable
#   fix is to forbid the duplication.
#
# A literal that matches a catalog value must instead be threaded from the
# catalog (e.g. a system property set by the test task from libs.versions.*).
#
# SCOPE: every build that can actually READ the catalog. A nested build counts as
# catalog-blind only when its settings.gradle.kts does not wire libs.versions.toml
# (the ROOT build gets it automatically from gradle/libs.versions.toml, and
# build-logic wires it explicitly — both are therefore guarded). Catalog-blind
# builds are reported as warnings, never silently skipped: they pin by hand and
# are a real drift risk. The kmp-project-template submodule is another repo.
#
# OPT-OUT: append `// catalog-drift-ok: <reason>` on the offending line.
#
# Exit 0 = clean, 1 = drift found.
# =============================================================================
set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

CATALOG="gradle/libs.versions.toml"
[ -f "$CATALOG" ] || { echo "❌ $CATALOG not found"; exit 1; }

# Standalone builds (own settings.gradle.kts) cannot reference the catalog.
# Catalog-blind = nested build whose settings.gradle.kts never mentions the catalog.
STANDALONE=()
while IFS= read -r s; do
  d=$(dirname "$s"); d="${d#./}"
  [ "$d" = "." ] && continue                        # root: catalog is automatic
  grep -q "libs.versions.toml" "$s" && continue     # explicitly wired (build-logic)
  STANDALONE+=("$d")
done < <(find . -name "settings.gradle.kts" -not -path "./build/*" -not -path "*/build/*" 2>/dev/null)

is_standalone() {
  local f="$1"
  for d in "${STANDALONE[@]:-}"; do
    case "$f" in "$d"/*) return 0 ;; esac
  done
  return 1
}

# Collect catalog versions worth guarding (x.y / x.y.z, 3+ chars — skip "24"/"36"
# style SDK ints, which legitimately appear as plain numbers everywhere).
VERSIONS=$(grep -oE '^[a-zA-Z0-9_-]+ = "[0-9]+\.[0-9]+(\.[0-9]+)?[^"]*"' "$CATALOG" \
  | sed -E 's/.*= "([^"]+)"/\1/' | sort -u)

violations=0
standalone_warnings=()
while IFS= read -r ver; do
  [ -z "$ver" ] && continue
  while IFS= read -r hit; do
    file="${hit%%:*}"; rest="${hit#*:}"; line="${rest%%:*}"; text="${rest#*:}"
    case "$file" in
      ./"$CATALOG"|"$CATALOG") continue ;;
      *"/build/"*) continue ;;
      *samples/kmp-project-template/*) continue ;;   # git submodule: another repo
    esac
    if is_standalone "${file#./}"; then
      # Standalone builds have their own settings.gradle.kts and genuinely cannot
      # read the catalog, so this is not an error — but it IS a real drift risk
      # (these literals must be bumped by hand), so surface it rather than hide it.
      standalone_warnings+=("${file#./}:$line hardcodes \"$ver\"")
      continue
    fi
    printf '%s' "$text" | grep -q 'catalog-drift-ok:' && continue
    echo "❌ $file:$line hardcodes \"$ver\" which is declared in $CATALOG"
    echo "     $(printf '%s' "$text" | sed 's/^[[:space:]]*//' | cut -c1-100)"
    violations=$((violations + 1))
  done < <(grep -rn --include="*.kt" --include="*.kts" -F "\"$ver\"" \
             build-logic */src */build.gradle.kts samples 2>/dev/null || true)
done <<< "$VERSIONS"

if [ "$violations" -gt 0 ]; then
  echo
  echo "❌ $violations hardcoded version literal(s) duplicate gradle/libs.versions.toml."
  echo "   Thread the value from the catalog instead (see ConfigCacheCompatTest for the"
  echo "   system-property pattern), or annotate with '// catalog-drift-ok: <reason>'."
  exit 1
fi
if [ "${#standalone_warnings[@]}" -gt 0 ]; then
  echo "⚠ standalone builds pin versions by hand (cannot read the catalog — bump manually):"
  printf '    %s\n' "${standalone_warnings[@]}"
fi
echo "✓ no catalog version drift"
