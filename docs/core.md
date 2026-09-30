# Slate Core (`slate`)

The base every other Slate module builds on. On its own it gives you the theme (dark modern or polished
vanilla), restyled vanilla screens, the Slate hub, and the development mode.

## Layouts and styles

Every menu has a **layout** (where things are) and a **style** (what they look like), and the two are independent:
any layout works with any style. The design behind them is `docs/LAYOUTS.md`.

| | What it is | Needs |
|---|---|---|
| Layout **Vanilla** | Minecraft's own screens and button placement; Core adds a **Slate** button to vanilla's title screen and pause menu so Slate stays reachable | nothing |
| Layout **Custom** | Slate's rebuilt screens (title, worlds, servers, pause, options, disconnected, loading) | Slate UI (`slate_menu`) |
| Layout **Overhaul** | Animated 3D scenes built from real Minecraft blocks, players and items: chests for buttons, worlds as cube planets, a factory that loads the game | Slate UI |
| Style **Slate** | The dark modern look: layered panels, an accent colour, pixel-stepped corners | nothing |
| Style **Vanilla** | Vanilla's stone buttons and panels, still animated | nothing |

A menu that does not exist in vanilla (Friends, Profile, Screenshots, the Slate hub) has no Vanilla layout. Without
Slate UI every vanilla menu stays in the Vanilla layout and the new menus use Custom.

- **First launch.** A setup screen appears before the title screen: the layout, the menu style and the container
  style, each with an explanation. Every choice applies the moment it is made; Confirm (or Esc) keeps what is on
  screen. "Run setup again" on the Interface page reopens it.
- **Per menu.** Options > Interface > Menus lists every menu Slate touches with its own layout and style. A menu
  follows the global choice until it is given its own; a layout a menu cannot show right now is greyed out and says
  why (the module that would provide it is missing, or the menu has no such layout).
- **Missing modules.** A feature of a module that is not installed is left out of the Custom and Vanilla layouts. In
  the Overhaul layout its button stays, greyed, with the tooltip "Install <module> to get this feature".
- **Containers** have a style switch of their own (`reskinContainers`), independent of the menu style.

## Skins and theme

- **Dark** (default): warm near-black surfaces, pixel-stepped corners, a single accent colour used for
  primary actions, focus and toggles. Text is off-white, never pure white.
- **Vanilla**: the stone buttons, panoramas and list textures you know, with animated hover lifts, press
  offsets and the same transitions and toasts.

Switch skins in the Slate hub (pause menu / title screen button, or bind "Open Slate hub" under
Controls → Slate), in Slate settings, or with the dev-mode action `slate:set_skin`. Accent presets and a
hex colour field live in Slate settings (the Config module offers the same on its Interface page).

Titles and headings use a pixel font: Pixeloid Sans by default, or Monocraft or Pixelify Sans, chosen under "Pixel
font" in Slate settings or on Slate Config's Interface page. Pixeloid Sans and Monocraft are drawn on Minecraft's own
pixel grid, so they stay crisp at every GUI scale; characters a font lacks fall back to the vanilla font.

`config/slate/core.json`:

| key | default | meaning |
|---|---|---|
| `layout` | `CUSTOM` | the layout every menu shows in unless it has its own: `VANILLA`, `CUSTOM` or `OVERHAUL` |
| `screens` | `{}` | per-menu overrides, keyed by menu id: `{"minecraft:title": {"layout": "OVERHAUL", "style": "VANILLA"}}` |
| `setupDone` | `false` | the first-launch setup was confirmed; delete the key to see it again |
| `skin` | `DARK` | `DARK` or `VANILLA` (the menu-style switch) |
| `accent` | `#D9805E` | accent colour |
| `radius` | `3` | corner step radius (0–4), dark skin only |
| `motion` | `1.0` | animation speed multiplier; `0` disables motion |
| `headingFont` | `true` | titles and headings in the pixel font; off uses the vanilla font everywhere |
| `pixelFont` | `pixeloid` | which pixel font: `pixeloid` (Pixeloid Sans), `monocraft` (Monocraft) or `pixelify` (Pixelify Sans); anything else reads as `pixeloid` |
| `transitions` | `true` | fade between menu screens |
| `uiSounds` | `true` | soft click/tick sounds |
| `toasts` | `true` | Slate notifications |
| `blurInGame` | `true` | blur the world behind in-game menus |
| `devMode` | `false` | enable the layout editor |
| `devGrid` / `devSnap` | `true` / `4` | editor grid and snap size |
| `reskinScope` | `ALLOWLIST` | which screens get restyled: `VANILLA_AND_SLATE`, `ALLOWLIST`, `ALL_NON_CONTAINER`, `NONE` |
| `reskinAllowlist` / `reskinDenylist` | … | package prefixes / mod ids added to or excluded from restyling |
| `reskinContainers` | `true` | inventories and containers get Slate's panel, slot wells, accent hover ring and light labels (the container-style switch, any skin) |

