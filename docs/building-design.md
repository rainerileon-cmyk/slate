# Slate Building — design contract (overnight build 2026-09-24)

Slate Building (`slate_building`) is a new Slate module: a polished, survival-friendly building mod on top of
Slate Core. Every agent codes against this file. If something here is impossible, implement the closest thing
that works and say so in your report. Read `DESIGN.md` (suite contract) and `docs/AGENT-BRIEF.md` first; the
parts of them that are wrong about Core are listed in "Core facts" below.

The user's request, condensed (all of it must end up working):
1. Block variants unified per material: stairs, vertical stairs, slabs, vertical slabs, fences, walls, and the
   DF pack's copycat shapes (Create 6: copycat **step** and copycat **panel**), plus new custom shapes.
   Variants in the world drop the ORIGINAL block (the material) and can be swapped seamlessly.
2. Optional placement ghost: semi-transparent preview of what you are about to place; opacity and saturation
   configurable; a separate option shows it for ANY block, not just variants.
3. Config "delete other variants": hide/remove vanilla + modded variant blocks (e.g. oak stairs) so everything
   goes through the unified system; world blocks and recipes that use them keep working.
4. Hold **Alt** with a block/variant: quick-swap radial wheel. Centre = the full block. LMB/RMB switch wheels,
   release with the cursor on a slice selects it, scroll wheel steps along the wheel. Configurable number of
   slices and exactly what is in each wheel, with good defaults. Super clean and animated.
5. **R** opens the big build menu: left = shape wheel (arrows switch wheels, click selects, search bar, no scroll
   select), below it a second wheel for chisel variants (texture variants of the block; chisel-mod compat), also
   with search and arrows. Right (wider) = scrollable table of building modes (fill, copy/paste, …), undo/redo
   buttons. Modes select an area with 2 right clicks and a good in-world overlay, or give abilities like
   mirroring. Same translucent ghost style as block placement.
6. Complex actions need a **toolbox**; more tools = more unlocks; tools have tiers; upgrades go in the toolbox.
   BetterInventory compat: a dedicated toolbox slot, and BI's Alt offhand function moves to another key.
7. Very configurable: wheels fully customisable, keybinds for every building mode.
8. Slate Config: top tabs inside pages; Customization = Mods / Resource Packs / Shader Packs; Multiplayer =
   Multiplayer + Chat tabs; Language + Accessibility merged, below Customization; DF pack tab removed; no
   "open video settings" button; a Gameplay category with a **Building** tab for this module's settings.

## 0. Identity and layout

| | |
|---|---|
| Gradle project | `:building` in `neoforge/` and `fabric/` |
| Shared source | `common/building/src/main/{java,resources}` |
| Mod id / namespace | `slate_building` |
| Package | `dev.fallingcloud.slate.building` |
| Display name | "Slate Building" |
| Side | both (registers blocks/items → must be on client AND server) |
| Depends on | `slate` (required), `slate_config` (optional, settings tab), `betterinventory`, `jei` (optional) |
| Configs | `config/slate/building.json` (client prefs), `config/slate/building-server.json` (server rules) |

Joining: unlike other Slate modules this one registers content, so client and server must both have it.
NeoForge `displayTest = "MATCH_VERSION"`; payloads still use the `slate` namespace (`Slate.id("building/…")`)
because Core's network requires it.

Loader-agnostic rules from DESIGN.md hold: no `net.neoforged.*`/`net.fabricmc.*` in `common/`, no split
packages, client classes never loaded on a dedicated server, soft deps only behind `isModLoaded` + a class that
is loaded after the check. Common code only uses signatures public in BOTH loader jars (Fabric's transitive
access wideners open things NeoForge keeps private — e.g. `MenuScreens.register` — always build both).

**Never put a `slate_building` data component into `Item.Properties`** (registration order of components vs
items differs between loaders); set our components on stacks at runtime only.

### Core facts (DESIGN.md is wrong about these)
No `Row/Column/Grid` (use `layout.ui.Flow`, `Anchor`, `Rect`); no `Anim.get(partial)`; `Colors.readableOn(bg)`;
`Theme.headingStyle()`; `Fonts.heading(...)`; icons are drawn with `Icons.draw(g, icon, x, y, size, color)`;
`SlateButton` has `HEIGHT/HEIGHT_SMALL/HEIGHT_LARGE`, no size enum; popups are global (`Popups`).
`SlateScreen.isPauseScreen()` returns true in a world → override to false for our in-world screens; its
`renderBackground` blurs + overlays → override for light overlays. `SlateEvents.KEY_PRESSED` fires only F1–F12
on Fabric and is not cancellable on NeoForge → never use it for Alt/R; use `KeyMapping` + our input router.
`HUD_RENDER` has only a float partial tick; tooltips/popups do not render on the HUD. No world-render,
in-world mouse, block event, recipe, loot or registry support exists in Core — this module adds what it needs.
Do NOT edit `common/core/.../gfx/SlateDraw.java` or anything under `common/menu/` except `MenuClient.java`
(the user has uncommitted work there). Add new Core helpers only as NEW files.

## 1. Economy (anti-dupe) — the rules everything obeys

- Every variant item/block is worth **exactly one material unit**. Double slab / double vertical slab = 2.
  Layer block with n layers = n.
- Swapping a held stack between shapes (Alt wheel, build-menu wheel, pick-block) keeps the **count** (1:1).
- Breaking a variant (native OR ours) drops the **material item × units**, only when the breaker could harvest
  the material (`player.hasCorrectToolForDrops(materialState)` / the tool in the loot params). The material
  item is the material block's own item (stone stairs → stone, not cobblestone; glass stairs → glass).
