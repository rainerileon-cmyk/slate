# Slate — design contract

Slate is a suite of mods that makes Minecraft's UI feel modern: dark-grey/black "modern but pixelated"
by default (think the Claude desktop app, a shade darker; Essential-mod-like, but better), with a
second **vanilla** skin that keeps stone buttons and dirt-free panels but is just as polished and
animated. Minecraft 1.21.1, **NeoForge + Fabric** from one shared source tree. Built for Leon's DF
modpack (CloudLauncher), but every module is a standalone mod.

This file is the contract every module (and every agent working on one) codes against. If you need
something Core does not offer, add it to Core in a **new file** and note it in your report; do not
change existing Core signatures.

## Modules (one jar each, per loader)

| Module | mod id | package | side | depends on |
|---|---|---|---|---|
| Core | `slate` | `dev.fallingcloud.slate.core` | both | — |
| Menu | `slate_menu` | `dev.fallingcloud.slate.menu` | client | core |
| Multiplayer | `slate_multiplayer` | `dev.fallingcloud.slate.multiplayer` | both | core |
| Chat | `slate_chat` | `dev.fallingcloud.slate.chat` | both | core, multiplayer |
| Config | `slate_config` | `dev.fallingcloud.slate.config` | client | core |
| Building | `slate_building` | `dev.fallingcloud.slate.building` | both (**required on both**: adds blocks/items) | core; config, jei, betterinventory optional |
| Teams | `slate_teams` | — | — | **later** (id reserved) |
| Quests | `slate_quests` | — | — | **later** (id reserved) |

Display names: "Slate", "Slate Menu", "Slate Multiplayer", "Slate Chat", "Slate Config", "Slate Building".
Version `1.0.0`, MIT, author `falling_colud`, maven group `dev.fallingcloud`.

Hard rules:
- **No split packages.** Every class of a module lives under that module's package and nowhere else
  (NeoForge puts each jar in its own module; a package that exists in two jars is a boot failure).
  Loader-specific code goes in `<module pkg>.neoforge.*` / `<module pkg>.fabric.*`.
- **Mixins** live in `<module pkg>.mixin` with config `slate_<module>.mixins.json` (core: `slate.mixins.json`).
  Never put a plain helper class inside a mixin package (Mixin refuses to load it at first call).
  Only vanilla classes are mixin targets in `common/`; loader classes may be targeted only from the
  loader tree. Give cosmetic injections `require = 0`.
- **Common code sees only**: vanilla Minecraft (Mojang mappings), Mixin + MixinExtras, Gson, Netty,
  SLF4J, JetBrains annotations, and Slate's own modules. Never `net.neoforged.*`, `net.fabricmc.*`,
  or `javax.annotation.*` (absent on Fabric — use `org.jetbrains.annotations`).
- Soft dependencies (Simple Voice Chat, MCEF, Sodium, Iris, ModMenu, Cloth, YACL…) are `compileOnly`
  and every call into them sits in a class that is only loaded after `SlatePlatform.get().isModLoaded(id)`
  passed. A missing/updated soft dep degrades a feature, never crashes.
- Joining must never be blocked by Slate's presence or version on either side. NeoForge:
  `displayTest = "IGNORE_ALL_VERSION"`, payloads registered optional. Fabric: check
  `ServerPlayNetworking.canSend` before sending to a client.
- Config files are Slate's own JSON (Gson) under `config/slate/`, loader-agnostic:
  `core.json`, `menu.json`, `multiplayer.json`, `chat.json`, `config.json`,
  `layouts/<screen id with ':' -> '.'>.json`, `menu/favorites.json`, `multiplayer/friends-cache.json`, …
  Reads tolerate missing keys (defaults); writes are atomic (write temp, move).
- Java 21, UTF-8, no BOM. `final` locals/params are house style but not required. Javadoc the *why*.

## Repository layout

