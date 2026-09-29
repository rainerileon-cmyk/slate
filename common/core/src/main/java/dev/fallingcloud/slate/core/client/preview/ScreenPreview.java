package dev.fallingcloud.slate.core.client.preview;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.core.screen.slot.Style;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Renders a real screen, small, into its own render target and blits it into a rectangle of the open screen: the
 * setup screen's live previews show the actual title screen of the chosen layout and style rather than a painted
 * mock. The previewed screen comes from a factory (a menu slot's screen, {@link MenuSlots#preview}), is initialised
 * at a virtual GUI size chosen so that every virtual pixel is a whole number of real ones (crisp text), ticked and
 * rendered each frame with the mouse parked off-screen, painted in its own slot's style, and disposed with
 * {@link #close()}. The GL state the off-screen pass touches (target, viewport, projection, blend, depth, cull,
 * shader colour) is put back, so the outer screen is untouched. A screen that throws is dropped, once, with a log
 * line, and {@link #render} then returns false so the caller can draw a mock instead.
 */
public final class ScreenPreview implements AutoCloseable {

    /** The GUI width the previewed screens are laid out for (a 854 px window at GUI scale 2). */
    private static final int VIRTUAL_WIDTH = 427;
    private static final int MAX_TEXTURE = 2048;

    private final Supplier<Screen> factory;
    @Nullable private Screen screen;
    @Nullable private TextureTarget target;
    private int vw, vh, tw, th;
    private boolean failed;

    public ScreenPreview(final Supplier<Screen> factory) {
        this.factory = factory;
    }

    /** Drops the previewed screen so the next frame builds a fresh one (after the layout or style changed). */
    public void invalidate() {
        dispose();
        failed = false;
    }

    public boolean failed() { return failed; }

    /** Once per client tick. */
    public void tick() {
        if (screen != null) {
            try { screen.tick(); } catch (final Exception e) { fail("tick", e); }
        }
    }

    /**
     * Draws the preview into {@code x, y, w, h} (GUI pixels) with {@code alpha}. Returns false when nothing was drawn
     * (the screen failed or the rectangle is too small).
     */
    public boolean render(final GuiGraphics g, final int x, final int y, final int w, final int h, final float alpha, final float partialTick) {
        if (failed || w < 32 || h < 18 || alpha <= 0.004f) return false;
        final Minecraft mc = Minecraft.getInstance();
        final Window win = mc.getWindow();
        final int scale = (int) Math.max(1, Math.round(win.getGuiScale()));
        final int pw = Math.min(MAX_TEXTURE, w * scale), ph = Math.min(MAX_TEXTURE, h * scale);
        // Whole real pixels per virtual pixel, so the preview is a true miniature with crisp text.
        final int k = Math.max(1, Math.round(pw / (float) VIRTUAL_WIDTH));
        final int nvw = Math.max(1, pw / k), nvh = Math.max(1, ph / k);
        try {
            if (target == null || tw != pw || th != ph) {
                if (target != null) target.destroyBuffers();
                target = new TextureTarget(pw, ph, true, Minecraft.ON_OSX);
                target.setClearColor(0f, 0f, 0f, 0f);
                tw = pw;
                th = ph;
            }
            if (screen == null || vw != nvw || vh != nvh) {
                if (screen != null) screen.removed();
                vw = nvw;
                vh = nvh;
                screen = factory.get();
                screen.init(mc, vw, vh);
            }
        } catch (final Exception e) {
            fail("build", e);
            return false;
        }
        // Everything the outer screen batched so far is drawn before the target changes.
        g.flush();
        final Style outerStyle = Theme.activeStyle();
        Theme.activate(MenuSlots.styleFor(screen));
        try {
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0f, vw, vh, 0f, 1000f, 21000f), VertexSorting.ORTHOGRAPHIC_Z);
            final GuiGraphics inner = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            screen.render(inner, -1, -1, partialTick);
            inner.flush();
        } catch (final Exception e) {
            fail("render", e);
            return false;
        } finally {
            Theme.activate(outerStyle);
            // Back to the window: the GUI projection GameRenderer set up, the main target and its viewport, the usual state.
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0f, (float) ((double) win.getWidth() / win.getGuiScale()),
                (float) ((double) win.getHeight() / win.getGuiScale()), 0f, 1000f, 21000f), VertexSorting.ORTHOGRAPHIC_Z);
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
        // The target's colour texture into the rectangle; the texture's origin is bottom-left, so v runs 1 -> 0.
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        RenderSystem.enableBlend();
        final Matrix4f m = g.pose().last().pose();
        final BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(m, x, y, 0).setUv(0f, 1f);
        b.addVertex(m, x, y + h, 0).setUv(0f, 0f);
        b.addVertex(m, x + w, y + h, 0).setUv(1f, 0f);
        b.addVertex(m, x + w, y, 0).setUv(1f, 1f);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        return true;
    }

    private void fail(final String what, final Exception e) {
        failed = true;
        Slate.LOGGER.warn("[Slate] screen preview failed to {} ({}); showing the painted preview instead", what, e.toString());
        dispose();
    }

    private void dispose() {
        if (screen != null) {
            try { screen.removed(); } catch (final Exception ignored) {}
            screen = null;
        }
    }

    @Override
    public void close() {
        dispose();
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }
}
