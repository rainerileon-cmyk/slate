package dev.fallingcloud.slate.multiplayer.client.stream;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.ScreenIds;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateSpinner;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Full-screen viewer for one stream: the picture letterboxed into the content area, stats below, PiP / stop actions. */
public final class StreamViewerScreen extends SlateScreen {

    private final String streamId;

    public StreamViewerScreen(final String streamId, @Nullable final Screen parent) {
        super(Component.translatable("slate_multiplayer.stream.viewer"), parent);
        this.streamId = streamId;
    }

    static {
        ScreenIds.register(StreamViewerScreen.class, "slate_multiplayer:stream", "Stream viewer");
    }

    public String streamId() { return streamId; }

    @Override
    protected void build() {
        addHeaderAction(new SlateIconButton(0, 0, 20, Icon.CLOSE, Component.translatable("slate_multiplayer.stream.stop_watching"), () -> {
            StreamViewer.stop(streamId);
            back();
        }));
        addHeaderAction(new SlateIconButton(0, 0, 20, Icon.PIP, Component.translatable("slate_multiplayer.stream.pip"), () -> {
            StreamViewer.setPip(streamId, true);
            back();
        }));
        final Rect c = contentRect();
        add(new SlateButton(c.x(), c.bottom() - 20, 110, Component.translatable("slate_multiplayer.stream.stop_watching"), () -> {
            StreamViewer.stop(streamId);
            back();
        }).variant(SlateButton.Variant.SECONDARY).icon(Icon.STOP));
        add(new SlateButton(c.x() + 116, c.bottom() - 20, 90, Component.translatable("slate_multiplayer.stream.pip"), () -> {
            StreamViewer.setPip(streamId, true);
            back();
        }).variant(SlateButton.Variant.GHOST).icon(Icon.PIP));
    }

    @Override
    public void tick() {
        super.tick();
        if (StreamViewer.get(streamId) == null) back();
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final StreamViewer.Viewing v = StreamViewer.get(streamId);
        final Rect c = contentRect();
        final int picH = c.h() - 44;
        if (v == null) {
            SlateDraw.textCentered(g, Component.translatable("slate_multiplayer.stream.ended"), c.centerX(), c.y() + picH / 2, Theme.current().muted());
            return;
        }
        final Rect pic = new Rect(c.x(), c.y() + 4, c.w(), picH);
        if (!Theme.current().isVanilla()) SlateDraw.outline(g, pic.x() - 1, pic.y() - 1, pic.w() + 2, pic.h() + 2, Theme.current().border(), Theme.current().radius());
        StreamViewer.drawPicture(g, v, pic.x(), pic.y(), pic.w(), pic.h());
        if (v.stalled()) SlateSpinner.draw(g, pic.centerX() - 8, pic.centerY() - 8, 16, 0xFFFFFFFF);
        final List<Component> stats = StreamViewer.stats(v);
        int y = pic.bottom() + 6;
        for (int i = 0; i < stats.size(); i++) {
            g.drawString(font, stats.get(i), c.x(), y, i == 0 ? Theme.current().text() : Theme.current().muted(), Theme.current().isVanilla());
            y += 11;
        }
    }
}
