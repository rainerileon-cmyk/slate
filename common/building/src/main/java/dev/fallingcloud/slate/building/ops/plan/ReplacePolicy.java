package dev.fallingcloud.slate.building.ops.plan;

import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.ops.ModeParams;
import net.minecraft.world.level.block.state.BlockState;

/** Which existing blocks a placing mode may overwrite (the {@code replace} parameter). */
public enum ReplacePolicy {
    /** Only air. */
    AIR,
    /** Air and replaceable blocks: grass, flowers, water, snow layers ... */
    REPLACEABLE,
    /** Anything breakable (unbreakable blocks and, in survival, containers are still left alone). */
    ALL;

    /** The policy chosen in {@code params}, or {@code fallback} when the mode has no {@code replace} parameter. */
    public static ReplacePolicy of(final ModeParams params, final ReplacePolicy fallback) {
        return params.mode().param(BuildModes.REPLACE.id()) == null ? fallback
            : params.getEnum(BuildModes.REPLACE.id(), ReplacePolicy.class, fallback);
    }

    /** Whether {@code existing} may be overwritten under this policy. */
    public boolean allows(final BlockState existing) {
        return switch (this) {
            case AIR -> existing.isAir();
            case REPLACEABLE -> existing.canBeReplaced();
            case ALL -> true;
        };
    }
}
