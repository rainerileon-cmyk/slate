package dev.fallingcloud.slate.earlywindow;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.neoforged.fml.loading.progress.ProgressMeter;
import net.neoforged.fml.loading.progress.StartupNotificationManager;
import org.lwjgl.system.MemoryUtil;

/**
 * The start-up window's picture in Slate's dark look: the background with a vignette, a "Minecraft" wordmark with the
 * accent cap and the version under it, then one floating card as Slate's in-game loading screens draw it (spinner, what
 * is loading, the latest log line, the accent progress bar with its percentage, or a sliding segment while the length
 * is unknown), up to two sub-steps below, and a footer with the loader version and memory. Drawn every frame from FML's
 * progress ({@link StartupNotificationManager}), in GUI units at the game's automatic GUI scale for the canvas, in the
 * heading font chosen in Slate's settings; text sits on the game's own grid (capitals from the top, 10-unit lines).
 */
final class SlateScene {

    // Slate's dark palette (Core's Palette.dark).
    private static final int BG = 0xFF161615, BG2 = 0xFF1B1B1A, BORDER = 0xFF33332F;
    private static final int TEXT = 0xFFECEAE4, MUTED = 0xFFA19F97, DIM = 0xFF6E6C66, DANGER = 0xFFE5484D, SHADOW = 0x66000000;
    /** As the in-game loading card: padding, width, a text line. */
    private static final int PAD = 12, MAX_W = 300, LINE = 10;
    private static final long FADE_IN_NS = 350_000_000L;

    /** A heading font from Core's assets, and its sizes (em, GUI units) for the wordmark and all other text. */
    private record Face(String file, float title, float text, boolean crisp) {
        static Face of(final String key) {
            return switch (key) {
                case "monocraft" -> new Face("monocraft.ttf", 27, 9, true);
                case "pixelify" -> new Face("pixelify.ttf", 36, 10, false);
                default -> new Face("pixeloidsans.ttf", 27, 9, true);
            };
        }
    }

    private final int accent, radius;
    private final Face face;
    private final String mcVersion, loaderVersion;
    private final long start = System.nanoTime();
    private final Gfx gfx = new Gfx();
    private ByteBuffer ttf;
    private PixelFont title, text;
    private float bakedScale = -1;
    /** The main bar as shown, eased towards the real value (which moves in steps). */
    private float shown = -1;
    private long lastFrame = System.nanoTime();

    SlateScene(final int accent, final int radius, final String font, final String mcVersion, final String loaderVersion) {
        this.accent = accent;
        this.radius = radius;
        this.face = Face.of(font);
        this.mcVersion = mcVersion;
        this.loaderVersion = loaderVersion;
    }

    /** One frame on a {@code pixelW} x {@code pixelH} canvas (the one bound, its viewport set). */
    void draw(final int pixelW, final int pixelH) {
        final int scale = guiScale(pixelW, pixelH);
        final float w = pixelW / (float) scale, h = pixelH / (float) scale;
        gfx.begin(pixelW, pixelH, scale);
        try {
            fonts(scale);
            gfx.rect(0, 0, w, h, BG);
            gfx.vignette(w, h, 0x59000000);
            gfx.alpha(ease(Math.min(1f, (System.nanoTime() - start) / (float) FADE_IN_NS)));
            layout(w, h);
        } finally {
            gfx.end();
        }
    }

    /** Frees the GL objects; call with the context current. */
    void dispose() {
        if (title != null) {
            title.delete();
            text.delete();
            title = null;
        }
        gfx.delete();
        if (ttf != null) MemoryUtil.memFree(ttf);
        ttf = null;
    }

    /** The game's automatic GUI scale: the largest whole scale that keeps at least 320 x 240 units. */
    private static int guiScale(final int w, final int h) {
        return Math.max(1, Math.min(w / 320, h / 240));
    }

    private void fonts(final float scale) {
        if (scale == bakedScale) return;
        if (ttf == null) ttf = load("/slate_earlywindow/" + face.file());
        if (title != null) {
            title.delete();
            text.delete();
        }
        title = PixelFont.bake(ttf, face.title(), scale, PixelFont.ASCII, face.crisp());
        text = PixelFont.bake(ttf, face.text(), scale, PixelFont.LATIN_1, face.crisp());
        bakedScale = scale;
    }

