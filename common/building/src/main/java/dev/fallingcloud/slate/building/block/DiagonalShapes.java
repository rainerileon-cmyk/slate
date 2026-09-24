package dev.fallingcloud.slate.building.block;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.variant.RotatedBox;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Diagonal connections for Slate Building's own fences, walls and panes, in step with DiagonalFences / DiagonalWalls /
 * DiagonalWindows (the DF pack). Those mods twin every vanilla-shaped block with a diagonal copy, which cannot work
 * for ours (the material lives in a block entity and the model is cut from it), so our blocks keep their own four
 * diagonal arms and follow the same rules: an arm towards a diagonal neighbour that takes one, never next to a
 * straight arm on either flank, never crossing a flanking neighbour's arm. With the matching mod absent no arm is
 * ever set, so without it everything stays as vanilla.
 *
 * <p>The arms are a 4-bit mask in the {@link ShapeBlockEntity} (one bit per {@link Diagonal}), not block state: with
 * the light and opacity properties a wall already has thousands of states, and four more booleans would multiply
 * that by sixteen. The mask is recomputed whenever a neighbour changes ({@code updateShape}, the block's own
 * {@code updateIndirectNeighbourShapes}), synced to clients with the entity, read by the collision shapes and by the
 * models (rotated arms cut from the material, {@link RotatedBox}). The library's twins read our blocks through
 * {@code block.diagonal.LibDiagonalBlocks} (their interface), and we read their arms through their properties.
 */
public final class DiagonalShapes {

    /** Which Diagonal mod switches each shape on. */
    public enum Kind {
        FENCE("diagonalfences"), WALL("diagonalwalls"), PANE("diagonalwindows");

        public final String modId;
        private Boolean enabled;

        Kind(final String modId) {
            this.modId = modId;
        }

        public boolean enabled() {
            if (enabled == null) enabled = SlatePlatform.get().isModLoaded(modId);
            return enabled;
        }
    }

    /** The four corners; {@code a} / {@code b} are the flanking cardinals, {@code yaw} turns a NORTH arm onto the diagonal. */
    public enum Diagonal {
        NORTH_EAST(1, -1, 45, Direction.NORTH, Direction.EAST),
        SOUTH_EAST(1, 1, 135, Direction.SOUTH, Direction.EAST),
        SOUTH_WEST(-1, 1, -135, Direction.SOUTH, Direction.WEST),
        NORTH_WEST(-1, -1, -45, Direction.NORTH, Direction.WEST);

        public final int dx, dz;
        public final float yaw;
        public final Direction a, b;

        Diagonal(final int dx, final int dz, final float yaw, final Direction a, final Direction b) {
            this.dx = dx;
            this.dz = dz;
            this.yaw = yaw;
            this.a = a;
            this.b = b;
        }

        public int bit() { return 1 << ordinal(); }

        public Diagonal opposite() {
            return switch (this) {
                case NORTH_EAST -> SOUTH_WEST;
                case SOUTH_EAST -> NORTH_WEST;
                case SOUTH_WEST -> NORTH_EAST;
                case NORTH_WEST -> SOUTH_EAST;
            };
        }

        /**
         * The arm of the flanking neighbour on {@code side} that would cross this one: our north-east arm crosses the
         * south-east arm of the block to the north and the north-west arm of the block to the east.
         */
        public Diagonal crossingAt(final Direction side) {
            final Direction na = side == a ? a.getOpposite() : a;
            final Direction nb = side == b ? b.getOpposite() : b;
            for (final Diagonal d : values()) if (d.a == na && d.b == nb) return d;
            return this;
        }
    }

    /** The library's own properties (its twins' arms), null without it. */
    private static final BooleanProperty @Nullable [] LIB_PROPS;
    private static final boolean LIB;

    static {
        BooleanProperty[] props = null;
        if (SlatePlatform.get().isModLoaded("diagonalblocks")) {
            try {
                final Class<?> api = Class.forName("fuzs.diagonalblocks.api.v2.DiagonalBlock");
                props = new BooleanProperty[Diagonal.values().length];
                for (final Diagonal d : Diagonal.values()) props[d.ordinal()] = (BooleanProperty) api.getField(d.name()).get(null);
            } catch (final Throwable t) {
                props = null;
                SlateBuilding.LOGGER.warn("[Slate Building] DiagonalBlocks is loaded but its API is not what this version expects; its twins and our shapes will not join diagonally: {}", t.toString());
            }
        }
        LIB_PROPS = props;
        LIB = props != null;
        if (LIB) SlateBuilding.LOGGER.info("[Slate Building] DiagonalBlocks found: fences, walls and panes take diagonal arms");
    }

