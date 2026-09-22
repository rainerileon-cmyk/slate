package dev.fallingcloud.slate.core.client.media;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.media.Attachment;
import dev.fallingcloud.slate.core.media.ClipboardImages;
import dev.fallingcloud.slate.core.media.MediaCache;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The click-to-zoom viewer: full-resolution image (GIFs keep animating), wheel zooms towards the cursor,
 * drag pans, prev/next through a gallery, save to the screenshots folder, copy link. Clicking off the
 * image closes it, as does Escape.
 *
 * <p>Opening and closing share one factor - the image scales and fades between 0.88 and 1 while the
 * backdrop fades with it - so the two are mirrors rather than an animated entrance and an abrupt cut.
 * Zoom is a smoothed chase towards a target, so fast scrolling reads as one glide; the pan compensation
 * uses the target values, which keeps the point under the cursor pinned for the whole glide.</p>
 */
public final class ImageViewerScreen extends SlateScreen {

    private static final int OPEN_MS = 160;
    private static final int CLOSE_MS = 130;

    private final List<Attachment> gallery;
    private int index;
    private final long openedAt = Clock.nowMs();
    private long lastFrameNanos = System.nanoTime();
    private long closingAt;

    private float zoom = 1, zoomTarget = 1;
    private float panX, panY, panTargetX, panTargetY;
    private int imgX0, imgY0, imgX1, imgY1;
    private boolean fitted;
    private SlateIconButton prev, next;

    public ImageViewerScreen(@Nullable final Screen parent, final Attachment attachment) {
        this(parent, List.of(attachment), 0);
    }

    public ImageViewerScreen(@Nullable final Screen parent, final List<Attachment> gallery, final int index) {
        super(Component.translatable("slate.viewer.title"), parent);
        this.gallery = gallery.isEmpty() ? List.of() : List.copyOf(gallery);
        this.index = Math.max(0, Math.min(gallery.size() - 1, index));
        this.showHeader = false;
        this.showBack = false;
    }

    @Nullable
    private Attachment current() {
        return gallery.isEmpty() ? null : gallery.get(index);
    }

    @Override
    protected void build() {
        final int size = 20;
        int x = width - 6 - size;
        add(new SlateIconButton(x, 6, size, Icon.CLOSE, Component.translatable("gui.back"), this::onClose));
        x -= size + 2;
        final Attachment att = current();
        if (att != null && att.url() != null) {
            add(new SlateIconButton(x, 6, size, Icon.LINK, Component.translatable("slate.viewer.copy_link"), this::copyLink));
            x -= size + 2;
            add(new SlateIconButton(x, 6, size, Icon.EXTERNAL, Component.translatable("slate.viewer.open_link"), this::openLink));
            x -= size + 2;
        }
        add(new SlateIconButton(x, 6, size, Icon.SAVE, Component.translatable("slate.viewer.save"), this::save));
        x -= size + 2;
        add(new SlateIconButton(x, 6, size, Icon.REFRESH, Component.translatable("slate.viewer.reset"), this::resetView));
        if (gallery.size() > 1) {
            prev = add(new SlateIconButton(6, height / 2 - 10, size, Icon.CHEVRON_LEFT, Component.translatable("slate.viewer.prev"), () -> step(-1)));
            next = add(new SlateIconButton(width - 6 - size, height / 2 - 10, size, Icon.CHEVRON_RIGHT, Component.translatable("slate.viewer.next"), () -> step(1)));
        }
        fitted = false;
    }

    private void step(final int d) {
        if (gallery.size() <= 1) return;
        index = ((index + d) % gallery.size() + gallery.size()) % gallery.size();
        fitted = false;
        panTargetX = panTargetY = panX = panY = 0;
        rebuildWidgets();
    }

    private void resetView() {
        fitted = false;
        panTargetX = panTargetY = panX = panY = 0;
    }

