# Slate Config module map (read-only scout)

Paths are relative to `C:/Users/leonr/Coding/MinecraftMods/Source/UI/Slate`. I use two abbreviations:
- `CFG` = `common/config/src/main/java/dev/fallingcloud/slate/config/`
- `CORE` = `common/core/src/main/java/dev/fallingcloud/slate/core/`

**Read this first: there are two settings screens.** Both need to be considered for the requested changes.
- **`ConfigHubScreen`** (`slate_config:hub`, title "Settings") is the Config module's hub. The DF pack tab, Mods and Resource packs pages live here.
- **`SlateOptionsScreen`** (`slate_menu:options`, title "Options") is in the Menu module: `common/menu/src/main/java/dev/fallingcloud/slate/menu/client/options/SlateOptionsScreen.java`. It replaces vanilla `OptionsScreen` by default (`MenuConfig.optionsScreen = true`; the swap is at `MenuClient.java:66`).
  - Each of its category pages starts with a big **"Open <category>"** button. On the Video page that button reads "Open Video Settings..." and opens `new VideoSettingsScreen(...)`, which the Config module then swaps to `ConfigHubScreen("video")`.
  - This is almost certainly the "open video settings button" the user means. `docs/MORNING-REPORT-2026-09-23.md:19` already flags "Two config screens now claim Options → Video Settings".
  - Its General page has a section headed **"Gameplay"** (`slate_menu.options.section.play`) holding FOV, render distance and difficulty. That is the only "Gameplay" anywhere, apart from df.json's "Gameplay & HUD" section.

---

## 1. Screen class hierarchy

**Core base classes**
- `CORE/screen/SlateScreen.java`: `public abstract class SlateScreen extends Screen`.
  - Constants: `PAD = 12`, `HEADER_H = 32`.
  - Methods used here: `protected <T extends AbstractWidget> T addHeaderAction(T)`, `public static int entrance(Renderable r, int index)`, `protected abstract void build()`.
- `CORE/screen/SidebarScreen.java`: `public abstract class SidebarScreen extends SlateScreen`.
  - Constants: `NAV_W = 118`, `NAV_W_NARROW = 30`, `ROW_H = 20`, `NAV_TOP = 6`, `PAGE_TITLE_H = 22`.
  - `protected abstract void definePages(List<SidebarPage> pages)` is called once, from `build()`, when `pages` is empty.
  - Page area: `public Rect pageTitleRect()` and `public Rect pageRect()` (the area below the title row).
  - Page lifecycle: `public <T …> T addPageWidget(T)`, `public <T extends AbstractWidget> T addPageAction(T)` (right end of the title row), `public void showPage(int)`, `public void showPage(String id)`, `public void refreshPage()`, `@Nullable public SidebarPage currentPage()`, `public List<SidebarPage> pages()`.
  - Last page is remembered **by index** in a static `LAST_PAGE` map keyed by `rememberKey`.
  - Ctrl+Tab / Ctrl+Shift+Tab / Ctrl+1..9 switch pages.
  - Nav rows are a flat list drawn in `renderNav`. There are no groups or separators. `renderPageTitle(GuiGraphics)` is `protected` and draws `page.title()` plus a rule.
- `CORE/screen/SidebarPage.java`: `public abstract class SidebarPage`.
  - `protected SidebarPage(String id, Component title, Icon icon)`
  - `id()`, `title()`, `icon()`, `badge()`
  - `public abstract void build(SidebarScreen screen, Rect area)`
  - `render(SidebarScreen, GuiGraphics, Rect, int, int, float)`, `tick()`, `onHide()`

**The hub**
- `CFG/hub/ConfigHubScreen.java`: `public final class ConfigHubScreen extends SidebarScreen`.
  - Constructor: `public ConfigHubScreen(@Nullable Screen parent, @Nullable String page)`, rememberKey `"slate_config:hub"`.
  - `build()` adds three header actions, right to left:
    1. `SlateIconButton(Icon.EXTERNAL, "slate_config.hub.vanilla_options")`, which runs `ScreenSwaps.runUnswapped(() -> mc.setScreen(new OptionsScreen(this, mc.options)))`
    2. `SlateIconButton(Icon.UNDO, "slate_config.hub.reset_page")` → `resetPage()`
    3. `ConfigSearchField` → `onSearch`
  - It then resolves `pendingPage` with `showPage(p.startsWith("curated:") || p.contains(":") ? p : resolvePageId(p))`.
  - Page-type-specific logic uses `instanceof` checks. All of these must learn about tabs or containers:
    - `trackOptionPage()` (skips `FavouritesPage`)
    - `applyFilterToPage()` (`OptionPageBase` → `filter`, `ModsPage` → `filter`)
    - `refreshOptionPages()`
    - `index()` (`OptionPageBase` / `ModsPage` / `PresetsPage` `searchEntries()`)
    - `jumpTo(Hit)`
    - `resetPage()` (only works on an `OptionPageBase`)
    - `keyPressed` / `mouseClicked` (`ControlsPage.captureKey` / `captureMouse`)
    - `removed()` → `ApplyQueue.flush()`
  - Other public members: `lastOptionPage()` and `debugSearch(String)` (test hook).

