package dev.fallingcloud.slate.building.variant;

import dev.fallingcloud.slate.building.net.ReshapeTarget;
import dev.fallingcloud.slate.building.net.SwapHeld;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server handlers of the variant payloads (called on the server thread by {@code BuildingNetwork}). Validate
 * everything: slot range, that the stack identifies as a variant, that the target shape is available, reach and
 * protection for in-world reshapes, and the economy rules of design §1.
 *
 * <p>Owner: A (variants). Skeleton stub: ignores the requests.
 */
public final class VariantActions {

    /** Turn the stack in {@code p.slot()} into {@code p.shape()} of the same material, count kept (1:1). */
    public static void swapHeld(final SwapHeld p, final ServerPlayer player) {
    }

    /** Reshape the block at {@code p.pos()} in the world, charging / refunding the unit difference. */
    public static void reshapeTarget(final ReshapeTarget p, final ServerPlayer player) {
    }

    private VariantActions() {}
}
