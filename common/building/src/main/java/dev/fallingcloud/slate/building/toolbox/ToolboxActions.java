package dev.fallingcloud.slate.building.toolbox;

import dev.fallingcloud.slate.building.net.OpenToolbox;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server handlers of the toolbox payloads (server thread, from {@code BuildingNetwork}).
 *
 * <p>Owner: E (toolbox). Skeleton stub: ignores the request.
 */
public final class ToolboxActions {

    /** Open the toolbox in {@code p.slot()} through {@code BuildingPlatform.openMenu}. */
    public static void openToolbox(final OpenToolbox p, final ServerPlayer player) {
    }

    private ToolboxActions() {}
}
