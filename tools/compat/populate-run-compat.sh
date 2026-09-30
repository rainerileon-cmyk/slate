#!/usr/bin/env bash
# Fills neoforge/dev/run-compat/mods/ with COPIES of the DF pack's production jars that matter for Slate Building
# (the pack itself is only read), plus the locally built BetterInventory. Re-run after the pack or BI changes.
#   bash tools/compat/populate-run-compat.sh [--shaders] [--seed-bi-migration]
#     --shaders             also copies a LOCAL shader pack (never downloads) and enables it in Iris
#     --seed-bi-migration   puts BetterInventory's carousel back on Left Alt (options.txt) with offhandKeyMigrated=false,
#                           so the next run shows its one-time move to Right Alt (compat-env checks the result)
# Then: bash <scratchpad>/grun.sh "$PWD/neoforge" :dev:runClientCompat -PbuildingHarness=compat
set -euo pipefail
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
PACK="${DF_PACK:-C:/Users/leonr/AppData/Roaming/CloudLauncher/default/packs/df/game}"
BI_JAR="${BI_JAR:-C:/Users/leonr/Coding/MinecraftMods/Source/QOL/BetterInventory/build/libs/betterinventory-1.1.5.jar}"
RUN="$REPO/neoforge/dev/run-compat"

SHADERS=0
SEED=0
for a in "$@"; do
  case "$a" in
    --shaders) SHADERS=1 ;;
    --seed-bi-migration) SEED=1 ;;
    *) echo "unknown option $a" >&2; exit 2 ;;
  esac
done

mkdir -p "$RUN/mods" "$RUN/config"
rm -f "$RUN/mods/"*.jar

# name globs in the pack's mods/ (each must match exactly one jar); dependencies resolved from each neoforge.mods.toml
JARS=(
  'sodium-neoforge-0.8.12+mc1.21.1.jar'              # rendering (nests fabric renderer api)
  'sodium-extra-neoforge-*.jar'                       # -> sodium
  'iris-neoforge-*.jar'                               # shaders
  'sable-neoforge-1.21.1-*.jar'                       # sub-levels with physics (nests its companion, Veil and the physics lib)
  'irisveil-*.jar'                                    # -> iris; bridges it to the Veil that Sable brings
  'create-1.21.1-6.0.10.jar'                          # nests flywheel, ponder, registrate
  'DiagonalFences-*.jar' 'DiagonalWalls-*.jar' 'DiagonalWindows-*.jar'   # nest diagonalblocks
  'PuzzlesLib-*.jar'                                  # <- Diagonal*
  'kleeslabs-neoforge-*.jar' 'balm-neoforge-*.jar'    # kleeslabs -> balm
  'jei-1.21.1-neoforge-*.jar'
  'curios-neoforge-*.jar'
  'open-parties-and-claims-neoforge-*.jar'            # no required deps
  'ShoulderSurfing-NeoForge-*.jar'
  'relics-1.21.1-*.jar' 'OctoLib-NEOFORGE-*.jar' 'architectury-*-neoforge.jar'   # relics -> curios, octolib -> architectury
)
for g in "${JARS[@]}"; do
  found=( $PACK/mods/$g )
  [ -f "${found[0]}" ] || { echo "missing in pack: $g" >&2; exit 1; }
  cp "${found[0]}" "$RUN/mods/"
  echo "copied $(basename "${found[0]}")"
done
cp "$BI_JAR" "$RUN/mods/"
echo "copied $(basename "$BI_JAR") (local build, not the pack's 1.0.1)"

# the pack's settings for the mods whose behaviour we check (copies; never written back)
for c in kleeslabs-common.toml shouldersurfing-client.toml; do
  [ -f "$PACK/config/$c" ] && cp "$PACK/config/$c" "$RUN/config/" && echo "config $c"
done

# A fresh run dir: skip the accessibility onboarding (options.txt needs its data version, or the game discards it),
# and no mod-loading warning screen (it would wait for a click before the harness world loads).
if [ ! -f "$RUN/options.txt" ]; then
  printf '%s\n' 'version:3955' 'onboardAccessibility:false' 'skipMultiplayerWarning:true' 'joinedFirstServer:true' \
    'tutorialStep:none' 'pauseOnLostFocus:false' > "$RUN/options.txt"
fi
[ -f "$RUN/config/neoforge-client.toml" ] || printf '%s\n' 'showLoadWarnings = false' > "$RUN/config/neoforge-client.toml"

if [ "$SEED" = 1 ]; then
  grep -v '^key_key.betterinventory.offhand_selector:' "$RUN/options.txt" > "$RUN/options.txt.tmp" || true
  echo 'key_key.betterinventory.offhand_selector:key.keyboard.left.alt' >> "$RUN/options.txt.tmp"
  mv "$RUN/options.txt.tmp" "$RUN/options.txt"
  if [ -f "$RUN/config/betterinventory-client.toml" ]; then
    sed -i 's/offhandKeyMigrated = true/offhandKeyMigrated = false/' "$RUN/config/betterinventory-client.toml"
  fi
  echo "BetterInventory carousel seeded on Left Alt (migration pending)"
fi

if [ "$SHADERS" = 1 ]; then
  SP="$(ls -1 "C:/Users/leonr/AppData/Roaming/CloudLauncher/default/packs/"*/game/shaderpacks/MakeUp-UltraFast-*.zip 2>/dev/null | head -1)"
  [ -n "$SP" ] || { echo "no local shader pack found" >&2; exit 1; }
  mkdir -p "$RUN/shaderpacks"
  cp "$SP" "$RUN/shaderpacks/"
  printf 'enableShaders=true\nshaderPack=%s\n' "$(basename "$SP")" > "$RUN/config/iris.properties"
  echo "shader pack $(basename "$SP") enabled"
elif [ -f "$RUN/config/iris.properties" ]; then
  printf 'enableShaders=false\n' > "$RUN/config/iris.properties"
fi
ls -1 "$RUN/mods"
