package dev.fallingcloud.slate.core.gfx;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Drawing primitives shared by every widget and screen. Two families: the pixel-stepped dark look
 * ({@link #pixelRound}, {@link #panel}, {@link #shadow}) and vanilla sprite helpers
 * ({@link #vanillaButton}, {@link #vanillaPanel}, {@link #vanillaListBackground}). All colours ARGB.
 * Fills use {@code GuiGraphics.fill}, which blends; sprite blits are wrapped in enableBlend because
 * {@code GuiGraphics.blit} does NOT enable blending on its own.
 *
 * <p>Text metrics: the vanilla font has {@code lineHeight} 9 with glyph bodies in rows 0-7, so a
 * single line is centred in a box of height {@code h} at {@link #textY}{@code (y, h)} - the same
 * formula vanilla's own widgets use, so Slate widgets line up with untouched vanilla ones.</p>
 */
public final class SlateDraw {

    // Vanilla sprites (1.21.1 names).
    public static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("widget/button");
    public static final ResourceLocation BUTTON_DISABLED = ResourceLocation.withDefaultNamespace("widget/button_disabled");
    public static final ResourceLocation BUTTON_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/button_highlighted");
    public static final ResourceLocation SLIDER = ResourceLocation.withDefaultNamespace("widget/slider");
    public static final ResourceLocation SLIDER_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/slider_highlighted");
    public static final ResourceLocation SLIDER_HANDLE = ResourceLocation.withDefaultNamespace("widget/slider_handle");
    public static final ResourceLocation SLIDER_HANDLE_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/slider_handle_highlighted");
    public static final ResourceLocation TEXT_FIELD = ResourceLocation.withDefaultNamespace("widget/text_field");
    public static final ResourceLocation TEXT_FIELD_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/text_field_highlighted");
    public static final ResourceLocation CHECKBOX = ResourceLocation.withDefaultNamespace("widget/checkbox");
    public static final ResourceLocation CHECKBOX_SELECTED = ResourceLocation.withDefaultNamespace("widget/checkbox_selected");
    public static final ResourceLocation CHECKBOX_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/checkbox_highlighted");
    public static final ResourceLocation CHECKBOX_SELECTED_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/checkbox_selected_highlighted");
    public static final ResourceLocation SCROLLER = ResourceLocation.withDefaultNamespace("widget/scroller");
    public static final ResourceLocation SCROLLER_BACKGROUND = ResourceLocation.withDefaultNamespace("widget/scroller_background");
    public static final ResourceLocation TAB = ResourceLocation.withDefaultNamespace("widget/tab");
    public static final ResourceLocation TAB_SELECTED = ResourceLocation.withDefaultNamespace("widget/tab_selected");
    public static final ResourceLocation TAB_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/tab_highlighted");
    public static final ResourceLocation TAB_SELECTED_HIGHLIGHTED = ResourceLocation.withDefaultNamespace("widget/tab_selected_highlighted");
    public static final ResourceLocation MENU_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/menu_background.png");
    public static final ResourceLocation INWORLD_MENU_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/inworld_menu_background.png");
    public static final ResourceLocation MENU_LIST_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/menu_list_background.png");
    public static final ResourceLocation INWORLD_MENU_LIST_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/inworld_menu_list_background.png");
    public static final ResourceLocation HEADER_SEPARATOR = ResourceLocation.withDefaultNamespace("textures/gui/header_separator.png");
    public static final ResourceLocation FOOTER_SEPARATOR = ResourceLocation.withDefaultNamespace("textures/gui/footer_separator.png");
    public static final ResourceLocation INWORLD_HEADER_SEPARATOR = ResourceLocation.withDefaultNamespace("textures/gui/inworld_header_separator.png");
    public static final ResourceLocation INWORLD_FOOTER_SEPARATOR = ResourceLocation.withDefaultNamespace("textures/gui/inworld_footer_separator.png");

    private static final String ELLIPSIS = "...";

    public static Font font() { return Minecraft.getInstance().font; }

    // ------------------------------------------------------------------ fills

    /** Plain rectangle. */
    public static void rect(final GuiGraphics g, final int x, final int y, final int w, final int h, final int color) {
        if (w <= 0 || h <= 0 || Colors.alpha(color) == 0) return;
        g.fill(x, y, x + w, y + h, color);
    }

    /**
     * Rectangle with pixel-stepped corners. Radius r cuts the corner in r steps of one pixel each
     * (r=3 -> a 3-pixel staircase), the signature "modern but pixel" shape. r=0 is a plain rect.
     */
    public static void pixelRound(final GuiGraphics g, final int x, final int y, final int w, final int h,
                                  final int color, final int radius) {
        if (w <= 0 || h <= 0 || Colors.alpha(color) == 0) return;
        final int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) { g.fill(x, y, x + w, y + h, color); return; }
        // Body between the corner zones, then one row per corner step, shrinking inward.
        g.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            final int inset = r - i;                       // top row i is inset by (r - i)
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    /** 1 px outline with the same stepped corners (matches {@link #pixelRound} pixel for pixel). */
    public static void outline(final GuiGraphics g, final int x, final int y, final int w, final int h,
                               final int color, final int radius) {
        if (w <= 0 || h <= 0 || Colors.alpha(color) == 0) return;
        final int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        g.fill(x + r, y, x + w - r, y + 1, color);                    // top
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);            // bottom
        g.fill(x, y + r, x + 1, y + h - r, color);                    // left
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);            // right
        for (int i = 1; i <= r; i++) {
            // Diagonal steps: pixel at (r - i, i - 1) etc. for each corner.
            final int px = r - i, py = i - 1;
            g.fill(x + px, y + py + 1, x + px + 1, y + py + 2, color);
            g.fill(x + w - 1 - px, y + py + 1, x + w - px, y + py + 2, color);
            g.fill(x + px, y + h - 2 - py, x + px + 1, y + h - 1 - py, color);
            g.fill(x + w - 1 - px, y + h - 2 - py, x + w - px, y + h - 1 - py, color);
        }
    }

    /** Filled panel + border using the theme radius. */
    public static void panel(final GuiGraphics g, final int x, final int y, final int w, final int h,
                             final int fill, final int border) {
        final int r = Theme.current().radius();
        pixelRound(g, x, y, w, h, fill, r);
        outline(g, x, y, w, h, border, r);
    }

    /** Hard 2 px offset shadow under a panel (pixel look, no blur). */
    public static void shadow(final GuiGraphics g, final int x, final int y, final int w, final int h, final float alpha) {
        if (alpha <= 0.01f) return;
        final int c = Colors.scaleAlpha(Theme.current().palette().shadow(), alpha);
        final int r = Theme.current().radius();
        // Only the visible L-shaped part (right + bottom bands) so the fill never double-blends under the panel.
        pixelRound(g, x + 2, y + h - r - 2, w, r + 4, c, r);   // bottom band (with the stepped corners)
        rect(g, x + w, y + 2, 2, h - r - 4, c);                // right band
    }

    /**
     * A filled pixel circle (diameter {@code 2r+1}) centred on ({@code cx},{@code cy}). Midpoint rows,
     * so it stays crisp at any size: status dots, avatars' online badges, radio buttons.
     */
    public static void pixelCircle(final GuiGraphics g, final int cx, final int cy, final int r, final int color) {
        if (r < 0 || Colors.alpha(color) == 0) return;
        if (r == 0) { g.fill(cx, cy, cx + 1, cy + 1, color); return; }
        final float rr = r + 0.5f;
        for (int dy = -r; dy <= r; dy++) {
            final int dx = (int) Math.floor(Math.sqrt(rr * rr - dy * dy));
            g.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /** 1 px pixel ring (the outline of {@link #pixelCircle}). */
    public static void pixelRing(final GuiGraphics g, final int cx, final int cy, final int r, final int color) {
        if (r <= 0 || Colors.alpha(color) == 0) return;
        final float rr = r + 0.5f;
        int prev = -1;
        for (int dy = -r; dy <= r; dy++) {
            final int dx = (int) Math.floor(Math.sqrt(rr * rr - dy * dy));
            // Left/right edge pixels of this row, plus the run that connects to the previous row.
            final int from = prev < 0 ? dx : Math.min(dx, prev + 1);
            g.fill(cx - dx, cy + dy, cx - from + 1, cy + dy + 1, color);
            g.fill(cx + from, cy + dy, cx + dx + 1, cy + dy + 1, color);
            prev = dx;
        }
    }

    public static void hgradient(final GuiGraphics g, final int x, final int y, final int w, final int h, final int left, final int right) {
        // fillGradient is vertical only; draw in 4 px strips.
        if (w <= 0 || h <= 0) return;
        for (int i = 0; i < w; i += 4) {
            final int c = Colors.lerp(left, right, (float) i / Math.max(1, w - 1));
            g.fill(x + i, y, Math.min(x + w, x + i + 4), y + h, c);
        }
    }

    public static void vgradient(final GuiGraphics g, final int x, final int y, final int w, final int h, final int top, final int bottom) {
        if (w <= 0 || h <= 0) return;
        g.fillGradient(x, y, x + w, y + h, top, bottom);
    }

    /** Horizontal 1 px rule. */
    public static void hline(final GuiGraphics g, final int x, final int y, final int w, final int color) {
        if (w <= 0) return;
        g.fill(x, y, x + w, y + 1, color);
    }

    public static void vline(final GuiGraphics g, final int x, final int y, final int h, final int color) {
        if (h <= 0) return;
        g.fill(x, y, x + 1, y + h, color);
    }

    /**
     * Darkens the edges of an area: the dark skin's "panels read as lifted" background treatment and
     * the veil over panoramas. {@code strength} 0-1 scales the alpha (0.35 is the screen default).
     */
    public static void vignette(final GuiGraphics g, final int x, final int y, final int w, final int h, final float strength) {
        if (strength <= 0.01f || w <= 0 || h <= 0) return;
        final int a = Math.round(255 * Mth.clamp(strength, 0f, 1f));
        final int edge = Colors.withAlpha(0x000000, a), clear = 0;
        final int vh = Math.max(8, h / 4), hw = Math.max(8, w / 8);
        vgradient(g, x, y, w, vh, edge, clear);
        vgradient(g, x, y + h - vh, w, vh, clear, edge);
        hgradient(g, x, y, hw, h, Colors.withAlpha(0x000000, a / 2), clear);
        hgradient(g, x + w - hw, y, hw, h, clear, Colors.withAlpha(0x000000, a / 2));
    }

    // ------------------------------------------------------------------ text

    /** Y at which a single line of the vanilla font is vertically centred in a box of height {@code h}. */
    public static int textY(final int y, final int h) {
        return y + (h - 9) / 2 + 1;
    }

    public static void text(final GuiGraphics g, final Component text, final int x, final int y, final int color) {
        g.drawString(font(), text, x, y, color, false);
    }

    public static void text(final GuiGraphics g, final String text, final int x, final int y, final int color) {
        g.drawString(font(), text, x, y, color, false);
    }

    public static void text(final GuiGraphics g, final FormattedCharSequence text, final int x, final int y, final int color) {
        g.drawString(font(), text, x, y, color, false);
    }

    /** Text with or without shadow: the dark skin draws flat, the vanilla skin with vanilla's shadow. */
    public static void text(final GuiGraphics g, final Component text, final int x, final int y, final int color, final boolean shadow) {
        g.drawString(font(), text, x, y, color, shadow);
    }

    public static void textShadow(final GuiGraphics g, final Component text, final int x, final int y, final int color) {
        g.drawString(font(), text, x, y, color, true);
    }

    public static void textCentered(final GuiGraphics g, final Component text, final int cx, final int y, final int color) {
        final FormattedCharSequence seq = text.getVisualOrderText();
        g.drawString(font(), seq, cx - font().width(seq) / 2, y, color, false);
    }

    public static void textCentered(final GuiGraphics g, final String text, final int cx, final int y, final int color) {
        g.drawString(font(), text, cx - font().width(text) / 2, y, color, false);
    }

    public static void textCentered(final GuiGraphics g, final Component text, final int cx, final int y, final int color, final boolean shadow) {
        final FormattedCharSequence seq = text.getVisualOrderText();
        g.drawString(font(), seq, cx - font().width(seq) / 2, y, color, shadow);
    }

    public static void textRight(final GuiGraphics g, final Component text, final int rightX, final int y, final int color) {
        final FormattedCharSequence seq = text.getVisualOrderText();
        g.drawString(font(), seq, rightX - font().width(seq), y, color, false);
    }

    public static void textRight(final GuiGraphics g, final Component text, final int rightX, final int y, final int color, final boolean shadow) {
        final FormattedCharSequence seq = text.getVisualOrderText();
        g.drawString(font(), seq, rightX - font().width(seq), y, color, shadow);
    }

    /** Text with an ellipsis if wider than {@code width}; inner styles (colours, fonts) survive the cut. */
    public static FormattedCharSequence truncate(final Component text, final int width) {
        final Font f = font();
        if (f.width(text) <= width) return text.getVisualOrderText();
        final FormattedText cut = f.substrByWidth(text, Math.max(0, width - f.width(ELLIPSIS)));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, FormattedText.of(ELLIPSIS, text.getStyle())));
    }

    /**
     * Draws {@code text} inside {@code width}; when it does not fit it slides back and forth like
     * vanilla's {@code renderScrollingString} (long button labels, list rows). Clipped to the box.
     */
    public static void drawScrollingText(final GuiGraphics g, final Component text, final int x, final int y, final int width,
                                         final int color, final boolean shadow) {
        drawScrollingText(g, text.getVisualOrderText(), x, y, width, color, shadow);
    }

    public static void drawScrollingText(final GuiGraphics g, final FormattedCharSequence text, final int x, final int y, final int width,
                                         final int color, final boolean shadow) {
        final int tw = font().width(text);
        if (tw <= width) { g.drawString(font(), text, x, y, color, shadow); return; }
        final int overflow = tw - width;
        final double seconds = Util.getMillis() / 1000.0;
        final double period = Math.max(overflow * 0.5, 3.0);
        final double e = Math.sin((Math.PI / 2) * Math.cos((Math.PI * 2) * seconds / period)) / 2.0 + 0.5;
        final int offset = (int) Math.round(Mth.lerp(e, 0.0, overflow));
        g.enableScissor(x, y - 1, x + width, y + 10);
        g.drawString(font(), text, x - offset, y, color, shadow);
        g.disableScissor();
    }

    /** {@link #drawScrollingText} centred in the box when it fits, scrolling when it does not. */
    public static void drawScrollingTextCentered(final GuiGraphics g, final Component text, final int x, final int y, final int width,
                                                 final int color, final boolean shadow) {
        final FormattedCharSequence seq = text.getVisualOrderText();
        final int tw = font().width(seq);
        if (tw <= width) g.drawString(font(), seq, x + (width - tw) / 2, y, color, shadow);
        else drawScrollingText(g, seq, x, y, width, color, shadow);
    }

    public static int width(final Component text) { return font().width(text); }

    public static int width(final String text) { return font().width(text); }

    public static int width(final FormattedCharSequence text) { return font().width(text); }

    public static int lineHeight() { return font().lineHeight; }

    // ------------------------------------------------------------------ textures

    /** Blit a whole texture scaled to {@code w x h} with an alpha. */
    public static void blitScaled(final GuiGraphics g, final ResourceLocation texture, final int x, final int y, final int w, final int h,
                                  final int texW, final int texH, final float alpha) {
        blit(g, texture, x, y, w, h, 0, 0, texW, texH, texW, texH, alpha);
    }

    /** Blit a region ({@code u,v,uw,vh}) of a {@code texW x texH} texture into {@code x,y,w,h} with an alpha. */
    public static void blit(final GuiGraphics g, final ResourceLocation texture, final int x, final int y, final int w, final int h,
                            final float u, final float v, final int uw, final int vh, final int texW, final int texH, final float alpha) {
        if (alpha <= 0.004f || w <= 0 || h <= 0) return;
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, alpha);
        g.blit(texture, x, y, w, h, u, v, uw, vh, texW, texH);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /**
     * Blit a whole texture into a box keeping its aspect ratio: {@code cover} fills the box (cropped),
     * otherwise the image is letterboxed (centred).
     */
    public static void blitFit(final GuiGraphics g, final ResourceLocation texture, final int x, final int y, final int w, final int h,
                               final int texW, final int texH, final boolean cover, final float alpha) {
        if (texW <= 0 || texH <= 0 || w <= 0 || h <= 0) return;
        final float s = cover ? Math.max((float) w / texW, (float) h / texH) : Math.min((float) w / texW, (float) h / texH);
        final int dw = Math.max(1, Math.round(texW * s)), dh = Math.max(1, Math.round(texH * s));
        final int dx = x + (w - dw) / 2, dy = y + (h - dh) / 2;
        if (cover) g.enableScissor(x, y, x + w, y + h);
        blitScaled(g, texture, dx, dy, dw, dh, texW, texH, alpha);
        if (cover) g.disableScissor();
    }

    /** A player's face (with the hat layer) from a skin texture, {@code size} px square. */
    public static void playerHead(final GuiGraphics g, final ResourceLocation skin, final int x, final int y, final int size, final float alpha) {
        if (alpha <= 0.004f) return;
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, alpha);
        PlayerFaceRenderer.draw(g, skin, x, y, size, true, false);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------ vanilla skin

    /** A vanilla stone button face, animated between normal and highlighted by {@code hover} (0-1). */
    public static void vanillaButton(final GuiGraphics g, final int x, final int y, final int w, final int h,
                                     final float hover, final boolean enabled, final float alpha) {
        vanillaFace(g, BUTTON, BUTTON_HIGHLIGHTED, BUTTON_DISABLED, x, y, w, h, hover, enabled, alpha);
    }

    /** Any base/highlighted/disabled sprite triple cross-faded by {@code hover}. */
    public static void vanillaFace(final GuiGraphics g, final ResourceLocation base, final ResourceLocation highlighted,
                                   final ResourceLocation disabled, final int x, final int y, final int w, final int h,
                                   final float hover, final boolean enabled, final float alpha) {
        if (alpha <= 0.004f || w <= 0 || h <= 0) return;
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, alpha);
        if (!enabled && disabled != null) {
            g.blitSprite(disabled, x, y, w, h);
        } else {
            g.blitSprite(base, x, y, w, h);
            if (hover > 0.01f && highlighted != null) {
                g.setColor(1f, 1f, 1f, alpha * Math.min(1f, hover));
                g.blitSprite(highlighted, x, y, w, h);
            }
        }
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /** Vanilla slider track + handle with the hover cross-fade. {@code handleX} is absolute. */
    public static void vanillaSlider(final GuiGraphics g, final int x, final int y, final int w, final int h, final int handleX,
                                     final float hover, final boolean enabled, final float alpha) {
        vanillaFace(g, SLIDER, SLIDER_HIGHLIGHTED, null, x, y, w, h, enabled ? hover : 0f, true, alpha);
        vanillaFace(g, SLIDER_HANDLE, SLIDER_HANDLE_HIGHLIGHTED, null, handleX, y, 8, h, enabled ? hover : 0f, true, alpha);
    }

    /** Vanilla checkbox sprite (17 px native) with the hover cross-fade. */
    public static void vanillaCheckbox(final GuiGraphics g, final int x, final int y, final int size, final boolean selected,
                                       final float hover, final float alpha) {
        vanillaFace(g, selected ? CHECKBOX_SELECTED : CHECKBOX, selected ? CHECKBOX_SELECTED_HIGHLIGHTED : CHECKBOX_HIGHLIGHTED,
            null, x, y, size, size, hover, true, alpha);
    }

    /** Vanilla text-field frame. */
    public static void vanillaTextField(final GuiGraphics g, final int x, final int y, final int w, final int h, final boolean focused) {
        RenderSystem.enableBlend();
        g.blitSprite(focused ? TEXT_FIELD_HIGHLIGHTED : TEXT_FIELD, x, y, w, h);
        RenderSystem.disableBlend();
    }

    /** Vanilla tab sprite (raised when selected) with the hover cross-fade. */
    public static void vanillaTab(final GuiGraphics g, final int x, final int y, final int w, final int h, final boolean selected,
                                  final float hover, final float alpha) {
        vanillaFace(g, selected ? TAB_SELECTED : TAB, selected ? TAB_SELECTED_HIGHLIGHTED : TAB_HIGHLIGHTED, null, x, y, w, h, hover, true, alpha);
    }

    /** Tiled vanilla menu background (dirt-free 1.21 stone) for the whole screen. */
    public static void vanillaPanel(final GuiGraphics g, final int x, final int y, final int w, final int h, final boolean inWorld) {
        RenderSystem.enableBlend();
        g.blit(inWorld ? INWORLD_MENU_BACKGROUND : MENU_BACKGROUND, x, y, 0, 0, w, h, 32, 32);
        RenderSystem.disableBlend();
    }

    /** Tiled vanilla list background. */
    public static void vanillaListBackground(final GuiGraphics g, final int x, final int y, final int w, final int h, final boolean inWorld) {
        RenderSystem.enableBlend();
        g.blit(inWorld ? INWORLD_MENU_LIST_BACKGROUND : MENU_LIST_BACKGROUND, x, y, x, y, w, h, 32, 32);
        RenderSystem.disableBlend();
    }

    /** The 2 px header/footer separators vanilla draws around lists. */
    public static void vanillaSeparator(final GuiGraphics g, final int x, final int y, final int w, final boolean header, final boolean inWorld) {
        RenderSystem.enableBlend();
        final ResourceLocation tex = inWorld ? (header ? INWORLD_HEADER_SEPARATOR : INWORLD_FOOTER_SEPARATOR)
            : (header ? HEADER_SEPARATOR : FOOTER_SEPARATOR);
        g.blit(tex, x, y, 0, 0, w, 2, 32, 2);
        RenderSystem.disableBlend();
    }

    /** An opaque vanilla-style dialog panel: black-ish fill, the stone tile, a two-tone border. */
    public static void vanillaDialog(final GuiGraphics g, final int x, final int y, final int w, final int h, final float alpha) {
        rect(g, x, y, w, h, Colors.withAlpha(0x000000, Math.round(0xE0 * alpha)));
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, alpha);
        g.blit(MENU_BACKGROUND, x, y, 0, 0, w, h, 32, 32);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        outline(g, x, y, w, h, Colors.withAlpha(0x000000, Math.round(255 * alpha)), 0);
        outline(g, x + 1, y + 1, w - 2, h - 2, Colors.withAlpha(0x8B8B8B, Math.round(255 * alpha)), 0);
    }

    // ------------------------------------------------------------------ misc

    /** Translucent full-screen dim (dark skin backgrounds, modal overlays). */
    public static void dim(final GuiGraphics g, final Screen screen, final int color) {
        g.fill(0, 0, screen.width, screen.height, color);
    }

    public static void dim(final GuiGraphics g, final int w, final int h, final int color) {
        g.fill(0, 0, w, h, color);
    }

    /** 1 px "focus ring" in the accent colour, outside the widget. */
    public static void focusRing(final GuiGraphics g, final int x, final int y, final int w, final int h, final float alpha) {
        if (alpha <= 0.02f) return;
        outline(g, x - 1, y - 1, w + 2, h + 2, Colors.scaleAlpha(Theme.current().accent(), alpha), Theme.current().radius() + 1);
    }

    /** The dark-skin button face used by Slate buttons and the vanilla-widget reskin (fill + border + shadow). */
    public static void darkFace(final GuiGraphics g, final int x, final int y, final int w, final int h,
                                final float hover, final float press, final boolean enabled, final float alpha) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        int fill = Colors.lerp(p.surface(), p.surfaceHover(), hover);
        int border = Colors.lerp(p.border(), p.borderStrong(), hover);
        if (!enabled) { fill = Colors.withAlpha(p.surface(), 0x80); border = Colors.withAlpha(p.border(), 0x80); }
        fill = Colors.brighten(fill, -0.12f * press);
        shadow(g, x, y, w, h, 0.35f * alpha * (1 - press));
        pixelRound(g, x, y, w, h, Colors.scaleAlpha(fill, alpha), t.radius());
        outline(g, x, y, w, h, Colors.scaleAlpha(border, alpha), t.radius());
    }

    /** Scissor in GUI coordinates (wraps GuiGraphics.enableScissor, which ignores the pose). */
    public static void scissor(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        g.enableScissor(x, y, x + Math.max(0, w), y + Math.max(0, h));
    }

    public static void unscissor(final GuiGraphics g) {
        g.disableScissor();
    }

    private SlateDraw() {}
}
