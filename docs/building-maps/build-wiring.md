# Slate loader wiring: scout report (read-only; no files edited, Gradle not run)

Repo root is `Slate/` = `C:/Users/leonr/Coding/MinecraftMods/Source/UI/Slate`. Paths below are relative to it.

## 1. How the build is put together

- **Gradle:** wrapper 9.6.1 in both loaders. The only working-tree change to `gradle-wrapper.properties` is CRLF line endings. `gradle.properties` is the same in both: `-Xmx4G`, `caching=true`, `parallel=false`, `daemon=false`, `mod_version=1.0.0`, `maven_group=dev.fallingcloud`, `minecraft_version=1.21.1`.
  - NeoForge: `neoforge_version=21.1.247`, plugin `net.neoforged.moddev` **2.0.141**.
  - Fabric: `fabric_loader_version=0.19.3`, `fabric_api_version=0.116.13+1.21.1`, plugin `fabric-loom` **1.17.14**.
- **`common/` is not a Gradle project.** Each loader root `build.gradle` has a `subprojects {}` block that attaches the shared sources to every subproject **by project name**:
  ```groovy
  def commonDir = file("${rootDir}/../common/${project.name}/src/main")
  if (commonDir.exists()) {
      sourceSets.main.java.srcDir "${commonDir}/java"
      sourceSets.main.resources.srcDir "${commonDir}/resources"
  }
  ```
  So the Gradle project must be named `building`, and the shared tree must be `common/building/src/main/{java,resources}`.
- **Other things the root `subprojects {}` block does (both loaders):**
  - Applies `java` plus the loader plugin.
  - Sets Java release 21, UTF-8, `-Xlint:-options`.
  - Adds the repositories: mavenCentral, Modrinth (`maven.modrinth` group only), BlameJared, and on Fabric also TerraformersMC.
  - Copies `LICENSE` into each jar as `LICENSE_slate-<project>`.
  - `processResources` expands `${version}`. NeoForge does this only in `META-INF/neoforge.mods.toml` and excludes `fabric.mod.json`. Fabric does it only in `fabric.mod.json` (`escapeBackslash = true`) and excludes `META-INF/neoforge.mods.toml`.
  - NeoForge sets `neoForge { version = rootProject.neoforge_version }`.
  - Fabric declares `minecraft`, `mappings loom.officialMojangMappings()`, `modImplementation fabric-loader`, and **`modImplementation "net.fabricmc.fabric-api:fabric-api:${fabric_api_version}"`** (the full umbrella artifact) for every subproject.