    private DiagonalShapes() {}

    /** Whether the DiagonalBlocks library is loaded and its API matched: our blocks then implement its interface. */
    public static boolean libPresent() {
        return LIB;
    }

    /**
     * Whether a block standing at a diagonal takes an arm of {@code kind}, as the library's twins decide it: fences
     * join fences; walls and panes join each other as well as their own kind.
     */
    public static boolean attaches(final BlockState neighbour, final Kind kind) {
        final Block block = neighbour.getBlock();
        return switch (kind) {
            case FENCE -> block instanceof FenceBlock || neighbour.is(BlockTags.FENCES);
            case WALL, PANE -> block instanceof WallBlock || neighbour.is(BlockTags.WALLS) || block instanceof IronBarsBlock;
        };
    }

    /**
     * {@code properties} with a dynamic shape while {@code kind}'s mod is loaded: the collision shape then depends on
     * the arms at that position, which vanilla would otherwise compute once per state and cache.
     */
    public static BlockBehaviour.Properties properties(final BlockBehaviour.Properties properties, final Kind kind) {
        return kind.enabled() ? properties.dynamicShape() : properties;
    }

    /** Whether {@code state} has a straight arm towards {@code side}. */
    static boolean cardinal(final BlockState state, final Direction side, final Kind kind) {
        if (kind == Kind.WALL) {
            final var p = switch (side) {
                case NORTH -> WallBlock.NORTH_WALL;
                case EAST -> WallBlock.EAST_WALL;
                case SOUTH -> WallBlock.SOUTH_WALL;
                default -> WallBlock.WEST_WALL;
            };
            return state.hasProperty(p) && state.getValue(p) != WallSide.NONE;
        }
        final BooleanProperty p = PipeBlock.PROPERTY_BY_DIRECTION.get(side);
        return state.hasProperty(p) && state.getValue(p);
    }

    static @Nullable Kind kindOf(final Block block) {
        if (block instanceof ShapeFenceBlock) return Kind.FENCE;
        if (block instanceof ShapeWallBlock) return Kind.WALL;
        if (block instanceof ShapePaneBlock) return Kind.PANE;
        return null;
    }

    // ------------------------------------------------------------------ the mask

    /** The arms of the block at {@code pos}: ours from the block entity, a library twin's from its properties, else none. */
    public static int mask(final BlockGetter level, final BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        if (kindOf(state.getBlock()) != null) {
            return level.getBlockEntity(pos) instanceof ShapeBlockEntity be ? be.diagonals() : 0;
        }
        if (LIB_PROPS == null) return 0;
        int mask = 0;
        for (final Diagonal d : Diagonal.values()) {
            final BooleanProperty p = LIB_PROPS[d.ordinal()];
            if (state.hasProperty(p) && state.getValue(p)) mask |= d.bit();
        }
        return mask;
    }

    public static boolean has(final BlockGetter level, final BlockPos pos, final Diagonal d) {
        return (mask(level, pos) & d.bit()) != 0;
    }

    /** The arms the block at {@code pos} should have, given {@code state} (its straight arms) and its neighbours. */
    static int compute(final BlockState state, final LevelAccessor level, final BlockPos pos, final Kind kind) {
        if (!kind.enabled()) return 0;
        int mask = 0;
        for (final Diagonal d : Diagonal.values()) {
            if (cardinal(state, d.a, kind) || cardinal(state, d.b, kind)) continue;          // a straight arm takes the corner
            final BlockPos np = pos.offset(d.dx, 0, d.dz);
            final BlockState ns = level.getBlockState(np);
            if (!attaches(ns, kind)) continue;
            // A block that is not ours must already hold an arm towards us (a library twin sets its own by its rules and
            // then tells us): no arm to a block that gives none back. Between our own the rules are symmetric.
            if (kindOf(ns.getBlock()) == null && !has(level, np, d.opposite())) continue;
            // No crossing: neither flanking neighbour may reach across our corner.
            if (has(level, pos.relative(d.a), d.crossingAt(d.a)) || has(level, pos.relative(d.b), d.crossingAt(d.b))) continue;
            mask |= d.bit();
        }
        return mask;
    }