    private void fitToScreen(final MediaCache.Entry e) {
        if (e.width > 0 && e.height > 0) {
            zoomTarget = Math.min(1f, Math.min((width - 40f) / e.width, (height - 40f) / e.height));
            zoom = zoomTarget;
            fitted = true;
        }
    }

    private void copyLink() {
        final Attachment att = current();
        if (att == null || att.url() == null) return;
        Minecraft.getInstance().keyboardHandler.setClipboard(att.url());
        SlateToasts.show(Component.translatable("slate.copied"), null, Icon.COPY);
    }

    private void openLink() {
        final Attachment att = current();
        if (att == null || att.url() == null) return;
        try {
            SlatePlatform.get().openUri(URI.create(att.url()));
        } catch (final Exception e) {
            SlateToasts.show(Component.translatable("slate.media.failed"), Component.literal(e.getMessage() == null ? "" : e.getMessage()), Icon.WARNING);
        }
    }

    /** Writes the original bytes (PNG/JPEG/GIF untouched) into the screenshots folder. */
    private void save() {
        final Attachment att = current();
        if (att == null) return;
        final MediaCache.Entry e = MediaCache.get(att);
        final byte[] bytes = e.bytes;
        if (bytes == null) {
            SlateToasts.show(Component.translatable("slate.viewer.save_failed"), Component.translatable("slate.media.loading"), Icon.WARNING);
            return;
        }
        final Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
        final String ext = ClipboardImages.extensionFor(bytes);
        final Path target = dir.resolve("slate-" + att.id() + "." + ext);
        final Thread t = new Thread(() -> {
            try {
                Files.createDirectories(dir);
                Files.write(target, bytes);
                Minecraft.getInstance().execute(() -> SlateToasts.show(Component.translatable("slate.viewer.saved"),
                    Component.literal(target.getFileName().toString()), Icon.SAVE, () -> net.minecraft.Util.getPlatform().openPath(dir)));
            } catch (final Exception ex) {
                Slate.LOGGER.warn("[Slate] could not save image: {}", ex.toString());
                Minecraft.getInstance().execute(() -> SlateToasts.show(Component.translatable("slate.viewer.save_failed"), Component.literal(String.valueOf(ex.getMessage())), Icon.WARNING));
            }
        }, "slate-save-image");
        t.setDaemon(true);
        t.start();
    }

    /** Starts the close animation; the real close happens once it finishes (see render). */
    @Override
    public void onClose() {
        if (closingAt == 0) closingAt = Clock.nowMs();
    }

    @Override
    public void back() {
        onClose();
    }

