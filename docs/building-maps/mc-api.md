# MC 1.21.1 / NeoForge 21.1.247 / Fabric API 0.116.13 signature check for a Slate building module (from javap)

## How this was checked

- **Jars**
  - Fabric side: `fabric/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-48f5f74c97/1.21.1-loom.mappings.1_21_1.layered+hash.2198-v2/*.jar`. This is Mojmap with Fabric API interface injection and transitive access wideners applied.
  - NeoForge side: the patched MC+NF jar `neoforge/core/build/moddev/artifacts/neoforge-21.1.247.jar`.
  - Fabric API: the remapped jars under `fabric/.gradle/loom-cache/remapped_mods/remapped/net/fabricmc/fabric-api/`.
- **Build setup**
  - NeoForge uses ModDevGradle 2.0.141 and `neoforge_version=21.1.247`.
  - Fabric uses loom 1.17.14, `officialMojangMappings()`, full `fabric-api:0.116.13+1.21.1` and loader 0.19.3.
- **Compile rule:** Slate has no separate common Gradle project. `common/<module>/src/main` is compiled twice, once against each loader's jar. Common code may only use signatures that are public in both jars. Every vanilla class below was diffed between the two jars:
  - `NF+` means the line exists only on NeoForge.
  - `NF-` means the line is missing or different on NeoForge.
- **Loom-jar injections:** the Loom jar injects `FabricBlock`, `FabricBakedModel`, `FabricIngredient`, `FabricBlockView`, `RenderDataBlockEntity`, `AttachmentTarget` and `FabricBakedModelManager` into vanilla types. None of these can be used from common code.
- **Existing Slate code:** Slate has no block or item registration abstraction anywhere (no `DeferredRegister` or `Registry.register` of blocks or items). Networking already exists in `common/core/src/main/java/dev/fallingcloud/slate/core/net/SlateNetwork.java`:
  - `sendToServer(CustomPacketPayload)`
  - `sendToPlayer(ServerPlayer, CustomPacketPayload)`
  - `canSendToPlayer(ServerPlayer, CustomPacketPayload.Type<?>)`
  - `serverHasChannel(CustomPacketPayload.Type<?>)`
  - Handlers implement `PayloadHandler<T>.handle(T payload, NetContext context)`.

Legend: `nm.` = `net.minecraft.`, `nf.` = `net.neoforged.neoforge.`, `fapi.` = `net.fabricmc.fabric.api.`, `cmb.` = `com.mojang.blaze3d.`.

---

## VANILLA

### nm.data.BlockFamilies / BlockFamily
- These classes are present in the runtime jars. I checked the obfuscated vanilla `minecraft-client.jar` and the server jar: `BlockFamilies`=`lw`, `BlockFamily`=`lx`, `BlockFamily$Variant`=`lx$b`, `RecipeProvider`=`oo` are all in both. It is not datagen-only.
- `BlockFamilies` has 67 `public static final BlockFamily` fields.
```
public static nm.data.BlockFamily$Builder familyBuilder(nm.world.level.block.Block);
public static java.util.stream.Stream<nm.data.BlockFamily> getAllFamilies();
// BlockFamily (ctor is package-private)
public nm.world.level.block.Block getBaseBlock();
public java.util.Map<nm.data.BlockFamily$Variant, nm.world.level.block.Block> getVariants();
public nm.world.level.block.Block get(nm.data.BlockFamily$Variant);
public boolean shouldGenerateModel(); public boolean shouldGenerateRecipe();
public java.util.Optional<java.lang.String> getRecipeGroupPrefix(); getRecipeUnlockedBy();
```
- `Variant` values: BUTTON, CHISELED, CRACKED, CUT, DOOR, CUSTOM_FENCE, FENCE, CUSTOM_FENCE_GATE, FENCE_GATE, MOSAIC, SIGN, SLAB, STAIRS, PRESSURE_PLATE, POLISHED, TRAPDOOR, WALL, WALL_SIGN. It also has `public String getRecipeGroup()`.
- `BlockFamily$Builder(Block)` is public, with setters `button/chiseled/mosaic/cracked/cut/door/customFence/fence/customFenceGate/fenceGate/sign(Block,Block)/slab/stairs/pressurePlate/polished/trapdoor/wall`, plus `dontGenerateModel()` and `getFamily()`.

### StairBlock (extends Block implements SimpleWaterloggedBlock)
```
public static final DirectionProperty FACING;
public static final EnumProperty<nm.world.level.block.state.properties.Half> HALF;
public static final EnumProperty<nm.world.level.block.state.properties.StairsShape> SHAPE;
public static final BooleanProperty WATERLOGGED;
protected final nm.world.level.block.state.BlockState baseState;
public nm.world.level.block.StairBlock(nm.world.level.block.state.BlockState, nm.world.level.block.state.BlockBehaviour$Properties);
public BlockState getStateForPlacement(nm.world.item.context.BlockPlaceContext);
protected BlockState updateShape(BlockState, Direction, BlockState, LevelAccessor, BlockPos, BlockPos);
protected VoxelShape getShape(BlockState, BlockGetter, BlockPos, CollisionContext);
public static boolean isStairs(BlockState);
protected BlockState rotate(BlockState, Rotation); protected BlockState mirror(BlockState, Mirror);
protected void createBlockStateDefinition(StateDefinition$Builder<Block, BlockState>);
```
- Also has protected static `VoxelShape TOP_AABB, BOTTOM_AABB, OCTET_NNN..OCTET_PPP, TOP_SHAPES[], BOTTOM_SHAPES[]`.

### SlabBlock
```
public static final EnumProperty<SlabType> TYPE; public static final BooleanProperty WATERLOGGED;
protected static final VoxelShape BOTTOM_AABB, TOP_AABB;
public nm.world.level.block.SlabBlock(BlockBehaviour$Properties);
public BlockState getStateForPlacement(BlockPlaceContext);
protected boolean canBeReplaced(BlockState, BlockPlaceContext);
protected VoxelShape getShape(BlockState, BlockGetter, BlockPos, CollisionContext);
protected BlockState updateShape(BlockState, Direction, BlockState, LevelAccessor, BlockPos, BlockPos);
```

### WallBlock
```
public static final BooleanProperty UP;
public static final EnumProperty<WallSide> EAST_WALL, NORTH_WALL, SOUTH_WALL, WEST_WALL;
public static final BooleanProperty WATERLOGGED;
public nm.world.level.block.WallBlock(BlockBehaviour$Properties);
public BlockState getStateForPlacement(BlockPlaceContext);
protected BlockState updateShape(BlockState, Direction, BlockState, LevelAccessor, BlockPos, BlockPos);
private boolean connectsTo(BlockState, boolean, Direction);   // private
private boolean shouldRaisePost(BlockState, BlockState, VoxelShape); // private
```

### FenceBlock (extends CrossCollisionBlock)
```
public nm.world.level.block.FenceBlock(BlockBehaviour$Properties);
public boolean connectsTo(BlockState, boolean, Direction);
private boolean isSameFence(BlockState);   // private
protected ItemInteractionResult useItemOn(ItemStack, BlockState, Level, BlockPos, Player, InteractionHand, BlockHitResult);
protected InteractionResult useWithoutItem(BlockState, Level, BlockPos, Player, BlockHitResult);
public BlockState getStateForPlacement(BlockPlaceContext);
```

