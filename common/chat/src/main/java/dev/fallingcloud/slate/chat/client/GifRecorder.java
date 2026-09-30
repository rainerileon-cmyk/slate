package dev.fallingcloud.slate.chat.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.fallingcloud.slate.chat.ChatConfig;
import dev.fallingcloud.slate.chat.SlateChat;
import dev.fallingcloud.slate.core.event.SlateKeys;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.media.GifEncoder;
import dev.fallingcloud.slate.core.media.MediaUpload;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4fStack;
import org.lwjgl.system.MemoryUtil;

/**
 * A screenshot that moves: records what the game shows for a few seconds and makes a GIF of it, to send in the chat
 * or to keep. The record key starts and stops it in the game; the button in the chat's bar closes the chat and
 * starts it. The recording ends by itself at the length set in the chat's settings.
 *
 * <p>Frames are taken at the very end of a frame, of all that is on the screen, a few times a second, and made
 * smaller on the graphics card before they are read (reading the whole screen would stall the game). The mark that
 * says "recording" is drawn after the frame is taken, so it is on the screen and not in the GIF. When the recording
 * ends the GIF is made on another thread, written into the screenshots folder like any screenshot, and shown with a
 * button to send it.</p>
 */
public final class GifRecorder {

    public static final KeyMapping KEY = new KeyMapping("key.slate_chat.gif", InputConstants.Type.KEYSYM, InputConstants.KEY_F8, SlateKeys.CATEGORY);

    /** Widths a GIF is made at, the widest that fits what may be sent first. */
    private static final float[] SMALLER = {1f, 0.8f, 0.64f, 0.5f, 0.4f};

    private enum State { IDLE, WAITING, RECORDING, ENCODING }

    /** What a recording has become. */
    public record Result(byte[] file, byte[] toSend, int width, int height, int sendWidth, int frames, long millis, Path saved) {}

    private static State state = State.IDLE;
    private static long stateSince, startMs, lastFrameMs;
    private static int width, height;
    private static final List<int[]> frames = new ArrayList<>();
    private static final List<Integer> times = new ArrayList<>();
    private static final List<RenderTarget> steps = new ArrayList<>();
    private static ByteBuffer pixels;

    public static boolean recording() { return state == State.RECORDING || state == State.WAITING; }

    public static boolean busy() { return state != State.IDLE; }

    /** An unattended development run ({@code slate.autoGif}) records once, as soon as the player is in a world. */
    private static boolean auto = Boolean.getBoolean("slate.autoGif");

