package dev.fallingcloud.slate.multiplayer.client.stream;

import dev.fallingcloud.slate.core.widget.SlateWidget;
import dev.fallingcloud.slate.multiplayer.MultiplayerConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The picture-in-picture window as a widget on whatever screen is open: draggable (position persists
 * in the client config), with expand and close controls in its title strip. When no screen is open the
 * same picture is drawn from the HUD hook without input.
 */
public final class PipWidget extends SlateWidget {

    private double dragOffX, dragOffY;
    private boolean dragging;
    private boolean moved;

    public PipWidget() {
        super(0, 0, 160, 90, Component.literal("Stream"));
        this.silent();
        sync();
    }

    private StreamViewer.Viewing viewing() { return StreamViewer.pipStream(); }

    private void sync() {
        final StreamViewer.Viewing v = viewing();
        final Minecraft mc = Minecraft.getInstance();
        if (v == null) { this.visible = false; return; }
        this.visible = true;
        final int[] r = StreamViewer.pipRect(v, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        setX(r[0]);
        setY(r[1]);
        setWidth(r[2]);
        setHeight(r[3]);
    }

    private boolean onExpand(final double mx, final double my) {
        return mx >= getX() + getWidth() - 28 && mx < getX() + getWidth() - 15 && my >= getY() && my < getY() + 11;
    }

    private boolean onClose(final double mx, final double my) {
        return mx >= getX() + getWidth() - 15 && mx < getX() + getWidth() && my >= getY() && my < getY() + 11;
    }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        final StreamViewer.Viewing v = viewing();
        if (v == null) return;
        if (onClose(mouseX, mouseY)) { StreamViewer.stop(v.info.id()); this.visible = false; return; }
        if (onExpand(mouseX, mouseY)) { StreamViewer.watch(v.info.id(), false); return; }
        dragging = true;
        moved = false;
        dragOffX = mouseX - getX();
        dragOffY = mouseY - getY();
    }

    @Override
    protected void onDrag(final double mouseX, final double mouseY, final double dragX, final double dragY) {
        if (!dragging) return;
        final Minecraft mc = Minecraft.getInstance();
        final int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        final int nx = (int) Math.max(0, Math.min(sw - getWidth(), mouseX - dragOffX));
        final int ny = (int) Math.max(0, Math.min(sh - getHeight(), mouseY - dragOffY));
        if (nx != getX() || ny != getY()) moved = true;
        setX(nx);
        setY(ny);
    }

    @Override
    public void onRelease(final double mouseX, final double mouseY) {
        super.onRelease(mouseX, mouseY);
        if (dragging && moved) {
            final int x = getX(), y = getY();
            MultiplayerConfigs.clientFile().update(c -> { c.pipX = x; c.pipY = y; });
        } else if (dragging && !moved) {
            final StreamViewer.Viewing v = viewing();
            if (v != null) StreamViewer.watch(v.info.id(), false);       // plain click opens the full viewer
        }
        dragging = false;
    }

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final StreamViewer.Viewing v = viewing();
        if (v == null) { this.visible = false; return; }
        if (!dragging) sync();
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        StreamViewer.drawPip(g, v, getX(), getY(), getWidth(), getHeight(), isHovered(), true);
        g.pose().popPose();
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        renderDark(g, mouseX, mouseY, partialTick);
    }
}
