package dev.fallingcloud.slate.core.stage.node;

import dev.fallingcloud.slate.core.stage.StageRenderContext;

/**
 * An invisible box that takes the mouse, the clicks and the keyboard focus for whatever it surrounds: a button made
 * of several nodes (a chest lid, the thing floating in it, its label) has one of these around it, so the whole thing
 * hovers and clicks as one. It draws nothing; the focus outline follows its bounds.
 */
public class HitNode extends StageNode {

    public HitNode() {
        pickable(true);
        hoverFeel(0f, 1f);
    }

    /** A box of {@code width × height × depth} blocks standing on the node's position, centred on X and Z. */
    public HitNode box(final float width, final float height, final float depth) {
        bounds(-width / 2f, 0f, -depth / 2f, width / 2f, height, depth / 2f);
        return this;
    }

    @Override
    protected void draw(final StageRenderContext ctx) {
        // Nothing to see.
    }
}
