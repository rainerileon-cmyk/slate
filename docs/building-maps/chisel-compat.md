# Chisel-wheel compat and building-mod UX research (MC 1.21.1, NeoForge and Fabric)

Every vanilla signature below was checked with `javap` against the mapped 1.21.1 jar (`Ports/_template/fabric/.gradle/loom-cache/.../minecraft-merged-...-v2.jar`). Mod facts come from each mod's GitHub source on its 1.21.1 branch. Paths are relative to the repo named.

## 0. Recommended architecture (short)
- **Build a server-side `ChiselGroups` index and send it to clients with our own payload.** Rechiseled's groups are not vanilla recipes. They sit only in the server's datapack ResourceManager, so the client cannot see them without Rechiseled's own sync.
- **When to rebuild the index:**
  - on `SlateEvents.SERVER_STARTED` (`common/core/.../event/SlateEvents.java:35`);
  - after `/reload`. Core has no datapack-reload event. Add one in a new Core file as a mixin at TAIL of `net.minecraft.server.players.PlayerList#public void reloadResources()`, which vanilla calls after the datapack reload to resend tags and recipes;
  - send to each player on `PLAYER_JOINED`.
- **Server APIs to read the data with:**
  - `MinecraftServer#public ResourceManager getResourceManager()`
  - `ResourceManager#Map<ResourceLocation,Resource> listResources(String, Predicate<ResourceLocation>)`
  - `ResourceManager#List<Resource> getResourceStack(ResourceLocation)`
  - `MinecraftServer#public RecipeManager getRecipeManager()`
- **Providers:** each provider returns *its own wheel*, and LMB/RMB switches between wheels. Don't merge them: andesite, for example, is in 5 sources at once. Provider order: user config overrides → Rechiseled → Chipped → Chisel Modern → Chisel Reborn → Create/stonecutter graph → vanilla `BlockFamilies`.
- **Detection:** check for the data itself (a directory, tag namespace or recipe type), not for a mod id. Chisel Modern and Chisel Reborn **both use mod id `chisel`**.

## 1. Chisel-style mods on 1.21.1

| Mod | Loaders (1.21.1) | mod id | Latest seen | How groups are defined | Readable from data alone? |
|---|---|---|---|---|---|
| **Rechiseled** (SuperMartijn642) | NeoForge + Fabric (branches `neoforge-1.21`, `fabric-1.21`) | `rechiseled` | `mod_version=1.2.6` | Custom data dir `data/<ns>/chiseling_recipes/*.json` (not a vanilla recipe) | Yes: read the JSON yourself on the server |
| **Chipped** (Terrarium) | NeoForge + Fabric (`enabledPlatforms=fabric,neoforge`) | `chipped` | `version=4.0.2` | Item and block tags `chipped:<base>`, listed by recipe type `chipped:workbench` | Yes: tags, synced to the client |
| **Chisel Modern** (Leclowndu93150, fork of Chisel-Team code) | NeoForge / Forge only | `chisel` | `mod_version=1.4.1`, neo 21.1.215 | Block and item tags `chisel:carving/<name>` | Yes: tags. KubeJS edits are invisible to us |
| **Chisel Reborn** (matthewperiut) | Fabric + NeoForge (+Forge/Quilt), Architectury, Yarn | `chisel` | branch `1.21.1`, `mod_version = 2.0.1` | Hard-coded in Java (static map) | No. Needs reflection, or an id heuristic |
| Chisels & Bits | NeoForge (21.1.33, Jan 2026) | `chiselsandbits` | — | Bit-level block entities, no variant groups | Nothing to read. Exclude its blocks from every operation |
| FramedBlocks (XFactHD) | NeoForge only (10.6.x) | `framedblocks` | — | Camouflage frames (block entity), no groups | No |
| Create copycats (**in the DF pack**, `create-1.21.1-6.0.10.jar`) | NeoForge (Fabric only via the community `netherg-io/create-fabric` fork) | `create` | 6.0.10 | `create:copycat_panel`, `create:copycat_step` (plus internal `copycat_base`, `copycat_bars`). Tags `create:copycat_allow`=[`minecraft:barrel`], `create:copycat_deny`=[`#minecraft:cauldrons`,`#minecraft:saplings`,`#minecraft:climbable`] | Shape reference only |
| Copycats+ | NeoForge (3.0.x for 1.21.1) | `copycats` | — | Copycat shapes (slopes, vertical steps, layers, beams…) | No |
| Macaw's (Roofs, Fences & Walls, …) | Forge / NeoForge / Fabric | `mcwroofs`, `mcwfences`, … | — | Shape catalogues, no groups | No |
| Additional Placements (FirEmerald) | NeoForge + separate Fabric port | — | 2.2.x | Adds vertical slab/stair *states* to existing blocks | **Overlaps our vertical variants**, so detect it and warn |

