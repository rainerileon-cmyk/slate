package dev.fallingcloud.slate.multiplayer.client.stream;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.gfx.Textures;
import dev.fallingcloud.slate.core.layout.LayoutApplier;
import dev.fallingcloud.slate.core.net.blob.BlobPayloads;
import dev.fallingcloud.slate.core.net.blob.BlobReceiver;
import dev.fallingcloud.slate.core.screen.ScreenIds;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateSpinner;
import dev.fallingcloud.slate.multiplayer.MultiplayerConfig;
import dev.fallingcloud.slate.multiplayer.MultiplayerConfigs;
import dev.fallingcloud.slate.multiplayer.client.SocialClient;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage;
import dev.fallingcloud.slate.multiplayer.social.StreamInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Watching friends' streams: frames arrive as {@code frame} blobs, each becomes a texture (the previous
 * one is released). A stream is shown either in {@link StreamViewerScreen} or as a draggable
 * picture-in-picture window - drawn on the HUD when no screen is open, and added to every non-container
 * screen as a real widget (so it can be dragged) while a stream is in PiP mode. Client main thread only.
 */
public final class StreamViewer {

    /** One stream being watched. */
    public static final class Viewing {
        public StreamInfo info;
        @Nullable public Textures.Loaded texture;
        public boolean pip;
        public int seq;
        public long lastFrameMs;
        public float fps;
        public long latencyMs;
        public int frameBytes;
        public long frames;
        public final long startedMs = System.currentTimeMillis();
        @Nullable SocialMessage.StreamFrame pending;

        Viewing(final StreamInfo info) { this.info = info; }

        public String title() { return info.title().isEmpty() ? info.owner().display() : info.title(); }

        public float aspect() { return texture == null || texture.height() == 0 ? 16f / 9f : (float) texture.width() / texture.height(); }

        /** No frame for a while: show the spinner. */
        public boolean stalled() { return texture == null || System.currentTimeMillis() - lastFrameMs > 4000; }
    }

