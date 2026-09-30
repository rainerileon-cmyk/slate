package dev.fallingcloud.slate.core.screen.reskin;

import dev.fallingcloud.slate.core.mixin.ContainerScreenAccessor;
import dev.fallingcloud.slate.core.screen.Reskin;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;

/**
 * The restyle of container screens ({@code reskinContainers}, the container-style switch; independent of the menu style,
 * painted with {@link Theme#containerPalette()}). While a screen paints its background
 * ({@code renderBg}), every texture blit that is a full-width strip of its body (a chest paints two, most screens one)
 * is drawn as Slate's panel instead, with a well per slot; a screen that paints its body some other way (nine-sliced,
 * from an atlas) gets a dark tint with the same wells laid over what it drew. The creative inventory's tab and
 * scroller sprites become Slate tabs and a scroll thumb. During {@code renderLabels} the dark grey vanilla titles
 * become light text. Everything else a screen draws (progress arrows, flames, bubbles, a mod's own widgets) still
 * draws on top. The two windows are opened and closed by {@code ContainerScreenMixin}; the hooks live in
 * {@code GuiGraphicsMixin}. Better Inventory's screens are the exception: their art is given the palette itself
 * ({@link BetterInventoryPalette}), so their background stays theirs.
 */
public final class ContainerReskin {

    private static final String CREATIVE_TAB = "container/creative_inventory/tab_";
    private static final String CREATIVE_SCROLLER = "container/creative_inventory/scroller";

    private static boolean inBackground, backgroundDone, inLabels;
    @Nullable private static AbstractContainerScreen<?> current;

    private ContainerReskin() {}

    public static void beginBackground() {
        inBackground = Reskin.containers() && Minecraft.getInstance().screen instanceof AbstractContainerScreen<?>
            && !BetterInventoryPalette.paints(Minecraft.getInstance().screen);
        current = inBackground ? (AbstractContainerScreen<?>) Minecraft.getInstance().screen : null;
        backgroundDone = false;
    }

    /** {@code renderBg} is over: a screen that never blitted its body as strips gets the tint fallback now. */
    public static void endBackground(final GuiGraphics g) {
        if (inBackground && !backgroundDone && current != null) {
            final ContainerScreenAccessor acc = (ContainerScreenAccessor) current;
            ReskinDraw.containerTint(g, acc.slate$leftPos(), acc.slate$topPos(), acc.slate$imageWidth(), acc.slate$imageHeight());
            drawSlots(g, current, acc.slate$leftPos(), acc.slate$topPos());
        }
        inBackground = false;
        current = null;
    }

    public static void beginLabels() {
        inLabels = Reskin.containers();
    }

    public static void endLabels() {
        inLabels = false;
    }

    /**
     * A texture blit during {@code renderBg}: a full-width strip of the container's body (a GUI texture, as wide as
     * the screen's image, starting at its left edge and inside its height) is the panel's now: the first strip draws
     * the whole panel, later strips draw nothing. Anything narrower (a progress arrow cut from the same texture)
     * passes through. True = handled.
     */
    public static boolean replaceBackground(final GuiGraphics g, final ResourceLocation texture, final int x, final int y, final int w, final int h) {
        if (!inBackground || current == null) return false;
        if (!texture.getPath().startsWith("textures/gui/")) return false;
        final ContainerScreenAccessor acc = (ContainerScreenAccessor) current;
        final int left = acc.slate$leftPos(), top = acc.slate$topPos(), iw = acc.slate$imageWidth(), ih = acc.slate$imageHeight();
        if (w != iw || x != left || y < top || y >= top + ih) return false;
        if (!backgroundDone) {
            backgroundDone = true;
            ReskinDraw.containerPanel(g, left, top, iw, ih);
            drawSlots(g, current, left, top);
            // The bits of the texture that were not slots and a screen expects to be there.
            if (current instanceof InventoryScreen) {
                ReskinDraw.containerInset(g, left + 25, top + 7, 51, 72);                                   // the player's frame
                ReskinDraw.containerArrow(g, left + 133, top + 30);                                         // 2x2 grid -> result
            } else if (current instanceof CraftingScreen) {
                ReskinDraw.containerArrow(g, left + 90, top + 35);                                          // grid -> result
            } else if (current instanceof AbstractFurnaceScreen<?>) {
                ReskinDraw.containerArrow(g, left + 79, top + 35);                                          // input -> output
            } else if (current instanceof CreativeModeInventoryScreen) {
                ReskinDraw.containerInset(g, left + 174, top + 17, 16, 114);                                // the scroll track
            }
        }
        return true;
    }

    private static void drawSlots(final GuiGraphics g, final AbstractContainerScreen<?> screen, final int left, final int top) {
        for (final Slot slot : screen.getMenu().slots) {
            if (slot.isActive()) ReskinDraw.containerSlot(g, left + slot.x - 1, top + slot.y - 1);
        }
    }

    /** A sprite blit during {@code renderBg}: the creative inventory's tabs and scroller are Slate's. True = handled. */
    public static boolean replaceSprite(final GuiGraphics g, final ResourceLocation sprite, final int x, final int y, final int w, final int h) {
        if (!inBackground || !(current instanceof CreativeModeInventoryScreen)) return false;
        final String path = sprite.getPath();
        if (path.startsWith(CREATIVE_TAB)) {
            ReskinDraw.containerTab(g, x, y, w, h, path.contains("_selected"));
            return true;
        }
        if (path.startsWith(CREATIVE_SCROLLER)) {
            ReskinDraw.containerThumb(g, x, y, w, h, !path.endsWith("disabled"));
            return true;
        }
        return false;
    }

    /** {@code drawString} colour during {@code renderLabels}: vanilla's dark grey titles (0x404040) read on a light texture, not on the panel. */
    public static int labelColor(final int color) {
        if (!inLabels) return color;
        final int rgb = color & 0xFFFFFF;
        final int max = Math.max(rgb >> 16 & 0xFF, Math.max(rgb >> 8 & 0xFF, rgb & 0xFF));
        if (max > 0x70) return color;                                       // already light (a mod's own colour)
        final int alpha = (color & 0xFC000000) == 0 ? 0xFF000000 : color & 0xFF000000;   // the font treats alpha 0 as opaque
        return alpha | (Theme.current().containerPalette().text() & 0xFFFFFF);
    }
}