    /** Every client tick: the key, and the end of a recording that has reached its length. */
    public static void tick() {
        while (KEY.consumeClick()) toggle();
        if (auto && state == State.IDLE) {
            final Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null && mc.screen == null && mc.getOverlay() == null && mc.player.tickCount > 40) {
                auto = false;
                begin(0);
            }
        }
        if (state == State.RECORDING && Clock.nowMs() - startMs >= ChatConfig.get().gifSeconds * 1000L) finish();
    }

    /** Starts a recording, or ends the one that is running. */
    public static void toggle() {
        if (state == State.IDLE) begin(0);
        else if (state == State.RECORDING) finish();
        else if (state == State.WAITING) state = State.IDLE;
    }

    /** From the chat's bar: the chat closes, and the recording starts when it has gone. */
    public static void startFromChat() {
        if (state != State.IDLE) return;
        Minecraft.getInstance().setScreen(null);
        begin(350);
    }

    private static void begin(final long waitMs) {
        state = State.WAITING;
        stateSince = Clock.nowMs() + waitMs;
    }

    // ------------------------------------------------------------------ frames

    /** The end of a frame of the game, all of it drawn: takes a frame of the recording, and draws the mark after it. */
    public static void endOfFrame() {
        if (state == State.IDLE) return;
        RenderSystem.assertOnRenderThread();
        final Minecraft mc = Minecraft.getInstance();
        final long now = Clock.nowMs();
        try {
            if (state == State.WAITING && now >= stateSince) {
                prepare(mc);
                state = State.RECORDING;
                startMs = now;
                lastFrameMs = 0;
            }
            if (state == State.RECORDING) {
                final long gap = 1000L / Math.max(4, Math.min(25, ChatConfig.get().gifFps));
                if (frames.isEmpty() || now - lastFrameMs >= gap) {
                    take(mc);
                    times.add((int) (now - startMs));
                    lastFrameMs = now;
                }
            }
        } catch (final RuntimeException e) {
            SlateChat.LOGGER.warn("[Slate Chat] GIF recording stopped: {}", e.toString());
            release();
            state = State.IDLE;
            return;
        }
        mark(mc, now);
    }

    /** The targets the screen is made smaller through: halved until it is near the width wanted, then to that width. */
    private static void prepare(final Minecraft mc) {
        release();
        final RenderTarget main = mc.getMainRenderTarget();
        final int wanted = Math.max(160, Math.min(960, ChatConfig.get().gifWidth));
        width = Math.min(wanted, main.width) & ~1;
        height = Math.max(2, Math.round(width * (main.height / (float) main.width))) & ~1;
        int w = main.width, h = main.height;
        while (w / 2 >= width * 1.5f) {
            w /= 2;
            h /= 2;
            steps.add(new TextureTarget(w, h, false, Minecraft.ON_OSX));
        }
        steps.add(new TextureTarget(width, height, false, Minecraft.ON_OSX));
        pixels = MemoryUtil.memAlloc(width * height * 4);
    }

    private static void take(final Minecraft mc) {
        final RenderTarget main = mc.getMainRenderTarget();
        RenderTarget from = main;
        for (final RenderTarget to : steps) {
            GlStateManager._glBindFramebuffer(0x8CA8, from.frameBufferId);
            GlStateManager._glBindFramebuffer(0x8CA9, to.frameBufferId);
            GlStateManager._glBlitFrameBuffer(0, 0, from.width, from.height, 0, 0, to.width, to.height, 0x4000, 0x2601);
            from = to;
        }
        GlStateManager._glBindFramebuffer(0x8CA8, from.frameBufferId);
        GlStateManager._pixelStore(0x0D05, 1);
        pixels.clear();
        GlStateManager._readPixels(0, 0, width, height, 0x1908, 0x1401, pixels);
        main.bindWrite(true);

        // What was read begins with the lowest row: the frame begins with the highest.
        final int[] frame = new int[width * height];
        for (int y = 0; y < height; y++) {
            final int row = (height - 1 - y) * width * 4;
            for (int x = 0; x < width; x++) {
                final int i = row + x * 4;
                frame[y * width + x] = 0xFF000000 | (pixels.get(i) & 0xFF) << 16 | (pixels.get(i + 1) & 0xFF) << 8 | pixels.get(i + 2) & 0xFF;
            }
        }
        frames.add(frame);
    }

    private static void release() {
        for (final RenderTarget t : steps) t.destroyBuffers();
        steps.clear();
        if (pixels != null) MemoryUtil.memFree(pixels);
        pixels = null;
        frames.clear();
        times.clear();
    }

    // ------------------------------------------------------------------ the GIF

    private static void finish() {
        if (state != State.RECORDING) return;
        final List<int[]> taken = new ArrayList<>(frames);
        final int[] at = times.stream().mapToInt(Integer::intValue).toArray();
        final long length = Clock.nowMs() - startMs;
        final int w = width, h = height;
        frames.clear();
        release();
        if (taken.size() < 2) {
            state = State.IDLE;
            SlateToasts.show(Component.translatable("slate_chat.gif.too_short"), null, Icon.CAMERA);
            return;
        }
        state = State.ENCODING;
        final Path folder = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
        Util.backgroundExecutor().execute(() -> {
            Result result = null;
            String error = null;
            try {
                result = make(taken, at, length, w, h, folder);
            } catch (final IOException | RuntimeException | OutOfMemoryError e) {
                error = String.valueOf(e.getMessage() == null ? e : e.getMessage());
            }
            final Result made = result;
            final String failed = error;
            Minecraft.getInstance().execute(() -> done(made, failed));
        });
    }

    /** Makes the GIF, writes it to the folder, and makes it again smaller for as long as it is too large to send. */
    private static Result make(final List<int[]> taken, final int[] at, final long length, final int w, final int h, final Path folder) throws IOException {
        final int[] delays = new int[taken.size()];
        for (int i = 0; i < delays.length; i++) delays[i] = i + 1 < at.length ? at[i + 1] - at[i] : (int) Math.max(40, length - at[i]);
        final byte[] file = GifEncoder.encode(taken, delays, w, h);
        Files.createDirectories(folder);
        Path saved = folder.resolve(Util.getFilenameFormattedDateTime() + ".gif");
        for (int n = 1; Files.exists(saved); n++) saved = folder.resolve(Util.getFilenameFormattedDateTime() + "_" + n + ".gif");
        Files.write(saved, file);

        byte[] toSend = file;
        int sendWidth = w;
        for (int i = 1; i < SMALLER.length && toSend.length > MediaUpload.MAX_BYTES; i++) {
            final int sw = Math.max(96, Math.round(w * SMALLER[i])) & ~1, sh = Math.max(2, Math.round(sw * (h / (float) w))) & ~1;
            final List<int[]> small = new ArrayList<>(taken.size());
            for (final int[] frame : taken) small.add(GifEncoder.scale(frame, w, h, sw, sh));
            toSend = GifEncoder.encode(small, delays, sw, sh);
            sendWidth = sw;
        }
        return new Result(file, toSend, w, h, sendWidth, taken.size(), length, saved);
    }

    private static void done(final Result result, final String error) {
        state = State.IDLE;
        final Minecraft mc = Minecraft.getInstance();
        if (result == null) {
            SlateToasts.show(Component.translatable("slate_chat.gif.failed"), Component.literal(error == null ? "?" : error), Icon.WARNING);
            return;
        }
        // Shown at once where nothing else is open; where something is, it is in the folder and the toast says so.
        if (mc.screen == null) mc.setScreen(new GifPreviewScreen(null, result));
        else SlateToasts.show(Component.translatable("slate_chat.gif.saved", result.saved().getFileName().toString()), null, Icon.CAMERA);
    }

    // ------------------------------------------------------------------ the mark

    /** On the screen and not in the GIF: drawn after the frame was taken, over everything. */
    private static void mark(final Minecraft mc, final long now) {
        final Matrix4fStack stack = RenderSystem.getModelViewStack();
        stack.pushMatrix();
        stack.translation(0f, 0f, -11000f);
        RenderSystem.applyModelViewMatrix();
        try {
            final GuiGraphics g = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            g.pose().translate(0f, 0f, 4000f);
            draw(g, now);
            g.flush();
        } finally {
            stack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void draw(final GuiGraphics g, final long now) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final int cap = ChatConfig.get().gifSeconds;
        final Component text;
        if (state == State.ENCODING) {
            text = Component.translatable("slate_chat.gif.making");
        } else if (state == State.WAITING) {
            text = Component.translatable("slate_chat.gif.ready");
        } else {
            text = Component.translatable("slate_chat.gif.recording", "%.1f".formatted((now - startMs) / 1000f), cap, KEY.getTranslatedKeyMessage());
        }
        final int w = SlateDraw.width(text) + 26;
        final int x = (g.guiWidth() - w) / 2;
        final int y = 6;
        if (t.isVanilla()) {
            g.fill(x, y, x + w, y + 16, 0xA0000000);
        } else {
            SlateDraw.shadow(g, x, y, w, 16, 0.4f);
            SlateDraw.pixelRound(g, x, y, w, 16, Colors.withAlpha(p.surface(), 0xF0), t.radius());
            SlateDraw.outline(g, x, y, w, 16, p.borderStrong(), t.radius());
        }
        final boolean on = state != State.RECORDING || (now / 500) % 2 == 0;
        Icons.draw(g, Icon.CAMERA, x + 4, y + 2, 12, on ? p.danger() : Colors.withAlpha(p.danger(), 0x70));
        g.drawString(SlateDraw.font(), text, x + 20, y + 4, t.isVanilla() ? 0xFFFFFFFF : p.text(), t.isVanilla());
        if (state == State.RECORDING) {
            // How much of its length the recording has used, along the mark's lower edge.
            final float used = Math.min(1f, (now - startMs) / (cap * 1000f));
            g.fill(x + 2, y + 15, x + 2 + Math.round((w - 4) * used), y + 16, p.danger());
        }
    }

    private GifRecorder() {}
}
