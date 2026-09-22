package dev.fallingcloud.slate.chat.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.fallingcloud.slate.chat.ChatConfig;
import dev.fallingcloud.slate.core.event.SlateKeys;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Hold-to-record voice messages: the record key in-game, or the mic button held in the chat screen. Key
 * goes down -> capture starts (with a toast when voice support is missing so the key never feels dead);
 * key comes up -> capture stops, the clip encodes, and {@link ChatSend#voice} takes it from there. The cap
 * from config ends the clip on its own. Capture/encode/playback are Slate Multiplayer's, reached through
 * {@link MultiplayerBridge}.
 */
public final class VoiceRecorder {

    public static final KeyMapping RECORD_KEY = new KeyMapping("key.slate_chat.record", InputConstants.Type.KEYSYM, InputConstants.KEY_B, SlateKeys.CATEGORY);

    private static boolean recording;
    private static boolean viaButton;
    private static long startMs;

    public static boolean isRecording() { return recording; }

    public static long elapsedMs() { return recording ? Clock.nowMs() - startMs : 0; }

    /** Every client tick: the in-game hold key and the length cap. */
    public static void tick() {
        final Minecraft mc = Minecraft.getInstance();
        final boolean keyDown = RECORD_KEY.isDown() && mc.player != null && mc.getConnection() != null && mc.screen == null;
        if (keyDown && !recording) {
            start(false);
        } else if (!keyDown && recording && !viaButton) {
            finish();
        } else if (recording && elapsedMs() > ChatConfig.get().maxRecordSeconds * 1000L) {
            finish();                                          // hit the cap - send what we have
        }
    }

    /** Mic button pressed in the chat screen. */
    public static void beginHold() {
        if (!recording) start(true);
    }

    /** Mic button released (anywhere). */
    public static void endHold() {
        if (recording && viaButton) finish();
    }

    private static void start(final boolean button) {
        if (!MultiplayerBridge.voiceAvailable()) {
            SlateToasts.show(Component.translatable("slate_chat.voice.unavailable"), Component.translatable("slate_chat.voice.unavailable.body"), Icon.MIC_OFF);
            return;
        }
        if (!ChatSend.canSendMedia()) {
            SlateToasts.show(Component.translatable("slate_chat.media.no_server"), Component.translatable("slate_chat.media.no_server.body"), Icon.WARNING);
            return;
        }
        if (MultiplayerBridge.startRecording(ChatConfig.get().maxRecordSeconds)) {
            recording = true;
            viaButton = button;
            startMs = Clock.nowMs();
        } else {
            final String err = MultiplayerBridge.lastError();
            SlateToasts.show(Component.translatable("slate_chat.voice.mic_error", err == null ? "?" : err), null, Icon.MIC_OFF);
        }
    }

    private static void finish() {
        recording = false;
        final Object clip = MultiplayerBridge.stopRecording();
        if (clip == null) {
            final String err = MultiplayerBridge.lastError();
            if (err != null && !err.isEmpty()) SlateToasts.show(Component.translatable("slate_chat.voice.mic_error", err), null, Icon.MIC_OFF);
            else SlateToasts.show(Component.translatable("slate_chat.voice.too_short"), null, Icon.MIC);
            return;
        }
        final byte[] bytes = MultiplayerBridge.serialize(clip);
        final int duration = MultiplayerBridge.durationMs(clip);
        if (bytes == null || bytes.length == 0) return;
        ChatSend.voice(bytes, duration);
    }

    /** The while-recording overlay: pulsing dot + elapsed / cap, just above the hotbar. */
    public static void renderHud(final GuiGraphics g) {
        if (!recording) return;
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final long elapsed = elapsedMs() / 1000;
        final Component text = Component.translatable("slate_chat.recording", "%d:%02d".formatted(elapsed / 60, elapsed % 60), ChatConfig.get().maxRecordSeconds);
        final int w = SlateDraw.width(text) + 26;
        final int x = (g.guiWidth() - w) / 2;
        final int y = g.guiHeight() - 68;
        if (t.isVanilla()) {
            g.fill(x, y, x + w, y + 16, 0xA0000000);
        } else {
            SlateDraw.shadow(g, x, y, w, 16, 0.4f);
            SlateDraw.pixelRound(g, x, y, w, 16, Colors.withAlpha(p.surface(), 0xF0), t.radius());
            SlateDraw.outline(g, x, y, w, 16, p.borderStrong(), t.radius());
        }
        // Pulse ~1 Hz so it reads as "live" - solid red reads as an icon, not an activity.
        final boolean on = (Clock.nowMs() / 500) % 2 == 0;
        Icons.draw(g, Icon.MIC, x + 4, y + 2, 12, on ? p.danger() : Colors.withAlpha(p.danger(), 0x70));
        g.drawString(SlateDraw.font(), text, x + 20, y + 4, t.isVanilla() ? 0xFFFFFFFF : p.text(), t.isVanilla());
    }

    private VoiceRecorder() {}
}