### CrossCollisionBlock
```
public static final BooleanProperty NORTH, EAST, SOUTH, WEST, WATERLOGGED;
protected static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION;
protected nm.world.level.block.CrossCollisionBlock(float, float, float, float, float, BlockBehaviour$Properties);
```

### FenceGateBlock (extends HorizontalDirectionalBlock)
```
public static final BooleanProperty OPEN, POWERED, IN_WALL;
public nm.world.level.block.FenceGateBlock(nm.world.level.block.state.properties.WoodType, BlockBehaviour$Properties);
public static boolean connectsToDirection(BlockState, Direction);
```

### IronBarsBlock (extends CrossCollisionBlock; this is the pane class)
```
public nm.world.level.block.IronBarsBlock(BlockBehaviour$Properties);
public final boolean attachsTo(BlockState, boolean);
```

### BlockItem
```
public nm.world.item.BlockItem(Block, nm.world.item.Item$Properties);
public InteractionResult useOn(UseOnContext);
public InteractionResult place(BlockPlaceContext);
public BlockPlaceContext updatePlacementContext(BlockPlaceContext);
protected BlockState getPlacementState(BlockPlaceContext);
protected boolean canPlace(BlockPlaceContext, BlockState);
protected boolean placeBlock(BlockPlaceContext, BlockState);
protected boolean mustSurvive();
protected SoundEvent getPlaceSound(BlockState);
public Block getBlock();
public void registerBlocks(Map<Block, Item>, Item);
NF+ protected SoundEvent getPlaceSound(BlockState, Level, BlockPos, Player);
NF+ public void removeFromBlockToItemMap(Map<Block, Item>, Item);
```

### BlockPlaceContext / UseOnContext / DirectionalPlaceContext
- These are the same on both loaders.
```
public BlockPlaceContext(Player, InteractionHand, ItemStack, BlockHitResult);
public BlockPlaceContext(UseOnContext);
public BlockPlaceContext(Level, Player, InteractionHand, ItemStack, BlockHitResult);
public static BlockPlaceContext at(BlockPlaceContext, BlockPos, Direction);
public BlockPos getClickedPos(); public boolean canPlace(); public boolean replacingClickedOnBlock();
public Direction getNearestLookingDirection(); getNearestLookingVerticalDirection(); public Direction[] getNearestLookingDirections();
public UseOnContext(Player, InteractionHand, BlockHitResult);
public UseOnContext(Level, Player, InteractionHand, ItemStack, BlockHitResult);
protected final BlockHitResult getHitResult();
getClickedPos() getClickedFace() getClickLocation():Vec3 isInside() getItemInHand() getPlayer() getHand() getLevel() getHorizontalDirection() isSecondaryUseActive() getRotation():float
public DirectionalPlaceContext(Level, BlockPos, Direction, ItemStack, Direction);
```

### Block / BlockBehaviour: drops, clone and destroy progress
```
// Block
public static List<ItemStack> getDrops(BlockState, ServerLevel, BlockPos, BlockEntity);
public static List<ItemStack> getDrops(BlockState, ServerLevel, BlockPos, BlockEntity, Entity, ItemStack);
public static void dropResources(BlockState, Level, BlockPos);
public static void dropResources(BlockState, LevelAccessor, BlockPos, BlockEntity);
public static void dropResources(BlockState, Level, BlockPos, BlockEntity, Entity, ItemStack);
public static void popResource(Level, BlockPos, ItemStack);
public ItemStack getCloneItemStack(nm.world.level.LevelReader, BlockPos, BlockState);
public BlockState playerWillDestroy(Level, BlockPos, BlockState, Player);
public void playerDestroy(Level, Player, BlockPos, BlockState, BlockEntity, ItemStack);
public void setPlacedBy(Level, BlockPos, BlockState, LivingEntity, ItemStack);
public static Block byItem(Item); public Item asItem();
public static BlockState updateFromNeighbourShapes(BlockState, LevelAccessor, BlockPos);
public static final int UPDATE_NEIGHBORS, UPDATE_CLIENTS, UPDATE_INVISIBLE, UPDATE_KNOWN_SHAPE, UPDATE_SUPPRESS_DROPS, UPDATE_MOVE_BY_PISTON, UPDATE_NONE, UPDATE_ALL, UPDATE_ALL_IMMEDIATE;
NF+ public void popExperience(ServerLevel, BlockPos, int);   // vanilla/Fabric: protected
// BlockBehaviour
protected List<ItemStack> getDrops(BlockState, nm.world.level.storage.loot.LootParams$Builder);
protected float getDestroyProgress(BlockState, Player, BlockGetter, BlockPos);
public final ResourceKey<LootTable> getLootTable();
protected boolean canBeReplaced(BlockState, BlockPlaceContext);
protected SoundType getSoundType(BlockState);
NF+ protected boolean isAir(BlockState);
// BlockBehaviour$BlockStateBase (public)
public float getDestroyProgress(Player, BlockGetter, BlockPos);
public List<ItemStack> getDrops(LootParams$Builder);
public boolean canBeReplaced(BlockPlaceContext);
public SoundType getSoundType(); public int getLightEmission();
public BlockState rotate(Rotation); public BlockState mirror(Mirror);
```
- **Loot table behaviour:** `getLootTable()` derives the key lazily as `blocks/<registry id>` when the `drops` field is null.
  - `Properties.ofFullCopy(base)` copies `drops`, which is usually null, so the variant gets its own table.
  - `Properties.dropsLike(Block)` sets `drops = block.getLootTable()`, so the variant uses the base block's loot table.
  - NF+ `Properties.lootFrom(Supplier<? extends Block>)`.

### Recipes
```
// RecipeManager extends SimpleJsonResourceReloadListener (no own prepare override)
public RecipeManager(HolderLookup$Provider);
protected void apply(Map<ResourceLocation, com.google.gson.JsonElement>, ResourceManager, ProfilerFiller);
public Optional<RecipeHolder<?>> byKey(ResourceLocation);
public Collection<RecipeHolder<?>> getRecipes(); getOrderedRecipes(); public Stream<ResourceLocation> getRecipeIds();
public <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<T>> getAllRecipesFor(RecipeType<T>);
public void replaceRecipes(Iterable<RecipeHolder<?>>);
protected static RecipeHolder<?> fromJson(ResourceLocation, JsonObject, HolderLookup$Provider);
private Multimap<RecipeType<?>, RecipeHolder<?>> byType; private Map<ResourceLocation, RecipeHolder<?>> byName;
// SimpleJsonResourceReloadListener
protected Map<ResourceLocation, JsonElement> prepare(ResourceManager, ProfilerFiller);
NF+ protected ResourceLocation getPreparedPath(ResourceLocation);
// Ingredient (final)
public boolean test(ItemStack); public ItemStack[] getItems(); public boolean isEmpty();
public static Ingredient of(ItemLike...); of(ItemStack...); of(Stream<ItemStack>); of(TagKey<Item>); of();
private final Ingredient$Value[] values;
NF+ public Ingredient(nf.common.crafting.ICustomIngredient); getCustomIngredient(); isCustom(); isSimple(); hasNoItems(); getValues(); public static fromValues(Stream<? extends Ingredient$Value>)
```
- `Ingredient$ItemValue`, `Ingredient$TagValue` and `Ingredient$Value` are package-private in the Loom jar and public on NeoForge. Common code cannot name them.
- `RecipeHolder<T extends Recipe<?>>` is a record: `RecipeHolder(ResourceLocation, T)`, `id()`, `value()`.
```
// SingleItemRecipe (abstract; StonecutterRecipe extends it)
public SingleItemRecipe(RecipeType<?>, RecipeSerializer<?>, String, Ingredient, ItemStack);
protected final Ingredient ingredient; protected final ItemStack result; protected final String group;
public ItemStack getResultItem(HolderLookup$Provider); public NonNullList<Ingredient> getIngredients();
public StonecutterRecipe(String, Ingredient, ItemStack);
// ShapedRecipe
public ShapedRecipe(String, CraftingBookCategory, ShapedRecipePattern, ItemStack, boolean);
public ShapedRecipe(String, CraftingBookCategory, ShapedRecipePattern, ItemStack);
final ShapedRecipePattern pattern;   // NF: public final
public int getWidth(); getHeight();
public ShapedRecipePattern(int, int, NonNullList<Ingredient>, Optional<ShapedRecipePattern$Data>);
// ShapelessRecipe
public ShapelessRecipe(String, CraftingBookCategory, ItemStack, NonNullList<Ingredient>);
```
- **Bytecode check of the accessors:**
  - `ShapedRecipe.getIngredients()` returns the live `pattern.ingredients()` list.
  - `ShapelessRecipe.getIngredients()` returns the live `ingredients` field.
  - Both lists can therefore be changed in place with `set()`.
  - `SingleItemRecipe.getIngredients()` returns a new list, so the `ingredient` field must be changed directly.
  - Every `getResultItem()` returns the result stack itself, not a copy. The stack's Item cannot be changed, so replacing a result means building a new recipe.

