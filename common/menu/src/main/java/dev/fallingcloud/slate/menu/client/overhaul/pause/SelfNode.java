package dev.fallingcloud.slate.menu.client.overhaul.pause;

import dev.fallingcloud.slate.core.stage.StageRenderContext;
import dev.fallingcloud.slate.core.stage.node.StageNode;
import dev.fallingcloud.slate.menu.SlateMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;

/**
 * The player, as they are at this moment, on a stage: the game's own player entity drawn by its own renderer, so the
 * armour they wear, what they hold, their cape and whatever a mod hangs on them are all there. Only the way they face
 * is the stage's: they look out of the scene, at whoever is looking at it, for as long as they are drawn, and are
 * given back exactly as they were.
 *
 * <p>The node's origin is the player's feet; yaw 0 faces +Z.</p>
 */
final class SelfNode extends StageNode {

    private boolean broken;

    SelfNode() {
        bounds(-0.4f, 0f, -0.4f, 0.4f, 1.9f, 0.4f);
        hoverFeel(0.03f, 1.03f);
        named("you");
    }

    @Override
    protected void draw(final StageRenderContext ctx) {
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer p = mc.player;
        if (p == null || broken) return;
        final EntityRenderer<? super LocalPlayer> renderer = mc.getEntityRenderDispatcher().getRenderer(p);
        if (renderer == null) return;
        final float body = p.yBodyRot, bodyO = p.yBodyRotO, yaw = p.getYRot(), yawO = p.yRotO, pitch = p.getXRot(), pitchO = p.xRotO,
            head = p.yHeadRot, headO = p.yHeadRotO;
        try {
            p.yBodyRot = 0f;
            p.yBodyRotO = 0f;
            p.setYRot(0f);
            p.yRotO = 0f;
            p.setXRot(0f);
            p.xRotO = 0f;
            p.yHeadRot = 0f;
            p.yHeadRotO = 0f;
            // The game's own partial tick: a paused game stands still between two ticks, and so does the player.
            renderer.render(p, 0f, mc.getTimer().getGameTimeDeltaPartialTick(true), ctx.pose, ctx.buffers, ctx.light);
        } catch (final RuntimeException e) {
            broken = true;
            SlateMenu.LOGGER.warn("[Slate Menu] the player could not be drawn on the pause menu, left out: {}", e.toString());
        } finally {
            p.yBodyRot = body;
            p.yBodyRotO = bodyO;
            p.setYRot(yaw);
            p.yRotO = yawO;
            p.setXRot(pitch);
            p.xRotO = pitchO;
            p.yHeadRot = head;
            p.yHeadRotO = headO;
        }
    }
}
