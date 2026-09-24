package dev.fallingcloud.slate.building.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * A cheap counter of client world changes inside one watched region: block changes (placed, broken, updated by the
 * server, a shape block's material changing) and chunks loading or unloading there. Fed by
 * {@code ClientLevelChangesMixin} ({@code ClientLevel.sendBlockUpdated}, {@code onChunkLoaded}, {@code unload}); one
 * bounds check per change. A preview that depends on the blocks in and around a selection watches that region and
 * re-plans when {@link #count()} moved, instead of re-planning on a timer. Client thread only.
 */
public final class WorldChanges {

    private static boolean watching;
    private static int minX, minY, minZ, maxX, maxY, maxZ;
    private static long count;

    /**
     * Watches {@code region} (world coordinates, grown by {@code margin} blocks on every side, for planners that read
     * neighbours) from now on, replacing the previous region; null stops watching.
     */
    public static void watch(final @Nullable AABB region, final int margin) {
        if (region == null) {
            watching = false;
            return;
        }
        minX = (int) Math.floor(region.minX) - margin;
        minY = (int) Math.floor(region.minY) - margin;
        minZ = (int) Math.floor(region.minZ) - margin;
        maxX = (int) Math.ceil(region.maxX) - 1 + margin;
        maxY = (int) Math.ceil(region.maxY) - 1 + margin;
        maxZ = (int) Math.ceil(region.maxZ) - 1 + margin;
        watching = true;
    }

    /** How many changes were seen inside the watched region so far (only ever grows). */
    public static long count() {
        return count;
    }

    /** A block changed on the client ({@code ClientLevel.sendBlockUpdated}). */
    public static void blockChanged(final BlockPos pos) {
        if (!watching) return;
        final int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        if (x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ) count++;
    }

    /** A chunk column loaded or unloaded on the client: every block in it may have changed. */
    public static void chunkChanged(final int chunkX, final int chunkZ) {
        if (!watching) return;
        if (chunkX >= minX >> 4 && chunkX <= maxX >> 4 && chunkZ >= minZ >> 4 && chunkZ <= maxZ >> 4) count++;
    }

    private WorldChanges() {}
}
