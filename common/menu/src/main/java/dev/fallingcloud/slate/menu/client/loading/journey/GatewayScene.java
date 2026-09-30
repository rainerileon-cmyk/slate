package dev.fallingcloud.slate.menu.client.loading.journey;

import dev.fallingcloud.slate.core.stage.Stage;
import dev.fallingcloud.slate.core.stage.StageFinish;
import dev.fallingcloud.slate.core.stage.anim.IdleMotion;
import dev.fallingcloud.slate.core.stage.node.BlocksNode;
import dev.fallingcloud.slate.core.stage.node.BurstNode;
import dev.fallingcloud.slate.core.stage.node.FxNode;
import dev.fallingcloud.slate.core.stage.node.MotesNode;
import dev.fallingcloud.slate.core.stage.node.PlayerNode;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The way to a server: a gate of obsidian on a terrace in the dark, the player standing before it. It is cold while
 * the server is looked for, takes fire when the server answers, burns brighter with every step of the way in (logging
 * in, encrypting, joining), and when the world behind it is being sent the view goes in through it.
 *
 * <p>A server that is left shows the same gate going out.</p>
 */
final class GatewayScene {

    /** Steps of the way in: the gate is lit from {@link #ANSWERED} on, and gone through at {@link #THROUGH}. */
    static final int LOOKING = 0, ANSWERED = 1, SECURED = 2, JOINING = 3, THROUGH = 4;

    /** The middle of the gate's opening. */
    private static final float GATE_Y = 3.5f;
    private static final int VIOLET = 0xFFA45CFF;

    private final Stage stage = new Stage();
    private final BlocksNode fire;
    private final FxNode pool, sparks, halo;
    private final MotesNode motes;
    private final BurstNode burst;
    private final PlayerNode player;
    private final StageFinish finish = StageFinish.cinematic().vignette(0.36f);
    private final boolean leaving;
    private float lit, near, through, since;
    private boolean fired;

    /** @param leaving a server that is being left: the gate starts burning and goes out */
    GatewayScene(final boolean leaving) {
        this.leaving = leaving;
        final Minecraft mc = Minecraft.getInstance();
        stage.gradient(0xFF05040A, 0xFF1A1326);
        stage.fog(true, 13f, 30f, 0xFF120D1C);
        stage.soft(true);
        stage.finish(finish);
        stage.resolutionScale(mc.getWindow().getWidth() <= 1600 ? 2f : 1.5f);
        stage.camera().clip(0.05f, 120f);
        stage.camera().idle(IdleMotion.sway(19000f, 2.2f, 0.5f, 0.02f));
        stage.lighting().key(1.5f, 5.5f, 6f).keyColor(0.62f, 0.58f, 0.7f).ambientColor(0.25f, 0.24f, 0.33f)
            .fill(-1f, 0.4f, 0.3f, 0.1f, 0.12f, 0.22f).rim(0.72f, 0.45f, 1f, 0.3f).sun(0.3f, 1f, 0.7f);

        // A dark floor that is lost in the dark, a terrace on it, the gate on the terrace.
        stage.add(FxNode.glow(20f, 0xFF211B2B).fade(2.4f)).at(0f, 0.005f, 1f);
        final BlocksNode terrace = new BlocksNode(stage.level(), 8, 2, 7);
        terrace.fill(0, 0, 0, 7, 0, 6, Blocks.POLISHED_BLACKSTONE_BRICKS);
        terrace.fill(1, 1, 1, 6, 1, 5, Blocks.POLISHED_BLACKSTONE_BRICKS);
        terrace.fill(2, 1, 1, 5, 1, 5, Blocks.POLISHED_DEEPSLATE);
        for (final int[] c : new int[][] {{0, 0, 0}, {7, 0, 0}, {0, 0, 6}, {7, 0, 6}}) terrace.put(c[0], c[1], c[2], Blocks.CHISELED_POLISHED_BLACKSTONE);
        for (final int[] c : new int[][] {{3, 0, 6}, {6, 0, 2}, {1, 1, 4}, {6, 1, 1}}) terrace.put(c[0], c[1], c[2], Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS);
        terrace.put(1, 1, 1, Blocks.GILDED_BLACKSTONE).put(6, 1, 5, Blocks.GILDED_BLACKSTONE);
        stage.add(terrace.done()).centered();

        final BlocksNode gate = new BlocksNode(stage.level(), 4, 5, 1);
        gate.fill(0, 0, 0, 3, 0, 0, Blocks.OBSIDIAN).fill(0, 4, 0, 3, 4, 0, Blocks.OBSIDIAN);
        gate.fill(0, 1, 0, 0, 3, 0, Blocks.OBSIDIAN).fill(3, 1, 0, 3, 3, 0, Blocks.OBSIDIAN);
        gate.put(0, 3, 0, Blocks.CRYING_OBSIDIAN).put(3, 1, 0, Blocks.CRYING_OBSIDIAN).put(2, 4, 0, Blocks.CRYING_OBSIDIAN);
        stage.add(gate.done()).centered().at(0f, 2f, 0f);

        final BlockState portal = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, Direction.Axis.X);
        fire = new BlocksNode(stage.level(), 2, 3, 1);
        for (int x = 0; x < 2; x++) for (int y = 0; y < 3; y++) fire.put(x, y, 0, portal);
        stage.add(fire.done()).centered().at(0f, 3f, 0f);
        fire.alpha(0f);

