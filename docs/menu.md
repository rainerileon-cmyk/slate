# Slate Menu (`slate_menu`)

Rebuilds Minecraft's main menus in Slate's style, in both skins. The whole set is Slate's **custom layout**
switch (`customLayout` in `config/slate/core.json`, set on the first-launch setup screen or in Slate settings):
with it off, every vanilla screen stays and Core adds a Slate button to vanilla's title and pause screens. With
it on, every screen can still be switched back to vanilla individually in `config/slate/menu.json`.

- **Title screen** — a left column of icon buttons (Singleplayer, Multiplayer, Friends when
  Multiplayer is installed, Screenshots, Mods when a mod list exists, Options, Quit), a **Continue**
  card that resumes the last world or server with one click, an account card with your head and
  session state, a footer with version, loader, mod count and Slate version, the panorama with a
  vignette (dark skin) and an animated splash. Layout adapts to the window size.
- **Worlds** — search, sort (last played, name, size, favourites first), cards or a compact list,
  favourite stars and tags (stored in `config/slate/menu/favorites.json`), a details panel, and
  Play / Edit / Backup / Duplicate / Delete (with confirmation) / Open folder / Recreate. "Create New
  World" opens the vanilla creator, restyled by Core.
- **Servers** — reads and writes the vanilla server list (`servers.dat`, so nothing is lost), with
  favourites and groups, search and sort, live pings (MOTD, players, latency, version compatibility,
  favicon), a **quick connect** field that shows the status of any typed address and joins on Enter,
  recent servers, LAN games, and a **Community** section from `config/slate/menu/community_servers.json`
  that a modpack can pre-fill. A "friends here" chip appears when Multiplayer knows friends are on a
  server. Right-click a server for its menu.
- **Pause menu** — one floating card over the blurred world: the world or server name, the time played
  this session and chips for the game mode, dimension, in-game day and time and difficulty; then the vanilla
  actions grouped by rules (Back to game; Advancements, Statistics; Options, Open to LAN / server links /
  player reporting; Screenshots, Friends; Disconnect / Save & quit with confirmation). Vanilla's feedback and
  bug-report links are gone, and there is no Slate button: Options opens Slate's settings hub.
- **Options** — a sidebar screen whose first page holds the options people actually change (FOV, render
  distance, GUI scale, fullscreen, vsync, max FPS, master volume) and whose other pages open the vanilla
  sub-screens, all restyled by Core; a "Slate settings hub" entry appears when the Config module is
  installed.
- **Screenshots** — a gallery of the `screenshots` folder with async thumbnails, search, sorting, a viewer
  with zoom and pan, and View / Copy / Rename / Delete / Open folder / Share (to friends, when Multiplayer
  is installed).
- **Disconnected** — the reason in a card with Reconnect and Back.

Dev-mode extras: element types `slate_menu:continue_card`, `slate_menu:world_list`,
`slate_menu:server_status`; actions `slate_menu:open_screenshots`, `open_worlds`, `open_servers`,
`play_last`; placeholder `{last_world}`.
