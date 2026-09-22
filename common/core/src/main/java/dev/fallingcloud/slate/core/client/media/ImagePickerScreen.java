package dev.fallingcloud.slate.core.client.media;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.media.FilePicker;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateList;
import dev.fallingcloud.slate.core.widget.SlateSpinner;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * An in-game image file browser - a real Screen, not an OS dialog, so it works in exclusive fullscreen
 * (a native popup behind a fullscreen window is invisible and hijacks focus). Left: quick places + the
 * directory listing. Right: live preview of the selected image with its dimensions and size, and Send.
 * A "Browse..." button opens the native dialog ({@link FilePicker}) when the window is not fullscreen.
 *
 * <p>Listings are read synchronously (a local directory listing is instant); the preview decodes on a
 * worker thread and uploads on the render thread, replacing a single reused texture id.</p>
 */
public final class ImagePickerScreen extends SlateScreen {

    private static final ResourceLocation PREVIEW_TEX = Slate.id("picker_preview");
    private static final String[] PLACES = { "Pictures", "Downloads", "Desktop", "Screenshots" };

    /** Remembered across opens for the session - people send from the same folder repeatedly. */
    private static Path lastDir;

    private final Consumer<Path> onPick;
    private Path dir;
    private List<Path> entries = List.of();
    @Nullable private Path selected;
    private SlateList<Path> list;
    private SlateButton sendButton;
    private Rect previewRect = new Rect(0, 0, 0, 0);

    private volatile int previewW, previewH;
    private volatile long previewBytes;
    private volatile boolean previewReady, previewLoading;
    private int previewGeneration;

    /**
     * @param onPick receives the chosen file; the screen returns to {@code parent} first
     */
    public ImagePickerScreen(@Nullable final Screen parent, final Consumer<Path> onPick) {
        super(Component.translatable("slate.picker.title"), parent);
        this.onPick = onPick;
    }

    @Override
    protected void build() {
        if (dir == null) {
            dir = lastDir != null && Files.isDirectory(lastDir) ? lastDir : firstExisting();
        }
        final Rect c = contentRect();
        int x = c.x();
        for (final String name : PLACES) {
            final Path target = FilePicker.place(name);
            final SlateButton b = add(new SlateButton(x, c.y(), 62, 16, Component.translatable("slate.picker.place." + name.toLowerCase(Locale.ROOT)), () -> navigate(target))
                .variant(SlateButton.Variant.GHOST).icon(name.equals("Screenshots") ? Icon.CAMERA : Icon.FOLDER));
            b.active = target != null && Files.isDirectory(target);
            x += 64;
        }
        if (FilePicker.available()) {
            add(new SlateButton(x + 4, c.y(), 76, 16, Component.translatable("slate.picker.browse"), () ->
                FilePicker.pickImage(p -> { if (Files.isRegularFile(p)) pick(p); })).variant(SlateButton.Variant.GHOST).icon(Icon.EXTERNAL));
        }
        final int listTop = c.y() + 32;
        final int listW = Math.max(120, c.w() / 2 - 6);
        list = add(new SlateList<Path>(c.x(), listTop, listW, c.bottom() - listTop - 26, 12, new RowRenderer()));
        list.gap(1).emptyText(Component.translatable("slate.picker.empty"));
        list.onSelect(p -> { if (p != null && !isUp(p) && !Files.isDirectory(p)) select(p); });
        list.onActivate(p -> {
            if (p == null) return;
            if (isUp(p)) navigate(dir.getParent());
            else if (Files.isDirectory(p)) navigate(p);
            else pick(p);
        });
        previewRect = new Rect(c.x() + listW + 8, listTop, c.w() - listW - 8, c.bottom() - listTop - 26);
        sendButton = add(new SlateButton(c.right() - 90, c.bottom() - 20, 90, 20, Component.translatable("slate.picker.send"), () -> { if (selected != null) pick(selected); })
            .variant(SlateButton.Variant.PRIMARY).icon(Icon.SEND));
        sendButton.active = selected != null;
        refresh();
    }

    private static Path firstExisting() {
        for (final String name : PLACES) {
            final Path p = FilePicker.place(name);
            if (p != null && Files.isDirectory(p)) return p;
        }
        return Path.of(System.getProperty("user.home"));
    }

    /** The ".." row is the directory's parent (or the directory itself at a root). */
    private boolean isUp(final Path p) {
        return !entries.isEmpty() && p == entries.get(0) && dir.getParent() != null && p.equals(dir.getParent());
    }

    private void navigate(@Nullable final Path target) {
        if (target == null || !Files.isDirectory(target)) return;
        dir = target;
        lastDir = target;
        selected = null;
        previewReady = false;
        if (sendButton != null) sendButton.active = false;
        refresh();
    }

