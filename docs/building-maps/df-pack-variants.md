## DF pack scout report (read-only; nothing in the pack was changed)

Paths are relative to `C:/Users/leonr/AppData/Roaming/CloudLauncher/default/packs/df/game/` unless marked. Loader is NeoForge 21.1.250 (from CloudLauncher `packs-cache.json`). There are 228 files in `mods/`. The pack has no chisel-type mod: no Chisel, Chipped, Rechiseled, FramedBlocks, Copycats+, vertical-slab mod, Effortless, Building Gadgets or WorldEdit.

### 1. Create 6.0.10 copycats (`mods/create-1.21.1-6.0.10.jar`)
Create is the only jar in the pack that mentions "copycat". The Aeronautics bundle (`create-aeronautics-bundled-1.21.1-1.3.0.jar`, which nests aeronautics, offroad and simulated 1.3.0) has none.

**Player-facing copycats: only two.** Both are made in a stonecutter from `#c:ingots/zinc` (4 per ingot) and each drops itself.

| Block | Blockstate props | Collision shape | Placement (`getStateForPlacement`) |
|---|---|---|---|
| `create:copycat_step` | `FACING` (horizontal), `HALF` (`Half`), `waterlogged` | `AllShapes.STEP_BOTTOM` = `shape(0,0,8,16,8,16)` for SOUTH; `STEP_TOP` = `shape(0,8,8,16,16,16)`. It is a quarter block, 16w × 8h × 8d. | `FACING = ctx.getHorizontalDirection()`. `HALF=TOP` if the clicked face is DOWN, or if the face isn't UP and the click's y-fraction is > 0.5 (same rule as stairs). |
| `create:copycat_panel` | `FACING` (all 6), `waterlogged` | `AllShapes.CASING_3PX` = `shape(0,0,0,16,3,16).forDirectional()`, a 3px plate | `FACING = ctx.getNearestLookingDirection().getOpposite()` |

- **Internal blocks** (loot table drops `minecraft:air`):
  - `create:copycat_base`: cube_all with `create:block/copycat_base`. It is the "no material" look.
  - `create:copycat_bars`: `facing=*`, models `copycat_panel/bars[_vertical]`. It is the model source when a panel's material is bars-like.
- **Blockstate JSONs** for step and panel point at `minecraft:block/air`. All rendering comes from the custom baked model.
- **Item models:** `create:block/copycat_base/step` (4 elements, from `[0,0,4]` to `[16,8,12]`) and `copycat_base/panel` (from `[0,0,0]` to `[16,3,16]`).
- **Tags:** `create:copycat_allow` = `[minecraft:barrel]`. `create:copycat_deny` = `[#cauldrons, #saplings, #climbable]`.
- **Placement helpers:** `CopycatStepBlock$PlacementHelper extends com.simibubi.create.foundation.placement.PoleHelper<Direction>`. `CopycatPanelBlock$PlacementHelper` also exists. They are registered through catnip, which lives in the nested `ponder-neoforge-1.0.82` jar:
  - `net.createmod.catnip.placement.PlacementHelpers.register(IPlacementHelper) : int`
  - `IPlacementHelper.getOffset(Player, Level, BlockState, BlockPos, BlockHitResult) : PlacementOffset`
  - `IPlacementHelper.displayGhost(PlacementOffset)`
  - `IPlacementHelper.renderAt(...)`
  - Config is `config/ponder-client.toml [placementAssist] indicatorType="TEXTURE", indicatorScale=1.0`.

