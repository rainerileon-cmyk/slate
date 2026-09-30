package dev.fallingcloud.slate.core.stage.node;

import dev.fallingcloud.slate.core.stage.StageLevel;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A piece of world built block by block from code: a meadow for friends to stand on, a clearing with a campfire.
 * Real block states, meshed and lit the way the game does it, so grass is tinted, flowers are crossed planes and a
 * campfire burns. Put the blocks, then {@link #done()}; the node's origin is the min corner of the box (or the
 * middle of its floor after {@link #centered()}).
 *
 * <pre>
 * BlocksNode land = new BlocksNode(stage.level(), 16, 8, 12);
 * land.fill(0, 0, 0, 15, 0, 11, Blocks.GRASS_BLOCK);
 * land.put(7, 1, 5, Blocks.CAMPFIRE.defaultBlockState());
 * stage.add(land.done()).centered();
 * </pre>
 */
public final class BlocksNode extends MeshedBlocksNode {

    private final int sizeX, sizeY, sizeZ;

    public BlocksNode(final StageLevel level, final int sizeX, final int sizeY, final int sizeZ) {
        super(level);
        this.sizeX = Math.max(1, sizeX);
        this.sizeY = Math.max(1, sizeY);
        this.sizeZ = Math.max(1, sizeZ);
        pickable(false);
        hoverFeel(0f, 1f);
        named("blocks");
    }

    public boolean inside(final int x, final int y, final int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < sizeX && y < sizeY && z < sizeZ;
    }

    /** Puts a block; places outside the box are ignored. */
    public BlocksNode put(final int x, final int y, final int z, final BlockState state) {
        return put(x, y, z, state, null);
    }

    public BlocksNode put(final int x, final int y, final int z, final BlockState state, @Nullable final CompoundTag nbt) {
        if (inside(x, y, z)) set(x, y, z, state, nbt);
        return this;
    }

    public BlocksNode put(final int x, final int y, final int z, final Block block) {
        return put(x, y, z, block.defaultBlockState(), null);
    }

    /** Fills the box between two corners (both included). */
    public BlocksNode fill(final int x0, final int y0, final int z0, final int x1, final int y1, final int z1, final Block block) {
        final BlockState state = block.defaultBlockState();
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) put(x, y, z, state, null);
            }
        }
        return this;
    }

    public BlockState at(final int x, final int y, final int z) {
        return get(x, y, z);
    }

    /** The highest block of a column, -1 when it is empty. */
    public int top(final int x, final int z) {
        for (int y = sizeY - 1; y >= 0; y--) if (!get(x, y, z).isAir()) return y;
        return -1;
    }

    /** Call after the last block: the node is meshed from what stands there now. */
    public BlocksNode done() {
        finishFill(new Vec3i(sizeX, sizeY, sizeZ));
        return this;
    }

    @Override
    public BlocksNode centered() {
        super.centered();
        return this;
    }
}
