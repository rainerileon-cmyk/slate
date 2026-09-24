package dev.fallingcloud.slate.building.client.menu;

import dev.fallingcloud.slate.building.client.BuildingIcons;
import dev.fallingcloud.slate.building.client.gfx.UiDraw;
import dev.fallingcloud.slate.building.ops.ToolType;
import dev.fallingcloud.slate.building.toolbox.ToolTier;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.building.toolbox.UpgradeType;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The build menu's toolbox summary: the toolbox glyph and one glyph per owned tool, tinted with its tier colour,
 * with a pip bar per tool under it; "Creative" or "No toolbox" otherwise. The tooltip lists every tool with its
 * tier and the installed upgrades.
 */
final class ToolboxChip extends SlateWidget {

    static final int H = 16;

    private ToolboxAccess.Capabilities caps = ToolboxAccess.Capabilities.NONE;

    ToolboxChip(final int x, final int y) {
        super(x, y, 20, H, Component.translatable("slate_building.ui.menu.toolbox"));
        refresh();
    }

    /** Re-reads the player's toolbox; resizes to fit. */
    void refresh() {
        final Minecraft mc = Minecraft.getInstance();
        caps = mc.player == null ? ToolboxAccess.Capabilities.NONE : ToolboxAccess.of(mc.player);
        setWidth(preferredWidth());
        final List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable("slate_building.ui.menu.toolbox"));
        if (caps.creative()) tip.add(Component.translatable("slate_building.ui.menu.toolbox_creative").withStyle(ChatFormatting.GRAY));
        boolean any = false;
        for (final ToolType t : ToolType.values()) {
            final int tier = caps.tier(t);
            if (tier <= 0) continue;
            any = true;
            tip.add(Component.translatable("slate_building.tool_tiered", ToolTier.byLevel(tier).displayName(), t.displayName())
                .withColor(UiDraw.tierColor(tier) & 0xFFFFFF));
        }
        if (!any) tip.add(Component.translatable("slate_building.ui.menu.toolbox_none").withStyle(ChatFormatting.GRAY));
        for (final UpgradeType u : UpgradeType.values()) {
            final int n = caps.upgrade(u);
            if (n > 0) tip.add(Component.translatable("slate_building.ui.menu.upgrade_count", u.displayName(), n).withStyle(ChatFormatting.DARK_AQUA));
        }
        tip(tip);
    }

    private int owned() {
        int n = 0;
        for (final ToolType t : ToolType.values()) if (caps.tier(t) > 0) n++;
        return n;
    }

    private int preferredWidth() {
        if (caps.creative()) return 16 + SlateDraw.width(Component.translatable("slate_building.ui.menu.creative")) + 6;
        final int n = owned();
        if (n == 0) return 16 + SlateDraw.width(Component.translatable("slate_building.ui.menu.no_toolbox")) + 6;
        return 16 + n * 11 + 3;
    }

    @Override
    public void onClick(final double mouseX, final double mouseY) {}

    @Override
    public void playDownSound(final net.minecraft.client.sounds.SoundManager handler) {}

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
            SlateDraw.rect(g, x, y, w, h, Colors.scaleAlpha(0x80000000, a));
            SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(hover() > 0.5f ? 0xFFA0A0A0 : 0xFF505050, a), 0);
        } else {
            SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(Colors.lerp(p.surface(), p.surfaceHover(), hover()), a), t.radius());
            SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(p.border(), a), t.radius());
        }
        Icons.draw(g, BuildingIcons.TOOLBOX, x + 4, y + 4, 8, Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.textMuted(), a));
        final int tx = x + 16;
        if (caps.creative() || owned() == 0) {
            final Component label = Component.translatable(caps.creative() ? "slate_building.ui.menu.creative" : "slate_building.ui.menu.no_toolbox");
            final int col = caps.creative() ? (vanilla ? 0xFFFFFF55 : p.accent()) : (vanilla ? 0xFFA0A0A0 : p.textDim());
            g.drawString(SlateDraw.font(), label, tx, SlateDraw.textY(y, h), Colors.scaleAlpha(col, a), vanilla);
            return;
        }
        int ix = tx;
        for (final ToolType tool : ToolType.values()) {
            final int tier = caps.tier(tool);
            if (tier <= 0) continue;
            final int col = UiDraw.tierColor(tier);
            Icons.draw(g, tool.icon(), ix, y + 3, 8, Colors.scaleAlpha(col, a));
            for (int i = 0; i < tier; i++) SlateDraw.rect(g, ix + i * 2, y + 12, 1, 2, Colors.scaleAlpha(col, a));
            ix += 11;
        }
    }
}