**DF pack scan:** I checked all 228 jars in `CloudLauncher/default/packs/df/game/mods/`. None contains `chiseling_recipes/` or `tags/*/carving/`, so no chisel mod is installed. The only chisel-like data in the pack is **Create's palette stonecutting** (section 2).

### 1a. Rechiseled (`SuperMartijn642/Rechiseled@neoforge-1.21`)
- **Loader:** `src/main/java/com/supermartijn642/rechiseled/chiseling/ChiselingRecipeDatapackPlugin.java`.
  - It calls `resourceManager.listResources("chiseling_recipes", r -> r.getPath().endsWith(".json"))`, then `getResourceStack(loc)`.
  - Files at the same path across packs are **concatenated**; a later file with `"overwrite": true` clears the earlier ones.
  - Group id = `<ns>:<path without "chiseling_recipes/" and ".json">`.
  - The `"type"` key is written but never read.
- **Dependencies:** `supermartijn642corelib`, `supermartijn642configlib`, `fusion`.
- **Client sync:** its own `PacketUpdateChiselingRecipes`.
- **Example** (`src/generated/resources/data/rechiseled/chiseling_recipes/stone.json`, trimmed):
```json
{"type":"rechiseled:chiseling","entries":[
  {"block":"minecraft:stone","slab":"minecraft:stone_slab","slab_worth":0.5,"stairs":"minecraft:stone_stairs"},
  "minecraft:cracked_stone_bricks",
  {"block":"rechiseled:stone_big_tiles","connecting_block":"rechiseled:stone_big_tiles_connecting",
   "slab":"rechiseled:stone_big_tiles_slab","slab_worth":0.5,"stairs":"rechiseled:stone_big_tiles_stairs",
   "connecting_slab":"…","connecting_slab_worth":0.5,"connecting_stairs":"…"}]}
```
- **Entry grammar** (`chiseling/ChiselingEntryImpl.java#fromJson`), each entry is one of:
  - a bare id string;
  - an object with any of `block|stairs|slab|connecting_block|connecting_stairs|connecting_slab`, an optional `<key>_worth` (float > 0) and `optional` (bool);
  - **legacy** `item` / `connecting_item`. Older 1.21.1 jars only have this form: `ChiselingBlockShape {BLOCK, STAIRS, SLAB}` is dated 07/01/2026.
- **Rules for our reader:**
  - Accept both forms, and skip unknown ids (Rechiseled throws unless `optional`).
  - Use the shape siblings so the wheel keeps the shape you're holding (stone_slab → stone_brick_slab).
  - Show `connecting_*` as a toggle, not as extra slices.
  - Never swap between entries of different `worth` without charging the difference.

### 1b. Chipped (`terrarium-earth/Chipped@1.21.1`)
- **Recipe type:** `chipped:workbench` (`common/.../registry/ModRecipeTypes.java`, `RECIPE_TYPES.register("workbench", …)`; the serializer has the same id).
- **Recipes:** one per workbench under `common/src/main/generated/resources/data/chipped/recipe/`: `alchemy_bench, botanist_workbench, carpenters_table, glassblower, loom_table, mason_table, tinkering_table`. Example:
  `{"type":"chipped:workbench","ingredients":[{"tag":"chipped:ancient_debris"},{"tag":"chipped:andesite"},…]}`.