### Creative tabs
- Vanilla has no event for adding to or removing from existing tabs. There is only `CreativeModeTab$DisplayItemsGenerator.accept(ItemDisplayParameters, Output)` for your own tab.
```
public static CreativeModeTab$Builder builder(CreativeModeTab$Row, int);   NF+ public static CreativeModeTab$Builder builder();
CreativeModeTabs: public static final ResourceKey<CreativeModeTab> BUILDING_BLOCKS, FUNCTIONAL_BLOCKS, TOOLS_AND_UTILITIES ...
Builder: title(Component) icon(Supplier<ItemStack>) displayItems(DisplayItemsGenerator) build()
NF+ withTabsBefore/withTabsAfter(ResourceKey<CreativeModeTab>... | ResourceLocation...), withSearchBar()
Output: accept(ItemStack, TabVisibility) accept(ItemStack) accept(ItemLike) acceptAll(Collection<ItemStack>)
```

### Data components and item properties
```
public static <T> nm.core.component.DataComponentType$Builder<T> builder();
Builder: persistent(Codec<T>) networkSynchronized(StreamCodec<? super RegistryFriendlyByteBuf, T>) cacheEncoding() build()
Item$Properties: public <T> Item$Properties component(DataComponentType<T>, T); stacksTo(int) durability(int) rarity(Rarity) fireResistant()
DataComponents: CONTAINER (ItemContainerContents), BUNDLE_CONTENTS, CUSTOM_DATA, BLOCK_STATE (BlockItemStateProperties), BLOCK_ENTITY_DATA
ItemContainerContents: static EMPTY; static fromItems(List<ItemStack>); copyInto(NonNullList<ItemStack>); stream(); nonEmptyItems()
ItemStack: <T> T set(DataComponentType<? super T>, T); <T> T update(DataComponentType<T>, T, UnaryOperator<T>); copyWithCount(int); transmuteCopy(ItemLike[, int]); static isSameItemSameComponents(ItemStack, ItemStack); shrink(int); hurtAndBreak(int, LivingEntity, EquipmentSlot)
Item: public static final Map<Block, Item> BY_BLOCK; static byBlock(Block); overrideStackedOnOther(ItemStack, Slot, ClickAction, Player); overrideOtherStackedOnMe(ItemStack, ItemStack, Slot, ClickAction, Player, SlotAccess); getTooltipImage(ItemStack); isBarVisible/getBarWidth/getBarColor(ItemStack)
```

### Models and quads (client)
```
// ItemOverrides
public ItemOverrides(ModelBaker, BlockModel, List<ItemOverride>);
private ItemOverrides();   // Fabric: private | NF+: protected ItemOverrides()
NF+ public ItemOverrides(ModelBaker, UnbakedModel, List<ItemOverride>, Function<Material, TextureAtlasSprite>);
public BakedModel resolve(BakedModel, ItemStack, nm.client.multiplayer.ClientLevel, LivingEntity, int);
// BakedModel
public abstract List<BakedQuad> getQuads(BlockState, Direction, RandomSource);
useAmbientOcclusion() isGui3d() usesBlockLight() isCustomRenderer() getParticleIcon() getOverrides()
getTransforms()   // abstract on Fabric, default on NF
// BakedQuad
public BakedQuad(int[], int, Direction, TextureAtlasSprite, boolean);
NF+ public BakedQuad(int[], int, Direction, TextureAtlasSprite, boolean, boolean); NF+ hasAmbientOcclusion()
protected final int[] vertices; protected final int tintIndex; protected final Direction direction; protected final TextureAtlasSprite sprite;
public TextureAtlasSprite getSprite(); public int[] getVertices(); public boolean isTinted(); public int getTintIndex(); public Direction getDirection(); public boolean isShade();
// FaceBakery
public BakedQuad bakeQuad(org.joml.Vector3f, org.joml.Vector3f, BlockElementFace, TextureAtlasSprite, Direction, ModelState, BlockElementRotation, boolean);
public static Direction calculateFacing(int[]); public static final int VERTEX_INT_SIZE, VERTEX_COUNT, UV_INDEX;
// ModelResourceLocation
public ModelResourceLocation(ResourceLocation, String); static inventory(ResourceLocation); static vanilla(String, String); id()
NF+ static standalone(ResourceLocation), STANDALONE_VARIANT
// ModelManager
getModel(ModelResourceLocation); getMissingModel(); getBlockModelShaper()
// BlockModelShaper
getBlockModel(BlockState); static stateToModelLocation(BlockState)
```

### Block rendering (client)
```
// BlockRenderDispatcher
public void renderSingleBlock(BlockState, cmb.vertex.PoseStack, nm.client.renderer.MultiBufferSource, int, int);
public void renderBatched(BlockState, BlockPos, BlockAndTintGetter, PoseStack, cmb.vertex.VertexConsumer, boolean, RandomSource);
public void renderBreakingTexture(BlockState, BlockPos, BlockAndTintGetter, PoseStack, VertexConsumer);
public BakedModel getBlockModel(BlockState); public ModelBlockRenderer getModelRenderer();
NF+ renderSingleBlock(BlockState, PoseStack, MultiBufferSource, int, int, ModelData, RenderType)
NF+ renderBatched(..., RandomSource, ModelData, RenderType)
// ModelBlockRenderer
public void renderModel(PoseStack$Pose, VertexConsumer, BlockState, BakedModel, float, float, float, int, int);
public void tesselateBlock(BlockAndTintGetter, BakedModel, BlockState, BlockPos, PoseStack, VertexConsumer, boolean, RandomSource, long, int);
NF+ renderModel(..., int, int, ModelData, RenderType); NF+ tesselateBlock/tesselateWithAO/tesselateWithoutAO(..., long, int, ModelData, RenderType)
```

