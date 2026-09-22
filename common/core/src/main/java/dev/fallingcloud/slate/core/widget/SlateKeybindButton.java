package dev.fallingcloud.slate.core.widget;

import com.mojang.blaze3d.platform.InputConstants;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Shows a key mapping's current key; click to capture the next key/mouse button (Escape unbinds).
 * The conflict flag draws the label in the warning colour. Saving options.txt is the caller's job
 * ({@code Minecraft.getInstance().options.save()} + {@code KeyMapping.resetMapping()}).
 */
public class SlateKeybindButton extends SlateButton {

    private final KeyMapping mapping;
    private boolean capturing;
    private boolean conflict;
    @Nullable private final Consumer<KeyMapping> onChange;

    public SlateKeybindButton(final int x, final int y, final int width, final KeyMapping mapping, @Nullable final Consumer<KeyMapping> onChange) {
        super(x, y, width, Component.empty(), null);
        this.mapping = mapping;
        this.onChange = onChange;
        refresh();
    }

    public KeyMapping mapping() { return mapping; }

    public boolean isCapturing() { return capturing; }

    public SlateKeybindButton conflict(final boolean c) { this.conflict = c; return this; }

    public void refresh() {
        setMessage(capturing ? Component.literal("> ").append(mapping.getTranslatedKeyMessage()).append(" <")
            : mapping.getTranslatedKeyMessage());
    }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        pressAnim.snap(1);
        if (capturing) return;
        capturing = true;
        setFocused(true);
        refresh();
    }

    /** Screen calls this for mouse buttons while capturing (mouseClicked with button != 0 or any). */
    public boolean captureMouse(final int button) {
        if (!capturing) return false;
        mapping.setKey(InputConstants.Type.MOUSE.getOrCreate(button));
        finish();
        return true;
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (capturing) { captureMouse(button); return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (capturing) {
            if (keyCode == 256) mapping.setKey(InputConstants.UNKNOWN);
            else mapping.setKey(InputConstants.getKey(keyCode, scanCode));
            finish();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void finish() {
        capturing = false;
        KeyMapping.resetMapping();
        refresh();
        SlateSounds.tick();
        if (onChange != null) onChange.accept(mapping);
    }

    @Override
    protected void drawContent(final net.minecraft.client.gui.GuiGraphics g, final int x, final int y, final int w, final int h, final int fg, final boolean shadow) {
        int color = fg;
        if (capturing) color = Colors.scaleAlpha(Theme.current().accent(), effectiveAlpha());
        else if (conflict) color = Colors.scaleAlpha(Theme.current().palette().warning(), effectiveAlpha());
        super.drawContent(g, x, y, w, h, color, shadow);
    }
}
