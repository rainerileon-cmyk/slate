# Overnight run — Slate Building + Slate Config tabs (2026-09-24)

**Slate Building is built, merged into `master`, and green on NeoForge and Fabric.** It is the new sixth Slate module
(`slate_building`, ~33k lines). The Slate Config rework is in too. Every in-game test scenario passes on both loaders,
including a run next to the DF pack's real mods (112/112). Nothing was installed into the DF pack.

Your uncommitted work on `master` (Continue card, confirm quit, skip onboarding, SlateDraw `truncate`) is untouched.
It is still uncommitted, and it builds together with the new code.

## Try it
- Dev client: `cd neoforge && ./gradlew.bat :dev:runClient`. In creative everything is unlocked.
- **Hold Left Alt** with any block → swap wheel. Move the mouse to a slice, release to pick. LMB/RMB switch
  wheels, scroll steps, 1–9 pick directly, and the centre is the full block.
- **R** → build menu. Left: shape wheel and chisel wheel (search, ◀ ▶). Right: the 20 building modes, undo and redo.
- In a mode: right-click corner A, right-click corner B, the ghosts show the result, right-click again to apply,
  left-click to cancel. Ctrl+scroll pushes the face you look at; Shift+scroll changes thickness, count or radius.
- Settings: Options now opens the Slate Config hub directly. Building settings are under **Gameplay → Building**,
  with a wheel editor.
- Survival: craft a **Builder's Toolbox** (3 copper + chest) and tools in copper → iron → diamond → netherite.
  Each tool unlocks modes, and upgrades raise the limits. The recipes are in JEI.
- Full guide: `docs/building.md`.

## Jars (not installed anywhere)
- `neoforge/<module>/build/libs/slate-<module>-neoforge-1.21.1-1.0.0.jar`. The Fabric ones are in `fabric/…`.
- `tools/install-df.ps1` now includes `building`. The DF pack currently has **no** Slate jars at all: the install
  from the 23rd is not on disk, so I didn't reinstall behind your back.
- BetterInventory **1.1.5**: `QOL/BetterInventory/build/libs/betterinventory-1.1.5.jar`. It adds the toolbox slot, and
  its offhand carousel moves to Right Alt when Slate Building is present. It is **uncommitted** in your BI repo, next to your
  own uncommitted 1.1.x work. It adds a slot, so the server and clients must update together. DF still runs BI 1.0.1.

## Decisions I made for you (easy to change)
- **Economy:** every shape is worth exactly one original block (a double slab is worth 2). Breaking a shape drops the
  original block. Vanilla recipes that made shapes cheaper are rebalanced to 1:1 (3 planks → 3 slabs), otherwise
  slabs would duplicate planks. This is the `rebalanceRecipes` server option.
- **"Remove other variants"** (off by default): hides vanilla and modded stairs, slabs etc. from creative and JEI, and
  removes the recipes that make them. The wheel still hands out the real `oak_stairs` item, so every recipe that uses
  them keeps working, and blocks already in the world keep working.
- **Alt and R:** the DF pack has six mods on Left Alt and two on R (Iris reload, Iron's spell wheel). Slate Building
  takes Alt only while you hold a block, or a toolbox or building tool while looking at a block. It takes R only
  while holding a block, toolbox or tool, or while a mode is active. Otherwise the key goes to the other mods.
  Create's toolbelt radial is blocked only while our wheel holds Alt.
- The toolbox is called **Builder's Toolbox**, because Create already has a "Toolbox".
- Chisel defaults contain no free smelting or moss: cobblestone and mossy variants are not linked to stone.
- Slate Config: the old "DF pack" page is retired (an existing `df.json` is renamed to `.retired` once), and Options
  opens the hub instead of Menu's options screen. The new sidebar order is Video, Audio, Controls, Gameplay, Interface, Multiplayer
  (Online + Chat), Customization (Mods, Resource Packs, Shader Packs, Skin), Language & Accessibility.

## How it was checked
- In-game harness scenarios, results pass/fail:

  | Scenario | NeoForge | Fabric |
  |---|---|---|
  | variants | 102/0 | 102/0 |
  | ops | 45/0 | 45/0 |
  | selection | 27/0 | 27/0 |
  | chisel | 35/0 | 35/0 |
  | toolbox | 43/0 | 43/0 |
  | menu | 13/0 | 13/0 |
  | input | 16/0 | 16/0 |
  | mirror, paste, move, dimension, render | all pass | render passes |

  Run them yourself with `-PbuildingHarness=<names>` (see `docs/building.md` › Development).
- DF compat dev run (`:dev:runClientCompat`, mods copied by `tools/compat/populate-run-compat.sh`): Sodium 0.8.12,
  Iris (+ a shader pack), Create 6.0.10, DiagonalFences/Walls/Windows, KleeSlabs, JEI, OPAC claims, Curios, Shoulder
  Surfing, Relics and BetterInventory 1.1.5. All 112 checks pass, and none of these mods shows a mixin conflict.
- A 7-angle code review found 46 real issues, 3 of them critical item dupes (e.g. extend-copying cauldrons → free
  iron). All are fixed, and regression checks were added to the harness.

## Known limitations
- Undo and the clipboard live in server memory and are lost on logout or restart.
- In survival, natural blocks that don't drop themselves (stone, glass, ores) can't be reshaped or chiselled in place
  (that would be free silk touch). Mine them first.
- Not tested: a real multiplayer server with two clients, the Rechiseled/Chipped/Chisel mods (only their data formats), and
  the full DF pack on NeoForge 21.1.250 (the dev run is 21.1.247 with the key mods).
- The in-world size label can sit partly off-screen at some camera angles.

## Housekeeping
- Agent worktrees are in `C:/Users/leonr/sbwt/*` (branches `agent/b-*`, `building`). Everything is merged into master.
  Remove them with `git worktree remove` or delete the folder and run `git worktree prune`.
- There is still no Python on this machine, so the icon atlas and mod icons now have Java generators:
  `tools/IconsGen.java` and `tools/ModIconsGen.java`.
