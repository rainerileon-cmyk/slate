package dev.fallingcloud.slate.multiplayer.client.ui;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.popup.Popup;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A modal one-line text prompt (nickname, note, group name). Uses {@link ChatInput} for the field so the
 * frame follows the dialog wherever it is centred; Enter confirms, Escape cancels.
 */
final class PromptPopup implements Popup {

    private static final int W = 240;

    private final Component title;
    @Nullable private final Component body;
    private final ChatInput input;
    private final SlateButton ok, cancel;
    private final Consumer<String> onOk;
    private final Anim open = new Anim(0, 200, Ease.OUT_BACK);
    private int x, y, h;

    private PromptPopup(final Component title, @Nullable final Component body, final String initial, final int maxLength, final Consumer<String> onOk) {
        this.title = title;
        this.body = body;
        this.onOk = onOk;
        this.input = new ChatInput(0, 0, W - 24).maxLength(maxLength).onSend(this::confirm);
        this.input.setValue(initial == null ? "" : initial);
        this.ok = new SlateButton(0, 0, 70, Component.translatable("gui.ok"), this::confirm).variant(SlateButton.Variant.PRIMARY);
        this.cancel = new SlateButton(0, 0, 70, Component.translatable("gui.cancel"), () -> Popups.close(this));
        open.snap(0);
        open.set(1);
    }

    static void open(final Component title, @Nullable final Component body, final String initial, final int maxLength, final Consumer<String> onOk) {
        final PromptPopup p = new PromptPopup(title, body, initial, maxLength, onOk);
        Popups.open(p);
        p.input.setFocused(true);
    }

    private void confirm() {
        Popups.close(this);
        onOk.accept(input.value().replace('\n', ' ').strip());
    }

    private void layout() {
        final Minecraft mc = Minecraft.getInstance();
        final int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        final int bodyH = body == null ? 0 : SlateDraw.font().split(body, W - 24).size() * 10 + 6;
        h = 12 + 14 + bodyH + 4 + 20 + 8 + 20 + 10;
        x = (sw - W) / 2;
        y = (sh - h) / 2;
        input.setX(x + 12);
        input.setY(y + 12 + 14 + bodyH + 4);
        input.setWidth(W - 24);
        ok.setX(x + W - 12 - 70);
        ok.setY(y + h - 10 - 20);
        cancel.setX(ok.getX() - 76);
        cancel.setY(ok.getY());
    }

    @Override public boolean isModal() { return true; }

    @Override public boolean contains(final double mouseX, final double mouseY) { return true; }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        layout();
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float o = open.get();
        final float a = Math.min(1f, o * 1.5f);
        if (t.isVanilla()) {
            SlateDraw.vanillaPanel(g, x, y, W, h, false);
            SlateDraw.outline(g, x, y, W, h, 0xFF000000, 0);
            SlateDraw.outline(g, x + 1, y + 1, W - 2, h - 2, 0xFF8B8B8B, 0);
        } else {
            SlateDraw.shadow(g, x, y, W, h, 0.7f * a);
            SlateDraw.pixelRound(g, x, y, W, h, Colors.scaleAlpha(p.surface(), a), t.radius());
            SlateDraw.outline(g, x, y, W, h, Colors.scaleAlpha(p.borderStrong(), a), t.radius());
        }
        Icons.draw(g, Icon.EDIT, x + 12, y + 11, 12, Colors.scaleAlpha(p.accent(), a));
        g.drawString(SlateDraw.font(), Fonts.heading(title), x + 28, y + 12, Colors.scaleAlpha(p.text(), a), t.isVanilla());
        if (body != null) {
            int by = y + 12 + 14;
            for (final var line : SlateDraw.font().split(body, W - 24)) {
                g.drawString(SlateDraw.font(), line, x + 12, by, Colors.scaleAlpha(p.textMuted(), a), t.isVanilla());
                by += 10;
            }
        }
        input.render(g, mouseX, mouseY, partialTick);
        ok.render(g, mouseX, mouseY, partialTick);
        cancel.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        layout();
        if (ok.mouseClicked(mouseX, mouseY, button) || cancel.mouseClicked(mouseX, mouseY, button)) return true;
        if (input.mouseClicked(mouseX, mouseY, button)) return true;
        return true;
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {
        ok.mouseReleased(mouseX, mouseY, button);
        cancel.mouseReleased(mouseX, mouseY, button);
        return true;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (keyCode == 256) { Popups.close(this); return true; }
        return input.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(final char c, final int modifiers) {
        return input.charTyped(c, modifiers);
    }
}
