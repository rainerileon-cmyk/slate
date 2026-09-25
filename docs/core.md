# Slate Core (`slate`)

The base every other Slate module builds on. On its own it gives you the theme (dark modern or polished
vanilla), restyled vanilla screens, the Slate hub, and the development mode.

## First launch: the three switches

The first time the game starts with Slate, a **setup screen** appears before the title screen: three switches with
explanations and live previews (a title screen in either layout and style, the inventory in either container
style). Every switch applies the moment it is flipped, so the screen itself changes with the style switch; Confirm
(or Esc) keeps what is on screen. The same three switches live in Slate settings ("What Slate changes"), in the
Slate hub's theme card and on Slate Config's Interface page, and "Run setup again" reopens the screen.

| Switch | `core.json` | On | Off |
|---|---|---|---|
| **Custom layout** | `customLayout` | Slate Menu's rebuilt screens (title, worlds, servers, pause, options, disconnected) replace vanilla's | Vanilla's own screens and button placement stay; Core adds a **Slate** button to vanilla's title screen (right of the accessibility button) and pause menu (in place of the feedback / bug-report row, which is removed) |
| **Custom menu style** | `skin` (`DARK` / `VANILLA`) | The dark modern look for every menu | Vanilla's stone buttons and panels, still animated |
| **Custom container style** | `reskinContainers` | Inventories and containers get Slate's panel, sunken slot wells, an accent hover ring and light labels, with either menu style | Their own textures |

They are independent: a vanilla layout with the dark style restyles vanilla's own screens; a custom layout with the
vanilla style gives Slate's screens stone buttons; the container style never depends on the menu style. Slate's own
rebuilt screens carry no Slate button (their Options lead to Slate's settings and hub).

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
| `customLayout` | `true` | Slate Menu's screens replace vanilla's (the layout switch) |
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