- Recipes that create native variants at better than 1:1 (3 planks → 6 slabs) would be dupes, so
  `rebalanceRecipes` (server, default ON) rewrites their result count to the number of material units consumed
  (shaped/shapeless: ingredient slots that are not rods/sticks; stonecutting: 1). `deleteNativeVariants`
  (default OFF) removes those recipes instead.
- Build operations pay per unit: any stack that identifies as the same material (full block OR any shape of
  it) counts as that many units, pulled from: toolbox pouch → inventory (hotbar, main, offhand; never the
  toolbox itself) → linked container (Supply Link upgrade). Refunds/drops go to: pouch/linked (Magnet
  upgrade) → inventory → dropped at the player's feet.
- In-world reshape (hammer) charges/refunds the unit difference (double slab → stairs refunds 1).
- Chisel swaps are 1:1 and only inside groups whose links are all 1:1 (never copper block ↔ cut copper).
- Undo refunds what it removes and re-charges what it restores; positions that changed since are skipped.
- Creative: no cost, no drops, limits from `creativeMaxVolume`.

## 2. Variant model (`building.variant`, owner A)

```java
public enum Shape {           // order = default wheel order; id = lower-case name
  FULL, STAIRS, SLAB, VERTICAL_SLAB, VERTICAL_STAIRS, WALL, FENCE, STEP, PANEL,
  FENCE_GATE, VERTICAL_STEP, POST, LAYER, PANE;
  String id(); Component displayName();         // slate_building.shape.<id>
  Icon icon();                                   // via BuildingIcons
  boolean custom();                              // has a slate_building block (all except FULL)
  @Nullable static Shape byId(String);
}
public record Variant(Block material, Shape shape) {}
```

