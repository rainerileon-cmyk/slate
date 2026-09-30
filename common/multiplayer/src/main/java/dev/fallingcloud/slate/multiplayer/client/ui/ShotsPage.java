package dev.fallingcloud.slate.multiplayer.client.ui;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.gfx.Textures;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.module.Features;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.core.screen.SidebarScreen;
import dev.fallingcloud.slate.core.screen.slot.CoreSlots;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The Screenshots tab of the Overhaul friends screen: the pictures taken in the game, newest first, there to be
 * sent. A click on a picture takes it to Messages, where the friend or group to send it to is chosen. The whole
 * gallery, with everything else it can do, is the UI module's; a button goes there when that module is installed.
 */
final class ShotsPage extends FriendsHubScreen.HubPage {

    private record Shot(Path file, long modified) {}

    @Nullable private List<Shot> shots;
    private boolean listing;
    @Nullable private SidebarScreen host;

    ShotsPage(final FriendsHubScreen screen) {
        super(screen, "screenshots", UiUtil.t("page.screenshots"), Icon.CAMERA);
    }

    private static Path folder() {
        return SlatePlatform.get().gameDir().resolve("screenshots");
    }

    @Override
    public void build(final SidebarScreen s, final Rect area) {
        host = s;
        final boolean gallery = Features.present(MenuSlots.UI_MODULE);
        int right = area.right();
        if (gallery) {
            final int w = Math.min(120, Math.max(70, area.w() / 3));
            right -= w;
            s.addPageWidget(new SlateButton(right, area.y(), w, UiUtil.t("shots.gallery"), () -> MenuSlots.open(CoreSlots.SCREENSHOTS, s)).icon(Icon.IMAGE));
            right -= 4;
        }
        s.addPageWidget(new dev.fallingcloud.slate.core.widget.SlateIconButton(right - 20, area.y(), 20, Icon.FOLDER, UiUtil.t("shots.folder"), () -> {
            try { Files.createDirectories(folder()); } catch (final IOException ignored) {}
            Util.getPlatform().openPath(folder());
        }));

        if (shots == null && !listing) list();
        final List<Shot> have = shots;
        final SlateScrollPanel panel = s.addPageWidget(new SlateScrollPanel(area.x(), area.y() + 26, area.w(), Math.max(20, area.h() - 26)));
        if (have == null || have.isEmpty()) return;
        final int inner = panel.innerWidth(), gap = 4;
        final int columns = Math.max(2, inner / 104);
        final int tw = (inner - gap * (columns - 1)) / columns, th = Math.round(tw * 9f / 16f);
        int i = 0;
        for (final Shot shot : have) {
            final int col = i % columns, row = i / columns;
            final Thumb t = new Thumb(tw, th, shot);
            panel.add(t, col * (tw + gap), row * (th + gap));
            t.playEntrance(Math.min(240, i * 22));
            i++;
        }
        panel.setContentHeight(((have.size() + columns - 1) / columns) * (th + gap));
    }

    private void list() {
        listing = true;
        CompletableFuture.supplyAsync(() -> {
            final List<Shot> out = new ArrayList<>();
            final Path dir = folder();
            if (!Files.isDirectory(dir)) return out;
            try (Stream<Path> files = Files.list(dir)) {
                for (final Path p : (Iterable<Path>) files::iterator) {
                    final String n = p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
                    if (!n.endsWith(".png") || !Files.isRegularFile(p)) continue;
                    long at = 0;
                    try { at = Files.getLastModifiedTime(p).toMillis(); } catch (final IOException ignored) {}
                    out.add(new Shot(p, at));
                }
            } catch (final IOException e) {
                SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] cannot list screenshots: {}", e.toString());
            }
            out.sort(Comparator.comparingLong(Shot::modified).reversed());
            return out.size() > 120 ? new ArrayList<>(out.subList(0, 120)) : out;
        }, Util.ioPool()).thenAcceptAsync(found -> {
            shots = found;
            listing = false;
            if (host != null && host.currentPage() == this) host.refreshPage();
        }, Minecraft.getInstance());
    }

    @Override
    public void onHide() {
        shots = null;          // listed again next time: a picture may have been taken meanwhile
    }

    @Override
    public void render(final SidebarScreen s, final GuiGraphics g, final Rect area, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        g.drawString(SlateDraw.font(), SlateDraw.truncate(UiUtil.t("shots.hint"), Math.max(40, area.w() - 150)), area.x(), area.y() + 6, van ? 0xFFC0C0C0 : p.textMuted(), van);
        final List<Shot> have = shots;
        if (have == null || have.isEmpty()) {
            SlateDraw.textCentered(g, UiUtil.t(have == null ? "shots.listing" : "shots.none"), area.centerX(), area.y() + area.h() / 2, van ? 0xFFC0C0C0 : p.textMuted());
        }
    }

    /** One picture: its thumbnail, lifting under the pointer, with the word "Send" coming up over it. */
    private static final class Thumb extends SlateWidget {

        private final Shot shot;
        @Nullable private Textures.Loaded picture;
        private boolean asked;
        private final Anim fade = new Anim(0, 240, Ease.OUT_CUBIC);

        Thumb(final int width, final int height, final Shot shot) {
            super(0, 0, width, height, Component.literal(shot.file().getFileName().toString()));
            this.shot = shot;
            tip(List.of(getMessage(), UiUtil.t("shots.send_tip")));
        }

        @Override
        public void onClick(final double mouseX, final double mouseY) {
            super.onClick(mouseX, mouseY);
            FriendsHubScreen.shareImage(shot.file());
        }

        @Override
        protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            draw(g, false);
        }

        @Override
        protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            draw(g, true);
        }

        private void draw(final GuiGraphics g, final boolean van) {
            final Theme t = Theme.current();
            final Palette p = t.palette();
            final float a = effectiveAlpha();
            if (a <= 0.004f) return;
            if (!asked) {
                asked = true;
                Textures.loadThumbnail(shot.file(), 320, loaded -> picture = loaded);
            }
            final int x = getX(), y = getY() + enterOffset() - Math.round(hover() * 1.5f), w = getWidth(), h = getHeight();
            SlateDraw.shadow(g, x, y, w, h, 0.4f * a * (0.5f + 0.5f * hover()));
            SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(van ? 0xFF101010 : p.bg2(), a), van ? 0 : t.radius());
            final Textures.Loaded pic = picture;
            fade.set(pic != null);
            if (pic != null && fade.get() > 0.01f) {
                g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
                SlateDraw.blitFit(g, pic.id(), x + 1, y + 1, w - 2, h - 2, pic.width(), pic.height(), true, a * fade.get());
                g.disableScissor();
            }
            if (hover() > 0.01f) {
                SlateDraw.vgradient(g, x + 1, y + h - 22, w - 2, 21, 0, Colors.scaleAlpha(0xD0000000, a * hover()));
                Icons.draw(g, Icon.SEND, x + 5, y + h - 14, 10, Colors.scaleAlpha(van ? 0xFFFFFFFF : p.accent(), a * hover()));
                g.drawString(SlateDraw.font(), UiUtil.t("shots.send"), x + 18, y + h - 13, Colors.scaleAlpha(0xFFFFFFFF, a * hover()), false);
            }
            SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(van ? Colors.lerp(0xFF000000, 0xFFFFFFFF, hover()) : Colors.lerp(p.border(), p.accent(), hover()), a), van ? 0 : t.radius());
            SlateDraw.focusRing(g, x, y, w, h, focus() * a);
        }
    }
}
