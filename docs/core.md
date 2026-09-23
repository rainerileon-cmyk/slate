# Slate Core (`slate`)

The base every other Slate module builds on. On its own it gives you the theme (dark modern or polished
vanilla), restyled vanilla screens, the Slate hub, and the development mode.

## Skins and theme

- **Dark** (default): warm near-black surfaces, pixel-stepped corners, a single accent colour used for
  primary actions, focus and toggles. Text is off-white, never pure white.
- **Vanilla**: the stone buttons, panoramas and list textures you know, with animated hover lifts, press
  offsets and the same transitions and toasts.

Switch skins in the Slate hub (pause menu / title screen button, or bind "Open Slate hub" under
Controls → Slate), in Slate settings, or with the dev-mode action `slate:set_skin`. Accent presets and a
hex colour field live in Slate settings (the Config module offers the same on its Interface page).

`config/slate/core.json`:

| key | default | meaning |
|---|---|---|
| `skin` | `DARK` | `DARK` or `VANILLA` |
| `accent` | `#D9805E` | accent colour |
| `radius` | `3` | corner step radius (0–4), dark skin only |
| `motion` | `1.0` | animation speed multiplier; `0` disables motion |
| `headingFont` | `true` | Pixelify Sans for headings |
| `transitions` | `true` | fade between menu screens |
| `uiSounds` | `true` | soft click/tick sounds |
| `toasts` | `true` | Slate notifications |
| `blurInGame` | `true` | blur the world behind in-game menus |
| `devMode` | `false` | enable the layout editor |
| `devGrid` / `devSnap` | `true` / `4` | editor grid and snap size |
| `reskinScope` | `ALLOWLIST` | which screens get restyled: `VANILLA_AND_SLATE`, `ALLOWLIST`, `ALL_NON_CONTAINER`, `NONE` |
| `reskinAllowlist` / `reskinDenylist` | … | package prefixes / mod ids added to or excluded from restyling |

Container screens (inventories, chests, machines, JEI) are never restyled.

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
