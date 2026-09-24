package dev.fallingcloud.slate.core.screen.reskin;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

/**
 * The dark-skin replacements for vanilla drawing, called from the reskin mixins. Everything here draws
 * with {@link SlateDraw} primitives so vanilla widgets look exactly like their Slate counterparts.
 * Fills honour the shader colour vanilla sets before drawing a widget, so faded widgets (title screen)
 * fade with it.
 */
public final class ReskinDraw {

    // ------------------------------------------------------------------ state helpers

    /** Updates and returns the hover lift for a vanilla widget (hovered or keyboard-focused, and active). */
    public static float hover(final AbstractWidget w) {
        if (!(w instanceof ReskinState st)) return w.active && w.isHoveredOrFocused() ? 1f : 0f;
        st.slate$hoverAnim().set(w.active && w.isHoveredOrFocused());
        return st.slate$hoverAnim().get();
    }

    /** Updates and returns the press amount (hovered with the left button held). */
    public static float press(final AbstractWidget w) {
        final boolean down = w.active && w.isHovered() && Minecraft.getInstance().mouseHandler.isLeftPressed();
        if (!(w instanceof ReskinState st)) return down ? 1f : 0f;
        st.slate$pressAnim().set(down);
        return st.slate$pressAnim().get();
    }

    // ------------------------------------------------------------------ widgets

    /** Button face: the Slate SECONDARY look, plus the focus ring for keyboard focus. */
    public static void button(final GuiGraphics g, final AbstractWidget w, final int x, final int y, final int width, final int height,
                              final float hover, final float press) {
        SlateDraw.darkFace(g, x, y, width, height, hover, press, w.active, 1f);
        if (w.active && w.isFocused() && !w.isHovered()) SlateDraw.focusRing(g, x, y, width, height, 1f);
    }

