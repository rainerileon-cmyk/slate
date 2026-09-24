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

    // ---- container screens (ContainerReskin): the panel that replaces the texture, and what the texture used to hold.
    // Painted with the CONTAINER palette (the dark one, in either skin) and the configured radius, so the container-style
    // switch is independent of the menu style. The look is the build menu's: a hard shadow, a near-opaque layered
    // panel with a hairline border and a top highlight, sunken slot wells with a pixel bevel, and an accent hover ring.

    /** A container screen's body in place of its background texture. */
    public static void containerPanel(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        final Theme t = Theme.current();
        final Palette p = t.containerPalette();
        final int r = t.containerRadius();
        containerShadow(g, x, y, w, h, r, p);
        SlateDraw.pixelRound(g, x, y, w, h, Colors.withAlpha(p.bg(), 0xF6), r);
        // A slightly lifted band along the top edge: the panel reads as a plate, not a hole.
        SlateDraw.vgradient(g, x + r + 1, y + r + 1, w - 2 * r - 2, Math.min(24, h / 4), Colors.withAlpha(p.surface(), 0x70), 0);
        SlateDraw.hline(g, x + r + 1, y + 1, w - 2 * r - 2, Colors.withAlpha(0xFFFFFF, 0x0C));
        SlateDraw.outline(g, x, y, w, h, p.border(), r);
    }

    /** The 2 px hard offset shadow of a container panel (the theme's shadow uses the skin radius; this one the container's). */
    private static void containerShadow(final GuiGraphics g, final int x, final int y, final int w, final int h, final int r, final Palette p) {
        final int c = Colors.scaleAlpha(p.shadow(), 0.7f);
        SlateDraw.pixelRound(g, x + 2, y + h - r - 2, w, r + 4, c, r);
        SlateDraw.rect(g, x + w, y + 2, 2, h - r - 4, c);
    }

    /** One 18 x 18 slot well (the item is drawn on top by the screen): sunken, with a one-pixel bevel. */
    public static void containerSlot(final GuiGraphics g, final int x, final int y) {
        final Palette p = Theme.current().containerPalette();
        SlateDraw.pixelRound(g, x, y, 18, 18, Colors.brighten(p.bg(), -0.32f), 1);
        SlateDraw.outline(g, x, y, 18, 18, Colors.withAlpha(p.border(), 0xD0), 1);
        // Bevel: darker along the top and left, a hair lighter along the bottom and right.
        SlateDraw.hline(g, x + 1, y + 1, 16, Colors.withAlpha(0x000000, 0x48));
        SlateDraw.vline(g, x + 1, y + 2, 15, Colors.withAlpha(0x000000, 0x48));
        SlateDraw.hline(g, x + 2, y + 16, 15, Colors.withAlpha(0xFFFFFF, 0x0A));
        SlateDraw.vline(g, x + 16, y + 2, 14, Colors.withAlpha(0xFFFFFF, 0x0A));
    }

    /**
     * The hovered slot, in place of vanilla's flat white square: a soft white lift over the item and an accent ring
     * around the well. Coordinates are the slot's (the screen has translated the pose to its image corner).
     */
    public static void containerSlotHighlight(final GuiGraphics g, final int x, final int y, final int z) {
        final Palette p = Theme.current().containerPalette();
        g.pose().pushPose();
        g.pose().translate(0, 0, z);
        SlateDraw.rect(g, x, y, 16, 16, Colors.withAlpha(0xFFFFFF, 0x2C));
        SlateDraw.outline(g, x - 1, y - 1, 18, 18, Colors.withAlpha(p.accent(), 0xC8), 1);
        SlateDraw.outline(g, x - 2, y - 2, 20, 20, Colors.withAlpha(p.accent(), 0x40), 2);
        g.pose().popPose();
    }

    /** A sunken area (the player model's frame in the inventory). */
    public static void containerInset(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        final Palette p = Theme.current().containerPalette();
        SlateDraw.pixelRound(g, x, y, w, h, Colors.brighten(p.bg(), -0.32f), 2);
        SlateDraw.outline(g, x, y, w, h, Colors.withAlpha(p.border(), 0xD0), 2);
        SlateDraw.hline(g, x + 2, y + 1, w - 4, Colors.withAlpha(0x000000, 0x48));
        SlateDraw.vline(g, x + 1, y + 2, h - 4, Colors.withAlpha(0x000000, 0x48));
    }

    /** The "goes to" arrow between an input and its result (crafting grid, furnace). */
    public static void containerArrow(final GuiGraphics g, final int x, final int y) {
        Icons.draw(g, Icon.ARROW_RIGHT, x + 4, y, 14, Theme.current().containerPalette().textDim());
    }

    /** A screen that painted its body its own way: a dark veil with the panel's edge, so it still sits in the style. */
    public static void containerTint(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        final Theme t = Theme.current();
        final Palette p = t.containerPalette();
        final int r = t.containerRadius();
        SlateDraw.pixelRound(g, x, y, w, h, Colors.withAlpha(p.bg(), 0xD4), r);
        SlateDraw.outline(g, x, y, w, h, p.border(), r);
    }

    /** A creative inventory tab (the item is drawn on it by the screen): the selected one carries an accent cap. */
    public static void containerTab(final GuiGraphics g, final int x, final int y, final int w, final int h, final boolean selected) {
        final Palette p = Theme.current().containerPalette();
        SlateDraw.pixelRound(g, x, y, w, h, selected ? Colors.withAlpha(p.bg(), 0xF6) : Colors.withAlpha(p.bg2(), 0xE0), 2);
        SlateDraw.outline(g, x, y, w, h, selected ? p.borderStrong() : Colors.withAlpha(p.border(), 0xC0), 2);
        if (selected) SlateDraw.rect(g, x + 3, y + 1, w - 6, 2, p.accent());
    }

    /** The creative inventory's scroll thumb (its track is part of the panel). */
    public static void containerThumb(final GuiGraphics g, final int x, final int y, final int w, final int h, final boolean enabled) {
        final Palette p = Theme.current().containerPalette();
        SlateDraw.pixelRound(g, x, y, w, h, enabled ? p.borderStrong() : Colors.withAlpha(p.borderStrong(), 0x60), 1);
        if (enabled) SlateDraw.rect(g, x + 1, y + 1, Math.max(1, w - 2), 1, Colors.withAlpha(0xFFFFFF, 0x18));
    }

    /** Tooltip box around the text rect (vanilla's rect is the text bounds; the box is 4 px bigger + 1 px border). */
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
