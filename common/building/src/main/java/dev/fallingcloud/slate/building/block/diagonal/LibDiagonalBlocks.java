package dev.fallingcloud.slate.building.block.diagonal;

import dev.fallingcloud.slate.building.block.DiagonalShapes;
import dev.fallingcloud.slate.building.block.ShapeFenceBlock;
import dev.fallingcloud.slate.building.block.ShapePaneBlock;
import dev.fallingcloud.slate.building.block.ShapeWallBlock;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import fuzs.diagonalblocks.api.v2.DiagonalBlockType;
import fuzs.diagonalblocks.api.v2.DiagonalBlockTypes;
import fuzs.diagonalblocks.api.v2.EightWayDirection;
import fuzs.diagonalblocks.api.v2.impl.StarCollisionBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The DiagonalBlocks (DiagonalFences / Walls / Windows) bridge: with the library loaded, Slate Building registers
 * these subclasses of its fence, wall and pane blocks, which implement the library's {@link StarCollisionBlock}
 * (its twins' own interface) so the twins of the vanilla blocks connect diagonally TO ours (they attach to any
 * {@code DiagonalBlock} of their type) and, when a twin's arm towards one of ours is set or cleared, ask us to
 * re-check ours through {@link #updateIndirectNeighborDiagonalProperty} instead of writing their properties into
 * our state. Our side tells the twins to re-check in turn ({@link #updateTwins}). Everything else (rules, the mask,
 * shapes, rendering) is {@link DiagonalShapes}, shared with the library-less case. Only loaded when
 * {@link DiagonalShapes#libPresent()}.
 */
public final class LibDiagonalBlocks {

    private LibDiagonalBlocks() {}

    public static ShapeFenceBlock fence(final BlockBehaviour.Properties properties) { return new Fence(properties); }

    public static ShapeWallBlock wall(final BlockBehaviour.Properties properties) { return new Wall(properties); }

    public static ShapePaneBlock pane(final BlockBehaviour.Properties properties) { return new Pane(properties); }

    /** The library's twins at the four diagonals re-check their arm towards {@code pos} the library's own way. */
    static void updateTwins(final LevelAccessor level, final BlockPos pos, final int flags, final int recursionLeft) {
        for (final DiagonalShapes.Diagonal d : DiagonalShapes.Diagonal.values()) {
            final BlockPos np = pos.offset(d.dx, 0, d.dz);
            final BlockState ns = level.getBlockState(np);
            if (!(ns.getBlock() instanceof StarCollisionBlock twin) || ns.getBlock() instanceof ShapeBlock) continue;
            final BlockState updated = twin.updateIndirectNeighborDiagonalProperty(ns, level, np, EightWayDirection.valueOf(d.opposite().name()));
            if (updated != null && updated != ns) level.setBlock(np, updated, flags, recursionLeft);
        }
    }

    /** A twin's arm towards our block at {@code pos} changed: our arms live in the block entity, so recompute and leave the state alone. */
    private static @Nullable BlockState armsChanged(final BlockState state, final LevelAccessor level, final BlockPos pos, final DiagonalShapes.Kind kind) {
        DiagonalShapes.refresh(state, level, pos, kind);
        return null;
    }

    public static final class Fence extends ShapeFenceBlock implements StarCollisionBlock {
        Fence(final BlockBehaviour.Properties properties) { super(properties); }

        @Override public DiagonalBlockType getType() { return DiagonalBlockTypes.FENCE; }

        @Override
        public @Nullable BlockState updateIndirectNeighborDiagonalProperty(final BlockState state, final LevelAccessor level, final BlockPos pos,
                                                                           final EightWayDirection direction) {
            return armsChanged(state, level, pos, DiagonalShapes.Kind.FENCE);
        }

        @Override
        public boolean attachesDirectlyTo(final BlockState state, final boolean sturdy, final Direction direction) {
            return connectsTo(state, sturdy, direction);
        }

        @Override
        public boolean attachesDiagonallyTo(final BlockState state, final EightWayDirection direction) {
            return DiagonalShapes.attaches(state, DiagonalShapes.Kind.FENCE);
        }

        @Override
        public void updateIndirectNeighbourShapes(final BlockState state, final LevelAccessor level, final BlockPos pos, final int flags, final int recursionLeft) {
            super.updateIndirectNeighbourShapes(state, level, pos, flags, recursionLeft);
            updateTwins(level, pos, flags, recursionLeft);
        }
    }

    public static final class Wall extends ShapeWallBlock implements StarCollisionBlock {
        Wall(final BlockBehaviour.Properties properties) { super(properties); }

        @Override public DiagonalBlockType getType() { return DiagonalBlockTypes.WALL; }

        @Override
        public @Nullable BlockState updateIndirectNeighborDiagonalProperty(final BlockState state, final LevelAccessor level, final BlockPos pos,
                                                                           final EightWayDirection direction) {
            return armsChanged(state, level, pos, DiagonalShapes.Kind.WALL);
        }

        /** Vanilla's wall rule ({@code WallBlock.connectsTo} is private): walls, gates facing us, or any sturdy non-exception face. */
        @Override
        public boolean attachesDirectlyTo(final BlockState state, final boolean sturdy, final Direction direction) {
            final Block block = state.getBlock();
            if (block instanceof FenceGateBlock && FenceGateBlock.connectsToDirection(state, direction)) return true;
            return state.is(BlockTags.WALLS) || block instanceof WallBlock || (!Block.isExceptionForConnection(state) && sturdy);
        }

        @Override
        public boolean attachesDiagonallyTo(final BlockState state, final EightWayDirection direction) {
            return DiagonalShapes.attaches(state, DiagonalShapes.Kind.WALL);
        }

        @Override
        public void updateIndirectNeighbourShapes(final BlockState state, final LevelAccessor level, final BlockPos pos, final int flags, final int recursionLeft) {
            super.updateIndirectNeighbourShapes(state, level, pos, flags, recursionLeft);
            updateTwins(level, pos, flags, recursionLeft);
        }
    }

    public static final class Pane extends ShapePaneBlock implements StarCollisionBlock {
        Pane(final BlockBehaviour.Properties properties) { super(properties); }

        @Override public DiagonalBlockType getType() { return DiagonalBlockTypes.WINDOW; }

        @Override
        public @Nullable BlockState updateIndirectNeighborDiagonalProperty(final BlockState state, final LevelAccessor level, final BlockPos pos,
                                                                           final EightWayDirection direction) {
            return armsChanged(state, level, pos, DiagonalShapes.Kind.PANE);
        }

        @Override
        public boolean attachesDirectlyTo(final BlockState state, final boolean sturdy, final Direction direction) {
            return attachsTo(state, sturdy);
        }

        @Override
        public boolean attachesDiagonallyTo(final BlockState state, final EightWayDirection direction) {
            return DiagonalShapes.attaches(state, DiagonalShapes.Kind.PANE);
        }

        @Override
        public void updateIndirectNeighbourShapes(final BlockState state, final LevelAccessor level, final BlockPos pos, final int flags, final int recursionLeft) {
            super.updateIndirectNeighbourShapes(state, level, pos, flags, recursionLeft);
            updateTwins(level, pos, flags, recursionLeft);
        }
    }
}
