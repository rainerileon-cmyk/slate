# Slate Core API reference (for a new `slate_building` module and the Config tab rework)

Repo: `C:/Users/leonr/Coding/MinecraftMods/Source/UI/Slate`. Core sources: `common/core/src/main/java/dev/fallingcloud/slate/core/`, shortened to `core/` below. Everything here was read from source; nothing was edited.

**Where DESIGN.md is wrong about Core.** These names do not exist:
- `layout.ui.Row`, `Column` and `Grid`. Only `Flow`, `Anchor` and `Rect` exist.
- `Anim.get(partial)`.
- `Colors.readable(fg,bg)`. It is `Colors.readableOn(bg)`.
- `Theme.headingFont()`. It is `Theme.headingStyle()`.
- `Fonts.HEADING`. It is `Fonts.heading(...)`.
- `SlateDraw.icon(...)`. Use `Icons.draw`.
- A size enum on `SlateButton`. There are only `HEIGHT` constants.
- A modal stack on `SlateScreen`. Popups are global, in `Popups`.

---

## 1. Module bootstrap

**`core/Slate.java`**
- `public static final String MOD_ID = "slate"`
- `public static final Logger LOGGER`
- `public static synchronized void init()`: idempotent. Loads `core.json` and calls `BlobChannel.register()`.
- `public static CoreConfig config()`
- `public static JsonConfig<CoreConfig> configFile()`
- `public static String version()`
- `public static ResourceLocation id(String path)`: always in the `slate` namespace.

**`core/module/SlateModule.java`** (interface)
```java
String id(); Component displayName(); Icon icon();
default void init() {}          // both sides, during mod construction: payloads, configs, server hooks
default void initClient() {}    // client, right after init(): screens, keys, element types
default List<HubEntry> hubEntries() { return List.of(); }
default List<ActionType> actions() { return List.of(); }
record HubEntry(Component label, Icon icon, Runnable onClick) {}
```

**`core/module/Modules.java`**
- `static void register(SlateModule)` runs, in order:
  1. `Slate.init()`
  2. dedupes by id
  3. `module.init()`
  4. `Actions.register(...)` for each of `actions()`
  5. if `SlatePlatform.get().isClient()`, `module.initClient()`
- Also: `static boolean isLoaded(String id)`, `static Optional<SlateModule> get(String id)`, `static List<SlateModule> all()`.

**`core/client/SlateClient.java`** (client glue; runs from the core loader entry)
- `static synchronized void init()`:
  - `Theme.reload()`
  - `SlateKeys.register(EDITOR_KEY, HUB_KEY)`
  - `CoreActions.registerAll()` and `CoreElements.registerAll()`
  - registers screen ids `slate:hub` and `slate:settings`
  - adds a tick listener and a `KEY_PRESSED` listener for the hub key
  - `DevHarness.init()`
- `public static final KeyMapping EDITOR_KEY` (F7) and `HUB_KEY` (unbound), both in category `SlateKeys.CATEGORY`.
- Hooks called by the mixins:
  - `onScreenChanged(@Nullable Screen)` → Popups, LayoutEditor, Transitions, `SCREEN_OPENED`
  - `onScreenInit(Screen)` → `LayoutApplier.apply`, `SCREEN_INIT_POST`, editor
  - `onScreenRendered(Screen, GuiGraphics, int, int, float)` → `Clock.onFrame`, `SCREEN_RENDER_POST`, editor, `Popups.render`, `SlateToasts.render`, `SlateTooltips.render`, `Transitions.render`
  - `onHudRendered(GuiGraphics, float partialTick)` → `Clock.onFrame` (only when no screen), `HUD_RENDER`, toasts (only when no screen)

**Hub:**
- `core/client/SlateHubScreen extends SlateScreen`, constructor `(Screen parent)`. It iterates `Modules.all()` and renders each module's `hubEntries()` as buttons.
- `CoreActions.SCREEN_FACTORIES` is a `public static final Map<String, Function<Screen,Screen>>`. Registering an id there makes `open_screen(id)` and `CoreActions.openScreen(String)` work.

**How existing modules register.** The pattern to copy:
- Module singleton, e.g. `common/menu/src/main/java/dev/fallingcloud/slate/menu/SlateMenu.java`:
  - `public static final SlateMenu MODULE`, `MOD_ID = "slate_menu"`, `id(path)`.
  - Lazy `configFile()` returns `JsonConfig.of("menu", MenuConfig.class, MenuConfig::new)`.
  - `init()` calls `Slate.init(); configFile();`.
  - `initClient()` calls `MenuClient.init()`.
- `common/config/.../config/SlateConfig.java`, `initClient()`:
  - `ScreenIds.register(ConfigHubScreen.class, "slate_config:hub", "Slate settings")`
  - `CoreActions.SCREEN_FACTORIES.put("slate_config:hub", p -> new ConfigHubScreen(p, null))`
  - `SlateEvents.CLIENT_TICK_END.register(ApplyQueue::tick)`
  - `ScreenSwaps.register(VideoSettingsScreen.class, orig -> new ConfigHubScreen(Minecraft.getInstance().screen, "video"))`, and the same for the other option screens.
- Loader entries:
  - NeoForge, client-only module: `neoforge/menu/src/main/java/.../menu/neoforge/SlateMenuNeoForge.java` is `@Mod(value = SlateMenu.MOD_ID, dist = Dist.CLIENT)`, and its constructor `(IEventBus modBus, ModContainer container)` calls `Modules.register(SlateMenu.MODULE)`.
  - NeoForge, both-sides module: `neoforge/multiplayer/.../SlateMultiplayerNeoForge.java` is `@Mod(value = SlateMultiplayer.MOD_ID)`, with no dist.
  - Fabric, client-only: `fabric/menu/.../SlateMenuFabric implements ClientModInitializer`, registered in `onInitializeClient`.
  - Fabric, both sides: `fabric/multiplayer/.../SlateMultiplayerFabric implements ModInitializer`, which registers in `onInitialize()`, so `initClient()` also runs from the main entrypoint. `SlateMultiplayerFabricClient` is empty.
  - **Fabric order caveat:** all `main` entrypoints run before all `client` entrypoints. A both-sides module's `initClient()` therefore runs **before** `SlateFabricClient` → `SlateClient.init()` (Theme and keys not yet initialised). `SlateKeys` queues, so key registration is safe. Don't rely on `SlateClient` state inside `initClient` on Fabric.
- Manifests:
  - `neoforge/<m>/src/main/resources/META-INF/neoforge.mods.toml` (has `displayTest = "IGNORE_ALL_VERSION"`, `[[mixins]] config = "slate_<m>.mixins.json"`, and a required dependency on `slate` `[1.0,)` with `ordering="AFTER"`)
  - `fabric/<m>/src/main/resources/fabric.mod.json`
- Gradle for a new module `building`:
  1. Add `include` to both `neoforge/settings.gradle` and `fabric/settings.gradle`.
  2. Create `neoforge/building/build.gradle`:
     - `base { archivesName = 'slate-building-neoforge-1.21.1' }`
     - `neoForge { mods { slate_building { sourceSet sourceSets.main } } }`
     - `dependencies { implementation project(':core') }`
  3. Create `fabric/building/build.gradle`: `loom { mods { slate_building {...} } }` and `implementation project(path: ':core', configuration: 'namedElements')`.
  4. Add it to both `dev/build.gradle` files (`evaluationDependsOn`, `mods { slate_building {...} }`, and `implementation`).
  5. Shared sources must be at `common/building/src/main/{java,resources}`. The root build adds `common/${project.name}` automatically.