**How a material is applied** (bytecode of `CopycatBlock`):
- **Right-click with a `BlockItem`:**
  - `getAcceptedBlockState(Level, BlockPos, ItemStack, Direction)` accepts:
    - the allow tag or `isAcceptedRegardless`;
    - otherwise a block that is not in the deny tag, whose shape bounds equal `Shapes.block()`, and whose collision is not empty.
  - It orients `FACING` / `HORIZONTAL_FACING` / `AXIS` / `HORIZONTAL_AXIS` to the clicked face. Then `prepareMaterial(Level, BlockPos, BlockState, Player, InteractionHand, BlockHitResult, BlockState)` runs; the panel overrides it for trapdoor and bars materials.
  - If the block already has a different material, the click passes.
  - If there is no material yet: `be.setMaterial(state)` and `be.setConsumedItem(stack)`, play the place sound, and shrink the stack by 1 unless in creative.
  - Right-clicking again with the same material block calls `CopycatBlockEntity.cycleMaterial()`. It cycles trapdoor `HALF`/`OPEN`, `FACING`, `HORIZONTAL_FACING` (clockwise), `AXIS`, `HORIZONTAL_AXIS`, `LIT`, or rose-quartz `POWERING`.
- **Placing with a block in the offhand:** `setPlacedBy` applies the offhand block automatically, oriented by `Direction.orderedByNearest(entity)`.
- **Wrench:** `onWrenched` returns `consumedItem` to the inventory (unless creative) and resets the material to `COPYCAT_BASE`. `onSneakWrenched` first tries to strip the material; if nothing was stripped it falls back to `IWrenchable.onSneakWrenched` (dismantle).

**Storage:** `CopycatBlockEntity extends SmartBlockEntity` has fields `BlockState material` and `ItemStack consumedItem`. Methods: `getMaterial()`, `hasCustomMaterial()`, `setMaterial(BlockState)`, `cycleMaterial()`, `getConsumedItem()`, `setConsumedItem(ItemStack)`, `getModelData()`. It also implements `TransformableBlockEntity`, `PartialSafeNBT` and `SpecialBlockEntityItemRequirement`. Class hierarchy: `CopycatBlock extends Block implements IBE<CopycatBlockEntity>, IWrenchable` → `WaterloggedCopycatBlock` → `CopycatStepBlock` / `CopycatPanelBlock`. It overrides many material-proxy hooks: `getSoundType`, `getFriction`, `getLightEmission`, `getExplosionResistance`, `getDestroyProgress`, `canHarvestBlock`, `getCloneItemStack`, `hidesNeighborFace`, `supportsExternalFaceHiding`, `getAppearance`, `wrappedColor()`, and others.

**Quad-cropping approach** (signatures from `javap`):
```
abstract class CopycatModel extends com.simibubi.create.foundation.model.BakedModelWrapperWithData   // extends NeoForge BakedModelWrapper<BakedModel>
  public static final ModelProperty<BlockState> MATERIAL_PROPERTY;   // + private OCCLUSION_PROPERTY, WRAPPED_DATA_PROPERTY, IS_EMISSIVE_PROPERTY
  protected ModelData.Builder gatherModelData(ModelData.Builder, BlockAndTintGetter, BlockPos, BlockState, ModelData);
  private void gatherOcclusionData(BlockAndTintGetter, BlockPos, BlockState, BlockState, CopycatModel$OcclusionData, CopycatBlock);
  public List<BakedQuad> getQuads(BlockState, Direction, RandomSource, ModelData, RenderType);
  protected abstract List<BakedQuad> getCroppedQuads(BlockState state, Direction side, RandomSource, BlockState material, ModelData wrappedData, RenderType);
  public static BlockState getMaterial(ModelData);  public static BakedModel getModelOf(BlockState);
class CopycatModel$OcclusionData { boolean[] occluded; void occlude(Direction); boolean isOccluded(Direction); }
class FilteredBlockAndTintGetter implements BlockAndTintGetter  (BlockAndTintGetter wrapped, Predicate<BlockPos> filter) // hides neighbours for CTM
CopycatStepModel / CopycatPanelModel / CopycatBarsModel extends CopycatModel  -> getCroppedQuads(...)
CopycatSpecialCases.isBarsMaterial(BlockState) / isTrapdoorMaterial(BlockState)
com.simibubi.create.foundation.model.BakedModelHelper.cropAndMove(int[] vertices, TextureAtlasSprite, AABB crop, Vec3 move) : int[]
com.simibubi.create.foundation.model.BakedQuadHelper.cloneWithCustomGeometry(BakedQuad, int[]) : BakedQuad   // + getXYZ/setXYZ/getU/setV/... on int[] vertex data
```
How it works:
- `gatherModelData` stores the material state. It also asks the material's model for its own data through `FilteredBlockAndTintGetter`, which only lets through neighbours that pass `canConnectTexturesToward` (this is what makes connected textures work). Per-face occlusion comes from `Block.shouldRenderFace` and `hidesNeighborFace`, and there is an emissive flag.
- `getCroppedQuads` takes the material's full-block quads (`BlockRenderDispatcher.getBlockModel(material)`) for each side and loops over sub-boxes (`Iterate.trueAndFalse`, offsets such as `VEC_Y_2/3/N2`, `CUBE_AABB.contract/move`). Each quad's vertex array goes through `cropAndMove`, which clips positions to the box and remaps UVs inside the same sprite, and the result is re-wrapped with `cloneWithCustomGeometry`.
- The panel uses the bars model (`AllBlocks.COPYCAT_BARS`) or the trapdoor's own model for those special materials.
- **Everything here is NeoForge-model-API based.** A Slate version needs one model wrapper per loader: a `BakedModelWrapper` plus `ModelData` on NeoForge, and `FabricBakedModel` with `emitBlockQuads` on Fabric.