    /** Slider track (the full widget face) with the filled portion up to {@code value}. */
    public static void sliderTrack(final GuiGraphics g, final int x, final int y, final int w, final int h, final float value,
                                   final float hover, final boolean active) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final int fill = active ? Colors.lerp(p.bg2(), p.surface(), hover) : Colors.withAlpha(p.bg2(), 0x80);
        final int border = active ? Colors.lerp(p.border(), p.borderStrong(), hover) : Colors.withAlpha(p.border(), 0x80);
        SlateDraw.pixelRound(g, x, y, w, h, fill, t.radius());
        final int filled = Math.round(4 + (w - 8) * Math.max(0f, Math.min(1f, value)));
        SlateDraw.pixelRound(g, x, y, Math.max(4, filled), h, Colors.withAlpha(active ? p.accent() : p.textDim(), 0x38), t.radius());
        SlateDraw.outline(g, x, y, w, h, border, t.radius());
    }

    /** Slider handle: 8 px wide knob. */
    public static void sliderHandle(final GuiGraphics g, final int x, final int y, final int w, final int h, final float hover, final boolean active) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final int r = Math.min(2, t.radius());
        SlateDraw.pixelRound(g, x, y, w, h, active ? Colors.lerp(p.surfaceActive(), p.textDim(), hover * 0.4f) : p.surface(), r);
        SlateDraw.outline(g, x, y, w, h, active ? Colors.lerp(p.borderStrong(), p.accent(), hover) : p.border(), r);
        SlateDraw.rect(g, x + w / 2 - 1, y + h / 2 - 3, 2, 6, Colors.withAlpha(active ? Colors.lerp(p.textMuted(), p.accent(), hover) : p.textDim(), 0xE0));
    }

    /** Text field frame. */
    public static void textField(final GuiGraphics g, final int x, final int y, final int w, final int h, final boolean focused,
                                 final float hover, final boolean active) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        int fill = focused ? p.surface() : Colors.lerp(p.bg2(), p.surface(), hover);
        int border = focused ? p.accent() : Colors.lerp(p.border(), p.borderStrong(), hover);
        if (!active) { fill = Colors.withAlpha(p.bg2(), 0x80); border = Colors.withAlpha(p.border(), 0x80); }
        SlateDraw.pixelRound(g, x, y, w, h, fill, t.radius());
        SlateDraw.outline(g, x, y, w, h, border, t.radius());
    }

    /** Checkbox box (vanilla's 17 px cell) with the check glyph. */
    public static void checkbox(final GuiGraphics g, final int x, final int y, final int size, final boolean selected, final float hover,
                                final boolean focused, final boolean active) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final int box = Math.max(8, size - 4);
        final int bx = x + (size - box) / 2, by = y + (size - box) / 2;
        int fill = selected ? p.accent() : Colors.lerp(p.surface(), p.surfaceHover(), hover);
        int border = selected ? p.accentHover() : Colors.lerp(p.borderStrong(), p.textDim(), hover);
        if (!active) { fill = Colors.withAlpha(fill, 0x70); border = Colors.withAlpha(border, 0x70); }
        SlateDraw.pixelRound(g, bx, by, box, box, fill, 2);
        SlateDraw.outline(g, bx, by, box, box, border, 2);
        if (selected) Icons.draw(g, Icon.CHECK, bx + (box - 8) / 2, by + (box - 8) / 2, 8, p.accentText());
        if (focused) SlateDraw.focusRing(g, bx, by, box, box, 1f);
    }

    // ------------------------------------------------------------------ lists

    public static void listBackground(final GuiGraphics g, final int x, final int y, final int w, final int h, final boolean inWorld) {
        final Palette p = Theme.current().palette();
        SlateDraw.rect(g, x, y, w, h, inWorld ? Colors.withAlpha(p.bg2(), 0xB0) : p.bg2());
    }

    public static void listSeparators(final GuiGraphics g, final int x, final int top, final int w, final int bottom, final boolean inWorld) {
        final Palette p = Theme.current().palette();
        SlateDraw.hline(g, x, top - 1, w, p.border());
        SlateDraw.hline(g, x, bottom, w, p.border());
    }

    /** Row selection: filled surface with a 2 px accent bar (focused lists get an accent outline). */
    public static void selection(final GuiGraphics g, final int x, final int y, final int w, final int h, final boolean focused) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        SlateDraw.pixelRound(g, x, y, w, h, p.surfaceActive(), t.radius());
        SlateDraw.outline(g, x, y, w, h, focused ? Colors.withAlpha(p.accent(), 0xA0) : p.borderStrong(), t.radius());
        SlateDraw.rect(g, x, y + 2, 2, h - 4, p.accent());
    }

    /** Vanilla's 6 px scroller background: a thin 4 px track. */
    public static void scrollTrack(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        final Palette p = Theme.current().palette();
        SlateDraw.pixelRound(g, x + 1, y, 4, h, Colors.withAlpha(p.surface(), 0x80), 1);
    }

    /** Vanilla's 6 px scroller thumb: a thin 4 px thumb. */
    public static void scrollThumb(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        final Palette p = Theme.current().palette();
        SlateDraw.pixelRound(g, x + 1, y, 4, h, p.borderStrong(), 1);
    }

    // ------------------------------------------------------------------ backgrounds

    /** Replaces the stone/dirt tile: solid bg + vignette out of a world, the translucent overlay in one. */
    public static void menuBackground(final GuiGraphics g, final int x, final int y, final int w, final int h, final boolean inWorld) {
        final Theme t = Theme.current();
        if (inWorld) {
            SlateDraw.rect(g, x, y, w, h, t.palette().overlay());
        } else {
            SlateDraw.rect(g, x, y, w, h, t.bg());
            SlateDraw.vignette(g, x, y, w, h, 0.3f);
        }
    }

    /** Replaces the transparent gradient some screens use. */
    public static void transparentBackground(final GuiGraphics g, final int w, final int h) {
        SlateDraw.rect(g, 0, 0, w, h, Colors.withAlpha(Theme.current().bg(), 0xC8));
    }

    /** Veil over the title-screen panorama: keeps it, darkens the edges so Slate's UI reads on top. */
    public static void panoramaVeil(final GuiGraphics g, final int w, final int h, final float fade) {
        if (fade <= 0.01f) return;
        SlateDraw.rect(g, 0, 0, w, h, Colors.withAlpha(Theme.current().bg(), Math.round(0x40 * fade)));
        SlateDraw.vignette(g, 0, 0, w, h, 0.5f * fade);
    }

    // ------------------------------------------------------------------ tooltips & tabs

    /** Tooltip box around the text rect (vanilla's rect is the text bounds; the box is 3 px bigger + 1 px border). */
    // ---- container screens (ContainerReskin): the panel that replaces the texture, and what the texture used to hold

    /** A container screen's body in place of its background texture. */
    public static void containerPanel(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        final Palette p = Theme.current().palette();
        SlateDraw.panel(g, x, y, w, h, p.surface(), p.borderStrong());
    }

    /** One 18 x 18 slot well (the item is drawn on top by the screen). */
    public static void containerSlot(final GuiGraphics g, final int x, final int y) {
        final Palette p = Theme.current().palette();
        SlateDraw.pixelRound(g, x, y, 18, 18, p.bg2(), 1);
        SlateDraw.outline(g, x, y, 18, 18, p.border(), 1);
    }

    /** A sunken area (the player model's frame in the inventory). */
    public static void containerInset(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        final Palette p = Theme.current().palette();
        SlateDraw.pixelRound(g, x, y, w, h, p.bg(), 2);
        SlateDraw.outline(g, x, y, w, h, p.border(), 2);
    }

    /** The "goes to" arrow between an input and its result (crafting grid, furnace). */
    public static void containerArrow(final GuiGraphics g, final int x, final int y) {
        Icons.draw(g, Icon.ARROW_RIGHT, x + 4, y, 14, Theme.current().palette().textDim());
    }

    /** A screen that painted its body its own way: a dark veil over it, so it still sits in the dark skin. */
    public static void containerTint(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        final Palette p = Theme.current().palette();
        SlateDraw.pixelRound(g, x, y, w, h, Colors.withAlpha(p.bg(), 0xD0), Theme.current().radius());
        SlateDraw.outline(g, x, y, w, h, p.borderStrong(), Theme.current().radius());
    }

    /** A creative inventory tab (the item is drawn on it by the screen). */
    public static void containerTab(final GuiGraphics g, final int x, final int y, final int w, final int h, final boolean selected) {
        final Palette p = Theme.current().palette();
        SlateDraw.pixelRound(g, x, y, w, h, selected ? p.surface() : p.bg2(), 2);
        SlateDraw.outline(g, x, y, w, h, selected ? p.borderStrong() : p.border(), 2);
    }

    /** The creative inventory's scroll thumb (its track is part of the panel). */
    public static void containerThumb(final GuiGraphics g, final int x, final int y, final int w, final int h, final boolean enabled) {
        final Palette p = Theme.current().palette();
        SlateDraw.pixelRound(g, x, y, w, h, enabled ? p.textDim() : Colors.withAlpha(p.textDim(), 0x60), 1);
    }

    public static void tooltipBackground(final GuiGraphics g, final int x, final int y, final int w, final int h, final int z) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final int bx = x - 4, by = y - 4, bw = w + 8, bh = h + 8;
        g.pose().pushPose();
        g.pose().translate(0, 0, z);
        SlateDraw.shadow(g, bx, by, bw, bh, 0.6f);
        SlateDraw.pixelRound(g, bx, by, bw, bh, Colors.withAlpha(p.surfaceActive(), 0xF4), t.radius());
        SlateDraw.outline(g, bx, by, bw, bh, p.borderStrong(), t.radius());
        g.pose().popPose();
    }

    /** The bar behind a {@code TabNavigationBar}. */
    public static void tabBar(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        final Palette p = Theme.current().palette();
        SlateDraw.rect(g, x, y, w, h, p.bg2());
        SlateDraw.hline(g, x, y + h - 1, w, p.border());
    }

    /** One tab: the selected one is a raised surface merging into the page, others are flat labels. */
    public static void tab(final GuiGraphics g, final int x, final int y, final int w, final int h, final boolean selected,
                           final float hover, final boolean focused) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final int r = t.radius();
        if (selected) {
            SlateDraw.pixelRound(g, x, y + 2, w, h - 2, p.surface(), r);
            SlateDraw.outline(g, x, y + 2, w, h - 2, p.borderStrong(), r);
            SlateDraw.rect(g, x + 1, y + h - 1, w - 2, 1, p.surface());          // open the bottom edge into the page
            SlateDraw.rect(g, x + r + 1, y + 2, w - r * 2 - 2, 2, p.accent());   // accent cap
        } else if (hover > 0.01f) {
            SlateDraw.pixelRound(g, x + 1, y + 4, w - 2, h - 6, Colors.scaleAlpha(p.surfaceHover(), hover), r);
        }
        if (focused) SlateDraw.rect(g, x + 4, y + h - 3, w - 8, 1, p.accent());
    }

    private ReskinDraw() {}
}