- **Groups** are the tags: `data/chipped/tags/item/andesite.json` → `{"values":["andesite","chipped:andesite_bricks", …]}` (66 members). There are 277 item tags plus 277 block tags.
- **Gotcha:** `ChippedRecipe` is `record ChippedRecipe(List<Ingredient> ingredients)` and **does not override `getIngredients()`**. Vanilla's `Recipe#default NonNullList<Ingredient> getIngredients()` returns `NonNullList.create()` (empty), so the generic recipe API returns nothing.
- **Runtime lookup (works on the client too, because tags are synced):**
```java
stack.getTags()                                   // ItemStack#Stream<TagKey<Item>> getTags()
     .filter(t -> t.location().getNamespace().equals("chipped"))
     .forEach(t -> BuiltInRegistries.ITEM.getTagOrEmpty(t)); // Registry#default Iterable<Holder<T>> getTagOrEmpty(TagKey<T>)
```
- **Exact alternative:** get the type with `BuiltInRegistries.RECIPE_TYPE.get(ResourceLocation.fromNamespaceAndPath("chipped","workbench"))`. Then call `RecipeManager#getRecipeFor(RecipeType<T>, I, Level)` with `new SingleRecipeInput(stack)` (raw types). Reflect the record accessor `ingredients()`, keep the `Ingredient#test(ItemStack)` hit, and read `Ingredient#ItemStack[] getItems()`. This mirrors `WorkbenchMenu.java:90`.
- **Hazards inside Chipped groups:** doors (2 tall), `barrel` (block entity), leaves, logs/stems (`axis`), torch/redstone_torch/lantern (attached), sand/gravel (gravity), `ancient_debris`, `gilded_blackstone` (drops nuggets), vine, lily_pad, iron_bars/panes, carpets.

### 1c. Chisel Modern (`Leclowndu93150/Chisel-Modern@master`, which is the 1.21.1 branch)
- **Tag name:** `api/block/ChiselBlockType.java:299` builds `BlockTags.create(Chisel.id("carving/" + name.replace("/", "_")))`.
- **Tag contents** (`data/provider/ChiselBlockTagsProvider.java`): chisel blocks, `addOptional(vanilla)` and `addOptionalTag(c:storage_blocks/...)`. The same ids exist as item tags. Block registry names are `chisel:<name>/<variation>`.
- **Lookup** (mirrors `carving/CarvingHelper.java#getCarvingGroupForItem`):
  `BuiltInRegistries.ITEM.getTagNames().filter(t -> ns=="chisel" && path.startsWith("carving/"))` → `stack.is(tag)` → `getTagOrEmpty(tag)`.
- **Limitation:** KubeJS changes (`KubeJSCompat.getAdditionalBlocks`) are not visible to us.

### 1d. Chisel Reborn (`matthewperiut/Chisel-Reborn@1.21.1`)
- **Groups are code only:** `common/.../block/ChiselGroupLookup.java` holds a static `Map<String, ChiselGroup>`, filled from `block/blocks/EzReg.java`.
  - Block id is `chisel:<pattern>/<material>`; the group name is `<material>`.
  - `minecraft:<material>` is added to its group.
  - Special groups: `nether_bricks`, glass, ice.
  - `ChiselGroup.inTags()` is a stub that returns false.
- **Without a compile dependency:** call `Class.forName("com.periut.chisel.block.ChiselGroupLookup").getMethod("getBlocksInGroup", Item.class).invoke(null, item)`, which returns `List<Item>`.
  - This is safe on Fabric: the parameter is the vanilla `Item` class, and the method name is not remapped.
  - Fallback heuristic: every `chisel:*/<m>` plus `minecraft:<m>`.