- No access wideners or access transformers exist anywhere in the tree.

## 2. Theme (`core/theme/`)

**`Theme`**
- Static: `current()`, `reload()` (rebuilds from `Slate.config()` and fires listeners), `onChange(Runnable)`, and `public static final ResourceLocation HEADING_FONT = Slate.id("heading")`.
- Instance:
  - `skin()`, `isVanilla()`, `palette()`
  - `radius()`: 0 on vanilla, otherwise `CoreConfig.radius` clamped to 0–4
  - `motion()`: float, 0 = snap, 1 = default, 2 = half speed
  - `transitions()`, `uiSounds()`, `toasts()`, `blurInGame()`
  - `headingStyle()`: `Style` using the Pixelify font when enabled
  - `ms(int base)`: base × motion
  - Shorthands: `bg()`, `surface()`, `border()`, `text()`, `muted()`, `accent()`

**`Skin`**: `enum { DARK, VANILLA }`, `static Skin parse(String)`.

**`Palette`** is a record of ARGB ints, in this order: `bg, bg2, surface, surfaceHover, surfaceActive, border, borderStrong, text, textMuted, textDim, accent, accentHover, accentText, danger, success, warning, overlay, shadow`.
- `DEFAULT_ACCENT = 0xFFD9805E`.
- Methods: `static Palette dark(int accent)`, `static Palette vanilla(int accent)`, `Palette withAccent(int)`, `record AccentPreset(String name, int color)`, and `List<AccentPreset> ACCENTS` (Terracotta, Ember, Amber, Moss, Mint, Sky, Cobalt, Lavender, Orchid, Rose, Slate, Bone).

| Token | `dark()` | `vanilla()` |
|---|---|---|
| bg / bg2 | `FF161615` / `FF1B1B1A` | `FF1E1E1E` / `FF242424` |
| surface / hover / active | `FF222221` / `FF2A2A28` / `FF323230` | `FF3A3A3A` / `FF474747` / `FF555555` |
| border / borderStrong | `FF33332F` / `FF45443F` | `FF000000` / `FF8B8B8B` |
| text / muted / dim | `FFECEAE4` / `FFA19F97` / `FF6E6C66` | `FFFFFFFF` / `FFA0A0A0` / `FF707070` |
| danger / success / warning | `FFE5484D` / `FF5CB176` / `FFE0A458` | `FFFF5555` / `FF55FF55` / `FFFFAA00` |
| overlay / shadow | `A0000000` / `66000000` | `B0000000` / `66000000` |

In both, `accentHover = Colors.hover(accent)` and `accentText = Colors.readableOn(accent)`.