- **`ext.slateLibsDir`** (both roots) is the first directory that exists among: `-Pslate.libs=<dir>`, `${rootDir}/../../../libs`, `C:/Users/leonr/Coding/MinecraftMods/Source/libs`.
- **`settings.gradle`** (both loaders): `include 'core', 'menu', 'multiplayer', 'chat', 'config', 'dev'`.
- **No access transformers or access wideners exist anywhere**: no `accesstransformer.cfg`, no `*.accesswidener`, no `accessWidenerPath`, no `"accessWidener"` in any `fabric.mod.json`. Private vanilla members are reached with accessor mixins, e.g. `common/menu/.../mixin/OptionsScreenAccessor.java` (`@Accessor("lastScreen")`).
- **Fabric jars have no refmap.** Loom 1.17 remaps mixins with tiny-remapper, so the mixin JSONs are shipped as they are.
- **Mixin configs** live in `common/<module>/src/main/resources/slate_<module>.mixins.json` (core's is `slate.mixins.json`). Shared shape:
  ```json
  {"required": true, "minVersion": "0.8", "package": "dev.fallingcloud.slate.<module>.mixin",
   "compatibilityLevel": "JAVA_21", "mixins": [], "client": [...], "injectors": {"defaultRequire": 1}}
  ```
  They are referenced from the NeoForge `[[mixins]] config = ...` entry and from the Fabric `"mixins": [...]` array.

## 2. Existing modules: every loader-side file

### core (`slate`, both sides)

**NeoForge**
- `neoforge/core/build.gradle`: `archivesName 'slate-core-neoforge-1.21.1'`, `neoForge { mods { slate { sourceSet sourceSets.main } } }`, no dependencies.
- `neoforge/core/src/main/java/dev/fallingcloud/slate/core/neoforge/SlateNeoForge.java` (`@Mod(Slate.MOD_ID)`), constructor `SlateNeoForge(IEventBus modBus, ModContainer container)`:
  - calls `Slate.init()`;
  - `modBus.addListener(RegisterPayloadHandlersEvent.class, NeoForgeNetwork::flush)`;
  - forwards `ServerStartedEvent`, `ServerStoppingEvent`, `ServerTickEvent.Post`, `PlayerEvent.PlayerLoggedInEvent` and `PlayerEvent.PlayerLoggedOutEvent` (all on `NeoForge.EVENT_BUS`) to `SlateEvents` (table in section 3);
  - `if (FMLEnvironment.dist.isClient()) SlateNeoForgeClient.init(modBus, container)`.
- `.../core/neoforge/SlateNeoForgeClient.java` (package-private, `static void init(IEventBus, ModContainer)`):
  - calls `SlateClient.init()`;
  - **keybinding flush:** `modBus.addListener(RegisterKeyMappingsEvent.class, e -> SlateKeys.install(e::register))`;
  - **config screen:** `container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new CoreSettingsScreen(parent))`;
  - forwards ClientTickEvent.Post, RenderGuiEvent.Post, ClientPlayerNetworkEvent.LoggingIn/LoggingOut and InputEvent.Key (section 3).
- `.../core/neoforge/NeoForgeNetwork.java` implements `SlateNetwork`:
  - `register(...)` queues `Pending` records, throws if the id namespace is not `slate`, and throws if called after the flush;
  - `static void flush(RegisterPayloadHandlersEvent event)` uses `event.registrar("1").optional()` and calls `playToServer`, `playToClient` or `playBidirectional`;
  - sending goes through `PacketDistributor`; channel checks use `NetworkRegistry.hasChannel`.
- `.../core/neoforge/NeoForgePlatform.java` implements `SlatePlatform` via `ModList`, `FMLPaths`, `FMLEnvironment` and `FMLLoader`. Its nested `NeoForgeConfigScreens` reads `IConfigScreenFactory` for `otherModConfigScreen`.
- `neoforge/core/src/main/resources/META-INF/services/dev.fallingcloud.slate.core.platform.SlatePlatform` → `dev.fallingcloud.slate.core.neoforge.NeoForgePlatform`.
- `.../META-INF/services/dev.fallingcloud.slate.core.net.SlateNetwork` → `dev.fallingcloud.slate.core.neoforge.NeoForgeNetwork`.
- `.../META-INF/neoforge.mods.toml`: `displayTest = "IGNORE_ALL_VERSION"`, `[[mixins]] config = "slate.mixins.json"`, dependencies `neoforge [21.1,)` and `minecraft [1.21.1,1.22)`.

**Fabric**
- `fabric/core/build.gradle`: `archivesName 'slate-core-fabric-1.21.1'`, `loom { mods { slate { sourceSet sourceSets.main } } }`, `modCompileOnly "com.terraformersmc:modmenu:11.0.3"`.
- `fabric/core/src/main/java/dev/fallingcloud/slate/core/fabric/SlateFabric.java` (`ModInitializer`): `onInitialize()` calls `Slate.init()` and forwards the ServerLifecycleEvents, ServerTickEvents and ServerPlayConnectionEvents listed in section 3.
- `.../SlateFabricClient.java` (`ClientModInitializer`), `onInitializeClient()`:
  - `SlateClient.init()`;
  - `SlateKeys.install(KeyBindingHelper::registerKeyBinding)`;
  - `FabricNetwork.installClient()`;
  - ClientTickEvents.END_CLIENT_TICK (also calls `FabricKeyInput.tick(mc)`), HudRenderCallback.EVENT, ClientPlayConnectionEvents.JOIN/DISCONNECT.
- `.../FabricNetwork.java`: `PayloadTypeRegistry.playC2S()` / `playS2C().register(type, codec)` plus `ServerPlayNetworking.registerGlobalReceiver`. Client receivers are queued in `CLIENT_PENDING` until `installClient()`.
- `.../FabricClientNetwork.java`: client-only half, uses `ClientPlayNetworking`.
- `.../FabricKeyInput.java`: polls GLFW for **F1–F12 only** and fires `KEY_PRESSED`.
- `.../FabricPlatform.java`, `ModMenuBridge.java` (reads other mods' ModMenu factories), `SlateModMenu.java` (`ModMenuApi` → `CoreSettingsScreen::new`).
- `META-INF/services/...SlatePlatform` → `dev.fallingcloud.slate.core.fabric.FabricPlatform`; `...SlateNetwork` → `dev.fallingcloud.slate.core.fabric.FabricNetwork`.
- `fabric.mod.json`: `"environment": "*"`, entrypoints `main` / `client` / `modmenu`, `"mixins": ["slate.mixins.json"]`, depends on `fabricloader >=0.16.0`, `minecraft ~1.21.1`, `java >=21`, `fabric-api *`.

### menu (`slate_menu`, client only)

**NeoForge**
- `neoforge/menu/build.gradle`: `archivesName 'slate-menu-neoforge-1.21.1'`, `mods { slate_menu {...} }`, `implementation project(':core')`.
- `.../menu/neoforge/SlateMenuNeoForge.java`: `@Mod(value = SlateMenu.MOD_ID, dist = Dist.CLIENT)`; the constructor `(IEventBus, ModContainer)` only calls `Modules.register(SlateMenu.MODULE)`.
- `neoforge.mods.toml`: `[[mixins]] slate_menu.mixins.json`; depends on `slate` (required, AFTER, side CLIENT), neoforge and minecraft.

**Fabric**
- `fabric/menu/build.gradle`: `implementation project(path: ':core', configuration: 'namedElements')`.
- `.../menu/fabric/SlateMenuFabric.java` (`ClientModInitializer`) calls `Modules.register(SlateMenu.MODULE)`.
- `fabric.mod.json`: `"environment": "client"`, `client` entrypoint only, depends on `slate >=1.0.0`.
- No config screen factory. Mixins: accessors plus the new, untracked `MinecraftOnboardingMixin`.

### config (`slate_config`, client only)

**NeoForge**
- `neoforge/config/build.gradle`: `implementation project(':core')` and `compileOnly fileTree(dir: rootProject.slateLibsDir, include: ['sodium*.jar','iris*.jar','voicechat*.jar'])`.
- `.../config/neoforge/SlateConfigNeoForge.java`: `@Mod(value = SlateConfig.MOD_ID, dist = Dist.CLIENT)`. It calls `Modules.register(...)` and `container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> SlateConfigApi.hub(parent, null))`.
- `NeoForgeConfigPlatform.java` + `NeoForgeSpecDocument.java`: the module's own ServiceLoader seam `dev.fallingcloud.slate.config.ConfigPlatform`, registered in `META-INF/services/dev.fallingcloud.slate.config.ConfigPlatform`. It exposes `ModConfigs` / `ModConfigSpec` as editable documents.
- `neoforge.mods.toml`: optional `sodium [0.6,)`.

**Fabric**
- `fabric/config/build.gradle`: adds `modCompileOnly modmenu:11.0.3` and the same `fileTree`.
- `SlateConfigFabric` (`ClientModInitializer` → `Modules.register`).
- `SlateConfigModMenu` (`ModMenuApi` → `SlateConfigApi.hub(parent, null)`).
- `FabricConfigPlatform` (returns empty) and its services file.
- `fabric.mod.json`: entrypoints `client` and `modmenu`; suggests `sodium` and `modmenu`.

### multiplayer (`slate_multiplayer`, both sides, networking)

**NeoForge**
- `neoforge/multiplayer/build.gradle`: `implementation project(':core')` and `compileOnly fileTree(... ['voicechat*.jar','mcef*.jar'])`.
- `.../multiplayer/neoforge/SlateMultiplayerNeoForge.java`: `@Mod(value = SlateMultiplayer.MOD_ID)` with **no `dist`**; it only calls `Modules.register`.
- `neoforge.mods.toml`: `slate` required, side **BOTH**; `voicechat` / `mcef` optional, side CLIENT.

**Fabric**
- `SlateMultiplayerFabric` (`ModInitializer`, `main`) calls `Modules.register`.
- `SlateMultiplayerFabricClient` (`ClientModInitializer`, `client`) is **empty**, because `initClient` already ran from `main`.
- `fabric.mod.json`: `"environment": "*"`.

**Networking lives in common code**, in `SlateMultiplayer.init()`:
```java
SlateNetwork.get().register(SocialPayload.TYPE, SocialPayload.CODEC, Flow.BOTH, (payload, ctx) -> { if (ctx.isClient()) ClientSide.onPayload(payload); else SocialHub.onPayload(payload.message(), ctx.sender()); });
```
- `TYPE = new Type<>(Slate.id("social"))`, so the id namespace is **`slate`**.
- Client-only references sit in a nested `private static final class ClientSide`.

### chat (`slate_chat`, both sides)

Same shape as multiplayer. `neoforge/chat/build.gradle` and `fabric/chat/build.gradle` add a module-to-module dependency:
- NeoForge: `implementation project(':multiplayer')`.
- Fabric: `implementation project(path: ':multiplayer', configuration: 'namedElements')`.

### dev (runner only)

- **`neoforge/dev/build.gradle`:**
  - `evaluationDependsOn(':core' | ':menu' | ':multiplayer' | ':chat' | ':config')`;
  - `neoForge { mods { slate {sourceSet project(':core').sourceSets.main}; slate_menu {...}; ... } }`;
  - `runs { client { client(); gameDirectory = file('run'); <harness props> }; server { server(); gameDirectory = file('run-server'); programArgument '--nogui' } }`;
  - `implementation project(':x')` for every module; `jar` disabled.
- **`fabric/dev/build.gradle`:**
  - `loom { runs { client { client(); runDir 'run'; <harness vmArgs> }; server { server(); runDir 'run-server' } } }`;
  - `implementation project(path: ':x', configuration: 'namedElements')` for every module;
  - **no `loom.mods {}` block**, so it relies on the sibling dev jars in `build/devlibs`;
  - `jar` and `remapJar` disabled.
  - **`fabric/dev/run` does not exist: the Fabric dev client has never been launched.** The morning report says Fabric was "build and remap; never launched".

### Bootstrap semantics (common, `core.module`)

- `Modules.register(SlateModule m)`: calls `Slate.init()`, then `putIfAbsent`, `m.init()`, and `Actions.register` for each of `m.actions()`. Then, **if `SlatePlatform.get().isClient()`, it calls `m.initClient()` immediately.**
- `SlateModule`: `String id()`, `Component displayName()`, `Icon icon()`, `default void init()`, `default void initClient()`, `default List<HubEntry> hubEntries()`, `default List<ActionType> actions()`, and `record HubEntry(Component label, Icon icon, Runnable onClick)`.
- **Ordering on NeoForge:** core's constructor runs `SlateClient.init()` before any dependent module is constructed (because of `ordering = "AFTER"`).
- **Ordering on Fabric:** all `main` entrypoints run before any `client` entrypoint. A both-sides module's `initClient()` therefore runs **before** `SlateFabricClient` calls `SlateClient.init()` and `SlateKeys.install`. Keys are queued, so that part is safe. `SlateClient.init()` is `public static synchronized` and idempotent, so it can be called first if needed.

## 3. Where `SlateEvents` are fired

`common/core/.../core/event/SlateEvents.java` holds `Event<T>` fields and uses `invoke(Consumer)` or `invokeUntilConsumed(Predicate)`.

| Event | NeoForge (file → call) | Fabric (file → call) |
|---|---|---|
| `SERVER_STARTED` | `SlateNeoForge` ctor → `ServerStartedEvent` | `SlateFabric.onInitialize` → `ServerLifecycleEvents.SERVER_STARTED` |
| `SERVER_STOPPING` | `ServerStoppingEvent` | `ServerLifecycleEvents.SERVER_STOPPING` |
| `SERVER_TICK_END` | `ServerTickEvent.Post` | `ServerTickEvents.END_SERVER_TICK` |
| `PLAYER_JOINED` / `PLAYER_LEFT` | `PlayerEvent.PlayerLoggedInEvent` / `PlayerLoggedOutEvent` | `ServerPlayConnectionEvents.JOIN` / `DISCONNECT` |
| `CLIENT_TICK_END` | `SlateNeoForgeClient.init` → `ClientTickEvent.Post` | `SlateFabricClient` → `ClientTickEvents.END_CLIENT_TICK` |
| `HUD_RENDER` | `RenderGuiEvent.Post` → `SlateClient.onHudRendered(g, e.getPartialTick().getGameTimeDeltaPartialTick(false))` | `HudRenderCallback.EVENT` → same method |
| `CLIENT_JOINED_SERVER` / `CLIENT_LEFT_SERVER` | `ClientPlayerNetworkEvent.LoggingIn` / `LoggingOut` | `ClientPlayConnectionEvents.JOIN` / `DISCONNECT` |
| `KEY_PRESSED` | `InputEvent.Key`: only `action==1` and `screen==null`, **every key** | `FabricKeyInput.tick`: **F1–F12 only** |
| `SCREEN_OPENED` | common: `core/mixin/MinecraftMixin` `setScreen` TAIL → `SlateClient.onScreenChanged` | same |
| `SCREEN_INIT_POST` | common: `core/mixin/ScreenMixin` `init(Minecraft,int,int)` TAIL and `rebuildWidgets` TAIL → `SlateClient.onScreenInit` | same |
| `SCREEN_RENDER_POST` | common: `ScreenMixin` `renderWithTooltip` TAIL → `SlateClient.onScreenRendered` | same |

**Implication for key handling:** Alt and R must not rely on `KEY_PRESSED`, because on Fabric it never fires for them. Use a `KeyMapping` registered with `SlateKeys.register` (in `initClient`) and poll `isDown()` / `consumeClick()` in `CLIENT_TICK_END` or per frame. The alternative is a common mixin on `KeyboardHandler.keyPress`. Note that core's own `KeyboardHandlerMixin`, `MouseHandlerMixin` and multiplayer's keyboard mixin all return early when `minecraft.screen == null`, so in-world input is not intercepted by Slate today.

### Adding new events

Two ways:
- **(a)** A new Core file (for example `core/event/SlateWorldEvents.java`) with `Event<...>` fields and functional interfaces, like `SlateEvents.ScreenRender`. Keep client types inside the nested interfaces so the class still loads on a server. Fire it from `SlateNeoForgeClient.init` and `SlateFabricClient.onInitializeClient`. The brief forbids editing `Slate.java` and `SlateClient.java`, not these loader files.
- **(b)** Keep Core untouched: subscribe in building's own loader entry classes and call common building code.

Verified APIs for each case:
- **World render after translucent:**
  - NeoForge: `NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.class, e -> { if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) ... })`. Getters: `getPoseStack()`, `getModelViewMatrix()`, `getProjectionMatrix()`, `getPartialTick()` (a `DeltaTracker`), `getCamera()`, `getFrustum()`, `getLevelRenderer()`. The stages are `AFTER_SKY` … `AFTER_TRANSLUCENT_BLOCKS`, `AFTER_TRIPWIRE_BLOCKS`, `AFTER_PARTICLES`, `AFTER_WEATHER`, `AFTER_LEVEL`.
  - Fabric: `WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> ...)`. The context has `matrixStack()`, `positionMatrix()`, `projectionMatrix()`, `camera()`, `tickCounter()`, `consumers()`, `frustum()`, `world()`. Also available: `START`, `AFTER_SETUP`, `BEFORE_ENTITIES`, `AFTER_ENTITIES`, `BEFORE_BLOCK_OUTLINE`, `BLOCK_OUTLINE`, `BEFORE_DEBUG_RENDER`, `LAST`, `END`.
  - In 1.21.1 the view rotation lives in the model-view matrix, not in the PoseStack. Pass both through and check in game.
- **Block outline replacement:** NeoForge `RenderHighlightEvent.Block` (cancellable); Fabric `WorldRenderEvents.BEFORE_BLOCK_OUTLINE` (return false to cancel).
- **In-world mouse scroll:**
  - NeoForge: `InputEvent.MouseScrollingEvent` (cancellable; `getScrollDeltaY()`, `isLeftDown()`, `isRightDown()`).
  - Fabric has **no API** for this; `ScreenMouseEvents` only covers screens.
  - Recommended for both loaders: one common mixin on vanilla `MouseHandler` — `private void onScroll(long, double, double)` at HEAD, cancellable, active when `screen == null`.
- **In-world mouse button:**
  - NeoForge: `InputEvent.MouseButton.Pre` (fields `getButton`, `getAction`, `getModifiers`), or `InputEvent.InteractionKeyMappingTriggered` (cancellable; `isAttack()`, `isUseItem()`, `isPickBlock()`, `getHand()`).
  - Fabric: `ClientPreAttackCallback.EVENT` (`boolean onClientPlayerPreAttack(Minecraft, LocalPlayer, int clickCount)`), `UseBlockCallback.EVENT`, `AttackBlockCallback`, `UseItemCallback`.
  - Or a common mixin on `MouseHandler.onPress(long,int,int,int)` or on `Minecraft`'s private `startUseItem()`, `startAttack()`, `continueAttack(boolean)`, `pickBlock()` (all verified in the 1.21.1 mojmap jar).
- **Register blocks and items:** nothing exists yet (section 5).
- **Server-side placement and break:** NeoForge `BlockEvent.EntityPlaceEvent`, `BlockEvent.BreakEvent`, `PlayerInteractEvent.RightClickBlock` / `LeftClickBlock`. Fabric `UseBlockCallback`, `PlayerBlockBreakEvents`.

## 4. What is on the compile classpath

- **NeoForge 21.1.247** (MDG 2.0.141). The patched Minecraft jar to run `javap` against is `neoforge/<module>/build/moddev/artifacts/neoforge-21.1.247.jar`; it includes NeoForge's ATs. Verified classes: `RenderLevelStageEvent(+$Stage)`, `InputEvent$MouseScrollingEvent`, `InputEvent$MouseButton$Pre/Post`, `InputEvent$InteractionKeyMappingTriggered`, `RenderHighlightEvent$Block`, `RegisterEvent`, `DeferredRegister(+$Blocks/$Items/$DataComponents)`, `BuildCreativeModeTabContentsEvent`, `RegisterMenuScreensEvent`, `ModifyDefaultComponentsEvent`, `IGlobalLootModifier`, `AddReloadListenerEvent`, `TagsUpdatedEvent`, `RegisterClientExtensionsEvent`, `RegisterGuiLayersEvent`, `PlayerInteractEvent$*`, `BlockEvent$*`. `boot-df.ps1` targets pack NeoForge `neoforge-21.1.250`; the mods.toml files require `[21.1,)`.
- **Fabric API is the full umbrella artifact** `fabric-api:0.116.13+1.21.1`, `modImplementation` on every subproject. Module versions from the cached POM:
  - Rendering: `fabric-rendering-v1 5.1.0` (WorldRenderEvents, HudRenderCallback, BlockEntityRendererRegistry, ColorProviderRegistry), `fabric-renderer-api-v1 3.4.1` (FRAPI: Renderer, MeshBuilder, QuadEmitter, FabricBakedModel), `fabric-renderer-indigo 1.7.1`, `fabric-model-loading-api-v1 2.1.0` (ModelLoadingPlugin, ModelModifier, BlockStateResolver), `fabric-blockrenderlayer-v1 1.1.52` (`BlockRenderLayerMap.INSTANCE.putBlock(Block, RenderType)`).
  - Registries and content: `fabric-registry-sync-v0 5.3.2`, `fabric-item-group-api-v1 4.1.7` (FabricItemGroup, `ItemGroupEvents.modifyEntriesEvent(ResourceKey<CreativeModeTab>)`), `fabric-screen-handler-api-v1 1.3.91` (ExtendedScreenHandlerType), `fabric-loot-api-v3 1.0.3` (`LootTableEvents.MODIFY/REPLACE/ALL_LOADED`), `fabric-object-builder-api-v1 15.2.1`, `fabric-recipe-api-v1 5.0.16`, `fabric-resource-conditions-api-v1 4.3.0`, `fabric-content-registries-v0 8.0.19`, `fabric-data-attachment-api-v1 1.4.7`, `fabric-transfer-api-v1 5.4.4`, `fabric-api-lookup-api-v1 1.6.72`.
  - Input and screens: `fabric-events-interaction-v0 0.7.14` (UseBlockCallback, AttackBlockCallback, UseItemCallback, ClientPreAttackCallback, ClientPickBlock*, PlayerBlockBreakEvents), `fabric-key-binding-api-v1`, `fabric-screen-api-v1`.
  - Other: `fabric-networking-api-v1 4.3.1`, `fabric-lifecycle-events-v1 2.6.0`, `fabric-block-api-v1`, `fabric-data-generation-api-v1`, `fabric-gametest-api-v1`, `fabric-transitive-access-wideners-v1 6.2.0`.
  - `fabric-api-deprecated` adds `loot-api-v2`, `rendering-v0`, `keybindings-v0`, `command-api-v1`, `commands-v0`, `convention-tags-v1`, `renderer-registries-v1`, `rendering-data-attachment-v1`.
- **Gotcha: the two loaders see different vanilla visibility.** Fabric's transitive access wideners open some vanilla members that NeoForge keeps private. Verified: `MenuScreens.register(...)` is `public static` in the Fabric-mapped jar but `private static` in `neoforge-21.1.247.jar`. Common code that compiles on Fabric can therefore fail on NeoForge, so always build both. Also verified: `MenuType(MenuSupplier, FeatureFlagSet)`, `StairBlock(BlockState, Properties)`, `SlabBlock(Properties)`, `WallBlock(Properties)`, `FenceBlock(Properties)` and `CreativeModeTab.builder(Row, int)` are public on both.

## 5. Game-content registration today: none

No module registers any blocks, items, block entities, menus/container screens, data components, creative tabs, recipes or loot modifiers. There is no `DeferredRegister`, no `RegisterEvent`, no `Registry.register`, and no `data/` folder in any `common/*/resources`. The only registrations are:
- payloads, through `SlateNetwork`;
- key mappings, through `SlateKeys`;
- `IConfigScreenFactory` and ModMenu screen factories (core and config only);
- `ScreenSwaps`, `ScreenIds`, `CoreActions.SCREEN_FACTORIES`, layout element types and actions.

So `slate_building` will be the first content mod in the suite. What that means:
- **NeoForge freezes its registries outside `RegisterEvent`.** `MappedRegistry` has `validateWrite`, `freeze` and `unfreeze`, and `GameData.postRegisterEvents` fires one `RegisterEvent` per registry. So common `init()` (which runs during mod construction) must **queue** entries, and the NeoForge entry must flush them with `modBus.addListener(RegisterEvent.class, e -> e.register(Registries.BLOCK, helper -> ...))`. The method signatures are `<T> void register(ResourceKey<? extends Registry<T>>, ResourceLocation, Supplier<T>)` and `<T> void register(ResourceKey<? extends Registry<T>>, Consumer<RegisterHelper<T>>)`.
- **Fabric** can call `Registry.register(BuiltInRegistries.X, id, obj)` directly from `onInitialize` (register blocks before their BlockItems).
- Suggested seam, in the style of `SlateNetwork`: a common `BuildingRegistry` that collects `(ResourceKey<Registry<T>>, ResourceLocation, Supplier<T>)`, flushed by each loader entry. Alternatively a new Core service `core.registry.SlateRegistries` with ServiceLoader implementations in `neoforge/core` and `fabric/core`.
- **Loader-specific pieces:**
  - Menu screens: NeoForge `RegisterMenuScreensEvent`; Fabric `MenuScreens.register` (widened) or `ExtendedScreenHandlerType`.
  - Creative tab contents: `BuildCreativeModeTabContentsEvent` / `ItemGroupEvents`.
  - Loot: NeoForge GLM serializer registry plus `data/neoforge/loot_modifiers/global_loot_modifiers.json`; Fabric `LootTableEvents.MODIFY`.
  - Block render layer: NeoForge uses `"render_type"` in the model JSON; Fabric needs `BlockRenderLayerMap.INSTANCE.putBlock` in the client entrypoint.
  - 1.21.1 datapack folders are singular: `data/slate_building/{recipe,loot_table,tags/block,tags/item}`.
- **Joining policy conflict:** DESIGN.md says joining must never be blocked, but synced registries mean client and server must both have `slate_building`. `displayTest = "IGNORE_ALL_VERSION"` cannot help once registries differ; Fabric's registry sync will also disconnect. The lead should choose `MATCH_VERSION` (or the default) for this module.
- **Payload ids must use the `slate` namespace** (e.g. `Slate.id("building_action")`). `NeoForgeNetwork` and `FabricNetwork` throw otherwise, and NeoForge throws if you register after `RegisterPayloadHandlersEvent`, so register in `init()`.

## 6. Checklist to add `:building` (mod id `slate_building`, package `dev.fallingcloud.slate.building`)

### Common tree
1. `common/building/src/main/java/dev/fallingcloud/slate/building/SlateBuilding.java`, implementing `SlateModule` the same way as `SlateMultiplayer`:
   - `MOD_ID = "slate_building"`, `LOGGER`, singleton `MODULE`, `id(String path)` helper;
   - `displayName()` returns `Component.translatable("slate_building.name")`; `icon()` returns an existing `Icon` constant;
   - `init()`: `Slate.init()`, then config (`JsonConfig.of("building", ...)` → `config/slate/building.json`), payloads, server `SlateEvents` hooks, queued registry entries;
   - `initClient()`: goes through a nested `ClientSide` class.
2. `common/building/src/main/resources/slate_building.mixins.json`, package `dev.fallingcloud.slate.building.mixin`. No helper classes inside the mixin package.
3. `common/building/src/main/resources/assets/slate_building/lang/en_us.json` with `slate_building.name` and key-category/keybinding names.
4. `.../assets/slate_building/icon.png`. The generator is `tools/modicons.py`: add `motif("building", "slate_building", """...32x32...""")`. **Python is not installed on this machine** (only the Store stub), so that script cannot run as things stand.
5. Blockstates, models, textures and `data/slate_building/...` go under the same resources root.

### NeoForge
6. `neoforge/settings.gradle`: add `'building'` to `include`.
7. `neoforge/building/build.gradle`:
   ```groovy
   base { archivesName = 'slate-building-neoforge-1.21.1' }
   neoForge { mods { slate_building { sourceSet sourceSets.main } } }
   dependencies {
       implementation project(':core')
       // soft deps, e.g.: compileOnly fileTree(dir: rootProject.slateLibsDir, include: ['create*.jar','betterinventory*.jar'])
   }
   ```
8. `neoforge/building/src/main/resources/META-INF/neoforge.mods.toml`: copy the multiplayer one (`modLoader`, `loaderVersion`, `license`, `[[mods]] modId="slate_building"`, `version="${version}"`, `displayName="Slate Building"`, `authors="falling_colud"`, `logoFile="assets/slate_building/icon.png"`, `displayTest` per section 5, description, `[[mixins]] config="slate_building.mixins.json"`, dependencies `slate` required/AFTER/BOTH, `neoforge [21.1,)`, `minecraft [1.21.1,1.22)`, plus optional soft deps).
9. `neoforge/building/src/main/java/dev/fallingcloud/slate/building/neoforge/SlateBuildingNeoForge.java`, `@Mod(SlateBuilding.MOD_ID)` with no `dist`, constructor `(IEventBus modBus, ModContainer container)`:
   - `Modules.register(SlateBuilding.MODULE)`;
   - `modBus.addListener(RegisterEvent.class, ...)` flush (and `BuildCreativeModeTabContentsEvent` if needed);
   - `if (FMLEnvironment.dist.isClient()) SlateBuildingNeoForgeClient.init(modBus, container)`.
10. `.../neoforge/SlateBuildingNeoForgeClient.java` (package-private, only loaded on the client dist):
    - `RenderLevelStageEvent`, `InputEvent.*`, `RenderHighlightEvent.Block`, `RegisterMenuScreensEvent`;
    - optionally `container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> ...)`. If this opens `SlateConfigApi.hub(parent, "building")`, the module needs `compileOnly project(':config')` and a `Modules.isLoaded("slate_config")` guard.
11. `neoforge/dev/build.gradle`: add `evaluationDependsOn(':building')`, `slate_building { sourceSet project(':building').sourceSets.main }` inside `neoForge.mods`, and `implementation project(':building')`.

### Fabric
12. `fabric/settings.gradle`: add `'building'`.
13. `fabric/building/build.gradle`:
    ```groovy
    base { archivesName = 'slate-building-fabric-1.21.1' }
    loom { mods { slate_building { sourceSet sourceSets.main } } }
    dependencies {
        implementation project(path: ':core', configuration: 'namedElements')
        // modCompileOnly "com.terraformersmc:modmenu:11.0.3"   // only with a modmenu entrypoint
    }
    ```
14. `fabric/building/src/main/resources/fabric.mod.json`: copy multiplayer's, with `"id": "slate_building"`, `"environment": "*"`, entrypoints `main: dev.fallingcloud.slate.building.fabric.SlateBuildingFabric`, `client: ...SlateBuildingFabricClient` (and optionally `modmenu`), `"mixins": ["slate_building.mixins.json"]`, depends `fabricloader >=0.16.0`, `minecraft ~1.21.1`, `java >=21`, `fabric-api *`, `slate >=1.0.0`.
15. `.../building/fabric/SlateBuildingFabric.java` (`ModInitializer`): `Modules.register(...)`, then `Registry.register` the queued entries (blocks first).
16. `.../building/fabric/SlateBuildingFabricClient.java` (`ClientModInitializer`): `WorldRenderEvents.*`, `BlockRenderLayerMap`, `MenuScreens.register` (and `SlateClient.init()` first if the client code needs it ready).
17. `fabric/dev/build.gradle`: add `implementation project(path: ':building', configuration: 'namedElements')`.

### Tooling and docs
18. `tools/install-df.ps1`: add `"building"` to `foreach ($m in @("core","menu","multiplayer","chat","config"))`.
19. `README.md` module table and `docs/building.md`. The DESIGN.md module table is lead-owned.
20. Config integration needs no compile dependency. The Config module reads `config/slate/<module>.json` generically through `Resolvers.slateModuleBindings(module)` / `slateModuleBinding(module, key)`. Precedent: `SimplePages.ChatPage` shows it when `Modules.isLoaded("slate_chat")` via `slateModule("chat","slate_chat",titleKey)`. Pages are added in `ConfigHubScreen.definePages(List<SidebarPage>)`. For live apply use `SlateConfigApi.registerReloadHook(String module, Runnable)`. The DF curated page comes from `common/config/src/main/resources/slate_config/df.json` via `CuratedPages.load()`. The "Gameplay" label only exists as `slate_menu.options.section.play` in Menu's `SlateOptionsScreen`, not as a Config page.

## 7. Dev run, harness and tools

- **Running the dev client:** `./gradlew.bat :dev:runClient` from `neoforge/` or `fabric/`. Run dirs are `neoforge/dev/run` (exists; `config/slate/*.json` is there) and `fabric/dev/run` (**never created**). `:dev:runServer` uses `run-server`.
- **Harness properties**, wired in both `dev/build.gradle` files (NeoForge as `systemProperty`, Fabric as `vmArg`) and **only applied when `-PautoScreens` or `-PautoWorld` is present**:
  - `-PautoScreens=a,b` → `slate.autoScreens`;
  - `-PautoWorld=true` → `slate.autoWorld`;
  - `-PautoSkin=DARK|VANILLA` → `slate.autoSkin`;
  - `-PautoFrames=N` → `slate.autoFrames` (default 45);
  - `-PautoQuit` → `slate.autoQuit` (default `true`);
  - `slate.autoDir` = `file('run')`.
  - All are read in `common/core/.../core/client/DevHarness.java` → `static void init()`, which `SlateClient.init()` calls.
  - `-PautoWorldScreens=a,b` → `slate.autoWorldScreens` (default `hud,minecraft:pause,minecraft:chat,slate:hub`).
  - Added since (30 September 2026): `-PautoLayout=VANILLA|CUSTOM|OVERHAUL`, `-PautoMotion=0`, `-PautoIntro=true`, `-PsampleData=true`, `-PautoWindow=1280x720`, `-PautoGif=true` (Chat records a GIF as soon as the player is in a world), `-PautoWorldId=<folder>` (that world is opened when it exists, made under that name when not), `-PautoLoadingShots=<ms>` (a picture every so often of the loading screens on the way into the world and out of it: `loading-NNN-<Screen>-<skin>.png`).
  - An entry may carry `@<ms>` (taken that long after the screen opened) and `#<x>x<y>` (the pointer there, as fractions of the screen). Special ids: `reload` (reloads the resources, taken from the overlay), in the world `click#<x>x<y>` (a left click on the open screen) and `command:<text>@<ms>` (a command sent as the player).
  - The world is created fresh: creative, peaceful, cheats on, seed `20260923L`, folder `slate-harness-NNNNN` (or `autoWorldId`).
  - Output is `<autoDir>/screenshots/<id>-<skin>.png` via `Screenshot.grab`, taken after a screen and everything Slate lays over it (popups, toasts, tooltips) have drawn (`DevHarness.screenFrame`, called at the end of `SlateClient.onScreenRendered`), or from `HUD_RENDER` when no screen is open. Batched draws are ended first, so a vanilla tooltip has its words.
  - Other hooks: env var `SLATE_CONFIG_SMOKE` → `config/SmokeTest.install()`. There is no quickPlay or dolly wiring in Slate.
- **`tools/`:**
  - `boot-df.ps1`: boots the DF pack with a CmlLib C# harness and runs a log analyzer, parking the drippy earlywindow jar during the run; exit 100 = still running at the timeout, i.e. boot survived. **Currently broken:** its default `-Harness` (`...\e94ada8d-...\scratchpad\boot\bin\Release\net10.0\boot.exe`) and `-Analyzer` (`analyze_log.py`) paths no longer exist.
  - `install-df.ps1`: deletes `slate-*-neoforge-1.21.1-*.jar*` from the DF mods folder, copies the newest jar of each module in a hard-coded list, and renames `chatterbox-*.jar` to `.disabled`.
  - `shot.ps1 -Out <png> [-TitleLike "Minecraft*"]`: `PrintWindow(hwnd, hdc, 2)` capture that does not steal focus.
  - `icons.py`: generates the icon atlas `assets/slate/textures/gui/icons.png`, `Icon.java` and `tools/icons-preview.png`. Append glyphs only; order matters.
  - `modicons.py`: the 64×64 per-module `icon.png`.
  - `emotes.py`: the chat emote sheet.
  - `multiplayer/{hubproto.py, hubtest.py, bot.py, README.md}`: hub TCP protocol tests.
- **Current DF pack state** (`%APPDATA%/CloudLauncher/default/packs/df/game/mods`, 228 files): **no Slate jars**, and `chatterbox-neoforge-1.21.1.jar` is **enabled** (the morning report said the opposite).
  - Relevant compat jars present: `create-1.21.1-6.0.10.jar` (copycat blocks: `copycat_panel`, `copycat_step`, `copycat_bars`, `copycat_base`), `betterinventory-neoforge-1.21.1.jar`, `jei-1.21.1-neoforge-19.44.0.403.jar`, `kleeslabs`, `terrain_slabs`, `Accurateblockplacement`.
  - **There is no Chisel-type mod** (nothing matches chisel, copycats+, framed, rechiseled, chipped or ctm).
  - BetterInventory source: `C:/Users/leonr/Coding/MinecraftMods/Source/QOL/BetterInventory` (jars up to `build/libs/betterinventory-1.1.4.jar`); Fabric port at `ports/fabric-1.21.1` (no built jar).

## 8. Vendored soft-dependency jars

`C:/Users/leonr/Coding/MinecraftMods/Source/libs/`, git-ignored, all NeoForge builds: `betterclouds.jar`, `bigwater.jar`, `creativecore.jar`, `iris.jar`, `littletiles.jar`, `mcef.jar`, `sable-companion.jar`, `sable-rapier.jar`, `sable.jar`, `simpleclouds.jar`, `sodium.jar` (the extracted inner jar), `veil.jar`, `voicechat.jar`, `voxy.jar`, `xaerolib.jar`, `xaeroworldmap.jar`, plus `README.md`.

- Modules pull them in with `compileOnly fileTree(dir: rootProject.slateLibsDir, include: [...])`. Config uses `sodium*`, `iris*`, `voicechat*`; multiplayer and chat use `voicechat*`, `mcef*`. Fabric uses the same NeoForge jars as `compileOnly`, restricted to loader-independent classes.
- ModMenu comes from Maven: `modCompileOnly "com.terraformersmc:modmenu:11.0.3"`, Fabric only.
- **Create, BetterInventory, JEI and any chisel mod are not in `libs/`.** Options: copy the DF pack jar into `libs/` and add a `fileTree` include, or use the Modrinth maven (`maven.modrinth:<slug>:<ver>`, already declared). JEI is on BlameJared maven, also declared.

## 9. Building

- **JAVA_HOME:** Bash has no `java`. Use `export JAVA_HOME="$(cygpath -w "$HOME/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2")"`; in PowerShell, `$env:JAVA_HOME="$env:USERPROFILE\.gradle\jdks\eclipse_adoptium-21-amd64-windows.2"`. The directory exists and `bin/javap.exe` works.
- **Commands** (from `neoforge/` or `fabric/`):
  - one module: `./gradlew.bat :building:build --no-daemon -q`;
  - compile only, faster: `:building:compileJava`;
  - add `:core:build` if core was touched; full build is `./gradlew.bat build --no-daemon -q`;
  - takes 1–4 minutes; never run two builds of the same loader directory at once;
  - asset SSL errors: just retry;
  - don't relaunch the dev client without the user's go-ahead (memory `no-auto-relaunch`).
- **Jar output:**
  - NeoForge: `neoforge/<m>/build/libs/slate-<m>-neoforge-1.21.1-1.0.0.jar`.
  - Fabric: remapped `fabric/<m>/build/libs/slate-<m>-fabric-1.21.1-1.0.0.jar`, plus the named dev jar `fabric/<m>/build/devlibs/slate-<m>-fabric-1.21.1-1.0.0-dev.jar` (what `:dev` consumes).
- **Mapped jars for `javap`:**
  - Fabric view: `C:/Users/leonr/Coding/MinecraftMods/Source/Ports/_template/fabric/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-48f5f74c97/.../minecraft-merged-...-v2.jar`. Fabric's access wideners are already applied, so it is **not** reliable for NeoForge visibility.
  - NeoForge view: `neoforge/core/build/moddev/artifacts/neoforge-21.1.247.jar`.
  - Fabric API jars: `~/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/<module>/<ver>/`. Class names in these raw jars are intermediary, remapped in dev.