    // ------------------------------------------------------------------ layout

    private void layout(final float w, final float h) {
        final List<ProgressMeter> meters = StartupNotificationManager.getCurrentProgress();
        final ProgressMeter main = meters.isEmpty() ? null : meters.get(0);
        final List<ProgressMeter> subs = meters.size() > 1 ? meters.subList(1, Math.min(3, meters.size())) : List.of();
        final String log = latestLog(main);

        final int cardW = (int) Math.min(w - 24, MAX_W), inner = cardW - PAD * 2;
        final int fixedH = PAD + 12 + 6 + 9 + PAD;             // padding, the heading row and a gap, the progress row
        final int logH = LINE + 6, subH = 6 + LINE + 1;         // a status line and its gap; a sub-step's label and bar
        final int cardH = fixedH + (log != null ? logH : 0) + subs.size() * subH;
        final float capH = title.capHeight();
        final float headH = capH + 6 + 2 + 6 + 7;               // the wordmark, the cap, the version's capitals
        final int gap = 20;
        // Placed for the tallest card (a log line and two sub-steps), so nothing moves as rows come and go.
        float y = Math.round(Math.max(8, (h - headH - gap - (fixedH + logH + 2 * subH)) * 0.45f));

        // Wordmark, the accent cap under it (fading out at both ends), the version.
        final String word = "Minecraft";
        title.draw(gfx, word, Math.round((w - title.width(word)) / 2), y, TEXT);
        y = Math.round(y + capH + 6);
        final float cap = Math.round(Math.min(96, title.width(word) * 0.6f) / 2) * 2;
        final int clear = accent & 0x00FFFFFF | 0x30000000;
        gfx.hgradient(Math.round(w / 2) - cap / 2, y, cap / 2, 2, clear, accent);
        gfx.hgradient(Math.round(w / 2), y, cap / 2, 2, accent, clear);
        y += 2 + 6;
        final String version = "Java Edition " + mcVersion;
        text.draw(gfx, version, Math.round((w - text.width(version)) / 2), y, MUTED);
        y = Math.round(y + 7 + gap);

        // The card: Slate's floating panel.
        final float x = Math.round((w - cardW) / 2);
        gfx.shadow(x, y, cardW, cardH, SHADOW, radius);
        gfx.pixelRound(x, y, cardW, cardH, BG & 0x00FFFFFF | 0xF0000000, radius);
        gfx.rect(x + radius + 1, y + 1, cardW - 2 * radius - 2, 1, 0x14FFFFFF);
        gfx.outline(x, y, cardW, cardH, BORDER, radius);

        // Spinner and heading, its accent cap under it, as the in-game card.
        spinner(x + PAD - 1, y + PAD - 1, 12);
        final String label = text.fit(label(main), inner - 16);
        text.draw(gfx, label, x + PAD + 16, y + PAD + 1, TEXT);
        final float capW = Math.max(16, Math.min(text.width(label), 48));
        gfx.hgradient(x + PAD + 16, y + PAD + 11, capW, 2, accent, clear);
        float cy = y + PAD + 12 + 6;

        if (log != null) {
            text.draw(gfx, text.fit(log, inner), x + PAD, cy, MUTED);
            cy += logH;
        }
        progressRow(x + PAD, cy, inner, main);
        cy += 9;

        for (final ProgressMeter m : subs) {
            cy += 6;
            final String count = m.steps() > 0 ? m.current() + " / " + m.steps() : "";
            final float countW = text.width(count);
            text.draw(gfx, text.fit(label(m), inner - countW - 8), x + PAD, cy, DIM);
            if (!count.isEmpty()) text.draw(gfx, count, x + PAD + inner - countW, cy, DIM);
            cy += LINE;
            bar(x + PAD, cy, inner, 1, m, BG2);
            cy += 1;
        }

        footer(w, h);
    }

