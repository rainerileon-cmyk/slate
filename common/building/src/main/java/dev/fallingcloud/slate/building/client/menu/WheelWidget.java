package dev.fallingcloud.slate.building.client.menu;

import dev.fallingcloud.slate.building.client.wheel.RadialWheel;
import dev.fallingcloud.slate.building.client.wheel.WheelSlice;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateTooltips;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A {@link RadialWheel} as a screen widget (build menu, wheel editor preview): the mouse hovers slices directly,
 * a click picks one ({@link #onPick}), the keyboard walks the ring when focused (arrows, Home/End, Enter/Space),
 * and a hovered slice shows its name (and lock) as a tooltip. Scrolling does nothing (design §5: no scroll select).
 */
public class WheelWidget extends SlateWidget {

    private final RadialWheel wheel = new RadialWheel();
    private @Nullable Consumer<Integer> onPick;
    private int keyboardIndex = RadialWheel.NONE;
    private int tipIndex = RadialWheel.NONE;
    private long tipSince;
    private boolean interactive = true;

    public WheelWidget(final int x, final int y, final int w, final int h) {
        super(x, y, w, h, Component.translatable("slate_building.ui.menu.wheel"));
        wheel.textured(true);
    }

    public RadialWheel wheel() { return wheel; }

    /** Called with the picked slice index or {@link RadialWheel#CENTER}. */
    public WheelWidget onPick(final Consumer<Integer> pick) {
        this.onPick = pick;
        return this;
    }

    /** False: display only (no hover, no clicks), e.g. the editor preview. */
    public WheelWidget interactive(final boolean on) {
        this.interactive = on;
        return this;
    }

    public void content(final List<WheelSlice> slices, final @Nullable WheelSlice center, final boolean animate) {
        wheel.content(slices, center, animate);
        if (keyboardIndex >= slices.size()) keyboardIndex = RadialWheel.NONE;
    }

    private int radius() {
        return Math.max(24, Math.min(getWidth(), getHeight()) / 2 - 7);
    }

    private void layoutWheel() {
        wheel.geometry(getX() + getWidth() / 2, getY() + enterOffset() + getHeight() / 2, radius());
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.active || !this.visible || !interactive || button != 0) return false;
        layoutWheel();
        final int hit = wheel.hitTest(mouseX, mouseY);
        if (hit == RadialWheel.NONE) return false;
        keyboardIndex = RadialWheel.NONE;
        pick(hit);
        return true;
    }

    private void pick(final int index) {
        if (onPick != null) onPick.accept(index);
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        return false;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!this.active || !this.visible || !interactive) return false;
        final int n = wheel.slices().size();
        final boolean hasCenter = wheel.center() != null;
        switch (keyCode) {
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN -> {
                if (n == 0) return false;
                keyboardIndex = keyboardIndex < 0 ? 0 : (keyboardIndex + 1) % n;
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP -> {
                if (n == 0) return false;
                keyboardIndex = keyboardIndex < 0 ? n - 1 : Math.floorMod(keyboardIndex - 1, n);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                if (!hasCenter) return false;
                keyboardIndex = RadialWheel.CENTER;
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                if (keyboardIndex == RadialWheel.NONE) return false;
                pick(keyboardIndex);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public void setFocused(final boolean focused) {
        super.setFocused(focused);
        if (!focused) keyboardIndex = RadialWheel.NONE;
    }

    @Override
    public void playDownSound(final net.minecraft.client.sounds.SoundManager handler) {}

    // ------------------------------------------------------------------ render

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, mouseX, mouseY);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, mouseX, mouseY);
    }

    private void draw(final GuiGraphics g, final int mouseX, final int mouseY) {
        final float a = effectiveAlpha();
        if (a <= 0.004f) return;
        layoutWheel();
        int hover = RadialWheel.NONE;
        if (interactive && this.active && this.isHovered() && g.containsPointInScissor(mouseX, mouseY)) hover = wheel.hitTest(mouseX, mouseY);
        if (hover == RadialWheel.NONE && isFocused()) hover = keyboardIndex;
        wheel.hover(hover);
        final float scale = 0.9f + 0.1f * Mth.clamp(a, 0f, 1f);
        wheel.render(g, a, scale);
        if (isFocused() && keyboardIndex == RadialWheel.NONE) {
            // A faint accent ring tells keyboard users the wheel has focus before they press an arrow.
            final int r = wheel.outerRadius() + 5;
            SlateDraw.pixelRing(g, wheel.centerX(), wheel.centerY(), r, Colors.scaleAlpha(Colors.withAlpha(Theme.current().accent(), 0x70), a * focus()));
        }
        tooltip(hover);
    }

    private void tooltip(final int hover) {
        if (hover != tipIndex) {
            tipIndex = hover;
            tipSince = Clock.nowMs();
        }
        if (hover == RadialWheel.NONE || Clock.nowMs() - tipSince < 180) return;
        final WheelSlice s = hover == RadialWheel.CENTER ? wheel.center() : hover < wheel.slices().size() ? wheel.slices().get(hover) : null;
        if (s == null) return;
        final List<Component> lines = new ArrayList<>();
        lines.add(s.label());
        if (!s.available()) lines.add(Component.translatable("slate_building.ui.wheel.unavailable").withStyle(ChatFormatting.GRAY));
        else if (s.lock() != null) lines.add(Component.empty().append(s.lock()).withStyle(ChatFormatting.GOLD));
        else if (s.current()) lines.add(Component.translatable("slate_building.ui.wheel.current").withStyle(ChatFormatting.GRAY));
        SlateTooltips.request(lines, this);
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput out) {
        final int h = wheel.hovered();
        final WheelSlice s = h == RadialWheel.CENTER ? wheel.center() : h >= 0 && h < wheel.slices().size() ? wheel.slices().get(h) : null;
        out.add(NarratedElementType.TITLE, s != null ? s.label() : getMessage());
    }
}
