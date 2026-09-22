package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.popup.Popup;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * A centred dialog: icon + title, wrapped body, a row of buttons, optional extra widgets (a text field for
 * "rename" dialogs). Modal: dims the screen and swallows input. Tab/Shift+Tab cycle focus, Enter runs the
 * PRIMARY button, Escape dismisses. Use the static helpers for the common confirm / prompt shapes.
 */
public class SlateModal implements Popup {

    public static final int WIDTH = 240;
    private static final int PAD = 12, TITLE_H = 16, LINE_H = 10, BUTTON_H = 20;

    private final Component title;
    @Nullable private final Component body;
    @Nullable private final Icon icon;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final List<SlateButton> buttons = new ArrayList<>();
    private final List<AbstractWidget> extras = new ArrayList<>();
    private final Anim open = new Anim(0, 200, Ease.OUT_BACK);
    private int x, y, w, h;
    private int extrasHeight;
    @Nullable private AbstractWidget focused;
    @Nullable private Runnable onDismiss;
    private boolean closing;

    public SlateModal(final Component title, @Nullable final Component body, @Nullable final Icon icon) {
        this.title = title;
        this.body = body;
        this.icon = icon;
        this.w = WIDTH;
        open.snap(0);
        open.set(1);
    }

    /** Adds a button (left to right). PRIMARY variant for the default action. */
    public SlateModal button(final Component label, final SlateButton.Variant variant, final Runnable action) {
        final SlateButton b = new SlateButton(0, 0, 80, label, () -> { Popups.close(this); if (action != null) action.run(); }).variant(variant);
        buttons.add(b);
        widgets.add(b);
        return this;
    }

    /** Adds a widget above the buttons (full width, stacked). */
    public SlateModal extra(final AbstractWidget widget) {
        extras.add(widget);
        widgets.add(widget);
        extrasHeight += widget.getHeight() + 6;
        return this;
    }

    public SlateModal width(final int width) { this.w = width; return this; }

    /** Runs when the dialog closes for any reason (a button, Escape, screen change). */
    public SlateModal onDismiss(final Runnable r) { this.onDismiss = r; return this; }

    public void show() {
        Popups.open(this);
        if (!extras.isEmpty()) { focused = extras.get(0); focused.setFocused(true); }
    }

    public void close() { Popups.close(this); }

    private List<FormattedCharSequence> bodyLines() {
        return body == null ? List.of() : SlateDraw.font().split(body, w - PAD * 2);
    }

    private void layout() {
        final Minecraft mc = Minecraft.getInstance();
        final int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        w = Math.min(w, sw - 16);
        final int bodyH = bodyLines().size() * LINE_H;
        h = PAD + TITLE_H + (bodyH > 0 ? bodyH + 6 : 0) + extrasHeight + (buttons.isEmpty() ? 0 : BUTTON_H + 6) + PAD;
        x = (sw - w) / 2;
        y = (sh - h) / 2;
        int yy = y + PAD + TITLE_H + (bodyH > 0 ? bodyH + 6 : 0);
        for (final AbstractWidget e : extras) {
            e.setX(x + PAD);
            e.setY(yy);
            e.setWidth(w - PAD * 2);
            yy += e.getHeight() + 6;
        }
        // Buttons right-aligned, primary last (rightmost).
        int bx = x + w - PAD;
        for (int i = buttons.size() - 1; i >= 0; i--) {
            final SlateButton b = buttons.get(i);
            b.setWidth(Math.max(70, b.preferredWidth() + 12));
            bx -= b.getWidth();
            b.setX(bx);
            b.setY(y + h - PAD - BUTTON_H);
            bx -= 6;
        }
    }

    @Override
    public boolean isModal() { return true; }

    @Override
    public boolean contains(final double mx, final double my) { return true; }