- **Telling it apart from Chisel Modern:** check for class `com.periut.chisel.Chisel` versus `com.leclowndu93150.chisel.Chisel`, or whether any `chisel:carving/*` tag exists.

## 2. Vanilla-only fallback: stonecutter graph plus BlockFamilies

**What the 1.21.1 jar contains** (all `data/minecraft/recipe/*stonecutting*`):
- 250 stonecutting files; 106 edges have count > 1.
- 44 full-block → full-block 1:1 edges, and **none of them is bidirectional**.
- 22 connected components:
  - tuff{tuff, polished, bricks, chiseled, chiseled_bricks}
  - deepslate{cobbled, polished, bricks, tiles, chiseled}
  - quartz{block, bricks, pillar, chiseled}
  - blackstone{blackstone, polished, polished_bricks, chiseled_polished}
  - stone{stone, stone_bricks, chiseled_stone_bricks}
  - sandstone ×3, red_sandstone ×3
  - andesite, diorite and granite pairs with their polished forms
  - basalt/polished, nether_bricks/chiseled, end_stone/end_stone_bricks, purpur_block/pillar
  - 8 separate `cut_copper`↔`chiseled_copper` pairs, one per oxidation × wax state.

**Why use the undirected components as default groups:**
- Every edge is 1:1, so going backwards (stone_bricks → stone) creates no items. This is how Rechiseled already behaves (stone ↔ stone_bricks ↔ cracked ↔ mossy).
- It covers modded stones automatically. **Create palettes** use tag ingredients, e.g. `data/create/recipe/cut_granite_from_stone_types_granite_stonecutting.json` = `{"type":"minecraft:stonecutting","ingredient":{"tag":"create:stone_types/granite"},"result":{"count":1,"id":"create:cut_granite"}}`. The tag `create:stone_types/granite` holds 15 ids, including `minecraft:granite` **and its stairs and walls**. So expand tag ingredients, and filter shape blocks out of the texture wheel.
- Read the edges with:
  - `RecipeManager#public <I,T> List<RecipeHolder<T>> getAllRecipesFor(RecipeType<T>)` with `RecipeType.STONECUTTING`
  - `SingleItemRecipe#public NonNullList<Ingredient> getIngredients()`
  - `SingleItemRecipe#public ItemStack getResultItem(HolderLookup$Provider)`
  - `RecipeHolder#id()` and `#value()`

**BlockFamilies fills the gaps.**
- API: `net.minecraft.data.BlockFamilies#public static Stream<BlockFamily> getAllFamilies()` and `BlockFamily#getBaseBlock()`, `#getVariants(): Map<BlockFamily$Variant, Block>`.
- `Variant` has `CHISELED, CRACKED, CUT, MOSAIC, POLISHED` and the shape values (`STAIRS, SLAB, WALL, FENCE, …`).
- There are 67 families. The class carries no `@Environment` marker in the merged jar, so it is available on both sides.
- It adds edges that only exist through smelting or crafting: `cracked_stone_bricks`, `cracked_deepslate_bricks/tiles`, `cracked_nether_bricks`, `cracked_polished_blackstone_bricks`, `bamboo_mosaic`, `copper_block→cut_copper`, and `cut_copper→chiseled_copper` for each state.
- Its shape `Variant`s are also the ready-made vanilla shape map for the *shape* wheel.
- **Neither source covers:** mossy variants (crafted with moss or vine), smooth stone, smooth sandstone or smooth quartz (smelted), and cobblestone↔stone. Put those in a shipped default override file.