### 2. Other placement, variant and UI mods

| Mod (jar) | What it is | Interop / risk |
|---|---|---|
| **KleeSlabs** 21.1.11 | Breaks only the half of a double slab you look at. Config `mode="ALWAYS"`. | Tags `kleeslabs:slabs` (= `#minecraft:slabs`, uses a horizontal `SlabBlock` converter), `kleeslabs:vertical_slabs`, `vertical_slabs/{quark,enchanted_vertical_slabs,nemos_vertical_slabs}`. Converters: `SlabConverter.isDoubleSlab(BlockState)` and `VerticalSlabConverter.getSingleSlab(BlockState, Level, BlockPos, Player, Direction)`. Registry: `SlabRegistry.registerSlabConverter(Block/TagKey, SlabConverter)`. Our slabs work for free if they are `SlabBlock` in `#minecraft:slabs`. Vertical slabs need a converter or JSON compat entry (format like `data/kleeslabs/kleeslabs_compat/*.json`: `{modid, converter, slabs[]}`). |
| **terrain_slabs** 3.1.0 (`net.countered.terrainslabs`) | Worldgen that places ~57 natural blocks on terrain edges (grass/dirt/sand/terracotta/stone-type slabs, plus `*_on_top` plant blocks). `ModSlabsMap.getSlabForBlock(Block)`. | These blocks exist in worlds. Stone-type ones duplicate vanilla slabs: `terrain_stone_slab` drops `minecraft:stone_slab` with silk touch. Needs mapping if "replace other variants" is on. |
| **DiagonalFences** 21.1.1, **DiagonalWalls** 21.1.2, **DiagonalWindows** 21.1.1 (Fuzs, nested lib `diagonalblocks`) | Registers a new diagonal twin block for every block that is an `instanceof FenceBlock` / `WallBlock` / `IronBarsBlock` with the vanilla property count (`DiagonalBlockTypeImpl.isTarget`). It then **re-points the BlockItem** to place the twin (`DiagonalBlockHandler.setBlockForItem`). | **High risk.** In DF, fence/wall/pane items place `diagonal*`-namespaced blocks, not the vanilla ones, so a variant mapping must follow `DiagonalBlockType.getBlockConversions()` (a `BiMap`). Our own fence/wall subclasses would get twins too, unless we change their property count or use `DiagonalBlockType.disableBlockFactory(ResourceLocation)`. Opt-out tags `diagonalfences:non_diagonal_fences`, `diagonalwalls:non_diagonal_walls`, `diagonalwindows:non_diagonal_windows` only apply at runtime. |
| **BridgingMod** 2.6.2 | Reach-around bridging plus slab assist (`enableSlabAssist=true`, `showCrosshair=true`). Key `key.bridgingmod.toggle_bridging` = `,`. | It changes where you place through `MinecraftClientMixin` / `OutlineRendererMixin`. Our ghost preview should read the vanilla hit result or defer while it is bridging. |
| **Accurate Block Placement** 1.0.0 (`com.clayborn.accurateblockplacement`) | Changes held-right-click placement cadence (mixins on `Minecraft` `startUseItem`/`rightClickDelay`, `GameRenderer`, `MultiPlayerGameMode`). Both keys (`togglevanillaplacement`, `togglefastbreaking`) are unbound. | Has a **patched** class `patch/AbpGate.blockOverridden(Object)` (dated 2026-08-31) that skips blocks overriding `useItemOn` / `useWithoutItem`. Our variant blocks count as interactive if they override those. Custom right-click placement must respect ABP's cooldown. |
| **Carry On** 2.2.6.13 | Shift + empty-hand right-click picks up blocks and entities. `key.carry.desc` = Left Shift. | Avoid a Shift + empty-hand right-click action. |
| Create placement helpers | See section 1. | Hold-to-extend ghost for copycats and poles, drawn on its own. |
| **Create Toolbox** (`content.equipment.toolbox.RadialToolboxMenu`, `ToolboxHandlerClient`) | Holding **Left Alt** (`AllKeys.TOOLBELT` "Access Nearby Toolboxes") opens a radial menu of a nearby Create Toolbox. `AllKeys.TOOL_MENU` "Focus Schematic Overlay" and `ALT_MODIFIER` are also Left Alt. `ROTATE_MENU` (radial wrench) is unbound. `ACTIVATE_TOOL` is Left Ctrl. | Creates a **name clash** ("toolbox") and a **key clash** (hold Alt → radial wheel) with the planned design. |
| **JEI** 19.44.0.403 | Recipe viewer. `showRecipe` R / A and `showUses` U only while hovering in a GUI (`JeiKeyConflictContext.JEI_GUI_HOVER`). | Hiding deleted variants needs JEI runtime hiding. |
| **Jade** 15.10.6 | Block tooltip. Keypad 0–5 (config, overlay, liquid, recipes, uses, narrate). `builtinCamouflage=true`. | Low risk. |
| **Open Parties and Claims** 0.28.1 | Claims. On NeoForge it hooks `xaero.pac.common.event.CommonEventsNeoForge`: `onEntityPlaceBlock(BlockEvent$EntityPlaceEvent)`, `onEntityMultiPlaceBlock(BlockEvent$EntityMultiPlaceEvent)`, `onDestroyBlock(BlockEvent$BreakEvent)`, `onLeftClickBlock` / `onRightClickBlock` / `onItemRightClick(PlayerInteractEvent$*)`, `onExplosionDetonate(ExplosionEvent$Detonate)`, `onCropTrample`. API: `OpenPACServerAPI.get(MinecraftServer).getChunkProtection()` → `IChunkProtectionAPI.onEntityPlaceBlock(Entity, ServerLevel, BlockPos)`, `onBlockInteraction(Entity, InteractionHand, ItemStack, ServerLevel, BlockPos, Direction, boolean, boolean[, boolean])`. Menu key = `'`. | **Area and paste modes that call `level.setBlock` directly bypass claims.** Fire the break/place events per block through a platform hook; `common/` cannot see `net.neoforged`. `aeroclaims` 0.9.2 (Aeronautics claims, OPAC/FTB-compatible) also applies. |
| **Curios** 9.5.1 | Accessory slots. Open key `key.curios.open.desc` = G. Slot types present: curios (back, belt, body, bracelet, charm, curio, hands, head, necklace, ring), ars (`an_focus`, belt, head, necklace, ring), irons (spellbook, necklace, ring), relics (back, belt, charm, curio, feet, hands, necklace, ring). | A toolbox slot could be a `data/<ns>/curios/slots/*.json` plus an entities json. BetterInventory already has `compat/CuriosCompat`. |
| **Inventory Sorter** 24.0.24 | Middle-click sort and scroll-wheel item moving, in GUIs only (`dodgemousetweaks=true`). | Low risk. |
| **TrashSlot** 21.1.11 (Balm/kuma) | Keys: `toggle` T, `delete` Delete, `delete_all` Shift+Delete, `toggle_lock` unbound. All in GUIs. | Low risk. |
| **MouseTweaks** 2.26.1 | Inventory drag and wheel tweaks, GUI only. No keybinds. | Low risk. |
| **Ping-Wheel** 1.12.2 | Location pings. `ping_location` = mouse button 5 (MOUSE type, code 4); `open_settings` unbound. **No radial menu class in the jar.** | Low risk. |
| **Shoulder Surfing** 5.0.11 | Over-the-shoulder camera. `free_look` = **Left Alt**, `swap_shoulder` = U, camera adjust on arrows / PgUp / PgDn. Config `decoupled_camera=true`, `pick_vector="CAMERA"`. | Alt clash. The ghost preview and area picks must use `Minecraft.hitResult`, which Shoulder Surfing rewrites, not our own eye ray. |
| Also relevant | Polymorph 1.1.0 (recipe-conflict picker), Sable 2.0.3 + Aeronautics (physics sub-levels: block pick and placement on moving structures), Immersive Portals 6.0.7 (the world renders several times per frame; affects the ghost render), Iris 1.8.14-beta.1 + Sodium 0.8.12 (translucent ghost with shaders), Controlling 19.0.5. | — |