    /** Current vertical animation offset (the dialog rises in / sinks out). */
    private int animDy() { return Math.round((1 - Math.min(1f, open.get())) * 10); }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        layout();
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float o = Math.max(0f, open.get());
        final float a = Math.min(1f, o * 1.5f);
        if (a <= 0.01f) return;
        final int dy = animDy();
        g.pose().pushPose();
        g.pose().translate(0, dy, 0);
        if (t.isVanilla()) {
            SlateDraw.vanillaDialog(g, x, y, w, h, a);
        } else {
            SlateDraw.shadow(g, x, y, w, h, 0.7f * a);
            SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(p.surface(), a), t.radius());
            SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(p.borderStrong(), a), t.radius());
        }
        int tx = x + PAD;
        if (icon != null) {
            final int ic = icon == Icon.WARNING ? p.warning() : icon == Icon.ERROR ? p.danger() : p.accent();
            Icons.draw(g, icon, tx, y + PAD - 1, 12, Colors.scaleAlpha(ic, a));
            tx += 16;
        }
        g.drawString(SlateDraw.font(), SlateDraw.truncate(Fonts.heading(title), x + w - PAD - tx), tx, y + PAD, Colors.scaleAlpha(p.text(), a), t.isVanilla());
        int by = y + PAD + TITLE_H;
        for (final FormattedCharSequence line : bodyLines()) {
            g.drawString(SlateDraw.font(), line, x + PAD, by, Colors.scaleAlpha(p.textMuted(), a), t.isVanilla());
            by += LINE_H;
        }
        for (final AbstractWidget wd : widgets) {
            wd.setAlpha(a);
            wd.render(g, closing ? -1 : mouseX, closing ? -1 : mouseY - dy, partialTick);
        }
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        layout();
        final double ay = my - animDy();
        for (final AbstractWidget wd : widgets) {
            if (wd.mouseClicked(mx, ay, button)) {
                if (focused != null && focused != wd) focused.setFocused(false);
                focused = wd;
                focused.setFocused(true);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(final double mx, final double my, final int button) {
        for (final AbstractWidget wd : widgets) wd.mouseReleased(mx, my - animDy(), button);
        return true;
    }

    @Override
    public boolean mouseDragged(final double mx, final double my, final int button, final double dx, final double dy) {
        return focused != null && focused.mouseDragged(mx, my - animDy(), button, dx, dy);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (focused != null && focused.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == 257 || keyCode == 335) {
            for (final SlateButton b : buttons) if (b.variant() == SlateButton.Variant.PRIMARY) { b.activate(); return true; }
        }
        if (keyCode == 258 && !widgets.isEmpty()) {          // tab cycles focus (shift = backwards)
            final boolean back = (modifiers & 1) != 0;
            int i = focused == null ? (back ? 0 : -1) : widgets.indexOf(focused);
            if (focused != null) focused.setFocused(false);
            i = ((i + (back ? -1 : 1)) % widgets.size() + widgets.size()) % widgets.size();
            focused = widgets.get(i);
            focused.setFocused(true);
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(final char c, final int modifiers) {
        return focused != null && focused.charTyped(c, modifiers);
    }

    @Override
    public void onClose() {
        if (onDismiss != null) onDismiss.run();
    }

    @Override
    public boolean beginClose() {
        if (Theme.current().motion() <= 0) return false;
        closing = true;
        open.set(0f, 120);
        open.ease(Ease.OUT_CUBIC);
        return true;
    }

    @Override
    public boolean closeFinished() { return open.get() <= 0.02f; }

    // ------------------------------------------------------------------ helpers

    public static void confirm(final Component title, final Component body, final Component okLabel, final Runnable onOk) {
        new SlateModal(title, body, Icon.QUESTION)
            .button(Component.translatable("gui.cancel"), SlateButton.Variant.SECONDARY, null)
            .button(okLabel, SlateButton.Variant.PRIMARY, onOk)
            .show();
    }

    public static void confirmDanger(final Component title, final Component body, final Component okLabel, final Runnable onOk) {
        new SlateModal(title, body, Icon.WARNING)
            .button(Component.translatable("gui.cancel"), SlateButton.Variant.SECONDARY, null)
            .button(okLabel, SlateButton.Variant.DANGER, onOk)
            .show();
    }

    public static void info(final Component title, final Component body) {
        new SlateModal(title, body, Icon.INFO).button(Component.translatable("gui.ok"), SlateButton.Variant.PRIMARY, null).show();
    }

    /** A dialog with one text field; {@code onOk} gets the entered text. */
    public static void prompt(final Component title, @Nullable final Component body, final String initial, final java.util.function.Consumer<String> onOk) {
        final SlateTextField field = new SlateTextField(0, 0, WIDTH - PAD * 2, Component.empty());
        field.setValue(initial == null ? "" : initial);
        final SlateModal m = new SlateModal(title, body, Icon.EDIT).extra(field);
        field.onEnter(() -> { Popups.close(m); onOk.accept(field.getValue()); });
        m.button(Component.translatable("gui.cancel"), SlateButton.Variant.SECONDARY, null)
         .button(Component.translatable("gui.ok"), SlateButton.Variant.PRIMARY, () -> onOk.accept(field.getValue()))
         .show();
    }
}