**Pitfalls:**
1. **1:N edges cause dupes.** Stonecutter gives `copper_block → cut_copper ×4`, `→ copper_grate ×4`, `→ chiseled_copper ×4`, `→ cut_copper_slab ×8`, and every block → slab ×2. If copper_block and cut_copper share a group, you can cut 1 block into 4 and chisel each back: an infinite dupe. **Rule:** drop any group link where a recipe between two members yields count ≠ 1, or charge using a worth ratio. This also applies to BlockFamilies' `copper_block→cut_copper` CUT link.
2. **Links only go one way.** Every vanilla link is one-directional. Treating them as undirected is deliberate, but it skips the moss cost (plain → mossy). Offer a server config `chisel.respectDirection`.
3. **Copper:**
   - Oxidation and wax are separate blocks, and the components are already separated per state.
   - Never let a swap cross states, or players get free wax, un-wax and de-oxidation.
   - Map states with `WeatheringCopper.getPrevious/getNext(Block)`, `WeatheringCopper.getFirst(Block)` and `HoneycombItem.WAXABLES` (`Supplier<BiMap<Block,Block>>`).
   - Modded copper on NeoForge uses the data maps `NeoForgeDataMaps.OXIDIZABLES/WAXABLES` (`net/neoforged/neoforge/registries/datamaps/builtin/`); on Fabric it's `OxidizableBlocksRegistry`. Those are loader-only, so reach them through a platform hook.
4. **Full-block filter.** Slabs, stairs and walls reached from a full block are shape variants, not texture variants. Filter them by `instanceof SlabBlock/StairBlock/WallBlock` or by BlockFamily shape variants.
5. **In-world swapping skips loot tables.** Stone drops cobblestone, but after a swap to stone_bricks it drops itself, which acts like free silk touch. Only swap placed blocks when `Block.getDrops(state, level, pos, be)` without a tool equals the block's own item, or require a silk-touch upgrade.
6. **Swapping blocks in the world:**
   - Copy the block-state properties both blocks share (axis, facing, half, waterlogged).
   - Refuse block entities (barrels), multi-block states (doors) and gravity blocks unless each case is handled.
7. **Rebuild the groups on `/reload`,** because recipes and tags change.

## 3. UX survey of existing building mods (what to borrow)

- **Effortless Building (Requios):**
  - **Hold ALT for the radial menu.** Build modes are in the centre, per-mode options on the right, and undo/redo, replace, modifier settings and config on the left.
  - Modifiers stack: Mirror (placed plane with size), Array (offset × count) and Radial Mirror (N slices).
  - Modifier settings key is NUMPAD+. It also has a randomizer bag and works in survival.
  - ALT conflicts with BetterInventory's Alt.
- **Construction Wand (Theta-Dev), 1.21 README:**
  - Tiers:

    | Wand | Durability | Max blocks | Angel distance | Destroy max |
    |---|---|---|---|---|
    | Stone | 131 | 9 | — | — |
    | Iron | 250 | 27 | 1 | 9 |
    | Diamond | 1561 | 128 | 4 | 25 |
    | Infinity | unbreakable | 1024 | 8 | 81 |

  - Cores are added in a crafting grid: Construction, Angel (mid-air placement using the offhand block) and Destruction (no block entities).
  - Options: row/column lock, Player/Target direction, replace (fluids, snow, grass), Match EXACT/SIMILAR/ANY, random from hotbar.
  - OPTKEY (Ctrl): Sneak+OPTKEY+LMB shows the last placement with a green border; Sneak+OPTKEY+RMB undoes it with items refunded.
  - Block sources: inventory, shulkers, bundles, modded containers. It has a block-entity blacklist (C&B blocked by default).
- **Building Gadgets 2 (Direwolf20, NeoForge 1.21.1):**
  - Gadgets: Building, Exchanging, Copy-Paste, Cut-Paste, Destruction. Modes (`util/modes/`): BuildToMe, VerticalColumn, HorizontalRow, VerticalWall, HorizontalWall, Stairs, Grid, Surface, plus Copy, Cut, Paste.
  - Radial menu (`ModeRadialMenu`), material list GUI, template manager and "Redprint" templates, per-player undo (`PacketUndo`).
  - Energy cost per block, from `setup/Config.java`: build 50 FE, exchange 100, paste 50 with copy free. `rayTraceRange` is 32 (max 64).
  - **Tick pacing** (`common/events/ServerTickHandler.java`): `amountPerTick = min(max(floor(size/300), size<60?1:5), 50)`. The queue stops when the player logs out.
  - Placed blocks go through a temporary `RenderBlock` block entity with a grow-in animation. Previews are cached in a VBO (`client/renderer/VBORenderer.java`).
  - **Protection** (`util/BuildingUtils.java:430,477,536`): `level.mayInteract(player,pos)` plus a manually fired NeoForge `BlockEvent.BreakEvent`.