**Recipes that consume shape variants** (matters for "replace variants"):
- Create has 161: 57 `*_slab_recycling` recipes (2 slabs → 1 block), 66 `deploying` (wax/scrape on copper stairs and slabs), plus crafting such as honeycomb waxing and seats from `#minecraft:wooden_slabs`.
- burnt_basic 16, natures_spirit 8, ars_nouveau 6, BOP 4, VanillaBackport 3, irons 2, AE2 1. The most-used ingredient is the tag `#minecraft:wooden_slabs` (27 uses).
- Mods with stairs/slab/fence/wall families: create (72/72/0/56 stairs/slab/fence/wall), natures_spirit (88/89/17/10), burnt_basic (26/29/14/9), BOP (20/23/13/4), ars_nouveau (23/23/1/0), AE2 (11/11/0/11), VanillaBackport (8/8/1/7), terrain_slabs (50 slabs).

### 3. Keybinds
- **There is no `options.txt` in the DF game folder.** There is also no `logs/`, no `servers.dat`, and `saves/` and `screenshots/` are empty. CloudLauncher's rules mark `options.txt` / `logs/` / `saves/` as Local (not synced), and its settings show DF PlayCount 0 with no LastPlayedAt. The list below is therefore the mods' **compiled defaults**, taken from a `javap` scan of every `KeyMapping` construction, including nested jars.
- **Left Alt (342), 6 bindings:**
  - `key.betterinventory.offhand_selector` (IN_GAME)
  - Create `toolbelt` (radial toolbox), `toolmenu` and `alt_modifier`
  - Iron's Spellbooks `spell_bar_modifier`
  - Relics `key.relics.active_abilities_list`
  - Shoulder Surfing `free_look`
