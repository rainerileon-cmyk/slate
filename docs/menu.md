# Slate Menu (`slate_menu`)

Slate UI: the module that rebuilds Minecraft's menus. It provides two of Slate's three layouts, **Custom** (rebuilt
screens) and **Overhaul** (3D scenes), each in both styles; the layout is chosen on the first-launch setup screen and
can be set per menu (Options > Interface > Menus, or `screens` in `config/slate/core.json`). See `docs/core.md` for
the model and `docs/LAYOUTS.md` for the design.

## Custom layout

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
- **Pause menu** — laid out as the main menu is, over the blurred world: a column of large buttons on one plate
  (Back to game, marked as the one the column is there for; Advancements; Statistics; Options; Open to LAN / server
  links / player reporting; Screenshots; Friends; Mods; Save & quit / Disconnect, red, with confirmation), and
  beside it two cards. The world's card has its picture (the save's own, or the server's), its name and chips for
  the game mode, the day and hour and the difficulty. The player's card has their face and name, how they are doing
  in the HUD's own signs (hearts, armour, hunger, level; nothing in Creative), and where they stand (coordinates
  and biome, left out under reduced debug info); a click on it opens the profile. "Game Menu" stands over both,
  large, with the time played this session at the other end of its line. A narrow window keeps the column alone.
  Vanilla's feedback and bug-report links are gone, and there is no Slate button: Options opens Slate's settings hub.
- **Options** — a sidebar screen whose first page holds the options people actually change (FOV, render
  distance, GUI scale, fullscreen, vsync, max FPS, master volume) and whose other pages open the vanilla
  sub-screens, all restyled by Core; a "Slate settings hub" entry appears when the Config module is
  installed.
- **Screenshots** — a gallery of the `screenshots` folder with async thumbnails, search, sorting, a viewer
  with zoom and pan, and View / Copy / Rename / Delete / Open folder / Share (to friends, when Multiplayer
  is installed).
- **Disconnected** — the reason in a card with Reconnect and Back.
- **Loading screens** — loading a world (with the spawn-area chunk map), joining a server ("Joining &lt;server&gt;",
  its status and Cancel), loading terrain (a portal trip keeps its portal behind the card), saving, and the generic
  progress screen: one floating card with a spinner, the title, the status and a progress bar. Vanilla's screens
  stay and are only drawn over, since the game drives them (`loadingScreens` in `menu.json`, Options › Menu ›
  Loading screens).
- **Start-up window (NeoForge)** — the window NeoForge shows while the game starts, drawn by Slate: a "Minecraft"
  wordmark with the accent cap and the game version, one floating card as the in-game loading screens draw it
  (spinner, what is loading, the latest log line, the accent progress bar with its percentage, up to two sub-steps)
  and a footer with the NeoForge version and memory, in the dark palette with the accent, corner radius and pixel
  font (`pixelFont`; Monocraft when the heading font is off) from `core.json`, all text on the game's pixel grid. It carries on as the game's own loading overlay until the title screen, and is drawn at the window's
  real size (FML's own canvas is a fixed 854 x 480, stretched). It comes from a small companion jar,
  `slate-earlywindow-neoforge` (a service jar FML runs before any mod, like Drippy Loading Screen's early-window jar;
  not a mod), and takes over only while `config/fml.toml` names NeoForge's default window (`fmlearlywindow`) and
  Slate's loading screens are on (custom layout, the dark menu style and `loadingScreens`, read from the settings files
  at start-up); a pack that chose another window keeps it, and fml.toml is never changed. Drippy Loading Screen's mod
  replaces the in-game half with its own overlay, so remove it with its early-window jar. Should drawing fail,
  NeoForge's own screen takes over in Slate's colours.

## Overhaul layout

Scenes instead of panels: real blocks, players and items, lit softly, with a camera that moves. Every scene works in
both styles, and with animations off (`motion` 0) it stands still in its end state.

