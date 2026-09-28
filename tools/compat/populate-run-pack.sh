#!/usr/bin/env bash
# Mirrors the whole DF pack into run-pack/ (repo root) with the freshly built Slate jars, then (with --launch) starts
# it the way CloudLauncher does: production NeoForge 21.1.250 from CloudLauncher's runtime, through the CmlLib boot
# harness. The full pack cannot run as a dev client: in an IDE run Minecraft validates the command tree at bootstrap,
# before mods are loaded, and Climate Rivers' worldgen mixin crashes there (PuzzlesLib: "mod list is null").
# The copy lives at the repo root, not under neoforge/dev: MCEF treats a "../build" folder beside the game directory
# as a dev environment and unpacks its natives there instead of in mods/mcef-libraries.
#   - every ENABLED jar in the pack's mods/ and its folders (MCEF's unpacked natives), its config/, options.txt and
#     shader packs (copies; the pack is only read)
#   - the pack's own slate-*.jar are replaced by neoforge/*/build/libs (run `./gradlew.bat assemble` in neoforge first)
#   - the pack's BetterInventory is swapped for the local build (Slate Building's toolbox slot needs 1.1.5)
#   - Accurate Block Placement is left out: Slate Building has it built in (and made to live with Bridging Mod's
#     reach-around), but steps aside whenever the mod is installed
#   - Drippy Loading Screen is left out, both its early-window jar and the mod: Slate draws the start-up window and
#     its continuation in game (slate-earlywindow), and Drippy's mod would swap the second half for its own overlay
#     (vanilla's red Mojang screen by default). Drippy's early window cannot hand over outside CloudLauncher's own
#     launch anyway (CustomLoadingOverlay ClassNotFound, as tools/boot-df.ps1 found). FancyMenu only lists it as optional.
# Worlds are not copied.
#   bash tools/compat/populate-run-pack.sh [--launch]
set -euo pipefail
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
PACK="${DF_PACK:-C:/Users/leonr/AppData/Roaming/CloudLauncher/default/packs/df/game}"
BI_JAR="${BI_JAR:-C:/Users/leonr/Coding/MinecraftMods/Source/QOL/BetterInventory/build/libs/betterinventory-1.1.5.jar}"
BOOT="${BOOT_EXE:-C:/Users/leonr/AppData/Local/Temp/claude/C--Users-leonr-Coding/e94ada8d-ad03-48ed-9d40-617065b642fa/scratchpad/boot/bin/Release/net10.0/boot.exe}"
RUN="$REPO/run-pack"

[ -d "$PACK/mods" ] || { echo "pack not found: $PACK" >&2; exit 1; }
[ -f "$BI_JAR" ] || { echo "BetterInventory build not found: $BI_JAR" >&2; exit 1; }
mkdir -p "$RUN/mods" "$RUN/config"
rm -f "$RUN/mods/"*.jar

n=0
for f in "$PACK/mods/"*.jar; do
  case "$(basename "$f")" in
    slate-*.jar | betterinventory-*.jar | drippyloadingscreen*.jar | *[Aa]ccurate[Bb]lock[Pp]lacement*.jar) continue ;;
  esac
  cp "$f" "$RUN/mods/"
  n=$((n + 1))
done
cp "$BI_JAR" "$RUN/mods/"
echo "copied $n pack jars + $(basename "$BI_JAR") (local build)"
# Mod data kept in mods/ (MCEF's natives: without them its first start downloads 80 MB and can crash on the race)
for d in "$PACK/mods/"*/; do
  [ -d "$d" ] && cp -r "$d" "$RUN/mods/" && echo "copied mods/$(basename "$d")/"
done
for m in core menu multiplayer chat config building earlywindow; do
  jar="$(ls -1t "$REPO/neoforge/$m/build/libs/"slate-$m-neoforge-1.21.1-*.jar 2>/dev/null | grep -v -- -sources | head -1 || true)"
  [ -n "$jar" ] || { echo "no built jar for $m: run ./gradlew.bat assemble in neoforge" >&2; exit 1; }
  cp "$jar" "$RUN/mods/"
  echo "slate: $(basename "$jar")"
done

# Configs and options as the pack has them (Slate's own included, so its menus look as they will in the pack).
cp -r "$PACK/config/." "$RUN/config/"
[ -f "$PACK/options.txt" ] && cp "$PACK/options.txt" "$RUN/options.txt"
[ -f "$RUN/config/fml.toml" ] && sed -i 's/^earlyWindowProvider = .*/earlyWindowProvider = "fmlearlywindow"/' "$RUN/config/fml.toml"
if [ -d "$PACK/shaderpacks" ]; then
  mkdir -p "$RUN/shaderpacks"
  cp -r "$PACK/shaderpacks/." "$RUN/shaderpacks/"
fi
echo "config/, options.txt and shader packs mirrored -> $RUN"

if [ "${1:-}" = "--launch" ]; then
  [ -f "$BOOT" ] || { echo "boot harness not found: $BOOT (set BOOT_EXE)" >&2; exit 1; }
  # 12 h: the harness kills the game at its timeout; closing the window ends the run sooner.
  exec "$BOOT" "$(cygpath -m "$RUN")" neoforge-21.1.250 43200 8192
fi