**Sidebar order** (`ConfigHubScreen.definePages`, lines 58–73):

| # | id | class | icon | title key |
|---|---|---|---|---|
| 1 | `video` | `page/VideoPage` | `MONITOR` | `slate_config.page.video` |
| 2 | `audio` | `page/AudioPage` | `VOLUME` | `.page.audio` |
| 3 | `controls` | `page/ControlsPage` | `KEYBOARD` | `.page.controls` |
| 4 | `chat` | `page/SimplePages.ChatPage` | `CHAT` | `.page.chat` |
| 5 | `interface` | `page/InterfacePage` | `PALETTE` | `.page.interface` |
| 6 | `multiplayer` | `SimplePages.MultiplayerPage` | `MULTIPLAYER` | `.page.multiplayer` |
| 7 | `accessibility` | `SimplePages.AccessibilityPage` | `ACCESSIBILITY` | `.page.accessibility` |
| 8 | `language` | `page/LanguagePage` | `LANGUAGE` | `.page.language` |
| 9 | `packs` | `page/ResourcePacksPage` | `PACK` | `.page.packs` ("Resource packs") |
| 10 | `mods` | `page/ModsPage` (**extends SidebarPage**, not OptionPageBase) | `MODS` | `.page.mods` |
| 11.. | `curated:<id>` | `page/CuratedPage`, one per file from `CuratedPages.load()` | from JSON (`df` = `SPARKLE`) | literal title (`"DF pack"`) |
| n-1 | `favourites` | `page/FavouritesPage` | `STAR` | `.page.favourites` |
| n | `presets` | `page/PresetsPage` (**extends SidebarPage**) | `BOOKMARK` | `.page.presets` |

`SlateConfig.PAGE_IDS` (the dev action choices) is `video, audio, controls, chat, interface, multiplayer, accessibility, language, packs, mods, favourites, presets`. The `SlateConfigApi` javadoc lists the same ids.

**How a page renders.** Yes, every option page is one long scrolling column.
- `CFG/ui/OptionPageBase.java` is `public abstract class OptionPageBase extends SidebarPage` with `protected abstract List<Section> sections()`.
- `build(SidebarScreen, Rect)` makes **one** `SlateScrollPanel` covering the whole `area`. For each non-empty `Section` it stacks, top to bottom:
  - a `SectionHeader` (when headers are shown),
  - an optional wrapped `SlateLabel` description,
  - one `OptionRow` per binding (`OptionRow.HEIGHT + 2`), or `custom.apply(w)` for custom widgets.
- Headers are hidden when only one section is visible, unless `showSingleHeader()` returns true (`CuratedPage` overrides it to true).
- Sections are collapsible; the state is stored in `ConfigSettings.collapsedSections` under the key `pageId + ":" + section.id`.
- Filtering by `filter` ignores collapse state and drops non-binding custom items.
- Other public API: `rebuild()` (keeps scroll), `filter(String)`, `focusRow(String bindingId)` (un-collapses the section, then `ensureVisible` + `flash()`), `resetAll()`, `refreshRows()`, `searchEntries()` (also calls `OptionResolvers.publish` on every binding), `rows()`, `currentSections()`, and `protected OptionPageBase emptyText(Component)`.
- `onHide()` → `ApplyQueue.flush()`.

**Sections, headers and rows**
- `CFG/ui/Section.java` (`public final class Section`):
  - Fields: `id`, `title`, `@Nullable description`, `List<Item> items`, `boolean collapsible = true`.
  - `record Item(@Nullable OptionBinding binding, @Nullable IntFunction<AbstractWidget> custom)`.
  - Builders: `static of(String id, Component title)`, `static of(String, Component, List<? extends OptionBinding>)`, `add(OptionBinding)`, `addAll(List)`, `custom(IntFunction<AbstractWidget>)`, `fixed()` (not collapsible).
  - Queries: `bindings()`, `isEmpty()`.
- `CFG/ui/SectionHeader.java`: `public class SectionHeader extends SlateWidget`, `HEIGHT = 20`.
  - Constructor: `SectionHeader(int x, int y, int width, Component title, int count, boolean collapsed, @Nullable Runnable onToggle, int depth)`.
  - Draws chevron + heading + count + rule, in both skins.
