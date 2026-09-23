# Slate Config (`slate_config`)

One settings screen for the whole game and the whole modpack. Open it from Options ("Slate settings
hub"), the pause menu, the Slate hub, the NeoForge mod list (Config) / ModMenu, or the dev-mode action
`slate_config:open` with a `page` argument.

## Pages

- **Video** — every vanilla video option, plus Sodium's option model when Sodium is installed
  (Quality / Performance / Advanced and the pages other mods add through Sodium's API, such as Iris
  and Sodium Extra), rendered in Slate's style; options that need a reload apply with a debounce, and
  ones that need a restart show a badge. Sodium's own video-settings screen and the vanilla Video,
  Sound, Controls, Key Binds, Chat, Language, Accessibility and Online screens are routed here
  (`swapVanillaScreens` in `config/slate/config.json` turns that off). An Iris section shows the current
  shader pack and opens Iris' pack screen.
- **Audio** — every sound category, music mute with restore, output device, Simple Voice Chat's
  entries live when it is installed.
- **Controls** — mouse and movement options, then every key binding by category with search, conflict
  warnings, an "unbound" filter and reset-all.
- **Chat**, **Interface** (Slate's skin, accent swatches and custom colour, motion, restyle lists),
  **Multiplayer**, **Accessibility**, **Language** (searchable, applies and reloads), **Resource packs**.
- **Mods** — every installed mod as a card with icon, version and description. "Configure" opens the
  mod's own screen when it registered one, otherwise a native editor for its config files: NeoForge
  `ModConfigSpec` TOMLs are rendered from the spec (ranges become sliders, enums dropdowns), other TOML,
  JSON/JSON5 and `.properties` files open in a line-preserving editor. "Open in external editor" is
  always there.
- **Curated pages** from `config/slate/config/pages/*.json`. Each option row is a `path`:
  `optionsTxt:<key>`, `key:<binding>`, `json:<file>:<pointer>`, `toml:<modid>:<file>:<section.key>`,
  `props:<file>:<key>`, `slate:<module>:<key>`, `sodium:<id>`, `iris:enabled|pack|open`,
  `voicechat:<entry>`. The shipped `df.json` (copied on first run) has five sections built from the DF
  pack's real config files: Visuals, Performance, Immersion, Gameplay & HUD, Social.
- **Favourites** (star any row), **Presets** (built-in Performance and Quality, plus "save current"),
  global **search** (`/` or Ctrl+F) that jumps to the page and highlights the row, per-row and per-page
  reset with confirmation.

## Files

`config/slate/config.json` (favourites, presets, swap toggle) and `config/slate/config/pages/*.json`.
Modpack authors ship curated pages by adding files to that folder.
