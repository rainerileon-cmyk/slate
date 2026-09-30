package dev.fallingcloud.slate.core.stage;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * How a stage pass blends. A stage draws into a picture of its own that is laid over the GUI afterwards, so the
 * picture's alpha is how much of the GUI it covers. The game's default blending writes the alpha of whatever was
 * drawn last: a faint veil of light over a solid wall would leave the wall as faint as the veil, and the GUI
 * behind would show through it. These two keep the cover right: what is solid stays solid.
 */
public final class StageBlend {

    /** Over what is there: colour by its alpha, and cover that only ever grows. */
    public static void over() {
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }

    /** Light: added to what is there, covering nothing. */
    public static void add() {
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
            GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
    }

    private StageBlend() {}
}