- **R (82):**
  - Iris `iris.keybind.reload` (reloads shaders; always active in game)
  - Iron's Spellbooks `spell_wheel` (a radial wheel, IN_GAME)
  - Punchy `key.punchy.tuning.reset` (registered unconditionally)
  - JEI `showRecipe` (GUI hover only)
- **Right Alt (346): nothing binds it.** Only Balm's modifier table mentions it, and that is not a binding.
- **The letters you asked about are all taken by default:**
  - Z: Ars `previous_slot`, Xaero `enlarge_map`
  - X: vanilla `loadToolbarActivator`, Ars `next_slot`
  - C: vanilla `saveToolbarActivator`, Ars `open_book`, cinematiczoom `zoom`
  - V: Ars `selection_hud`, Iron's `spellbook_cast`, Simple Voice Chat `voice_chat`
  - B: atmospherics `open`, chatterbox `record`, Xaero `new_waypoint`
  - G: Curios open, Ars `head_curio_hotkey`, guideme `guide`, Simple Voice Chat `group`, Punchy config `sword_inspect_rework`
  - H: Simple Voice Chat `hide_icons`
  - J: ToastControl `clear`, Punchy config `sword_inspect_old`
  - K: Iris `toggleShaders`
  - U: corpse `death_history`, Xaero `waypoints`, Shoulder Surfing `swap_shoulder`, JEI `showUses` (GUI)
  - Y: Xaero `minimap_settings`
  - N: Simple Voice Chat `disable_voice_chat`
  - M: Xaero `open_map`, Simple Voice Chat `mute_microphone`
  - The least contended are **H, K, Y and N** (one binding each).
