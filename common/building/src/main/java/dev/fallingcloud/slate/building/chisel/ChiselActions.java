package dev.fallingcloud.slate.building.chisel;

import dev.fallingcloud.slate.building.net.ChiselHeld;
import dev.fallingcloud.slate.building.net.ChiselTarget;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server handlers of the chisel payloads (server thread, from {@code BuildingNetwork}). Only 1:1 swaps inside one
 * group are allowed (design §9); validate slot, group membership, tool tier, reach and protection.
 *
 * <p>Owner: I (chisel). Skeleton stub: ignores the requests.
 */
public final class ChiselActions {

    /** Swap the stack in {@code p.slot()} to group member {@code p.material()}, keeping shape and count. */
    public static void chiselHeld(final ChiselHeld p, final ServerPlayer player) {
    }

    /** Chisel the block at {@code p.pos()} into group member {@code p.material()}. */
    public static void chiselTarget(final ChiselTarget p, final ServerPlayer player) {
    }

    private ChiselActions() {}
}