        pool = (FxNode) stage.add(FxNode.glow(4.2f, 0xB08A45F0).fade(1.5f)).at(0f, 2.02f, 1.2f);
        halo = (FxNode) stage.add(FxNode.glow(9f, 0x38733FD0).fade(1.2f)).at(0f, 0.02f, 3f);
        sparks = (FxNode) stage.add(FxNode.sparkles(30, 2.2f, 3.2f, 1.2f, 0.06f, 0xFFE6CCFF, 3L)).at(0f, GATE_Y, 0.5f);
        motes = (MotesNode) stage.add(new MotesNode(52, 6f, 4.4f, 4f, 0.03f, 0x90C58BFF, 9L)).at(0f, 2f, 0.6f);
        burst = (BurstNode) stage.add(new BurstNode(34, 1.5f, 0.07f, VIOLET, 21L)).at(0f, 2.6f, 0.2f);

        player = PlayerNode.local();
        player.lookAtCursor(false).pose(PlayerNode.Pose.STAND);
        stage.add(player).at(-1.25f, 2f, 2.1f).yaw(168f);

        if (leaving) {
            lit = 1f;
            fired = true;
        }
        glow();
    }

    Stage stage() { return stage; }

    boolean leaving() { return leaving; }

    /** Everything that lives off the gate's fire, at the strength the fire has now. */
    private void glow() {
        final float l = lit;
        fire.alpha(Math.min(1f, l * 1.25f));
        pool.opacity(0.69f * l);
        halo.opacity(0.22f * l);
        sparks.opacity(l);
        motes.strength(0.25f + 0.75f * l);
        finish.bloom(0.42f + 0.34f * l);
        stage.lighting().keyColor(Mth.lerp(l, 0.62f, 0.66f), Mth.lerp(l, 0.58f, 0.46f), Mth.lerp(l, 0.7f, 0.95f)).key(Mth.lerp(l, 1.5f, 0.4f), Mth.lerp(l, 5.5f, 3.6f),
            Mth.lerp(l, 6f, 2.6f));
        stage.lighting().rim(0.72f, 0.45f, 1f, 0.16f + 0.3f * l);
    }

    /**
     * One frame.
     *
     * @param step how far the way in is ({@link #LOOKING} to {@link #THROUGH}); a server being left: ignored
     */
    void render(final GuiGraphics g, final int width, final int height, final int step, final float partialTick) {
        final float motion = Theme.current().motion();
        final float now = stage.timeMs();
        final float dt = Math.min(0.25f, Math.max(0f, now - since) / 1000f);
        since = now;
        final float wantLit, wantNear, wantThrough;
        if (leaving) {
            // The gate goes out, the view draws back.
            wantLit = 0f;
            wantNear = 0f;
            wantThrough = 0f;
        } else {
            wantLit = step <= LOOKING ? 0f : step == ANSWERED ? 0.6f : step == SECURED ? 0.8f : 1f;
            wantNear = Mth.clamp(step / 3f, 0f, 1f);
            wantThrough = step >= THROUGH ? 1f : 0f;
        }
        if (motion <= 0f) {
            lit = wantLit;
            near = wantNear;
            through = wantThrough;
        } else {
            lit += Mth.clamp(wantLit - lit, -dt * (leaving ? 0.55f : 2f), dt * 1.5f);
            near += (wantNear - near) * Math.min(1f, dt * 1.6f);
            through += Mth.clamp(wantThrough - through, -dt, dt * 0.62f);
        }
        if (!fired && lit > 0.05f) {
            fired = true;
            burst.fire();
        }
        glow();
        aim(width / (float) Math.max(1, height));
        stage.render(g, 0, 0, width, height, -1, -1, partialTick);
    }

    /** From the side of the terrace towards the gate; at the end in through it. */
    private void aim(final float aspect) {
        final float narrow = aspect < 1.5f ? (1.5f - aspect) * 9f : 0f;
        final float n = near * near * (3f - 2f * near);
        float x = Mth.lerp(n, 4.0f, 3.1f), y = Mth.lerp(n, 3.5f, 3.3f), z = Mth.lerp(n, 14.2f, 12.2f);
        float tx = Mth.lerp(n, -0.4f, -0.3f), ty = Mth.lerp(n, 3.5f, 3.6f), tz = 0f;
        float fov = Mth.lerp(n, 38f, 37f) + narrow;
        if (through > 0f) {
            final float t = through * through * (3f - 2f * through);
            x = Mth.lerp(t, x, 0f);
            y = Mth.lerp(t, y, GATE_Y);
            z = Mth.lerp(t, z, 0.75f);
            tx = Mth.lerp(t, tx, 0f);
            ty = Mth.lerp(t, ty, GATE_Y);
            tz = Mth.lerp(t, tz, -4f);
            fov = Mth.lerp(t, fov, 58f);
        }
        stage.camera().at(x, y, z).lookAt(tx, ty, tz).fov(fov);
    }

    /** How much of the way through the gate the view is: the words fade as it goes. */
    float through() { return through; }

    void close() {
        stage.close();
    }
}