- **Other keys worth knowing:**
  - I: Punchy `inspect`, `aero_cam_sync` toggle
  - O: Iris shaderpack selection, ssrcamerafixes `shoulder_cycle`
  - W: Ponder, GUI hover only
  - **F7: Punchy `tuning.toggle_pivot`, which clashes with Slate dev mode's F7**
  - F6: Punchy `save_session` and Veil `editor`
  - F8 / F9: PowerScreenshot. CloudLauncher's own window toggle is also F8.
- **Fabric vs NeoForge:** NeoForge lets several mappings share a key (split by conflict context and modifier). Vanilla and Fabric keep one mapping per key in `KeyMapping.MAP`, so a duplicate shadows the other on Fabric. This is vanilla behaviour, not verified in these jars.

### 4. Relevant configs
- `betterinventory-client.toml`: `useVanillaInventory=false`, `strongLockedSlots=true`, zoom enabled (scale 3.5), `hud.showToolInHand=true`.
- `betterinventory-common.toml`: `wornToolFloor=true`.
- `betterinventory-startup.toml`: backpack placeable and openable; `stackMode="VANILLA"`.
- BetterInventory jar 1.0.1 has `client/screen/BlockChooserScreen`, `logic/ToolService`, a `tool_slot` concept and `compat/CuriosCompat`. Its offhand selector is `KeyMapping("key.betterinventory.offhand_selector", KeyConflictContext.IN_GAME, KEYSYM, 342, "key.categories.betterinventory")`, registered in `BetterInventoryClient`. Source is `Source/QOL/BetterInventory`.
- `kleeslabs-common.toml`: `mode="ALWAYS"`.
- `terrain_slabs.json`: slab generation on, `enableCornerSlabs=false`.
- `bridgingmod.json`: see the table above.
- `accurateblockplacement.properties`: `accurateplace-enabled=true`, `fastbreak-enabled=false`.
- `carryon-common.toml`: `pickupAllBlocks=false`, `heavyTiles=true`.
- `ponder-client.toml [placementAssist]`: `indicatorType="TEXTURE"`. `create-client.toml` has no placement settings.
- `shouldersurfing-client.toml`: `decoupled_camera=true`, `pick_vector="CAMERA"`, `crosshair_type="STATIC"`.
- `curios-common.toml`: `slots=[]`.
- `pingwheel.json`: `playerInfoMode="HOLD"`.
- `config/` has no `slate/` folder. There are stale configs for mods that are not installed (oreexcavation, waystones, amendments, visualworkbench and others).

