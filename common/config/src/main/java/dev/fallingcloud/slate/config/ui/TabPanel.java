package dev.fallingcloud.slate.config.ui;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The scroll panel holding a tab's rows. After a tab switch it slides a few pixels in from the side the
 * new tab lies on (its rows fade in on their own, see {@link Entrance}); {@code Theme.motion() == 0} snaps.
 */
public class TabPanel extends SlateScrollPanel {

    private static final int SLIDE_PX = 14;

    private final int baseX;
    private final Anim slide = new Anim(0, 240, Ease.OUT_CUBIC);

    public TabPanel(final int x, final int y, final int width, final int height) {
        super(x, y, width, height);
        this.baseX = x;
    }

    /** {@code dir} &gt; 0: the new tab is to the right, so the content comes in from the right. 0 = no slide. */
    public TabPanel slideFrom(final int dir) {
        if (dir != 0) {
            slide.snap(Integer.signum(dir) * SLIDE_PX);
            slide.set(0);
        }
        return this;
    }

    @Override
    protected void renderWidget(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final int x = baseX + Math.round(slide.get());
        if (getX() != x) setX(x);
        super.renderWidget(g, mouseX, mouseY, partialTick);
    }
}
