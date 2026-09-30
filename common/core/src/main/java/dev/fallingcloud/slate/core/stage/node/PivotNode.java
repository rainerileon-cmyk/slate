package dev.fallingcloud.slate.core.stage.node;

import dev.fallingcloud.slate.core.stage.StageRenderContext;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * A point in space with nothing to show: other nodes {@link StageNode#attachTo ride on it}, so moving, turning,
 * scaling, hiding or fading the pivot does the same to all of them at once (a chest with its sign and whatever
 * floats above it; a player with a nameplate). {@link #onFrame} runs a callback every frame before anything is
 * placed, which is where such a group animates itself.
 */
public class PivotNode extends StageNode {

    @Nullable private Consumer<StageRenderContext> frame;

    public PivotNode() {
        pickable(false);
        hoverFeel(0f, 1f);
    }

    /** Runs every frame the stage renders, before the transforms of this frame are built. */
    public PivotNode onFrame(@Nullable final Consumer<StageRenderContext> callback) {
        this.frame = callback;
        return this;
    }

    @Override
    public void update(final StageRenderContext ctx) {
        if (frame != null) frame.accept(ctx);
    }

    @Override
    protected void draw(final StageRenderContext ctx) {
        // Nothing to see.
    }
}