### 5. Where Slate went
- **No trace of Slate anywhere under CloudLauncher.**
  - `mods/` has no `slate-*` jars and no `*.disabled` files.
  - `chatterbox-neoforge-1.21.1.jar` is enabled (mtime 2026-09-16).
  - `config/slate/` does not exist.
  - The only "slate" hits in `mod-fingerprints.json` are the word "translated".
  - `game/.cloudlauncher/mods.json` (2026-09-22 01:45) and `packs/df/.sync-manifest.json` (2026-09-22 01:38) both list chatterbox, not Slate.
- **The timestamps say `mods/` has not changed since 2026-09-21 08:11:39.** That is the directory's own modification time, and the same holds for `config/`. The install (commits around 2026-09-23 02:40) and the later removal would both have updated it.
- The install's boot test would have created `logs/latest.log` and `options.txt`. Neither exists.
- CloudLauncher's client sync (`OtherProjects/CloudLauncher/CloudLauncher/Services/PackFolderService.cs`, around line 785) only deletes files that are listed in its own sync lock. It would not have removed `slate-*` jars, and it would not explain the unchanged directory time.
- **Conclusion:** the install and boot writes most likely never reached the real folder (redirected or reverted outside the repo), or the folder was restored with its timestamps kept. I cannot tell which from here.
- `docs/MORNING-REPORT-2026-09-23.md` and `PROGRESS.md` in the Slate repo claim an install that is not on disk now.

### 6. Slate DF boot harness (`tools/boot-df.ps1`, plus `tools/install-df.ps1`)
- **Parameters:** `-Pack` (default `%APPDATA%\CloudLauncher\default\packs\df\game`), `-Harness`, `-Analyzer`, `-Seconds 240`, `-Version "neoforge-21.1.250"`. The version matches the pack.
- **Steps:**
  1. Renames `drippyloadingscreen-earlywindow_*.jar` to `*.bootparked`. Its early window breaks the harness (`CustomLoadingOverlay` class not found).
  2. Runs `cmd /c "<boot.exe>" "<Pack>" <Version> <Seconds> 8192 > %TEMP%\slate-df-boot.log 2>&1`. Exit code 100 means the game was still running at the timeout, i.e. it got past mod loading.
  3. In a `finally` block, renames Drippy back.
  4. Runs `python <analyze_log.py> <Pack>\logs\latest.log` if the analyzer exists.
  5. Prints the first 40 lines matching `Slate|slate_`.
- **The harness is currently unusable.** `-Harness` points at a CmlLib .NET 10 `boot.exe` and `-Analyzer` at `analyze_log.py`, both under `...\Temp\claude\C--Users-leonr-Coding\e94ada8d-...\scratchpad\`. That folder no longer exists, `%TEMP%\slate-df-boot.log` is gone, the harness source is not in the repo, and there is no real Python on PATH (only the Microsoft Store stub). CloudLauncher's own CmlLib launch code is in `OtherProjects/CloudLauncher/CloudLauncher/Services/LaunchService.cs` and `TestLaunchScope.cs`.
- **`install-df.ps1`:** deletes `mods/slate-*-neoforge-1.21.1-*.jar*`. It copies the newest `neoforge/<m>/build/libs/slate-<m>-neoforge-1.21.1-*.jar` for core, menu, multiplayer, chat and config. Unless `-KeepChatterbox` is given, it renames `chatterbox-*.jar` to `.disabled`. It will need `building` added to that module list.

Scratch artifacts (bytecode dump, key-mapping list, recipe scan) are in `C:\Users\leonr\AppData\Local\Temp\claude\C--Users-leonr-Coding-MinecraftMods-Source-UI-Slate\84b04579-fab4-4717-a704-804ffaef9973\scratchpad\` (`keys\keymaps.txt`, `keys\dump.txt`, `recipes.txt`).