    /** The main bar with the percentage at its right, as the in-game loading card's progress row. */
    private void progressRow(final float x, final float y, final float w, final ProgressMeter m) {
        final long now = System.nanoTime();
        if (m == null || m.steps() <= 0) {
            bar(x, y + 3, w, 3, null, BORDER);
            shown = -1;
            lastFrame = now;
            return;
        }
        final float target = Math.max(0f, Math.min(1f, m.progress()));
        if (shown < 0 || target < shown) shown = target;
        else shown += (target - shown) * Math.min(1f, (now - lastFrame) / 1e9f * 8f);
        lastFrame = now;
        final String pct = Math.round(target * 100) + "%";
        final float pctW = text.width(pct);
        final float barW = Math.max(20, w - pctW - 8);
        gfx.rect(x, y + 3, barW, 3, BORDER);
        gfx.rect(x, y + 3, Math.round(barW * shown), 3, accent);
        text.draw(gfx, pct, x + w - pctW, y, TEXT);
    }

    private void footer(final float w, final float h) {
        final float y = Math.round(h - 8 - 7);
        text.draw(gfx, "NeoForge " + loaderVersion, 10, y, DIM);
        final Runtime rt = Runtime.getRuntime();
        final long max = rt.maxMemory(), used = rt.totalMemory() - rt.freeMemory();
        final String mem = String.format(Locale.ROOT, "Memory %.1f / %.1f GB", used / 1073741824.0, max / 1073741824.0);
        final float barW = 40, right = Math.round(w - 10);
        text.draw(gfx, mem, right - barW - 6 - text.width(mem), y, DIM);
        final float frac = max > 0 ? Math.min(1f, used / (float) max) : 0;
        gfx.rect(right - barW, y + 3, barW, 2, BORDER);
        gfx.rect(right - barW, y + 3, Math.round(barW * frac), 2, frac > 0.85f ? DANGER : accent);
    }

    /** Core's pixel spinner: 8 dots round a ring, one lit and a short fading tail. */
    private void spinner(final float x, final float y, final float size) {
        final int dots = 8;
        final int lit = (int) ((System.nanoTime() / 90_000_000L) % dots);
        final float r = (size - 2) / 2f, cx = x + size / 2f, cy = y + size / 2f;
        final float dot = Math.max(1, (int) size / 8);
        for (int i = 0; i < dots; i++) {
            final double ang = i * Math.PI * 2 / dots;
            final float px = Math.round(cx + (float) Math.cos(ang) * r - dot / 2);
            final float py = Math.round(cy + (float) Math.sin(ang) * r - dot / 2);
            final int d = (i - lit + dots) % dots;
            final float a = d == 0 ? 1f : d == 1 ? 0.6f : d == 2 ? 0.35f : 0.15f;
            gfx.rect(px, py, dot, dot, withAlpha(accent, a));
        }
    }

    /** A meter's fill on its track, or a segment sliding along the track while its length is unknown. */
    private void bar(final float x, final float y, final float w, final float h, final ProgressMeter m, final int track) {
        gfx.rect(x, y, w, h, track);
        if (m != null && m.steps() > 0) {
            gfx.rect(x, y, Math.round(w * Math.max(0f, Math.min(1f, m.progress()))), h, accent);
            return;
        }
        final float seg = Math.max(24, w / 3);
        final float phase = (System.nanoTime() / 1_000_000L % 1400L) / 1400f;
        final float sx = Math.round(x - seg + (w + seg) * phase);
        final float from = Math.max(x, sx), to = Math.min(x + w, sx + seg);
        if (to > from) gfx.rect(from, y, to - from, h, accent);
    }

    private static String label(final ProgressMeter m) {
        if (m == null) return "Loading";
        final String text = m.label() != null ? m.label().getText() : null;
        return text != null && !text.isBlank() ? text : m.name();
    }

    /** The newest line of FML's start-up log, or null (or when it only repeats the heading). */
    private static String latestLog(final ProgressMeter main) {
        try {
            final String text = StartupNotificationManager.getMessages().stream()
                .min(Comparator.comparingInt(StartupNotificationManager.AgeMessage::age))
                .map(a -> a.message().getText())
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);
            return text == null || text.equals(label(main)) ? null : text;
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private static float ease(final float t) {
        final float u = 1 - t;
        return 1 - u * u * u;
    }

    private static int withAlpha(final int argb, final float a) {
        return Math.round(((argb >>> 24) & 0xFF) * a) << 24 | argb & 0x00FFFFFF;
    }

    private static ByteBuffer load(final String resource) {
        try (InputStream in = SlateScene.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("missing " + resource);
            final byte[] bytes = in.readAllBytes();
            return MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
        } catch (final IOException e) {
            throw new IllegalStateException("could not read " + resource, e);
        }
    }
}
