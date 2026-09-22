package dev.fallingcloud.slate.core.gfx;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Drawing primitives shared by every widget and screen. Two families: the pixel-stepped dark look
 * ({@link #pixelRound}, {@link #panel}, {@link #shadow}) and vanilla sprite helpers
 * ({@link #vanillaButton}, {@link #vanillaPanel}, {@link #vanillaListBackground}). All colours ARGB.
 * Fills use {@code GuiGraphics.fill}, which blends; sprite blits are wrapped in enableBlend because
 * {@code GuiGraphics.blit} does NOT enable blending on its own.
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
    public static final ResourceLocation MENU_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/menu_background.png");
    public static final ResourceLocation INWORLD_MENU_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/inworld_menu_background.png");
    public static final ResourceLocation MENU_LIST_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/menu_list_background.png");
    public static final ResourceLocation INWORLD_MENU_LIST_BACKGROUND = ResourceLocation.withDefaultNamespace("textures/gui/inworld_menu_list_background.png");
    public static final ResourceLocation HEADER_SEPARATOR = ResourceLocation.withDefaultNamespace("textures/gui/header_separator.png");
    public static final ResourceLocation FOOTER_SEPARATOR = ResourceLocation.withDefaultNamespace("textures/gui/footer_separator.png");
    public static final ResourceLocation INWORLD_HEADER_SEPARATOR = ResourceLocation.withDefaultNamespace("textures/gui/inworld_header_separator.png");
    public static final ResourceLocation INWORLD_FOOTER_SEPARATOR = ResourceLocation.withDefaultNamespace("textures/gui/inworld_footer_separator.png");

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

    /** 1 px outline with the same stepped corners. */
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
        final int c = Colors.scaleAlpha(Theme.current().palette().shadow(), alpha);
        pixelRound(g, x + 2, y + 2, w, h, c, Theme.current().radius());
    }

    public static void hgradient(final GuiGraphics g, final int x, final int y, final int w, final int h, final int left, final int right) {
        // fillGradient is vertical only; draw in 4 px strips.
        for (int i = 0; i < w; i += 4) {
            final int c = Colors.lerp(left, right, (float) i / Math.max(1, w - 1));
            g.fill(x + i, y, Math.min(x + w, x + i + 4), y + h, c);
        }
    }

    public static void vgradient(final GuiGraphics g, final int x, final int y, final int w, final int h, final int top, final int bottom) {
        g.fillGradient(x, y, x + w, y + h, top, bottom);
    }

    /** Horizontal 1 px rule. */
    public static void hline(final GuiGraphics g, final int x, final int y, final int w, final int color) {
        g.fill(x, y, x + w, y + 1, color);
    }

    public static void vline(final GuiGraphics g, final int x, final int y, final int h, final int color) {
        g.fill(x, y, x + 1, y + h, color);
    }

    // ------------------------------------------------------------------ text

    public static void text(final GuiGraphics g, final Component text, final int x, final int y, final int color) {
        g.drawString(font(), text, x, y, color, false);
    }

    public static void text(final GuiGraphics g, final String text, final int x, final int y, final int color) {
        g.drawString(font(), text, x, y, color, false);
    }

    public static void textShadow(final GuiGraphics g, final Component text, final int x, final int y, final int color) {
        g.drawString(font(), text, x, y, color, true);
    }

    public static void textCentered(final GuiGraphics g, final Component text, final int cx, final int y, final int color) {
        final FormattedCharSequence seq = text.getVisualOrderText();
        g.drawString(font(), seq, cx - font().width(seq) / 2, y, color, false);
    }

    public static void textRight(final GuiGraphics g, final Component text, final int rightX, final int y, final int color) {
        final FormattedCharSequence seq = text.getVisualOrderText();
        g.drawString(font(), seq, rightX - font().width(seq), y, color, false);
    }

    /** Text with an ellipsis if wider than {@code width}. */
    public static FormattedCharSequence truncate(final Component text, final int width) {
        final Font f = font();
        if (f.width(text) <= width) return text.getVisualOrderText();
        final String ell = "...";
        final FormattedText cut = f.substrByWidth(text, Math.max(0, width - f.width(ell)));
        return Component.literal(cut.getString() + ell).withStyle(text.getStyle()).getVisualOrderText();
    }

    public static int width(final Component text) { return font().width(text); }

    public static int width(final String text) { return font().width(text); }

    public static int lineHeight() { return font().lineHeight; }

    // ------------------------------------------------------------------ vanilla skin

    /** A vanilla stone button face, animated between normal and highlighted by {@code hover} (0-1). */
    public static void vanillaButton(final GuiGraphics g, final int x, final int y, final int w, final int h,
                                     final float hover, final boolean enabled, final float alpha) {
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, alpha);
        if (!enabled) {
            g.blitSprite(BUTTON_DISABLED, x, y, w, h);
        } else {
            g.blitSprite(BUTTON, x, y, w, h);
            if (hover > 0.01f) {
                g.setColor(1f, 1f, 1f, alpha * hover);
                g.blitSprite(BUTTON_HIGHLIGHTED, x, y, w, h);
            }
        }
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    /** Vanilla text-field frame. */
    public static void vanillaTextField(final GuiGraphics g, final int x, final int y, final int w, final int h, final boolean focused) {
        RenderSystem.enableBlend();
        g.blitSprite(focused ? TEXT_FIELD_HIGHLIGHTED : TEXT_FIELD, x, y, w, h);
        RenderSystem.disableBlend();
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

    // ------------------------------------------------------------------ misc

    /** Translucent full-screen dim (dark skin backgrounds, modal overlays). */
    public static void dim(final GuiGraphics g, final Screen screen, final int color) {
        g.fill(0, 0, screen.width, screen.height, color);
    }

    /** 1 px "focus ring" in the accent colour, outside the widget. */
    public static void focusRing(final GuiGraphics g, final int x, final int y, final int w, final int h, final float alpha) {
        if (alpha <= 0.02f) return;
        outline(g, x - 1, y - 1, w + 2, h + 2, Colors.scaleAlpha(Theme.current().accent(), alpha), Theme.current().radius() + 1);
    }

    /** Scissor in GUI coordinates (wraps GuiGraphics.enableScissor, which ignores the pose). */
    public static void scissor(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        g.enableScissor(x, y, x + w, y + h);
    }

    public static void unscissor(final GuiGraphics g) {
        g.disableScissor();
    }

    private SlateDraw() {}
}
