package dev.fallingcloud.slate.building.ops.plan;

import net.minecraft.network.chat.Component;

/**
 * The errors planners report (shown by the HUD, and sent back by the server when it refuses an operation). Keys live
 * in the {@code ops-server} lang part; arguments are plain numbers so the server can forward them as strings.
 */
public final class PlanErrors {

    public static Component noAnchor() { return Component.translatable("slate_building.plan.no_anchor"); }

    public static Component noBlock() { return Component.translatable("slate_building.plan.no_block"); }

    public static Component tooWide(final int span, final int max) { return Component.translatable("slate_building.plan.too_wide", span, max); }

    public static Component tooMany(final int count, final int max) { return Component.translatable("slate_building.plan.too_many", count, max); }

    public static Component noClipboard() { return Component.translatable("slate_building.plan.no_clipboard"); }

    public static Component pickBlock() { return Component.translatable("slate_building.plan.pick_block"); }

    public static Component noShape() { return Component.translatable("slate_building.plan.no_shape"); }

    public static Component noOffhand() { return Component.translatable("slate_building.plan.no_offhand"); }

    public static Component disabled() { return Component.translatable("slate_building.plan.disabled"); }

    public static Component modeDisabled() { return Component.translatable("slate_building.plan.mode_disabled"); }

    public static Component needsToolbox() { return Component.translatable("slate_building.plan.needs_toolbox"); }

    public static Component failed() { return Component.translatable("slate_building.plan.failed"); }

    /** Part of the selection is in chunks that are not loaded (planners never load one). */
    public static Component unloaded() { return Component.translatable("slate_building.error.unloaded"); }

    private PlanErrors() {}
}
