package dev.fallingcloud.slate.menu.client.screenshots;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.gfx.Textures;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.core.widget.SlateSpinner;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import dev.fallingcloud.slate.menu.api.ScreenshotShareProvider;
import dev.fallingcloud.slate.menu.api.SlateMenuApi;
import dev.fallingcloud.slate.menu.client.Fmt;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Full-screen viewer: fit / 1:1, wheel zoom around the cursor, drag to pan, arrow keys or buttons for
 * previous / next, and the same file actions as the gallery. Deleting or renaming here is reflected
 * when the gallery is shown again (it rescans on re-add).
 */
public final class ScreenshotViewer extends SlateScreen {

    private final List<Screenshots.Shot> shots;
    private int index;
    @Nullable private Textures.Loaded image;
    private boolean loading;
    private boolean fit = true;
    private float zoom = 1f;
    private double panX, panY;
    private boolean dragging;
    private final Anim fade = new Anim(0, 200, Ease.OUT_CUBIC);
    @Nullable private SlateIconButton fitButton;

    public ScreenshotViewer(@Nullable final Screen parent, final List<Screenshots.Shot> shots, final int index) {
        super(Component.translatable("slate_menu.screenshots.title"), parent);
        this.shots = shots;
        this.index = Math.max(0, Math.min(shots.size() - 1, index));
        this.maxContentWidth = 0;
    }

    @Nullable private Screenshots.Shot current() { return shots.isEmpty() ? null : shots.get(index); }

    @Override
    public Component getTitle() {
        final Screenshots.Shot s = current();
        return s == null ? super.getTitle() : Component.literal(s.fileName());
    }

    // ------------------------------------------------------------------ build

    @Override
    protected void build() {
        addHeaderAction(new SlateIconButton(0, 0, 20, Icon.CHEVRON_RIGHT, Component.translatable("slate_menu.screenshots.next"), () -> step(1)));
        addHeaderAction(new SlateIconButton(0, 0, 20, Icon.CHEVRON_LEFT, Component.translatable("slate_menu.screenshots.prev"), () -> step(-1)));
        fitButton = addHeaderAction(new SlateIconButton(0, 0, 20, fit ? Icon.FULLSCREEN : Icon.WINDOWED,
            Component.translatable(fit ? "slate_menu.screenshots.actual_size" : "slate_menu.screenshots.fit"), this::toggleFit));
        addHeaderAction(new SlateIconButton(0, 0, 20, Icon.TRASH, Component.translatable("slate_menu.screenshots.delete"), this::confirmDelete));
        addHeaderAction(new SlateIconButton(0, 0, 20, Icon.EDIT, Component.translatable("slate_menu.screenshots.rename"), this::rename));
        addHeaderAction(new SlateIconButton(0, 0, 20, Icon.COPY, Component.translatable("slate_menu.screenshots.copy"), () -> { final Screenshots.Shot s = current(); if (s != null) SlateScreenshotsScreen.copy(s); }));
        final ScreenshotShareProvider share = SlateMenuApi.screenshotShareProvider();
        if (share != null) addHeaderAction(new SlateIconButton(0, 0, 20, Icon.SEND, Component.translatable("slate_menu.screenshots.share"), () -> { final Screenshots.Shot s = current(); if (s != null) share.share(s.path()); }));
        addHeaderAction(new SlateIconButton(0, 0, 20, Icon.FOLDER, Component.translatable("slate_menu.screenshots.open_folder"), () -> Util.getPlatform().openPath(Screenshots.dir())));
        if (image == null && !loading) loadImage();
    }

    private void loadImage() {
        final Screenshots.Shot s = current();
        image = null;
        if (s == null) return;
        loading = true;
        fade.snap(0);
        Textures.load(s.path(), l -> {
            if (current() != s) return;
            image = l;
            loading = false;
            fade.set(1);
        });
    }

    private void step(final int dir) {
        if (shots.isEmpty()) return;
        index = ((index + dir) % shots.size() + shots.size()) % shots.size();
        panX = panY = 0;
        fit = true;
        zoom = 1f;
        loading = false;
        loadImage();
        if (fitButton != null) { fitButton.setIcon(Icon.FULLSCREEN); fitButton.tip(Component.translatable("slate_menu.screenshots.actual_size")); }
    }

    private void toggleFit() {
        if (fit) { fit = false; zoom = 1f; panX = panY = 0; }
        else { fit = true; panX = panY = 0; }
        if (fitButton != null) {
            fitButton.setIcon(fit ? Icon.FULLSCREEN : Icon.WINDOWED);
            fitButton.tip(Component.translatable(fit ? "slate_menu.screenshots.actual_size" : "slate_menu.screenshots.fit"));
        }
    }

    private void rename() {
        final Screenshots.Shot s = current();
        if (s == null) return;
        SlateModal.prompt(Component.translatable("slate_menu.screenshots.rename"), Component.translatable("slate_menu.screenshots.rename_body"), s.name(), name -> {
            if (name.isBlank() || name.trim().equals(s.name())) return;
            if (Screenshots.rename(s, name)) {
                final String file = s.fileName();
                final int dot = file.lastIndexOf('.');
                final String ext = dot > 0 ? file.substring(dot) : ".png";
                final java.nio.file.Path np = s.path().resolveSibling(name.trim() + ext);
                shots.set(index, new Screenshots.Shot(np, name.trim(), name.trim() + ext, s.modified(), s.size()));
                loadImage();
            } else {
                SlateToasts.show(Component.translatable("slate_menu.screenshots.rename_failed"), Component.literal(name), Icon.WARNING);
            }
        });
    }

