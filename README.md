# Slate

A suite of mods that makes Minecraft's interface feel modern. Dark grey/black "modern but pixel"
look by default, with a second **vanilla** skin that keeps stone buttons and panoramas but is just as
polished and animated. Minecraft 1.21.1, **NeoForge and Fabric** from one shared source tree.

| Module | Mod id | What it does |
|---|---|---|
| Core | `slate` | Theme engine (dark + vanilla skins, accent colours), widget toolkit, animations, screen transitions, restyling of vanilla screens, the **development mode** layout editor (edit any menu: move/hide/add buttons, labels, images, panels with actions), Slate hub |
| Menu | `slate_menu` | Rebuilt title, worlds, servers, pause, options screens; favourites, search, live pings, quick connect, community servers, screenshot gallery |
| Multiplayer | `slate_multiplayer` | Friends, friend groups, presence, DMs and group chats with media, invites, screen sharing, Simple Voice Chat integration; a social hub on any server that has it |
| Chat | `slate_chat` | In-game chat rebuilt: channels, grouped messages with heads, images/GIFs/voice clips/video, emotes, mentions, history and search |
| Config | `slate_config` | One unified settings screen: video (Sodium included), audio, controls, chat, interface, multiplayer, every mod's config, curated modpack pages, search, favourites, presets |
| Building | `slate_building` | Every block's shapes in one family (stairs, slabs, vertical slabs, walls, steps, panels, …) swapped on an Alt wheel, placement ghost, an R build menu with 20 building modes (fill, walls, sphere, replace, copy/paste, move, mirror, …) unlocked by tiered tools in a Builder's Toolbox, chisel variants, undo/redo; one-material-unit economy, no dupes. Adds blocks, so the server needs it too ([docs](docs/building.md)) |

Teams and Quests modules are planned (ids reserved).

## Building

```bash
cd neoforge && ./gradlew.bat build      # jars in neoforge/<module>/build/libs
cd fabric   && ./gradlew.bat build      # jars in fabric/<module>/build/libs
```

Dev client with every module: `./gradlew.bat :dev:runClient` in either loader directory
(`:dev:runServer` for a dedicated server). Soft dependencies (Sodium, Simple Voice Chat, MCEF, Iris)
compile against vendored jars in `MinecraftMods/Source/libs` (or `-Pslate.libs=<dir>`).

## Layout

- `DESIGN.md` — the design contract (module briefs, Core API, visual language).
- `common/<module>/src/main` — shared code and assets (loader-agnostic).
- `neoforge/`, `fabric/` — loader projects; each `<module>/src/main` holds only that loader's glue.
- `docs/` — user docs per module, the agent brief.
- `tools/` — icon/emote generators, screenshot and install scripts.

## Configuration

Everything lives under `config/slate/`: `core.json` (skin, accent, motion, dev mode, restyle scope),
`menu.json`, `multiplayer.json`, `chat.json`, `config.json`, `building.json` (client building preferences),
`building-server.json` (server building rules, synced to players), `building-chisel.json` (chisel groups),
`layouts/<screen>.json` (dev-mode layouts), `config/pages/*.json` (curated settings pages). Modpacks ship
defaults by placing files there.

License: MIT.
