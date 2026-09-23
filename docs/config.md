# Slate Config (`slate_config`)

One settings screen for the whole game and the whole modpack. With Slate Menu installed, vanilla's
**Options** button opens it directly (without Menu, vanilla's Options screen stays and its sub-screens open
the matching page here). It also opens from the pause menu, the Slate hub, the NeoForge mod list (Config) /
ModMenu, and the dev-mode action `slate_config:open` with a `page` argument.

## Layout

A sidebar of categories; every page shows its sections as **tabs across the top** instead of one long
scroll, and a tab only shows its own rows. A page with a single section has no tab bar. Where a tab holds
several groups, they show as small secondary tabs (one at a time) or as headers. The tab you were on is
remembered per page (`lastTabs` in `config/slate/config.json`). Tab strips scroll sideways with arrows and a
"more" list when they run out of room. Tab switches slide and fade (honouring Slate's animation speed).

Keyboard: Ctrl+Tab / Ctrl+Shift+Tab cycle the sidebar, Ctrl+PgUp / Ctrl+PgDn cycle the top tabs,
Ctrl+Shift+PgUp / PgDn the secondary tabs, `/` or Ctrl+F focuses search.

## Sidebar

- **Video**: one tab per mod in Sodium's option model (Sodium, Sodium Extra, Reese's, Iris, ...) with that
  mod's own pages as secondary tabs, rendered in Slate's style; options that need a reload apply with a
  debounce, and ones that need a restart show a badge. A **Vanilla** tab holds Display / World / Effects
  under headers, minus the rows Sodium already shows, with the "show duplicates" toggle at its top. Without
  Sodium, Display / World / Effects are the tabs. There are no "open the other video screen" buttons any more.
- **Audio**: Volume (every sound category, music mute with restore), Output, and Simple Voice Chat's
  entries when it is installed.
- **Controls**: Mouse, Movement, Key binds (unbound filter, reset-all, conflict warnings; key categories
  stay as collapsible headers inside the tab).
- **Gameplay**: a **General** tab (the world's difficulty with vanilla's lock while a world is open, main
  hand, attack indicator, auto-jump, toggle sprint/sneak, operator items tab) and every tab other modules
  add (Slate Building adds **Building**).
- **Interface**: Slate's skin, accent swatches and custom colour, motion, restyle lists, the vanilla GUI
  options that belong here, Slate Menu, development mode.
- **Multiplayer**: **Online** (server list, Realms, telemetry, hide addresses, Slate Multiplayer) and
  **Chat** (chat box, messages, Slate Chat).
- **Customization**: **Mods** (every installed mod as a card; "Configure" opens the mod's own screen when
  it has one, otherwise a native editor for its config files: NeoForge `ModConfigSpec` TOMLs are rendered
  from the spec, other TOML, JSON/JSON5 and `.properties` files open in a line-preserving editor),
  **Resource Packs**, **Shader Packs** (only with Iris: every pack in `shaderpacks/` with the active one
  marked, Apply, shaders on/off, the folder, and Iris' own screen for per-pack options; if Iris' internals
  are unreachable, Apply opens Iris' screen instead) and **Skin** (skin layers).
- **Language & Accessibility**: **Language** (searchable list, Apply reloads; font options) and
  **Accessibility** (reading, motion & effects, input).
- **Favourites** (star any row) and **Presets** (built-in Performance and Quality, plus "save current").
- **Curated pages** a modpack ships, after Presets (see below).

Global **search** filters the tab on screen (showing every match of that page, grouped by tab) and lists hits
everywhere else as "Category › Tab"; picking one opens that page and tab and flashes the row. The header's
reset button resets the **current tab** after a confirmation.

## Opening a page

`SlateConfigApi.openHub(parent, path)`. A path is a sidebar id (`video`, `audio`, `controls`, `gameplay`,
`interface`, `multiplayer`, `customization`, `language_accessibility`, `favourites`, `presets`),
`category/tab` (`multiplayer/chat`, `customization/shaders`, `gameplay/building`), or `page/tab` for a top
tab of a page (`controls/keys`, `video/vanilla`). The old ids `chat`, `online`, `packs`, `mods`, `shaders`,
`language` and `accessibility` still work. Vanilla's sub-screens are routed the same way: Video → `video`,
Music & Sounds → `audio`, Controls → `controls`, Key Binds → `controls/keys`, Chat → `multiplayer/chat`,
Online → `multiplayer/online`, Language → `language_accessibility/language`, Accessibility →
`language_accessibility/accessibility`, and Sodium's own screen → `video` (`swapVanillaScreens` in
`config/slate/config.json` turns that off). The header's first button always opens vanilla's own Options
screen (skin customization, telemetry, credits).

## Adding a tab from another module

`dev.fallingcloud.slate.config.api.SettingsTabs.register(category, new Tab(id, title, icon, sections, order))`
from a class only loaded when `slate_config` is present. Categories: `gameplay`, `interface`, `multiplayer`,
`customization` (a tab of the category; its sections become secondary tabs), and any other page id (the
tab is appended to that page's top tabs). The sections supplier runs on every rebuild. Register a reload
hook (`SlateConfigApi.registerReloadHook`) so changes apply live.

## Curated pages

`config/slate/config/pages/*.json`, one sidebar entry per file, each section a top tab. Each option row is
a `path`: `optionsTxt:<key>`, `key:<binding>`, `json:<file>:<pointer>`, `toml:<modid>:<file>:<section.key>`,
`props:<file>:<key>`, `slate:<module>:<key>`, `sodium:<id>`, `iris:enabled|pack|open`, `voicechat:<entry>`.
Nothing ships in the folder any more: the old "DF pack" page is gone, and an existing `pages/df.json` (id
`df`) is renamed to `df.json.retired` once (`dfPageRetired` in `config.json`).

## Files

`config/slate/config.json` (favourites, presets, remembered tabs, swap toggle) and
`config/slate/config/pages/*.json`.