### LevelRenderer (client)
```
private void renderHitOutline(PoseStack, VertexConsumer, Entity, double, double, double, BlockPos, BlockState);   // private on both
private static void renderShape(PoseStack, VertexConsumer, VoxelShape, double, double, double, float, float, float, float);   // private on both
public static void renderVoxelShape(PoseStack, VertexConsumer, VoxelShape, double, double, double, float, float, float, float, boolean);
public static void renderLineBox(PoseStack, VertexConsumer, AABB, float, float, float, float);
public static void renderLineBox(PoseStack, VertexConsumer, double, double, double, double, double, double, float, float, float, float);
public static void addChainedFilledBoxVertices(PoseStack, VertexConsumer, double, double, double, double, double, double, float, float, float, float);
public void renderLevel(nm.client.DeltaTracker, boolean, Camera, GameRenderer, LightTexture, Matrix4f, Matrix4f);
public void setBlocksDirty(int,int,int,int,int,int); public void setSectionDirty(int,int,int);
public static int getLightColor(BlockAndTintGetter, BlockPos);
NF+ public int getTicks(); public Frustum getFrustum(); iterateVisibleBlockEntities(Consumer<BlockEntity>)
```

### RenderType / RenderStateShard (client)
```
public static RenderType translucent(); solid(); cutout(); cutoutMipped(); translucentMovingBlock(); lines(); lineStrip(); debugFilledBox(); debugQuads(); entityTranslucent(ResourceLocation); itemEntityTranslucentCull(ResourceLocation)
public static RenderType$CompositeRenderType create(String, VertexFormat, VertexFormat$Mode, int, RenderType$CompositeState);            // public on BOTH
public static RenderType$CompositeRenderType create(String, VertexFormat, VertexFormat$Mode, int, boolean, boolean, RenderType$CompositeState);
RenderType$CompositeState: public static CompositeStateBuilder builder();   // public on BOTH
RenderStateShard (public static final on BOTH): TRANSLUCENT_TRANSPARENCY, POSITION_COLOR_SHADER, RENDERTYPE_TRANSLUCENT_SHADER, RENDERTYPE_LINES_SHADER, BLOCK_SHEET_MIPPED, NO_CULL, NO_DEPTH_TEST, LEQUAL_DEPTH_TEST, COLOR_WRITE, VIEW_OFFSET_Z_LAYERING, TRANSLUCENT_TARGET
Sheets: translucentCullBlockSheet(); translucentItemSheet(); solidBlockSheet(); cutoutBlockSheet()
Minecraft: public RenderBuffers renderBuffers();  RenderBuffers: bufferSource() : MultiBufferSource$BufferSource; outlineBufferSource()
ItemBlockRenderTypes: getChunkRenderType(BlockState); getRenderType(BlockState, boolean)   NF+ setRenderLayer(Block, RenderType|ChunkRenderTypeSet|Predicate), getRenderLayers(BlockState)
```
- `ItemBlockRenderTypes.setRenderLayer` does not exist on the Fabric side. Use `BlockRenderLayerMap` there.

### BlockEntity
```
public BlockEntity(BlockEntityType<?>, BlockPos, BlockState);
protected void loadAdditional(CompoundTag, HolderLookup$Provider);
protected void saveAdditional(CompoundTag, HolderLookup$Provider);
public CompoundTag getUpdateTag(HolderLookup$Provider);
public Packet<ClientGamePacketListener> getUpdatePacket();
public final CompoundTag saveWithoutMetadata(HolderLookup$Provider); saveWithFullMetadata(...); public final void loadWithComponents(CompoundTag, HolderLookup$Provider);
protected void applyImplicitComponents(BlockEntity$DataComponentInput); protected void collectImplicitComponents(DataComponentMap$Builder); public void removeComponentsFromTag(CompoundTag);
public void setChanged(); getBlockState(); setBlockState(BlockState)
NF+ getPersistentData(); setData/removeData/syncData(AttachmentType)   (NF IBlockEntityExtension: onDataPacket(Connection, ClientboundBlockEntityDataPacket, HolderLookup$Provider), handleUpdateTag(CompoundTag, HolderLookup$Provider), requestModelDataUpdate(), getModelData())
ClientboundBlockEntityDataPacket: public static create(BlockEntity); public static create(BlockEntity, BiFunction<BlockEntity, RegistryAccess, CompoundTag>); getPos(); getTag()
BlockEntityType: public static <T extends BlockEntity> BlockEntityType$Builder<T> of(BlockEntityType$BlockEntitySupplier<? extends T>, Block...); Builder.build(com.mojang.datafixers.types.Type<?>)
BlockEntityType$BlockEntitySupplier: public interface on BOTH — T create(BlockPos, BlockState)
```

### Menus and containers
```
public MenuType(MenuType$MenuSupplier<T>, FeatureFlagSet);   // MenuSupplier public on BOTH: T create(int, Inventory)
protected AbstractContainerMenu(MenuType<?>, int);
protected Slot addSlot(Slot); public abstract ItemStack quickMoveStack(Player, int); public abstract boolean stillValid(Player);
protected boolean moveItemStackTo(ItemStack, int, int, boolean); public void removed(Player); public boolean clickMenuButton(Player, int);
public void clicked(int, int, ClickType, Player); public final NonNullList<Slot> slots; public final int containerId;
public SimpleContainer(int); public SimpleContainer(ItemStack...); getItems(); fromTag(ListTag, HolderLookup$Provider); createTag(HolderLookup$Provider); addListener(ContainerListener)
public Slot(Container, int, int, int); mayPlace(ItemStack); getMaxStackSize(); mayPickup(Player); isActive()
```

### Player, abilities and game mode
```
Player: public Abilities getAbilities(); public boolean mayBuild(); public boolean mayUseItemAt(BlockPos, Direction, ItemStack); public double blockInteractionRange(); public boolean canInteractWithBlock(BlockPos, double); public Inventory getInventory(); public boolean hasCorrectToolForDrops(BlockState); public float getDestroySpeed(BlockState); isCreative(); isSpectator()
  NF+ hasCorrectToolForDrops(BlockState, Level, BlockPos); getDigSpeed(BlockState, BlockPos)
Abilities: public boolean invulnerable, flying, mayfly, instabuild, mayBuild;
Inventory: public final NonNullList<ItemStack> items; public int selected; getSelected(); findSlotMatchingItem(ItemStack); clearOrCountMatchingItems(Predicate<ItemStack>, int, Container); contains(ItemStack|TagKey<Item>|Predicate<ItemStack>); removeItem(int,int); add(ItemStack); placeItemBackInInventory(ItemStack); setPickedItem(ItemStack); getSuitableHotbarSlot(); getFreeSlot()
ServerPlayerGameMode: public boolean destroyBlock(BlockPos); public InteractionResult useItemOn(ServerPlayer, Level, ItemStack, InteractionHand, BlockHitResult); public InteractionResult useItem(ServerPlayer, Level, ItemStack, InteractionHand); isCreative(); getGameModeForPlayer()
MultiPlayerGameMode (client): destroyBlock(BlockPos); startDestroyBlock(BlockPos, Direction); useItemOn(LocalPlayer, InteractionHand, BlockHitResult); hasInfiniteItems(); getPlayerMode()
Level: public boolean mayInteract(Player, BlockPos); setBlock(BlockPos, BlockState, int); setBlock(BlockPos, BlockState, int, int); destroyBlock(BlockPos, boolean, Entity, int); removeBlock(BlockPos, boolean); setBlockAndUpdate; sendBlockUpdated(BlockPos, BlockState, BlockState, int); addDestroyBlockEffect(BlockPos, BlockState); playSound(Player, BlockPos, SoundEvent, SoundSource, float, float); isLoaded(BlockPos)
BlockHitResult: public BlockHitResult(Vec3, Direction, BlockPos, boolean); static miss(Vec3, Direction, BlockPos); withDirection(Direction); withPosition(BlockPos); getBlockPos(); getDirection(); isInside()
```

