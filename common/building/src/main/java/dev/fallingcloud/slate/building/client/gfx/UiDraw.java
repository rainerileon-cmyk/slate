package dev.fallingcloud.slate.building.client.gfx;

import com.mojang.blaze3d.platform.InputConstants;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.building.toolbox.ToolTier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Small themed pieces shared by the wheel overlay, the HUD and the build menu: floating label pills, key caps,
 * chips and tier pips, all in both skins. Everything takes an alpha so it can fade with its owner.
 */
public final class UiDraw {

    public static final int PILL_H = 16;
    public static final int KEYCAP_H = 11;

    /** A floating label background (HUD over the world): layered surface (dark) or a vanilla-tooltip-like box. */
    public static void pill(final GuiGraphics g, final int x, final int y, final int w, final int h, final float alpha) {
        if (alpha <= 0.01f) return;
        final Theme t = Theme.current();
        final Palette p = t.palette();
        if (t.isVanilla()) {
            SlateDraw.rect(g, x + 1, y, w - 2, h, Colors.scaleAlpha(0xE0100010, alpha));
            SlateDraw.rect(g, x, y + 1, w, h - 2, Colors.scaleAlpha(0xE0100010, alpha));
            SlateDraw.outline(g, x + 1, y + 1, w - 2, h - 2, Colors.scaleAlpha(0x60FFFFFF, alpha), 0);
        } else {
            SlateDraw.shadow(g, x, y, w, h, 0.45f * alpha);
            SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(Colors.withAlpha(p.surface(), 0xF0), alpha), t.radius());
            SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(p.border(), alpha), t.radius());
        }
    }

    /** Short name of what a key mapping is bound to ("R", "LMB", "Alt"); empty when unbound. */
    public static String keyName(final @Nullable KeyMapping mapping) {
        if (mapping == null || mapping.isUnbound()) return "";
        final InputConstants.Key key = com.mojang.blaze3d.platform.InputConstants.getKey(mapping.saveString());
        return keyName(key);
    }

    public static String keyName(final InputConstants.Key key) {
        if (key.getType() == InputConstants.Type.MOUSE) {
            return switch (key.getValue()) {
                case 0 -> Component.translatable("slate_building.ui.key.lmb").getString();
                case 1 -> Component.translatable("slate_building.ui.key.rmb").getString();
                case 2 -> Component.translatable("slate_building.ui.key.mmb").getString();
                default -> Component.translatable("slate_building.ui.key.mouse", key.getValue() + 1).getString();
            };
        }
        final String s = key.getDisplayName().getString();
        return s.length() > 10 ? s.substring(0, 9) + "…" : s;
    }

    /** Width of a key cap for {@code text}. */
    public static int keycapWidth(final String text) {
        return SlateDraw.width(text) + 6;
    }

    /** A key cap ("R", "Alt"); returns its width. */
    public static int keycap(final GuiGraphics g, final String text, final int x, final int y, final float alpha) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final int w = keycapWidth(text);
        if (alpha <= 0.01f) return w;
        if (t.isVanilla()) {
            SlateDraw.rect(g, x, y, w, KEYCAP_H, Colors.scaleAlpha(0xFF000000, alpha));
            SlateDraw.rect(g, x + 1, y + 1, w - 2, KEYCAP_H - 2, Colors.scaleAlpha(0xFF6F6F6F, alpha));
            SlateDraw.rect(g, x + 1, y + KEYCAP_H - 2, w - 2, 1, Colors.scaleAlpha(0xFF3F3F3F, alpha));
            g.drawString(SlateDraw.font(), text, x + 3, y + 2, Colors.scaleAlpha(0xFFFFFFFF, alpha), true);
        } else {
            SlateDraw.pixelRound(g, x, y, w, KEYCAP_H, Colors.scaleAlpha(p.surfaceActive(), alpha), 2);
            SlateDraw.rect(g, x + 2, y + KEYCAP_H - 1, w - 4, 1, Colors.scaleAlpha(p.borderStrong(), alpha));
            g.drawString(SlateDraw.font(), text, x + 3, y + 2, Colors.scaleAlpha(p.text(), alpha), false);
        }
        return w;
    }

    /** "[key] label" hint; returns the total width. */
    public static int hint(final GuiGraphics g, final String key, final Component label, final int x, final int y, final float alpha) {
        final Theme t = Theme.current();
        final int kw = key.isEmpty() ? 0 : keycap(g, key, x, y, alpha) + 3;
        final int col = t.isVanilla() ? 0xFFD0D0D0 : t.palette().textMuted();
        g.drawString(SlateDraw.font(), label, x + kw, y + 2, Colors.scaleAlpha(col, alpha), t.isVanilla());
        return kw + SlateDraw.width(label);
    }

    public static int hintWidth(final String key, final Component label) {
        return (key.isEmpty() ? 0 : keycapWidth(key) + 3) + SlateDraw.width(label);
    }

    /** Colour of a tool tier (pips, chips): copper, iron, diamond, netherite. */
    public static int tierColor(final int level) {
        return switch (ToolTier.byLevel(level)) {
            case COPPER -> 0xFFD2855A;
            case IRON -> 0xFFD8D8D8;
            case DIAMOND -> 0xFF5ED6D0;
            case NETHERITE -> 0xFF8E7A8C;
        };
    }

    /** {@code filled} of {@code total} 2x4 pips (tier display); returns the width. */
    public static int pips(final GuiGraphics g, final int x, final int y, final int filled, final int total, final int color, final float alpha) {
        final Theme t = Theme.current();
        final int off = t.isVanilla() ? 0xFF3A3A3A : t.palette().surfaceActive();
        for (int i = 0; i < total; i++) {
            SlateDraw.rect(g, x + i * 3, y, 2, 4, Colors.scaleAlpha(i < filled ? color : off, alpha));
        }
        return total * 3 - 1;
    }

    /** A small chip: optional icon + text on a surface; returns the width. */
    public static int chip(final GuiGraphics g, final @Nullable Icon icon, final Component text, final int x, final int y,
                           final int fg, final float alpha) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final int w = chipWidth(icon, text);
        final int h = 12;
        if (alpha <= 0.01f) return w;
        if (t.isVanilla()) {
            SlateDraw.rect(g, x, y, w, h, Colors.scaleAlpha(0x90000000, alpha));
            SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(0xFF505050, alpha), 0);
        } else {
            SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(p.surfaceActive(), alpha), 2);
        }
        int tx = x + 4;
        if (icon != null) {
            Icons.draw(g, icon, x + 3, y + 2, 8, Colors.scaleAlpha(fg, alpha));
            tx += 10;
        }
        g.drawString(SlateDraw.font(), text, tx, y + 2, Colors.scaleAlpha(fg, alpha), t.isVanilla());
        return w;
    }

    public static int chipWidth(final @Nullable Icon icon, final Component text) {
        return SlateDraw.width(text) + 8 + (icon != null ? 10 : 0);
    }

    private UiDraw() {}
}
