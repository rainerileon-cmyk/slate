package dev.fallingcloud.slate.menu.client.loading;

import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateSpinner;
import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.menu.client.loading.journey.Journey;
import dev.fallingcloud.slate.core.screen.slot.CoreSlots;
import dev.fallingcloud.slate.core.screen.slot.Layout;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.menu.mixin.LevelLoadingScreenAccessor;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Slate's look for vanilla's loading screens: loading a world, joining a server, loading terrain, saving, and the
 * generic progress screen. The game drives those screen instances itself (the world loader feeds the chunk map, the
 * connection updates the status, the terrain wait closes the screen), so they cannot be swapped for Slate screens;
 * each keeps its logic and only its drawing is replaced ({@code *ScreenMixin} in {@code menu.mixin}): the screen's own
 * background (Slate's with the dark skin, the portal for a portal trip), then one floating card with a spinner, the
 * title, the status, the chunk map while the spawn area generates, a progress bar (sliding while the length of the
 * wait is unknown) and the screen's own buttons along the bottom.
 *
 * <p>That is the Custom layout. With the Overhaul layout the same screens are drawn by {@link Journey}: a scene in
 * place of the card.</p>
 */
public final class LoadingScreens {

    private static final int PAD = 12;
    private static final int MAX_W = 300;
    /** Largest size of the chunk map (a whole number of pixels per chunk, up to this). */
    private static final int MAP_MAX = 90;

    /** The screen last drawn, and the progress shown on it (eased towards the real value, which moves in steps). */
    private static @Nullable Screen lastScreen;
    private static float shownProgress;
    private static long lastFrameMs;

    /**
     * Whether Slate draws the loading screens: the {@code minecraft:level_loading} slot resolves to a Slate layout (the
     * global layout or its override, and menu.json's {@code loadingScreens} keeps the Custom mark on the slot).
     */
    public static boolean active() {
        return MenuSlots.effective(CoreSlots.LEVEL_LOADING) != Layout.VANILLA;
    }

    /**
     * One frame of a loading screen.
     *
     * @param progress 0..1, or negative while the length of the wait is unknown
     * @param chunks   the spawn area's chunk map (world loading only)
     * @param buttons  the screen's own widgets, laid out along the bottom of the card
     */
    public static void render(final Screen screen, final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick,
                              final Component title, @Nullable final Component status, final float progress,
                              @Nullable final StoringChunkProgressListener chunks, final List<? extends AbstractWidget> buttons) {
        if (Journey.active() && Journey.render(screen, g, mouseX, mouseY, partialTick, title, status, progress, chunks, buttons)) return;
        screen.renderBackground(g, mouseX, mouseY, partialTick);
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        final Font font = Minecraft.getInstance().font;

        final int w = Math.min(screen.width - 24, MAX_W);
        final int inner = w - PAD * 2;
        final List<FormattedCharSequence> lines = status == null || status.getString().isBlank() ? List.of() : font.split(status, inner);
        final int statusLines = Math.min(3, lines.size());
        final int diameter = chunks == null ? 0 : chunks.getDiameter();
        final int cell = diameter <= 0 ? 0 : Mth.clamp(MAP_MAX / diameter, 2, 8);
        final int mapH = diameter * cell;
        int h = PAD + 12 + 6;                                      // top padding, the title row, a gap
        h += statusLines == 0 ? 0 : statusLines * 10 + 6;
        h += mapH == 0 ? 0 : mapH + 12;
        h += 9;                                                    // the progress row
        h += buttons.isEmpty() ? PAD : 10 + buttons.size() * 24 - 4 + PAD;
        final int x = (screen.width - w) / 2, y = Math.max(4, (screen.height - h) / 2);
        SlateDraw.floatingPanel(g, x, y, w, h, 1f);

        // Spinner and title, the heading's accent cap under it on the dark skin.
        SlateSpinner.draw(g, x + PAD - 1, y + PAD - 1, 12, van ? 0xFFFFFFFF : p.accent());
        final Component heading = Fonts.heading(title);
        final FormattedCharSequence shownTitle = SlateDraw.truncate(heading, inner - 16);
        g.drawString(font, shownTitle, x + PAD + 16, y + PAD + 1, van ? 0xFFFFFFFF : p.text(), van);
        if (!van) SlateDraw.accentCap(g, x + PAD + 16, y + PAD + 11, Math.max(16, Math.min(font.width(shownTitle), 48)), 1f);
        int cy = y + PAD + 12 + 6;

        for (int i = 0; i < statusLines; i++) {
            g.drawString(font, lines.get(i), x + PAD, cy, van ? 0xFFC0C0C0 : p.textMuted(), van);
            cy += 10;
        }
        if (statusLines > 0) cy += 6;

        if (mapH > 0) {
            final int mapX = x + (w - mapH) / 2;
            // A sunken well under the map, like the build menu's slots.
            if (van) SlateDraw.rect(g, mapX - 3, cy - 3, mapH + 6, mapH + 6, 0x80000000);
            else {
                SlateDraw.pixelRound(g, mapX - 3, cy - 3, mapH + 6, mapH + 6, p.bg2(), t.radius());
                SlateDraw.outline(g, mapX - 3, cy - 3, mapH + 6, mapH + 6, p.border(), t.radius());
            }
            chunkMap(g, chunks, mapX, cy, cell);
            cy += mapH + 12;
        }

        progressRow(g, font, screen, x + PAD, cy, inner, progress, van, p);
        cy += 9;

        if (!buttons.isEmpty()) {
            cy += 10;
            for (final AbstractWidget b : buttons) {
                b.setX(x + PAD);
                b.setY(cy);
                b.setWidth(inner);
                b.render(g, mouseX, mouseY, partialTick);
                cy += 24;
            }
        }
    }

    /**
     * The spawn area's chunks in vanilla's colours per generation stage. Unlike vanilla's map, a chunk that has not
     * started yet is left out (vanilla paints it black, so the map starts as a black square), and with room for it the
     * cells keep a one-pixel gap.
     */
    private static void chunkMap(final GuiGraphics g, final StoringChunkProgressListener chunks, final int x, final int y, final int cell) {
        final Object2IntMap<ChunkStatus> colors = LevelLoadingScreenAccessor.slate$colors();
        final int d = chunks.getDiameter();
        final int gap = cell >= 4 ? 1 : 0;
        g.drawManaged(() -> {
            for (int r = 0; r < d; r++) {
                for (int s = 0; s < d; s++) {
                    final ChunkStatus status = chunks.getStatus(r, s);
                    if (status == null) continue;
                    final int cx = x + r * cell, cy = y + s * cell;
                    g.fill(cx, cy, cx + cell - gap, cy + cell - gap, colors.getInt(status) | 0xFF000000);
                }
            }
        });
    }

    /** The bar with the percentage at its right, or a segment sliding along it while the length of the wait is unknown. */
    private static void progressRow(final GuiGraphics g, final Font font, final Screen screen, final int x, final int y, final int w,
                                    final float progress, final boolean van, final Palette p) {
        final long now = Util.getMillis();
        final int track = van ? 0xFF404040 : p.border();
        final int fill = van ? 0xFF80FF80 : p.accent();
        if (progress < 0) {
            SlateDraw.rect(g, x, y + 3, w, 3, track);
            final int seg = Math.max(24, w / 3);
            final float phase = (now % 1400L) / 1400f;
            final int sx = x - seg + Math.round((w + seg) * phase);
            final int from = Math.max(x, sx), to = Math.min(x + w, sx + seg);
            if (to > from) SlateDraw.rect(g, from, y + 3, to - from, 3, fill);
            lastScreen = screen;
            shownProgress = 0;
            lastFrameMs = now;
            return;
        }
        final float target = Mth.clamp(progress, 0f, 1f);
        if (screen != lastScreen) {
            lastScreen = screen;
            shownProgress = target;
        } else {
            final float dt = Math.min(0.25f, (now - lastFrameMs) / 1000f);
            shownProgress += (target - shownProgress) * Math.min(1f, dt * 8f);
        }
        lastFrameMs = now;
        final Component pct = Component.literal(Math.round(target * 100) + "%");
        final int pctW = font.width(pct);
        final int barW = Math.max(20, w - pctW - 8);
        SlateDraw.rect(g, x, y + 3, barW, 3, track);
        SlateDraw.rect(g, x, y + 3, Math.round(barW * shownProgress), 3, fill);
        g.drawString(font, pct, x + w - pctW, y, van ? 0xFFFFFFFF : p.text(), van);
    }

    private LoadingScreens() {}
}
