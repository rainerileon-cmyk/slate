package dev.fallingcloud.slate.building.client.model;

import dev.fallingcloud.slate.building.block.ShapeBlockEntity;
import dev.fallingcloud.slate.building.registry.BuildingComponents;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * Shared client helpers of the shape models: where a shape's material comes from (block entity, item component),
 * the material's model, the state an item shows, and the colour delegation to the material. Loader glue installs
 * the two things common code cannot know ({@link #install}).
 */
public final class ShapeModels {

    /** Material key of the "no material" look (the placeholder cube cut to the shape). */
    public static final Object UNSET = new Object() {
        @Override
        public String toString() {
            return "slate_building:unset";
        }
    };

    /** A material item's tint for one tint index (the loaders expose item colours differently). */
    @FunctionalInterface
    public interface ItemTint {
        int color(ItemStack stack, int tintIndex);
    }

    private static volatile Predicate<BlockState> translucent = s -> ItemBlockRenderTypes.getChunkRenderType(s) == RenderType.translucent();
    private static volatile ItemTint itemTint = (stack, tint) -> -1;
    private static final Map<Block, ItemStack> MATERIAL_STACKS = new ConcurrentHashMap<>();

    /**
     * Loader glue: how to tell a translucent material (NeoForge: from its model's render types; Fabric: from the
     * block render layer map) and how to read an item colour (NeoForge: {@code Minecraft.getItemColors()}; Fabric:
     * {@code ColorProviderRegistry.ITEM}).
     */
    public static void install(final Predicate<BlockState> isTranslucent, final ItemTint tint) {
        translucent = isTranslucent;
        itemTint = tint;
    }

    // ---- materials ----

    /**
     * The material stored at {@code pos}, or null when there is none / it is not a shape block. Never another shape
     * block (a corrupt save must not make a model recurse into itself).
     */
    public static @Nullable BlockState materialAt(final @Nullable BlockGetter level, final @Nullable BlockPos pos) {
        if (level == null || pos == null) return null;
        final BlockState material = level.getBlockEntity(pos) instanceof ShapeBlockEntity be ? be.material() : null;
        return usable(material) ? material : null;
    }

    /** The material block of a shape item stack (its {@code slate_building:material} component), or null. */
    public static @Nullable Block materialOf(final ItemStack stack) {
        final ResourceLocation id = stack.get(BuildingComponents.MATERIAL.get());
        if (id == null) return null;
        final Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        return block != null && usable(block.defaultBlockState()) ? block : null;
    }

    private static boolean usable(final @Nullable BlockState material) {
        return material != null && !material.isAir() && !(material.getBlock() instanceof ShapeBlock);
    }

    /**
     * The sides of the shape block at {@code pos} whose boundary faces a neighbour hides, as a bit mask (bit
     * {@code direction.ordinal()}): a full opaque neighbour, or one the material itself hides against (glass stairs
     * next to glass, like glass next to glass). The shape blocks do not occlude (their shape depends on the material
     * per position), so vanilla's own face culling keeps every face; this restores the culling for the faces that are
     * certainly hidden. Chunk meshing only (the neighbours are read from the render region).
     */
    public static int hiddenSides(final BlockGetter level, final BlockPos pos, final @Nullable BlockState material) {
        int mask = 0;
        final BlockPos.MutableBlockPos n = new BlockPos.MutableBlockPos();
        for (final Direction d : Direction.values()) {
            n.setWithOffset(pos, d);
            final BlockState neighbour = level.getBlockState(n);
            if (neighbour.isAir()) continue;
            if (neighbour.isSolidRender(level, n) || material != null && material.skipRendering(neighbour, d)
                || shapeHides(level, n, neighbour, d.getOpposite(), material)) mask |= 1 << d.ordinal();
        }
        return mask;
    }

    /**
     * Whether a neighbouring SHAPE block hides our face: it covers its whole face toward us, and its material is
     * opaque, or the same see-through material as ours (two glass shapes merge like two glass blocks).
     */
    private static boolean shapeHides(final BlockGetter level, final BlockPos n, final BlockState neighbour, final Direction towardUs,
                                      final @Nullable BlockState material) {
        if (!(neighbour.getBlock() instanceof ShapeBlock) || !ShapeQuadBaker.coversFace(neighbour, towardUs)) return false;
        final BlockState other = materialAt(level, n);
        if (other == null) return true;   // the opaque placeholder
        if (other.canOcclude() && !translucent(other)) return true;
        return material != null && other.getBlock() == material.getBlock();
    }

    /** Whether {@code side} is set in a {@link #hiddenSides} mask. */
    public static boolean hidden(final int mask, final @Nullable Direction side) {
        return side != null && (mask >> side.ordinal() & 1) != 0;
    }

    /** The baked model of a (material) block state. */
    public static BakedModel modelOf(final BlockState state) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
    }

    /** A fresh random seeded like the chunk renderer seeds a block at a position ({@code state.getSeed(pos)}). */
    public static RandomSource random(final long seed) {
        return RandomSource.create(seed);
    }

    /** Whether the material draws in the translucent layer (stained glass, ice, slime, ...). */
    public static boolean translucent(final BlockState material) {
        try {
            return translucent.test(material);
        } catch (final RuntimeException e) {
            return false;
        }
    }

    // ---- colours ----

    /** Block colour of a shape block: the material's colour at that position (grass, leaves, water-tinted blocks). */
    public static int blockColor(final BlockState state, final @Nullable BlockAndTintGetter level, final @Nullable BlockPos pos, final int tintIndex) {
        final BlockState material = materialAt(level, pos);
        if (material == null) return -1;
        return Minecraft.getInstance().getBlockColors().getColor(material, level, pos, tintIndex);
    }

    /** Item colour of a shape item: the material item's colour (the grass block item's green, ...). */
    public static int itemColor(final ItemStack stack, final int tintIndex) {
        final Block material = materialOf(stack);
        if (material == null || tintIndex < 0) return -1;
        final ItemStack materialStack = MATERIAL_STACKS.computeIfAbsent(material, ItemStack::new);
        if (materialStack.isEmpty()) return -1;
        try {
            return itemTint.color(materialStack, tintIndex);
        } catch (final RuntimeException e) {
            return -1;
        }
    }

    // ---- item display ----

    /**
     * The state a shape item shows: a representative, readable orientation per shape (stairs facing east like
     * vanilla's stair item, walls and fences as a straight run, gates closed, posts upright, ...). Properties are
     * looked up by name, so a shape without that property simply keeps its default.
     */
    public static BlockState displayState(final Block block) {
        final BlockState s = block.defaultBlockState();
        if (!(block instanceof ShapeBlock shape)) return s;
        return switch (shape.shape()) {
            case STAIRS -> with(s, "facing", "east", "half", "bottom", "shape", "straight", "waterlogged", "false");
            case SLAB -> with(s, "type", "bottom", "waterlogged", "false");
            case VERTICAL_SLAB -> with(s, "facing", "south", "type", "single", "waterlogged", "false");
            case VERTICAL_STAIRS -> with(s, "facing", "north", "waterlogged", "false");
            case WALL -> with(s, "up", "true", "north", "low", "south", "low", "east", "none", "west", "none", "waterlogged", "false");
            case FENCE -> with(s, "north", "true", "south", "true", "east", "false", "west", "false", "waterlogged", "false");
            case FENCE_GATE -> with(s, "facing", "south", "open", "false", "in_wall", "false");
            case STEP -> with(s, "facing", "south", "half", "bottom", "waterlogged", "false");
            case PANEL -> with(s, "facing", "up", "waterlogged", "false");
            case POST -> with(s, "axis", "y", "waterlogged", "false");
            case LAYER -> with(s, "layers", "2", "facing", "up", "waterlogged", "false");
            case PANE -> with(s, "east", "true", "west", "true", "north", "false", "south", "false", "waterlogged", "false");
            default -> with(s, "waterlogged", "false");
        };
    }

    /** {@code state} with the given name/value pairs applied where the property exists and accepts the value. */
    private static BlockState with(final BlockState state, final String... pairs) {
        BlockState out = state;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            final Property<?> p = out.getBlock().getStateDefinition().getProperty(pairs[i]);
            if (p != null) out = set(out, p, pairs[i + 1]);
        }
        return out;
    }

    private static <T extends Comparable<T>> BlockState set(final BlockState state, final Property<T> p, final String value) {
        return p.getValue(value).map(v -> state.setValue(p, v)).orElse(state);
    }

    /** Drops cached item stacks (resource reload). */
    static void clear() {
        MATERIAL_STACKS.clear();
    }

    private ShapeModels() {}
}
