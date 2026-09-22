package dev.fallingcloud.slate.core.screen;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** One page of a {@link SidebarScreen}. Add widgets through {@code screen.addPageWidget} in {@link #build}. */
public abstract class SidebarPage {

    private final String id;
    private final Component title;
    private final Icon icon;

    protected SidebarPage(final String id, final Component title, final Icon icon) {
        this.id = id;
        this.title = title;
        this.icon = icon;
    }

    public String id() { return id; }

    public Component title() { return title; }

    public Icon icon() { return icon; }

    /** Badge count shown next to the nav entry (0 = none). */
    public int badge() { return 0; }

    /** Create the page's widgets inside {@code area}. Called whenever the page is shown or the screen resizes. */
    public abstract void build(SidebarScreen screen, Rect area);

    /** Draw behind the page's widgets. */
    public void render(final SidebarScreen screen, final GuiGraphics g, final Rect area, final int mouseX, final int mouseY, final float partialTick) {}

    /** Once per client tick while shown. */
    public void tick() {}

    /** The page is being hidden (switching pages or closing). */
    public void onHide() {}
}
