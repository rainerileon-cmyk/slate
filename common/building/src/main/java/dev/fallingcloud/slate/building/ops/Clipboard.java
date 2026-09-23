package dev.fallingcloud.slate.building.ops;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A copied region: its size and the blocks relative to its minimum corner. The server keeps one per player; the
 * client gets a copy through {@code ClipboardSync} for the paste preview.
 *
 * <p>Owner: D1 (ops server). Skeleton: the data shape only; D1 adds NBT (de)serialisation and rotation/mirroring,
 * and may add components to the records (never rename).
 */
public record Clipboard(Vec3i size, List<Entry> entries) {

    /**
     * One copied block.
     *
     * @param offset   position relative to the clipboard's minimum corner
     * @param state    the block state
     * @param material the material when the block is one of our shape blocks, else null
     */
    public record Entry(BlockPos offset, BlockState state, @Nullable BlockState material) {}

    public Clipboard {
        entries = List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