```
Slate/
  DESIGN.md                    this file
  common/<module>/src/main/{java,resources}     shared source (added to both loader projects' source sets)
  neoforge/                    Gradle multi-project (ModDevGradle): core, menu, multiplayer, chat, config, dev
  fabric/                      Gradle multi-project (fabric-loom):  core, menu, multiplayer, chat, config, dev
  tools/                       icon/atlas generators (python), verification scripts
  docs/                        user-facing docs per module
```

Build: `cd neoforge && ./gradlew build` and `cd fabric && ./gradlew build`. Jars land in
`<loader>/<module>/build/libs/slate-<module>-<loader>-1.21.1-<ver>.jar`. Dev client: `./gradlew :dev:runClient`
in either loader dir (loads every module).

## Core API (package `dev.fallingcloud.slate.core`)

### Bootstrap
- `Slate` — `MOD_ID`, `LOGGER`, `init()` (idempotent; every module calls it first), `config()` → `CoreConfig`.
- `module.SlateModule` — interface a module implements: `id()`, `displayName()`, `init()` (both sides,
  during mod construction), `initClient()` (client, after `init`), `hubEntries()` (buttons for the
  Slate hub screen), `actions()` (dev-mode actions it contributes). `module.Modules.register(m)` /
  `Modules.isLoaded(id)` / `Modules.all()`.