- **Axiom:** creative only. Take its UX ideas only:
  - gizmo move/rotate;
  - magic, box and lasso selection;
  - a history *timeline*;
  - a block palette picker;
  - "capabilities" toggles such as infinite reach and replace;
  - polished, animated editor panels.
- **Litematica:**
  - Colour-coded ghost overlay: missing, wrong block, wrong state and extra blocks each get their own colour.
  - Layer limiting (render only N layers), material list with available vs needed counts.
  - Easy Place, area selection with a tool item, scrolling to grow or move corners.
  - **Every action is a rebindable malilib hotkey.**
- **WorldEdit + CUI:**
  - Wand: LMB sets pos1, RMB sets pos2. The CUI draws a grid outline with the size shown.
  - Operations: set, replace, walls, outline, hollow, stack, move, copy/paste with rotate and flip.
  - Masks, bounded per-player undo/redo history, selection shapes (cuboid, poly, ellipsoid, cylinder).
- **Create schematics:**
  - Schematic & Quill: click two corners, **Ctrl+scroll pushes or pulls the face you're looking at**, then name and save.
  - Placement tool modes: move, rotate, flip, deploy, print (creative).
  - The Schematicannon prints survival builds from connected inventories and uses fuel. Replace policies: never, solid only, any, including empty. It hands out a material checklist.
- **FramedBlocks:** camouflaged frames with about 200 shapes. The Framing Saw converts frames between shapes at a **material cost**, which is a model for charging shape swaps. The Blueprint copies frame plus camo and consumes items in survival.
- **Copycats+ and Create copycats:** apply any full block as a skin, cycle orientation by clicking again, reset with a wrench. Useful shape reference: panel, step, slopes, vertical step, layers, beams.
- **Macaw's:** shapes that auto-connect (roofs, fences), shown by catalogue breadth.

### Checklist for a polished, survival-friendly building mod
1. Two-click area selection. After the first click the box follows the crosshair; the second click commits. Right-click on air or Esc clears it.
2. Ctrl+scroll pushes or pulls the face you're looking at; arrow keys nudge; a key recentres the anchor.
3. Size shown over the box in the world (W×H×L), plus block count and materials needed vs owned.
4. Ghost preview uses the same translucent style as block placement, with configurable opacity and saturation.
5. Colour-coded preview states:
   - valid;
   - missing items (red tint);
   - protected or denied (red outline);
   - will replace (amber);
   - will break (red cross).

   Use a colour-blind-safe palette.
6. Previews stay cheap:
   - cache vertex buffers;
   - recompute only when the anchor or target changes;
   - above a configurable cap (e.g. 4k blocks), show only the outline;
   - generate large shapes asynchronously.
