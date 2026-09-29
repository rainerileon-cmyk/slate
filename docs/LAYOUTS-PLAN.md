# Slate — Layouts plan: vanilla / custom / overhaul

**Status:** plan only, nothing implemented yet (written 2026-09-29).
**Source:** the owner's handwritten design page "Minecraft Slate mod" (OneNote notebook "Layoutsfinal", 2026-09-28).
It is transcribed in [Appendix A](#appendix-a--transcript-of-the-owners-page). The transcript is the source of truth:
where this plan and the transcript disagree, the transcript wins.

## Start here (for agents)

1. Read §0–§4 of this file completely, then `DESIGN.md` (suite contract) and `docs/AGENT-BRIEF.md` (house rules:
   both loaders green, no loader classes in `common/`, soft deps isolated, both styles, real code not TODOs).
2. Work packages are in §5, in dependency order. Take the first one whose dependencies are done, and set its status
   in the §5 table when you start and finish.
3. Anything marked **Proposal** was not asked for explicitly. It fills a gap, because the owner asked for that
   ("IMPORTANT: REQUIRED POLISH AND TO FILL IN THE GAPS", "Don't be afraid to touch and edit things to have them fit
   this vision"). Build proposals after the explicit items of the same package.
4. Open decisions are in §9, and each one has a **default**. Use the default and keep going; never block on a question.
5. Before coding: `git pull`, then `git status`. If there are uncommitted changes you did not make, they belong to
   someone else, so leave them alone. Never modify the DF modpack install or the BetterInventory repo (§5 A7 explains
   why).

Contents: [§0 Vision](#0-the-vision-in-ten-lines) · [§1 Glossary](#1-glossary) · [§2 Rules](#2-rules-every-work-package-must-satisfy)
· [§3 Today](#3-what-exists-today) · [§4 Target design](#4-target-design) · [§5 Work packages](#5-work-packages)
· [§6 Order and parallelism](#6-order-and-parallelism) · [§7 Verification](#7-verification) · [§8 Risks](#8-risks)
· [§9 Decisions](#9-decisions-with-defaults) · [Appendix A](#appendix-a--transcript-of-the-owners-page)
· [Appendix B](#appendix-b--file-map)

---

## 0. The vision in ten lines

1. Every menu Slate touches can be shown in one of three **layouts**:
   - **Vanilla** — Minecraft's own screen.
   - **Custom** — the Slate screens that exist today.
   - **Overhaul** — new, animated, 3D scenes made of real Minecraft blocks, players and items.
2. Every menu can also be painted in one of two **styles**, **Vanilla** or **Slate**. Any combination of layout and
   style works, set globally and overridable per menu.
3. The modules are:
   - **UI** — graphical changes to menus. This is today's Slate Menu.
   - **Multiplayer** — friends and more.
   - **Chat** — the chat rework.
   - **Player customization** — accounts, skins and cosmetics. This module is **new**.
   - Core, Config and Building also stay. Every module works on its own.
4. The Overhaul layout exists only when the UI module is installed. When a module is missing, its features behave
   differently per layout:
   - Overhaul shows them **locked**, with an "Install X module" tooltip.
   - Custom hides them.
   - Vanilla adds nothing.
5. The Slate style also applies to vanilla screens whose replacing module is not installed.
6. First launch asks for the layout (3 choices), the style (2 choices) and the container style. Options › Interface
   has an override for each menu.
7. New Overhaul screens:
   - Main menu: three chests.
   - Mod-loading screen: a Create conveyor scene.
   - Play: a ring of worlds and servers.
   - Profile: a skin stage.
   - Friends: friends standing in a world.
   - World creation: a preview of the world.
   - Options: nine tabs and a live game view.
8. Polish the Slate style. BetterInventory takes on Slate's colour palette.
9. Everything must be animated and look super polished.
10. Other screens that were not listed still get brought into line with the Overhaul look.

## 1. Glossary

| Term | Meaning |
|---|---|
| **Layout** | Which screen shows for a menu: `VANILLA` (Minecraft's), `CUSTOM` (Slate's current rebuilt screens), `OVERHAUL` (new scene-based screens) |
| **Style** | How the 2D chrome is painted: `SLATE` (today's `skin: "DARK"`) or `VANILLA` (today's `skin: "VANILLA"`) |
| **Container style** | Today's `reskinContainers` switch. Unchanged |
| **Menu slot** | One logical menu (title, play, options, friends, …) that can have up to three layouts. Registry in Core (§4.2) |
| **UI module** | Slate Menu, `slate_menu`. See Q1 |
| **Player customization module** | New module, working id `slate_profile`. See Q2 |
| **Stage** | New Core toolkit that renders real 3D Minecraft content inside a GUI rectangle (§4.3) |
| **Scene** | A Stage set-up loaded from a structure `.nbt` plus a JSON file (camera, anchors). It can be built in-game |
| **Locked feature** | An Overhaul element whose module is not installed. It is shown greyed out with an "Install X" tooltip |

## 2. Rules every work package must satisfy

- **R1** — Any layout × style combination works for every menu slot. Menus that Minecraft does not have get no Vanilla
  layout.
- **R2** — Every module works when installed alone with Core. §7 has the install matrix.
- **R3** — When a module is missing, the feature it provides:
  - adds nothing in the **Vanilla** layout;
  - has no button in the **Custom** layout;
  - is shown **locked** in the **Overhaul** layout, with the tooltip "Install <Module> to get this feature".
- **R4** — Overhaul layouts exist only when the UI module is installed. Without the UI module:
  - every menu that exists in vanilla uses its Vanilla layout;
  - every new menu uses its Custom layout.

  The first-launch layout choice is part of the UI module.
- **R5** — With style = Slate, vanilla screens are restyled even when the module that would replace them is missing.
  Core's `Reskin` already does this; keep it that way.
- **R6** — Without the UI module, the style switch (vanilla / Slate) must still be reachable from **each module's own
  settings screen**.
- **R7** — Every new menu gets a Custom and an Overhaul layout. The Vanilla layout exists only if Minecraft has the
  menu. If the owner did not specify the Custom version, design it from what already exists.
- **R8** — In the Vanilla layout, extra buttons go where other mods usually put theirs, and the menus must still feel
  vanilla.
- **R9** — Overhaul scenes are rendered with real Minecraft content: blocks, block entities, player models and items,
  not flat drawings. "Make sure all views look like actual minecraft." Long term, the owner builds the scenes in-game
  (§4.3).
- **R10** — Everything is animated and honours `motion` (0 = snap). Both styles work, the keyboard works, and there
  are tooltips. The polish bar is set in `docs/AGENT-BRIEF.md`.
- **R11** — Screens not listed in the owner's page still get changes that match the Overhaul look (§5 B5).
- **R12** — Both loaders are green. No `net.neoforged` or `net.fabricmc` imports in `common/`. Soft dependencies only
  behind `SlatePlatform.get().isModLoaded(id)`. Joining a server is never blocked.

## 3. What exists today

- **The three switches** are in `CoreConfig`, saved as `config/slate/core.json`:
  - `customLayout` — the layout switch.
  - `skin` — `DARK` or `VANILLA`, the style switch.
  - `reskinContainers` — the container style.
  - Code reads them through `Theme.customLayout()` and `Theme.current().isVanilla()`.
  - The first-launch screen is `core/client/setup/SlateSetupScreen`. Its previews are hand-drawn mocks in
    `SetupPreview`.
- **Screen swaps** go through `core/screen/ScreenSwaps`, an exact-class map called from `core/mixin/MinecraftMixin`.
  Menu registers them in `menu/client/MenuClient`. Each swap is gated by `customLayout` plus a per-screen flag in
  `menu.json` (`titleScreen`, `worldsScreen`, `serversScreen`, `pauseScreen`, `optionsScreen`, `disconnectedScreen`).
- **Custom-layout screens:**

  | Owner | Screens |
  |---|---|
  | Menu | `SlateTitleScreen`, `SlateWorldsScreen`, `SlateServersScreen`, `SlatePauseScreen`, `SlateDisconnectedScreen`, `SlateScreenshotsScreen` |
  | Menu, drawn over vanilla screens | Loading screens, in `LoadingScreens` |
  | Config | `ConfigHubScreen`. `SlateOptionsScreen` from Menu is used only when Config is absent |
  | Multiplayer | `FriendsHubScreen`, with the pages Friends, Requests, Groups, Messages, Streams and Settings |

- **NeoForge start-up window:** `neoforge/earlywindow` (`SlateEarlyWindow`, `SlateScene`, `Gfx`, `PixelFont`,
  `SlateLook`).
  - `SlateLook` reads `core.json` and `menu.json` **with regexes**, for these keys: `skin`, `accent`, `radius`,
    `customLayout`, `loadingScreens`, `headingFont` and `pixelFont`.
- **3D in the GUI:** none yet. The only player rendering is `SlateAvatar` (faces), so Stage is entirely new work.
- **Player customization:** nothing exists.
- **Known debt,** found while mapping the code:
  - three different "plate" implementations;
  - the hub is unreachable by default;
  - a promised dev-mode pencil button does not exist;
  - duplicated helpers;
  - stale reference screenshots;
  - probably a label-colour bug in the Building toolbox.

  These are in §5 A6. Appendix B has the full file map.
- **The DF pack** (the owner's modpack, the main target) contains Create 6.0.10, Xaero's Minimap and World Map,
  Drippy Loading Screen with its early-window jar, FancyMenu, Sodium, Iris, Simple Voice Chat, MCEF, BetterInventory
  and Controlling. It has no controller mod and no JourneyMap.

## 4. Target design

### 4.1 Layout and style model (Core)

`config/slate/core.json` after the change. `JsonConfig` already tolerates missing keys.

```jsonc
{
  "layout": "CUSTOM",           // NEW. Global layout: VANILLA | CUSTOM | OVERHAUL
  "skin": "DARK",               // UNCHANGED key, called "Style" in the UI: DARK = Slate style, VANILLA = vanilla style
  "reskinContainers": true,     // UNCHANGED: container style
  "screens": {                  // NEW: per-menu overrides. A missing entry or field follows the global value
    "minecraft:title":  { "layout": "OVERHAUL" },
    "minecraft:options": { "style": "VANILLA" }
  },
  "customLayout": true          // LEGACY (see below)
}
```

**Migration.** Done once, when `layout` is missing:

- `customLayout: true` becomes `CUSTOM`, and `false` becomes `VANILLA`.
- Every `menu.json` per-screen flag that is `false` becomes `screens[<slot>].layout = "VANILLA"`. A migration flag
  records that this was done.
- Keep **writing** `customLayout` (as `layout != VANILLA`) for one release, so older jars and the start-up window keep
  working.

**New installs.** Default to `CUSTOM` until B1 and B2 ship, then to `OVERHAUL` (Q5). The setup screen asks anyway.

**Resolution for a slot:**

```
available(slot):
  VANILLA  iff  the slot is a menu Minecraft has
  CUSTOM   iff  a custom provider is registered (its module is loaded)
  OVERHAUL iff  an overhaul provider is registered AND slate_menu is loaded            (R4)
effectiveLayout(slot) = override(slot) ?? global, clamped to available(slot):
  vanilla menus:  OVERHAUL -> CUSTOM -> VANILLA   (fall back downwards)
  new menus:      VANILLA  -> CUSTOM, OVERHAUL -> CUSTOM
effectiveStyle(slot)  = override(slot).style ?? global skin
```

**Core API.** Add new files only, and keep existing signatures (house rule). Suggested package:
`dev.fallingcloud.slate.core.screen.slot`.

```java
public enum Layout { VANILLA, CUSTOM, OVERHAUL }
public enum Style { SLATE, VANILLA }                 // maps to Skin.DARK / Skin.VANILLA
public record MenuSlot(String id, Component name, String owner, boolean vanillaMenu, List<String> vanillaClasses) {}
public final class MenuSlots {
    static void register(MenuSlot slot);
    static void provide(String slotId, Layout layout, Function<Screen, Screen> factory); // gets the vanilla screen (or the parent)
    static Set<Layout> available(String slotId);
    static Layout effective(String slotId);
    static Style style(String slotId);
    static void open(String slotId, @Nullable Screen parent);   // for new menus that have no vanilla class
    static List<MenuSlot> all();                                  // drives the settings tables (§4.5)
}
```

**Wiring:**

- Core installs one swap for each vanilla class a slot lists, and that swap asks `MenuSlots`.
- `MenuClient` registers **providers** instead of raw swaps.
- `ScreenSwaps` stays available for other uses.
- `Theme.customLayout()` stays as a deprecated shim that returns `global layout != VANILLA`.

**Per-menu style:**

- `Theme` caches both variants.
- After a swap, the `setScreen` hook sets the active variant from the style of the new screen's slot. Unknown screens
  use the global style.
- `Theme.current()` returns the active variant. `containerPalette()` is unchanged.

**Start-up window:**

- Core writes a flat file, `config/slate/earlywindow.properties`, whenever settings change. It holds the effective
  layout and style of slot `slate:loading`, plus accent, radius, font and `loadingScreens`.
- `SlateLook` reads that file first and falls back to its current regexes. This stops the start-up window from breaking
  on JSON structure changes.

### 4.2 Menu slots

| Slot id | Vanilla | Custom (module · class) | Overhaul (module · class) |
|---|---|---|---|
| `minecraft:title` | `TitleScreen` | Menu · `SlateTitleScreen` | Menu · new (B1) |
| `minecraft:select_world` | `SelectWorldScreen` | Menu · `SlateWorldsScreen` | Menu · new `PlayScreen`, worlds ring (B2) |
| `minecraft:multiplayer` | `JoinMultiplayerScreen` | Menu · `SlateServersScreen` | Menu · `PlayScreen`, servers ring (B2) |
| `minecraft:create_world` | `CreateWorldScreen` | Menu · **new** (B3) | Menu · new (B3) |
| `minecraft:options` | `OptionsScreen` | Config · `ConfigHubScreen`, or Menu · `SlateOptionsScreen` when Config is absent | Config · new (C1). Needs Menu **and** Config |
| `minecraft:pause` | `PauseScreen` | Menu · `SlatePauseScreen` | Menu · new (B5, Proposal) |
| `minecraft:disconnected` | `DisconnectedScreen` | Menu · `SlateDisconnectedScreen` | Menu · new (B5, Proposal) |
| `slate:loading` | NeoForge's default window; Fabric's overlay | Current start-up card and in-game `LoadingScreens` | Conveyor scene (B4) |
| `minecraft:level_loading` (and the other world-loading screens) | vanilla | Menu · `LoadingScreens` (drawn over) | B5, Proposal |
| `slate_menu:screenshots` | — | Menu · `SlateScreenshotsScreen` | B5, Proposal |
| `slate_multiplayer:hub` (Friends) | — | Multiplayer · `FriendsHubScreen` | Multiplayer · new (D1). Needs Menu |
| `slate_profile:profile` | — | Profile · new (E2) | Profile · new (E2). Needs Menu |
| `slate:setup` | — | Core · setup v2 (A4) | Same screen: it is the chooser |
| `slate:hub` | — | Core · `SlateHubScreen` | None; Options covers it (Q9) |
| Containers | vanilla | — | — (only the container style applies) |

### 4.3 Stage and scenes (Core, client only)

Suggested package: `dev.fallingcloud.slate.core.stage`.

- **Render target.** Render into its **own `RenderTarget`**, sized to the rectangle × GUI scale (with a cap), then
  blit it into the GUI. This keeps depth isolated, allows fog and vignette, and allows rendering at lower resolution.
  The owner plays at 5120×1440.
- **Camera.** Perspective: FOV, position, target, roll.
- **Nodes:**

  | Node | Draws | Notes |
  |---|---|---|
  | `Block` | a block state | optionally through its block-entity renderer with state, e.g. a chest lid angle |
  | `Item` | an item | |
  | `Player` | a player model | skin from `SkinManager` or a local PNG; poses stand, sit, wave, look-at-cursor, idle |
  | `Entity` | an entity | pets, animals |
  | `Text` | text in 3D | sign text, nameplates |
  | `Model` | a JSON block/item model from resources | Blockbench "Java model" export |
  | `Structure` | a `StructureTemplate` read from `.nbt` | meshed **once** into vertex buffers |
  | `Voxel` | generated meshes | planets, heightfields |
  | `Fx` | clouds, sparkles | lightweight quads |
- **Lighting.** Fixed sun direction plus ambient light, and full block light. A fake `BlockAndTintGetter` provides
  biome tints.
- **Picking.** A ray from the mouse is tested against the node bounding boxes. This drives hover, press and click. The
  keyboard cycles focus through the pickable nodes, and the focused node gets a 3D outline.
- **Animation:**
  - Tweens use `core.gfx.Ease`.
  - Camera paths are line, spline or orbit, with easing. They are JSON-compatible with Dolly's shot format
    (`../../Base/dolly/SHOTS.md`). Copy the evaluator into Core; do not add a dependency on Dolly.
  - With `motion == 0`, jump straight to the end state.
- **Scenes:**
  - Files are `assets/<ns>/slate/scenes/<id>.json` plus an optional `.nbt`.
  - `config/slate/scenes/<id>.json|.nbt` overrides them, for modpacks and for the owner's own builds.
  - The JSON holds: background (colour or gradient), structure, camera (intro path plus idle motion), light, and
    named **anchors**. Anchors are the positions and rotations where code puts interactive nodes, for example
    `chest_play` or `sign_continue`.
- **Proposal: scene capture.** A dev-mode action that turns a saved structure-block template plus the player's current
  eye position into a scene JSON. The owner can then build scenes in-game ("this needs to actually be built in
  minecraft").
- **Dev-mode editor.** It edits only the 2D chrome of Overhaul screens. 3D content is edited through scene files.
- **Budget.** A title scene costs 2 ms of frame time or less at 1440p. No allocations per frame. Meshes are cached.
  The stage pauses when its screen is hidden.
- **Compatibility.** Sodium and Iris (test with a shader pack active), GUI scales 1–6, ultrawide screens, and both
  loaders.

### 4.4 Modules, locked features, new module

| Module | Id | Role in this plan |
|---|---|---|
| Core | `slate` | Layout and style model, menu slots, Stage, feature gates, setup, the style everywhere, BetterInventory compat |
| UI | `slate_menu` | Custom and Overhaul layouts of the vanilla menus, and the loading screens. Its presence enables Overhaul |
| Config | `slate_config` | Options in Custom and Overhaul. Overhaul also needs the UI module |
| Multiplayer | `slate_multiplayer` | Friends in Custom and Overhaul |
| Chat | `slate_chat` | Chat rework. No layout changes, only polish (R11) and the style row (R6) |
| **Player customization** | `slate_profile` (Q2) | **New.** Accounts, looks and skins, cosmetics, profile picture and bio. Profile screen in Custom and Overhaul |
| Building | `slate_building` | Unaffected. Its style follows the switch, and it gets the polish item A6.7 |

- **`KnownModules`** (Core). A static catalogue of every Slate module: id, name key, icon, one-line pitch and optional
  URL. It is used for tooltips when a module is **not** installed.
- **`Features`** (Core). One call answers "is module X present?" and applies R3:

  | Layout | Module present | Module missing |
  |---|---|---|
  | Overhaul | normal element | locked element: desaturated, lock glyph, tooltip `slate.feature.locked` = "Install %s to get this feature" |
  | Custom | normal element | the caller gets `null` and skips it |
  | Vanilla buttons (A5) | added | not added |

### 4.5 Settings surfaces

- **Setup v2.** This is Core's first-launch screen, with three rows:
  1. **Layout** (Vanilla | Custom | Overhaul). Without the UI module, Custom and Overhaul are disabled, with the note
     "Needs Slate UI".
  2. **Style** (Vanilla | Slate).
  3. **Containers** (Vanilla | Slate).

  The live previews should render the real screens small, off-screen, instead of the hand-drawn mocks. The Overhaul
  preview is the three-chest Stage scene.
- **Options › Interface** (Config) keeps today's rows and adds:
  - A **Menus** table built from `MenuSlots.all()`. Each row has a Layout segmented control listing only the
    available layouts; unavailable ones are disabled and have a tooltip. It also has a Style segmented control and a
    "follow global" reset.
  - A **Containers** row.
  - Core's own settings screen, used when Config is absent, shows the same table in a simpler form.
- **Every module's own settings screen** gets a **Style** row (R6). This covers Multiplayer's `SettingsPage`,
  `ChatSettingsScreen`, Building's settings, Profile's settings and Core's settings.

---

## 5. Work packages

Status values: `TODO` → `IN PROGRESS (<agent/branch>)` → `DONE (<commit>)`.

| WP | Title | Module(s) | Depends on | Status |
|---|---|---|---|---|
| A1 | Layout and style model, menu slots, migration | Core, Menu, Config, earlywindow | — | IN PROGRESS (claude/stoic-shannon-5lboan) |
| A2 | Module catalogue and feature gates | Core | A1 | TODO |
| A3 | Stage toolkit and scenes | Core | — (spike first) | IN PROGRESS (agent worktree, merged into claude/stoic-shannon-5lboan) |
| A4 | Setup v2 and settings surfaces | Core, Config, all modules | A1, A2 | TODO |
| A5 | Vanilla-layout extra buttons | Core (+ modules) | A2 | TODO |
| A6 | Polish and cleanup | all | — | TODO |
| A7 | BetterInventory follows Slate's palette | Core | — | TODO |
| B1 | Main menu, Overhaul | Menu | A1 A2 A3 | TODO |
| B2 | Play screen, Overhaul (+ shared data layer) | Menu | A1 A2 A3 | TODO |
| B3 | World creation, Custom and Overhaul | Menu | A1 (+ A3) | TODO |
| B4 | Mod-loading screen, Overhaul | earlywindow, Menu | A1 | TODO |
| B5 | Filling the gaps: pause, disconnected, world loading, screenshots | Menu | A1 A2 A3 | TODO |
| C1 | Options, Overhaul (+ Custom regrouping) | Config | A1 A2 | TODO |
| D1 | Friends, Overhaul | Multiplayer | A1 A2 A3, D2 | TODO |
| D2 | Friends, Custom layout updates | Multiplayer, Core | A1 | TODO |
| E1 | Player customization module: skeleton and global store | Profile (new) | A1 | TODO |
| E2 | Profile screen, Custom and Overhaul | Profile | E1, A3 | TODO |
| E3 | Looks and skins | Profile | E1 | TODO |
| E4 | Cosmetics and multiplayer sync | Profile | E1 | TODO |
| E5 | Profile picture and bio, integration with friends | Profile, Multiplayer | E1, D2 | TODO |
| E6 | Account switcher | Profile | Q4 | TODO |
| F1 | Docs, screenshots, final verification | all | everything | TODO |

### A1 — Layout and style model

**Do**

1. Implement §4.1: the enums, `MenuSlot`, `MenuSlots` and the resolution rules.
2. Add `layout` and `screens` to `CoreConfig`, with the migration from `customLayout` and from the `menu.json` flags.
3. Change `Theme`: add the active-style variant, and keep `customLayout()` as a shim.
4. Change the swap path in `MinecraftMixin`/`ScreenSwaps` so it consults `MenuSlots`.
5. Change `MenuClient` to register slots and providers.
6. Change Config's `installSwaps`. Today vanilla option sub-screens are redirected into the hub whenever the Vanilla
   layout is on. The redirect must follow the `minecraft:options` slot.
7. Write `earlywindow.properties`, and have `SlateLook` read it.
8. Add a harness flag, `-PautoLayout=VANILLA|CUSTOM|OVERHAUL`, in `neoforge/dev/build.gradle` and `fabric/dev`.

**Done when**

- Changing the global layout, or one slot's override, changes the screen at its next open.
- Style overrides per slot work.
- An old `core.json` and `menu.json` migrate with no visible change.
- The start-up window still picks the right look.
- Both loaders are green.

### A2 — Module catalogue and feature gates

**Do**

1. Add `KnownModules` and `Features` (§4.4).
2. Add the locked look for 2D widgets (a `SlateButton` state) and for Stage nodes.
3. Add the language keys.

**Done when**

A test screen (harness id `slate:feature_test`) shows every catalogue module as normal, hidden or locked, correctly for
each layout, with any combination of modules removed.

### A3 — Stage toolkit and scenes

**Do**

1. Build §4.3.
2. **Start with a one-day spike**: a chest with an animated lid, and a skinned player. Render both into an FBO inside a
   `SlateScreen`, on both loaders, with Iris and a shader pack on. Report the result before building the rest.
3. Build the full node set, picking, the timeline and camera paths, and scene loading (resources plus config override).
4. Add a test screen, `slate:stage_test`, that shows every node type.

**Done when**

- The test screen renders all of these:
  - a chest opening;
  - players standing and sitting with real skins;
  - an item;
  - a sign with text;
  - a `.nbt` structure;
  - a voxel planet.
- Hover and click work on the nodes.
- `motion 0` snaps to the end state.
- Frame time is within budget.
- It works with Iris shaders on.

### A4 — Setup v2 and settings surfaces

**Do**

1. Build §4.5.
2. Replace the hand-drawn `SetupPreview` mocks with small renders of the real screens. If that proves impossible,
   keep mocks but generate them from the same code paths.
3. Add the Style row to every module's own settings screen (R6).

**Done when**

- A first launch with only Core offers Style and Containers, and shows the "needs Slate UI" note for Layout.
- With the UI module installed, all three rows work live.
- The Menus table lists every registered slot.

### A5 — Vanilla-layout extra buttons

R8. In the Vanilla layout, add a vanilla-looking button for each **installed** feature module, for example Friends and
Profile. Place them the way popular mods do: a half-width row, or 20 px icon buttons next to the language and
accessibility buttons, as in ModMenu. Keep the existing Slate hub button. In the pause menu, put them where the
feedback row was, as half-width buttons. Missing modules add nothing.

**Done when:** screenshots of the Vanilla title and pause screens, in both styles, look like vanilla plus a
well-behaved mod.

### A6 — Polish and cleanup

The owner: "improve the Slate style a bit, small things to improve polish". Each item is small, and they can be done in
parallel.

1. **One plate.**
   - Route the title's navigation plate (hand-rolled, alpha 0x8C) and `BuildMenuScreen.panel()` through
     `SlateDraw.floatingPanel`.
   - The pause card on the vanilla style should use `floatingPanel`'s vanilla branch, as the loading card already does.
2. **One section header.** Merge the two `SectionHeader` classes (Menu's is 18 px, Config's is 20 px) into Core.
3. **One tab widget.** Retire `SlateTabs`, which only `GifLibraryScreen` uses, in favour of `SlateTabStrip`.
4. **Deduplicate helpers.** `ClipboardImages` and `FilePicker` each exist three times (core, menu/config, mp).
   Keep one of each, in Core.
5. **Dead code.** `MenuClient.friendsScreenId()` checks ids that are never registered. Deprecate `Theme.HEADING_FONT`,
   but do not delete it (it is public API).
6. **The dev-mode "pencil button".** The config comment and the `slate.settings.dev_mode.tip` string promise one on
   editable screens, and none exists. Build it, or fix the text.
7. **Suspected bug: the Building `ToolboxScreen`.** It paints itself in the menu style, not the container style. With
   the vanilla style and the container style on, its labels may turn light on its light panel. Verify, then make it
   follow the container style.
8. **Settings that are duplicated in four places.** After A4, the switches live in the setup screen, Options ›
   Interface, and Core settings (only when Config is absent). The hub card appears only in the Vanilla layout.
9. **The Slate hub is unreachable by default.** Resolve it as Q9 says.
10. **Style audit.** Every Slate screen, in both styles:
    - spacing on the 2/8 px grid;
    - hover, press and focus states;
    - motion timings;
    - text contrast;
    - empty states;
    - scrollbars;
    - tooltip consistency.

    Fix what is off.
11. **Refresh `docs/screenshots/`** before starting, as a baseline, and again at F1.

### A7 — BetterInventory follows Slate's palette

The owner: "make a compat with better inventory changing it's color palette to the currently used slate one".

**Background**

- BetterInventory (`betterinventory`) draws its screens from sprite sheets, and each resource pack draws its own
  version of the sheet.
- Its dark panels use RGB 26,26,26. It ships a built-in "Vanilla Style" pack whose id contains `vanilla_style`.
- `ClientTheme.ghostWash()` uses 0x9E1A1A1A (dark) or 0x9E8B8B8B (vanilla).
- Its repo is at `../../QOL/BetterInventory`, with about 190 uncommitted changes. **Do not edit that repo.** Do
  everything from Slate's side.

**Do**

1. Add `core/compat/betterinventory/*`, a soft dependency.
2. When BI is loaded and the container style is Slate, recolour BI's GUI sheet textures at runtime:
   - map BI's dark sheet colours to `Theme.containerPalette()` and the accent;
   - register them as dynamic textures at the same locations;
   - re-apply after every resource reload and on every theme change;
   - never trigger a full resource reload just for an accent change.
3. **Proposal:** the Vanilla container style switches on BI's own `vanilla_style` pack.

**Check both BI versions:** the DF pack runs 1.0.1, and the latest is 1.1.4. Texture paths may differ between them;
detect and support both.

**Done when:** BI's inventory in the DF pack matches Slate's container look and follows accent changes live.

### B1 — Main menu, Overhaul

**From the owner**

- Big "Minecraft" logo at the top left.
- An animated Minecraft scene with **three chests** that open one by one after a **cinematic zoom-in**. Shortly after
  each chest opens, an animated 3D button appears in its opened lid. The bottom part of each chest has a clickable
  **sign** that is also a button.

  | Chest | 3D button | Sign |
  |---|---|---|
  | 1 | **Play**: a beautiful cube world with clouds all around it. It zooms in slightly and animates on hover | **Continue** |
  | 2 | **Profile**: simply shows the current player's skin | **Friends** |
  | 3 | **Options**: a Create cogwheel rotating slowly. It spins faster for about a second on hover | **Quit game** |

- For now the scene is **the three chests on a dark background**. Later the owner builds the scene in Minecraft
  (scene id `slate_menu:title`, §4.3). Code places the chests and buttons at the scene's anchors.

**How**

1. **Intro.** It plays once per game session, with a shorter settle when returning from a sub-screen:
   - the camera zooms in for about 1.8 s with outCubic easing;
   - chest lids open over about 350 ms each, 250 ms apart;
   - each button rises with a spring ease;
   - chest-open sounds play at low volume if `uiSounds` is on.
2. **Play** opens the `minecraft:select_world` slot, which is the Play screen's worlds ring in Overhaul.
3. **Continue** uses `LastPlayed` and `ContinueCard`'s logic. If nothing was played yet, the sign is greyed out, with
   the tooltip "Nothing to continue yet".
4. **Profile** needs `slate_profile` and **Friends** needs `slate_multiplayer`. When the module is missing, each is
   locked (R3).
5. **The cogwheel.** Use Create's own cogwheel block model when Create is installed. Otherwise use a Slate cogwheel
   model (Q6).
6. **Signs** are real sign models with the text rendered on them. On hover, a sign lifts and glows.
7. **Keyboard.** ←/→ cycles Play, Continue, Profile, Friends, Options and Quit. Enter activates the focused one.
8. **Styles.** The 3D scene is the same in both styles. The style applies to the 2D chrome: tooltips, corner buttons,
   footer and dialogs.
9. **Proposal: filling the gaps.**
   - Small corner icon buttons: Mods (when a mod list exists), Screenshots, Language, Accessibility, and Edit (dev
     mode).
   - A footer with version, loader and mod count.
   - An account chip at the top right, with head and name, that opens Profile.
   - The camera framing adapts to the aspect ratio (ultrawide).

**Done when**

- The intro plays once, and every target works with mouse and keyboard.
- The locked states are correct with modules removed.
- Motion 0 snaps.
- Both styles and both loaders work, and harness screenshots exist.

### B2 — Play screen, Overhaul

**From the owner**

- **Worlds sit on a rotating ring.** Worlds further back get smaller.
  - A config sets how many are visible at once. Scrolling brings new ones in, so any number of worlds can be shown.
  - To scroll: click a world, use the ← → arrows, or use the mouse wheel.
- **A second, smaller ring sits at the centre.** Clicking it makes the current ring shrink out and the new one expand
  to replace it.
  - That second ring holds the **servers**. It works exactly the same way, and has its own inner ring that goes back to
    the worlds with the same animation.
- **Each world and server looks like the Play button's cube world.** Each one is procedurally generated to look
  slightly different, and some defining feature makes servers look different from worlds.
- **When a world or server is selected:**
  - If JourneyMap or Xaero's map is installed, show an **interactive minimap**.
  - Below it, show the main details. Scrolling shows the rest.
  - Below those, show **Edit, Play, Trash** and **⋯** for more (clone, etc.).
- **Below the ring:** a Play button, the world name, and small edit icons.
- **Top left:** a search bar.
- **Top:** the sort order.
- **A little star favourites a world or server.** Favourites always appear first.

**How**

1. **Shared data layer, first.** Pull the models out of `SlateWorldsScreen` and `SlateServersScreen` into
   `menu/client/play/model`. The world models are `WorldEntry`, `WorldActions` and `WorldFavorites`; the server models
   are `ServerPinger`, `ServerMeta`, `LanScanner`, `CommunityServers` and `ServerActions`. Both the Custom and the
   Overhaul screens then use them, with no change in the Custom behaviour.
2. **`PlanetGenerator(seed, kind)`** builds voxel planets. It is also used by B1 (the Play button) and B3.
   - **Worlds:** seed the planet with a hash of the folder and name. Vary the biome palette, trees, water, mountains
     and snow. Hardcore worlds get a distinct palette. **Proposal:** show the world's `icon.png` on a small flag.
   - **Servers:** add a defining feature, for example an antenna or beacon beam plus a ring. **Proposal:** show the
     favicon on a banner.
3. **Config:** `menu.json: playRingVisible`, default 7, range 3–15, plus the sort keys that already exist.
4. **Proposal: creating new entries.** Slot 0 of each ring is a ghost "+" planet: New world, or Add server.
5. **Proposal: server groups.** Filter chips for All, Favourites, Community, LAN and Recent. The Community, LAN and
   Recent groups that exist today stay reachable this way.
6. **Minimap (Q8).**
   - Xaero's World Map first, because it is in the DF pack. It needs a spike on its on-disk cache format.
   - JourneyMap region PNG tiles second.
   - Otherwise, show no map.
   - The map is interactive: drag to pan, wheel to zoom.
7. **The right panel.**
   - **Worlds:** mode, version, last played, size, cheats, hardcore.
   - **Servers:** MOTD, ping, players with heads, version compatibility, and the "friends here" chip.
   - **Scrolling reveals more.** Worlds: folder, seed if known, play time. Servers: address and resource-pack policy.
   - **⋯ menu, worlds:** Duplicate, Backup, Open folder, Recreate, Export.
   - **⋯ menu, servers:** Copy address, Move group, Remove.

**Done when**

- 50 or more worlds scroll smoothly, and switching between the worlds and servers rings animates both ways.
- Favourites, search and sort work.
- Every action works.
- The minimap works in the DF pack with Xaero's World Map, or the spike result is reported.

### B3 — World creation, Custom and Overhaul

**From the owner (Overhaul)**

- The top shows a view that is a preview of the world, with a simple button to minimise it.
- The world-creation tabs are below it.

**How**

1. **Do not reimplement world creation.** Presets, datapacks, experiments and modded world types make that fragile.
   Keep vanilla's `CreateWorldScreen` state and logic. Draw Slate's chrome, move the tab bar and tab content down, and
   inject the preview. Loading screens already use this "drawn over" technique.
2. **The Custom layout** is new (R7). It restyles the tabs with `SlateTabStrip` and shows a small preview card.
3. **The Overhaul layout** puts a large preview on top that can be minimised.
4. **Preview (Q12).**
   - Take the screen's `WorldCreationContext`, which gives the selected dimensions and the registries. Build a
     `RandomState` from it.
   - Sample the biome source and heights on a grid around spawn (for example 128×128 at a 4-block step), on a
     background thread.
   - Debounce by 300 ms after a change to the seed or preset, and cancel stale jobs.
   - Show a biome-colour map with height shading. In Overhaul, add a slowly rotating voxel diorama of the centre, built
     with `PlanetGenerator`'s mesher.
   - World types that cannot be previewed show "No preview for this world type".
   - If the "World Preview" mod is installed, do not add ours.

**Done when:** the preview updates as the seed changes, in both layouts and both styles, and creating a world works
exactly as it does in vanilla (datapacks, experiments, modded presets).

### B4 — Mod-loading screen, Overhaul

**From the owner**

- The sketch places: Version at the top left, the "Minecraft" logo, "X/Y (ram)", "Loading text", and a custom 3D
  animation.
- The loading screen is a **conveyor-belt scene built with Minecraft's Create mod**. A final object or container shows
  the progress of crafting items.
- On the left, a process shows the progress of the current task. It is also built in the game. When the task finishes,
  it adds to the final count and resets.
- On the far left, a **vertical lava container** shows RAM usage. It is also built in the game.
- Text shows the current task and the exact amount of RAM used.

**How**

1. **Pre-render the frames.** The NeoForge start-up window runs before Minecraft's renderer exists, so it cannot draw
   block models. The scene is built in-game, then filmed with Dolly (`../../Base/dolly`, frame-locked PNGs; see the
   repo `CLAUDE.md`, "Filming a mod") in **layers**:
   - a static background;
   - a looping belt;
   - the process machine, whose frames are indexed by the current task's progress;
   - item sprites;
   - the lava tank at N fill levels, or a scrolling lava strip;
   - the states of the final container.

   A `tools/` script packs the frames into sprite sheets. The early window loads them with STB, which it already uses
   for fonts.
2. **Placeholder until the owner builds the scene:** the same layout, drawn with the existing `Gfx` primitives.
3. **Data:**
   - FML progress messages become tasks. Each completed task sends one item along the belt into the container, and the
     counter goes up by one.
   - RAM is `totalMemory - freeMemory` against `maxMemory`, shown as the lava fill level and as the text "X / Y MB".
4. **The in-game half.** Keep the same scene through Minecraft's `LoadingOverlay` until the title screen, on NeoForge,
   as the Custom card already does. On Fabric, where there is no start-up window, draw the same scene in a
   `LoadingOverlay` mixin, with the frames loaded as `NativeImage`s.
5. **Gating** comes from the `slate:loading` slot in `earlywindow.properties`:
   - **Vanilla:** NeoForge's own window, recoloured when the style is Slate (as today).
   - **Custom:** today's card.
   - **Overhaul:** the conveyor scene.
6. **Budget:** the frames plus the early-window jar stay at or under about 6 MB.
7. **Drippy Loading Screen is in the DF pack.** Slate only takes over when `fml.toml` names NeoForge's default window
   (Q11). Removing Drippy from the pack is the owner's call.

**Done when:** a NeoForge start-up shows the Overhaul scene (placeholder or real) with live tasks, a working counter
and the RAM tank; Fabric shows it during `LoadingOverlay`; the Vanilla and Custom layouts still work.

### B5 — Filling the gaps (Proposals, R11)

Do these after B1–B4. Each needs Overhaul and a matching Custom polish.

- **Pause.** The world stays visible behind a lighter blur. A left rail shows three small Stage objects that mirror the
  title: the cube world (Back to game), the player (Profile and Friends) and the cog (Options). Next to them are text
  buttons and information chips: world, session time, dimension, day and time. Save & Quit or Disconnect is a sign,
  with a confirmation.
- **Disconnected.** The reason is shown on a sign in a small dark scene, with Reconnect and Back.
- **In-world loading screens.** The spawn chunk map is shown as a voxel diorama that rises as chunks load.
- **Screenshots.** The gallery in Overhaul chrome. A stretch goal is an item-frame wall.
- **Hub.** It has no place in Overhaul (Q9).

### C1 — Options, Overhaul

**From the owner**

- The main tabs are: General, Video, Controls, Audio, Multiplayer, Interface, Language and accessibility,
  Customization (mods, resource packs, shaders), Advanced.
- It should feel polished and intuitive. Advanced configs stay on the page that opens when you click Advanced.
  Horizontal sub-tabs are allowed inside tabs.
- General and Video have a **real in-game window** above them when the player is in a world. A simple button minimises
  it.
- Controls allows easy access, plus controller compatibility when a controller mod is installed.
- Audio has device switching and the voice-chat settings.
- Multiplayer has chat, friends settings and more.
- Interface keeps what it has now and adds **style and layout toggles for each menu** Slate touches, and for containers
  in general. A general version of these toggles is what the first launch shows.
- The rest mostly stays the same.

**Where each tab's content comes from**

| New tab | Contents (from today's Config pages) |
|---|---|
| General | **Proposal:** the most-changed options: FOV, render distance, GUI scale, brightness, fullscreen, max FPS, master volume, difficulty. Also Gameplay's General tab, plus the `SettingsTabs` `gameplay` tabs (for example Building) as sub-tabs |
| Video | Today's Video tabs (Display, Graphics, Performance, Animations & effects, HUD & extras), without the rows flagged advanced |
| Controls | Mouse, Movement, Key binds, plus a Controller sub-tab when a controller mod is present (`controlify`, `controllable`; none is in the DF pack) |
| Audio | Volume, Output (device switching, which exists today), Voice chat (Simple Voice Chat) |
| Multiplayer | Online, Chat (Slate Chat), **Friends** (the Multiplayer module's settings, moved here), Skin layers |
| Interface | Today's Interface page, plus the Menus table and Containers (§4.5) |
| Language & accessibility | Language, Accessibility |
| Customization | Mods, Resource packs, Shader packs |
| Advanced | Every row flagged `advanced`, with the same tab structure as sub-tabs. Also restyle scope and lists, dev mode, Favourites, Presets, curated pages, and the raw config editors |

**How**

1. **Add an `advanced` flag** to the option-row and section model, and curate which vanilla and Sodium rows are
   advanced.
2. **The live game view:**
   - Copy the main render target after the level renders and before the GUI draws, into an FBO. This needs a new Core
     hook.
   - Draw it scaled, in a framed rectangle at the top of General and Video.
   - Show it only when `level != null`.
   - Minimising collapses it to a slim bar. That state is persisted in `config.json`.
   - Changes to FOV, brightness, render distance or graphics show live.
3. **Proposal: the Custom layout.** Regroup `ConfigHubScreen` into the same nine categories, so both layouts share one
   structure and differ only in presentation.
4. **Overhaul needs both Menu and Config (R4).** Config registers the provider.

**Done when**

- All nine tabs exist and search still finds everything.
- The live view works in a world and is absent on the title screen.
- The Menus table changes screens live.
- Both styles work.

### D1 — Friends, Overhaul

**From the owner**

- Tabs on the left: **Friends, Messages, Groups, Requests**. Also have a **Screenshots** tab here.
- Search and order selectors, the same as for worlds.
- Friends are shown **in a Minecraft world**:
  - online friends stand and offline friends sit;
  - two arrows show more friends to the left or right;
  - every friend has their name and status shown above them.
- Clicking a friend selects them, and their info and buttons appear below.
- A **+** button on the right adds friends.
- The Groups tab is a similar view, with the players **sitting around a campfire**.
- The design of the rest, including the other tabs, is open to be creative with.

**How**

1. **Scene** `slate_multiplayer:friends` (§4.3). The placeholder is a small grass diorama with logs and benches for
   sitting.
2. **Players:**
   - They are drawn with Stage `Player` nodes, skinned through `SkinManager` from their `PlayerRef` UUID.
   - A label above each shows the name and presence, from `FriendInfo` and `Presence`: online, away, in game on a
     server, offline.
   - Idle animations: players look at the cursor, and wave on hover.
3. **The arrows** page through the friends; the number per page is set by the width.
4. **The selection panel** shows presence details. Buttons: Message, Invite, Join (when on a server), Player card,
   Remove, Block.
5. **Groups:** one campfire per group, with its members sitting around it. Click a campfire to open that group's chat
   and details. The arrows switch between groups.
6. **Proposals for the other tabs:**
   - **Messages:** a thread list, and the conversation with the other player's model standing beside it.
   - **Requests:** incoming and outgoing requests, with the players waiting at a gate.
   - **Screenshots:** the gallery (see D2).
7. **Streams are gone** unless they are enabled in the config (Q10).

**Done when:** every tab works against a real hub (`tools/multiplayer`), with presence updates live; the locked and
missing-module rules hold; both styles work.

### D2 — Friends, Custom layout updates

1. **Streams are off by default** in both layouts: `multiplayer.json` gets `streams: false`. With it off, hide the
   Streams page, the stream toasts, the picture-in-picture view and the stream actions. The toggle lives in
   Options › Multiplayer › Friends.
2. **Add a Screenshots tab** to `FriendsHubScreen`.
   - Move the screenshot model out of Menu into Core: folder listing, sorting and the thumbnail cache, from
     `menu/client/screenshots/Screenshots`. It goes to `core.media.screenshots`.
   - This lets Multiplayer show the gallery **without** Menu (R2).
   - The share-to-friends action that exists today stays.
3. **Move the friends settings** to Options › Multiplayer. Keep a link to them in the hub.

### E1 — Player customization module: skeleton and global store

1. **The module.** Add Gradle projects `:profile` under both `neoforge/` and `fabric/`, copying the wiring from `chat`
   or `building`.
   - Mod id `slate_profile`, package `dev.fallingcloud.slate.profile`, display name "Slate Profile" (Q2).
   - It runs on both sides. Its payloads are **optional**, so joining a server is never blocked.
2. **The global store.** The owner wants profiles and skins "saved to PC across any instance/version", so they cannot
   live in the instance folder.
   - Location:

     | OS | Path |
     |---|---|
     | Windows | `%APPDATA%\.slate\` |
     | macOS | `~/Library/Application Support/slate/` |
     | Linux | `$XDG_DATA_HOME/slate/` or `~/.local/share/slate/` |

     The system property `slate.globalDir` overrides it.
   - Writes are atomic and use a file lock, because two instances may run at once. The schema is versioned, with
     migrations.
   - It holds: `looks/`, `cosmetics.json`, `profile.json` (profile picture and bio), and **never** tokens (see E6).
3. **Instance preferences** go in `config/slate/profile.json`.

### E2 — Profile screen, Custom and Overhaul

**From the owner (Overhaul)**

- **The view:**
  - "Select skin" at the top left and a search box at the top right.
  - A **carousel of looks and skins**. Left and right arrows switch between them.
  - The look in view is on a stage, lit by a nice 3D shining light. The **selected** look shines brighter, and its
    light stays on even when it is not in view.
  - **Select** and **Edit** buttons.
  - A **Microsoft account switcher** (a "v" dropdown).
  - A **profile picture** selector and swapper, and a **bio**.
- **Creating a look:** move to the blank slot, which shows a default Steve or Alex.
- **Editing:**
  - The other looks animate out and are replaced by buttons.
  - **Left side:** an upload-base-skin option, plus buttons to edit the left and right legs and arms, and pets (this
    last word is an uncertain reading).
  - **Right side:** accessories such as shirts, pants, back accessories and hats.
  - Clicking a button zooms in on that body part and shows the available items, rendered in 3D. A small button at the
    top right of the preview turns the zoom off entirely.

**How**

1. **Proposal: the Custom layout (R7).** A `SlateScreen` with the looks list on the left and the 3D preview on a
   pedestal in the centre (Stage). On the right, tabs for Details and Cosmetics. It uses the same data as Overhaul.
2. **The Overhaul layout** follows the spec above, using Stage spotlights (§4.3).
3. **Hooks:**
   - B1's Profile chest and the account chip open this screen.
   - Profile is locked (R3) when the module is absent.

### E3 — Looks and skins

1. **A look** is: name, skin PNG, model (slim or classic), and a cosmetics loadout.
2. **"Upload base skin"** uses a file picker and validates the image: 64×64 or legacy 64×32.
3. **"Select" (Q3).**
   - Apply the look's skin to the **Minecraft account** through Mojang's official API:
     `POST https://api.minecraftservices.com/minecraft/profile/skins` with the session's bearer token.
   - Ask for confirmation first.
   - Only Microsoft accounts can do this.
   - Rate limits apply, and servers may show the new skin only after a rejoin.
   - The token is only ever sent to `api.minecraftservices.com` and is never logged.
   - The cosmetics part of the look is Slate-only (E4).

### E4 — Cosmetics and multiplayer sync

1. **Categories, following the owner's two columns:**
   - **Body:** left arm, right arm, left leg, right leg. For example variants or colour tints.
   - **Pets:** a small companion that follows the player.
   - **Shirts and pants:** overlays composited into a per-player skin texture.
   - **Back:** cape-like items, backpacks, wings.
   - **Hats.**

   "Make a sample one for everything for testing": ship **at least one sample per category**.
2. **Data-driven items:**
   - Each item is `assets/slate_profile/cosmetics/<category>/<id>.json`. It holds: a model (JSON block/item model, the
     format Blockbench exports), a texture, an attachment bone, transforms and colour options.
   - Render them with player render layers. The registration is loader glue: NeoForge's `AddLayers` event, Fabric's
     feature-renderer callback.
   - Pets are separate lightweight renderers, with smoothed following.
3. **Multiplayer:** "ensure it works in multiplayer too".
   - The client sends its loadout to the server as JSON of 8 KB or less.
   - The server relays every loadout to every client that has `slate_profile`. Nothing is persisted beyond the session,
     and the server config has caps.
   - Players without the mod, or on servers without it, see vanilla.
   - **Later:** publish loadouts through the Multiplayer home hub, so friends see cosmetics on servers that lack the
     module.

**Done when:** two clients on one dev server see each other's sample cosmetics; a vanilla client can join that server
and play.

### E5 — Profile picture and bio

1. **Storage** is in the global store:
   - the profile picture is a PNG of at most 128×128 (or the skin's face);
   - the bio is at most 200 characters.
2. **When Multiplayer is also installed,** add an optional social message, `ProfileCard(uuid, bio, pfp blob id)`. The
   picture goes over the blob channel. `PlayerCardPopup` and the D1 labels show both.

### E6 — Account switcher

This is the most security-sensitive item. Follow the Q4 default.

**v1**

- Show the current account read-only in the dropdown.
- If the In-Game Account Switcher mod is installed, open its screen, or list its accounts through its API.

**Native switcher.** Build it only after the owner approves a security design:

- the Microsoft OAuth device-code flow;
- refresh tokens encrypted at rest and never written to the global store in plain text;
- the session replaced through a mixin, with profile keys and chat signing reset.

### F1 — Docs, screenshots and final verification

1. **Update the docs:**
   - `README.md`
   - `DESIGN.md`: modules table, Core API, the switches.
   - `docs/core.md`: the switches table becomes layout, style and containers.
   - `docs/menu.md`, `docs/config.md`, `docs/multiplayer.md`
   - a new `docs/profile.md`
2. **Refresh `docs/screenshots/`** for the whole matrix (§7).
3. **Write a morning report** in the style of the earlier ones (`docs/MORNING-REPORT-*.md`).
4. **Set every row of the §5 table to DONE**, or explain why it is not.

---

## 6. Order and parallelism

```
A3 (spike first) ──────────────┐
A1 ──► A2 ──► A4, A5           ├─► B1, B2, B5, D1, E2
 │                             │
 ├─► B3 (3D part needs A3) ◄───┘
 ├─► B4   (frames, not Stage)
 ├─► C1   (after A2)
 ├─► D2 ──► D1, E5
 └─► E1 ──► E2 (needs A3), E3, E4, E5 ;  E6 after Q4
A6, A7: any time, in parallel
```

- **Milestone 1:** A1, A2, A3, A4, B1 and B2. The Overhaul main menu and Play screen are usable at this point.
- **Milestone 2:** C1, D2, D1 and B3.
- **Milestone 3:** B4, the E packages and B5.
- **Running several agents at once** (the building-run pattern, `docs/building-brief.md`):
  - A1–A4 touch the same Core files, so **one agent at a time** works on them.
  - After milestone 1 merges, each B, C, D and E agent owns only its own module folder.
  - New Core helpers are added only as **new files**, and their names are listed in the report.
  - Builds are serialised (at most 3 concurrently), and only one dev client runs at a time.

## 7. Verification

1. **Builds.** `cd neoforge && ./gradlew.bat build` and `cd fabric && ./gradlew.bat build` must both be green. The
   early window is NeoForge only.
2. **Screenshot matrix.**
   - Run `:dev:runClient -PautoScreens=<slot ids> -PautoSkin=DARK|VANILLA -PautoLayout=… -PautoQuit=true`, adding
     `-PautoWorld` for in-world screens.
   - Cover every slot in §4.2 × every available layout × both styles, on NeoForge.
   - Also run a smaller subset on Fabric.
   - The images land in `neoforge/dev/run/screenshots/`. Read them, and fix anything that looks off.
3. **Install matrix** (R2, R3, R4, R6).
   - Add `-PslateModules=core,menu,…` to the `dev/build.gradle` files so that only the listed modules are on the dev
     runtime. Configs must not "disable" a module; a missing module means its jar is absent.
   - Runs:
     - core
     - core+menu
     - core+config
     - core+multiplayer
     - core+chat
     - core+profile
     - core+menu+multiplayer
     - core+menu+config
     - all
   - Check each run for: no crash; the locked and hidden rules; the style switch reachable from each module's settings;
     layouts clamped correctly.
4. **Server.**
   - Run `:dev:runServer` with and without each module. Joining is never blocked in either direction.
   - Two clients check E4's cosmetics sync and D1's presence.
5. **Full-pack boot.** Boot the DF-like test instance (the repo's `run-pack`, CmlLib harness) to the title screen, then
   scan the logs for Slate errors. Never modify the real DF pack.
6. **Performance.**
   - Stage scenes stay within budget at 1440p and on ultrawide.
   - Rotating the Play ring with 100 worlds does not stutter.
   - The early-window assets stay within their size budget.
7. **Accessibility.**
   - `motion 0` snaps everything.
   - Every Overhaul screen can be used with the keyboard alone.
   - Narration labels are present.
   - Tooltips explain every locked item.

## 8. Risks

| Risk | Mitigation |
|---|---|
| 3D rendering in the GUI (FBO + perspective) × Iris/Sodium × two loaders | A3 starts with a spike and reports before the rest is built |
| Account switching and token handling | Deferred behind Q4; v1 is read-only or uses In-Game Account Switcher |
| Mojang skin API (Microsoft accounts only, rate limits, skin caches) | Confirmation step; clear errors; Slate-only fallback (Q3) |
| Start-up window: no Minecraft renderer, asset size, FML changes, Drippy in the DF pack | Pre-rendered layered frames; size budget; Q11 |
| Xaero's World Map uses its own on-disk format | Spike; JourneyMap tiles and "no map" as fallbacks (Q8) |
| Switching style per screen could flicker during transitions | Resolve the style before the transition snapshot |
| Scope: this is several overnight builds | The milestones in §6 each ship on their own |
| Real scene assets depend on the owner building them in-game | Placeholders plus the scene-capture tool (Q7); never block on them |

## 9. Decisions (with defaults)

Use the default unless the owner has answered. Record answers here.

| # | Question | Default |
|---|---|---|
| Q1 | Which module is "the UI module"? | Slate Menu (`slate_menu`). Keep the id. Change the display name to **"Slate UI"**, matching the owner's wording and the "Install Slate UI" tooltips. Config stays separate as the settings engine |
| Q2 | Id and name of the player customization module | `slate_profile`, "Slate Profile" |
| Q3 | Does selecting a look change the real Minecraft skin? | Yes: upload through Mojang's official API, after a confirmation. Cosmetics stay Slate-only |
| Q4 | Account switcher | v1 is read-only, or opens In-Game Account Switcher when that mod is installed. The native OAuth switcher only after the owner approves its security design |
| Q5 | Default layout for new installs | `CUSTOM` until B1 and B2 ship, then `OVERHAUL`. The setup screen always asks |
| Q6 | The "Create cogwheel" | Create's own cogwheel model when Create is loaded (it is in the DF pack), otherwise Slate's own cogwheel model |
| Q7 | Who builds the real scenes (title, loading, friends)? | The owner, in-game. Agents ship placeholders plus the scene-capture tool and never block on it |
| Q8 | Minimap source for the Play screen | Xaero's World Map first, JourneyMap second, otherwise no map; report if Xaero's format is impractical |
| Q9 | Slate hub in the new world | Not linked from Overhaul; Options › Interface covers it. It stays as the Vanilla layout's Slate button |
| Q10 | Streams | Off by default in both layouts (`multiplayer.json: streams=false`), with the toggle in Options › Multiplayer |
| Q11 | Drippy Loading Screen in the DF pack | Slate's loading overhaul only runs when `fml.toml` names NeoForge's default window; removing Drippy is the owner's call |
| Q12 | World-creation preview | A top-down biome and height map, plus the rotating voxel diorama in Overhaul; defer to the "World Preview" mod when it is installed |

---

## Appendix A — Transcript of the owner's page

Transcribed from handwriting on 2026-09-29. Spelling is normalised. `[Sketch: …]` describes a drawing, and `(?)` marks
an uncertain reading. The page is one long column, and this transcript keeps its order.

**Minecraft Slate mod**

I am going to be repetitive down the line, but here are the most important things to remember:
- The slate mod will allow any combination of vanilla, custom, overhaul layouts (only custom and overhaul if the menu
  does not exist in vanilla) and vanilla or slate style;
- All modules should work independently.
- Following the info provided here: UI module (→ graphical changes to menus), Multiplayer Module (→ friends and more),
  Chat Module (→ chat rework), Player customization module (→ accounts and skin customization).

If a module is not installed:
- if it edits a vanilla menu → nothing
- if it's new → remove the button in custom layout; [a word scribbled out] if overhaul add "install X module" tooltip

Overhaul menus are only with ui module installed.

Remember that the slate style must be there even for vanilla screens if the module that edits them is not installed.

When adding a new menu add overhaul and custom layouts; choose how to make custom layout according to what we have
(if not specified).

It is also very important that we improve the slate style a bit, small things to improve polish.
Also make a compat with Better Inventory changing its color palette to the currently used slate one.
If the ui module is not installed switching between styles (vanilla/slate) should still be possible in each mod's
config (layout is custom anyways bcs no vanilla version exists).

**Main Menu (ui module) – Overhaul**

[Sketch: full-screen frame; big "Minecraft" logo top-left; three chests in a row labelled "Play", "Profile",
"Options"; under each chest a sign: "continue", "Friends", "quit game".]

The main menu will have an animated minecraft scene with 3 chests opening; this will later be made in minecraft, for
now just have the 3 chests with a dark background as the scene; this needs to actually be built in minecraft.

It will be animated with a cinematic zoom-in and the chests opening one by one; shortly after each one opens, a 3D
animated button appears in the opened lid; on the bottom part of each chest there will be a clickable sign also acting
as a button.

The first chest has the Play and Continue buttons; the play button should be a beautiful cube world with clouds all
around it; it does a small zoom and animation when hovering.

The second chest has the Profile and Friends buttons. The profile button will simply show the current player's skin.

The third chest will have the Options and Quit game buttons; the options button should be a Create cogwheel slowly
rotating; it does a faster rotation for a second when hovering it.

(beside the main menu section)
- The mod will allow on first startup to choose between 3 layout options and 2 styles → this is the ui module
  (including custom layout); without it all layouts are vanilla (or custom when no vanilla exists bcs they are new
  non-minecraft menus).
- Layouts: Vanilla, custom, overhauled. Styles: Vanilla, slate. Any combination of these should work.
- Custom layout should be the one we already have and here we make the overhauled.
- Important: make sure everything is well animated and looks super polished.
- Important: even if something is not explicitly asked to be converted to overhauled you should still apply changes
  you see fit to match the overhauled style/layout.
- The modules should be changed/created according to the infos here; every menu listed here needs its custom and
  vanilla version (if a new menu no need for vanilla layout). In the configs you can tweak layout and style of specific
  menus (again, new menus won't have vanilla layout option).
- Again, if needed, change/create modules if needed according to this info and make sure if a module is not installed
  an "install X module to get this feature" tooltip is shown (in the overhauled version; for the custom version just
  have the button not show).
- In the vanilla layouts have the extra buttons be placed while keeping the menus feeling vanilla like other mods
  usually do.
- Make sure all views look like actual minecraft (I mean the ones like in friends screen with an actual world).

**IMPORTANT: REQUIRED POLISH AND TO FILL IN THE GAPS**
Don't be afraid to touch and edit things to have them fit this vision.

**Loading Screen (for mod loading) (ui module) – Overhaul**

[Sketch: "Version" top-left; "Minecraft" logo; "X/Y (ram)" over a tall container at the far left; "Loading text"; a
long conveyor belt along the bottom → "custom 3d animation".]

The idea of the loading screen is to have a minecraft Create-mod-built conveyor belt scene with a final
object/container showing progress of crafting items, and on the left a process (also game built) showing the progress
of the current task; once finished it adds to the final count and resets.
On the very left a vertical lava container (also game built) shows ram usage.
Text about the current task and precise amount of ram used are also present.

**Play Screen (ui module) – overhauled**

[Sketch: left: "Search" and "order" at the top; an elliptical ring of world bubbles, small at the back and large at the
front, with a smaller inner ring and a "+" near the centre; under the front bubble "Name" and "Play !" between < and >
arrows. Right: "Select world"; a "Minimap" box; "world main info"; "Edit  Play  [trash]  …"; "↓ scroll for more world
info".]

The play screen is a complicated one.
Worlds are selected from a rotating ring; they get smaller as they go further back. A config allows to choose how many
worlds you can see at once, but by scrolling new ones appear, allowing to show any number of worlds. Scrolling works by
clicking on a world, the left and right arrows or the mouse wheel.

At the center a second smaller ring shows; when clicking it, the current one shrinks out and the new one expands
replacing it. These are the servers, which work in the exact same way and have an inner ring also working in the same
way, which goes back to worlds with the same animation.

Each world/server looks just like the play button one but each is procedurally generated to look slightly different,
and some defining feature should have servers look differently from worlds.

When a world or server is selected:
- if JourneyMap or Xaero's map is installed, show interactable minimap
- below show main details, and by scrolling show the rest
- below, an edit, play, trash icon and 3 dots for more are present (clone, etc.)

Below the ring world view a play button, world name and small edit icons are present.
On the top left a search bar is present.
A little star allows to favourite worlds/servers and they always appear first.
On the top you can choose the worlds sorting order.

**Profile screen (Player customization module) – Overhauled**

[Sketch 1: "Select skin" top-left, "Search" top-right; a carousel of skins fading towards the sides, the current one in
the middle under a spotlight; "select" and "edit" buttons; "Microsoft account: XXXXXXX v"; a "PFP" box next to
"Bio: …".]
→ edit →
[Sketch 2: left column of 4 slots; the skin in the middle under the spotlight, with a search icon and "Search" under
it; right column of 4 slots; below, a row of 4 boxes and one more box under the first.]

The profile screen brings some fun features.
It allows to create and scroll multiple looks/skins (to create, simply move to a blank one which shows a default MC
Steve/Alex); left and right arrows allow to switch between these. The one currently looked at has a nice looking 3D
shining light, making sure the whole preview feels like a stage; the selected one shines brighter and its light shines
even if not currently looked at.

It adds a Microsoft account switcher with the little v, a pfp selector/swapper and bio; these, including skins, should
save to PC across any instance/version.

When editing a skin the other ones animate out, in place of buttons to edit parts of the body and add accessories.

On the left will appear an upload base skin option along other buttons to edit left/right legs/arms/Pets (?); on the
right buttons for accessories like shirts, pants, back accessories, hats, etc. Make a sample one for everything for
testing and ensure it works in multiplayer too.

Clicking on each of these zooms into that body part and shows the list of available items as rendered in 3D; a little
button on the top right of the player preview allows to disable the zoom entirely.

**Friends Screen (Multiplayer module) – Overhaul**

[Sketch: "Search" and "order" at the top, "+" at the top right; on the left a stack of tabs with "Friends" on top; a row
of player figures standing on a ground line between < and > arrows; "Message" under the row.]

- Drop out the streams features unless toggled in mods configs.

The friends screen will have 4 tabs on the left: Friends, Messages, Groups, Requests.
Same search and order selectors as in worlds.
Shows friends in a MC world, online ones standing, offline sitting; 2 arrows allow to show more left or right; all
friends have name and status shown above.
Below appear buttons for the selected friend.
Clicking on a friend selects them, and will show their info and buttons below.
On the right a + button to add friends is present.
In the groups tab a similar view is shown but with players sitting around a campfire.
I will let you be creative and choose how to polish the rest, including other tabs.
- Actually also have a screenshots tab here.

**World Creation screen (ui module) – Overhauled**

[Sketch: "Create world - Tab" at the top-left; a hatched preview area across the top; three tabs hanging from a line
under it; the tab's content below.]

Shows a view being a preview of the world (a simple button allows to minimize it); has the tabs for world creation
below.

**Options Screen (ui module) – Overhauled**

Has these main tabs:
1 – General, 2 – Video, 3 – Controls, 4 – Audio, 5 – Multiplayer, 6 – Interface,
7 – Language and accessibility (Accessibility), 8 – Customization (mods / resource packs / shaders), 9 – Advanced

[Sketch: "Settings - Tab"; a left column of tabs; a wide box at the top of the content (the in-game view); option rows
in two columns under it.]

All should feel polished and intuitive, leaving advanced configs in the page opening on clicking Advanced, and allowing
horizontal subtabs inside tabs.
General and Video should have a real in-game window above if the player is inside a world; a simple button allows to
minimize it.
- Controls must allow easy access, and controller compat if the mod for it is installed.
- Audio should have device switching and voice chat settings too.
- Multiplayer has chat, friends settings and more.
- Interface will, in addition to current stuff, have specific style and layout toggles for each menu the mod touches,
  and for containers in general (a generalized version of this shows when starting the game for the first time).

Rest should mostly be the same / you can think of the rest yourself.

---

## Appendix B — File map

Paths are relative to `common/<module>/src/main/java/dev/fallingcloud/slate/` unless a path is given in full. Numbers in
brackets are approximate line counts on 2026-09-29.

**Core (`core/`)**

| Area | Files |
|---|---|
| Config | `config/CoreConfig` [90] |
| Theme | `theme/Theme` [109], `theme/Palette` [61], `theme/Skin`, `theme/PixelFont`, `theme/Colors` |
| Swaps and screens | `screen/ScreenSwaps` [67], `screen/ScreenIds` [135], `screen/SlateScreen` [259], `screen/SidebarScreen` [295], `mixin/MinecraftMixin` [52] |
| Restyle | `screen/Reskin` [83], `screen/reskin/ContainerReskin` [124], `screen/reskin/ReskinDraw` [286] (containers at 165–248), and the mixins for AbstractButton, AbstractSelectionList, AbstractSliderButton, AbstractWidget, Checkbox, EditBox, ScreenBackground, TabButton, TabNavigationBar and TooltipRenderUtil |
| Drawing | `gfx/SlateDraw` [541]: `floatingPanel` at 475, `sectionRule` at 496, `accentCap` at 509, `chip` at 516, `vanilla*` at 350–435 |
| Widgets | `widget/*`, including `SlateTabStrip` [424] and `SlateCard` [190] |
| Setup | `client/setup/SlateSetupScreen` [232], `SetupPreview` [234], `mixin/MinecraftSetupMixin` [25] |
| Hub and settings | `client/SlateHubScreen` [192], `client/CoreSettingsScreen` [210] |
| Vanilla-layout buttons | `client/VanillaScreenButtons` [85] |
| Modules | `module/Modules`, `module/SlateModule` |
| Dev-mode editor | `layout/*`, `layout/editor/*`, `client/CustomScreen` |

**Menu, the UI module (`menu/`)**

| Area | Files |
|---|---|
| Bootstrap | `client/MenuClient` [160], `MenuConfig` [96] |
| Title | `client/title/SlateTitleScreen` [287], `NavButton`, `ContinueCard` [190], `AccountCard` |
| Worlds | `client/worlds/SlateWorldsScreen` [537], `WorldCard`, `WorldEntry`, `WorldActions`, `WorldFavorites` |
| Servers | `client/servers/SlateServersScreen` [593], `ServerCard`, `ServerDialog`, `ServerActions`, `ServerPinger`, `LanScanner`, `CommunityServers`, `ServerMeta` |
| Pause | `client/pause/SlatePauseScreen` [226] |
| Options without Config | `client/options/SlateOptionsScreen` [402] |
| Disconnected | `client/disconnect/SlateDisconnectedScreen` |
| Screenshots | `client/screenshots/{SlateScreenshotsScreen,ScreenshotViewer,Screenshots}` |
| Loading | `client/loading/LoadingScreens` [181], `LoadingPreviews`; mixins LevelLoading, Connect, ReceivingLevel, Progress and GenericMessage `ScreenMixin` |
| Start-up window | `neoforge/earlywindow/src/main/java/dev/fallingcloud/slate/earlywindow/`: `SlateEarlyWindow`, `SlateScene`, `Gfx`, `PixelFont`, `SlateLook`, `SceneElement`, `FmlAccess`, `SlateEarlyWindowBootstrapper` |

**Config (`config/`)**

| Area | Files |
|---|---|
| Hub | `hub/ConfigHubScreen` [402] |
| Pages | `page/CategoryPage` [251], `page/*` (including `InterfacePage`, `SkinPage`) |
| Rows and sections | `ui/OptionPageBase` [552], `OptionRow`, `Section`, `SectionHeader` |
| Settings API | `api/SettingsTabs` [55], `SlateConfigApi` |
| Search | `search/*`, `option/KeySearch` |
| Core bindings | `resolver/CoreBindings` |
| Sodium | `sodium/SodiumBridge` |
| Swaps | `SlateConfig.installSwaps()` |

**Multiplayer (`multiplayer/`)**

| Area | Files |
|---|---|
| Friends hub | `client/ui/FriendsHubScreen` [172]; pages `FriendsPage`, `RequestsPage`, `GroupsPage`, `MessagesPage`, `StreamsPage`, `SettingsPage`; `PlayerCardPopup` |
| Social model | `social/{FriendInfo,Presence,GroupInfo,RequestInfo,PlayerRef,SocialMessage}` |
| Client | `client/SocialClient`, `client/MultiplayerClient` |
| Streams | `client/stream/*` |
| Voice | `voice/*` |

**Chat (`chat/`)**

`mixin/ChatScreenMixin`, `mixin/ChatComponentMixin`, `client/ChatScreenUi`, `ChatRenderer`, `ChatSettingsScreen`,
`ChatConfig`.

**Building (`building/`)**

`toolbox/client/ToolboxScreen` (A6.7), `BuildMenuScreen.panel()` (A6.1), `client/settings/BuildingSettingsTab`.

**Language files**

`common/<module>/src/main/resources/assets/<namespace>/lang/en_us.json`. For building, the parts are merged at build
time.

**Harness**

`neoforge/dev/build.gradle` handles `-PautoScreens`, `-PautoSkin`, `-PautoWorld`, `-PautoWorldScreens`, `-PautoFrames`,
`-PautoQuit` and `-PbuildingHarness`. Screenshots go to `neoforge/dev/run/screenshots/`.

**External**

| What | Path |
|---|---|
| Dolly camera rig | `../../Base/dolly` (`SHOTS.md`) |
| BetterInventory | `../../QOL/BetterInventory` (read only) |
| DF pack | read only |