    private void confirmDelete() {
        final Screenshots.Shot s = current();
        if (s == null) return;
        SlateModal.confirmDanger(Component.translatable("slate_menu.screenshots.delete"), Component.translatable("slate_menu.screenshots.delete_body", s.fileName()),
            Component.translatable("slate_menu.screenshots.delete"), () -> {
                if (!Screenshots.delete(s)) return;
                SlateToasts.show(Component.translatable("slate_menu.screenshots.deleted"), Component.literal(s.fileName()), Icon.TRASH);
                shots.remove(index);
                if (shots.isEmpty()) { back(); return; }
                index = Math.min(index, shots.size() - 1);
                image = null;
                loadImage();
            });
    }

    // ------------------------------------------------------------------ geometry

    private float scale(final Rect area) {
        if (image == null) return 1f;
        if (!fit) return zoom;
        return Math.min(1f, Math.min((float) area.w() / image.width(), (float) area.h() / image.height()));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        final Rect area = contentRect();
        if (image == null || !area.contains(mouseX, mouseY)) return false;
        final float old = scale(area);
        final float target = Mth.clamp(old * (scrollY > 0 ? 1.2f : 1 / 1.2f), 0.05f, 12f);
        // Keep the pixel under the cursor fixed.
        final double cx = area.centerX() + panX, cy = area.centerY() + panY;
        final double dx = mouseX - cx, dy = mouseY - cy;
        panX -= dx * (target / old - 1);
        panY -= dy * (target / old - 1);
        fit = false;
        zoom = target;
        if (fitButton != null) { fitButton.setIcon(Icon.WINDOWED); fitButton.tip(Component.translatable("slate_menu.screenshots.fit")); }
        return true;
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && contentRect().contains(mouseX, mouseY)) { dragging = true; return true; }
        return false;
    }

    @Override
    public boolean mouseReleased(final double mouseX, final double mouseY, final int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(final double mouseX, final double mouseY, final int button, final double dragX, final double dragY) {
        if (dragging && button == 0) { panX += dragX; panY += dragY; return true; }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        switch (keyCode) {
            case 263, 266 -> { step(-1); return true; }              // left, page up
            case 262, 267, 32 -> { step(1); return true; }           // right, page down, space
            case 48, 320 -> { fit = true; panX = panY = 0; return true; }        // 0
            case 49, 321 -> { fit = false; zoom = 1f; panX = panY = 0; return true; }   // 1
            case 61, 334 -> { fit = false; zoom = Mth.clamp(scale(contentRect()) * 1.2f, 0.05f, 12f); return true; }   // + / =
            case 45, 333 -> { fit = false; zoom = Mth.clamp(scale(contentRect()) / 1.2f, 0.05f, 12f); return true; }   // -
            case 261 -> { confirmDelete(); return true; }
            case 291 -> { rename(); return true; }                      // F2
            case 67 -> { if (hasControlDown()) { final Screenshots.Shot s = current(); if (s != null) SlateScreenshotsScreen.copy(s); return true; } }
            default -> {}
        }
        return false;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        if (!Theme.current().isVanilla()) g.fill(0, HEADER_H, width, height, 0x80000000);
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final Rect area = contentRect();
        final Screenshots.Shot s = current();
        if (s == null) {
            SlateDraw.textCentered(g, Component.translatable("slate_menu.screenshots.empty"), area.centerX(), area.centerY(), p.textMuted());
            return;
        }
        if (image == null) {
            SlateSpinner.draw(g, area.centerX() - 8, area.centerY() - 8, 16, t.isVanilla() ? 0xFFFFFFFF : p.accent());
            return;
        }
        final float sc = scale(area);
        final int dw = Math.max(1, Math.round(image.width() * sc)), dh = Math.max(1, Math.round(image.height() * sc));
        final float a = fade.get();
        SlateDraw.scissor(g, area.x(), area.y(), area.w(), area.h());
        g.pose().pushPose();
        g.pose().translate(area.centerX() + panX, area.centerY() + panY, 0);
        g.pose().scale(sc, sc, 1f);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.setColor(1, 1, 1, a);
        g.blit(image.id(), -image.width() / 2, -image.height() / 2, image.width(), image.height(), 0, 0, image.width(), image.height(), image.width(), image.height());
        g.setColor(1, 1, 1, 1);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        g.pose().popPose();
        SlateDraw.unscissor(g);
        // Info bar
        final Component info = Component.literal((index + 1) + " / " + shots.size() + " · " + image.width() + "×" + image.height()
            + " · " + Fmt.bytes(s.size()) + " · " + Math.round(sc * 100) + "%" + (fit ? " (" + Component.translatable("slate_menu.screenshots.fit").getString() + ")" : ""));
        final int iw = font.width(info) + 12;
        final int ix = area.centerX() - iw / 2, iy = area.bottom() - 16;
        SlateDraw.pixelRound(g, ix, iy, iw, 14, Colors.withAlpha(t.isVanilla() ? 0x000000 : p.bg(), 0xC0), t.radius());
        g.drawString(font, info, ix + 6, iy + 3, t.isVanilla() ? 0xFFFFFFFF : p.textMuted(), t.isVanilla());
        if (dw < 8 || dh < 8) { /* nothing */ }
    }
}
