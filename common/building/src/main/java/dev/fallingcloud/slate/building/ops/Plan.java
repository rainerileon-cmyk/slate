package dev.fallingcloud.slate.building.ops;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * What a {@link ModePlanner} produced: the changes in execution order, their bounds (for the overlay and limits),
 * an error when the selection cannot be applied (shown by the HUD, blocks apply), and how many positions the mode
 * wanted before limits/filters trimmed it ({@code requestedCount}, for "384/512" style stats).
 */
public record Plan(List<Change> changes, AABB bounds, @Nullable Component error, int requestedCount) {

    /** Nothing to do, no error. */
    public static final Plan EMPTY = new Plan(List.of(), new AABB(0, 0, 0, 0, 0, 0), null, 0);

    public Plan {
        changes = List.copyOf(changes);
    }

    public static Plan error(final Component error) {
        return new Plan(List.of(), EMPTY.bounds(), error, 0);
    }

    /** No error (it may still be empty). */
    public boolean ok() {
        return error == null;
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }
}
