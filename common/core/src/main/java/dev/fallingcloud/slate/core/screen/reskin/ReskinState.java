package dev.fallingcloud.slate.core.screen.reskin;

import dev.fallingcloud.slate.core.gfx.Anim;

/**
 * Per-widget animation state the reskin keeps on every vanilla {@code AbstractWidget} (implemented by
 * Core's {@code AbstractWidgetMixin}). Lets buttons, sliders and checkboxes animate their hover/press
 * without Slate owning them.
 */
public interface ReskinState {

    /** 0..1 hover/focus lift, eased. */
    Anim slate$hoverAnim();

    /** 0..1 press amount, eased. */
    Anim slate$pressAnim();
}
