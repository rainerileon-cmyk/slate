package dev.fallingcloud.slate.core.widget.popup;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * The popup stack for the current screen. Core's screen hooks route input here first and render the
 * stack last, so popups work on vanilla screens too. Changing screens clears the stack. Popups that
 * animate out stay in a separate "closing" list: drawn, never given input.
 */
public final class Popups {

    private static final Deque<Popup> STACK = new ArrayDeque<>();
    private static final List<Popup> CLOSING = new ArrayList<>();
    private static final Anim dim = new Anim(0, 160, Ease.OUT_CUBIC);
    @Nullable private static Screen owner;

    public static void open(final Popup popup) {
        STACK.push(popup);
        owner = net.minecraft.client.Minecraft.getInstance().screen;
        if (popup.isModal()) dim.set(1f);
    }

    public static void close(final Popup popup) {
        if (STACK.remove(popup)) {
            popup.onClose();
            if (popup.beginClose()) CLOSING.add(popup);
        }
        if (STACK.stream().noneMatch(Popup::isModal)) dim.set(0f);
    }

    public static void closeTop() {
        final Popup p = STACK.peek();
        if (p != null) close(p);
    }

    public static void closeAll() {
        while (!STACK.isEmpty()) close(STACK.peek());
        CLOSING.clear();
        dim.snap(0);
    }

    @Nullable public static Popup top() { return STACK.peek(); }

    public static boolean any() { return !STACK.isEmpty(); }

    public static boolean isOpen(final Popup popup) { return STACK.contains(popup); }

    /** Screen hook: a new screen was set. */
    public static void onScreenChanged(@Nullable final Screen screen) {
        if (screen != owner) closeAll();
        owner = screen;
    }

    public static void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick, final int w, final int h) {
        final float d = dim.get();
        if (d > 0.01f) g.fill(0, 0, w, h, Colors.scaleAlpha(Theme.current().palette().overlay(), d));
        CLOSING.removeIf(Popup::closeFinished);
        if (STACK.isEmpty() && CLOSING.isEmpty()) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        for (final Popup p : CLOSING) p.render(g, -1, -1, partialTick);
        // Draw bottom-up so the top popup paints last.
        final Popup[] arr = STACK.toArray(new Popup[0]);
        for (int i = arr.length - 1; i >= 0; i--) arr[i].render(g, mouseX, mouseY, partialTick);
        g.pose().popPose();
    }

    // ---- input; each returns true when consumed (a popup is open)

    public static boolean mouseClicked(final double mx, final double my, final int button) {
        final Popup p = STACK.peek();
        if (p == null) return false;
        if (p.contains(mx, my)) { p.mouseClicked(mx, my, button); return true; }
        if (p.isModal()) return true;
        close(p);                                  // click outside closes it; the click is still consumed
        return true;
    }

    public static boolean mouseReleased(final double mx, final double my, final int button) {
        final Popup p = STACK.peek();
        return p != null && (p.mouseReleased(mx, my, button) || p.isModal());
    }

    public static boolean mouseDragged(final double mx, final double my, final int button, final double dx, final double dy) {
        final Popup p = STACK.peek();
        return p != null && (p.mouseDragged(mx, my, button, dx, dy) || p.isModal());
    }

    public static boolean mouseScrolled(final double mx, final double my, final double sx, final double sy) {
        final Popup p = STACK.peek();
        if (p == null) return false;
        if (p.contains(mx, my) || p.isModal()) { p.mouseScrolled(mx, my, sx, sy); return true; }
        return false;
    }

    public static boolean keyPressed(final int key, final int scan, final int mods) {
        final Popup p = STACK.peek();
        if (p == null) return false;
        if (p.keyPressed(key, scan, mods)) return true;
        if (key == 256) { close(p); return true; }
        return p.isModal();
    }

    public static boolean charTyped(final char c, final int mods) {
        final Popup p = STACK.peek();
        return p != null && (p.charTyped(c, mods) || p.isModal());
    }

    private Popups() {}
}
