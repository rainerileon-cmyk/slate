# Overnight run — Slate (2026-09-22 → 23)

**Slate is built, installed in the DF pack, and the pack boots with it.** Five mods, NeoForge + Fabric
1.21.1, ~45k lines, both skins screenshot-verified. Source: `Coding\MinecraftMods\Source\UI\Slate`
(git, branch `master`). Docs: `Slate\README.md`, `Slate\docs\*.md`, screenshots in `Slate\docs\screenshots`.

## What is installed in DF right now
`packs\df\game\mods\slate-{core,menu,multiplayer,chat,config}-neoforge-1.21.1-1.0.0.jar`.
`chatterbox-neoforge-1.21.1.jar` was renamed to `.disabled` (Slate Chat replaces it; both loaded = two chats).
Boot test with the CmlLib harness (the way CloudLauncher launches): title screen reached, no Slate errors.
CloudLauncher's `mods.json` was NOT touched (it rewrites the file itself; the jars just show up as `file:` entries).

Things you will notice / may want to toggle:
- The title screen, worlds, servers, pause, options and a new Screenshots gallery are Slate's. Every screen
  has an off switch in `config/slate/menu.json` if you prefer a vanilla one.
- FancyMenu is still enabled in DF; it registers its layer on Slate's title screen too. If anything looks
  doubled, disable FancyMenu (Slate's dev mode covers the same use case) or Slate Menu's title toggle.
- `chat_heads` is also still enabled; Slate Chat draws heads itself, so you may see two. Disable one.
- Two config screens now claim Options → Video Settings: Slate Config routes vanilla's and Sodium's video
  screens to its hub (`config/slate/config.json: swapVanillaScreens`).
- Controlify's Sodium-compat mixin logs a failure at boot (`FlatButtonWidgetMixin … setLabel`); that is
  Controlify vs Sodium 0.8.12, pre-existing, harmless.

## Modules
- **Core** — dark modern skin (Claude-app-like, darker, accent `#D9805E`, 12 presets) + polished vanilla
  skin, widget toolkit, animations, screen transitions, restyle of every vanilla menu and known config
  screens (never inventories), Slate hub, **dev mode** (F7 or hub toggle): FancyMenu-style editor — move,
  resize, hide vanilla buttons; add buttons/text/images/panels/heads/links with actions (open screen, URL,
  command, join server, quit, folder, copy, sound, fullscreen, skin, toast, sequences, conditionals);
  undo/redo, layers, properties, snapping, guides, export/import, custom screens. Layouts in
  `config/slate/layouts/`, ship them with the pack.
- **Menu** — title (nav column, Continue card, account card, friends panel), worlds (search/sort/favourites/
  tags/backup/duplicate/details), servers (favourites, groups, live pings, quick connect, recent, LAN,
  community list, "friends here"), pause, options hub, screenshots gallery + viewer, disconnected screen.
- **Multiplayer** — friends/requests/groups/presence/DMs+group chat with images/invites/screen sharing
  (PiP + full viewer)/Simple Voice Chat integration. A **social hub** runs on any server with the module;
  set `homeHub` in `config/slate/multiplayer.json` (e.g. your DF server `host:25580`, server side
  `multiplayer-server.json: enabled=true`) and it works from the title screen and across servers.
  Protocol tested end to end with a scripted client (50 checks) + real client/dedicated server.
- **Chat** — Chatterbox rebuilt: grouped messages with heads, channels (Global/System/DMs), images, GIFs
  (Tenor key in `config/slate/media.json`), voice clips (needs SVC), video links (MCEF), emotes, mentions,
  history, search, typing indicators.
- **Config** — unified settings hub: video incl. Sodium's pages natively, audio, controls (keybinds +
  conflicts), chat, interface, multiplayer, accessibility, language, packs, every mod's config (own screen /
  NeoForge TOML spec / JSON / properties editors), curated pages — `df.json` ships 5 sections / 55 options
  built from your real DF config files — plus search, favourites, presets.

## Not verified live (compile-checked only)
Sodium page, Simple Voice Chat (speaking, groups, voice clips), Iris section, Fabric runtime (Fabric jars
build and remap; never launched), screen sharing in a real multi-client session (tested with two clients
on one PC), the Menu↔Multiplayer "friends here"/share hooks through a real friend.

## How the night went
Skeleton + Core API by hand, then seven parallel agents in git worktrees (icons, core polish + reskin,
editor, menu, multiplayer, chat + media stack, config). The account limit cut six of them off at 01:30;
all were resumed with context intact after the reset and finished. Integration: merges were conflict-free
except one mixin list; both loaders green; a built-in screenshot harness (`-PautoScreens=… -PautoWorld=true
-PautoSkin=VANILLA`) captured 28 screens across both skins for review. Two seams were fixed by hand
(chat↔multiplayer DM bridge accessors, Sodium screen swap by class name).

## Next steps when you are up
1. Launch DF, look at the title screen and open the hub (pause → Slate). Try dev mode: toggle it in the
   hub, press F7 on the title screen.
2. Decide on FancyMenu / chat_heads (see above).
3. To use friends across servers: enable the hub on your DF server (`config/slate/multiplayer-server.json`)
   and put its address in your client `multiplayer.json`.
4. Teams and Quests modules are the next build (ids reserved: `slate_teams`, `slate_quests`).