**`Colors`** (all static):
- `argb(a,r,g,b)`, `alpha/red/green/blue(c)`
- `withAlpha(c, int 0-255)`, `scaleAlpha(c, float)`
- `lerp(a,b,t)` (includes alpha), `mix(a,b,t)` (RGB only, keeps a's alpha)
- `brighten(c, f±)`, `scale(c, f)`
- `fromHex(String, fallback)`, `toHex(c)` (`#RRGGBB`)
- `luminance(c)`, `readableOn(bg)`, `hover(c)`
- `hsvToRgb(h,s,v)`, `float[] rgbToHsv(c)`

## 3. gfx (`core/gfx/`)

**`SlateDraw`** (every public static)
- Sprite ids (vanilla `ResourceLocation`s): `BUTTON`, `BUTTON_DISABLED`, `BUTTON_HIGHLIGHTED`, `SLIDER*`, `TEXT_FIELD*`, `CHECKBOX*`, `SCROLLER*`, `TAB*`, `MENU_BACKGROUND`, `INWORLD_MENU_BACKGROUND`, `MENU_LIST_BACKGROUND`, `INWORLD_MENU_LIST_BACKGROUND`, `(INWORLD_)HEADER/FOOTER_SEPARATOR`.
- `Font font()`
- Fills and shapes:
  - `rect(g,x,y,w,h,color)`, `pixelRound(g,x,y,w,h,color,radius)` (stepped corners), `outline(g,x,y,w,h,color,radius)`
  - `panel(g,x,y,w,h,fill,border)` (uses the theme radius)
  - `shadow(g,x,y,w,h,float alpha)`: hard 2 px offset
  - `pixelCircle(g,cx,cy,r,color)`, `pixelRing(g,cx,cy,r,color)`
  - `hgradient(g,x,y,w,h,left,right)`, `vgradient(g,x,y,w,h,top,bottom)`
  - `hline(g,x,y,w,color)`, `vline(g,x,y,h,color)`
  - `vignette(g,x,y,w,h,float strength)`
- Text:
  - `int textY(int y,int h)`
  - `text(g, Component|String|FormattedCharSequence, x,y,color)`, `text(g,Component,x,y,color,boolean shadow)`, `textShadow(g,Component,x,y,color)`
  - `textCentered(g, Component|String, cx,y,color)`, `textCentered(g,Component,cx,y,color,shadow)`
  - `textRight(g,Component,rightX,y,color[,shadow])`
  - `FormattedCharSequence truncate(Component,int width)`. Working-tree change: returns empty when even "..." does not fit.
  - `drawScrollingText(g, Component|FormattedCharSequence, x,y,width,color,shadow)`, `drawScrollingTextCentered(g,Component,x,y,width,color,shadow)`
  - `int width(Component|String|FormattedCharSequence)`, `int lineHeight()`
- Textures:
  - `blitScaled(g,tex,x,y,w,h,texW,texH,float alpha)`
  - `blit(g,tex,x,y,w,h,float u,float v,int uw,int vh,int texW,int texH,float alpha)`
  - `blitFit(g,tex,x,y,w,h,texW,texH,boolean cover,float alpha)`
  - `playerHead(g,skin,x,y,size,alpha)`
- Vanilla skin pieces:
  - `vanillaButton(g,x,y,w,h,float hover,boolean enabled,float alpha)`
  - `vanillaFace(g,base,highlighted,disabled,x,y,w,h,hover,enabled,alpha)`
  - `vanillaSlider(g,x,y,w,h,handleX,hover,enabled,alpha)`
  - `vanillaCheckbox(g,x,y,size,selected,hover,alpha)`
  - `vanillaTextField(g,x,y,w,h,focused)`
  - `vanillaTab(g,x,y,w,h,selected,hover,alpha)`
  - `vanillaPanel(g,x,y,w,h,inWorld)`, `vanillaListBackground(g,x,y,w,h,inWorld)`, `vanillaSeparator(g,x,y,w,header,inWorld)`, `vanillaDialog(g,x,y,w,h,alpha)`
- Misc:
  - `dim(g,Screen,color)`, `dim(g,w,h,color)`
  - `focusRing(g,x,y,w,h,float alpha)`
  - `darkFace(g,x,y,w,h,float hover,float press,boolean enabled,float alpha)`
  - Scissor: `scissor(g,x,y,w,h)` and `unscissor(g)`. These wrap `enableScissor`, which ignores the pose.
- **Missing for building UIs:** there is no arc, sector, ring-segment, line or triangle primitive, and nothing for 3D or in-world drawing.

**`Anim`**
- Constructors: `Anim(float initial)` (150 ms, `OUT_CUBIC`) and `Anim(float initial,int durationMs,Ease)`.
- Methods: `duration(int)`, `ease(Ease)`, `set(float)`, `set(float,int ms)`, `set(boolean)`, `snap(float)`, `target()`, `isAnimating()`, `get()`.
- Honours `Theme.motion()`: 0 snaps, otherwise duration × motion.

**`Ease`** enum: `LINEAR, OUT_CUBIC, IN_OUT_CUBIC, OUT_QUINT, OUT_BACK, OUT_EXPO, SPRING`, with `float apply(float t)`.

**`Clock`**: `nowMs()`, `nowNanos()`, `onFrame()` (called by the Core hooks), `frameDelta()` (ms, clamped to 100). Real time, keeps running while the game is paused.

**`Fonts`**: `MutableComponent heading(Component)` and `heading(String)`.

**`Icons`**
- `ATLAS = slate:textures/gui/icons.png`
- `draw(g, Icon, x, y, color)` (16 px) and `draw(g, Icon, x, y, int size, color)` (8/12/16/24/32 look right). Tinted with `g.setColor`.

**`Icon`** enum: 160 constants in 16×16 cells, 16 columns. Methods `u()`, `v()`, `static rows()`, and constants `CELL=16`, `COLUMNS=16`.
```
ARROW_LEFT ARROW_RIGHT ARROW_UP ARROW_DOWN CHEVRON_LEFT CHEVRON_RIGHT CHEVRON_UP CHEVRON_DOWN CLOSE CHECK PLUS MINUS SEARCH SETTINGS FRIENDS USER
CHAT MIC MIC_OFF SPEAKER SPEAKER_OFF HEADSET SCREEN_SHARE MONITOR CAMERA IMAGE FOLDER WORLD SERVER STAR STAR_FILLED HEART
HEART_FILLED EDIT TRASH COPY LINK EXTERNAL PLAY PAUSE STOP REFRESH FILTER SORT GRID LIST PIN LOCK
UNLOCK BELL BELL_OFF INFO WARNING ERROR QUESTION HOME EXIT POWER FULLSCREEN WINDOWED KEYBOARD MOUSE GAMEPAD LANGUAGE
ACCESSIBILITY PACK SHADER CLOUD MOON SUN SPARKLE BOLT CLOCK CALENDAR TAG BOOKMARK SEND ATTACH GIF EMOTE
VIDEO DOWNLOAD UPLOAD SAVE UNDO REDO LAYERS MOVE RESIZE EYE EYE_OFF PALETTE BRUSH CODE TERMINAL BUG
WRENCH SLIDERS TOGGLE DOT DOTS MENU DRAG VOLUME SIGNAL WIFI SHIELD CROWN TEAM QUEST TROPHY MAP
COMPASS SWORD BLOCK SLATE SINGLEPLAYER MULTIPLAYER REALMS MODS QUIT BACKUP DUPLICATE IMPORT EXPORT MUSIC PING LAN
NEW_WORLD CHEATS HARDCORE CREATIVE SURVIVAL ADVENTURE SPECTATOR DIFFICULTY GROUP INVITE BLOCKED AWAY ONLINE OFFLINE STREAM PIP
REPLY REACT HISTORY TYPING MENTION HASH PENCIL_OFF SNAP ALIGN_LEFT ALIGN_CENTER ALIGN_RIGHT ALIGN_TOP ALIGN_MIDDLE ALIGN_BOTTOM TEXT PANEL
```
- The atlas is generated by `tools/icons.py` (Pillow).
- **This machine has no Python** (only the WindowsApps store stub), so the atlas cannot be regenerated here. A module could ship its own atlas and blit it with `SlateDraw.blit`.

**`Textures`**
- `record Loaded(ResourceLocation id,int width,int height)`
- `cached(Path)`, `load(Path, Consumer<Loaded>)`, `loadThumbnail(Path,int maxSize,Consumer<Loaded>)`
- `fromBytes(byte[], String cacheKey)`, `register(NativeImage, String prefix)`, `release(Loaded)`, `invalidate(Path)`

## 4. Widgets (`core/widget/`)

**`SlateWidget extends AbstractWidget`** (abstract)
- Constructor: `protected SlateWidget(int x,int y,int w,int h,Component message)`.
- Protected anims: `hoverAnim` (140 ms), `pressAnim` (90), `focusAnim` (160), `enterAnim` (220). `ENTER_SLIDE = 6`.
- `renderWidget` is **final**. It updates the anims, requests the tooltip after `SlateTooltips.DELAY_MS = 350` of hover, then dispatches `renderVanilla` or `renderDark` on `Theme.current().isVanilla()`. Subclasses implement:
  `protected abstract void renderDark(GuiGraphics g,int mouseX,int mouseY,float partialTick)` and `renderVanilla(...)`.
- Fluent:
  - `tip(Component)`, `tip(List<Component>)`: return `SlateWidget`; only `SlateButton` and `SlateIconButton` override them covariantly
  - `tipLines()`, `playEntrance(int delayMs)`, `silent()`, `enabled(boolean)` (sets `active`), `shown(boolean)`
- State:
  - `hover()`, `press()`, `focus()`, `effectiveAlpha()` (alpha × entrance), `isEntering()`, `contains(mx,my)`
  - protected `enterProgress()`, `enterOffset()` (6 px → 0), `isPressed()`, `flashPress()`
- Input: `onClick` snaps press, `onRelease`/`mouseReleased` clear it, `playDownSound` → `SlateSounds.click` unless `silent`. Narration uses the default button text.
- Focus: standard vanilla `FocusNavigationEvent` via AbstractWidget. The focus ring shows only when focused and not hovered.

**Buttons**
- **`SlateButton`**
  - `enum Variant { PRIMARY, SECONDARY, GHOST, DANGER }`; `HEIGHT=20`, `HEIGHT_SMALL=16`, `HEIGHT_LARGE=26`, `PAD=6`.
  - Constructors: `(x,y,w,h,Component label,Runnable onPress)` and `(x,y,w,label,onPress)`.
  - Methods: `variant(Variant)`, `icon(@Nullable Icon)`, `iconSize(int)`, `leftAligned()`, `onPress(Runnable)`, `scrollLongLabels()`, `variant()`, `preferredWidth()`, `activate()`.
  - Enter/Space activates. Protected `drawContent(g,x,y,w,h,fg,shadow)` can be overridden.
- **`SlateIconButton extends SlateButton`**
  - Constructor: `(x,y,int size,Icon,Component label,Runnable)`. Defaults to GHOST; the label becomes the tooltip.
  - Methods: `toggled(boolean)` (accent "on" state), `isToggled()`, `setIcon(Icon)`.
- **`SlateKeybindButton extends SlateButton`**
  - Constructor: `(x,y,w,KeyMapping,@Nullable Consumer<KeyMapping> onChange)`.
  - Methods: `mapping()`, `isCapturing()`, `conflict(boolean)`, `refresh()`, `captureMouse(int button)`.

**Toggles and value inputs**
- **`SlateToggle`**: `(x,y,w,Component label,boolean value,Consumer<Boolean>)`. Methods `value()`, `setValue(boolean)`, `onChange`, `switchFirst()`. `SWITCH_W=22`, `SWITCH_H=12`.
- **`SlateCheckbox`**: `(x,y,w,label,boolean,Consumer<Boolean>)`. Methods `value()`, `setValue`, `onChange`.
- **`SlateSlider`**
  - Constructor: `(x,y,w,Component label,double min,double max,double step,double value,DoubleFunction<String> format,DoubleConsumer onChange)`.
  - Height is 30 (label above the track); `compact(true)` makes it 20.
  - Methods: `onCommit(DoubleConsumer)` (fires on release), `value()`, `setValue`, `min()`, `max()`. Arrow keys work.
- **`SlateDropdown<T>`**
  - Constructor: `(x,y,w,List<T> options,T value,Function<T,Component> labeler,Consumer<T> onChange)`. Height 20.
  - Methods: `label(Component)`, `value()`, `setValue`, `setOptions(List<T>)`, `options()`. Opens a `MenuPopup`.
- **`SlateSegmented<T>`**: same constructor shape as `SlateDropdown`. Methods `value()`, `index()`, `setValue(T)`. Arrow keys work.
- **`SlateColorField`**: `(x,y,w,int color,IntConsumer)`. Methods `color()`, `setColor(int)`, `hexField()`. Opens `ColorPickerPopup`.
- **`SlateSwatches`**: `(x,y,w,List<Palette.AccentPreset>,int selectedColor,IntConsumer onPick)`.

**Text fields.** Neither is a `SlateWidget`.
- **`SlateTextField extends EditBox`**
  - Constructors: `(x,y,w,h,Component narration)` and `(x,y,w,narration)`. `HEIGHT=20`.
  - Methods: `placeholder(Component)`, `icon(Icon)`, `clearButton(boolean)`, `onChange(Consumer<String>)`, `onEnter(Runnable)`, `onEscape(Runnable)`, `maxLength(int)`, `text(String)`, `setInvalid/isInvalid`, `frameX/Y/Width/Height()`.
- **`SlateSearchField extends SlateTextField`**: `(x,y,w,Consumer<String> onChange)`. Search icon, clear button; Esc clears, then unfocuses.

**`SlateTabs`**: the widget the top-tab Config rework will use.
```java
public record Tab(Component label, @Nullable Icon icon, int badge) { Tab(Component); Tab(Component, Icon); }
public static final int HEIGHT = 22;
public SlateTabs(int x,int y,int width,List<Tab> tabs,int selected,IntConsumer onChange)
compact()  index()  tabs()  setBadge(int i,int count)  select(int i)  contentWidth()
```
- **Layout:**
  - By default tabs stretch to share `width` equally.
  - `compact()` sizes each tab to its content: label + 16, plus 14 if it has an icon, plus the badge width.
  - The height is fixed at 22.
- **Behaviour:**
  - `select(i)` is a no-op when out of range or already selected. Otherwise it animates the slide, plays `SlateSounds.tick()` and calls `onChange`.
  - Keys: ←/→ select the neighbouring tab, Home/End jump to the ends.
  - A click picks the tab under the mouse. There is no click sound; the tick replaces it.
- **Dark look:**
  - A 1 px `border` baseline runs under the bar.
  - Hovered, unselected tabs get a `surfaceHover` pixel-round fill.
  - Text is `text` when selected, `textMuted` on hover, `textDim` otherwise; the selected tab's icon is accent-coloured.
  - A 2 px accent underline slides between tabs (200 ms `OUT_CUBIC`).
  - Focus ring on the selected tab.
- **Vanilla look:** vanilla `widget/tab` sprites. The selected tab is raised (unselected ones are drawn 2 px lower); hovered ones cross-fade to highlighted.
- **Not provided:** content switching (the caller rebuilds its content in `onChange`; see the example in `core/client/media/GifLibraryScreen.java`), overflow scrolling, close buttons and vertical tabs.

**Layout containers**
- **`SlateScrollPanel extends AbstractContainerWidget`**
  - Constructor: `(x,y,w,h)`. `BAR_W=4`, `BAR_HIT=8`.
  - Adding: `add(T,relX,relY)`, `add(T)` (keeps absolute position), `addRelative(T)`, `clear()`.
  - Config: `padding(int)`, `background(boolean)`, `edgeFades(boolean)`, `scrollStep(int)`, `setContentHeight(int)`, `contentHeight()`, `innerWidth()`.
  - Scrolling: `maxScroll()`, `scrollAmount()`, `scrollTo(double)`, `scrollBy(double)`, `snapScroll(double)`, `ensureVisible(AbstractWidget)`.
  - Nested panels hand the wheel to their parent.
- **`SlateList<T> extends SlateWidget`** (virtualised)
  - Constructor: `(x,y,w,h,int rowHeight,RowRenderer<T>)`.
  - `RowRenderer.render(GuiGraphics g, T item, int index, int x, int y, int w, int h, boolean hovered, boolean selected, int mouseX, int mouseY)`; optional `default boolean click(T,int index,int x,int y,int w,int h,double mx,double my,int button)`.
  - Methods: `items(List<T>)`, `items()`, `gap(int)`, `rowHeight(int)`, `onSelect/onActivate/onRightClick(Consumer<T>)`, `emptyText(Component)`, `plainRows()`, `selectedIndex()`, `select(int|T)`, `clearSelection()`, `maxScroll()`, `scrollAmount()`, `rowsWidth()`, `scrollTo`, `snapScroll`, `ensureVisible(int)`.
- **`SlateCard extends AbstractContainerWidget`**
  - Constructor: `(x,y,w,h)`.
  - Methods: `<T extends AbstractWidget> T add(T,relX,relY)`, `clearChildren()`, `widgets()`, `onClick(Runnable)`, `onRightClick(Runnable)`, `selected(boolean)`, `isSelected()`, `flat()`, `fill(int argb)`, `keepChildAlpha()`, `playEntrance(int)`, `hover()`.
  - Protected overrides: `renderContent(g,x,y,w,h,mx,my,pt)` and `renderOverlay(...)`.

**Display widgets**
- **`SlateLabel`**
  - `enum Style { HEADING(14), TITLE(12), BODY(10), MUTED(10), CAPTION(10) }`, `enum Align { LEFT, CENTER, RIGHT }`.
  - Constructors: `(x,y,w,Component)` and `(x,y,Component)`.
  - Methods: `style(Style)`, `align(Align)`, `wrap(boolean)`, `color(int)`, `shadow(boolean)`, `text(Component)`, `autoHeight()`, `static heightFor(Component,int w,Style)`.
- **`SlateBadge`**: `(x,y,Component,int color)`, with `color(int)`, `text(Component)`, `static int draw(g,Component,x,y,color)` (10 px pill) and `static int drawCount(g,int count,x,y)`.
- Others:
  - `SlateSeparator(x,y,len,boolean vertical)` and `(x,y,w,Component caption)`
  - `SlateProgress(x,y,w,h)` with `.set/.snap/.indeterminate/.color`
  - `SlateSpinner(x,y,size)`, `static draw(g,x,y,size,color)`
  - `SlateAvatar`

**Overlays and popups**
- **`SlateModal implements Popup`**
  - Constructor: `(Component title,@Nullable Component body,@Nullable Icon)`. `WIDTH=240`.
  - Methods: `button(Component,SlateButton.Variant,Runnable)`, `extra(AbstractWidget)`, `width(int)`, `onDismiss(Runnable)`, `show()`, `close()`.
  - Statics: `confirm(title,body,okLabel,Runnable)`, `confirmDanger(...)`, `info(title,body)`, `prompt(title,body,String initial,Consumer<String>)`.
- **`SlateToasts`** (static)
  - `show(Component title,@Nullable Component body,@Nullable Icon)`, plus overloads `(…, Runnable onClick)` and `(…, Runnable onClick, int lifeMs)`.
  - Also `clear()`, `anyVisible()`. Thread-safe. Rendered on screens and on the HUD.
- **`SlateTooltips`**: `request(List<Component>, AbstractWidget owner)`, `request(Component, owner)`, `requestAt(List<Component>,x,y)`, `hasPending()`. Rendered by the screen hook only, so **not on the HUD**.
- **`SlateSounds`**: `click()`, `click(SoundManager)`, `tick()`, `chime()`.
- **`SlateContextMenu.open(double mx,double my,List<MenuPopup.Item>)`**.
- **`popup/Popup`** interface: `render`, `mouseClicked`, `contains`; defaults `mouseReleased`, `mouseDragged`, `mouseScrolled`, `keyPressed`, `charTyped`, `isModal`, `onClose`, `beginClose`, `closeFinished`.
- **`popup/Popups`** (static):
  - Methods: `open`, `close`, `closeTop`, `closeAll`, `any`, `isOpen`, `top()`.
  - `onScreenChanged` closes every popup when the screen changes.
  - Popups render only through the screen hook, so they need an open screen.
- **`MenuPopup.Item`** factories: `of(label,action)`, `of(label,icon,action)`, `danger`, `disabled`, `checked`, `sep()`.

**`layout/ui` helpers**
- **`Flow`**: `static column(x,y,gap)`, `static row(x,y,gap)`, `place(T)`, `skip(int)`, `x()`, `y()`, `maxX()`, `maxY()`.
- **`Anchor`** enum: `TOP_LEFT TOP TOP_RIGHT LEFT CENTER RIGHT BOTTOM_LEFT BOTTOM BOTTOM_RIGHT` with `x(screenW,ox,w)`, `y(...)`, `offsetX/Y`, `parse`, `nearest`.
- **`Rect(int x,int y,int w,int h)`**: `of`, `right`, `bottom`, `centerX`, `centerY`, `contains`, `inset(all)`, `inset(h,v)`, `withX/Y/W/H`, `move`, `Rect[] splitTop/splitLeft/splitBottom(px)`, `centered(w,h)`, `intersect`.

## 5. Screens (`core/screen/`)

**`SlateScreen extends Screen`** (abstract)
- Constants: `PAD=12`, `HEADER_H=32`, `STAGGER_MS=18`, `STAGGER_MAX_MS=200`.
- Constructor: `protected SlateScreen(Component title, @Nullable Screen parent)`.
- Protected fields: `parent`, `showHeader=true`, `showBack=true`, `panoramaBackground=false`, `maxContentWidth=0`, `slashFocusTarget` (the EditBox that `/` focuses).
- Building:
  - `init()` is **final**. It calls `protected abstract void build()`, adds the back `SlateIconButton` when there is a header and a parent, then on the first build only plays the staggered entrance.
  - `add(T)` (= `addRenderableWidget`), `addHeaderAction(T)` (header right side, filled right-to-left).
  - `public static int entrance(Renderable,int index)`, `nextEntranceDelay()`.
- Rects: `contentRect()`, `headerRect()`, `headerTitleX()`, `renderableList()`.
- Navigation: `back()` calls `Popups.closeAll()` then `setScreen(parent)`. `onClose()` → `back()`. Esc → `back()`, `/` → search field.
- `isPauseScreen()` returns true in a world, so it **pauses singleplayer**. A build menu or radial must override it to return false.
- `renderBackground`:
  - Vanilla skin: panorama with vignette if `panoramaBackground` and no world; otherwise vanilla's background.
  - Dark skin in a world: `renderBlurredBackground` when `blurInGame`, then an `overlay` fill.
  - Dark skin, no world: panorama with a bg veil, or solid `bg` with a 0.3 vignette.
  - Override it for a light radial overlay.
- `render` calls `super.render` (background and widgets), then `renderHeader(g)`, then `renderContent(g,mx,my,pt)` (override the last one).
- Other helpers: `theme()`, `palette()`, `inWorld()`.
- Popups, toasts, tooltips, the editor and transitions are all drawn by the global hook, not by the screen.

**`SidebarScreen extends SlateScreen`** (abstract)
- Constants: `NAV_W=118`, `NAV_W_NARROW=30` (narrow when `width < 420`), `ROW_H=20`, `NAV_TOP=6`, `PAGE_TITLE_H=22`.
- Constructor: `protected SidebarScreen(Component title, @Nullable Screen parent, String rememberKey)`. The last page is remembered in a static map, in memory only.
- `protected abstract void definePages(List<SidebarPage>)`, called once.
- Queries: `pages()`, `currentIndex()`, `currentPage()`, `narrow()`, `navWidth()`, `navRect()`, `pageTitleRect()`, `pageRect()`.
- Page building: `addPageWidget(T)`, `addPageAction(T)`.
- Switching:
  - `showPage(int)` calls `onHide`, `Popups.closeAll`, tears down the page widgets, rebuilds and plays the entrance.
  - Also `showPage(String id)` and `refreshPage()`.
  - Keys: Ctrl+Tab and Ctrl+Shift+Tab cycle; Ctrl+1..9 jump.
- `render` draws background, nav, page title, `page.render`, renderables, header, content. Protected: `renderNav` and `renderPageTitle`.
- **The nav does not scroll, and has no groups, sub-rows or tabs.** Rows are placed at `NAV_TOP + i*ROW_H`, so too many pages overflow.

**`SidebarPage`** (abstract)
- Constructor: `(String id, Component title, Icon icon)`; accessors `id()`, `title()`, `icon()`.
- `badge()`: default 0.
- `abstract void build(SidebarScreen screen, Rect area)`.
- `render(SidebarScreen, GuiGraphics, Rect area, int mx, int my, float pt)`, `tick()`, `onHide()`.

**`ScreenIds`**
- `register(Class<? extends Screen>, String id, String display)`, `of(Screen)`, `of(Class<?>)`, `display(id)`.
- `isVanilla(Screen)`, `isSlate(Screen)` (class name starts with `dev.fallingcloud.slate.`), `isContainer(Screen)` (`AbstractContainerScreen`).
- `guessNamespace(String)`, `known()`.
- Built-in ids cover all vanilla menus and option screens, `minecraft:realms`, `minecraft:packs` and the others listed in the static block.

**`ScreenSwaps`**
- `register(Class, Function<Screen,Screen>)` (exact-class match; returning null keeps the original), `unregister`.
- `registerByName(String className, fn)`, `unregisterByName`.
- `has(Class)`, `runUnswapped(Runnable)`, `@Nullable Screen apply(@Nullable Screen)`.

**`Reskin`**
- `active()` (cached per screen instance), `dark()`, `vanillaMotion()`, `inScope(Screen)`, `invalidate()`.
- Scope comes from `CoreConfig.reskinScope`: `VANILLA_AND_SLATE`, `ALLOWLIST` (default), `ALL_NON_CONTAINER` or `NONE`.
- Container screens are never restyled.
- **Opting out** can only be done through the config: `CoreConfig.reskinDenylist`, a list of class-name prefixes. There is no programmatic API or marker interface.

**`Transitions`**: `onScreenChanged(prev,next)` and `render(g,screen,w,h)`, a 140 ms veil.
- Skipped when `prev` or `next` is null (opening from the game has `prev == null`), and for `ChatScreen`.
- In a world, it only applies when `next` is a Slate screen.

**`reskin/ReskinDraw`** (static painters used by the mixins) and `reskin/ReskinState` (`slate$hoverAnim()`, `slate$pressAnim()`).

## 6. Events (`core/event/`)

**`Event<T>`**: constructor `(String name)`; `register(T)`, `unregister(T)`, `listeners()`, `invoke(Consumer<T>)`, `boolean invokeUntilConsumed(Predicate<T>)`. Exceptions are logged and swallowed.

| Constant | Type / handler | Fired by |
|---|---|---|
| `CLIENT_TICK_END` | `Event<Runnable>` | NF: `ClientTickEvent.Post`. Fabric: `ClientTickEvents.END_CLIENT_TICK` |
| `SCREEN_INIT_POST` | `Consumer<Screen>` | `ScreenMixin`, on `init(Minecraft,II)` TAIL and `rebuildWidgets` TAIL, via `SlateClient.onScreenInit` |
| `SCREEN_OPENED` | `Consumer<Screen>` (null means closed) | `MinecraftMixin` `setScreen` TAIL |
| `SCREEN_RENDER_POST` | `ScreenRender.render(Screen, GuiGraphics, int mouseX, int mouseY, float partialTick)` | `ScreenMixin` `renderWithTooltip` TAIL |
| `HUD_RENDER` | `HudRender.render(GuiGraphics, float partialTick)` | NF: `RenderGuiEvent.Post`. Fabric: `HudRenderCallback`. Both pass `getGameTimeDeltaPartialTick(false)`, so **no DeltaTracker**. Fires from `Gui.render`, so also beneath an open screen in a world, and never with F1 (`hideGui`). |
| `CLIENT_JOINED_SERVER` / `CLIENT_LEFT_SERVER` | `Runnable` | NF: `ClientPlayerNetworkEvent.LoggingIn/Out`. Fabric: `ClientPlayConnectionEvents.JOIN/DISCONNECT` |
| `KEY_PRESSED` | `KeyPress.onKey(int key, int scancode, int modifiers) -> boolean` | See the semantics below |
| `SERVER_STARTED` / `SERVER_STOPPING` / `SERVER_TICK_END` | `Consumer<MinecraftServer>` | NF: `ServerStartedEvent`, `ServerStoppingEvent`, `ServerTickEvent.Post`. Fabric: `ServerLifecycleEvents` / `ServerTickEvents.END_SERVER_TICK` |
| `PLAYER_JOINED` / `PLAYER_LEFT` | `Consumer<ServerPlayer>` | NF: `PlayerLoggedIn/OutEvent`. Fabric: `ServerPlayConnectionEvents.JOIN/DISCONNECT` |

Loader wiring lives in:
- `neoforge/core/src/main/java/.../core/neoforge/SlateNeoForge.java` and `SlateNeoForgeClient.java`
- `fabric/core/src/main/java/.../core/fabric/SlateFabric.java`, `SlateFabricClient.java` and `FabricKeyInput.java`

**`KEY_PRESSED` semantics** (it is unusable for Alt or R on Fabric):
- NeoForge: from `InputEvent.Key`, only when `action == 1` (press, not repeat or release) and `screen == null`.
  - `InputEvent.Key` is **not cancellable** (checked in the NeoForge 21.1.247 sources). Vanilla has already processed the key, so returning true only stops the remaining Slate listeners.
- Fabric: `FabricKeyInput.tick` polls GLFW once per client tick for **F1–F12 only**, with `modifiers` always 0.
- On both loaders: no key-release event, and nothing fires while a screen is open. Core's `KeyboardHandlerMixin` routes screen keys to popups and the editor only; Multiplayer adds its own `KeyboardHandlerMixin` for a screen hotkey.

**`SlateKeys`**
- `CATEGORY = "key.categories.slate"`.
- `register(KeyMapping)` queues until the loader calls `install(Consumer<KeyMapping>)`. NeoForge does that in `RegisterKeyMappingsEvent` (e::register); Fabric uses `KeyBindingHelper::registerKeyBinding`.
- `drain()`.
- Register during init. For keybinds, poll `KeyMapping.consumeClick()` or `isDown()` in `CLIENT_TICK_END`; that is the reliable path on both loaders.

**Not in Core:**
- world or level render stage
- mouse click or scroll in the world
- block break, place or use
- attack or interact
- item tooltip
- HUD event carrying a DeltaTracker
- render-tick or frame-start events
- command registration
- resource reload listeners
- key release

## 7. Networking (`core/net/`)

```java
interface SlateNetwork {
  static SlateNetwork get();
  <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type,
      StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Flow flow, PayloadHandler<T> handler);
  void sendToServer(CustomPacketPayload payload);          // no-op if not connected or server lacks the channel
  void sendToPlayer(ServerPlayer player, CustomPacketPayload payload); // no-op if client lacks the channel
  boolean canSendToPlayer(ServerPlayer player, CustomPacketPayload.Type<?> type);
  boolean serverHasChannel(CustomPacketPayload.Type<?> type);
}
enum Flow { C2S, S2C, BOTH; boolean toServer(); boolean toClient(); }
@FunctionalInterface interface PayloadHandler<T extends CustomPacketPayload> { void handle(T payload, NetContext context); }
record NetContext(boolean isClient, @Nullable ServerPlayer sender) { static client(); static server(ServerPlayer); }
```
- **Payload ids must use the `slate` namespace**, e.g. `Slate.id("building_action")`. `NeoForgeNetwork.register` throws `IllegalArgumentException` otherwise.
- Registering after `RegisterPayloadHandlersEvent` throws `IllegalStateException`, so register in `SlateModule.init()` on both sides.
- NeoForge: a single `event.registrar("1").optional()`. Fabric: `PayloadTypeRegistry.playC2S/playS2C` plus global receivers; client receivers are installed from `SlateFabricClient`.
- Handlers run on the main thread.
- Codecs come from vanilla `StreamCodec`/`ByteBufCodecs`; Core has no helpers.

**Blob channel** (`core/net/blob/`, registered by `Slate.init()`)
- **`BlobPayloads`**:
  - `CHUNK_BYTES = 24_000`
  - `record Start(String id,String kind,int totalBytes,int durationMs,String sender,String target,String meta)`
  - `record Chunk(String id,int index,byte[] data)`
  - `record End(String id)`
  - `validId(String)`
- **`BlobSender`**:
  - `interface Link { boolean ready(); void send(CustomPacketPayload); }`
  - `setLink(Link)`, `serverLink()`, `ready()`, `newId()`
  - `String send(String kind, byte[] bytes, int durationMs, String target, String meta, Consumer<String> whenSent)`
  - `queued()`, `tick()`; paced at 3 chunks per tick
- **`BlobReceiver`**:
  - `record Received(Start start, byte[] bytes)`
  - `onStart(Consumer<Start>)`, `onComplete(Consumer<Received>)`, `onProgress(ProgressListener)`
  - `maxIncomingBytes` = 8 MiB
- **`BlobRelay`** (server):
  - `registerRouter(String prefix, BlobRouter)`
  - `maxBlobBytes` = 3 MiB, `maxChunksPerSecond` = 80, `maxConcurrentTransfers` = 3
- **`BlobRouter`**: `List<ServerPlayer> recipients(ServerPlayer sender, BlobPayloads.Start start)`.
- A blob is a fit for syncing large schematics or copy-paste clipboards between client and server.

## 8. Config

**`core/config/JsonConfig<T>`**
- `GSON` (pretty-printed, no HTML escaping).
- `static <T> JsonConfig<T> of(String name, Class<T>, Supplier<T> defaults)` → `config/slate/<name>.json`.
- `static at(Path, Class<T>, Supplier<T>)`, `static Path dir()`.
- `get()`, `file()`, `load()` (writes the defaults if the file is missing or unreadable), `save()` (atomic), `update(Consumer<T>)` (edits then saves), `reset()`.
- The value type is a POJO with public fields; initialisers act as defaults.

**`CoreConfig`** fields and defaults:
```
String skin = "DARK"
String accent = "#D9805E"
double motion = 1.0
boolean headingFont = true
boolean transitions = true
boolean uiSounds = true
boolean toasts = true
int radius = 3
boolean blurInGame = true
boolean devMode = false
boolean devGrid = true
int devSnap = 4
String reskinScope = "ALLOWLIST"
List<String> reskinAllowlist   (cloth, yacl, configured, neoforge gui, modmenu, controlling, realms, sodium gui, iris gui)
List<String> reskinDenylist    (fancymenu, jei, create, xaero)
boolean isVanillaSkin()
```

**How a module declares its config:** a lazy `JsonConfig.of("<short>", X.class, X::new)` in the module class (see `SlateMenu.configFile()`). A server-side file is a separate name, e.g. `multiplayer-server.json`.

**There is no settings registry in Core.** The Config module hardcodes its pages. What it does have:
- **Page list:** `common/config/.../config/hub/ConfigHubScreen.java` `definePages` is a fixed list:
  1. VideoPage
  2. AudioPage
  3. ControlsPage
  4. `SimplePages.ChatPage`
  5. InterfacePage
  6. `SimplePages.MultiplayerPage`
  7. `SimplePages.AccessibilityPage`
  8. LanguagePage
  9. ResourcePacksPage
  10. ModsPage
  11. one `CuratedPage` per `CuratedPages.load()`, which is where the "DF pack" page comes from (the shipped `resources/slate_config/df.json`, id `df`, title "DF pack")
  12. FavouritesPage
  13. PresetsPage
- **Header actions:** EXTERNAL "vanilla options", UNDO "reset page", and the search field.
- **Video settings buttons:** they are action rows `video:open_sodium` and `video:open_vanilla` in `page/VideoPage.java`.
- **Pages are one long scroll:** `ui/OptionPageBase extends SidebarPage`. Subclasses implement `protected abstract List<Section> sections()`, and all sections render into one `SlateScrollPanel`.
  - `ui/Section`: `of(id,title[,bindings])`, `add(OptionBinding)`, `addAll`, `custom(IntFunction<AbstractWidget>)`, `fixed()`.
- **Generic module surfacing:**
  - `resolver/Resolvers.slateModuleBindings(String module)` turns the top-level fields of `config/slate/<module>.json` into `slate:<module>:<key>` rows.
  - `SimplePages.slateModule(...)` (package-private) uses it for chat and multiplayer only.
  - Core's own fields are typed live bindings in `resolver/CoreBindings`.
- **Extension points for other modules** (require Config as a soft dependency, called from a guarded class):
  - `SlateConfigApi.registerReloadHook(String module, Runnable)`: without a hook, rows show a restart badge.
  - `SlateConfigApi.openHub(Screen parent, String page)` and `hub(...)`.
  - `option.OptionResolvers.register(OptionResolver)`; `OptionResolver { String prefix(); Optional<OptionBinding> resolve(String rest); }`.
  - `OptionResolvers.publish(OptionBinding)` and `publishAll`.
  - `option.Binding.of(id, OptionType, Component)` with `.getter/.setter/.def/.range/.choices/.format/.tooltip/.action/.enabledIf/.restart/.searchWords`.
  - `OptionType { BOOLEAN, INT, DOUBLE, CHOICE, STRING, COLOR, LIST, KEYBIND, ACTION, INFO }`.
  - `config/slate/config/pages/*.json` curated pages (paths like `slate:<module>:<key>`).
- **No "Gameplay" page exists in the Config hub.** The only gameplay section is "Gameplay & HUD" inside the curated DF page. Menu keeps its own settings in `common/menu/.../client/options/SlateOptionsScreen.java` (a `SidebarScreen`), not in the Config hub.

## 9. Platform and registries

**`core/platform/SlatePlatform`** (ServiceLoader via `Services.load`; implemented by `NeoForgePlatform` and `FabricPlatform`, declared in `META-INF/services` in each loader's core module):
- `Loader loader()`
- `boolean isModLoaded(String)`
- `Optional<ModInfo> modInfo(String)`, `List<ModInfo> allMods()`
- `Path configDir()`, `Path gameDir()`
- `boolean isClient()`, `boolean isDedicatedServer()`, `boolean isDevelopmentEnvironment()`
- `Optional<Function<Screen,Screen>> otherModConfigScreen(String modId)`
- `void openUri(URI)`

Supporting types:
- `Loader { NEOFORGE, FABRIC; displayName }`
- `ModInfo(String id,String name,String version,String description,List<String> authors,Optional<Path> iconPath)`
- `Services.load(Class<T>)`: reusable for a module's own loader seam. Precedent: `common/config/.../ConfigPlatform.java`, implemented as `NeoForgeConfigPlatform`/`FabricConfigPlatform`, each with its own `META-INF/services` file.

**No registry abstraction exists anywhere.** A search across `common/`, `neoforge/*/src` and `fabric/*/src` for `Registry`, `DeferredRegister`, `BuiltInRegistries` and `Registries.` finds only Slate's own in-memory registries (`Actions`, `ElementTypes`, `Modules`, `OptionResolvers`). No module registers blocks, items, block entities, menu types, data components, creative tabs, recipes, loot modifiers or commands.

## 10. Core mixins (`core/mixin/`; `slate.mixins.json` is all client, `"mixins": []`, defaultRequire 1)

**Hooks and input routing**
- **`MinecraftMixin`**:
  - `setScreen` HEAD, cancellable: substitutes vanilla's `null → TitleScreen` when there is no level, runs `ScreenSwaps.apply`, and if the result differs, cancels and re-enters `setScreen(swapped)` under a `slate$reentering` guard.
  - `setScreen` TAIL: `SlateClient.onScreenChanged(this.screen)`.
- **`ScreenMixin`**: `init(Minecraft,II)` HEAD/TAIL and `rebuildWidgets` TAIL → `onScreenInit`; `renderWithTooltip` TAIL → `onScreenRendered`. Implements `LayoutApplier.ScreenAccess` (`slate$add`, `slate$remove`).
- **`MouseHandlerMixin`**: `onPress`, `onScroll` and `onMove` at HEAD → `ScreenInput`. **Only when `screen != null`**.
- **`KeyboardHandlerMixin`**: `keyPress` and `charTyped` at HEAD → `ScreenInput`. **Only when `screen != null`**.
- `ScreenInput` routes events to Popups, then toasts, then `EditorOverlay`, plus the F7 editor key. Nothing reaches the in-world game.

**Reskin mixins** (all `require = 0`; they check `Reskin.*`)
- `AbstractWidgetMixin`: lazy `ReskinState` anims.
- `AbstractButtonMixin`, `AbstractSliderButtonMixin`, `EditBoxMixin`, `CheckboxMixin`, `AbstractSelectionListMixin`: wrap `GuiGraphics.blitSprite(RL,IIII)` inside `renderWidget`. The button and slider mixins also modify the text-colour argument.
- `AbstractSelectionListMixin` also: `renderListBackground`, `renderListSeparators`, `renderSelection` at HEAD.
- `ScreenBackgroundMixin`: `renderMenuBackground`, `renderTransparentBackground`, `renderPanorama`, `renderBlurredBackground` at HEAD. Skips Slate screens.
- `TooltipRenderUtilMixin`: `renderTooltipBackground`, both overloads.
- `TabButtonMixin` (`renderWidget` HEAD) and `TabNavigationBarMixin` (`render` HEAD): restyle vanilla `TabNavigationBar` tabs.
- `TitleScreenMixin`: `renderPanorama` TAIL vignette.
- `ScreenLayoutMixin` (priority 1500): `renderWithTooltip` HEAD/ModifyArgs to park the mouse while the editor is open; wraps `render→renderBackground` and `renderBackground` HEAD for layout backgrounds.

## 11. Uncommitted working-tree changes (the user's; do not clobber)

`git diff` on `master` @ `42a3ac9`:
- **`common/core/.../gfx/SlateDraw.java`**: `truncate()` now returns `FormattedCharSequence.EMPTY` when `width` is smaller than the width of "...". Before, it could draw a bare ellipsis. Javadoc expanded.
- **`common/menu/.../MenuConfig.java`**: new fields `boolean confirmQuitGame = true` and `boolean skipOnboarding = true`.
- **`common/menu/.../client/options/SlateOptionsScreen.java`**: two new `cfgToggle` rows, `confirm_quit_game` and `skip_onboarding`.
- **`common/menu/.../client/title/SlateTitleScreen.java`**: Quit calls a new `quit(mc)`, which shows `SlateModal.confirmDanger` when `confirmQuitGame` is set.
- **`common/menu/.../client/title/ContinueCard.java`**:
  - Play becomes a 20 px `SlateIconButton` (PRIMARY) at `(w-28, h-28)`.
  - New `rowRight(...)` stops text short of the button; new `chip(...)` skips mode badges that don't fit.
- **`common/menu/src/main/resources/assets/slate_menu/lang/en_us.json`**: three keys (`title.confirm_quit`, `options.menu.confirm_quit_game`, `options.menu.skip_onboarding`).
- **`common/menu/src/main/resources/slate_menu.mixins.json`**: adds `MinecraftOnboardingMixin`.
- **New, untracked: `common/menu/.../menu/mixin/MinecraftOnboardingMixin.java`**: `@Inject Minecraft.addInitialScreens` HEAD sets `options.onboardAccessibility = false` when `skipOnboarding` is on.
- **`fabric/` and `neoforge/gradle/wrapper/gradle-wrapper.properties`**: line endings only. `git diff --ignore-cr-at-eol` shows nothing.

## Gaps for a building module

1. **No world render hook.** A loader-tree bridge (NF `RenderLevelStageEvent`, Fabric `WorldRenderEvents.AFTER_TRANSLUCENT`/`LAST`) plus a new `SlateEvents` constant is needed for ghost-block previews, selection boxes and mirror planes. Core has no translucent block-model or outline/box helpers either (`LevelRenderer`/`PoseStack`/`MultiBufferSource` code is needed).
2. **No in-world input hooks.**
   - Nothing for mouse clicks or scroll without a screen: area selection needs to intercept right-click, and scroll-to-select in the radial needs to stop hotbar scrolling.
   - Nothing for key release: hold-Alt-then-release has no event.
   - `KEY_PRESSED` is F1–F12 only on Fabric and not cancellable on NeoForge.
   - Options: `KeyMapping` polling plus new common mixins on `MouseHandler.onScroll`/`onPress` (screen == null branch) and `Minecraft.startUseItem`/`handleKeybinds`, or loader events (NF `InputEvent.MouseScrollingEvent`/`InteractionKeyMappingTriggered`/`MouseButton.Pre`, which are cancellable; Fabric has no equivalents, so a mixin is required).
3. **No registries.** Blocks (vertical stairs and slabs, copycats), items (toolbox, tools, upgrades), block entities, data components (toolbox contents, tiers), creative tabs, menu types and recipe serializers all need a new seam, e.g. a module-owned `BuildingPlatform` service, or NF `DeferredRegister` versus Fabric `Registry.register`. Registries also need server-side mixins in the module's `"mixins"` array (Core's is empty).
4. **Joining rules conflict.** DESIGN says joining must never be blocked (`displayTest = "IGNORE_ALL_VERSION"`, optional payloads). A mod that registers blocks and items must be on both sides, so that rule cannot hold for `slate_building`'s content registration. Its payloads must still use the `slate:` namespace.
5. **No container or menu support.**
   - The Reskin skips `AbstractContainerScreen`, and there are no `AbstractContainerMenu` or `MenuType` helpers.
   - A toolbox inventory GUI needs its own menu, screen and sync.
   - BetterInventory integration (extra slot, reworked offhand-Alt) lives outside this repo.
6. **No server gameplay events.** Block break/place/use, attack, item use, loot or drop modification (needed for "variants drop the original block"), recipe or tag reload, datapack, commands, world-save hooks and per-level ticks are all missing. Only `SERVER_STARTED/STOPPING/TICK_END` and `PLAYER_JOINED/LEFT` exist.
7. **HUD limits.**
   - `HUD_RENDER` carries only the partial tick, and fires beneath open screens in a world.
   - Tooltips and popups do not render on the HUD.
   - A non-screen radial HUD would also need a mouse-look suppression hook (`MouseHandler.turnPlayer`).
   - The screen route (a `SlateScreen` radial) works now but must override `isPauseScreen()` (the default pauses singleplayer), `renderBackground` (the default draws blur plus overlay) and `keyReleased`. Movement stops while any screen is open.
8. **Missing drawing primitives.** No arc, annular sector, line, triangle or rotated quad for the wheel. Only rects, pixel-round shapes, `pixelCircle`/`pixelRing` and gradients exist. No item or block-model rendering helper beyond vanilla `GuiGraphics.renderItem`.
9. **Missing widgets.** No scrollable tab strip, grid picker, radial menu, tree or table widget. `SidebarScreen`'s nav does not scroll or group, which matters for the Config merge into Customization, Multiplayer and Language/Accessibility with top tabs.
10. **Config integration.** Core has no module-settings registry. A "Building" page in Config needs either a new Config-side contribution API (none exists; pages are hardcoded in `ConfigHubScreen.definePages`) or a Config-module edit, plus `SlateConfigApi.registerReloadHook("building", ...)` so changes apply live. The keybind page reads `options.keyMappings`, so keys registered through `SlateKeys` appear automatically.
11. **Missing icons.** Nothing for hammer or toolbox, stairs, slab, wall or fence, fill, mirror or rotate, select or area, line or sphere. `tools/icons.py` needs Python and Pillow, and this machine has no Python.