### Minecraft, input and HUD (client)
```
Minecraft: public HitResult hitResult; public Entity crosshairPickEntity; public LocalPlayer player; public ClientLevel level; public MultiPlayerGameMode gameMode; public Screen screen; public final MouseHandler mouseHandler; KeyboardHandler keyboardHandler; Options options; Gui gui; LevelRenderer levelRenderer; GameRenderer gameRenderer
  public DeltaTracker getTimer(); getBlockRenderer(); getItemRenderer(); getModelManager(); setScreen(Screen)
  private void startUseItem(); private boolean startAttack(); private void continueAttack(boolean); private void pickBlock(); private void handleKeybinds(); private int rightClickDelay;
  missTime: public on Fabric jar, protected on NF
MouseHandler: private void onPress(long, int, int, int); private void onScroll(long, double, double); private void onMove(long, double, double); public void handleAccumulatedMovement(); private void turnPlayer(double); isLeftPressed(); isRightPressed(); xpos(); ypos(); grabMouse(); releaseMouse(); isMouseGrabbed(); private double accumulatedScrollX, accumulatedScrollY;   NF+ getXVelocity()/getYVelocity()
KeyboardHandler: public void keyPress(long, int, int, int, int); private void charTyped(long, int, int);
KeyMapping: public KeyMapping(String, int, String); public KeyMapping(String, InputConstants$Type, int, String); isDown(); consumeClick(); setDown(boolean); getKey(); matches(int,int); matchesMouse(int)
  NF+ ctors with IKeyConflictContext / KeyModifier (see NeoForge)
Options: public final KeyMapping keyUse, keyAttack, keyPickItem, keySwapOffhand, keyInventory; KeyMapping[] keyHotbarSlots
InputConstants: public static boolean isKeyDown(long, int); KEY_LALT, KEY_RALT      Screen: static hasAltDown()/hasShiftDown()/hasControlDown()
Gui: public void render(GuiGraphics, DeltaTracker); private final nm.client.gui.LayeredDraw layers;   NF+ private final nf.client.gui.GuiLayerManager layerManager
LayeredDraw: add(LayeredDraw$Layer); add(LayeredDraw, BooleanSupplier); render(GuiGraphics, DeltaTracker)   LayeredDraw$Layer: void render(GuiGraphics, DeltaTracker)
DeltaTracker (interface): getGameTimeDeltaTicks(); getGameTimeDeltaPartialTick(boolean); getRealtimeDeltaTicks(); static ZERO, ONE
```
- For mixins, the input methods to target are `MouseHandler.onPress`, `MouseHandler.onScroll` and `KeyboardHandler.keyPress`.

### Other building helpers
```
Registry: static <V, T extends V> T register(Registry<V>, ResourceLocation, T); register(Registry<V>, ResourceKey<V>, T)
StructureTemplate(): fillFromWorld(Level, BlockPos, Vec3i, boolean, Block); placeInWorld(ServerLevelAccessor, BlockPos, BlockPos, StructurePlaceSettings, RandomSource, int); getSize(); getBoundingBox(StructurePlaceSettings, BlockPos)
StructurePlaceSettings(): setRotation(Rotation) setMirror(Mirror) setRotationPivot(BlockPos) setIgnoreEntities(boolean)
StateHolder: getValue(Property<T>); setValue(Property<T>, V); trySetValue(Property<T>, V); hasProperty; getProperties(); getValues()
LevelChunkSection: getStates(); maybeHas(Predicate<BlockState>); setBlockState(int,int,int,BlockState[,boolean]); hasOnlyAir()   ChunkAccess: getSections(); setUnsaved(boolean)
BlockBehaviour$Properties: static of(); static ofFullCopy(BlockBehaviour); static ofLegacyCopy(BlockBehaviour); dropsLike(Block); noLootTable(); noOcclusion(); dynamicShape(); sound(SoundType); strength(float[,float]); lightLevel(ToIntFunction<BlockState>); mapColor(...); requiresCorrectToolForDrops(); forceSolidOn(); pushReaction(PushReaction)
```

---

## NEOFORGE 21.1.247

### Mod bus vs game bus
- Events that implement `IModBusEvent` go on the mod bus: `ModelEvent.*`, `RegisterGuiLayersEvent`, `RegisterKeyMappingsEvent`, `RegisterMenuScreensEvent`, `RegisterEvent`, `BuildCreativeModeTabContentsEvent`, `RegisterClientExtensionsEvent`.
- Everything else here goes on the game bus.

### IBakedModelExtension (BakedModel extends it on NF)
```
default List<BakedQuad> getQuads(BlockState, Direction, RandomSource, nf.client.model.data.ModelData, RenderType);
default ModelData getModelData(BlockAndTintGetter, BlockPos, BlockState, ModelData);
default nf.client.ChunkRenderTypeSet getRenderTypes(BlockState, RandomSource, ModelData);
default List<RenderType> getRenderTypes(ItemStack, boolean);
default List<BakedModel> getRenderPasses(ItemStack, boolean);
default nf.common.util.TriState useAmbientOcclusion(BlockState, ModelData, RenderType);
default BakedModel applyTransform(ItemDisplayContext, PoseStack, boolean);
default TextureAtlasSprite getParticleIcon(ModelData);
```
- `nf.client.model.BakedModelWrapper<T extends BakedModel>` (abstract): `protected final T originalModel;` and `public BakedModelWrapper(T)`.
- `ChunkRenderTypeSet`: `static none() all() of(RenderType...) of(Collection) union(...)`, `contains(RenderType)`, `asList()`.

### ModelData / ModelProperty
```
ModelData (final): static EMPTY; static builder(); static <T> of(ModelProperty<T>, T); <T> T get(ModelProperty<T>); has(ModelProperty<?>); getProperties(); derive()
ModelData$Builder: <T> with(ModelProperty<T>, T); build()
ModelProperty<T> implements Predicate<T>: public ModelProperty(); public ModelProperty(Predicate<T>)
IBlockGetterExtension: default ModelData getModelData(BlockPos);  ILevelExtension: default ModelDataManager getModelDataManager()
```

### ModelEvent (mod bus)
```
ModifyBakingResult: Map<ModelResourceLocation, BakedModel> getModels(); Function<Material, TextureAtlasSprite> getTextureGetter(); ModelBakery getModelBakery()
RegisterAdditional: void register(ModelResourceLocation)      // use ModelResourceLocation.standalone(rl) (NF-only)
BakingCompleted: ModelManager getModelManager(); Map<ModelResourceLocation, BakedModel> getModels(); ModelBakery getModelBakery()
```

### RenderLevelStageEvent (game bus)
```
getStage(); getLevelRenderer(); getPoseStack(); getModelViewMatrix(); getProjectionMatrix(); getRenderTick():int; getPartialTick():DeltaTracker; getCamera(); getFrustum()
Stage: AFTER_SKY, AFTER_SOLID_BLOCKS, AFTER_CUTOUT_MIPPED_BLOCKS_BLOCKS, AFTER_CUTOUT_BLOCKS, AFTER_ENTITIES, AFTER_BLOCK_ENTITIES, AFTER_TRANSLUCENT_BLOCKS, AFTER_TRIPWIRE_BLOCKS, AFTER_PARTICLES, AFTER_WEATHER, AFTER_LEVEL; static fromRenderType(RenderType)
```
- The event has no buffer-source getter. Use `Minecraft.getInstance().renderBuffers().bufferSource()`.