### Platform (`core.platform`)
- `SlatePlatform.get()` (ServiceLoader `META-INF/services/dev.fallingcloud.slate.core.platform.SlatePlatform`):
  `loader()` → `Loader.NEOFORGE|FABRIC`, `isModLoaded(id)`, `modInfo(id)`, `allMods()`,
  `configDir()`, `gameDir()`, `isClient()`, `isDedicatedServer()`, `isDevelopmentEnvironment()`,
  `otherModConfigScreen(modId, parent)` (client; another mod's own config screen if it registered one),
  `openUri(uri)`.
- `ModInfo(id, name, version, description, authors, iconPath)`.

### Events (`core.event`)
`SlateEvents` — tiny typed event bus fed by the loader layer. Handlers run on the owning thread.
`CLIENT_TICK_END`, `SCREEN_INIT_POST(Screen)` (after widgets exist), `SCREEN_RENDER_POST(Screen, GuiGraphics, mouseX, mouseY, partial)`,
`HUD_RENDER(GuiGraphics, partial)`, `CLIENT_JOINED_SERVER`, `CLIENT_LEFT_SERVER`,
`SERVER_STARTED(MinecraftServer)`, `SERVER_STOPPING(MinecraftServer)`, `SERVER_TICK_END(MinecraftServer)`,
`PLAYER_JOINED(ServerPlayer)`, `PLAYER_LEFT(ServerPlayer)`, `KEY_PRESSED(key, scancode, mods) -> boolean consumed` (client, screens excluded).
`SlateKeys.register(KeyMapping)` — queued; the loader layer flushes it into the real registration.

### Networking (`core.net`)
`SlateNetwork.get()`: `register(Type<T>, StreamCodec<? super RegistryFriendlyByteBuf,T>, Flow, PayloadHandler<T>)`
(call during `init()`, both sides; `Flow.C2S|S2C|BOTH`), `sendToServer(payload)`,
`sendToPlayer(ServerPlayer, payload)`, `canSendToPlayer(ServerPlayer, Type)` (the client has the channel),
`serverHasChannel(Type)` (client side). Handler gets `(payload, NetContext)`; `NetContext.sender()` is the
`ServerPlayer` for C2S, `NetContext.isClient()` for S2C; always on the receiving side's main thread.
Max payload on the wire ~2 MiB but the singleplayer pipe has NO limit — never "verify" sizes in SP.
Big data goes over `core.net.blob` (`BlobStart/BlobChunk/BlobEnd`, 24 000-byte chunks, sender pacing
3 chunks/tick, server bookkeeping relay — lifted from Chatterbox).

### Theme (`core.theme`)
- `Skin { DARK, VANILLA }`. `Theme.current()` → `skin()`, `palette()`, `radius()` (pixel-step corner
  radius, 0 on vanilla), `motion()` (0 = animations off, 1 = default), `headingFont()` (a `Style` with the
  Pixelify Sans TTF font, or vanilla when disabled). `Theme.reload()` after config changes; listeners via
  `Theme.onChange(Runnable)`.
- `Palette` — ARGB ints: `bg, bg2, surface, surfaceHover, surfaceActive, border, borderStrong, text,
  textMuted, textDim, accent, accentHover, accentText, danger, success, warning, overlay, shadow`,
  plus `with(...)` and `AccentPreset` list (named accents). Dark default: bg `#161615`, bg2 `#1B1B1A`,
  surface `#222221`, hover `#2A2A28`, active `#323230`, border `#33332F`, strong `#45443F`, text `#ECEAE4`,
  muted `#A19F97`, dim `#6E6C66`, accent `#D9805E`, danger `#E5484D`, success `#5CB176`, warning `#E0A458`.
  Vanilla palette: text white, muted `#A0A0A0`, accent `#5CB176`-ish green, surfaces are drawn with sprites.
- `Colors` helpers: `withAlpha`, `lerp`, `mix`, `brighten`, `fromHex`, `toHex`, `readable(fg,bg)`.

### Drawing & animation (`core.gfx`)
- `SlateDraw`: `panel(g, x,y,w,h, fill, border)`, `pixelRound(g, x,y,w,h, color, radius)` (stepped
  corners), `outline`, `hgradient/vgradient`, `shadow`, `icon(g, Icon, x,y, size, color)`,
  `text/textCentered/textRight` with palette colours, `truncate(font, text, width)`, `scissor push/pop`,
  `vanillaButton(g, x,y,w,h, hover, active, alpha)`, `vanillaPanel`, `vanillaList` (sprite based).
- `Anim` — a float that eases toward a target in real time: `Anim(initial)`, `set(target)`,
  `snap(v)`, `get()`, `get(partial)`; `Ease` (linear, outCubic, inOutCubic, outQuint, outBack, outExpo, spring);
  `Clock.nowMs()`, `Clock.frameDelta()`. Honour `Theme.motion()` (0 → snap).
- `Icons` / `Icon` enum — 16×16 white glyphs in `assets/slate/textures/gui/icons.png` (generated by
  `tools/icons.py`; add glyphs there, regenerate, extend the enum). Tinted on draw.
- `Fonts.HEADING` style, `Fonts.MONO`? (no) — headings use Pixelify Sans via a font provider
  (`assets/slate/font/heading.json`), body text uses vanilla.

### Widgets (`core.widget`) — all extend `SlateWidget extends AbstractWidget`
Every widget renders both skins (`renderDark` / `renderVanilla`), animates hover/press/focus with `Anim`,
supports `tooltip(Component)` through Slate's tooltip, `enabled`, `visible`, keyboard focus and narration.
`SlateButton` (variants `PRIMARY, SECONDARY, GHOST, DANGER`, optional `Icon`, sizes S/M/L),
`SlateIconButton`, `SlateToggle` (switch), `SlateCheckbox`, `SlateSlider` (int/double, formatter),
`SlateTextField` (placeholder, icon, clear button, `onChange`, validation colour), `SlateSearchField`,
`SlateDropdown<T>`, `SlateSegmented<T>`, `SlateTabs`, `SlateLabel` (HEADING/BODY/MUTED/CAPTION),
`SlateSeparator`, `SlateProgress`, `SlateSpinner`, `SlateBadge`, `SlateAvatar` (player head, online dot),
`SlateCard` (container with hover lift), `SlateScrollPanel` (children + smooth scroll + scrollbar),
`SlateList<T>` (virtualised rows, selection, `RowRenderer`), `SlateKeybindButton`, `SlateColorField`
(hex + swatch + picker popup), `SlateContextMenu`, `SlateModal` (dialog: title, body, buttons),
`SlateToasts` (top-right stack, `SlateToasts.show(title, body, Icon)`), `SlateTooltips`.
Layout helpers in `core.layout.ui`: `Row`, `Column`, `Grid` (gap, padding, alignment), `Anchor`
(`TOP_LEFT … BOTTOM_RIGHT, CENTER`) + `Rect`.

### Screens (`core.screen`)
- `SlateScreen extends Screen`: dark bg (solid `bg` + subtle vignette; in-world: blurred + `overlay`),
  header (title, optional back button, optional right-side actions), body area (`contentRect()`),
  entrance animation (fade + 6 px slide), ESC/back handling, modal stack, toasts, tooltips, and the
  dev-mode edit button. Subclass, override `build()` (called on init & resize) and `renderContent`.
- `SidebarScreen extends SlateScreen`: left nav (icons + labels, collapsible) + page area; pages are
  `SidebarPage` (title, icon, `build`, `render`). Used by Config hub, Multiplayer hub, Menu's Options.
- `ScreenIds.of(Screen)` → stable id (`minecraft:title`, `minecraft:select_world`, `minecraft:multiplayer`,
  `minecraft:options`, `minecraft:pause`, `minecraft:create_world`, `slate_menu:title`, …; unknown →
  `<guessed modid>:<ClassSimpleName>`). `ScreenIds.isContainer(Screen)`.
- `Transitions` — cross-screen fade (120 ms) driven from `MinecraftMixin.setScreen`.
- `Reskin` — the generic restyle of vanilla widgets/backgrounds/tooltips/lists on screens in scope
  (`CoreConfig.reskinScope: VANILLA_AND_SLATE | ALLOWLIST | ALL_NON_CONTAINER | NONE`). Container
  screens (`AbstractContainerScreen`) follow `reskinContainers` instead (`screen.reskin.ContainerReskin`: panel,
  slot wells, accent slot highlight, light labels, in either skin).

### Dev mode / layouts (`core.layout`)
The FancyMenu-like editor, but simpler. Toggle with the keybind (default `F7`) or `CoreConfig.devMode`.
- `ScreenLayout` (JSON): `screen` id, `hidden` vanilla widgets, `moved` vanilla widgets
  (`WidgetKey` → rect+anchor), `elements` (custom: `id, type, anchor, x, y, w, h, props, actions`), `background`.
  `WidgetKey` = translation key of the widget's message (or literal text) + occurrence index; stable across
  reorders.
- Element types: `button`, `icon_button`, `label`, `image`, `panel`, `separator`, `player_head`, `link`,
  `spacer`, plus module-registered types (Menu: `world_card`, `server_status`; Multiplayer: `friends_panel`).
- `Actions` registry: `open_screen(id)`, `back`, `open_url`, `run_command(cmd, side)`, `join_server(address)`,
  `open_world(folder)`, `quit`, `open_folder(path)`, `copy_text`, `play_sound`, `set_option(key, value)`,
  `toggle_fullscreen`, `set_skin`, `sequence`, `if_mod_loaded`, plus module actions. Each action declares
  an argument schema so the editor renders a form for it.
- `Placeholders`: `{player}`, `{uuid}`, `{mc_version}`, `{loader}`, `{mod_count}`, `{fps}`, `{time}`,
  `{date}`, `{friends_online}`, `{server}`, `{world}`.
- `LayoutStore` (load/save/list/reset, file watch), `LayoutApplier` (runs at `SCREEN_INIT_POST`),
  `EditorOverlay` (selection, drag, resize handles, arrow nudge, snap grid, alignment guides, multi-select,
  undo/redo, copy/paste, layers list, properties panel with per-type forms, add-element palette,
  save/reset/preview/export/import, "new custom screen"). Custom screens: `CustomScreen(id)` reachable
  through `open_screen("custom:<id>")`.

### Config (`core.config`)
`JsonConfig<T>` (Gson, atomic write, defaults on missing fields, `load()/save()/get()`), `CoreConfig`
fields: the three switches `customLayout` (Menu's screens replace vanilla's; off = vanilla screens plus a Slate
button on vanilla's title and pause screens, added by `client.VanillaScreenButtons`), `skin` (`DARK`/`VANILLA`,
the menu style) and `reskinContainers` (the container style, painted with `Theme.containerPalette()` in either
skin), `setupDone` (the first-launch `client.setup.SlateSetupScreen` ran; `MinecraftSetupMixin` puts it at the
head of the start-up screen chain until then), `accent` (hex), `motion` (0–2), `headingFont` (bool), `devMode`,
`reskinScope`, `reskinAllowlist` (mod ids), `transitions`, `uiSounds`, `toasts`. The Config module edits these;
Core exposes a small built-in settings page too so Core alone is usable.

### Slate hub
`SlateHubScreen` — reachable from the pause/title menus and `/slate` (client command where available):
lists installed modules with icons, quick toggles (skin, dev mode), and each module's `hubEntries()`.

## Module briefs

### Menu (`slate_menu`) — client
Replaces (via `Minecraft.setScreen` swap in Core's `MinecraftMixin`): TitleScreen, SelectWorldScreen,
JoinMultiplayerScreen, PauseScreen, OptionsScreen (main page), plus new screens: Screenshots gallery,
Quick-connect. Everything else vanilla is restyled by Core's Reskin. Features: continue-card (last
world/server), favourite ("like") worlds & servers with star + sort, search/filter/sort, world & server
tags, world details (size, last played, mode, version, cheats), backup/duplicate/delete/open folder,
server list groups, live ping with player list, MOTD, favicon, version-compat chip, "friends here" chip
when Multiplayer is present, community server list from `config/slate/menu/community_servers.json`,
quick connect with live status for any typed address, recent servers, LAN, screenshots gallery (async
thumbnails, viewer with zoom/pan, delete, copy, open folder, share to friends when Multiplayer is present),
animated buttons, splash, version/mod count footer, account card with head. Provides layout element
types + actions to the dev mode. Vanilla skin variant of every screen.

### Multiplayer (`slate_multiplayer`) — both sides
Social layer. **Hub** = a `SocialHub` that runs on any server with the module (state under
`<server dir>/slate-hub/`) and can ALSO listen on a TCP port (`hub.listenPort`, default 25580, netty,
length-prefixed frames carrying the same `SocialMessage` records as the in-game payload channel) so
clients reach it from the title screen and from other servers. Client `SocialClient` picks the link:
hub TCP link (`multiplayer.json: homeHub`) if configured/reachable, else the payload link to the
current server if it runs the module. Same message set either way. Auth on the TCP link = Mojang
session (`joinServer`/`hasJoinedServer`, offline fallback when `hub.onlineMode=false`).
Features: friends (request/accept/decline/remove/block), nicknames & notes, friend groups (named sets;
each has a group chat channel), presence (online/away/in-game on `<server>` + dimension; join button →
`join_server`), DMs & group chats with media (images/GIF/voice clips over the blob channel, GIF picker,
clipboard paste — media stack lifted from Chatterbox into `core.net.blob` + `multiplayer.media`),
invites (to server, to voice group), notifications/toasts, player cards (skin render, stats, actions),
**screen sharing** (`ScreenShare`: framebuffer → downscale → JPEG (`ImageIO`) → blob stream at an adaptive
6–15 fps; viewers get a PiP window or full-screen viewer), **Simple Voice Chat** integration (soft dep;
speaking indicators, mute/deafen/per-player volume in our UI, voice groups from friend groups, PTT
state) — isolated in one adapter class like Chatterbox's `VoicechatAdapter`. Screens: Friends
(`SidebarScreen`): Friends / Requests / Groups / Messages / Streams / Settings. Title-screen friends
panel (layout element). Provides actions to the dev mode.

### Chat (`slate_chat`) — both sides
Chatterbox rebuilt on Core + Multiplayer: replaces the HUD chat and `ChatScreen` with Slate-styled ones
(both skins): grouped messages with heads and timestamps, channel tabs (Global / DM:<friend> /
Group:<name> / System) with unread badges, media cards (image/GIF/voice/video via `multiplayer.media`),
input bar with attach + GIF + emote buttons, `:emote:` pixel emotes rendered through a bitmap font
provider, mentions highlight + ping sound, hover actions (reply, copy, react?), link previews, typing
indicator (in-server payload), chat history per server on disk, search, compact/cozy density, slide-in
animation (replaces ChatAnimation). Public-chat media still needs the server module (relay); DMs go
through Multiplayer. Keeps Chatterbox's fallback tokens (`[voice 0:07 #hex8]`).

### Config (`slate_config`) — client
Unified settings: `SidebarScreen` with pages Video (vanilla + **Sodium's option model** when present,
rendered natively via `net.caffeinemc.mods.sodium.client.config.ConfigManager` — soft dep, compiled
against `libs/sodium.jar`; Sodium's own screen is replaced), Audio, Controls (searchable keybinds,
conflicts, categories), Chat, Interface (skin, accent presets + hex, motion, heading font, transitions,
toasts), Multiplayer, Accessibility, Language, Resource packs, Mods (every mod: native editors for
NeoForge `ModConfigSpec` TOML via `ConfigTracker`, Fabric mods' own screens via ModMenu factories,
NeoForge `IConfigScreenFactory`, plus generic JSON / `.properties` / TOML file editors), Search across
everything, Favourites (pin options), Presets (snapshot/restore of chosen options), Reset per option.
**Curated pages** from `config/slate/config/pages/*.json` (`path` = `optionsTxt:<key>` | `sodium:<id>` |
`toml:<modid>:<file>:<section.key>` | `json:<file>:<pointer>` | `props:<file>:<key>` | `slate:<module>:<key>`);
ships `df.json` — an example curated set for the DF pack (Visuals, Performance, Immersion, Gameplay,
Social) built from the real config files in the pack. (Since 2026-09-24: sections are top tabs, Gameplay and
Customization are categories other modules add tabs to via `config.api.SettingsTabs`, and the DF page is retired;
[docs/config.md](docs/config.md) is current.)

### Building (`slate_building`) — both sides
A survival-friendly building mod: unified shapes per material (natives such as oak stairs plus its own
material-carrying shape blocks) with a strict one-material-unit economy, an Alt swap wheel, placement ghosts, an R
build menu with 20 building modes (selected in the world, planned identically on client and server, executed over
ticks, undoable) unlocked by tiered tools in a Builder's Toolbox, chisel groups, and a Building tab under Slate
Config's Gameplay category. It is the one module that registers content, so it breaks two rules above on purpose:
it must be installed on both sides (NeoForge `displayTest = "MATCH_VERSION"`), and it adds `building.json`,
`building-server.json` (synced to clients) and `building-chisel.json`. Its payloads still use the `slate`
namespace. The contract (model, APIs, economy, ownership, and the corrections to the Core section above) is
[docs/building-design.md](docs/building-design.md); the user guide is [docs/building.md](docs/building.md).

## Visual language (both skins)
- 2 px base grid, 8 px spacing unit, pixel-stepped corners (radius 3 → steps 1,1,1), 1 px borders.
- Dark: layered surfaces (bg < surface < hover < active), accent used sparingly (primary button, focus
  ring, active tab, toggles), text never pure white. Shadows are 1–2 px hard offsets (pixel look).
- Vanilla: real widget sprites, brightness lifts on hover (animated), pressed offset 1 px, panels use
  `menu_background`/`inworld_menu_background`, lists use `menu_list_background`; still animated.
- Motion: 120–180 ms ease-out for hover/press, 200–260 ms for panels, staggered 20 ms entrance;
  `Theme.motion()==0` snaps everything.
- Every interactive element: hover state, press state, focus ring, disabled state, tooltip where useful.
- Keyboard: Tab order, Enter/Space activate, Esc back, arrows in lists, `/` focuses search.
