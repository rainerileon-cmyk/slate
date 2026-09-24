# Slate — progress tracker (overnight build, 2026-09-23)

Lead keeps this current. If the session is resumed, restart every task listed as RUNNING whose branch
has no final commit (check `git branch -a` + `git log agent/<name>`), then continue at "Integration".

## Phase 0 — skeleton (DONE, commit 1)
- Two-loader Gradle tree, 5 modules, both `gradlew build` green.
- Core API: theme, palette, widgets (26), screens (SlateScreen/SidebarScreen), layout model + applier,
  actions, element types, blob channel, events, network, platform, config, mixins (setScreen swap,
  screen init/render hooks, input routing).

## Phase 1 — parallel tasks (worktrees under the session scratchpad, branches `agent/<name>`)

2026-09-23 01:30: the account usage limit cut six agents off mid-task; all six were RESUMED via SendMessage
(context intact) at 01:33. If resuming again: their worktrees hold the work; menu (c259abf), chat (3b97f5c)
and config (8c5166d + edits) have commits, core-polish/editor/multiplayer only uncommitted trees.
| task | branch | owns | state |
|---|---|---|---|
| icons | agent/icons | tools/icons.py, icons.png, Icon.java, mod icons | DONE, merged (89de1dd) |
| core-polish | agent/core-polish | core.widget, core.gfx.SlateDraw, core.screen, core.mixin (reskin), hub/settings screens | DONE (a7a2bc8), merged |
| editor | agent/editor | core.layout.editor.*, editor lang | DONE (48cdb07), merged |
| menu | agent/menu | common/menu, loader menu dirs | DONE (4ccffe1), merged |
| multiplayer | agent/multiplayer | common/multiplayer, loader mp dirs, multiplayer.voice | DONE (e5bd692), merged |
| chat | agent/chat | core.media, core.client.media (new), common/chat, loader chat dirs | DONE (3b97f5c), merged |
| config | agent/config | common/config, loader config dirs | DONE (b57e7e1), merged |

## Phase 2 — integration (DONE 02:50)
- master = all seven branches merged + chat<->multiplayer bridge alignment (5f6f50b); NeoForge + Fabric builds green.
- Screenshot passes done: 10 menu screens x 2 skins, in-world HUD/pause/chat/hub x 2 skins (`neoforge/dev/run/screenshots`).
- Harness lesson: another agent's `Stop-Process` killed master's dev client twice (exit -1); rerun after agents finish.
- Final builds green on both loaders; jars installed in the DF pack (Chatterbox disabled); CmlLib boot test: title screen reached, 0 fatal signatures.
- Agent worktrees removed (branches `agent/*` kept). Morning report: `docs/MORNING-REPORT-2026-09-23.md`.
- Merge branches into main, full build both loaders, fix cross-module seams (media in DMs, hub entries).
- Dev-client smoke run (`neoforge/dev runClient`), screenshots of every screen in both skins.
- Boot test in the DF pack via the CmlLib harness, then install jars into `packs/df/game/mods/`
  (disable `chatterbox-neoforge-1.21.1.jar` — superseded by Slate Chat).
- Docs + memory + morning report.

# Slate Building — overnight build, 2026-09-24

New module `slate_building` (user guide `docs/building.md`, contract `docs/building-design.md`). Branch
`building`, one worktree per agent under `C:/Users/leonr/sbwt/<name>` on `agent/b-<name>`; builds through the shared
3-slot semaphore, one dev client at a time. Master was left alone all night (the user has uncommitted work there).

