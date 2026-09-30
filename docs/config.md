# Slate Config (`slate_config`)

One settings screen for the whole game and the whole modpack. With Slate Menu installed, vanilla's
**Options** button opens it directly (without Menu, vanilla's Options screen stays and its sub-screens open
the matching page here). It also opens from the pause menu, the Slate hub, the NeoForge mod list (Config) /
ModMenu, and the dev-mode action `slate_config:open` with a `page` argument.

## Layout

A sidebar of categories; every page shows its sections as **tabs across the top** instead of one long
scroll, and a tab only shows its own rows. A page with a single section has no tab bar. Where a tab holds
several groups, they show as small secondary tabs (one at a time) or as collapsible headers. The tab you
were on is remembered per page (`lastTabs` in `config/slate/config.json`). Tab strips scroll sideways with
arrows and a "more" list when they run out of room. Tab switches fade and rise into place, vertically only
(honouring Slate's animation speed).

Keyboard: Ctrl+Tab / Ctrl+Shift+Tab cycle the sidebar, Ctrl+PgUp / Ctrl+PgDn cycle the top tabs,
Ctrl+Shift+PgUp / PgDn the secondary tabs, `/` or Ctrl+F focuses search.

## Sidebar

- **General**: the page the screen opens with, and one short list: the handful of options players change
  most (FOV, render distance, GUI scale, brightness, fullscreen, frame rate cap, master volume, and the
  world's difficulty while a world is open). Every row also has its proper home on another page.
- **Video**: tabs by topic, never by mod: **Display**, **Graphics**, **Performance**, **Animations &
  effects** and **HUD & extras**. Each tab lists vanilla's rows first, then a collapsible header per mod page
  on Sodium's option model ("Sodium › Quality", "Sodium Extra › Animations", Iris, Reese's, ...) holding the
  rows that belong to that topic (sorted by what each option is about; a page can be split across tabs).
  Vanilla rows Sodium lists itself (render distance, graphics, clouds, ...) show once, as Sodium's row.
  Options that need a reload apply with a debounce, and ones that need a restart show a badge. Without
  Sodium the tabs hold vanilla alone.
- **Audio**: Volume (every sound category, music mute with restore), Output, and Simple Voice Chat's
  entries when it is installed.
- **Controls**: Mouse, Movement (auto-jump, toggle sprint/sneak: their only home), Key binds (unbound
  filter, reset-all, conflict warnings; key categories stay as collapsible headers inside the tab). The
  header search finds a key by its name, the key it is bound to ("left alt", "F5") or its category.
- **Gameplay**: a **General** tab (the world's difficulty with vanilla's lock while a world is open, main
  hand, attack indicator, operator items tab) and every tab other modules add (Slate Building adds
  **Building**): one scrolling list under collapsible headers, no second row of tabs.
- **Multiplayer**: **Online** (server list, Realms, telemetry, hide addresses, Slate Multiplayer), **Chat**
  (chat box, messages, Slate Chat) and **Skin** (skin layers).
- **Customization**: **Mods** (every installed mod as a card; "Configure" opens the mod's own screen when
  it has one, otherwise a native editor for its config files: NeoForge `ModConfigSpec` TOMLs are rendered
  from the spec, other TOML, JSON/JSON5 and `.properties` files open in a line-preserving editor),
  **Resource Packs** (the vanilla pack screen, **Import packs** through a native file dialog straight into
  the folder, the folder, what is active) and **Shader Packs** (only with Iris: every pack in `shaderpacks/`
  with the active one marked, Apply, **Import**, shaders on/off, the folder, and Iris' own screen for
  per-pack options; if Iris' internals are unreachable, Apply opens Iris' screen instead).
- **Interface**: Slate's skin, accent swatches and custom colour, the heading font and its pixel font, motion, restyle (scope, inventories &
  containers, lists), the vanilla GUI options that belong here, Slate Menu, development mode.
- **Accessibility** (id `language_accessibility`): **Language** (searchable list, Apply reloads; font
  options) and **Accessibility** (reading, motion & effects).
- **Favourites** (star any row) and **Presets** (built-in Performance and Quality, plus "save current").
- **Curated pages** a modpack ships, after Presets (see below).

Global **search** filters the tab on screen (showing every match of that page, grouped by tab) and lists hits
everywhere else as "Category › Tab"; picking one opens that page and tab and flashes the row. The header's
reset button resets the **current tab** after a confirmation.

## Opening a page

`SlateConfigApi.openHub(parent, path)`. A path is a sidebar id (`general`, `video`, `audio`, `controls`, `gameplay`,
`multiplayer`, `customization`, `interface`, `language_accessibility`, `favourites`, `presets`),
`category/tab` (`multiplayer/chat`, `multiplayer/skin`, `customization/shaders`, `gameplay/building`), or
`page/tab` for a top tab of a page (`controls/keys`, `video/graphics`). The old ids `chat`, `online`,
`packs`, `mods`, `shaders`, `skin`, `language` and `accessibility` still work. The screenshot harness opens
any of them as `slate_config:hub/<path>`. Vanilla's sub-screens are routed the same way: Video → `video`,
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

`config/slate/config.json` (favourites, presets, remembered tabs, collapsed headers, swap toggle) and
`config/slate/config/pages/*.json`.