- `CFG/ui/OptionRow.java`: `public class OptionRow extends AbstractContainerWidget`, `HEIGHT = 24`.
  - Constructor: `OptionRow(int x, int y, int width, OptionBinding binding, int depth)`.
  - Layout: star (favourite) | label (+ restart badge; INFO value text) | control from `Controls.create` | reset `SlateIconButton(Icon.UNDO)`. The reset button exists only when `hasDefault() && type().snapshotable()`.
  - Methods: `onChanged(Consumer<OptionRow>)`, `control()`, `flash()`, `refresh()`, `binding()`.
- `CFG/ui/Controls.java`: `static AbstractWidget create(OptionBinding b, int x, int y, int w, Consumer<Object> onChange)`. Control per type:

  | Type | Control |
  |---|---|
  | BOOLEAN | `SlateToggle` |
  | INT/DOUBLE, bounded | `ConfigSlider` |
  | INT/DOUBLE, unbounded | `ConfigTextField` (debounced) |
  | CHOICE | `SlateDropdown<Choice>` |
  | STRING | `ConfigTextField` |
  | COLOR | `ConfigColorField` |
  | LIST | `SlateButton` → `ListEditor.open` |
  | KEYBIND | `SlateKeybindButton` |
  | ACTION | `SlateButton` with `Icon.EXTERNAL` |

  `static void refresh(OptionBinding, AbstractWidget)` pushes a new value into an existing control. `WIDTH = 150`.

---

## 2. Every page

The "Sections" column is what would become top tabs.

**Video.** `CFG/page/VideoPage.java`. `static boolean sodium()` returns `isModLoaded("sodium") && SodiumBridge.available()`. Sections, in order:
1. If Sodium is present: `SodiumBridge.sections()` (`CFG/sodium/SodiumBridge.java`). There is **one Section per page of every `ModOptions`** registered with Sodium 0.8's `ConfigManager.CONFIG` (Sodium's own pages, plus Iris / Sodium Extra / … pages).
   - Section id: `sodium.<configId>.<pageIdx>`. Title: literal `"<Mod name> › <Page name>"`.
   - An `ExternalPage` becomes one ACTION row.
   - Bindings are ids `sodium:<rl>`, built by `wrap(Option, ModOptions)` and cached in `CACHE`. Apply goes through `Config.applyOption`; heavy options are debounced via `ApplyQueue.later(…, 450, …)`.
   - Also: `installScreenSwap(BiFunction<Screen,String,Screen>)` and `openNativeScreen(Screen)`.
   - In the DF pack this means many sections: Sodium 0.8.12 + Sodium Extra + Iris + Reese's Sodium Options are installed.
2. `display`: fullscreen, enableVsync, maxFps, guiScale, gamma, menuBackgroundBlurriness.
3. `world`: renderDistance … prioritizeChunkUpdates.
4. `effects`: fov … showAutosaveIndicator.
   - Sections 2–4 hide `VanillaOptions.COVERED_BY_SODIUM` rows when Sodium is present, unless `showDuplicateRows` is on.
5. `shaders` (only if `isModLoaded("iris")`): `IrisBridge.section()` (`CFG/iris/IrisBridge.java`, title `slate_config.video.shaders`). Rows:
   - `iris:pack` (INFO, current pack)
   - `iris:enabled` (BOOLEAN, via `IrisApi.getConfig().setShadersEnabledAndApply`)
   - `iris:open` (ACTION, `api.openMainIrisScreenObj(parent)`)
6. `more` (title `slate_config.video.more`, "More"):
   - `video:open_sodium` (only when Sodium is present): ACTION `slate_config.video.open_sodium` "Sodium's own video settings" → `SodiumBridge.openNativeScreen(mc.screen)`.
   - `video:open_vanilla`: ACTION `slate_config.video.open_vanilla` "Vanilla video settings" → `ScreenSwaps.runUnswapped(() -> mc.setScreen(new VideoSettingsScreen(parent, mc, mc.options)))`.
   - `video:show_duplicates`: BOOLEAN, writes `ConfigSettings.showDuplicateRows`.
   - Also `public static List<OptionBinding> presetBindings()`.

