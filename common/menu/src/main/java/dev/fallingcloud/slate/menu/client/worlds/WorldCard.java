package dev.fallingcloud.slate.menu.client.worlds;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateBadge;
import dev.fallingcloud.slate.core.widget.SlateCard;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateTooltips;
import dev.fallingcloud.slate.menu.client.Fmt;
import dev.fallingcloud.slate.menu.client.play.model.WorldEntry;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;

/** A world in the grid view: icon, name, folder, chips (mode, flags, tags), last played + size, star. */
public class WorldCard extends SlateCard {

    public static final int HEIGHT = 74;
    private static final int ICON = 48;

    private final WorldEntry entry;
    private final SlateIconButton star;

    public WorldCard(final int x, final int y, final int w, final WorldEntry entry, final SlateWorldsScreen screen) {
        super(x, y, w, HEIGHT);
        this.entry = entry;
        star = add(new SlateIconButton(0, 0, 18, entry.favorite ? Icon.STAR_FILLED : Icon.STAR,
            Component.translatable(entry.favorite ? "slate_menu.unfavorite" : "slate_menu.favorite"), () -> screen.toggleFavorite(entry)), w - 22, 4);
        star.toggled(entry.favorite);
        onClick(() -> screen.clicked(entry));
        onRightClick(() -> screen.contextMenu(entry));
    }

    public WorldEntry entry() { return entry; }

    /** Icon paints on top of the card: a right-click landing on it must still open the menu. */
    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Draws icon + text block; shared with the compact list rows. */
    public static void drawSummary(final GuiGraphics g, final WorldEntry e, final int x, final int y, final int w, final int iconSize, final boolean twoLine, final int mouseX, final int mouseY) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        final LevelSummary s = e.summary;
        e.requestIcon();
        e.requestSize();
        // Icon
        if (e.icon != null) {
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.blit(e.icon.id(), x, y, iconSize, iconSize, 0, 0, e.icon.width(), e.icon.height(), e.icon.width(), e.icon.height());
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        } else {
            SlateDraw.pixelRound(g, x, y, iconSize, iconSize, van ? 0x80000000 : p.bg2(), t.radius());
            Icons.draw(g, Icon.WORLD, x + iconSize / 2 - 8, y + iconSize / 2 - 8, 16, van ? 0xFFA0A0A0 : p.textDim());
        }
        SlateDraw.outline(g, x, y, iconSize, iconSize, van ? 0xFF000000 : p.border(), t.radius());
        if (s.isLocked() || s.isDisabled() || s.requiresManualConversion()) {
            Icons.draw(g, Icon.WARNING, x + iconSize - 10, y + iconSize - 10, 10, p.warning());
            if (mouseX >= x && mouseX < x + iconSize && mouseY >= y && mouseY < y + iconSize) {
                SlateTooltips.request(List.of(s.isLocked() ? Component.translatable("selectWorld.locked") : s.getInfo()), null);
            }
        }
        final int tx = x + iconSize + 8;
        final int textW = w - iconSize - 8;
        g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(e.name()), textW), tx, y, p.text(), van);
        final int muted = van ? 0xFFC0C0C0 : p.textMuted(), dim = van ? 0xFFA0A0A0 : p.textDim();
        if (twoLine) {
            g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(e.id()), textW), tx, y + 11, dim, van);
            int cx = tx;
            final int cy = y + 24;
            cx += SlateBadge.draw(g, s.getGameMode().getShortDisplayName(), cx, cy, p.accent()) + 3;
            if (s.isHardcore()) cx += SlateBadge.draw(g, Component.translatable("slate_menu.worlds.hardcore"), cx, cy, p.danger()) + 3;
            if (s.hasCommands()) cx += SlateBadge.draw(g, Component.translatable("slate_menu.worlds.cheats"), cx, cy, p.warning()) + 3;
            if (s.isExperimental()) cx += SlateBadge.draw(g, Component.translatable("slate_menu.worlds.experimental"), cx, cy, p.warning()) + 3;
            for (final String tag : e.tags) {
                final int bw = SlateDraw.width(tag) + 8;
                if (cx + bw > x + w) break;
                cx += SlateBadge.draw(g, Component.literal(tag), cx, cy, van ? 0xFF8B8B8B : p.borderStrong()) + 3;
            }
            final String size = e.size >= 0 ? Fmt.bytes(e.size) : e.size == -3 ? "..." : "";
            final Component line = Component.empty().append(Fmt.ago(s.getLastPlayed())).append(" · ").append(s.getWorldVersionName())
                .append(size.isEmpty() ? "" : " · " + size);
            g.drawString(SlateDraw.font(), SlateDraw.truncate(line, textW), tx, y + 40, muted, van);
        } else {
            final Component line = Component.empty().append(s.getGameMode().getShortDisplayName()).append(" · ").append(Fmt.ago(s.getLastPlayed()))
                .append(" · ").append(s.getWorldVersionName());
            g.drawString(SlateDraw.font(), SlateDraw.truncate(line, textW), tx, y + 11, muted, van);
        }
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int x, final int y, final int w, final int h, final int mouseX, final int mouseY, final float partialTick) {
        drawSummary(g, entry, x + 8, y + (h - ICON) / 2, w - 8 - 30, ICON, true, mouseX, mouseY);
        if (isSelected() && !Theme.current().isVanilla()) SlateDraw.rect(g, x, y + 6, 2, h - 12, Colors.withAlpha(Theme.current().accent(), 0xFF));
    }

    public void refresh() {
        star.setIcon(entry.favorite ? Icon.STAR_FILLED : Icon.STAR);
        star.toggled(entry.favorite);
        star.tip(Component.translatable(entry.favorite ? "slate_menu.unfavorite" : "slate_menu.favorite"));
    }
}