### RenderHighlightEvent$Block (game bus, cancellable)
- This replaces the need to mixin the private `renderHitOutline`.
```
getTarget():BlockHitResult; getLevelRenderer(); getCamera(); getDeltaTracker(); getPoseStack(); getMultiBufferSource()
```

### InputEvent (game bus)
```
MouseScrollingEvent (cancellable; in-world only): (double scrollDeltaX, double scrollDeltaY, boolean left, boolean right, boolean middle, double mouseX, double mouseY) — getScrollDeltaX(); getScrollDeltaY(); isLeftDown(); isRightDown(); isMiddleDown(); getMouseX(); getMouseY()
MouseButton$Pre (cancellable): getButton(); getAction(); getModifiers()
InteractionKeyMappingTriggered (cancellable): (int, KeyMapping, InteractionHand) — isAttack(); isUseItem(); isPickBlock(); getHand(); getKeyMapping(); setSwingHand(boolean)
Key (NOT cancellable): getKey(); getScanCode(); getAction(); getModifiers()
```
- `ScreenEvent$MouseScrolled$Pre` and `$Post` also exist.

### GUI layers
```
RegisterGuiLayersEvent (mod bus): registerBelowAll/registerAboveAll(ResourceLocation, LayeredDraw$Layer); registerBelow/registerAbove(ResourceLocation other, ResourceLocation id, LayeredDraw$Layer); replaceLayer(ResourceLocation, Layer); wrapLayer(ResourceLocation, UnaryOperator<Layer>)
RenderGuiLayerEvent(.Pre cancellable / .Post): getGuiGraphics(); getPartialTick():DeltaTracker; getName():ResourceLocation; getLayer()
VanillaGuiLayers: CROSSHAIR, HOTBAR, SELECTED_ITEM_NAME, CHAT, DEBUG_OVERLAY, ...
```

### BlockEvent (game bus)
```
BlockEvent: getLevel():LevelAccessor; getPos(); getState()
EntityPlaceEvent (cancellable): public EntityPlaceEvent(nf.common.util.BlockSnapshot, BlockState placedAgainst, Entity); getEntity(); getBlockSnapshot(); getPlacedBlock(); getPlacedAgainst()
EntityMultiPlaceEvent: (List<BlockSnapshot>, BlockState, Entity); getReplacedBlockSnapshots()
BreakEvent (cancellable): public BreakEvent(Level, BlockPos, BlockState, Player); getPlayer()
BlockSnapshot: static create(ResourceKey<Level>, LevelAccessor, BlockPos[, int])
EventHooks: static boolean onBlockPlace(Entity, BlockSnapshot, Direction); static boolean onMultiBlockPlace(Entity, List<BlockSnapshot>, Direction)
CommonHooks: static BlockEvent$BreakEvent fireBlockBreak(Level, GameType, ServerPlayer, BlockPos, BlockState)
```
- These let area modes respect claim/protection mods.

### IBlockExtension (Block implements it on NF)
```
default SoundType getSoundType(BlockState, LevelReader, BlockPos, Entity);
default int getLightEmission(BlockState, BlockGetter, BlockPos);
default float getFriction(BlockState, LevelReader, BlockPos, Entity);
default ItemStack getCloneItemStack(BlockState, HitResult, LevelReader, BlockPos, Player);
default BlockState getAppearance(BlockState, BlockAndTintGetter, BlockPos, Direction, BlockState, BlockPos);
default boolean hidesNeighborFace(BlockGetter, BlockPos, BlockState, BlockState, Direction);
default boolean supportsExternalFaceHiding(BlockState);
default float getExplosionResistance(BlockState, BlockGetter, BlockPos, Explosion);
default boolean canHarvestBlock(BlockState, BlockGetter, BlockPos, Player);
default boolean onDestroyedByPlayer(BlockState, Level, BlockPos, Player, boolean, FluidState);
default MapColor getMapColor(BlockState, BlockGetter, BlockPos, MapColor);
default boolean isFlammable/int getFlammability(BlockState, BlockGetter, BlockPos, Direction);
default BlockState getToolModifiedState(BlockState, UseOnContext, nf.common.ItemAbility, boolean);
default boolean isLadder(BlockState, LevelReader, BlockPos, LivingEntity);
default void onBlockStateChange(LevelReader, BlockPos, BlockState, BlockState);
```
- `getDestroyProgress` is not in `IBlockExtension`.

### Client extensions
```
RegisterClientExtensionsEvent (mod bus): registerItem(IClientItemExtensions, Item...); registerBlock(IClientBlockExtensions, Block...)
IClientItemExtensions: static of(ItemStack|Item); default BlockEntityWithoutLevelRenderer getCustomRenderer(); getArmPose(...); applyForgeHandTransform(PoseStack, LocalPlayer, HumanoidArm, ItemStack, float, float, float); getFont(...)
IClientBlockExtensions: addHitEffects(BlockState, Level, HitResult, ParticleEngine); addDestroyEffects(BlockState, Level, BlockPos, ParticleEngine); playBreakSound(BlockState, Level, BlockPos)
```

### Registration
```
RegisterEvent (mod bus): <T> void register(ResourceKey<? extends Registry<T>>, ResourceLocation, Supplier<T>); <T> void register(ResourceKey<? extends Registry<T>>, Consumer<RegisterEvent$RegisterHelper<T>>); getRegistryKey()
  RegisterHelper<T>: register(ResourceLocation, T); default register(ResourceKey<T>, T)
DeferredRegister: static create(Registry<T>|ResourceKey<? extends Registry<T>>, String); static createItems(String); createBlocks(String); createDataComponents(String); register(String, Supplier<? extends I>); register(String, Function<ResourceLocation, ? extends I>); void register(IEventBus); addAlias(ResourceLocation, ResourceLocation); getEntries()
  $Blocks: registerBlock(String, Function<BlockBehaviour$Properties, ? extends B>, BlockBehaviour$Properties); registerSimpleBlock(String[, Properties])
  $Items: registerItem(String, Function<Item$Properties, ? extends I>[, Item$Properties]); registerSimpleBlockItem(String, Supplier<? extends Block>[, Item$Properties])
  $DataComponents: <D> registerComponentType(String, UnaryOperator<DataComponentType$Builder<D>>)
IRegistryExtension: addAlias(ResourceLocation, ResourceLocation)
```
- `MissingMappingsEvent` is not present. `IdMappingEvent` (`nf.registries.IdMappingEvent`) is present.

### BuildCreativeModeTabContentsEvent (mod bus; implements CreativeModeTab$Output)
```
getTab(); getTabKey(); getParameters(); getFlags(); hasPermissions(); getParentEntries()/getSearchEntries(): ObjectSortedSet<ItemStack>
accept(ItemStack, TabVisibility); insertAfter(ItemStack, ItemStack, TabVisibility); insertBefore(...); insertFirst(ItemStack, TabVisibility); remove(ItemStack, TabVisibility)
```

### Menus
```
IMenuTypeExtension: static <T extends AbstractContainerMenu> MenuType<T> create(nf.network.IContainerFactory<T>)
IContainerFactory<T> extends MenuType$MenuSupplier<T>: T create(int, Inventory, RegistryFriendlyByteBuf); default T create(int, Inventory)
IPlayerExtension: openMenu(MenuProvider, BlockPos); openMenu(MenuProvider, Consumer<RegistryFriendlyByteBuf>)
RegisterMenuScreensEvent (mod bus): <M, U extends Screen & MenuAccess<M>> void register(MenuType<? extends M>, MenuScreens$ScreenConstructor<M, U>)
```

