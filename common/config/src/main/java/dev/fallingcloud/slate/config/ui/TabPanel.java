package dev.fallingcloud.slate.config.ui;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The scroll panel holding a tab's rows. After a tab switch it rises a few pixels into place (its rows fade and
 * rise on their own, see {@link Entrance}, so the whole motion stays vertical); {@code Theme.motion() == 0} snaps.
 */
public class TabPanel extends SlateScrollPanel {

    private static final int SLIDE_PX = 8;

    private final int baseY;
    private final Anim slide = new Anim(0, 240, Ease.OUT_CUBIC);

    public TabPanel(final int x, final int y, final int width, final int height) {
        super(x, y, width, height);
        this.baseY = y;
    }

    /** Any non-zero {@code dir} plays the entrance (the direction no longer matters: the content always rises). 0 = no slide. */
    public TabPanel slideFrom(final int dir) {
        if (dir != 0) {
            slide.snap(SLIDE_PX);
            slide.set(0);
        }
        return this;
    }

    @Override
    protected void renderWidget(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final int y = baseY + Math.round(slide.get());
        if (getY() != y) setY(y);
        super.renderWidget(g, mouseX, mouseY, partialTick);
    }
}
