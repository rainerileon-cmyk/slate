package dev.fallingcloud.slate.building.ops;

import net.minecraft.network.chat.Component;

/**
 * The {@link ModePlanner} of each {@link BuildMode}.
 *
 * <p>Owner: D1 (ops server). Skeleton stub: every mode plans nothing and reports
 * {@code slate_building.plan.unavailable}, so the client flow can already be built and shows a clean error.
 */
public final class Planners {

    public static ModePlanner of(final BuildMode mode) {
        return ctx -> Plan.error(Component.translatable("slate_building.plan.unavailable", mode.name()));
    }

    private Planners() {}
}
