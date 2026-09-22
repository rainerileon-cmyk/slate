package dev.fallingcloud.slate.multiplayer.voice.clips;

import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Plays one decoded voice clip at a time through the system mixer (lifted from Chatterbox).
 *
 * <p>Playback deliberately does not go through Minecraft's sound engine or voicechat's channels: a voice
 * message is UI audio - it should play "in your head" at full volume regardless of where anyone is
 * standing, exactly like Discord. A plain {@link SourceDataLine} at the clip's native 48 kHz mono does that
 * with no positional maths and no dependency on a voice server connection.</p>
 *
 * <p>Static single-slot on purpose: clicking a second clip stops the first, clicking the playing clip stops
 * it. The renderer polls {@link #playingId()} and {@link #progress()} for the card UI.</p>
 */
public final class VoicePlayer {

    private static volatile String playingId;
    private static volatile float progress;
    private static volatile Thread worker;

    public static String playingId() { return playingId; }

    /** 0..1 through the current clip; only meaningful while {@link #playingId()} matches. */
    public static float progress() { return progress; }

    /** Toggles: starts this clip, stopping any other; stops it if it is the one playing. */
    public static void toggle(final String id, final short[] pcm) {
        if (id.equals(playingId)) {
            stop();
            return;
        }
        stop();
        if (pcm == null) return;
        playingId = id;
        progress = 0;
        final Thread t = new Thread(() -> run(id, pcm), "slate-voice-play");
        t.setDaemon(true);
        worker = t;
        t.start();
    }

    public static void stop() {
        playingId = null;              // the worker notices and drains out
        final Thread t = worker;
        if (t != null) t.interrupt();
    }

    private static void run(final String id, final short[] pcm) {
        final AudioFormat format = new AudioFormat(VoiceClip.SAMPLE_RATE, 16, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format, VoiceClip.SAMPLE_RATE);          // one second of buffer
            line.start();
            final byte[] bytes = new byte[pcm.length * 2];
            for (int i = 0; i < pcm.length; i++) {             // little-endian, matching the format above
                bytes[i * 2] = (byte) (pcm[i] & 0xFF);
                bytes[i * 2 + 1] = (byte) ((pcm[i] >> 8) & 0xFF);
            }
            int at = 0;
            while (at < bytes.length && id.equals(playingId)) {
                final int n = line.write(bytes, at, Math.min(9600, bytes.length - at));
                at += n;
                progress = (float) at / bytes.length;
            }
            if (id.equals(playingId)) {
                line.drain();
            } else {
                line.flush();                                   // stopped early - drop what is queued
            }
        } catch (final Exception e) {
            SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] voice playback failed: {}", e.toString());
        } finally {
            if (id.equals(playingId)) playingId = null;
            progress = 0;
        }
    }

    private VoicePlayer() {}
}
