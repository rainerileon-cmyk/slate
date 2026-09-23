package dev.fallingcloud.slate.building.ops;

import dev.fallingcloud.slate.building.variant.Variant;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * One planned block change.
 *
 * @param pos           where
 * @param target        the state to set ({@code air} for BREAK)
 * @param targetVariant the variant being placed when {@code target} is one of our shape blocks (the material goes
 *                      into its block entity) or a native variant; null for plain blocks and breaks
 * @param kind          PLACE into air/replaceable, REPLACE an existing block, BREAK (to air)
 */
public record Change(BlockPos pos, BlockState target, @Nullable Variant targetVariant, Kind kind) {

    public enum Kind { PLACE, REPLACE, BREAK }

    public Change {
        pos = pos.immutable();
    }
}
