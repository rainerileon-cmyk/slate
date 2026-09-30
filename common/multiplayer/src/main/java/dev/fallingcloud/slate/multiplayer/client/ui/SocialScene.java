package dev.fallingcloud.slate.multiplayer.client.ui;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.stage.Stage;
import dev.fallingcloud.slate.core.stage.StageFinish;
import dev.fallingcloud.slate.core.stage.StageRenderContext;
import dev.fallingcloud.slate.core.stage.anim.IdleMotion;
import dev.fallingcloud.slate.core.stage.node.BlocksNode;
import dev.fallingcloud.slate.core.stage.node.FxNode;
import dev.fallingcloud.slate.core.stage.node.MotesNode;
import dev.fallingcloud.slate.core.stage.node.PivotNode;
import dev.fallingcloud.slate.core.stage.node.PlayerNode;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The piece of Minecraft the Overhaul friends screen shows its people in: a meadow with a path in the late
 * afternoon (the Friends tab: friends stand on the path, or sit on it when they are offline), or a clearing at
 * night with a campfire burning in its middle (the Groups tab: the members sit round the fire). The land is made
 * of real blocks; the people are player models wearing their own skins.
 *
 * <p>The scene knows nothing about friends: it is handed figures (who, where, standing or sitting) and draws
 * their name and a line under it over their heads, in the GUI's own crisp text. The ground is at {@code y = 0}, the
 * middle of the path (or the fire) at the origin, the viewer looks along {@code -z}.</p>
 */
final class SocialScene {

    enum Setting { MEADOW, CAMPFIRE }

    /** One person on the scene. */
    static final class Figure {
        final UUID uuid;
        final PivotNode root = new PivotNode();
        final PlayerNode body;
        final FxNode pool;
        final boolean sitting;
        Component name = Component.empty();
        Component line = Component.empty();
        int lineColor;
        /** Drawn grey: there, but not reachable right now. */
        boolean faded;
        private final Anim in = new Anim(0, 360, Ease.OUT_BACK);
        private final Anim chosen = new Anim(0, 200, Ease.OUT_CUBIC);
        private final float x, z;
        private final float phase;

        private Figure(final UUID uuid, final PlayerNode body, final FxNode pool, final boolean sitting, final float x, final float z, final float phase) {
            this.uuid = uuid;
            this.body = body;
            this.pool = pool;
            this.sitting = sitting;
            this.x = x;
            this.z = z;
            this.phase = phase;
        }

        float hover() { return body.hover(); }
    }

    final Stage stage;
    final Setting setting;
    private final List<Figure> figures = new ArrayList<>();
    private final Vector3f ndc = new Vector3f();
    @Nullable private UUID selected;

    SocialScene(final Screen owner, final Setting setting) {
        this.setting = setting;
        final Stage s = new Stage().bind(owner);
        this.stage = s;
        s.soft(true);
        s.resolutionScale(Minecraft.getInstance().getWindow().getWidth() <= 2048 ? 2f : 1.5f);
        s.focusOutline(false);
        if (setting == Setting.MEADOW) meadow(s);
        else campfire(s);
    }

    // ------------------------------------------------------------------ the two places