    private static final Map<String, Viewing> WATCHING = new LinkedHashMap<>();
    private static boolean initialised;

    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        BlobReceiver.onComplete(r -> { if ("frame".equals(r.kind())) onFrame(r); });
        SlateEvents.SCREEN_INIT_POST.register(StreamViewer::onScreenInit);
        SlateEvents.HUD_RENDER.register((g, pt) -> {
            final Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) {
                ScreenShare.onFrameRendered();
                renderHudPip(g);
            }
        });
        SlateEvents.SCREEN_RENDER_POST.register((screen, g, mx, my, pt) -> ScreenShare.onFrameRendered());
    }

    // ------------------------------------------------------------------ control

    public static Collection<Viewing> all() { return WATCHING.values(); }

    @Nullable public static Viewing get(final String id) { return WATCHING.get(id); }

    public static boolean isWatching(final String id) { return WATCHING.containsKey(id); }

    @Nullable
    public static Viewing pipStream() {
        for (final Viewing v : WATCHING.values()) if (v.pip) return v;
        return null;
    }

    /** Start watching: full-screen viewer, or picture-in-picture. */
    public static void watch(final String id, final boolean pip) {
        final StreamInfo info = SocialClient.get().stream(id);
        if (info == null) return;
        Viewing v = WATCHING.get(id);
        if (v == null) {
            v = new Viewing(info);
            WATCHING.put(id, v);
            SocialClient.get().watchStream(id, true);
        }
        setPip(id, pip);
        if (!pip) {
            final Minecraft mc = Minecraft.getInstance();
            if (!(mc.screen instanceof StreamViewerScreen s && s.streamId().equals(id))) mc.setScreen(new StreamViewerScreen(id, mc.screen));
        }
    }

    public static void setPip(final String id, final boolean pip) {
        final Viewing v = WATCHING.get(id);
        if (v == null) return;
        if (pip) for (final Viewing o : WATCHING.values()) o.pip = false;
        v.pip = pip;
    }

    public static void stop(final String id) {
        final Viewing v = WATCHING.remove(id);
        if (v == null) return;
        SocialClient.get().watchStream(id, false);
        release(v);
    }

    public static void stopAll() {
        for (final String id : new ArrayList<>(WATCHING.keySet())) stop(id);
    }

    private static void release(final Viewing v) {
        if (v.texture != null) { Textures.release(v.texture); v.texture = null; }
    }

    // ------------------------------------------------------------------ hub callbacks

    public static void onUpdate(final StreamInfo info) {
        final Viewing v = WATCHING.get(info.id());
        if (v != null) v.info = info;
    }

    public static void onEnded(final String id) {
        final Viewing v = WATCHING.remove(id);
        if (v != null) release(v);
    }

    public static void onFrameInfo(final SocialMessage.StreamFrame f) {
        final Viewing v = WATCHING.get(f.streamId());
        if (v != null) v.pending = f;
    }

    public static void onFrameStart(final BlobPayloads.Start start) {}

    private static void onFrame(final BlobReceiver.Received r) {
        final String target = r.start().target();
        if (target == null || !target.startsWith("s:")) return;
        final Viewing v = WATCHING.get(target.substring(2));
        if (v == null) return;
        final Optional<Textures.Loaded> tex = Textures.fromBytes(r.bytes(), null);
        if (tex.isEmpty()) return;
        release(v);
        v.texture = tex.get();
        final long now = System.currentTimeMillis();
        if (v.lastFrameMs > 0) {
            final float inst = 1000f / Math.max(1, now - v.lastFrameMs);
            v.fps = v.fps == 0 ? inst : v.fps * 0.8f + inst * 0.2f;
        }
        v.lastFrameMs = now;
        v.frames++;
        v.frameBytes = r.bytes().length;
        final long sent = metaLong(r.start().meta(), "t");
        v.seq = (int) metaLong(r.start().meta(), "seq");
        if (sent > 0) v.latencyMs = Math.max(0, now - sent);
        else if (v.pending != null && v.pending.seq() == v.seq) v.latencyMs = Math.max(0, now - v.pending.sentMs());
    }

    private static long metaLong(final String meta, final String key) {
        if (meta == null) return 0;
        for (final String part : meta.split(";")) {
            final int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(key)) {
                try { return Long.parseLong(part.substring(eq + 1).trim()); } catch (final NumberFormatException e) { return 0; }
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ picture in picture

    /** The PiP rectangle {x, y, w, h} for the current window size. */
    public static int[] pipRect(final Viewing v, final int screenW, final int screenH) {
        final MultiplayerConfig cfg = MultiplayerConfigs.client();
        final int w = Math.max(96, Math.min(cfg.pipWidth, screenW - 16));
        final int h = Math.max(54, Math.round(w / v.aspect()));
        int x = cfg.pipX < 0 ? screenW - w - 8 : cfg.pipX;
        int y = cfg.pipY < 0 ? screenH - h - 30 : cfg.pipY;
        x = Math.max(0, Math.min(screenW - w, x));
        y = Math.max(0, Math.min(screenH - h, y));
        return new int[] { x, y, w, h };
    }

    private static void renderHudPip(final GuiGraphics g) {
        final Viewing v = pipStream();
        if (v == null) return;
        final int[] r = pipRect(v, g.guiWidth(), g.guiHeight());
        drawPip(g, v, r[0], r[1], r[2], r[3], false, false);
    }

    /** Draws the stream picture with its frame, title and stats; controls only when {@code showControls}. */
    public static void drawPip(final GuiGraphics g, final Viewing v, final int x, final int y, final int w, final int h, final boolean hover, final boolean showControls) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        if (t.isVanilla()) {
            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
            SlateDraw.outline(g, x - 2, y - 2, w + 4, h + 4, hover ? 0xFFFFFFFF : 0xFF8B8B8B, 0);
        } else {
            SlateDraw.shadow(g, x, y, w, h, 0.5f);
            SlateDraw.pixelRound(g, x - 1, y - 1, w + 2, h + 2, hover ? p.borderStrong() : p.border(), t.radius());
        }
        drawPicture(g, v, x, y, w, h);
        // Title strip
        g.fill(x, y, x + w, y + 11, 0x90000000);
        g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(v.title()), w - (showControls ? 40 : 6)), x + 3, y + 2, 0xFFEEEEEE, false);
        if (v.stalled()) {
            SlateSpinner.draw(g, x + w / 2 - 6, y + h / 2 - 6, 12, 0xFFFFFFFF);
        } else {
            final String stats = Math.round(v.fps) + " fps";
            g.drawString(SlateDraw.font(), stats, x + w - SlateDraw.width(stats) - 3, y + h - 10, 0xFFCCCCCC, false);
        }
        if (showControls) {
            Icons.draw(g, Icon.FULLSCREEN, x + w - 26, y + 1, 8, hover ? 0xFFFFFFFF : 0xFFBBBBBB);
            Icons.draw(g, Icon.CLOSE, x + w - 13, y + 1, 8, hover ? 0xFFFFFFFF : 0xFFBBBBBB);
        }
    }

    /** The frame texture fit into the rect (letterboxed), or a dark placeholder. */
    public static void drawPicture(final GuiGraphics g, final Viewing v, final int x, final int y, final int w, final int h) {
        g.fill(x, y, x + w, y + h, 0xFF0A0A0A);
        final Textures.Loaded tex = v.texture;
        if (tex == null) return;
        final float s = Math.min((float) w / tex.width(), (float) h / tex.height());
        final int dw = Math.max(1, Math.round(tex.width() * s)), dh = Math.max(1, Math.round(tex.height() * s));
        final int dx = x + (w - dw) / 2, dy = y + (h - dh) / 2;
        RenderSystem.enableBlend();
        g.setColor(1, 1, 1, 1);
        g.blit(tex.id(), dx, dy, dw, dh, 0, 0, tex.width(), tex.height(), tex.width(), tex.height());
        RenderSystem.disableBlend();
    }

    private static void onScreenInit(final Screen screen) {
        if (pipStream() == null || screen instanceof StreamViewerScreen || ScreenIds.isContainer(screen)) return;
        if (screen instanceof LayoutApplier.ScreenAccess acc) acc.slate$add(new PipWidget());
    }

    /** Human readable stats for the viewer screen. */
    public static List<Component> stats(final Viewing v) {
        final List<Component> out = new ArrayList<>();
        out.add(Fonts.heading(Component.literal(v.title())));
        final String res = v.texture == null ? "-" : v.texture.width() + "x" + v.texture.height();
        out.add(Component.literal(v.info.owner().display() + "  ·  " + res + "  ·  " + Math.round(v.fps) + " fps  ·  ~" + v.latencyMs + " ms  ·  "
            + v.info.viewers() + (v.info.viewers() == 1 ? " viewer" : " viewers") + "  ·  " + (v.frameBytes / 1024) + " KB/frame"));
        return out;
    }

    static int textColor() { return Theme.current().text(); }

    static int mutedColor() { return Colors.withAlpha(Theme.current().muted(), 0xFF); }

    private StreamViewer() {}
}
