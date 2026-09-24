package dev.fallingcloud.slate.building.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.client.model.ShapeItemModels;
import dev.fallingcloud.slate.building.client.model.ShapeQuadBaker;
import dev.fallingcloud.slate.core.client.render.SlateRenderEvents;
import dev.fallingcloud.slate.core.event.Event;
import dev.fallingcloud.slate.core.event.SlateEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * Client init of rendering (design §6), and the one place the in-world renderers draw.
 *
 * <p><b>Frame.</b> The ghost and overlay renderers draw from a {@code SlateRenderEvents.AFTER_TRANSLUCENT} listener
 * that is registered at the end of client start-up (first client tick), so it runs after every listener other
 * modules registered during their init: a mode controller that submits from its own AFTER_TRANSLUCENT listener is
 * drawn in the same frame. Just before drawing, {@link #FRAME} fires (per-frame submitters such as the hand preview
 * use it). Nothing is drawn with F1 (hidden GUI) or in Iris' shadow pass. With Immersive Portals (several world
 * passes per frame) each pass draws what was submitted for it.
 *
 * <p><b>Matrices (verified in game on NeoForge 21.1 and Fabric).</b> During AFTER_TRANSLUCENT the RenderSystem
 * model-view matrix holds only the camera rotation and the event's pose stack is identity, so every renderer here
 * writes camera-relative positions ({@code world - camera}) and draws with {@code RenderSystem.getModelViewMatrix()}
 * and {@code getProjectionMatrix()}; cached meshes are stored relative to an origin block and drawn with
 * {@code modelView × translate(origin - camera)} (or the ghost shader's {@code GhostOffset}). On NeoForge the stage
 * fires inside the translucent chunk layer's render state (under Fabulous graphics the translucent target is bound,
 * so ghosts composite with water and glass correctly); on Fabric it fires after particles on the main target. Both
 * restore the GL state we change ({@link RenderKit#restore()}).
 *
 * <p>Owner: B (render). Called from {@code BuildingClient.init()}.
 */
public final class BuildingRender {

    /**
     * Fired once per world render pass, right before ghosts and overlays are drawn: submit per-frame ghosts and
     * overlays here (render thread; the level and player exist).
     */
    public static final Event<Runnable> FRAME = new Event<>("slate_building:render_frame");

    private static boolean initialised;
    private static boolean installed;
    private static boolean inTick;
    private static boolean failedOnce;

    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        SlateEvents.CLIENT_TICK_END.register(BuildingRender::installDrawOnce);
        SlateEvents.CLIENT_LEFT_SERVER.register(BuildingRender::onLeave);
        HandPreview.init();
        RenderHarness.register();
    }

    /** Registered last so every other AFTER_TRANSLUCENT listener (submitters) runs before the draw. */
    private static void installDrawOnce() {
        if (installed) return;
        installed = true;
        SlateRenderEvents.AFTER_TRANSLUCENT.register(BuildingRender::draw);
    }

    // ---- tick bracket (mixin on Minecraft.tick) ----

    /** Whether the client tick is running (submissions made now live until the next tick). */
    static boolean inTick() {
        return inTick;
    }

    /** {@code Minecraft.tick} HEAD. */
    public static void onTickStart() {
        inTick = true;
        GhostRenderer.beginTick();
        OverlayRenderer.beginTick();
    }

    /** {@code Minecraft.tick} RETURN. */
    public static void onTickEnd() {
        inTick = false;
    }

    // ---- drawing ----

    private static void draw(final SlateRenderEvents.WorldRenderContext ctx) {
        final Minecraft mc = Minecraft.getInstance();
        final ClientLevel level = mc.level;
        try {
            if (level == null || mc.player == null || mc.options.hideGui || RenderCompat.shadowPass()) return;
            FRAME.invoke(Runnable::run);
            GhostRenderer.draw(ctx, level);
            OverlayRenderer.draw(ctx);
        } catch (final RuntimeException e) {
            if (!failedOnce) {
                failedOnce = true;
                SlateBuilding.LOGGER.error("[Slate Building] world overlay rendering failed (logged once)", e);
            }
        } finally {
            RenderKit.restore();
            GhostRenderer.endPass();
            OverlayRenderer.endPass();
        }
    }

    /** Leaving a world. Fabric fires the disconnect event on the network thread; GPU buffers are freed on the render thread. */
    private static void onLeave() {
        if (!RenderSystem.isOnRenderThread()) {
            Minecraft.getInstance().execute(BuildingRender::onLeave);
            return;
        }
        GhostRenderer.clearAll();
        OverlayRenderer.clearAll();
        HandPreview.reset();
    }

    /**
     * Resources reloaded (loader glue): cropped quads, item models and ghost meshes hold atlas coordinates of the
     * old atlas, so all of them are rebuilt on next use.
     */
    public static void onResourcesReloaded() {
        ShapeQuadBaker.clearCaches();
        ShapeItemModels.clear();
        if (Minecraft.getInstance().isSameThread()) GhostRenderer.invalidate();
        else Minecraft.getInstance().execute(GhostRenderer::invalidate);
    }

    private BuildingRender() {}
}
