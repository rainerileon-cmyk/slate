package dev.fallingcloud.slate.multiplayer.client.stream;

import com.mojang.blaze3d.platform.NativeImage;
import dev.fallingcloud.slate.core.net.blob.BlobSender;
import dev.fallingcloud.slate.multiplayer.MultiplayerConfig;
import dev.fallingcloud.slate.multiplayer.MultiplayerConfigs;
import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;
import dev.fallingcloud.slate.multiplayer.client.ImageEncoding;
import dev.fallingcloud.slate.multiplayer.client.Notifications;
import dev.fallingcloud.slate.multiplayer.client.SocialClient;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage;
import dev.fallingcloud.slate.multiplayer.social.StreamInfo;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.jetbrains.annotations.Nullable;

/**
 * Sharing your own screen: the main render target is read every N ms on the render thread
 * ({@link Screenshot#takeScreenshot}), downscaled and JPEG-encoded on one worker, and sent as a
 * {@code frame} blob to {@code s:<streamId>} (the hub relays to viewers). The rate adapts between the
 * configured min and max fps to how much the blob sender still has queued, so a slow link drops frames
 * instead of building latency. Nothing is captured while nobody watches.
 */
public final class ScreenShare {

    private static final ExecutorService ENCODER = Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, "slate-screenshare");
        t.setDaemon(true);
        return t;
    });

    private static String streamId = "";
    private static boolean starting;
    @Nullable private static StreamInfo info;
    private static final AtomicBoolean ENCODING = new AtomicBoolean();
    private static long lastCaptureMs;
    private static int seq;
    private static long startedMs;
    // stats
    private static float measuredFps;
    private static long lastFrameSentMs;
    private static int lastFrameBytes;
    private static int lastW, lastH;
    private static long lastEncodeMs;

    public static boolean isSharing() { return !streamId.isEmpty(); }

    public static boolean isStarting() { return starting; }

    public static String streamId() { return streamId; }

    @Nullable public static StreamInfo info() { return info; }

    public static int viewers() { return info == null ? 0 : info.viewers(); }

    public static float fps() { return measuredFps; }

    public static int lastFrameBytes() { return lastFrameBytes; }

    public static int width() { return lastW; }

    public static int height() { return lastH; }

    public static long encodeMs() { return lastEncodeMs; }

    public static long uptimeMs() { return isSharing() ? System.currentTimeMillis() - startedMs : 0; }

    public static void start(final String title) {
        if (isSharing() || starting) return;
        if (!SocialClient.get().connected()) {
            Notifications.plain("Not connected", "Screen sharing needs a hub connection");
            return;
        }
        starting = true;
        SocialClient.get().startStream(title);
    }

    public static void stop() {
        if (isSharing()) SocialClient.get().stopStream(streamId);
        final boolean was = isSharing();
        streamId = "";
        starting = false;
        info = null;
        if (was) Notifications.plain("Screen share ended", null);
    }

    public static void toggle() {
        if (isSharing() || starting) stop(); else start("");
    }

    // ---- hub callbacks (client main thread)

    public static void onStarted(final String id) {
        streamId = id;
        starting = false;
        seq = 0;
        startedMs = System.currentTimeMillis();
        measuredFps = 0;
        Notifications.plain("Sharing your screen", "Friends can watch from the Streams page");
    }

    public static void onUpdate(final StreamInfo i) {
        if (i.id().equals(streamId) || streamId.isEmpty()) info = i;
    }

    public static void onEnded(final String id) {
        if (id.equals(streamId)) { streamId = ""; info = null; starting = false; }
    }

    public static void onLinkLost() {
        streamId = "";
        starting = false;
        info = null;
    }

    // ---- capture

    /** Called once per rendered frame (from the HUD hook when no screen is open, else from the screen hook). */
    public static void onFrameRendered() {
        if (!isSharing() || ENCODING.get() || viewers() <= 0) return;
        final MultiplayerConfig cfg = MultiplayerConfigs.client();
        final int minFps = Math.max(1, Math.min(30, cfg.streamMinFps)), maxFps = Math.max(minFps, Math.min(30, cfg.streamMaxFps));
        final int queued = BlobSender.queued();
        final int fps = queued > 40 ? minFps : queued > 12 ? (minFps + maxFps) / 2 : maxFps;
        final long now = System.currentTimeMillis();
        if (now - lastCaptureMs < 1000 / fps) return;
        lastCaptureMs = now;
        final Minecraft mc = Minecraft.getInstance();
        final NativeImage img;
        try {
            img = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        } catch (final Exception e) {
            SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] screen capture failed: {}", e.toString());
            return;
        }
        final int mySeq = ++seq;
        final String id = streamId;
        final int maxWidth = Math.max(160, Math.min(1280, cfg.streamMaxWidth));
        final float quality = (float) Math.max(0.2, Math.min(0.9, cfg.streamQuality));
        ENCODING.set(true);
        ENCODER.execute(() -> {
            final long t0 = System.currentTimeMillis();
            ImageEncoding.Encoded enc = null;
            try {
                enc = ImageEncoding.encodeFrame(img, maxWidth, quality);
            } catch (final Exception e) {
                SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] frame encode failed: {}", e.toString());
            }
            final long encodeMs = System.currentTimeMillis() - t0;
            final ImageEncoding.Encoded result = enc;
            mc.execute(() -> {
                ENCODING.set(false);
                if (result == null || !id.equals(streamId) || !BlobSender.ready()) return;
                final long sentMs = System.currentTimeMillis();
                SocialClient.get().send(new SocialMessage.StreamFrame(id, mySeq, result.width(), result.height(), sentMs, result.bytes().length));
                BlobSender.send("frame", result.bytes(), 0, "s:" + id, "seq=" + mySeq + ";w=" + result.width() + ";h=" + result.height() + ";t=" + sentMs, null);
                lastEncodeMs = encodeMs;
                lastFrameBytes = result.bytes().length;
                lastW = result.width();
                lastH = result.height();
                if (lastFrameSentMs > 0) {
                    final float inst = 1000f / Math.max(1, sentMs - lastFrameSentMs);
                    measuredFps = measuredFps == 0 ? inst : measuredFps * 0.8f + inst * 0.2f;
                }
                lastFrameSentMs = sentMs;
            });
        });
    }

    private ScreenShare() {}
}