- **Main menu** — three open chests on a dark floor under the Minecraft logo. In each lid hangs a button: **Play**
  (a cube planet), **Profile** (your head, in the look you wear; it turns a little by itself and nods to the pointer,
  and does not follow it about; nor does any figure on the Profile screen) and **Options** (a large cogwheel: Create's own when that mod is installed). On each
  chest's front is a sign: **Continue** (the last world or server), **Friends**, **Quit game**. The small buttons
  (language, accessibility, Realms, screenshots, mods) sit in the bottom right corner.
- **Play** — worlds and servers as cube planets on a ring, the chosen one in front, with its details beside it; one
  header row with the title and the search above both halves. The ring shows eleven planets (`ringPlanets` in
  `config/slate/menu.json`), smaller and closer together towards the back. When there are more, a whirl of light
  stands at the back of the ring: turning the ring draws the planet that leaves into it and brings the next one out.
  The other ring (servers, or worlds) hangs small in the middle and swaps with a click. Under the ring stand Play,
  the star and a plus that makes a new world (or adds a server); beside the details stand Edit, Open folder, Backup
  (for a server: Refresh, Copy address), delete and the rest.
- **World creation** — the land of the world about to be made, worked out from the chosen world type and seed and
  seen from the air, above three hanging tabs (Game, World, More).
- **Options** — Slate Config's hub: its categories down the left, sub-tabs across the top, the options in two columns,
  and over them a live view of the game while you are in a world.
- **Start-up** — a factory that loads the game, at work from the first frame to the last: a tank of lava stands as
  high as the memory in use, ingots come onto a belt one after the other, a press stamps each into a sheet, and the
  sheets are stacked in a store with a window, the very sheets the press makes, which fills as the loading goes on.
  With Create installed the factory is built from Create's own models and textures, read from its jar when the game
  starts (nothing of Create is shipped with Slate); without it, from Slate's own. The Minecraft logo is the game's.
  On NeoForge it starts in the early window and carries on as the game's loading overlay; on Fabric it is the
  loading overlay.
- **Opening a world** — the world's land from the air, under the sky of the hour it is in that world. The land comes
  up out of the ground around the place you will stand on, ring by ring, as far as the loading has got, with a shaft
  of light on the spot itself. It is the land as you left it: when a world is left, what was loaded around you is
  kept in the world's folder (`slate/land.bin`, a few dozen kilobytes) and shown the next time, with what you built
  on it; what that does not cover, and a world that is new, comes from the world's own generator. Leaving a world
  shows the same land going down again. The world's name, the step, the progress and the chunk map stand over it.
- **Joining a server** — a gate of obsidian on a terrace in the dark, you standing before it. It is cold while the
  server is looked for, takes fire when the server answers and burns brighter with every step (logging in,
  encrypting, joining); when the terrain is being sent the view goes in through it. Leaving a server shows the gate
  going out. The server's name and picture, the step and Cancel stand over it.
- Waits inside a world (another dimension, a respawn) keep the game's own backdrop under the same words.
- **Pause menu** — the world stays behind everything, out of focus. On the left, in the words of the loading
  screens, what is paused (the world's name, large; the mode, the dimension, the day and hour) and the list of what
  can be done, in the heading font on no plate at all: Back to game has the accent's bar beside it from the start,
  the others take it under the pointer. On the right stands the player as they are at that moment (armour, what
  they hold) on the piece of the world they stand on, cut out the way a doll's house is: the ground under their
  feet, and behind them what they were looking at, with no ceiling and nothing between them and the viewer. Under
  it are their hearts, armour, hunger and level, and where they are. A click on the figure opens the profile.

Dev-mode extras: element types `slate_menu:continue_card`, `slate_menu:world_list`,
`slate_menu:server_status`; actions `slate_menu:open_screenshots`, `open_worlds`, `open_servers`,
`play_last`; placeholder `{last_world}`.