    private float appear() {
        if (Theme.current().motion() <= 0) return closingAt > 0 ? 0 : 1;
        if (closingAt > 0) {
            final float t = Mth.clamp((Clock.nowMs() - closingAt) / (float) Theme.current().ms(CLOSE_MS), 0, 1);
            return 1 - t * t;
        }
        final float t = Mth.clamp((Clock.nowMs() - openedAt) / (float) Theme.current().ms(OPEN_MS), 0, 1);
        return 1 - (1 - t) * (1 - t);
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final float a = appear();
        g.fill(0, 0, width, height, Colors.withAlpha(Theme.current().isVanilla() ? 0x000000 : 0x0A0A0A, Math.round(0xE4 * a)));
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final long now = System.nanoTime();
        final float dt = Math.min((now - lastFrameNanos) / 1_000_000_000f, 0.05f);
        lastFrameNanos = now;
        final float chase = Theme.current().motion() <= 0 ? 1f : 1 - (float) Math.pow(0.0005, dt);
        zoom += (zoomTarget - zoom) * chase;
        panX += (panTargetX - panX) * chase;
        panY += (panTargetY - panY) * chase;

        final float appear = appear();
        if (closingAt > 0 && appear <= 0.001f) {
            this.minecraft.setScreen(parent);
            return;
        }
        renderBackground(g, mouseX, mouseY, partialTick);

        final Attachment att = current();
        final MediaCache.Entry e = att == null ? null : MediaCache.get(att);
        final Palette p = Theme.current().palette();
        if (e == null || !e.ready() || e.frames.isEmpty()) {
            if (e != null) MediaDraw.drawPlaceholder(g, e, width / 2 - 20, height / 2 - 20, 40, 40, appear);
            if (e != null && e.status == MediaCache.Status.FAILED) SlateDraw.textCentered(g, MediaDraw.statusText(e), width / 2, height / 2 + 14, p.textMuted());
        } else {
            if (!fitted) fitToScreen(e);
            final float appearScale = 0.88f + 0.12f * appear;
            final int w = Math.max(1, Math.round(e.width * zoom * appearScale));
            final int h = Math.max(1, Math.round(e.height * zoom * appearScale));
            imgX0 = Math.round(width / 2f - w / 2f - panX * zoom * appearScale);
            imgY0 = Math.round(height / 2f - h / 2f - panY * zoom * appearScale);
            imgX1 = imgX0 + w;
            imgY1 = imgY0 + h;
            MediaDraw.drawFrame(g, e, imgX0, imgY0, w, h, appear);
            if (closingAt == 0) {
                final String who = e.sender.isEmpty() ? "" : e.sender + "  -  ";
                final String info = "%s%dx%d  %.0f%%%s".formatted(who, e.width, e.height, zoom * 100,
                    gallery.size() > 1 ? "  (%d/%d)".formatted(index + 1, gallery.size()) : "");
                g.drawString(font, info, 6, height - 14, Colors.withAlpha(p.textMuted(), Math.round(255 * appear)), Theme.current().isVanilla());
            }
        }
        // Widgets (toolbar) drawn after the image so they sit on top; fade with the screen.
        for (final net.minecraft.client.gui.components.Renderable r : renderableList()) {
            if (r instanceof net.minecraft.client.gui.components.AbstractWidget wd) wd.setAlpha(appear);
            r.render(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (closingAt > 0) return true;
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        final boolean onImage = mouseX >= imgX0 && mouseX < imgX1 && mouseY >= imgY0 && mouseY < imgY1;
        if (!onImage && button == 0) {
            onClose();
            return true;
        }
        return onImage;
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double dx, final double dy) {
        if (closingAt > 0) return true;
        final float oldTarget = zoomTarget;
        zoomTarget = Mth.clamp(zoomTarget * (dy > 0 ? 1.25f : 1 / 1.25f), 0.05f, 16f);
        // Anchor the image point under the cursor through the glide - computed against targets.
        final float ix = (float) ((mouseX - width / 2f) / oldTarget + panTargetX);
        final float iy = (float) ((mouseY - height / 2f) / oldTarget + panTargetY);
        panTargetX = ix - (float) (mouseX - width / 2f) / zoomTarget;
        panTargetY = iy - (float) (mouseY - height / 2f) / zoomTarget;
        return true;
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY) {
        if (closingAt > 0) return true;
        panTargetX -= (float) (dragX / Math.max(0.01f, zoom));
        panTargetY -= (float) (dragY / Math.max(0.01f, zoom));
        panX = panTargetX;                                     // dragging tracks the hand exactly; only zoom glides
        panY = panTargetY;
        return true;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        switch (keyCode) {
            case 263 -> { step(-1); return true; }                       // left
            case 262 -> { step(1); return true; }                        // right
            case 61, 334 -> { zoomTarget = Mth.clamp(zoomTarget * 1.25f, 0.05f, 16f); return true; }   // = / keypad +
            case 45, 333 -> { zoomTarget = Mth.clamp(zoomTarget / 1.25f, 0.05f, 16f); return true; }   // - / keypad -
            case 48, 320 -> { resetView(); return true; }                 // 0
            case 83 -> { if (Screen.hasControlDown()) { save(); return true; } }   // ctrl+s
            default -> {}
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
