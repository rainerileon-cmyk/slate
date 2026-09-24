package dev.fallingcloud.slate.building.toolbox;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;

/**
 * A container linked to a toolbox with the Supply Link upgrade (resolved, loaded and in range).
 *
 * <p>Owner: E (toolbox). Skeleton: the data shape.
 *
 * @param dimension where the container is
 * @param pos       its position
 * @param container the live container
 */
public record LinkedContainer(ResourceKey<Level> dimension, BlockPos pos, Container container) {

    /** Every slot of the container, for the ops economy. */
    public List<SlotRef> slots() {
        final SlotRef[] out = new SlotRef[container.getContainerSize()];
        for (int i = 0; i < out.length; i++) out[i] = new SlotRef(container, i);
        return List.of(out);
    }
}