    /** Recomputes and stores the arms of the block at {@code pos} (a no-op without its block entity). */
    public static void refresh(final BlockState state, final LevelAccessor level, final BlockPos pos, final Kind kind) {
        if (level.getBlockEntity(pos) instanceof ShapeBlockEntity be) be.setDiagonals(compute(state, level, pos, kind));
    }

    /** After a change at {@code pos}: the block itself and the four diagonal neighbours that are ours recompute their arms. */
    public static void refreshAround(final BlockState state, final LevelAccessor level, final BlockPos pos, final Kind kind) {
        refresh(state, level, pos, kind);
        for (final Diagonal d : Diagonal.values()) {
            final BlockPos np = pos.offset(d.dx, 0, d.dz);
            final BlockState ns = level.getBlockState(np);
            final Kind nk = kindOf(ns.getBlock());
            if (nk != null) refresh(ns, level, np, nk);
        }
    }

    // ------------------------------------------------------------------ geometry

    /** The set arms of {@code mask} as rotated render boxes of {@code northArm} (block pixels). */
    public static List<RotatedBox> rotated(final int mask, final List<AABB> northArm) {
        if (mask == 0) return List.of();
        final List<RotatedBox> out = new ArrayList<>(4 * northArm.size());
        for (final Diagonal d : Diagonal.values()) {
            if ((mask & d.bit()) == 0) continue;
            for (final AABB b : northArm) out.add(new RotatedBox(b, d.yaw));
        }
        return out;
    }

    private record ArmKey(Diagonal d, int half, int y0, int y1) {}

    private static final Map<ArmKey, VoxelShape> ARMS = new ConcurrentHashMap<>();
    /** How far (block pixels from the centre) a diagonal arm runs: the block corner. */
    private static final double REACH = 8 * Math.sqrt(2);

    /** {@code base} plus the arms of {@code mask}: runs of boxes {@code half} px either side of the diagonal, from {@code y0} to {@code y1} (pixels). */
    public static VoxelShape withArms(final int mask, final VoxelShape base, final int half, final int y0, final int y1) {
        VoxelShape out = base;
        for (final Diagonal d : Diagonal.values()) if ((mask & d.bit()) != 0) out = Shapes.or(out, arm(d, half, y0, y1));
        return out;
    }

    private static VoxelShape arm(final Diagonal d, final int half, final int y0, final int y1) {
        return ARMS.computeIfAbsent(new ArmKey(d, half, y0, y1), k -> {
            VoxelShape s = Shapes.empty();
            final double step = Math.max(1, k.half());
            for (double t = 2; t <= REACH; t += step) {
                final double cx = 8 + t * k.d().dx / Math.sqrt(2), cz = 8 + t * k.d().dz / Math.sqrt(2);
                final double x0 = Math.max(0, cx - k.half()), x1 = Math.min(16, cx + k.half());
                final double z0 = Math.max(0, cz - k.half()), z1 = Math.min(16, cz + k.half());
                if (x1 - x0 < 0.5 || z1 - z0 < 0.5) continue;
                s = Shapes.or(s, Shapes.box(x0 / 16, k.y0() / 16.0, z0 / 16, x1 / 16, k.y1() / 16.0, z1 / 16));
            }
            return s.optimize();
        });
    }

    /** A per-block cache of shapes with arms, keyed by state and mask. */
    public static final class ShapeCache {
        private record Key(BlockState state, int mask) {}

        private final Map<Key, VoxelShape> shapes = new ConcurrentHashMap<>();
        private final int half, y0, y1;

        public ShapeCache(final int half, final int y0, final int y1) {
            this.half = half;
            this.y0 = y0;
            this.y1 = y1;
        }

        /** {@code base} with the arms of {@code mask}; {@code base} itself for an empty mask. */
        public VoxelShape get(final BlockState state, final int mask, final VoxelShape base) {
            if (mask == 0) return base;
            return shapes.computeIfAbsent(new Key(state, mask), k -> withArms(k.mask(), base, half, y0, y1));
        }
    }
}
