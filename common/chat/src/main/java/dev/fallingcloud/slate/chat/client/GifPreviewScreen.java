package dev.fallingcloud.slate.chat.client;

import dev.fallingcloud.slate.core.client.media.MediaDraw;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.media.MediaCache;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateSpinner;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import java.io.IOException;
import java.nio.file.Files;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The GIF that was just recorded, playing, with what can be done with it: send it into the chat that is open, open
 * the folder it was written to, or throw it away.
 */
public final class GifPreviewScreen extends SlateScreen {

    private final GifRecorder.Result gif;
    private final String id;
    private MediaCache.Entry playing;
    private Rect view = new Rect(0, 0, 0, 0);

    public GifPreviewScreen(@Nullable final Screen parent, final GifRecorder.Result gif) {
        super(Component.translatable("slate_chat.gif.title"), parent);
        this.gif = gif;
        this.id = "gif-recording-" + System.nanoTime();
    }

    @Override
    protected void build() {
        if (playing == null) playing = MediaCache.putLocal(id, gif.file(), MediaCache.KIND_IMAGE, 0);
        final Rect c = contentRect();
        view = new Rect(c.x(), c.y(), c.w(), c.h() - 28);
        final int y = c.bottom() - 20;
        final boolean can = ChatSend.canSendMedia();
        final SlateButton send = add(new SlateButton(c.right() - 110, y, 110, 20,
            Component.translatable("slate_chat.gif.send", ChatChannels.current().label()), this::send).variant(SlateButton.Variant.PRIMARY).icon(Icon.SEND));
        send.active = can;
        if (!can) send.tip(Component.translatable("slate_chat.media.no_server.body"));
        else if (gif.sendWidth() != gif.width()) send.tip(Component.translatable("slate_chat.gif.send.smaller", gif.sendWidth(), gif.toSend().length / 1024));
        add(new SlateButton(c.right() - 110 - 6 - 96, y, 96, 20, Component.translatable("slate_chat.gif.folder"),
            () -> Util.getPlatform().openFile(gif.saved().getParent().toFile())).variant(SlateButton.Variant.GHOST).icon(Icon.FOLDER));
        add(new SlateButton(c.x(), y, 84, 20, Component.translatable("slate_chat.gif.delete"), this::delete).variant(SlateButton.Variant.DANGER).icon(Icon.TRASH));
    }

    private void send() {
        ChatSend.image(gif.toSend());
        onClose();
    }

    private void delete() {
        try {
            Files.deleteIfExists(gif.saved());
        } catch (final IOException e) {
            SlateToasts.show(Component.translatable("slate_chat.gif.failed"), Component.literal(String.valueOf(e.getMessage())), Icon.WARNING);
        }
        onClose();
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        if (t.isVanilla()) {
            g.fill(view.x(), view.y(), view.right(), view.bottom(), 0x80000000);
            SlateDraw.outline(g, view.x(), view.y(), view.w(), view.h(), 0xFF000000, 0);
        } else {
            SlateDraw.panel(g, view.x(), view.y(), view.w(), view.h(), p.bg2(), p.border());
        }
        if (playing != null && playing.ready()) {
            MediaDraw.drawFit(g, playing, view.x() + 4, view.y() + 4, view.w() - 8, view.h() - 22, 1f, true, true);
        } else {
            SlateSpinner.draw(g, view.centerX() - 6, view.centerY() - 6, 12, t.isVanilla() ? 0xFFFFFFFF : p.accent());
        }
        final String info = "%d x %d   %d %s   %.1f s   %d KB".formatted(gif.width(), gif.height(), gif.frames(),
            Component.translatable("slate_chat.gif.frames").getString(), gif.millis() / 1000f, gif.file().length / 1024);
        g.drawString(font, info, view.x() + 6, view.bottom() - 13, t.isVanilla() ? 0xFFE0E0E0 : p.textMuted(), t.isVanilla());
        final String name = gif.saved().getFileName().toString();
        g.drawString(font, name, view.right() - 6 - font.width(name), view.bottom() - 13, t.isVanilla() ? 0xFFA0A0A0 : p.textDim(), t.isVanilla());
    }

    @Override
    public void removed() {
        MediaCache.forget(id);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