    private void refresh() {
        final List<Path> dirs = new ArrayList<>();
        final List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (final Path p : stream) {
                final String n = p.getFileName() == null ? "" : p.getFileName().toString();
                if (n.startsWith(".")) continue;
                if (Files.isDirectory(p)) dirs.add(p);
                else if (n.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|gif|webp)")) files.add(p);
            }
        } catch (final IOException | SecurityException e) {
            Slate.LOGGER.debug("[Slate] cannot list {}: {}", dir, e.toString());
        }
        dirs.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
        files.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
        final List<Path> all = new ArrayList<>(dirs.size() + files.size() + 1);
        if (dir.getParent() != null) all.add(dir.getParent());
        all.addAll(dirs);
        all.addAll(files);
        entries = all;
        if (list != null) list.items(all);
    }

    private void select(final Path p) {
        selected = p;
        previewReady = false;
        previewLoading = true;
        sendButton.active = true;
        final int generation = ++previewGeneration;
        final Thread t = new Thread(() -> {
            try {
                final byte[] data = Files.readAllBytes(p);
                final NativeImage img = NativeImage.read(new java.io.ByteArrayInputStream(data));
                Minecraft.getInstance().execute(() -> {
                    if (generation != previewGeneration) { img.close(); return; }   // stale - another file was clicked
                    Minecraft.getInstance().getTextureManager().release(PREVIEW_TEX);
                    final DynamicTexture tex = new DynamicTexture(img);
                    tex.setFilter(true, false);
                    Minecraft.getInstance().getTextureManager().register(PREVIEW_TEX, tex);
                    previewW = img.getWidth();
                    previewH = img.getHeight();
                    previewBytes = data.length;
                    previewReady = true;
                    previewLoading = false;
                });
            } catch (final Exception e) {
                Slate.LOGGER.debug("[Slate] preview failed for {}: {}", p, e.toString());
                previewLoading = false;
            }
        }, "slate-preview");
        t.setDaemon(true);
        t.start();
    }

    private void pick(final Path p) {
        back();
        if (onPick != null) onPick.accept(p);
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final Rect c = contentRect();
        // Path bar, left-truncated so the tail (the interesting part) stays visible.
        String path = dir.toString();
        while (font.width(path) > c.w() - 4 && path.length() > 8) path = "..." + path.substring(4);
        g.drawString(font, path, c.x(), c.y() + 20, p.textMuted(), t.isVanilla());

        final Rect pr = previewRect;
        if (t.isVanilla()) {
            g.fill(pr.x(), pr.y(), pr.right(), pr.bottom(), 0x80000000);
            SlateDraw.outline(g, pr.x(), pr.y(), pr.w(), pr.h(), 0xFF000000, 0);
        } else {
            SlateDraw.panel(g, pr.x(), pr.y(), pr.w(), pr.h(), p.bg2(), p.border());
        }
        if (selected != null && previewReady && previewW > 0) {
            final int[] s = MediaDraw.fit(previewW, previewH, pr.w() - 8, pr.h() - 20, false);
            RenderSystem.enableBlend();
            g.blit(PREVIEW_TEX, pr.x() + (pr.w() - s[0]) / 2, pr.y() + 4 + (pr.h() - 20 - s[1]) / 2, s[0], s[1], 0, 0, previewW, previewH, previewW, previewH);
            RenderSystem.disableBlend();
            final String info = "%dx%d  %d KB".formatted(previewW, previewH, previewBytes / 1024);
            g.drawString(font, info, pr.x() + 4, pr.bottom() - 12, p.textMuted(), t.isVanilla());
            final String name = selected.getFileName() == null ? "" : selected.getFileName().toString();
            g.drawString(font, SlateDraw.truncate(Component.literal(name), pr.w() - font.width(info) - 12), pr.x() + 8 + font.width(info), pr.bottom() - 12, p.textDim(), t.isVanilla());
        } else if (selected != null && previewLoading) {
            SlateSpinner.draw(g, pr.centerX() - 6, pr.centerY() - 6, 12, t.isVanilla() ? 0xFFFFFFFF : p.accent());
        } else {
            SlateDraw.textCentered(g, Component.translatable(selected == null ? "slate.picker.hint" : "slate.media.failed"), pr.centerX(), pr.centerY() - 4, p.textDim());
        }
    }

    @Override
    public void removed() {
        Minecraft.getInstance().getTextureManager().release(PREVIEW_TEX);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** One listing row: icon + name; the parent row reads "..". */
    private final class RowRenderer implements SlateList.RowRenderer<Path> {
        @Override
        public void render(final GuiGraphics g, final Path item, final int index, final int x, final int y, final int w, final int h,
                           final boolean hovered, final boolean selectedRow, final int mouseX, final int mouseY) {
            final Theme t = Theme.current();
            final Palette p = t.palette();
            final boolean up = isUp(item);
            final boolean isDir = up || Files.isDirectory(item);
            final Icon icon = up ? Icon.ARROW_UP : isDir ? Icon.FOLDER : Icon.IMAGE;
            final int fg = isDir ? (t.isVanilla() ? 0xFFFFE080 : p.warning()) : (hovered || selectedRow ? p.text() : p.textMuted());
            Icons.draw(g, icon, x + 3, y + 2, 8, Colors.withAlpha(fg, 0xD0));
            final String label = up ? ".." : item.getFileName() == null ? item.toString() : item.getFileName().toString();
            g.drawString(font, SlateDraw.truncate(Component.literal(label), w - 18), x + 14, y + 2, fg, t.isVanilla());
        }
    }
}