7. Reach: `Player#blockInteractionRange()` plus a tier bonus. "Air placement" at a scroll-adjustable distance when no block is hit (Angel core / Effortless style).
8. Axis and plane locks (row, column, plane) plus "build to me".
9. Shapes: line, wall, floor, box (filled/hollow/outline), cylinder, sphere/dome, circle, diagonal, slope/stairs, pyramid.
10. Area operations: fill, replace (filter Exact/Similar/Any), hollow, walls, overlay on the top surface, clear, and exchange that keeps state properties.
11. "Extend face" (Construction core) as a quick one-click mode.
12. Stackable modifiers: mirror X/Y/Z with a block or half-block plane, array (offset × count), radial (N slices). Draw the planes and centres in the world, and mirror or rotate stair and vertical-stair orientations correctly (`BlockState#rotate(Rotation)` / `#mirror(Mirror)`).
13. Copy / cut / paste with rotate, flip and move-anchor, shown as a ghost first. The clipboard persists, and saved blueprint items can be shared.
14. Random-palette placement with weights. A whole chisel or shape wheel can be the palette.
15. Undo/redo on the server, per player and bounded by operation count and block count. Buttons in the R menu plus keybinds, and a highlight of the last operation.
16. Undo restores only positions that still match the post-operation state. It refunds items to the inventory (overflow drops at the player's feet); redo charges again.
17. Survival cost: pull from the hotbar, inventory, offhand, shulkers and bundles, toolbox storage upgrades, and optionally linked containers (BG2-style bind).
18. When materials run short, show a clear "missing X ×N" message and a choice between "place what I can" and "all or nothing".
19. Tool durability or energy cost per block, with maximum blocks per operation, reach, and unlocked modes all tied to toolbox tier and upgrades.
20. Survival breaking respects harvest level:
    - never touch unbreakable blocks (hardness < 0);
    - apply the tool's silk touch and fortune;
    - drops go to the inventory.
21. Spread large operations across ticks (BG2 formula), with a global per-tick budget. Show a HUD progress bar and a cancel option; stop and settle refunds on logout.
22. Placement animation (grow or fade in) with sound, honouring `Theme.motion()`.
23. Protection:
    - `Level#mayInteract(Player, BlockPos)`, `Player#mayBuild()` and `Player#mayUseItemAt(BlockPos, Direction, ItemStack)`;
    - `Level#isInWorldBounds(BlockPos)` and `WorldBorder#isWithinBounds(BlockPos)`;
    - fire the loader's place/break events through a platform hook, so Open Parties and Claims (in DF) can deny.
    - Denied cells turn red in the preview and are skipped.
24. Never load or generate chunks. Refuse or skip positions in unloaded chunks.
25. Server authority: the client sends only mode, anchors and parameters. The server recomputes positions and validates distance, tier, limits and materials. Rate-limit per player, and never accept block lists from the client (especially for paste).
26. Block entities: never copy their contents in survival. Exclude block entities from cut/move unless explicitly supported. Keep a `#<ns>:no_build` tag blacklist that by default covers C&B, spawners, portals, bedrock, command and structure blocks.
27. Multi-part and multi-count states: doors, beds, tall plants, double slabs, candles, sea pickles, snow layers, turtle eggs. Cost equals the real item count, and placement goes through `BlockItem#place(BlockPlaceContext)` so both halves are handled.
28. Fluids: a replace toggle for fluids, grass and snow. No free water through waterlogging; decide the waterlogged property explicitly.
29. Update flags: `Level#setBlock(BlockPos, BlockState, int)` with `Block.UPDATE_ALL` for normal placement. Suppress drops when clearing (`UPDATE_SUPPRESS_DROPS`), and settle gravity blocks and fluids afterwards.
30. Radial wheels:
    - hold to open, release to select;
    - 8–10 slices per ring, configurable;
    - centre is the full block;
    - LMB/RMB switch rings; scroll steps through slices; number keys pick directly;
    - animated open and hover, labels and key hints;
    - recentre the cursor on open; never open over a screen.
31. Every mode and modifier gets a keybind, unbound by default. Conflicts show on Slate Config's Controls page.
32. Discoverability:
    - tooltips with key hints;
    - a first-use toast or short tutorial;
    - JEI info pages (JEI is in the DF pack);
    - action-bar feedback, e.g. "Filled 1,234 — Undo".
33. "Why not?" explanations: protected, missing items, over the tier limit, tool too weak, unloaded chunk.
34. Settings live on the tool (data components) or in client config. The server syncs its limits so the preview matches what the server will do.
35. Creative bypasses cost and limits (configurable). Adventure mode means `mayBuild()` returns false; spectator is disabled.
36. Server admins: per-mode permission level (e.g. paste OP-only), a log of large operations, and global maximums.
37. The chisel/variant swap keeps the held shape and all shared properties, both for the item in hand and for the looked-at block in the world.
38. Shape-swap cost follows the Rechiseled worth / FramedBlocks saw idea: a slab is worth 0.5, and double slab ↔ full block is valid.

### DF-pack interactions to handle
- `open-parties-and-claims`: claims.
- `BridgingMod` and `Accurateblockplacement`: they have their own placement previews and keys, so avoid double previews.
- `kleeslabs`: breaks half of a double slab, which also applies to our vertical slabs.
- `DiagonalFences` / `DiagonalWalls`: our fences and walls should extend vanilla `FenceBlock`/`WallBlock` so they get diagonal connections.
- `ShoulderSurfing`: take the target from `Minecraft.hitResult`, not our own raycast.
- `Ping-Wheel`: another wheel UI, watch for key clashes.
- `carryon`, `sable`, `create-aeronautics`, `immersive_portals`: skip positions on moving structures and across portals.
- `betterinventory`: its Alt function clashes with the swap wheel.

### Pitfalls
- **Dupes:**
  - undo refunds after the world changed;
  - 1:N chisel links;
  - copying block-entity contents or item components;
  - cost counted per block instead of per item (doors, beds, double slabs);
  - supports removed by a fill so torches drop *and* get refunded;
  - undoing a break after the drops were already collected: take the drops back or disallow;
  - in-world swaps that bypass loot tables.
- **Claims:** any path that calls `setBlock` without the checks and events above bypasses them.
- **Lag:**
  - operations done in one tick;
  - neighbour-update cascades from sand and water;
  - lighting storms;
  - loading chunks;
  - huge preview meshes rebuilt every frame.
- **Authority and desync:** client-side predicted placement needs a server-driven rollback. Group data has to be resynced after `/reload`.

Sources:
- [Rechiseled (GitHub)](https://github.com/SuperMartijn642/Rechiseled), [Rechiseled (CurseForge)](https://www.curseforge.com/minecraft/mc-mods/rechiseled/files/7589670)
- [Chipped (GitHub)](https://github.com/terrarium-earth/Chipped), [Chipped (CurseForge)](https://www.curseforge.com/minecraft/mc-mods/chipped)
- [Chisel Modern (CurseForge)](https://www.curseforge.com/minecraft/mc-mods/chisel-modern), [Chisel Modern (GitHub)](https://github.com/Leclowndu93150/Chisel-Modern)
- [Chisel Reborn (GitHub)](https://github.com/matthewperiut/Chisel-Reborn), [Chisel Reborn (Modrinth)](https://modrinth.com/mod/chisel-reborn)
- [Chisels & Bits](https://www.curseforge.com/minecraft/mc-mods/chisels-bits/files/7405270)
- [FramedBlocks](https://www.curseforge.com/minecraft/mc-mods/framedblocks)
- [Copycats+](https://modrinth.com/mod/copycats/version/3.0.1+mc.1.21.1-neoforge)
- [Create Fabric 1.21.1 fork](https://github.com/netherg-io/create-fabric)
- [Macaw's Fences and Walls](https://www.curseforge.com/minecraft/mc-mods/macaws-fences-and-walls)
- [Additional Placements](https://www.curseforge.com/minecraft/mc-mods/additional-placements)
- [Effortless Building](https://www.curseforge.com/minecraft/mc-mods/effortless-building)
- [Construction Wand README (1.21)](https://github.com/Theta-Dev/ConstructionWand/blob/1.21/README.md)
- [Building Gadgets 2](https://github.com/Direwolf20-MC/BuildingGadgets2), [Building Gadgets (CurseForge)](https://www.curseforge.com/minecraft/mc-mods/building-gadgets/files/5442392)