| stage | agent / branch | what | state |
|---|---|---|---|
| 0 | lead | design contract, agent brief, scout maps (`docs/building-maps/`), `SettingsTabs` API | DONE (7ead097) |
| 1 | S skeleton | Gradle wiring both loaders, registries, platform + loader impls, network, config shells, enums, input router, exclusive keys, `BuildingHarness`, Core `SlateRenderEvents` | DONE (b897ba7, ed50180) |
| 1 | F config-tabs | Slate Config top tabs, Gameplay/Customization categories, merged Multiplayer and Language/Accessibility pages, shader packs, DF page retired (`SlateTabStrip` in Core) | DONE, merged (86f70f2) |
| 1 | G betterinventory | BetterInventory 1.1.5: toolbox slot, `BetterInventoryApi`, offhand carousel key moved to Right Alt (one-time migration) | DONE in the BI repo, uncommitted (jar built) |
| 1 | H icons | 48 building glyphs, Java ports `IconsGen`/`ModIconsGen`, mod icon | DONE, merged (aeb9244) |
| 2 | A variants | variant registry and discovery, 13 shape blocks, unify hooks (drops, recipes, creative tab, JEI), swap and in-world reshape | DONE, merged (c4e6562) |
| 2 | B render | shape quad baker + loader models, ghost shader and renderer, overlays, hand preview | DONE, merged (114c5fc) |
| 2 | C ui | Alt swap wheel, build menu, mode HUD, wheel editor, settings tab and screen, pick-block swap | DONE, merged (51f6159) |
| 2 | D1 ops-server | planners for all 20 modes, economy, tick executor, history, clipboard, symmetry, `/slatebuild`, ops self-test | DONE, merged (987c739) |
| 2 | D2 ops-client | selection state machine, previews through the shared planners, anchors, params | DONE, merged (a06f56f) |
| 2 | E toolbox | toolbox, tools, upgrades, menu + screen, pouch, Supply Link, recipes, textures, BI bridge | DONE, merged (c715a96) |
| 2 | I chisel | group index, providers (overrides, Rechiseled, Chipped, Chisel, stonecutter, families), safety rules, sync, held and in-world chisel | DONE, merged (ab789a4) |
| 3 | INT integration | wired the parts onto each other's real code, settings rows, reload hooks, harness | DONE (16b39bf, 9fcfb0c) |
| 4 | review | read-only review of the integrated tree: 46 confirmed findings (3 critical dupes) | DONE |
| 5 | fix-ops / fix-variants / fix-ui / fix-render | all confirmed findings fixed (economy dupes, unloaded chunks, reshape loot guard, natives kept with deleteNatives, stuck keys on Fabric, HUD, re-planning) | DONE, merged (8fc2d4c, afc02da, 1fdfcf4, 2d87f57); both loaders green |
| 6 | COMPAT | DF-pack dev run (`:dev:runClientCompat`): Sodium, Iris, Diagonal*, OPAC, BetterInventory pass; KleeSlabs, Create toolbelt and JEI subtype compat added | DONE, merged (f1f351d) |
| 7 | POLISH | compat patches (KleeSlabs probe in `canBreak`, JEI stand-in entries, Create copper stairs by name), in-world wheel only with toolbox/tool in hand, area-reshape loot guard, onboarding tip; full harness NeoForge + Fabric + DF compat, all 0 FAIL | DONE (3ef0b37, d3a500b) |
| 7 | DOCS | `docs/building.md`, README, DESIGN, this section | DONE (agent/b-docs) |
| 8 | lead | merge building → master (user's uncommitted files untouched), final build both loaders, morning report `docs/MORNING-REPORT-2026-09-24.md` | DONE |

Decided overnight: the in-world reshape/chisel wheel claims Left Alt only while the toolbox or a building tool is
in the main hand; an empty hand leaves Alt to Shoulder Surfing / Relics / Create.

# Slate — layout / style switches and polish, 2026-09-24

- Core: the three switches (`customLayout`, `skin`, `reskinContainers`), the first-launch `SlateSetupScreen` with live
  mocks (`client.setup`), `VanillaScreenButtons` (Slate button on vanilla's title and pause screens, feedback row
  removed), the container restyle in either skin with the build-menu look and an accent slot highlight, floating
  panels / section rules / accent caps in `SlateDraw`, header accent cap. Settings, hub and Config's Interface page
  carry the switches and "Run setup again".
- Menu: swaps gated by the layout switch; pause menu rebuilt as one floating card (chips, rules, no feedback or Slate
  buttons); title nav on a plate, no Slate button; details and quick-connect plates.
- Building: diagonal arms of shape fences / walls / panes get a true turned outline (`DiagonalVoxelShape`), a collision
  band as wide as the drawn arm, arms that run to the block centre; the build menu opens with an empty hand (Smart).
- Not built here: the cloud session had no route to the Minecraft / NeoForge / Fabric mavens, so both loader builds
  still have to be run locally (`cd neoforge && ./gradlew.bat build`, `cd fabric && ./gradlew.bat build`).