**Where the "open video settings" buttons are** (the user's button is item 1):
1. **Menu `SlateOptionsScreen.Page.build`** (line 251): `c.add(open(c.w, Component.translatable("slate_menu.options.open", title()), icon(), () -> opener.apply(SlateOptionsScreen.this)))`, plus the note `slate_menu.options.open_hint` ("Opens the full vanilla settings page for this category.").
   - The label is "Open %s" + `options.video`, i.e. "Open Video Settings...".
   - It opens `new VideoSettingsScreen(...)`, which is swapped to `ConfigHubScreen("video")`.
   - Every other Page has the same button: sounds, controls, chat, skin, language, accessibility, online, packs, telemetry, credits.
2. The ConfigHub VideoPage "More" rows `video:open_sodium` and `video:open_vanilla` described above.
3. The ConfigHub header button `slate_config.hub.vanilla_options` ("Vanilla options screen").

**Audio.** `CFG/page/AudioPage.java`.
- `volume`: an `audio:music_enabled` toggle (mute/restore via `ConfigSettings.musicVolumeBeforeMute`) plus `soundCategory_<src>` for every `SoundSource`.
- `output`: soundDevice, directionalAudio, showSubtitles.
- `voicechat` (only if loaded): `CFG/voice/VoicechatBridge.section()`, title `slate_config.audio.voicechat`.

**Controls.** `CFG/page/ControlsPage.java`.
- `mouse`
- `movement` (toggleSprint, toggleCrouch, autoJump)
- `keys`: a `fixed()` section whose custom `Toolbar` holds an "Unbound only" `SlateToggle` and a "Reset all keys" `SlateButton`.
- Then **one section per key category**, id `keys.<category>`, ordered by `VANILLA_ORDER` then alphabetically. In a big pack that is dozens of categories, so they cannot all be top tabs.
- Conflict marking uses `markConflicts()`. Other API: `public SlateKeybindButton activeCapture()`, `public boolean captureKey(int,int,int)`, `public boolean captureMouse(int)`.

**Chat.** `SimplePages.ChatPage`, in `CFG/page/SimplePages.java`.
- `chat` (`slate_config.chat.vanilla`): 17 vanilla keys.
- `slate_chat` (only if `Modules.isLoaded("slate_chat")`): `SimplePages.slateModule("chat","slate_chat","slate_config.chat.slate")`.

**Interface.** `CFG/page/InterfacePage.java`.
- `look`: skin `SlateSegmented`, a `SwatchRow` custom widget, core accent / radius / headingFont / blurInGame.
- `motion`: core motion, transitions, uiSounds, toasts.
- `restyle`: reskinScope, reskinAllowlist, reskinDenylist.
- `vanilla`: guiScale, narrator, highContrast, forceUnicodeFont, …, operatorItemsTab.
- `slate_menu` (only if the menu module is loaded).
- `dev`: devMode, devGrid, devSnap, plus an `interface:open_config_folder` action.

**Multiplayer.** `SimplePages.MultiplayerPage`.
- `online`: realmsNotifications, allowServerListing, hideMatchedNames, onlyShowSecureChat, telemetryOptInExtra, plus `multiplayer:hide_server_address` and `multiplayer:skip_warning`.
- `slate_multiplayer` (only if loaded).

**Accessibility.** `SimplePages.AccessibilityPage`.
- `reading` (12 vanilla keys)
- `motion` (11 vanilla keys + core motion and transitions)
- `input` (autoJump, toggleSprint, toggleCrouch)

**Language.** `CFG/page/LanguagePage.java`.
- `pick` (`fixed()`): a `ConfigSearchField`, a `SlateList<Lang>` (fixed height 190), and an "Apply language" `SlateButton`. Apply calls `lm.setSelected`, then `options.save()`, then `mc.reloadResourcePacks()`.
- `font`: forceUnicodeFont, japaneseGlyphVariants.

**Resource packs.** `CFG/page/ResourcePacksPage.java`, id `packs`.
- `active` (`fixed()`): INFO rows `packs:active.<i>`.
- `actions` (`fixed()`, "Manage"):
  - `packs:open` opens the vanilla `PackSelectionScreen` and returns to the hub.
  - `packs:folder` opens the resource pack folder.

**Shader packs.** There is **no page for this.** It exists only as the Iris section inside Video. There is no in-Slate pack list; Iris' own screen is opened instead.

**Mods.** `CFG/page/ModsPage.java` (`extends SidebarPage`).
- Builds its own `ConfigSearchField` at `area.y`, then a `SlateScrollPanel` at `area.y + 26` holding one `SlateCard` per mod (from `SlatePlatform.get().allMods()`), each with a `ModIcon`.
- "Configure" calls `ModConfigTargets.forMod(modId)` (`CFG/mods/ModConfigTargets.java`), which returns a `record Target(Component label, Icon icon, Runnable open)`, best first:
  1. the mod's own screen,
  2. NeoForge native spec docs via `ConfigPlatform.nativeConfigs` → `FileEditorScreen`,
  3. file docs from `ConfigFiles.forMod`.
- `FileEditorScreen` is a separate `SlateScreen` (`CFG/editor/FileEditorScreen.java`) that also uses `SectionHeader` + `OptionRow`.
- API: `public void filter(String)`, `public List<SearchIndex.Entry> searchEntries()`.

**Curated / "DF pack".** Built by `CFG/curated/CuratedPages.java` and `CFG/page/CuratedPage.java`.
- `bootstrap()`, called from `SlateConfig.initClient`, creates `config/slate/config/pages/` and **copies the shipped resource `/slate_config/df.json`** (`common/config/src/main/resources/slate_config/df.json`) there as `df.json` if it is missing.
- `load()` parses every `*.json` in that folder into `PageDef(id, title, icon, sections, file)`.
- `ConfigHubScreen.definePages` adds one `CuratedPage` per file, with id `"curated:" + def.id()`.
- df.json has id `df`, title "DF pack", icon SPARKLE, and sections Visuals, Performance, Immersion, "Gameplay & HUD", Social.
- A copy already exists on disk at `neoforge/dev/run/config/slate/config/pages/df.json`. The DF pack game dir currently has no `config/slate/` folder.

**Favourites.** `CFG/page/FavouritesPage.java`: one `fixed()` section `pinned`, filled by `resolved()` (ids from `ConfigSettings.favourites` resolved through `OptionResolvers.resolve`).

**Presets.** `CFG/page/PresetsPage.java` (`extends SidebarPage`).
- A "Save current as preset" button plus one card per preset from `Presets.all()`.
- The save dialog's sources are FAVOURITES, VIDEO (`VideoPage.presetBindings()`) and PAGE (`hub.lastOptionPage().currentSections()`).

**Search.** `CFG/search/SearchIndex.java` and `SearchPopup.java` (details in §4).

---

## 3. Gameplay category / module contributions

- **There is no Gameplay page in `ConfigHubScreen`.**
- **There is no registry through which other modules contribute pages, sections or tabs.** `SlateModule` (`CORE/module/SlateModule.java`) only has `id`, `displayName`, `icon`, `init`, `initClient`, `hubEntries`, `actions`.
- The only hook in `CFG/SlateConfigApi.java`: `public static void registerReloadHook(String module, Runnable reload)`, plus `hasReloadHook` and `runReloadHook`. **No module calls it**, so every Slate-module row shows a restart badge (`Resolvers.wrapSlateModule` sets `.restart(!hasHook)`).

**How Slate's own settings appear today**
- Core: `CFG/resolver/CoreBindings.java`, as `slate:core:<key>`, shown on the Interface page (and core motion/transitions on Accessibility).
- chat / multiplayer / menu: `SimplePages.slateModule(module, modId, titleKey)` → `Resolvers.slateModuleBindings(module)`. This reads the **top-level fields** of `config/slate/<module>.json` through `JsonDocument`, so:
  - labels come from `Humanize.key` (no lang lookup),
  - there are no tooltips, ranges or defaults (so no reset),
  - numbers get text fields.
  - Placement is hard-coded: Chat page (`slate_chat`), Multiplayer page (`slate_multiplayer`), Interface page (`slate_menu`), each gated by `Modules.isLoaded(id)`.
- Each module also has its own screens: `CoreSettingsScreen` (`slate:settings`), `ChatSettingsScreen` (`slate_chat:settings`), the Settings page of `FriendsHubScreen`, and Menu's `SlateOptionsScreen.MenuPage`.

**Build dependencies.** `neoforge/config/build.gradle` has `implementation project(':core')` only. Fabric has the same plus modmenu. Config does not depend on the other modules, and they do not depend on it.

---

## 4. Option model

- `CFG/option/OptionBinding.java` (interface):
  - Required: `String id()`, `Component label()`, `@Nullable Component tooltip()`, `OptionType type()`, `@Nullable Object get()`, `void set(@Nullable Object)` (applies AND persists).
  - Defaults: `defaultValue()`, `hasDefault()`, `isDefault()`, `reset()`, `range()`, `choices()`, `requiresRestart()`, `enabled()`, `action()`, `actionLabel()`, `valueText(Object)`, `searchText()`.
- `CFG/option/Binding.java`: `public class Binding implements OptionBinding`.
  - Factories: `static Binding of(String id, OptionType, Component)` and `of(String, OptionType, String)`.
  - Fluent setters: `label`, `tooltip(Component|String)`, `getter(Supplier<Object>)`, `setter(Consumer<Object>)`, `def`, `range(NumberRange|double,double,double)`, `choices(List<Choice>)`, `restart`, `enabledIf(BooleanSupplier)`, `action(Runnable)`, `action(Component, Runnable)`, `format(Function<Object,Component>)`, `searchWords(String)`.
  - `static OptionBinding override(base, label, tooltip, range, choices, restart)` is used by curated pages.
- `OptionType`: `BOOLEAN, INT, DOUBLE, CHOICE, STRING, COLOR, LIST, KEYBIND, ACTION, INFO`. `snapshotable()` is false for ACTION and INFO.
- Resolvers:
  - `CFG/option/OptionResolver.java` (`String prefix()`, `Optional<OptionBinding> resolve(String rest)`).
  - `OptionResolvers`: `register`, `publish`, `publishAll`, `resolve(String path)` (published bindings win), `invalidate(prefix)`.
  - Built-ins in `CFG/resolver/Resolvers.registerAll()`:

    | Prefix | Source |
    |---|---|
    | `optionsTxt:` | `VanillaOptions` |
    | `key:` | `KeyBinding` |
    | `json:<file>:<ptr>` | JSON file |
    | `toml:<modid>:<file>:<path>` | native NeoForge spec first, then file |
    | `props:` | `.properties` file |
    | `slate:<module>:<key>` | `core` → `CoreBindings`; else `config/slate/<module>.json` |
    | `sodium:` | `SodiumResolver` |
    | `iris:` | Iris bindings |
    | `voicechat:` | Voice chat bindings |

  - `public static Optional<OptionBinding> slateModuleBinding(String module, String key)` and `public static List<OptionBinding> slateModuleBindings(String module)`.
- **Apply:** `OptionRow.apply` calls `binding.set(v)`, shows a restart toast once, then `onChanged` (the page refreshes the other rows). Debouncing uses `CFG/ui/ApplyQueue` (`later(key, ms, run)`, `flush()`, ticked every client tick).
- **Favourites:** the row star calls `ConfigSettings.toggleFavourite(id)`. `CFG/ConfigSettings.java` (`config/slate/config.json`) holds `favourites`, `presets`, `musicVolumeBeforeMute`, `collapsedSections`, `showDuplicateRows`, `swapVanillaScreens`.
- **Reset:** per row via `binding.reset()`; per page via the hub header → `SlateModal.confirmDanger` → `OptionPageBase.resetAll()` (all sections).
- **Search:**
  - `SearchIndex.Entry(String pageId, Component pageTitle, Component sectionTitle, OptionBinding binding)`; each haystack is binding search text + section title + page title.
  - `query(text, excludePage, limit)`.
  - The hub filters the current page in place and shows a `SearchPopup` of hits from other pages. The crumb reads "Page › Section".
  - A pick calls `jumpTo`, which runs `showPage(pageId)` then `focusRow(bindingId)`.
- **Duplicated rows across pages matter for the merges.** Bindings are cached, so the duplicates share state:
  - `hideMatchedNames` and `onlyShowSecureChat` appear on both Chat and Multiplayer.
  - `forceUnicodeFont` appears on Language, Accessibility and Interface.
  - `chatOpacity`, `chatDelay` and others appear on both Chat and Accessibility.

---

## 5. Tab widgets

- `CORE/widget/SlateTabs.java`: `public class SlateTabs extends SlateWidget`, `HEIGHT = 22`.
  - `record Tab(Component label, @Nullable Icon icon, int badge)`, with constructors `Tab(Component)` and `Tab(Component, Icon)`.
  - Constructor: `SlateTabs(int x, int y, int width, List<Tab> tabs, int selected, IntConsumer onChange)`.
  - Methods: `compact()`, `index()`, `tabs()`, `setBadge(int i, int count)`, `select(int)`, `contentWidth()`.
  - Behaviour: animated underline on the dark skin, raised `SlateDraw.vanillaTab` tabs on the vanilla skin, arrow / Home / End keys, narration.
  - **No overflow handling.** Stretch mode divides the width evenly; compact mode can run past the width with no clipping or scrolling.
- Its only user is `CORE/client/media/GifLibraryScreen.java:71`. **No config page uses tabs.**
- `CORE/mixin/TabNavigationBarMixin.java` and `TabButtonMixin.java` only restyle vanilla's tab bars.

---

## 6. Screen swaps

- `CORE/screen/ScreenSwaps.java`: `register(Class<? extends Screen>, Function<Screen,Screen>)`, `registerByName(String className, Function)`, `unregister*`, `runUnswapped(Runnable)`, `apply(Screen)`.
  - Matching is by exact class. The map is a `LinkedHashMap`, so a second registration for the same class replaces the first.
- `CFG/SlateConfig.installSwaps()` does nothing when `ConfigSettings.swapVanillaScreens` is false. Otherwise it registers `swap(cls, page)` → `new ConfigHubScreen(Minecraft.getInstance().screen, page)`:

  | Vanilla screen | Hub page |
  |---|---|
  | `VideoSettingsScreen` | `video` |
  | `SoundOptionsScreen` | `audio` |
  | `ControlsScreen`, `KeyBindsScreen` | `controls` |
  | `ChatOptionsScreen` | `chat` |
  | `LanguageSelectScreen` | `language` |
  | `AccessibilityOptionsScreen` | `accessibility` |
  | `OnlineOptionsScreen` | `multiplayer` |

  - When Sodium is loaded it also calls `SodiumBridge.installScreenSwap(...)`, i.e. `registerByName("net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen", …)` → hub `"video"` (it reads `prevScreen` reflectively).
  - Caveat: the DF pack also has `reeses-sodium-options`, and an exact-name swap will not catch a replacement screen.
- `OptionsScreen` itself is swapped by **Menu** (`MenuClient.java:66`): `cfg().optionsScreen ? new SlateOptionsScreen(((OptionsScreenAccessor) s).slate$lastScreen()) : null`.
- Other entry points to the hub:
  - `SlateConfigApi.openHub(Screen, String)` / `hub(Screen, String)`
  - `CoreActions.SCREEN_FACTORIES.put("slate_config:hub", …)` (used by Menu's SlatePage)
  - NeoForge `IConfigScreenFactory` in `neoforge/config/.../SlateConfigNeoForge.java`
  - Fabric ModMenu in `fabric/config/.../SlateConfigModMenu.java`
  - the dev action `slate_config:open` with a `page` argument
  - `SmokeTest` (`CFG/SmokeTest.java`, which walks `hub.pages()` ids and calls `showPage("video")`)

---

## 7. Lang files

- Config: `common/config/src/main/resources/assets/slate_config/lang/en_us.json`. Key families: `slate_config.page.*`, `.video.*`, `.audio.*`, `.controls.*`, `.chat.*`, `.multiplayer.*`, `.accessibility.*`, `.interface.*`, `.core.*`, `.language.*`, `.packs.*`, `.mods.*`, `.presets.*`, `.opt.<key>.tip`, `.hub.*`, `.row.*`.
- Menu options: `common/menu/src/main/resources/assets/slate_menu/lang/en_us.json` (`slate_menu.options.*`, including `open`, `open_hint`, `section.play`).
- Docs mention the page structure in `docs/config.md` and `DESIGN.md:233-245`.

---

## Change map

**(a) Top tabs instead of one long scroll**
- `CFG/ui/OptionPageBase.java`:
  - Add an active-tab index, and remember the tab per page (a new `ConfigSettings` field, e.g. `Map<String,String> lastTab`).
  - In `build()`, when not hosted inside a category (see c–e) and more than one section is visible:
    - put a `SlateTabs(…).compact()` at `area.y()`, with one `Tab` per visible `Section` (title, `bindings().size()` as badge only while filtering);
    - start the `SlateScrollPanel` at `area.y() + SlateTabs.HEIGHT + 6`;
    - render only the active section's items, with no collapsible `SectionHeader`.
  - `focusRow` must select the tab that owns the binding.
  - `filter`: either show matches from all tabs, or keep the tab and set match-count badges via `setBadge`.
  - `resetAll`: decide whether it resets the current tab or all tabs; the confirm text is in `ConfigHubScreen.resetPage`.
  - `Section.collapsible` and `ConfigSettings.collapsedSections` become irrelevant for tabs.
- `ControlsPage`: key categories are too many for tabs. Use tabs Mouse / Movement / Key binds, and keep the categories as collapsible headers inside Key binds. Its `Toolbar` section stays in that tab.
- `VideoPage`: group Sodium sections **per mod** (one tab per `ModOptions` — "Sodium", "Sodium Extra", "Iris" — with that mod's pages as sub-headers) rather than one tab per Sodium page. Also a "Vanilla" tab (Display / World / Effects as headers). This needs a small change to `SodiumBridge.sections()`, or a new grouped variant.
- `SlateTabs` needs overflow handling: scroll arrows, or a "more" dropdown. Core rules allow adding methods or classes, but not changing signatures. Alternatively, put tabs in the title row by overriding `SidebarScreen.renderPageTitle` (protected) in `ConfigHubScreen`.
- Update `SearchPopup` / `SearchIndex.Entry` crumbs to "Category › Tab".

**(b) Remove the "open video settings" button**
- Menu: `SlateOptionsScreen.Page.build` (line 251) adds the "Open %s" button and `open_hint` note.
  - Best fix: when `Modules.isLoaded("slate_config")`, route the Options screen to the hub itself. At `MenuClient.java:66`, return `CoreActions.SCREEN_FACTORIES.get("slate_config:hub").apply(lastScreen)`, or keep `SlateOptionsScreen` but drop the `Page` entries the hub covers.
  - Keep the swap in Menu. Registering a second `OptionsScreen` swap from Config would silently override Menu's (the map key is the same class), depending on init order.
- Config: delete the `more` section's `video:open_sodium` and `video:open_vanilla` rows in `VideoPage.sections()`. Move `video:show_duplicates` to the Vanilla tab.
- Lang keys to drop: `slate_config.video.open_sodium*`, `.open_vanilla*`, `.more`. Consider also dropping the hub header `slate_config.hub.vanilla_options` button.
- `SodiumBridge.openNativeScreen` becomes unused.

**(c) Customization = Mods / Resource Packs / Shader Packs**
- New container class, e.g. `CFG/page/TabbedCategoryPage extends SidebarPage`. It holds a `List<SidebarPage>` of children and draws a `SlateTabs` row, then calls `child.build(screen, subRect)` with the rect below the tabs. It also forwards `render`, `tick` and `onHide`, and exposes `activeChild()` and `select(String childId)`.
- Children:
  - `ModsPage` (unchanged; its own search field sits at the top of the sub-rect);
  - `ResourcePacksPage`;
  - a new `ShaderPacksPage` (id `shaders`, `Icon.SHADER`), built from `IrisBridge.section()`. Remove the `shaders` section from `VideoPage`. Optionally add a real pack list later by scanning `shaderpacks/` and selecting reflectively.
- Children hosted in a container render their sections as plain headers, with no nested tabs.
- `ConfigHubScreen`: every `instanceof` site (`trackOptionPage`, `applyFilterToPage`, `refreshOptionPages`, `index`, `jumpTo`, `resetPage`, `keyPressed` / `mouseClicked` for `ControlsPage`) must unwrap to the active child.
- Page ids: `SearchIndex.Entry.pageId` should be the leaf id plus a leaf → category map. `showPage(String)` must accept leaf ids by selecting the category and then the tab. Note `build()`'s `p.contains(":")` special case, so pick `/` or a lookup rather than `:` as the separator.
- Lang: new `slate_config.page.customization`, `.page.shaders`.

**(d) Multiplayer = Multiplayer + Chat tabs**
- `TabbedCategoryPage("multiplayer", Icon.MULTIPLAYER, [MultiplayerPage, ChatPage])` in `definePages`.
- Remove the `hideMatchedNames` / `onlyShowSecureChat` duplicates from one of the two.
- Swaps: `ChatOptionsScreen` → multiplayer category, chat tab. `OnlineOptionsScreen` → multiplayer category, online tab.

**(e) Language + Accessibility, directly below Customization**
- `TabbedCategoryPage(…, [LanguagePage, AccessibilityPage])`, ordered right after Customization in `definePages`. Dedupe `forceUnicodeFont`.
- Update the swaps for `LanguageSelectScreen` and `AccessibilityOptionsScreen`.
- New lang key, e.g. `slate_config.page.language_accessibility`.

**(f) Remove the DF pack tab**
- Delete `common/config/src/main/resources/slate_config/df.json` and the copy block in `CuratedPages.bootstrap()`.
- Migrate existing installs: remove or rename `config/slate/config/pages/df.json` once. Gate this on a new `ConfigSettings` flag, or check `"id":"df"`, because the shipped resource will be gone.
- Keep the curated-pages loader for modpack authors, or drop the curated loop in `definePages` if the feature should go entirely.
- Update `docs/config.md`, `DESIGN.md:242-245`, and `CuratedPages`' javadoc.
- Delete the dev copy at `neoforge/dev/run/config/slate/config/pages/df.json`.

**(g) Gameplay category with a Building tab fed by the new module**
- No API exists. Add one to `CFG/SlateConfigApi.java`, for example:

  ```java
  record SettingsTab(String id, Component title, Icon icon, Supplier<List<Section>> sections)
  static void registerTab(String categoryId, SettingsTab tab)
  static List<SettingsTab> tabs(String categoryId)
  ```

  Keep it in a `ConcurrentHashMap` / `LinkedHashMap`, like `RELOAD_HOOKS`.
- In the hub, a new `GameplayPage` (`TabbedCategoryPage` id `gameplay`, e.g. `Icon.SWORD` or `Icon.SURVIVAL`) builds an `OptionPageBase` per registered tab. It can also host vanilla gameplay rows (autoJump, toggleSprint/Crouch, mainHand, attackIndicator; difficulty as in Menu's GeneralPage). Add `gameplay` to `SlateConfig.PAGE_IDS`.
- The building module builds proper `Binding`s (ranges, defaults, tooltips from its own lang keys) over its own `config/slate/building.json` via `JsonConfig`. Keybinds for building modes appear on Controls automatically as `KeyMapping`s.
- Registration must live in a class loaded only after `Modules.isLoaded("slate_config")` / `SlatePlatform.get().isModLoaded("slate_config")`, called from `initClient`.
- Gradle for the new module on both loaders: `compileOnly project(':config')` (Fabric: `configuration: 'namedElements'`). Declare an optional dependency in the mod metadata.
- Fallback with zero API: `SimplePages.slateModule("building","slate_building",…)` gated by `Modules.isLoaded("slate_building")`. It works today, but gives humanized labels, no ranges, defaults or tooltips, and restart badges unless the module calls `SlateConfigApi.registerReloadHook("building", …)`.

**Also update**
- `SlateConfig.PAGE_IDS` and `installSwaps()` page ids.
- `SlateConfigApi` javadoc.
- `SmokeTest` (walks `pages()`; needs to walk tabs).
- `PresetsPage.saveDialog` (`lastOptionPage().currentSections()` should mean all tabs).
- `ConfigSettings` (new fields for last tab and the df migration flag).
- `docs/config.md`.
- Both lang files.