    private static void meadow(final Stage s) {
        // Late afternoon: a blue that has started to warm, gold along the horizon, long soft light from the right.
        s.gradient(0xFF2F5FA6, 0xFFF3D3A2);
        s.fog(true, 15f, 34f, 0xFFEFCFA0);
        s.finish(StageFinish.cinematic().bloom(0.3f).vignette(0.24f));
        s.lighting().sun(0.55f, 0.85f, 0.45f).ambient(0.62f)
            .key(6f, 6f, 7f).keyColor(0.74f, 0.64f, 0.5f).ambientColor(0.42f, 0.42f, 0.46f).fill(-1f, 0.3f, 0.3f, 0.14f, 0.18f, 0.26f).rim(1f, 0.9f, 0.72f, 0.2f);

        final int w = 31, h = 11, d = 15;
        final BlocksNode land = new BlocksNode(s.level(), w, h, d);
        final Random rnd = new Random(20260929L);
        final int[][] top = new int[w][d];
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                // Two blocks of ground everywhere; towards the back the land rises into a low bank.
                int height = 2;
                if (z <= 4) height += Math.round((5 - z) * 0.55f + Mth.sin(x * 0.7f) * 0.6f + Mth.cos(x * 0.31f + 1.3f) * 0.5f);
                height = Mth.clamp(height, 2, 5);
                top[x][z] = height;
                for (int y = 0; y < height; y++) land.put(x, y, z, y == height - 1 ? Blocks.GRASS_BLOCK : Blocks.DIRT);
            }
        }
        // The path the friends stand on, a little ragged along its edges.
        for (int x = 0; x < w; x++) {
            for (int z = 8; z <= 9; z++) land.put(x, 1, z, Blocks.DIRT_PATH);
            if (rnd.nextInt(3) == 0) land.put(x, 1, 7, Blocks.DIRT_PATH);
            if (rnd.nextInt(4) == 0) land.put(x, 1, 10, Blocks.DIRT_PATH);
        }
        // Grass and flowers wherever the ground is grass.
        final Block[] flowers = {Blocks.POPPY, Blocks.DANDELION, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER};
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                final int y = top[x][z];
                if (!land.at(x, y - 1, z).is(Blocks.GRASS_BLOCK)) continue;
                final float f = rnd.nextFloat();
                if (f < 0.2f) land.put(x, y, z, Blocks.SHORT_GRASS);
                else if (f < 0.25f) land.put(x, y, z, Blocks.FERN);
                else if (f < 0.31f) land.put(x, y, z, flowers[rnd.nextInt(flowers.length)]);
            }
        }
        // Trees along the bank, two lantern posts where the path leaves the picture, a few things of a lived-in place.
        tree(land, 3, top[3][2], 2, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 4, rnd);
        tree(land, 9, top[9][1], 1, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES, 5, rnd);
        tree(land, 15, top[15][3], 3, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 3, rnd);
        tree(land, 21, top[21][1], 1, Blocks.OAK_LOG, Blocks.OAK_LEAVES, 5, rnd);
        tree(land, 27, top[27][2], 2, Blocks.BIRCH_LOG, Blocks.BIRCH_LEAVES, 4, rnd);
        for (final int x : new int[] {6, 24}) {
            final int y = top[x][6];
            land.put(x, y, 6, Blocks.OAK_FENCE).put(x, y + 1, 6, Blocks.OAK_FENCE).put(x, y + 2, 6, Blocks.LANTERN);
        }
        land.put(12, top[12][5], 5, Blocks.HAY_BLOCK).put(13, top[13][5], 5, Blocks.HAY_BLOCK).put(12, top[12][5] + 1, 5, Blocks.HAY_BLOCK);
        land.put(19, top[19][5], 5, Blocks.BARREL).put(18, top[18][6], 6, Blocks.COMPOSTER);
        // The top of the ground at the path is y = 0 of the scene, the path's middle its z = 0.
        s.add(land.done()).at(-w / 2f, -2f, -9f);

        s.add(FxNode.clouds(7, 34f, 10f, 8.5f, 1.1f, 5L, 0.16f)).at(0f, 0f, -9f).alpha(0.9f);
        s.add(new MotesNode(40, 12f, 3f, 5f, 0.028f, 0x58FFEFC8, 8L)).at(0f, 1.3f, 1f);
    }

    private static void campfire(final Stage s) {
        // Night: the fire is the light, the rest is blue dark and stars.
        s.gradient(0xFF05070F, 0xFF131B33);
        s.fog(true, 9f, 24f, 0xFF0B1022);
        s.finish(StageFinish.cinematic().bloom(0.62f).vignette(0.4f).warmth(0.03f));
        s.lighting().sun(0.2f, 0.9f, 0.4f).ambient(0.3f).brightness(0.62f)
            .key(0f, 0.9f, 0f).keyColor(1.05f, 0.6f, 0.26f).ambientColor(0.13f, 0.15f, 0.26f).fill(0f, 1f, 0.3f, 0.05f, 0.07f, 0.16f).rim(1f, 0.62f, 0.3f, 0.34f).wrap(0.6f);

        final int w = 21, h = 11, d = 17;
        final BlocksNode land = new BlocksNode(s.level(), w, h, d);
        final Random rnd = new Random(4242L);
        final int cx = w / 2, cz = 9;
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                final float far = (float) Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                land.put(x, 0, z, Blocks.DIRT);
                // Trodden earth round the fire, grass beyond, podzol under the trees.
                final Block ground = far < 1.6f ? Blocks.COARSE_DIRT : far < 3.4f && rnd.nextInt(3) != 0 ? Blocks.DIRT_PATH : far > 6.5f && rnd.nextInt(3) == 0 ? Blocks.PODZOL : Blocks.GRASS_BLOCK;
                land.put(x, 1, z, ground);
                if (ground == Blocks.GRASS_BLOCK && far > 3.6f) {
                    final float f = rnd.nextFloat();
                    if (f < 0.2f) land.put(x, 2, z, Blocks.SHORT_GRASS);
                    else if (f < 0.26f) land.put(x, 2, z, Blocks.FERN);
                }
            }
        }
        land.put(cx, 2, cz, Blocks.CAMPFIRE);
        // Spruces standing round the clearing, close behind and open towards the viewer.
        final int[][] trees = {{2, 3}, {6, 1}, {11, 0}, {15, 2}, {19, 4}, {0, 8}, {20, 9}, {1, 13}, {19, 14}};
        for (final int[] t : trees) spruce(land, t[0], 2, t[1], 5 + rnd.nextInt(3));
        land.put(4, 2, 6, Blocks.OAK_FENCE).put(4, 3, 6, Blocks.LANTERN);
        s.add(land.done()).at(-w / 2f, -2f, -cz - 0.5f);

        s.add(FxNode.sparkles(110, 40f, 14f, 4f, 0.07f, 0xC0DCE6FF, 9L)).at(0f, 9f, -14f);
        s.add(FxNode.glow(3.6f, 0x58FF9A3C).fade(1.6f)).at(0f, 0.02f, 0f);
        s.add(FxNode.glow(1.3f, 0x70FFC86A).fade(1.4f)).at(0f, 0.03f, 0f);
        // Sparks going up from the fire.
        s.add(new MotesNode(26, 0.9f, 2.6f, 0.9f, 0.03f, 0xD0FFB050, 2L)).at(0f, 1.7f, 0f);
    }

    private static BlockState leaves(final Block block) {
        final BlockState s = block.defaultBlockState();
        return s.hasProperty(LeavesBlock.PERSISTENT) ? s.setValue(LeavesBlock.PERSISTENT, true) : s;
    }

    private static void tree(final BlocksNode land, final int x, final int y, final int z, final Block log, final Block leaf, final int height, final Random rnd) {
        for (int i = 0; i < height; i++) land.put(x, y + i, z, log);
        final BlockState crown = leaves(leaf);
        for (int dy = height - 2; dy <= height + 1; dy++) {
            final int reach = dy >= height ? 1 : 2;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (Math.abs(dx) == reach && Math.abs(dz) == reach && (reach == 1 || rnd.nextBoolean())) continue;
                    if (dx == 0 && dz == 0 && dy < height) continue;
                    if (land.inside(x + dx, y + dy, z + dz) && land.at(x + dx, y + dy, z + dz).isAir()) land.put(x + dx, y + dy, z + dz, crown);
                }
            }
        }
    }

    private static void spruce(final BlocksNode land, final int x, final int y, final int z, final int height) {
        for (int i = 0; i < height; i++) land.put(x, y + i, z, Blocks.SPRUCE_LOG);
        final BlockState crown = leaves(Blocks.SPRUCE_LEAVES);
        for (int dy = 2; dy <= height + 1; dy++) {
            final int reach = dy > height ? 0 : dy == height ? 1 : (height - dy) % 2 == 0 ? 2 : 1;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (reach == 2 && Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                    if (dx == 0 && dz == 0 && dy < height) continue;
                    if (land.inside(x + dx, y + dy, z + dz) && land.at(x + dx, y + dy, z + dz).isAir()) land.put(x + dx, y + dy, z + dz, crown);
                }
            }
        }
    }

    // ------------------------------------------------------------------ the people

    List<Figure> figures() { return figures; }

    /**
     * Puts a person on the scene. They come in one after the other, in the order they are added.
     *
     * @param yaw degrees the person is turned: 0 faces the viewer
     */
    Figure add(final UUID uuid, final String name, final boolean sitting, final float x, final float z, final float yaw, final Runnable onClick) {
        final PlayerNode body = uuid.equals(Minecraft.getInstance().getUser().getProfileId()) ? PlayerNode.local() : new PlayerNode(uuid, name);
        body.pose(sitting ? PlayerNode.Pose.SIT : PlayerNode.Pose.STAND);
        body.hoverFeel(0.03f, 1.04f);
        body.onClick(onClick);
        final Theme t = Theme.current();
        final FxNode pool = FxNode.glow(0.85f, Colors.withAlpha(Colors.lerp(0xFFFFE9C8, t.accent(), t.isVanilla() ? 0f : 0.5f), 0x66)).fade(1.6f);
        final Figure f = new Figure(uuid, body, pool, sitting, x, z, figures.size() * 1.7f);
        stage.add(f.root).at(x, 0f, z).rotate(yaw, 0f, 0f);
        stage.add(pool).at(0f, 0.03f, 0f).attachTo(f.root);
        stage.add(body).attachTo(f.root);
        f.root.onFrame(ctx -> frame(f, ctx));
        final int delay = Math.min(420, figures.size() * 70);
        f.in.snap(0f);
        f.in.set(1f, 360 + delay);
        figures.add(f);
        return f;
    }

    private void frame(final Figure f, final StageRenderContext ctx) {
        final float in = Mth.clamp(f.in.get(), 0f, 1.15f);
        f.chosen.set(f.uuid.equals(selected));
        final float c = f.chosen.get();
        f.root.scale(Math.max(0.001f, in));
        // The chosen one steps half a pace forward (along the way they face).
        final float yaw = (float) Math.toRadians(f.root.yaw());
        f.root.at(f.x + Mth.sin(yaw) * 0.28f * c, 0f, f.z + Mth.cos(yaw) * 0.28f * c);
        f.pool.alpha(c);
        f.pool.visible(c > 0.02f);
        f.body.muted(f.faded ? 0.78f : 0f);
        // A pointer on someone who is there gets a wave.
        if (!f.sitting) f.body.pose(f.body.hover() > 0.4f && !f.faded ? PlayerNode.Pose.WAVE : PlayerNode.Pose.STAND);
    }

    void clearFigures() {
        for (final Figure f : figures) {
            stage.remove(f.body);
            stage.remove(f.pool);
            stage.remove(f.root);
        }
        figures.clear();
    }

    void select(@Nullable final UUID uuid) { this.selected = uuid; }

    @Nullable UUID selected() { return selected; }

    // ------------------------------------------------------------------ the view

    /**
     * Sets the camera for a view of this shape so that {@code span} blocks of the scene's middle fit across it, and
     * a standing person with their name over them fits from top to bottom.
     */
    void aim(final Rect view, final float span) {
        final float aspect = Math.max(0.6f, view.w() / (float) Math.max(1, view.h()));
        if (setting == Setting.MEADOW) {
            final float fov = 30f;
            final float tan = (float) Math.tan(Math.toRadians(fov / 2f));
            // Far enough for the row to fit across, and for a person with their name to fit upright.
            final float across = (span + 1.2f) / (2f * aspect * tan * 0.9f);
            final float upright = 3.95f / (2f * tan);
            final float dist = Math.max(across, upright);
            stage.camera().at(0f, 1.3f + dist * 0.17f, dist).lookAt(0f, 1.36f, 0f).fov(fov).clip(0.05f, 120f);
            stage.camera().idle(IdleMotion.sway(23000f, 2.2f, 0.4f, 0.015f));
        } else {
            final float fov = 34f;
            final float tan = (float) Math.tan(Math.toRadians(fov / 2f));
            final float across = (span + 1.6f) / (2f * aspect * tan * 0.92f);
            final float upright = 4.4f / (2f * tan);
            final float dist = Math.max(across, upright);
            stage.camera().at(0f, 1f + dist * 0.42f, dist * 0.9f).lookAt(0f, 0.85f, -0.1f).fov(fov).clip(0.05f, 120f);
            stage.camera().idle(IdleMotion.sway(30000f, 9f, 0.5f, 0.02f));
        }
    }

    /** Where a name goes: over the head it belongs to, or higher when another name is in the way. */
    private static final class Tag {
        Figure figure;
        int headX, headY, x, y, w, h, nameW, lineW;
        float depth, alpha;
        net.minecraft.util.FormattedCharSequence name, line;
    }

    /**
     * Draws every person's name, and the line under it, over their head: in the GUI, so the letters are as crisp as
     * every other letter on the screen. Names never cover each other: the one further from the viewer moves up, and a
     * thin line ties it to its head. Call after the stage was drawn into {@code view}.
     */
    void drawTags(final GuiGraphics g, final Rect view) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        final var font = SlateDraw.font();
        final List<Tag> tags = new ArrayList<>();
        for (final Figure f : figures) {
            final float in = Mth.clamp(f.in.get(), 0f, 1f);
            if (in < 0.5f) continue;
            final Vector3f at = f.root.position();
            final float top = (f.sitting ? 1.42f : 1.95f) * Math.min(1f, in) + 0.12f;
            if (!stage.camera().project(at.x, top, at.z, ndc)) continue;
            final Tag tag = new Tag();
            tag.figure = f;
            tag.alpha = (in - 0.5f) * 2f;
            tag.depth = ndc.z;
            tag.headX = view.x() + Math.round((ndc.x * 0.5f + 0.5f) * view.w());
            tag.headY = view.y() + Math.round((0.5f - ndc.y * 0.5f) * view.h());
            tags.add(tag);
        }
        // A name gets the room there is between its head and the next: in a row that is the pace of the row.
        int room = setting == Setting.MEADOW ? 116 : 64;
        if (setting == Setting.MEADOW) {
            // Twice the pace: names of neighbours overlap then, and the one behind moves up a row, so they stand zig-zag.
            for (final Tag a : tags) for (final Tag b : tags) if (a != b) room = Math.min(room, Math.abs(a.headX - b.headX) * 2 - 6);
        }
        room = Math.max(38, room);
        for (final Tag tag : tags) {
            final Figure f = tag.figure;
            tag.name = SlateDraw.truncate(Fonts.heading(f.name), room - 6);
            tag.line = SlateDraw.truncate(f.line, room - 13);
            tag.nameW = font.width(tag.name);
            tag.lineW = font.width(tag.line);
            tag.w = Math.max(tag.nameW, tag.lineW > 0 ? tag.lineW + 7 : 0) + 6;
            tag.h = tag.lineW > 0 ? 22 : 12;
            tag.x = Mth.clamp(tag.headX - tag.w / 2, view.x() + 2, Math.max(view.x() + 2, view.right() - 2 - tag.w));
            tag.y = tag.headY - tag.h - 1;
        }
        // Nearest first: they keep their place, the ones behind make way upwards.
        tags.sort((a, b) -> Float.compare(a.depth, b.depth));
        for (int i = 0; i < tags.size(); i++) {
            final Tag tag = tags.get(i);
            boolean moved = true;
            for (int guard = 0; moved && guard < 12; guard++) {
                moved = false;
                for (int j = 0; j < i; j++) {
                    final Tag o = tags.get(j);
                    if (tag.x < o.x + o.w + 1 && o.x < tag.x + tag.w + 1 && tag.y < o.y + o.h + 1 && o.y < tag.y + tag.h + 1) {
                        tag.y = o.y - tag.h - 1;
                        moved = true;
                    }
                }
            }
            tag.y = Math.max(view.y() + 2, tag.y);
        }
        g.enableScissor(view.x(), view.y(), view.right(), view.bottom());
        for (final Tag tag : tags) {
            final Figure f = tag.figure;
            final float a = tag.alpha;
            final boolean sel = f.uuid.equals(selected);
            final float lit = Math.max(f.chosen.get(), f.hover());
            final int x = tag.x, y = tag.y, w = tag.w, h = tag.h, cx = x + w / 2;
            // A name that had to move up is tied to its head.
            if (tag.headY - (y + h) > 3) SlateDraw.vline(g, tag.headX, y + h, tag.headY - (y + h) - 1, Colors.scaleAlpha(van ? 0x90FFFFFF : Colors.withAlpha(p.textMuted(), 0x90), a));
            // A plate behind the two lines, stronger for whoever is chosen or pointed at.
            final int plate = van ? Colors.withAlpha(0xFF000000, Math.round(0x58 + 0x40 * lit)) : Colors.withAlpha(p.bg(), Math.round(0x80 + 0x58 * lit));
            SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(plate, a), van ? 0 : 2);
            if (sel) SlateDraw.rect(g, x + 2, y + h - 1, w - 4, 1, Colors.scaleAlpha(van ? 0xFFFFFFFF : p.accent(), a));
            final int ink = f.faded ? (van ? 0xFFC0C0C0 : p.textMuted()) : (van ? 0xFFFFFFFF : p.text());
            g.drawString(font, tag.name, cx - tag.nameW / 2, y + 2, Colors.scaleAlpha(Colors.lerp(ink, van ? 0xFFFFFFFF : p.accent(), sel ? 0.55f : 0f), a), true);
            if (tag.lineW > 0) {
                final int lx = cx - (tag.lineW + 7) / 2;
                SlateDraw.rect(g, lx, y + 14, 4, 4, Colors.scaleAlpha(f.lineColor, a));
                g.drawString(font, tag.line, lx + 7, y + 12, Colors.scaleAlpha(van ? 0xFFD0D0D0 : p.textMuted(), a), false);
            }
        }
        g.disableScissor();
    }

    void close() {
        figures.clear();
        stage.close();
    }
}