### Loot, keys and ingredients
```
IGlobalLootModifier: ObjectArrayList<ItemStack> apply(ObjectArrayList<ItemStack>, LootContext); MapCodec<? extends IGlobalLootModifier> codec(); static LOOT_CONDITIONS_CODEC
LootModifier (abstract): protected LootModifier(LootItemCondition[]); protected abstract doApply(ObjectArrayList<ItemStack>, LootContext); protected static codecStart(RecordCodecBuilder$Instance<T>)
NeoForgeRegistries(.Keys).GLOBAL_LOOT_MODIFIER_SERIALIZERS, INGREDIENT_TYPES, ATTACHMENT_TYPES
KeyModifier (enum): CONTROL, SHIFT, ALT, NONE; static getActiveModifier(); isActive(IKeyConflictContext); matches(InputConstants$Key)
KeyConflictContext (enum implements IKeyConflictContext): UNIVERSAL, GUI, IN_GAME
KeyMapping NF+ ctors: (String, IKeyConflictContext, InputConstants$Type, int, String); (String, IKeyConflictContext, KeyModifier, InputConstants$Type, int, String); (String, IKeyConflictContext, InputConstants$Key, String); (String, IKeyConflictContext, KeyModifier, InputConstants$Key, String)
IKeyMappingExtension: setKeyConflictContext; getKeyModifier(); setKeyModifierAndCode(KeyModifier, InputConstants$Key); isActiveAndMatches(InputConstants$Key); isConflictContextAndModifierActive()
RegisterKeyMappingsEvent (mod bus): register(KeyMapping)
ICustomIngredient: boolean test(ItemStack); Stream<ItemStack> getItems(); boolean isSimple(); IngredientType<?> getType(); default Ingredient toVanilla()
IngredientType<T> (record): (MapCodec<T>) | (MapCodec<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>)
```

### Other NeoForge events a building mod will need
```
PlayerInteractEvent$RightClickBlock (cancellable): getHitVec(); setUseBlock/setUseItem(TriState); setCancellationResult(InteractionResult); getHand(); getItemStack(); getPos(); getFace(); getLevel(); getSide()
PlayerInteractEvent$LeftClickBlock (cancellable): getAction(); setUseBlock/setUseItem(TriState)
PlayerInteractEvent$RightClickItem (cancellable)
ClientTickEvent$Pre/$Post; PlayerTickEvent$Pre/$Post
AddReloadListenerEvent: addListener(PreparableReloadListener); getServerResources(); getRegistryAccess(); getConditionContext()
TagsUpdatedEvent; RecipesUpdatedEvent (client); OnDatapackSyncEvent
ChunkEvent$Load: getChunk():ChunkAccess; isNewChunk()
```
- Also present: `ModifyDefaultComponentsEvent`, `ItemTooltipEvent`, `RenderTooltipEvent$GatherComponents`, `RegisterColorHandlersEvent$Block/$Item`, `RegisterClientReloadListenersEvent`, `ClientPlayerNetworkEvent$LoggingIn/Out`, `PlayerEvent$BreakSpeed`, `RegisterPayloadHandlersEvent`.

---

## FABRIC API 0.116.13+1.21.1

The full umbrella jar is on the classpath, so every module listed exists. Module versions: renderer-api-v1 3.4.1, model-loading-api-v1 2.1.0, rendering-v1, loot-api-v3 1.0.3, events-interaction-v0 0.7.14, item-group-api-v1 4.1.7.

### Renderer API
```
FabricBakedModel (BakedModel extends it on Fabric):
  default boolean isVanillaAdapter();
  default void emitBlockQuads(BlockAndTintGetter, BlockState, BlockPos, Supplier<RandomSource>, fapi.renderer.v1.render.RenderContext);
  default void emitItemQuads(ItemStack, Supplier<RandomSource>, RenderContext);
RenderContext: QuadEmitter getEmitter(); pushTransform(RenderContext$QuadTransform); popTransform(); hasTransform(); isFaceCulled(Direction); itemTransformationMode(); RenderContext$BakedModelConsumer bakedModelConsumer(); default Consumer<Mesh> meshConsumer(); default Consumer<BakedModel> fallbackConsumer()
  $BakedModelConsumer: accept(BakedModel); accept(BakedModel, BlockState)      $QuadTransform: boolean transform(MutableQuadView)
QuadEmitter: pos(int, float, float, float); color(int, int); uv(int, float, float); spriteBake(TextureAtlasSprite, int); cullFace(Direction); nominalFace(Direction); material(RenderMaterial); colorIndex(int); tag(int); copyFrom(QuadView); fromVanilla(BakedQuad, RenderMaterial, Direction); fromVanilla(int[], int); square(Direction, float, float, float, float, float); emit()
QuadView: x/y/z(int); posByIndex(int,int); copyPos(int, Vector3f); u/v(int); color(int); colorIndex(); tag(); cullFace(); lightFace(); nominalFace(); toVanilla(int[], int)
RendererAccess: static INSTANCE; getRenderer(); hasRenderer()
Renderer: meshBuilder(); materialFinder(); materialById(ResourceLocation); registerMaterial(ResourceLocation, RenderMaterial)
MaterialFinder: blendMode(BlendMode); disableColorIndex(boolean); emissive(boolean); disableDiffuse(boolean); ambientOcclusion(fapi.util.TriState); glint(TriState); clear(); find()
BlendMode: DEFAULT, SOLID, CUTOUT_MIPPED, CUTOUT, TRANSLUCENT
ForwardingBakedModel (abstract, implements BakedModel, WrapperBakedModel): protected BakedModel wrapped; protected ForwardingBakedModel(BakedModel); public ForwardingBakedModel(); getWrappedModel()
```
- There is no `Renderer.get()` static in this version. Use `RendererAccess.INSTANCE.getRenderer()`.

### ModelLoadingPlugin (client)
```
static void register(ModelLoadingPlugin); void onInitializeModelLoader(ModelLoadingPlugin$Context)
Context: addModels(ResourceLocation...|Collection); registerBlockStateResolver(Block, BlockStateResolver); Event<ModelResolver> resolveModel(); Event<ModelModifier$OnLoad> modifyModelOnLoad(); Event<ModelModifier$BeforeBake> modifyModelBeforeBake(); Event<ModelModifier$AfterBake> modifyModelAfterBake()
ModelModifier$AfterBake: BakedModel modifyModelAfterBake(BakedModel, ModelModifier$AfterBake$Context)
  Context: resourceId():ResourceLocation; topLevelId():ModelResourceLocation; sourceModel(); textureGetter(); settings(); baker(); loader()
ModelModifier phases: OVERRIDE_PHASE, DEFAULT_PHASE, WRAP_PHASE, WRAP_LAST_PHASE
FabricBakedModelManager (injected into ModelManager): default BakedModel getModel(ResourceLocation)
```
- `PreparableModelLoadingPlugin` also exists.