Geometry of our custom shapes (all waterloggable where vanilla's equivalent is; "units" in brackets):
| Shape | Block class (ours) | States | Geometry |
|---|---|---|---|
| STAIRS | `ShapeStairBlock extends StairBlock` (base state: stone) | vanilla | vanilla stairs incl. inner/outer |
| SLAB | `ShapeSlabBlock extends SlabBlock` | vanilla (double = 2) | vanilla; merges only with the SAME material |
| VERTICAL_SLAB | `VerticalSlabBlock` | `facing` (4 horiz), `type` single/double, waterlogged | half block against `facing` side; double = full (2) |
| VERTICAL_STAIRS | `VerticalStairsBlock` | `facing` (4), waterlogged | L-shaped full-height: full block minus the quarter column in the `facing`-clockwise corner |
| WALL | `ShapeWallBlock extends WallBlock` + extra hidden property | vanilla | vanilla wall |
| FENCE | `ShapeFenceBlock extends FenceBlock` + extra hidden property | vanilla | vanilla fence (connects to #fences) |
| FENCE_GATE | `ShapeFenceGateBlock extends FenceGateBlock(WoodType.OAK)` | vanilla | vanilla gate |
| STEP | `StepBlock` (Create copycat step) | `facing` (4), `half`, waterlogged | 16w × 8h × 8d quarter block: `(0,0,8)-(16,8,16)` for SOUTH bottom |
| PANEL | `PanelBlock` (Create copycat panel) | `facing` (6), waterlogged | 3 px plate against `facing` (`(0,0,0)-(16,3,16)` for UP) |
| VERTICAL_STEP | `VerticalStepBlock` | `facing` (4 corners), waterlogged | 8 × 16 × 8 quarter column |
| POST | `PostBlock` | `axis`, waterlogged | 8 × 8 centred beam along axis |
| LAYER | `LayerBlock` | `layers` 1..8, `facing` (6, the face it grows from), waterlogged | 2 px per layer; n layers = n units |
| PANE | `ShapePaneBlock extends IronBarsBlock` + extra hidden property | vanilla | 2 px pane with connections |

The extra hidden property (`BooleanProperty SHAPED = create("shaped")`, always true) keeps DiagonalFences/
DiagonalWalls/DiagonalWindows (DF pack) from registering twins of our blocks (they target blocks with the
vanilla property count). Verify against the DF jars' `DiagonalBlockTypeImpl.isTarget` if you can.

Placement mirrors vanilla/Create: stairs like vanilla; slab/vertical slab/layer merge when clicking the same
block with the same material; step: `facing = ctx.getHorizontalDirection()`, `half` from click height; panel:
`facing = ctx.getNearestLookingDirection().getOpposite()` (placed against the clicked face); vertical step:
corner from the click position within the block; post: axis of the clicked face; layer: grows from the
clicked face.

`ShapeBlockEntity` (one BE type for all shape blocks): `BlockState material` (null/air = unset), synced with
`getUpdateTag`/`getUpdatePacket`; on client update → `level.sendBlockUpdated(pos, s, s, 8)` so the section
re-meshes. `ShapeBlock` interface on every shape block:
```java
public interface ShapeBlock {
  Shape shape();
  /** Boxes in block space 0..16 used for rendering (quad cropping) and for the ghost. */
  List<AABB> renderBoxes(BlockState state);
  /** Units this state is worth (double slab 2, layers n). */
  int units(BlockState state);
  static @Nullable BlockState material(BlockGetter level, BlockPos pos);
}
```
Material delegation on our blocks: destroy progress and harvestability from the material
(`getDestroyProgress` → material's), explosion resistance, sound (NeoForge extension methods declared in
common WITHOUT `@Override` — `getSoundType(BlockState, LevelReader, BlockPos, Entity)`,
`getLightEmission(BlockState, BlockGetter, BlockPos)`, `getFriction(...)`,
`getExplosionResistance(BlockState, BlockGetter, BlockPos, Explosion)`, `getCloneItemStack(BlockState,
HitResult, LevelReader, BlockPos, Player)` — they override the NeoForge interface defaults at runtime and are
plain methods on Fabric), drops (override `getDrops(BlockState, LootParams.Builder)`), pick block (vanilla
`getCloneItemStack(LevelReader, BlockPos, BlockState)` returns the variant stack).

Items: one `ShapeBlockItem extends BlockItem` per shape block; the material lives in data component
`slate_building:material` (`Holder<Block>`-free: store the block `ResourceLocation`; codec + stream codec).
`getName(stack)` = `"<material name> <shape name>"` (`slate_building.variant_name`). `setPlacedBy` copies the
component into the BE. A shape item without a material places nothing (returns FAIL) and shows "No material".

### VariantRegistry (both sides, deterministic from registries — no sync needed)
```java
public final class VariantRegistry {
  static VariantRegistry get();                          // built lazily after registries freeze; rebuilt on tags update
  Optional<Variant> identify(ItemStack stack);           // planks→(planks,FULL); oak_stairs→(planks,STAIRS); our item→component
  Optional<Variant> identify(BlockState state, @Nullable BlockEntity be);
  Optional<Variant> identify(BlockGetter level, BlockPos pos);
  boolean isMaterial(Block block);                       // eligible full block (see below)
  List<Shape> shapesFor(Block material);                 // shapes available (native or custom), wheel filtering
  boolean isAvailable(Block material, Shape shape);
  @Nullable Block nativeBlock(Block material, Shape shape); // the realising native block, if any
  ItemStack stackFor(Block material, Shape shape, int count); // native item if it exists, else our shape item + component
  int units(BlockState state, @Nullable BlockEntity be); // 1, 2 for doubles, n for layers
  Set<Item> nativeVariantItems();                        // everything "delete natives" hides
  /** Placement state for placing variant `v` from `ctx` (delegates to the native ITEM's BlockItem so DiagonalBlocks' re-pointed items keep working). */
  @Nullable BlockState placementState(Variant v, BlockPlaceContext ctx);
}
```
Discovery of natives (deterministic, both sides): (1) vanilla `BlockFamilies.getAllFamilies()` base →
STAIRS/SLAB/WALL/FENCE/FENCE_GATE; (2) every `StairBlock` → its `baseState` block (accessor mixin);
(3) `SlabBlock`/`WallBlock`/`FenceBlock`/`FenceGateBlock` via the sibling-stairs prefix (`oak_slab` →
`oak_stairs` → oak_planks) then name candidates (`X`, `Xs`, `X_planks`, `X_block`, `X_bricks`→…);
(4) server-config overrides/ignores; (5) identification of re-pointed blocks (DiagonalFences twins) through
`block.asItem()`. Ties: BlockFamilies > same namespace as material > `minecraft` > registry order.
Materials: full-cube collision + render shape MODEL + has a BlockItem + not an `EntityBlock` + not one of our
shapes + not in `#slate_building:not_material` (ship: bedrock, barrier, command/structure/jigsaw blocks,
spawner, end portal frame, reinforced deepslate, light, budding amethyst) OR in `#slate_building:material`.

### Unify hooks (owner A, common mixins)
- `BlockBehaviour.BlockStateBase.getDrops(LootParams.Builder)` RETURN: native variant drops → material × count.
- `RecipeManager.apply(Map<ResourceLocation, JsonElement>, …)` HEAD: rebalance or remove JSON recipes whose
  result is a native variant item (see §1); log counts.
- `CreativeModeTab.buildContents` TAIL: when `deleteNativeVariants`, strip native variant items from
  display + search lists.
- JEI (optional, compileOnly JEI API from BlameJared maven): hide native variant items when deleted.

## 3. Registry, platform and loader seams (skeleton)

```java
// building.registry
public final class BuildingRegistry {       // queue, flushed by each loader entry
  static <T> RegistryRef<T> register(ResourceKey<? extends Registry<T>> registry, String path, Supplier<? extends T> factory);
  static List<Entry<?>> entries();           // insertion order
}
public final class RegistryRef<T> implements Supplier<T> { T get(); ResourceLocation id(); }
BuildingBlocks / BuildingItems / BuildingBlockEntities / BuildingComponents / BuildingMenus / BuildingTabs / BuildingTags
```
NeoForge flush: `RegisterEvent` per registry key. Fabric flush: `Registry.register` in the order BLOCK, ITEM,
BLOCK_ENTITY_TYPE, DATA_COMPONENT_TYPE, MENU, CREATIVE_MODE_TAB, others.

```java
// building.platform (ServiceLoader, like core SlatePlatform; impls in each loader tree)
public interface BuildingPlatform {
  static BuildingPlatform get();
  boolean canBreak(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state);   // NF BreakEvent / Fabric PlayerBlockBreakEvents.BEFORE
  /** Sets the block (flags), fires the loader place event (NF EventHooks.onBlockPlace with a pre-change snapshot) and reverts + returns false if cancelled. */
  boolean tryPlace(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state, Direction face, int flags);
  void openMenu(ServerPlayer p, MenuProvider provider);
  boolean isFakePlayer(Player p);
}
```
Also both loaders: vanilla checks `level.mayInteract(p, pos)`, `p.mayBuild()`, world border,
`level.isLoaded(pos)` happen in common code before calling the platform.

Loader glue per owner (skeleton creates empty classes with a static `init(...)`, entry classes call them):
`neoforge/building/.../building/neoforge/{render/NeoRenderGlue, ops/NeoOpsGlue, toolbox/NeoToolboxGlue,
variant/NeoVariantGlue, client/NeoClientGlue}` and the same under `fabric/…/fabric/`. Owners only edit their
own glue class.

Mixin configs (skeleton declares all of them in both manifests; owners fill their own):
`slate_building.mixins.json` (skeleton: input router, accessors), `slate_building.variant.mixins.json` (A),
`slate_building.render.mixins.json` (B), `slate_building.ops.mixins.json` (D1), `slate_building.ui.mixins.json`
(C), `slate_building.toolbox.mixins.json` (E), `slate_building.chisel.mixins.json` (I). Packages:
`dev.fallingcloud.slate.building.mixin.<owner>` — never put helper classes in a mixin package.

## 4. Client input (skeleton `building.client.input`)

One router for in-world input (no screen open), fed by common client mixins on `MouseHandler.onPress`,
`onScroll`, `turnPlayer` (reads + zeroes the accumulated deltas when consumed), `Minecraft.startUseItem`,
`startAttack`, `continueAttack`, `pickBlock`, `KeyboardHandler.keyPress` (screen == null only):
```java
public final class BuildInput {
  public interface Handler {
    default int priority() { return 0; }                    // higher runs first
    default boolean onMouseButton(int button, int action, int mods) { return false; }
    default boolean onScroll(double dx, double dy) { return false; }
    default boolean onMouseLook(double dx, double dy) { return false; }  // true = camera does not turn
    default boolean onUse() { return false; }               // cancel vanilla right-click use
    default boolean onAttack() { return false; }            // cancel vanilla attack/mining start
    default boolean suppressContinueAttack() { return false; }
    default boolean onPickBlock() { return false; }
    default boolean onKey(int key, int scancode, int action, int mods) { return false; }
  }
  public static void register(Handler h);
}
```
Priorities: wheel overlay 100, mode controller 50, pick-block swap 10.

**Exclusive keys** (`ExclusiveKeys`, mixin on static `KeyMapping.set(Key, boolean)` and `KeyMapping.click(Key)`
HEAD): a key mapping registered with `ExclusiveKeys.claim(KeyMapping, BooleanSupplier wantsNow)` gets the key to
itself when `wantsNow` is true at press time — other mappings bound to the same key are not set down and get
no click. Releases always propagate to everyone. The DF pack has SIX mods on Left Alt (Create toolbelt radial,
Relics, Iron's Spellbooks, Shoulder Surfing free look, BetterInventory offhand, Create alt modifier) and R
(Iris shader reload, Iron's spell wheel): swap key claims Alt only while holding a variant-able item (or
looking at a variant block with a hammer); menu key claims R while holding a block/variant/toolbox/building
tool or while a mode is active (config `menuKeyContext`: `SMART` default, `ALWAYS`).

Keybinds (`SlateKeys.register`, category `key.categories.slate_building`):
`swap` = Left Alt, `build_menu` = R, `undo`, `redo`, `confirm` (apply the pending selection; default
unbound — right click also confirms), `cancel` (unbound), `exit_mode` (unbound), and **one per building mode**
`mode.<id>` (unbound). All show up in Slate Config's Controls page automatically.

## 5. Swap wheel and build menu (owner C, `building.client.wheel`, `building.client.menu`)

**Alt wheel** (overlay drawn in the HUD pass, not a Screen, so the player can keep walking):
- Opens while the swap key is held and the main-hand stack identifies as a variant (or: empty hand / building
  tool while looking at a variant block and the player may reshape → in-world reshape wheel).
- Virtual cursor from mouse deltas (camera frozen via `onMouseLook`), recentred on open. Centre disc = FULL
  (rendered as the 3D item); slices around it = the wheel's entries rendered as 3D items (the real variant
  stacks), current shape highlighted, unavailable ones hidden (`hideUnavailable`) or greyed.
- LMB / RMB = previous / next wheel page (page dots + wheel name). Scroll = step selection along the ring.
  Number keys 1–9 pick directly. Release = apply the hovered/selected slice (`SwapHeld` payload), or nothing if
  the cursor is in the dead zone around the current item.
- Animation (honour `Theme.motion()`): open = scale 0.85→1 + fade (OUT_BACK, ~180 ms), slices stagger in
  (~15 ms each), hover = slice pushes out + brightens (120 ms), selection ring slides (angle anim), close = fast
  fade. Both skins: dark skin = layered surfaces + accent; vanilla skin = stone/dark panel look.
- Label under the wheel: variant name + count, "Needs: Hammer" style hint when locked.
- Default wheels: 1 "Shapes": STAIRS, SLAB, VERTICAL_SLAB, VERTICAL_STAIRS, WALL, FENCE, STEP, PANEL;
  2 "More": FENCE_GATE, VERTICAL_STEP, POST, LAYER, PANE; 3 "Chisel" (dynamic, only if the material has a
  chisel group and chisel is unlocked). `maxSlices` (4–12, default 8) — overflow spills to extra pages.

**Build menu** (R) — `BuildMenuScreen extends SlateScreen`, `isPauseScreen() == false`, light in-world
background, opens/closes with R, Esc:
- Left column (~40%): "Shapes" wheel (same component, larger, click to select, arrows ◀ ▶ switch wheel pages,
  search field filters slices by name — matching slices glow, others dim — scroll does nothing), below it the
  "Chisel" wheel (group members of the held material; search + arrows; locked state explains the Chisel tool).
- Right column (~60%): header "Building modes" + Undo / Redo buttons with counts + toolbox summary chip
  (tools owned, tier pips). A scrollable table (`SlateList`-style rows, no search): icon, name, one-line
  description, required tool + tier chip, keybind chip, lock icon + reason when locked, active highlight.
  Clicking a row activates the mode (closes the menu unless Shift is held); a detail/options panel under the
  table edits the selected mode's parameters (toggles, segmented choices, sliders) and shows its hints.
- Both skins, entrance stagger, keyboard: arrows move in the table, Enter activates, Tab cycles areas,
  `/` focuses search.

**HUD** while a mode is active: a compact chip (top centre, config anchor) with the mode icon + name +
live stats ("12 × 4 × 8 · 384 blocks · 384/512 Oak Planks") + key hints (RMB corner / RMB again apply /
LMB cancel / Ctrl+scroll resize); a progress bar while the server executes; action-bar style result line
("Filled 384 — press U to undo"). Drawn in `HUD_RENDER`.

**Wheel editor** (`WheelEditorScreen`): list of wheels (add/remove/rename/reorder), each wheel's slice list
(add from all shapes, remove, reorder by drag or arrows), `maxSlices`, reset to defaults; opened from the
Building settings tab and from the build menu's gear button.

**Pick block**: middle-click on a variant in survival → select the hotbar slot holding the same material (any
shape) and, if its shape differs, request a swap to the picked shape (config `pickBlockSwaps`).

## 6. Rendering (owner B, `building.client.render`, `building.client.model`)

- `ShapeQuadBaker` (common client): given a shape state + material state (+ random, + side) returns cropped
  quads: for each render box, for each face, take the material model's quads for that direction (culled
  faces from `getQuads(material, dir, rand)`; internal faces use the same direction's quads, cull = null),
  crop positions to the box and remap UVs inside the same sprite (Create's `cropAndMove` idea), keep tint
  index and shade. Cache per (shape state, material state) with a size-bounded LRU; clear on resource reload.
  Faces on the block boundary keep `side` (culled against neighbours), internal faces go in `side == null`.
- NeoForge: a `BakedModelWrapper` per shape-block state installed in `ModelEvent.ModifyBakingResult`;
  `getModelData(level, pos, state, data)` reads the BE material into `ModelData`; `getQuads(..., ModelData,
  RenderType)` returns cropped quads for the material's render type; `getRenderTypes` = the material's chunk
  render type set. Fabric: `ForwardingBakedModel` with `isVanillaAdapter() == false`, `emitBlockQuads` reads
  the BE from the view and emits cropped quads with the material's blend mode, installed via
  `ModelLoadingPlugin.modifyModelAfterBake`.
- Items: our shape items use an `ItemOverrides` that resolves a cached per-material baked model built by the
  same baker (vanilla path, both loaders).
- Colours: block + item colour handlers for all shape blocks/items delegate to the material's colour at that
  position (grass, leaves, water-tinted). Registered in loader glue (NF `RegisterColorHandlersEvent`, Fabric
  `ColorProviderRegistry`).
- Blockstate JSONs for our blocks point every state at one simple model (`slate_building:block/shape_unset`,
  a cube with the "no material" texture) — the wrapper replaces it; the particle sprite comes from the material.
- **Ghosts** — `GhostRenderer.submit(Ghost)` per frame and `submitCached(key, version, supplier)` for large
  plans (vertex buffer cached until `version` changes). Styles PLACE (normal), REPLACE (amber tint), REMOVE
  (red, cross-hatched/outlined), INVALID (red outline only). Opacity and saturation from config; a ghost core
  shader (`slate_building:ghost`: `mix(luma, rgb, saturation)`, `alpha *= opacity`) registered via NF
  `RegisterShadersEvent` / Fabric `CoreShaderRegistrationCallback`; when a shader pack is active (Iris API via
  reflection) fall back to the vanilla translucent shader with alpha only. Optional subtle pulse + outline.
  Above `previewMaxBlocks` only outlines are drawn.
- **Hand preview**: when enabled, holding a variant (or any block item if `previewAllBlocks`) and looking at a
  block → compute `BlockPlaceContext` from `Minecraft.hitResult`, `updatePlacementContext`, the placement state
  (invoker mixin on `BlockItem.getPlacementState`, or `VariantRegistry.placementState` for variants), skip if
  it cannot be placed; submit as PLACE ghost (INVALID if obstructed/can't survive). Also mirrored copies when
  symmetry is active. Skip while BridgingMod's bridging assist is showing its own preview if detectable.
- `OverlayRenderer`: `box(AABB, int argb, boolean animated)` (glowing animated edges + faint faces),
  `marker(BlockPos, int argb)`, `line(Vec3, Vec3, int argb)`, `plane(Direction.Axis, double coord, AABB bounds,
  int argb)` (mirror planes with a grid), `label(Vec3, Component, int argb)` (billboard, readable, through
  walls optional). Accent colour from `Theme.current().palette().accent()`.
- World render hook: Core gets a new file `core/client/render/SlateRenderEvents` with
  `AFTER_TRANSLUCENT` (`Event<WorldRender>`, `record WorldRenderContext(PoseStack poseStack, Matrix4f
  modelView, Matrix4f projection, Camera camera, float partialTick)`) fired by core's loader client classes
  (NF `RenderLevelStageEvent` AFTER_TRANSLUCENT_BLOCKS; Fabric `WorldRenderEvents.AFTER_TRANSLUCENT` — its
  `consumers()` is null there, so draw through `Minecraft.renderBuffers().bufferSource()` + `endBatch`). During
  this event the RenderSystem model-view already holds the camera rotation on both loaders: translate by
  `-camera.getPosition()` and draw. Verify in game on NeoForge.

## 7. Building modes (owners D1 server + D2 client, `building.ops`)

```java
public enum ToolType { TROWEL, HAMMER, BRUSH, BLUEPRINT, SQUARE, CHISEL }   // + Icon, lang
public enum ModeKind { AREA, POINT, MOVE, TOGGLE, MEASURE }
public record ModeParam(String id, Type type, Object def, int min, int max, List<String> options) { enum Type { BOOL, INT, CHOICE } }
public record BuildMode(String id, ModeKind kind, @Nullable ToolType tool, int minTier, List<ModeParam> params,
                        boolean places, boolean breaks) { Component name(); Component description(); Icon icon(); }
public final class BuildModes { static List<BuildMode> all(); static @Nullable BuildMode byId(String); /* constants below */ }
```
| id | kind | tool ≥ tier | what it does | params |
|---|---|---|---|---|
| `fill` | AREA | Trowel 1 | fill the box with the held block/variant (or palette) | replace (AIR / REPLACEABLE / ALL), palette (HELD / HOTBAR_RANDOM / HOTBAR_CHECKER) |
| `walls` | AREA | Trowel 1 | the 4 vertical sides of the box | thickness 1–4, replace, palette |
| `line` | AREA | Trowel 1 | 3D line A→B | thickness 1–3, palette |
| `extend` | POINT | Trowel 1 | Construction-wand: add one layer on every connected same block of the clicked face (max by tier 16/64/256/1024) | match (EXACT / BLOCK / ANY), lock (NONE / HORIZONTAL / VERTICAL) |
| `hollow` | AREA | Trowel 2 | box shell | thickness 1–4, replace, palette |
| `outline` | AREA | Trowel 2 | the 12 edges | replace, palette |
| `cylinder` | AREA | Trowel 2 | A = base centre, B = radius + height | hollow, palette, replace |
| `sphere` | AREA | Trowel 3 | A = centre, B = radius point | hollow, part (FULL / DOME / BOWL), palette, replace |
| `replace` | AREA | Brush 1 | replace matching blocks with the held one; keeps shape + orientation when both are variants | filter (CLICKED / ANY_SOLID / OFFHAND), keepShape |
| `overlay` | AREA | Brush 2 | cover the top surface of the area | depth 1–3, mode (ON_TOP / REPLACE_TOP) |
| `clear` | AREA | Hammer 1 | break everything (drops to inventory) | filter (ALL / CLICKED / PLANTS), keepFluids |
| `reshape` | AREA | Hammer 2 | change the shape of every variant/material block in the box to the held shape, keeping material | filter (ALL / CLICKED_MATERIAL) |
| `copy` | AREA | Blueprint 1 | copy the box to the clipboard (no block-entity contents in survival) | includeAir |
| `paste` | POINT | Blueprint 1 | paste the clipboard at the clicked block (ghost first) | rotation 0/90/180/270, mirror NONE/X/Z, includeAir, replace |
| `cut` | AREA | Blueprint 2 | copy + clear | — |
| `stack` | AREA | Blueprint 3 | repeat the box N times along a direction | count 1–16, spacing 0–8, direction (LOOK / UP / DOWN / N / S / E / W) |
| `move` | MOVE | Blueprint 3 | select, then click a destination | rotation, mirror |
| `mirror` | TOGGLE | Square 1 | your normal placing and breaking is mirrored across a plane through the centre | axis X / Z / XZ, radius by tier 16/32/64/128, mirrorBreaking |
| `radial` | TOGGLE | Square 3 | N-fold rotational symmetry around the centre | slices 2–8, mirrorBreaking |
| `measure` | MEASURE | — | size / volume / distance, no world change | — |

Client flow (D2, `building.client.mode`): activating a mode (menu row or its keybind) makes it current;
AREA: 1st right-click = corner A (reach = `blockInteractionRange()` + tier reach bonus; on air, a corner at a
scroll-adjustable distance), the box then follows the crosshair, 2nd right-click = corner B, the planned result
shows as ghosts, 3rd right-click (or `confirm` key) applies; left-click cancels the pending selection;
Ctrl+scroll pushes/pulls the looked-at face of the box, Shift+scroll changes the main numeric param (thickness /
count / radius); arrow keys nudge. POINT: one click shows the ghost, the next applies. TOGGLE: the centre is the
targeted block when activated (re-centre with `confirm`), plane/centre drawn in the world. Normal placing is
suppressed while an AREA/POINT/MOVE mode has a pending selection; mining works normally unless a selection is
pending (then left-click cancels). The planner below runs client-side for the preview and server-side for the
real thing, so the preview matches.

```java
// common, deterministic — D1
public record PlanContext(Level level, Player player, BuildMode mode, ModeParams params, List<BlockPos> anchors,
                          Direction face, Palette palette, @Nullable Clipboard clipboard, Limits limits) {}
public record Change(BlockPos pos, BlockState target, @Nullable Variant targetVariant, Kind kind) { enum Kind { PLACE, REPLACE, BREAK } }
public record Plan(List<Change> changes, AABB bounds, @Nullable Component error, int requestedCount) {}
public interface ModePlanner { Plan plan(PlanContext ctx); }
public final class Planners { static ModePlanner of(BuildMode m); }
public final class ModeParams { /* id → Object; CompoundTag codec; typed getters with defaults from BuildMode */ }
public record Palette(List<WeightedEntry> entries) { /* resolved from held stack / hotbar; material+shape aware */ }
public record Limits(int maxVolume, int maxSpan, int reachBonus, int blocksPerTick, int undoDepth) {}
```
Server (D1): validate (mode unlocked by toolbox, anchors within reach + bonus, volume/span within limits,
not spectator/adventure, rate limit), plan, charge (place-what-you-can by default), execute over ticks (per-op
blocks/tick by tier, global budget per tick), every position checked (`level.isLoaded`, `mayInteract`, world
border, claims via `BuildingPlatform`), placements through `BuildingPlatform.tryPlace` (BE material set for
our shapes), breaks via `canBreak` + drops through `Block.getDrops` with a tool stack matching the hammer tier
(so variant drops → material), block entities skipped in survival (config), unbreakable (`destroySpeed < 0`)
skipped. Progress S2C every ~5 ticks, result S2C at the end, stop + settle on logout. History per player
(`undoDepth` + Memory upgrades, capped by `maxUndoBlocks`), in memory. Clipboard per player (server copy +
S2C `ClipboardSync` for the client preview; capped by tier volume). Symmetry: mixins on `BlockItem.place`
RETURN and `ServerPlayerGameMode.destroyBlock` RETURN replay the placement/break at the mirrored positions
(with `state.mirror/rotate`) under the same economy and protection. Commands (loader glue):
`/slatebuild undo|redo|cancel|limits|reload`.

## 8. Toolbox (owner E, `building.toolbox`)

- Items: `slate_building:toolbox` ("Builder's Toolbox" — Create already has a "Toolbox"), tools
  `slate_building:<tier>_<tool>` for tiers `copper`, `iron`, `diamond`, `netherite` (tier 1–4) × tools
  `trowel, hammer, brush, blueprint, square, chisel`; upgrades `slate_building:<x>_upgrade` for `reach,
  capacity, speed, memory, supply_link, magnet, efficiency`.
- Toolbox inventory (vanilla `DataComponents.CONTAINER`): 6 tool slots (one per `ToolType`, slot accepts only
  that type), 4 upgrade slots, 9 pouch slots (building materials, used first by operations). Right-click in
  air opens it (`ToolboxMenu` + Slate-styled `ToolboxScreen`, both skins); sneak + right-click a container with
  a Supply Link upgrade links it (stored on the toolbox, shown in the tooltip).
- Tools have durability (copper 250, iron 750, diamond 2000, netherite 5000), repair in an anvil with the tier
  ingot/diamond/netherite ingot; netherite via smithing table (upgrade template + netherite ingot).
  Operations use 1 durability per `durabilityPerBlocks` blocks (Efficiency halves).
- `ToolboxAccess` (both sides; client computes from its synced inventory):
```java
public final class ToolboxAccess {
  static ItemStack find(Player p);                // BI toolbox slot → offhand → hotbar → main inventory
  static Capabilities of(Player p);               // creative + creativeBypass → everything at tier 4
  public record Capabilities(Map<ToolType, Integer> tiers, Map<UpgradeType, Integer> upgrades, boolean creative) {
    boolean unlocked(BuildMode m); @Nullable Component lockReason(BuildMode m); int tier(ToolType t);
    Limits limits(BuildingServerSettings s);
  }
  static void damageTool(ServerPlayer p, ToolType t, int blocks);
  static List<SlotRef> pouch(ServerPlayer p);    // material source for ops
  static @Nullable LinkedContainer supplyLink(ServerPlayer p);
}
```
- BetterInventory bridge (reflective, `compat/BetterInventoryBridge`): `dev.fallingcloud.betterinventory.api.
  BetterInventoryApi.toolbox(Player)`; tag `#slate_building:toolboxes` (the slot accepts it).
- Recipes (1.21.1 folders are singular: `data/slate_building/recipe/`), loot none, tags
  (`#slate_building:toolboxes`, `#slate_building:tools`, `#slate_building:upgrades`), creative tab
  "Slate Building" (toolbox, tools, upgrades, one showcase stack per shape in oak planks + stone bricks).
  Item textures generated by a Java tool (`tools/building/Textures.java`, run with `java Textures.java`), pixel
  art in the Slate style, per-tier palettes.

## 9. Chisel groups (owner I, `building.chisel`)

Server-side index rebuilt on `SERVER_STARTED` and after datapack reload (mixin `PlayerList.reloadResources`
TAIL), synced to clients (`ChiselGroupsSync`) on join and after reload. Providers, each producing its own
wheel page (never merged): overrides file (`config/slate/building-chisel.json`, shipped defaults) → Rechiseled
(`data/*/chiseling_recipes/*.json`, both entry grammars) → Chipped (`chipped:*` item tags) → Chisel Modern
(`chisel:carving/*` tags) → Chisel Reborn (reflective `ChiselGroupLookup`) → stonecutter 1:1 full-block graph
(undirected components; expand tag ingredients; drop shapes) → vanilla `BlockFamilies`
CHISELED/CRACKED/CUT/MOSAIC/POLISHED. Rules: drop any link with a count ≠ 1 recipe between members; never
cross copper oxidation/wax states; skip block entities, doors/beds, gravity blocks. API:
```java
public final class ChiselGroups { static ChiselGroups client(); static ChiselGroups server(MinecraftServer);
  List<Group> groupsOf(Block material); record Group(String source, Component name, List<Block> members) {} }
```
Held chisel swap keeps the shape (stone_slab → stone_brick_slab when that variant exists, else custom shape).
In-world chisel (Chisel tier 2): only when the block's no-tool drops equal its own item (else it would be free
silk touch), copies shared state properties.

## 10. Configuration

`BuildingConfig` (client, `config/slate/building.json`) — nested sections, one file per section, owned as noted:
`wheel` (C): swapEnabled, wheels (list of `{name, entries[]}`), maxSlices 8, hideUnavailable, scrollSelects,
clickSwitchesWheel, releaseToSelect, numberKeys, wheelScale 1.0, labels, includeChiselPage, pickBlockSwaps,
exclusiveSwapKey, menuKeyContext; `preview` (B): enabled true, allBlocks false, opacity 0.45, saturation 0.6,
outline true, pulse true, maxBlocks 4096, showMirrored true; `hud` (C): enabled, anchor TOP, scale 1.0,
actionBar true, sounds true; `modes` (D2): lastMode, params per mode (JSON object), airDistance 4,
confirmWithRightClick true.

`BuildingServerConfig` (server, `config/slate/building-server.json`, synced subset via `ServerSettingsSync`):
`variants` (A): unify true, rebalanceRecipes true, deleteNativeVariants false, customShapes true,
materialDenylist [], materialAllowlist [], variantOverrides {}, ignoredVariants [], swapNeedsTool false;
`ops` (D1): enabled true, requireToolbox true, creativeBypass true, maxVolume [256,2048,16384,65536],
maxSpan [16,32,64,128], reachBonus [0,8,16,32], blocksPerTick [8,24,64,160], globalBlocksPerTick 2048,
creativeMaxVolume 262144, undoDepth 10, undoPerMemory 10, maxUndoBlocks 200000, durabilityPerBlocks 4,
placeWhatYouCan true, respectClaims true, allowBlockEntities false, disabledModes [], pasteOpLevel 0;
`toolbox` (E): durability per tier, allowPouch true, supplyLinkRange 64; `chisel` (I): enabled true,
stonecutterGroups true, blockFamilies true, modCompat true, inWorld true.

Slate Config integration (C): `SettingsTabs.register("gameplay", new Tab("building", …))` (API in
`common/config/.../config/api/SettingsTabs.java`) from a class loaded only when `slate_config` is present; sections
General / Placement preview / Swap wheel / Build menu & HUD / Server rules (editable in singleplayer, read-only
on a server) with proper `Binding`s (labels + tooltips from lang, ranges, defaults) and a "Edit wheels…" custom
row; `SlateConfigApi.registerReloadHook("building", …)` so changes apply live.

## 11. Networking (`building.net`, skeleton; ids `slate:building/<name>`)

C2S: `SwapHeld(int slot, String shape)` (A), `ReshapeTarget(BlockPos, String shape)` (A),
`ChiselHeld(int slot, ResourceLocation material)` (I), `ChiselTarget(BlockPos, ResourceLocation material)` (I),
`ApplyOp(String mode, CompoundTag params, List<BlockPos> anchors, Direction face, int slot)` (D1),
`CancelOp()`, `Undo()`, `Redo()`, `SetSymmetry(String mode, CompoundTag params, BlockPos centre)` (D1),
`OpenToolbox(int slot)` (E).
S2C: `ServerSettingsSync(CompoundTag)` (skeleton), `ChiselGroupsSync(CompoundTag)` (I),
`HistoryState(int undo, int redo, String label)`, `OpProgress(int op, int done, int total, String mode)`,
`OpResult(int op, String mode, int placed, int broken, int skipped, String messageKey, List<String> args)`,
`ClipboardSync(CompoundTag)`, `SymmetryState(String mode, CompoundTag params, BlockPos centre)` (D1).
Handlers dispatch to owner classes (`variant.VariantActions`, `chisel.ChiselActions`, `ops.OpActions`,
`toolbox.ToolboxActions`, `client.ClientActions`) — owners implement bodies, never the registration file.

## 12. Ownership map

| Agent | Owns |
|---|---|
| S skeleton | gradle wiring, manifests, `SlateBuilding`, `registry/*`, `platform/*` + loader impls, `net/*`, `config/*` shells, enums in §2/§7/§8 as declared, `client/input/*` + base mixins, `BuildingHarness`, `BuildingIcons`, core `SlateRenderEvents` + firing, empty owner glue/init classes |
| A variants | `variant/*`, `block/*`, `item/ShapeBlockItem`, `mixin/variant/*`, variant data (tags), `config/ServerVariants` |
| B render | `client/render/*`, `client/model/*`, `mixin/render/*`, block/item models + blockstates, loader render glue, `config/PreviewSettings` |
| C ui | `client/wheel/*`, `client/menu/*`, `client/hud/*`, `client/settings/*`, `mixin/ui/*`, pick-block, `config/WheelSettings`, `config/HudSettings` |
| D1 ops-server | `ops/*` (planners, executor, economy, history, clipboard, symmetry), `mixin/ops/*`, ops loader glue (protection impls, commands), `config/ServerOps` |
| D2 ops-client | `client/mode/*` (selection state machine, previews via planners, anchors, params), `config/ModeSettings` |
| E toolbox | `toolbox/*`, tool/upgrade/toolbox items, menu + screen, recipes, item textures + tool, `compat/BetterInventoryBridge`, `mixin/toolbox/*`, toolbox loader glue, `config/ServerToolbox` |
| I chisel | `chisel/*`, `mixin/chisel/*`, `config/ServerChisel`, shipped chisel defaults |
| F config-tabs | `common/config/**` (except `api/SettingsTabs.java` signatures), `MenuClient.java` options routing, docs/config.md |
| G betterinventory | `Source/QOL/BetterInventory` (toolbox slot, key migration, API class) |
| H icons | `tools/icons*`, `tools/IconsGen.java`, core `Icon.java` + `icons.png`, `assets/slate_building/icon.png` |

Lang: each agent writes its keys to `common/building/src/main/resources/assets/slate_building/lang/parts/<agent>.json`
(the lead merges them into `en_us.json`); F/H use their own module lang files.
