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
 * "rename" dialogs). Modal: dims the screen and swallows input. Use the static helpers for the common
 * confirm / prompt shapes.
 */
public class SlateModal implements Popup {

    public static final int WIDTH = 240;

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

    public void show() {
        Popups.open(this);
        if (!extras.isEmpty()) { focused = extras.get(0); focused.setFocused(true); }
    }

    private List<FormattedCharSequence> bodyLines() {
        return body == null ? List.of() : SlateDraw.font().split(body, w - 24);
    }

    private void layout() {
        final Minecraft mc = Minecraft.getInstance();
        final int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        final int bodyH = bodyLines().size() * 10;
        h = 12 + 14 + (bodyH > 0 ? bodyH + 8 : 4) + extrasHeight + (buttons.isEmpty() ? 0 : 26) + 6;
        x = (sw - w) / 2;
        y = (sh - h) / 2;
        int yy = y + 12 + 14 + (bodyH > 0 ? bodyH + 8 : 4);
        for (final AbstractWidget e : extras) {
            e.setX(x + 12);
            e.setY(yy);
            e.setWidth(w - 24);
            yy += e.getHeight() + 6;
        }
        // Buttons right-aligned
        int bx = x + w - 12;
        for (int i = buttons.size() - 1; i >= 0; i--) {
            final SlateButton b = buttons.get(i);
            b.setWidth(Math.max(70, SlateDraw.width(b.getMessage()) + 24));
            bx -= b.getWidth();
            b.setX(bx);
            b.setY(y + h - 6 - 20);
            bx -= 6;
        }
    }

    @Override
    public boolean isModal() { return true; }

    @Override
    public boolean contains(final double mx, final double my) { return true; }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        layout();
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float o = open.get();
        final float a = Math.min(1f, o * 1.5f);
        final int yy = y + Math.round((1 - o) * 10);
        g.pose().pushPose();
        g.pose().translate(0, yy - y, 0);
        if (t.isVanilla()) {
            SlateDraw.vanillaPanel(g, x, y, w, h, false);
            SlateDraw.outline(g, x, y, w, h, 0xFF000000, 0);
            SlateDraw.outline(g, x + 1, y + 1, w - 2, h - 2, 0xFF8B8B8B, 0);
        } else {
            SlateDraw.shadow(g, x, y, w, h, 0.7f * a);
            SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(p.surface(), a), t.radius());
            SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(p.borderStrong(), a), t.radius());
        }
        int tx = x + 12;
        if (icon != null) { Icons.draw(g, icon, tx, y + 11, 12, Colors.scaleAlpha(p.accent(), a)); tx += 16; }
        g.drawString(SlateDraw.font(), Fonts.heading(title), tx, y + 12, Colors.scaleAlpha(p.text(), a), t.isVanilla());
        int by = y + 12 + 14;
        for (final FormattedCharSequence line : bodyLines()) {
            g.drawString(SlateDraw.font(), line, x + 12, by, Colors.scaleAlpha(p.textMuted(), a), t.isVanilla());
            by += 10;
        }
        for (final AbstractWidget wd : widgets) wd.render(g, mouseX, mouseY - (yy - y), partialTick);
        g.pose().popPose();
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        layout();
        for (final AbstractWidget wd : widgets) {
            if (wd.mouseClicked(mx, my, button)) {
                if (focused != null && focused != wd) focused.setFocused(false);
                focused = wd;
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(final double mx, final double my, final int button) {
        for (final AbstractWidget wd : widgets) wd.mouseReleased(mx, my, button);
        return true;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (focused != null && focused.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == 257 || keyCode == 335) {
            for (final SlateButton b : buttons) if (b.variant == SlateButton.Variant.PRIMARY) { b.onClick(0, 0); return true; }
        }
        if (keyCode == 258 && !widgets.isEmpty()) {          // tab cycles focus
            int i = focused == null ? -1 : widgets.indexOf(focused);
            if (focused != null) focused.setFocused(false);
            focused = widgets.get((i + 1) % widgets.size());
            focused.setFocused(true);
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(final char c, final int modifiers) {
        return focused != null && focused.charTyped(c, modifiers);
    }

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
        final SlateTextField field = new SlateTextField(0, 0, WIDTH - 24, Component.empty());
        field.setValue(initial == null ? "" : initial);
        final SlateModal m = new SlateModal(title, body, Icon.EDIT).extra(field);
        field.onEnter(() -> { Popups.close(m); onOk.accept(field.getValue()); });
        m.button(Component.translatable("gui.cancel"), SlateButton.Variant.SECONDARY, null)
         .button(Component.translatable("gui.ok"), SlateButton.Variant.PRIMARY, () -> onOk.accept(field.getValue()))
         .show();
    }
}