### WorldRenderEvents (client)
```
START, AFTER_SETUP, BEFORE_ENTITIES, AFTER_ENTITIES, BEFORE_BLOCK_OUTLINE, BLOCK_OUTLINE, BEFORE_DEBUG_RENDER, AFTER_TRANSLUCENT, LAST, END
AfterTranslucent.afterTranslucent(WorldRenderContext); AfterEntities.afterEntities(ctx); Last.onLast(ctx); BeforeEntities.beforeEntities(ctx)
BeforeBlockOutline: boolean beforeBlockOutline(WorldRenderContext, HitResult)
BlockOutline: boolean onBlockOutline(WorldRenderContext, WorldRenderContext$BlockOutlineContext)  -> vertexConsumer(); entity(); cameraX/Y/Z(); blockPos(); blockState()
WorldRenderContext: worldRenderer(); matrixStack(); tickCounter():DeltaTracker; blockOutlines(); camera(); gameRenderer(); lightmapTextureManager(); projectionMatrix(); positionMatrix(); world(); profiler(); advancedTranslucency(); consumers():MultiBufferSource; frustum()
```
- **Nullability, from the javadoc in the source jar:**
  - `matrixStack()` is null before AFTER_ENTITIES.
  - `consumers()` is null before BEFORE_ENTITIES and after BEFORE_DEBUG_RENDER. It is therefore null in AFTER_TRANSLUCENT, so draw from your own buffer there, or draw in AFTER_ENTITIES or BEFORE_DEBUG_RENDER.
  - In AFTER_TRANSLUCENT the render matrix does not include the camera transform.

### Other Fabric events and helpers
```
HudRenderCallback.EVENT: void onHudRender(GuiGraphics, DeltaTracker)
ClientTickEvents: START_CLIENT_TICK/END_CLIENT_TICK (onStartTick/onEndTick(Minecraft)), START_WORLD_TICK, END_WORLD_TICK
ScreenEvents: BEFORE_INIT, AFTER_INIT (afterInit(Minecraft, Screen, int, int)); remove/beforeRender/afterRender/beforeTick/afterTick(Screen)
ScreenMouseEvents: allowMouseScroll(Screen) -> boolean allowMouseScroll(Screen, double, double, double, double); allowMouseClick(Screen) -> boolean allowMouseClick(Screen, double, double, int); before/after variants
PlayerBlockBreakEvents: BEFORE -> boolean beforeBlockBreak(Level, Player, BlockPos, BlockState, BlockEntity); AFTER -> void afterBlockBreak(...same); CANCELED -> onBlockBreakCanceled(...same)
UseBlockCallback.EVENT: InteractionResult interact(Player, Level, InteractionHand, BlockHitResult)
AttackBlockCallback.EVENT: InteractionResult interact(Player, Level, InteractionHand, BlockPos, Direction)
UseItemCallback.EVENT: InteractionResultHolder<ItemStack> interact(Player, Level, InteractionHand)
ClientPreAttackCallback.EVENT: boolean onClientPlayerPreAttack(Minecraft, LocalPlayer, int)
ClientPickBlockGatherCallback.EVENT: ItemStack pick(Player, HitResult); ClientPickBlockApplyCallback.EVENT: ItemStack pick(Player, HitResult, ItemStack)
BlockPickInteractionAware: ItemStack getPickedStack(BlockState, BlockGetter, BlockPos, Player, HitResult)
ItemGroupEvents: MODIFY_ENTRIES_ALL -> modifyEntries(CreativeModeTab, FabricItemGroupEntries); static modifyEntriesEvent(ResourceKey<CreativeModeTab>) -> modifyEntries(FabricItemGroupEntries)
  FabricItemGroupEntries: getDisplayStacks()/getSearchTabStacks(): List<ItemStack>; accept; prepend(...); addAfter(ItemLike|ItemStack|Predicate<ItemStack>, ...); addBefore(...)
  FabricItemGroup: static CreativeModeTab$Builder builder()
ExtendedScreenHandlerType<T, D> extends MenuType<T>: public ExtendedScreenHandlerType(ExtendedScreenHandlerType$ExtendedFactory<T, D>, StreamCodec<? super RegistryFriendlyByteBuf, D>); T create(int, Inventory, D); getPacketCodec()
  $ExtendedFactory: T create(int, Inventory, D)      ExtendedScreenHandlerFactory<D> extends MenuProvider: D getScreenOpeningData(ServerPlayer)
LootTableEvents (v3; v2 also present): REPLACE -> LootTable replaceLootTable(ResourceKey<LootTable>, LootTable, LootTableSource, HolderLookup$Provider); MODIFY -> void modifyLootTable(ResourceKey<LootTable>, LootTable$Builder, LootTableSource, HolderLookup$Provider); ALL_LOADED
FabricBlockView (injected into BlockGetter): default Object getBlockEntityRenderData(BlockPos); hasBiomes(); getBiomeFabric(BlockPos)
RenderDataBlockEntity (injected into BlockEntity): default Object getRenderData()
BuiltinItemRendererRegistry.INSTANCE: register(ItemLike, BuiltinItemRendererRegistry$DynamicItemRenderer); get(ItemLike)
  DynamicItemRenderer: void render(ItemStack, ItemDisplayContext, PoseStack, MultiBufferSource, int, int)
BlockRenderLayerMap.INSTANCE: putBlock(Block, RenderType); putBlocks(RenderType, Block...); putItem(Item, RenderType); putFluid(...)
KeyBindingHelper: static KeyMapping registerKeyBinding(KeyMapping); static InputConstants$Key getBoundKeyOf(KeyMapping)
ServerChunkEvents.CHUNK_LOAD: void onChunkLoad(ServerLevel, LevelChunk)
CustomIngredient: test(ItemStack); List<ItemStack> getMatchingStacks(); requiresTesting(); CustomIngredientSerializer<?> getSerializer(); default toVanilla()
```
- `ColorProviderRegistry` (rendering v1), `ItemTooltipCallback`, `DefaultItemComponentEvents`, `PayloadTypeRegistry` and `ServerPlayNetworking` are also present.

### Not in Fabric API 0.116.13+1.21.1 (needs a mixin or another route)
- **Mouse scroll or click outside a screen:** there is no event. Mixin the private `MouseHandler.onScroll(long,double,double)` and `onPress(long,int,int,int)`. Left click can also go through `ClientPreAttackCallback`.
- **Key press:** there is no key-press event. Mixin `KeyboardHandler.keyPress`, or poll `KeyMapping` or `InputConstants.isKeyDown(long,int)` in `END_CLIENT_TICK`.
- **Block place:** there is no place event (NF has `EntityPlaceEvent`). Use `UseBlockCallback` or a mixin on `BlockItem.place`.
- **Recipes:** there is no recipe-manager event. Mixin the protected `RecipeManager.apply(Map, ResourceManager, ProfilerFiller)`, or call `replaceRecipes(Iterable)`.
- **HUD layers:** there is no HUD layer registration (no `HudLayerRegistrationCallback` or `IdentifiedLayer`). Only `HudRenderCallback` exists.
- **Loot drops:** there is no drops event (no `LootTableEvents.MODIFY_DROPS`). Only REPLACE, MODIFY and ALL_LOADED exist.
- **Key modifiers:** there is no KeyModifier or KeyConflictContext equivalent.
- **Model data:** there is no ModelData equivalent. Use `RenderDataBlockEntity.getRenderData()` together with `FabricBlockView.getBlockEntityRenderData(BlockPos)`.
- **Hit outline:** `renderHitOutline` is private on both loaders. Use NF `RenderHighlightEvent.Block` and Fabric `WorldRenderEvents.BEFORE_BLOCK_OUTLINE`/`BLOCK_OUTLINE`.