package dev.fallingcloud.slate.multiplayer.voice.clips;

import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;

/**
 * Soft-dependency facade for voice clip recording/decoding (lifted from Chatterbox; same API the Chat
 * module calls). Everything else calls this and never mentions voicechat; {@link VoicechatAdapter} (the
 * only class here with voicechat imports) is loaded lazily behind {@link #available()}, so a missing or
 * incompatibly-updated Simple Voice Chat turns voice messages off instead of crashing anything.
 */
public final class VoiceSupport {

    private static Boolean available;
    private static VoicechatAdapter adapter;

    public static synchronized boolean available() {
        if (available == null) {
            if (!SlatePlatform.get().isModLoaded("voicechat")) {
                available = false;
            } else {
                try {
                    VoicechatAdapter.probe();
                    adapter = new VoicechatAdapter();
                    available = true;
                } catch (final Throwable t) {
                    // A voicechat update that moved the internals lands here - message stays useful.
                    SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] Simple Voice Chat present but its internals changed - "
                        + "voice messages disabled until Slate is updated: {}", t.toString());
                    available = false;
                }
            }
        }
        return available;
    }

    public static boolean startRecording(final int maxSeconds) {
        return available() && adapter.startRecording(maxSeconds);
    }

    /** @return the finished clip, or null when too short / capture failed. */
    public static VoiceClip stopRecording() {
        return available() ? adapter.stopRecording() : null;
    }

    public static boolean isRecording() {
        return available() && adapter.isRecording();
    }

    /** Decodes a received clip to 48 kHz mono PCM. Null when voice support is off. */
    public static short[] decode(final VoiceClip clip) {
        if (!available() || clip == null) return null;
        try {
            return adapter.decode(clip);
        } catch (final Throwable t) {
            SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] voice decode failed: {}", t.toString());
            return null;
        }
    }

    public static String lastError() {
        return adapter != null ? adapter.lastError() : null;
    }

    private VoiceSupport() {}
}
