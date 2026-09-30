package dev.fallingcloud.slate.profile.client.ui;

import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.stage.Stage;
import dev.fallingcloud.slate.core.stage.StageFinish;
import dev.fallingcloud.slate.core.stage.StageRenderContext;
import dev.fallingcloud.slate.core.stage.StageSoft;
import dev.fallingcloud.slate.core.stage.node.PlayerNode;
import dev.fallingcloud.slate.core.stage.node.StageNode;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateSounds;
import dev.fallingcloud.slate.core.widget.SlateTooltips;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import dev.fallingcloud.slate.profile.Slot;
import dev.fallingcloud.slate.profile.client.Cosmetic;
import dev.fallingcloud.slate.profile.client.render.CosmeticRenderer;
import dev.fallingcloud.slate.profile.client.render.LookNode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The row of boxes under the player in the edit view: what there is for the chosen slot, each thing in a box of its
 * own, in 3D. A hat turns in its box; a shirt is shown on a plain figure, cut down to the part it covers. One scene
 * lies over the whole row and holds all the things, so a row of twenty costs what a row of two does. The boxes are
 * as large as the screen has room for ({@link #boxFor}); a row that does not fill the width stands in its middle,
 * under the player. The wheel (or the arrows at the ends) moves a longer one; the pointer on a box lets the player
 * try the thing on, a click keeps it.
 */
final class Shelf extends SlateWidget {

    /** One box: a thing, or one of the things that are not a thing (nothing at all, a skin, a picture to bring). */
    static final class Entry {
        final String id;
        final Component label;
        @Nullable final Cosmetic cosmetic;
        @Nullable final Icon icon;
        @Nullable StageNode node;
        boolean chosen;
        final Anim hover = new Anim(0, 140, Ease.OUT_CUBIC);

        Entry(final String id, final Component label, @Nullable final Cosmetic cosmetic, @Nullable final Icon icon) {
            this.id = id;
            this.label = label;
            this.cosmetic = cosmetic;
            this.icon = icon;
        }
    }

    /** A built thing turning in its box. */
    private static final class ThingNode extends StageNode {
        private final Cosmetic cosmetic;

        ThingNode(final Cosmetic cosmetic) {
            this.cosmetic = cosmetic;
            bounds(-0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f);
            pickable(false);
            hoverFeel(0f, 1f);
            spin(34f);
        }

        @Override
        protected void draw(final StageRenderContext ctx) {
            if (cosmetic.translucent()) return;
            paint(ctx);
        }

        @Override
        public boolean hasTranslucentPass() { return cosmetic.translucent(); }

        @Override
        protected void drawTranslucent(final StageRenderContext ctx) {
            paint(ctx);
        }

        private void paint(final StageRenderContext ctx) {
            final float age = ctx.timeMs / 50f;
            if (ctx.soft) {
                final BufferBuilder bb = StageSoft.begin(StageSoft.ENTITY);
                CosmeticRenderer.drawAlone(ctx.pose, cosmetic, age, ctx.light, 0xFFFFFFFF, c -> bb);
                StageSoft.end(bb, StageSoft.ENTITY, cosmetic.texture(), cosmetic.translucent() ? Math.min(0.999f, alpha()) : alpha(), false);
            } else {
                final int color = (Math.round(alpha() * 255f) << 24) | 0xFFFFFF;
                CosmeticRenderer.drawAlone(ctx.pose, cosmetic, age, ctx.light, color, c -> ctx.buffers.getBuffer(RenderType.entityTranslucent(c.texture())));
            }
        }
    }

    static final int GAP = 4;
    /** Under a box: the name of what it holds. */
    static final int LABEL = 12;
    /** Blocks of the scene from the top of the row to its foot. */
    private static final float TALL = 1.24f;

    /** The side of a box on a screen this high: a seventh of it, within what still reads and what still fits. */
    static int boxFor(final int screenHeight) {
        return Mth.clamp(screenHeight / 7, 44, 72);
    }

    /** The side of a box. */
    private final int box;
    private final Stage stage;
    private final List<Entry> entries = new ArrayList<>();
    private final Consumer<Entry> onPick;
    private final Consumer<Entry> onHover;
    private final Anim scroll = new Anim(0, 200, Ease.OUT_CUBIC);
    @Nullable private Entry hovered;

    Shelf(final Screen owner, final int x, final int y, final int width, final int box, final Consumer<Entry> onPick, final Consumer<Entry> onHover) {
        super(x, y, width, box + LABEL, Component.empty());
        this.box = box;
        this.onPick = onPick;
        this.onHover = onHover;
        final Stage s = new Stage().bind(owner);
        this.stage = s;
        s.background(0);
        s.soft(true);
        s.finish(StageFinish.glow());
        s.resolutionScale(Minecraft.getInstance().getWindow().getWidth() <= 2048 ? 2f : 1.5f);
        s.focusOutline(false);
        s.lighting().key(1.5f, 3.5f, 6f).ambientColor(0.46f, 0.45f, 0.47f).sun(0.3f, 1f, 0.7f);
        silent();
    }

    Stage stage() { return stage; }

    List<Entry> entries() { return entries; }

    /** Puts these in the boxes, the row back at its start. */
    void show(final List<Entry> list) {
        for (final Entry e : entries) if (e.node != null) stage.remove(e.node);
        entries.clear();
        entries.addAll(list);
        for (final Entry e : entries) if (e.node != null) stage.add(e.node);
        scroll.snap(0f);
        hovered = null;
    }

    /** A box for a built thing. */
    static Entry thing(final Cosmetic c) {
        final Entry e = new Entry(c.id(), c.name(), c, null);
        e.node = new ThingNode(c);
        return e;
    }

    /** A box for a painted thing: a plain figure wearing it, only the part it covers. */
    static Entry painted(final Cosmetic c, final boolean slim) {
        final Entry e = new Entry(c.id(), c.name(), c, null);
        final LookNode figure = LookNode.sample(c, slim);
        figure.show(parts(c.slot()));
        figure.pickable(false);
        figure.lookAtCursor(false);
        figure.breathe(false);
        figure.hoverFeel(0f, 1f);
        e.node = figure;
        return e;
    }

    /** A box for a skin: the whole figure in it. */
    static Entry skin(final String id, final Component label, final ResourceLocation texture, final boolean slim) {
        final Entry e = new Entry(id, label, null, null);
        final LookNode figure = LookNode.ofTexture(texture, slim);
        figure.pickable(false);
        figure.lookAtCursor(false);
        figure.breathe(false);
        figure.hoverFeel(0f, 1f);
        e.node = figure;
        return e;
    }

    /** A box with a sign in it and nothing in 3D: "nothing", "bring a picture". */
    static Entry sign(final String id, final Component label, final Icon icon) {
        return new Entry(id, label, null, icon);
    }

    private static Set<PlayerNode.Part> parts(final Slot slot) {
        return switch (slot) {
            case SHIRT -> EnumSet.of(PlayerNode.Part.BODY, PlayerNode.Part.RIGHT_ARM, PlayerNode.Part.LEFT_ARM, PlayerNode.Part.HEAD);
            case PANTS -> EnumSet.of(PlayerNode.Part.RIGHT_LEG, PlayerNode.Part.LEFT_LEG, PlayerNode.Part.BODY);
            case RIGHT_ARM -> EnumSet.of(PlayerNode.Part.RIGHT_ARM);
            case LEFT_ARM -> EnumSet.of(PlayerNode.Part.LEFT_ARM);
            case RIGHT_LEG -> EnumSet.of(PlayerNode.Part.RIGHT_LEG);
            case LEFT_LEG -> EnumSet.of(PlayerNode.Part.LEFT_LEG);
            case FACE, HAT -> EnumSet.of(PlayerNode.Part.HEAD);
            default -> EnumSet.allOf(PlayerNode.Part.class);
        };
    }

    /** Where the middle of what a figure shows lies, and how tall that is, in blocks of the figure. */
    private static float[] frame(final Entry e) {
        if (e.cosmetic == null) return new float[] {0f, 0.93f, 1.95f};
        return switch (e.cosmetic.slot()) {
            case SHIRT -> new float[] {0f, 1.2f, 1.25f};
            case PANTS -> new float[] {0f, 0.52f, 1.2f};
            case RIGHT_ARM -> new float[] {-0.36f, 1.12f, 0.95f};
            case LEFT_ARM -> new float[] {0.36f, 1.12f, 0.95f};
            case RIGHT_LEG -> new float[] {-0.12f, 0.38f, 0.95f};
            case LEFT_LEG -> new float[] {0.12f, 0.38f, 0.95f};
            case FACE, HAT -> new float[] {0f, 1.62f, 0.72f};
            default -> new float[] {0f, 0.93f, 1.95f};
        };
    }

    private int pitch() { return box + GAP; }

    /** How wide the row of boxes is. */
    private int rowWidth() { return Math.max(0, entries.size() * pitch() - GAP); }

    private int maxScroll() { return Math.max(0, rowWidth() - getWidth()); }

    private int boxX(final int index) {
        // A row shorter than the shelf stands in its middle.
        final int lead = Math.max(0, (getWidth() - rowWidth()) / 2);
        return getX() + lead + index * pitch() - Math.round(scroll.get());
    }

    @Nullable
    private Entry at(final double mx, final double my) {
        if (my < getY() || my >= getY() + box || mx < getX() || mx >= getX() + getWidth()) return null;
        for (int i = 0; i < entries.size(); i++) {
            final int bx = boxX(i);
            if (mx >= bx && mx < bx + box) return entries.get(i);
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (!isMouseOver(mouseX, mouseY) || maxScroll() <= 0) return false;
        final double by = scrollY != 0 ? scrollY : -scrollX;
        scroll.set(Mth.clamp(scroll.target() - (float) by * pitch(), 0f, maxScroll()));
        return true;
    }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        final Entry e = at(mouseX, mouseY);
        if (e == null) return;
        SlateSounds.click();
        onPick.accept(e);
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, mouseX, mouseY, partialTick, false);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, mouseX, mouseY, partialTick, true);
    }

    private void draw(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick, final boolean van) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        if (a <= 0.004f) return;
        final int x = getX(), y = getY() + enterOffset(), w = getWidth();
        final Entry over = isHovered() ? at(mouseX, mouseY) : null;
        if (over != hovered) {
            hovered = over;
            onHover.accept(over);
        }
        g.enableScissor(x, y - 2, x + w, y + box + 14);
        // The boxes.
        for (int i = 0; i < entries.size(); i++) {
            final Entry e = entries.get(i);
            final int bx = boxX(i);
            if (bx + box < x || bx > x + w) continue;
            e.hover.set(e == over);
            final float h = e.hover.get();
            final int by = y - Math.round(h * 1.5f);
            if (van) {
                g.fill(bx, by, bx + box, by + box, Colors.scaleAlpha(Colors.lerp(0x70000000, 0x90303030, h), a));
                SlateDraw.outline(g, bx, by, box, box, Colors.scaleAlpha(e.chosen ? 0xFFFFFFFF : Colors.lerp(0xFF000000, 0xFFC0C0C0, h), a), 0);
            } else {
                SlateDraw.pixelRound(g, bx, by, box, box, Colors.scaleAlpha(Colors.lerp(Colors.withAlpha(p.surface(), 0xB0), Colors.withAlpha(p.surfaceHover(), 0xE8), h), a), t.radius());
                if (e.chosen) SlateDraw.vgradient(g, bx + 1, by + box / 2, box - 2, box / 2 - 1, Colors.withAlpha(p.accent(), 0), Colors.scaleAlpha(Colors.withAlpha(p.accent(), 0x50), a));
                SlateDraw.outline(g, bx, by, box, box, Colors.scaleAlpha(e.chosen ? p.accent() : Colors.lerp(Colors.withAlpha(p.border(), 0xB0), p.borderStrong(), h), a), t.radius());
            }
            if (e.icon != null) Icons.draw(g, e.icon, bx + (box - 16) / 2, by + (box - 16) / 2, 16, Colors.scaleAlpha(van ? 0xFFE0E0E0 : Colors.lerp(p.textMuted(), p.text(), h), a));
        }
        g.disableScissor();

        // The things, all in one scene laid over the row.
        place(w);
        g.enableScissor(x, y - 2, x + w, y + box + 2);
        stage.alpha(a);
        stage.render(g, x, y - 2, w, box + 2, -1e9, -1e9, partialTick);
        g.disableScissor();

        // What the box under the pointer holds, by name; the name of what is chosen stands under its box.
        g.enableScissor(x, y, x + w, y + box + 14);
        for (int i = 0; i < entries.size(); i++) {
            final Entry e = entries.get(i);
            if (!e.chosen && e != over) continue;
            final int bx = boxX(i);
            final var label = SlateDraw.truncate(e.label, Math.min(getWidth(), 150));
            final int lw = SlateDraw.font().width(label);
            final int lx = Mth.clamp(bx + box / 2 - lw / 2, x, Math.max(x, x + w - lw));
            if (e == over || over == null) g.drawString(SlateDraw.font(), label, lx, y + box + 3, Colors.scaleAlpha(e.chosen && !van ? p.accent() : van ? 0xFFFFFFFF : p.text(), a), van);
        }
        g.disableScissor();
        // Arrows where the row goes on.
        final float sc = scroll.get();
        if (sc > 1f) Icons.draw(g, Icon.CHEVRON_LEFT, x + 1, y + box / 2 - 5, 10, Colors.scaleAlpha(van ? 0xFFFFFFFF : p.accent(), a));
        if (sc < maxScroll() - 1f) Icons.draw(g, Icon.CHEVRON_RIGHT, x + w - 11, y + box / 2 - 5, 10, Colors.scaleAlpha(van ? 0xFFFFFFFF : p.accent(), a));
        if (over != null && SlateDraw.font().width(over.label) > Math.min(getWidth(), 150)) SlateTooltips.request(over.label, this);
    }

    /** Sets every thing where its box is, and the camera so that a block of the scene is a box of the row. */
    private void place(final int width) {
        final float viewTall = TALL * (box + 2) / (float) box;
        final float fov = 9f;
        final float dist = viewTall / (2f * (float) Math.tan(Math.toRadians(fov / 2f)));
        stage.camera().at(0f, 0f, dist).lookAt(0f, 0f, 0f).fov(fov).clip(0.05f, 60f);
        final float perPixel = viewTall / (box + 2);
        for (int i = 0; i < entries.size(); i++) {
            final Entry e = entries.get(i);
            if (e.node == null) continue;
            final int bx = boxX(i);
            final boolean seen = bx + box > getX() - box && bx < getX() + width + box;
            e.node.visible(seen);
            if (!seen) continue;
            final float h = e.hover.get();
            final float cx = (bx + box / 2f - (getX() + width / 2f)) * perPixel;
            final float cy = (h * 1.5f - 1f) * perPixel;
            if (e.node instanceof LookNode figure) {
                final float[] f = frame(e);
                final float s = (0.96f + 0.06f * h) / f[2];
                figure.scale(s);
                figure.at(cx - f[0] * s, cy - f[1] * s, 0f);
                figure.yaw(e.cosmetic == null ? 18f : 24f + (e.hover.get() > 0.01f ? Mth.sin(System.nanoTime() / 1.0e9f * 1.6f) * 22f * h : 0f));
            } else {
                e.node.scale(0.86f + 0.1f * h);
                e.node.at(cx, cy, 0f);
            }
        }
    }

    void close() {
        entries.clear();
        stage.close();
    }
}
