package dev.fallingcloud.slate.building.client.settings;

import dev.fallingcloud.slate.building.client.BuildingIcons;
import dev.fallingcloud.slate.building.client.menu.WheelEditorScreen;
import dev.fallingcloud.slate.building.client.wheel.WheelConfig;
import dev.fallingcloud.slate.building.config.WheelSettings;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The "Edit wheels…" row of the Building settings: the wheel glyph, the label, a summary of the configured wheels
 * ("Shapes 8 · More 5") and a chevron; the whole row is a button that opens the {@link WheelEditorScreen}.
 */
public final class WheelsRow extends SlateWidget {

    public static final int HEIGHT = 24;

    public WheelsRow(final int width) {
        super(0, 0, width, HEIGHT, Component.translatable("slate_building.settings.wheel.edit"));
        tip(Component.translatable("slate_building.settings.wheel.edit.desc"));
    }

    private static Component summary() {
        final MutableComponent out = Component.empty();
        int i = 0;
        for (final WheelSettings.Wheel w : WheelConfig.wheel().wheels) {
            if (i > 0) out.append(" · ");
            out.append(WheelConfig.displayName(w, i))
                .append(" " + WheelConfig.shapesOf(w).size());
            i++;
        }
        if (i == 0) out.append(Component.translatable("slate_building.ui.editor.no_wheels"));
        return out;
    }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        open();
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active || !this.visible) return false;
        if (keyCode == 257 || keyCode == 32 || keyCode == 335) {
            flashPress();
            open();
            return true;
        }
        return false;
    }

    private static void open() {
        final Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new WheelEditorScreen(mc.screen));
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, false);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, true);
    }

    private void draw(final GuiGraphics g, final boolean vanilla) {
        final float a = effectiveAlpha();
        if (a <= 0.004f) return;
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
        if (vanilla) {
            SlateDraw.vanillaButton(g, x, y + 2, w, h - 4, Math.max(hover(), focus()), this.active, a);
        } else {
            SlateDraw.pixelRound(g, x, y + 1, w, h - 2, Colors.scaleAlpha(Colors.lerp(p.surface(), p.surfaceHover(), hover()), a), t.radius());
            SlateDraw.outline(g, x, y + 1, w, h - 2, Colors.scaleAlpha(Colors.lerp(p.border(), p.accent(), Math.max(hover() * 0.6f, focus())), a), t.radius());
        }
        final int fg = vanilla ? 0xFFFFFFFF : p.text();
        Icons.draw(g, BuildingIcons.WHEEL, x + 7, y + (h - 12) / 2, 12, Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.accent(), a));
        final Component label = getMessage();
        g.drawString(SlateDraw.font(), label, x + 24, SlateDraw.textY(y, h), Colors.scaleAlpha(fg, a), vanilla);
        final int sx = x + 24 + SlateDraw.width(label) + 10;
        final int sw = x + w - 18 - sx;
        if (sw > 20) {
            final var cut = SlateDraw.truncate(summary(), sw);
            g.drawString(SlateDraw.font(), cut, x + w - 18 - SlateDraw.width(cut), SlateDraw.textY(y, h),
                Colors.scaleAlpha(vanilla ? 0xFFA0A0A0 : p.textDim(), a), vanilla);
        }
        Icons.draw(g, Icon.CHEVRON_RIGHT, x + w - 13, y + (h - 8) / 2, 8, Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.textMuted(), a));
    }
}
