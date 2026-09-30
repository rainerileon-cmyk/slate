package dev.fallingcloud.slate.core.stage;

import dev.fallingcloud.slate.core.stage.node.StageNode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;

/**
 * A {@link Stage} as a widget: it renders the stage in its rectangle, hands mouse and keys to it, and takes part in
 * the screen's Tab order (when the widget is focused, Tab and the arrows cycle the stage's pickable nodes; at the
 * end of the list Tab moves on to the next widget). Narration reads the focused node's name. The widget does not
 * own the stage: close it from the screen.
 */
public class StageWidget extends AbstractWidget {

    private final Stage stage;

    public StageWidget(final int x, final int y, final int width, final int height, final Stage stage, final Component label) {
        super(x, y, width, height, label);
        this.stage = stage;
    }

    public Stage stage() { return stage; }

    @Override
    protected void renderWidget(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        stage.focusVisible(isFocused());
        final boolean mouse = isHovered() && g.containsPointInScissor(mouseX, mouseY);
        stage.render(g, getX(), getY(), getWidth(), getHeight(), mouse ? mouseX : -1e9, mouse ? mouseY : -1e9, partialTick);
    }

    /**
     * Takes the click only when something on the stage is under the pointer: a stage often fills the whole screen,
     * and the widgets drawn over its empty parts must still get their clicks.
     */
    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.active || !this.visible || !isMouseOver(mouseX, mouseY)) return false;
        return stage.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {
        return stage.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        return this.active && this.visible && stage.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void setFocused(final boolean focused) {
        super.setFocused(focused);
        stage.focusVisible(focused);
        if (focused && stage.focused() == null) stage.focusNext(1);
    }

    @Override
    public void playDownSound(final SoundManager handler) {
        // Nodes play their own click.
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, getMessage());
        final StageNode n = stage.focused() != null ? stage.focused() : stage.hovered();
        if (n != null) out.add(NarratedElementType.USAGE, n.name());
    }
}