**Inventories and containers** (`reskinContainers`, the container-style switch): the one full-size background
blit a container screen makes becomes Slate's floating panel (hard shadow, hairline border, a lifted top edge)
with a sunken, bevelled well per slot, the hovered slot gets an accent ring instead of vanilla's white square,
and the dark grey titles become light text. It paints with the dark palette and the current accent in either
skin, so it works with the vanilla menu style too. Progress arrows, flames and a mod's own widgets keep drawing
on top. A screen that paints its background in pieces gets a dark veil with the same wells, the creative
inventory's tabs and scroller become Slate's, and the deny-list applies (Create, JEI, Xaero's and FancyMenu
screens by default).

**Better Inventory** draws its screens from sheets of pixel art in a palette of its own, so it gets a palette swap
instead of the panel: with the container style on, its art, its hotbar and the few colours it paints from code take
the colours containers are painted with (the dark palette and the accent in use; the ornaments and whatever is
pointed at take the accent). Both of its looks are handled, the dark one and its Vanilla Style pack, whichever
resource pack the art comes from, and a change of accent shows at once. Nothing of that mod is changed on disk; with
the container style off, or with its screens on the deny-list, it looks as it always did.

## Development mode

Turn on `devMode` (hub toggle or settings) and press **F7** on any menu (or use the hub's "Edit this
screen"). You can move, resize and hide the screen's own buttons, and add your own elements — buttons,
icon buttons, text (with placeholders like `{player}`, `{fps}`, `{mod_count}`), images, panels,
separators, player heads, links — each with a list of **actions**: open a screen, go back, open a URL,
run a command, join a server, quit, open a folder, copy text, play a sound, toggle fullscreen, switch
skin, show a toast, run several actions, or only-if-a-mod-is-loaded. Modules add their own elements and
actions (Menu: continue card, server status; Multiplayer: friends panel).

Layouts are saved per screen in `config/slate/layouts/<screen-id>.json` and apply at every window size
thanks to anchors. Export/import copies a layout as JSON through the clipboard. "New custom screen"
creates a blank screen reachable through `slate:open_screen` with `custom:<id>`. Modpacks ship layouts by
placing the files in that folder (CloudLauncher content defaults work for this).

### Editor controls

Toolbar: Add, Undo, Redo, Grid, Snap, Layers, Properties, Preview, Background, Reset, Export, Import,
New custom screen, Save, Exit. Keyboard: Esc (deselect, then exit prompt), Delete, arrows nudge 1 px
(Shift = 8), Tab next element, Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z, Ctrl+S save, Ctrl+C / Ctrl+V (paste
offsets +8,+8), Ctrl+A, Ctrl+D duplicate, G grid, P preview, L layers. Mouse: click / Shift-click select,
drag to move, handles to resize, drag on empty space to rubber-band, right-click for the context menu,
Alt while dragging disables snapping, Shift constrains to one axis. Anchors are picked automatically from
where you drop an element and can be changed in the properties panel.

## For mod developers

Core is a normal dependency: `dev.fallingcloud.slate.core.*` gives you the widget toolkit
(`SlateButton`, `SlateTextField`, `SlateList`, `SlateScrollPanel`, `SlateModal`, `SlateToasts`, …), the
screen bases (`SlateScreen`, `SidebarScreen`), the theme (`Theme.current()`), loader-agnostic events
(`SlateEvents`), networking (`SlateNetwork`, `core.net.blob` for large transfers), and the platform
seam (`SlatePlatform`). See `DESIGN.md` for the API overview.
