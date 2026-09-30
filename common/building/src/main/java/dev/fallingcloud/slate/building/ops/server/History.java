package dev.fallingcloud.slate.building.ops.server;

import dev.fallingcloud.slate.building.ops.BuildMode;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * One player's undo / redo history (in memory, dropped on logout). Undo reverts an entry position by position,
 * newest first: a position is only touched when it still holds what the operation left there, it refunds what was
 * paid for it and charges back what it gave (design §1), and what it actually reverted becomes the redo entry.
 * Bounded by depth (undoDepth + Memory upgrades), by {@code maxUndoBlocks} positions and by {@code maxUndoDataKiB} of
 * block-entity data (creative operations keep container contents) over both stacks; the oldest entries go first. An
 * operation larger than either cap is not remembered at all.
 */
final class History {

    /**
     * One changed position.
     *
     * @param space          the Sable sub-level the position lay on when it was changed, null in the world itself: a
     *                       position of a ship's plot means nothing once the ship is gone, and something else once
     *                       another ship has the plot, so it is only reverted while it is still that ship's
     * @param before         the state before
     * @param beforeMaterial our shape block's material before, else null
     * @param beforeData     block-entity data before (creative only), else null
     * @param after          the state the operation left
     * @param afterMaterial  our shape block's material after, else null
     * @param paid           what the player paid for {@code after}
     * @param gained         what the player got for {@code before} (drops, refunds)
     */
    record BlockRecord(BlockPos pos, @Nullable UUID space, BlockState before, @Nullable BlockState beforeMaterial, @Nullable CompoundTag beforeData,
                       BlockState after, @Nullable BlockState afterMaterial, List<Economy.Cost> paid, List<Economy.Cost> gained) {}

    /**
     * One undoable operation; {@code records} in execution order; {@code free} when it ran without costs (creative);
     * {@code dataBytes} the block-entity data it keeps.
     */
    record Entry(BuildMode mode, ResourceKey<Level> dimension, List<BlockRecord> records, boolean free, long dataBytes) {

        Entry(final BuildMode mode, final ResourceKey<Level> dimension, final List<BlockRecord> records, final boolean free) {
            this(mode, dimension, records, free, dataBytes(records));
        }

        int size() { return records.size(); }

        private static long dataBytes(final List<BlockRecord> records) {
            long b = 0;
            for (final BlockRecord r : records) if (r.beforeData() != null) b += r.beforeData().sizeInBytes();
            return b;
        }
    }

    private final Deque<Entry> undo = new ArrayDeque<>();
    private final Deque<Entry> redo = new ArrayDeque<>();

    int undoCount() { return undo.size(); }

    int redoCount() { return redo.size(); }

    @Nullable Entry peekUndo() { return undo.peekLast(); }

    @Nullable Entry peekRedo() { return redo.peekLast(); }

    @Nullable Entry popUndo() { return undo.pollLast(); }

    @Nullable Entry popRedo() { return redo.pollLast(); }

    /** A new operation: remembered for undo, and the redo stack is dropped (the timeline branched). */
    void pushNew(final Entry e, final int depth, final int maxBlocks, final long maxData) {
        if (e.records().isEmpty()) return;
        redo.clear();
        pushUndo(e, depth, maxBlocks, maxData);
    }

    void pushUndo(final Entry e, final int depth, final int maxBlocks, final long maxData) {
        if (e.records().isEmpty() || e.size() > maxBlocks || e.dataBytes() > maxData) return;
        undo.addLast(e);
        trim(depth, maxBlocks, maxData);
    }

    void pushRedo(final Entry e, final int depth, final int maxBlocks, final long maxData) {
        if (e.records().isEmpty() || e.size() > maxBlocks || e.dataBytes() > maxData) return;
        redo.addLast(e);
        trim(depth, maxBlocks, maxData);
    }

    void clear() {
        undo.clear();
        redo.clear();
    }

    private void trim(final int depth, final int maxBlocks, final long maxData) {
        final int d = Math.max(1, depth);
        while (undo.size() > d) undo.pollFirst();
        while (redo.size() > d) redo.pollFirst();
        long total = 0;
        long data = 0;
        for (final Entry e : undo) { total += e.size(); data += e.dataBytes(); }
        for (final Entry e : redo) { total += e.size(); data += e.dataBytes(); }
        while ((total > maxBlocks || data > maxData) && (!undo.isEmpty() || !redo.isEmpty())) {
            final Entry dropped = undo.size() >= redo.size() && !undo.isEmpty() ? undo.pollFirst() : redo.pollFirst();
            if (dropped != null) {
                total -= dropped.size();
                data -= dropped.dataBytes();
            }
        }
    }
}
