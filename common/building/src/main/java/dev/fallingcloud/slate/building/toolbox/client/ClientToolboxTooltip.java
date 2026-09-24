package dev.fallingcloud.slate.building.toolbox.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.fallingcloud.slate.building.toolbox.BuildingToolItem;
import dev.fallingcloud.slate.building.toolbox.ToolTier;
import dev.fallingcloud.slate.building.toolbox.ToolboxContents;
import dev.fallingcloud.slate.building.toolbox.ToolboxTooltip;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * The picture in a toolbox's tooltip: the six tool slots (the tool, or a faint silhouette of the missing one) with a
 * four-pip tier meter under each, then the four upgrade slots and a 3x3 grid showing which pouch slots are filled.
 * Drawn on vanilla's dark tooltip background, so it uses fixed light-on-dark colours in both skins.
 */
public final class ClientToolboxTooltip implements ClientTooltipComponent {

    private static final int CELL = 18;
    private static final int PIP_W = 3, PIP_H = 2;
    private static final int EMPTY_PIP = 0xFF3A383C;
    private static final int GHOST = 0x38FFFFFF;
    private static final int CELL_EMPTY = 0xFF34323A;
    private static final int CELL_FULL = 0xFFB9B4C2;

    private final List<ItemStack> items;

    public ClientToolboxTooltip(final ToolboxTooltip data) {
        this.items = data.items();
    }

    @Override
    public int getHeight() {
        return CELL + 4 + CELL + 2;
    }

    @Override
    public int getWidth(final Font font) {
        return ToolboxContents.TOOLS * CELL;
    }

    @Override
    public void renderImage(final Font font, final int x, final int y, final GuiGraphics g) {
        for (int i = 0; i < ToolboxContents.TOOLS; i++) {
            final int cx = x + i * CELL;
            final ItemStack tool = i < items.size() ? items.get(i) : ItemStack.EMPTY;
            if (tool.isEmpty()) {
                ghost(g, ToolboxScreen.toolGhost(ToolboxContents.toolType(i)), cx + 1, y + 1, GHOST);
            } else {
                g.renderItem(tool, cx + 1, y + 1);
                g.renderItemDecorations(font, tool, cx + 1, y + 1);
            }
            final int level = tool.getItem() instanceof BuildingToolItem t ? t.level() : 0;
            final int color = level > 0 ? ToolTier.byLevel(level).color() : EMPTY_PIP;
            for (int p = 0; p < ToolTier.MAX; p++) {
                g.fill(cx + 2 + p * (PIP_W + 1), y + CELL + 1, cx + 2 + p * (PIP_W + 1) + PIP_W, y + CELL + 1 + PIP_H, p < level ? color : EMPTY_PIP);
            }
        }
        final int row = y + CELL + 4;
        for (int i = 0; i < ToolboxContents.UPGRADES; i++) {
            final int cx = x + i * CELL;
            final ItemStack up = items.size() > ToolboxContents.FIRST_UPGRADE + i ? items.get(ToolboxContents.FIRST_UPGRADE + i) : ItemStack.EMPTY;
            if (up.isEmpty()) ghost(g, ToolboxScreen.UPGRADE_GHOST, cx + 1, row + 1, GHOST);
            else g.renderItem(up, cx + 1, row + 1);
        }
        // Pouch: its block glyph, then 3x3 cells with the filled ones lit.
        final int gx = x + ToolboxContents.UPGRADES * CELL + 1;
        ghost(g, ToolboxScreen.POUCH_GHOST, gx, row + 1, GHOST);
        final int px = gx + 18, py = row + 1;
        for (int i = 0; i < ToolboxContents.POUCH; i++) {
            final int slot = ToolboxContents.FIRST_POUCH + i;
            final boolean full = slot < items.size() && !items.get(slot).isEmpty();
            final int cx = px + (i % 3) * 6, cy = py + (i / 3) * 6;
            g.fill(cx, cy, cx + 5, cy + 5, full ? CELL_FULL : CELL_EMPTY);
        }
    }

    private static void ghost(final GuiGraphics g, final net.minecraft.resources.ResourceLocation tex, final int x, final int y, final int argb) {
        RenderSystem.enableBlend();
        g.setColor(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
        g.blit(tex, x, y, 0, 0, 16, 16, 16, 16);
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}
