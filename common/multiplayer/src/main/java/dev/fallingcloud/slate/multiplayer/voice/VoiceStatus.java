package dev.fallingcloud.slate.multiplayer.voice;

import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the UI wants to know or do about Simple Voice Chat, behind a soft-dependency gate.
 * {@link SvcAdapter} is the only class with voicechat imports and is loaded lazily after
 * {@code isModLoaded("voicechat")} passed; every call is wrapped so a moved internal degrades to
 * "voice unavailable" instead of a crash. Client only.
 */
public final class VoiceStatus {

    private static Boolean available;
    private static SvcAdapter adapter;
    private static long lastWarnMs;

    public static synchronized boolean available() {
        if (available == null) {
            if (!SlatePlatform.get().isModLoaded("voicechat")) {
                available = false;
            } else {
                try {
                    SvcAdapter.probe();
                    adapter = new SvcAdapter();
                    available = true;
                } catch (final Throwable t) {
                    SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] Simple Voice Chat present but its internals changed - voice integration off: {}", t.toString());
                    available = false;
                }
            }
        }
        return available;
    }

    private static <T> T safe(final java.util.function.Supplier<T> call, final T fallback) {
        if (!available()) return fallback;
        try {
            return call.get();
        } catch (final Throwable t) {
            final long now = System.currentTimeMillis();
            if (now - lastWarnMs > 30_000) {
                lastWarnMs = now;
                SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] voice call failed: {}", t.toString());
            }
            return fallback;
        }
    }

    /** Voice chat is connected to the current server's voice server. */
    public static boolean connected() { return safe(() -> adapter.connected(), false); }

    public static boolean isSpeaking(final UUID player) { return safe(() -> adapter.isTalking(player), false); }

    public static boolean isWhispering(final UUID player) { return safe(() -> adapter.isWhispering(player), false); }

    /** The player has a voice state on this server (so volume/mute controls make sense). */
    public static boolean hasVoice(final UUID player) { return safe(() -> adapter.hasState(player), false); }

    public static boolean isPlayerMuted(final UUID player) { return safe(() -> adapter.isPlayerDisabled(player), false); }

    public static boolean isMuted() { return safe(() -> adapter.isMuted(), false); }

    public static void setMuted(final boolean muted) { safe(() -> { adapter.setMuted(muted); return null; }, null); }

    public static void toggleMuted() { setMuted(!isMuted()); }

    /** SVC "disabled" = you hear nobody and send nothing (deafen). */
    public static boolean isDeafened() { return safe(() -> adapter.isDisabled(), false); }

    public static void setDeafened(final boolean deaf) { safe(() -> { adapter.setDisabled(deaf); return null; }, null); }

    public static void toggleDeafened() { setDeafened(!isDeafened()); }

    public static boolean isPttDown() { return safe(() -> adapter.isPttDown(), false); }

    /** Own microphone activity (talking or whispering). */
    public static boolean isSelfSpeaking() { return safe(() -> adapter.selfTalking(), false); }

    /** Per-player volume multiplier (1 = default). */
    public static double volume(final UUID player) { return safe(() -> adapter.volume(player), 1.0); }

    public static void setVolume(final UUID player, final double volume) {
        safe(() -> { adapter.setVolume(player, Math.max(0, Math.min(4, volume))); return null; }, null);
    }

    @Nullable public static UUID currentGroup() { return safe(() -> adapter.groupId(), null); }

    public static String groupName(final UUID group) { return safe(() -> adapter.groupName(group), ""); }

    /** Creates a voice group on the current server; the id shows up in {@link #currentGroup()} shortly after. */
    public static boolean createGroup(final String name) { return safe(() -> { adapter.createGroup(name); return true; }, false); }

    public static boolean joinGroup(final UUID group) { return safe(() -> { adapter.joinGroup(group); return true; }, false); }

    public static boolean leaveGroup() { return safe(() -> { adapter.leaveGroup(); return true; }, false); }

    private VoiceStatus() {}
}
