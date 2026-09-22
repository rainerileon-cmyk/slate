package dev.fallingcloud.slate.multiplayer.voice.clips;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;
import de.maxhenkel.voicechat.plugins.impl.VoicechatClientApiImpl;
import de.maxhenkel.voicechat.voice.client.ClientManager;
import de.maxhenkel.voicechat.voice.client.ClientVoicechat;
import de.maxhenkel.voicechat.voice.client.MicThread;
import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The ONLY class in the clip stack that touches Simple Voice Chat types (lifted from Chatterbox). Everything
 * else goes through {@link VoiceSupport}, which loads this class lazily behind a mod-presence check, so
 * voicechat being absent means these imports are simply never resolved.
 *
 * <p>Capture uses voicechat's own mic-test recipe, because there is no public capture API: borrow the running
 * {@link MicThread} if there is one (locking it so voicechat's transmit loop stops consuming frames), else
 * construct one - without {@code start()}: the poll methods read the device synchronously, the thread's own
 * {@code run()} loop is only its network transmit path. Internal API, verified against voicechat 2.6.x:
 * {@code MicThread(ClientVoicechat, ClientVoicechatConnection, Consumer<MicrophoneException>)},
 * {@code pollProcessedAudio(boolean)}, {@code setMicrophoneLocked(boolean)}, {@code getError(Consumer)},
 * {@code close()}, {@code isClosed()}; {@code VoicechatClientApiImpl.instance().createEncoder(OpusEncoderMode)} /
 * {@code createDecoder()}. Every entry point is wrapped so a voicechat update degrades to "unavailable".</p>
 */
final class VoicechatAdapter {

    private final AtomicBoolean recording = new AtomicBoolean();
    private final AtomicReference<List<short[]>> captured = new AtomicReference<>();
    private volatile MicThread mic;
    private volatile boolean ownMic;
    private volatile String micError;

    /** Cheap probe used by VoiceSupport to confirm the internals still look like we expect. */
    static void probe() {
        VoicechatClientApiImpl.instance();
        ClientManager.getClient();
    }

    boolean isRecording() { return recording.get(); }

    boolean startRecording(final int maxSeconds) {
        if (!recording.compareAndSet(false, true)) return false;
        micError = null;
        final List<short[]> frames = new ArrayList<>(maxSeconds * 50);
        captured.set(frames);
        try {
            final ClientVoicechat client = ClientManager.getClient();
            MicThread thread = client != null ? client.getMicThread() : null;
            if (thread != null && !thread.isClosed()) {
                ownMic = false;
            } else {
                thread = new MicThread(client, null, err -> micError = err.getMessage());
                ownMic = true;
            }
            thread.setMicrophoneLocked(true);
            thread.getError(err -> micError = err.getMessage());
            mic = thread;
        } catch (final Throwable t) {
            SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] could not open microphone", t);
            recording.set(false);
            return false;
        }

        final Thread pump = new Thread(() -> {
            final int maxFrames = maxSeconds * 1000 / VoiceClip.FRAME_MS;
            try {
                while (recording.get() && frames.size() < maxFrames) {
                    final MicThread m = mic;
                    if (m == null) break;
                    final short[] frame = m.pollProcessedAudio(true);   // denoise + AGC, like voicechat itself
                    if (frame != null) {
                        frames.add(frame);
                    } else {
                        Thread.sleep(4);
                    }
                }
            } catch (final Throwable t) {
                micError = String.valueOf(t.getMessage());
            } finally {
                releaseMic();
            }
        }, "slate-mic");
        pump.setDaemon(true);
        pump.start();
        return true;
    }

    /** @return the finished clip, or null if nothing usable was captured. Blocks only trivially. */
    VoiceClip stopRecording() {
        if (!recording.compareAndSet(true, false)) return null;
        // The pump thread notices the flag within one frame; give it a moment, then read what it collected.
        try { Thread.sleep(30); } catch (final InterruptedException e) { Thread.currentThread().interrupt(); }
        releaseMic();
        final List<short[]> frames = captured.getAndSet(null);
        if (frames == null || frames.size() < 10) return null;    // under 200 ms is a misclick, not a message

        final VoicechatApi api = VoicechatClientApiImpl.instance();
        final OpusEncoder encoder = api.createEncoder(OpusEncoderMode.VOIP);
        try {
            final List<byte[]> opus = new ArrayList<>(frames.size());
            for (final short[] pcm : new ArrayList<>(frames)) {
                final byte[] packet = encoder.encode(pcm);
                if (packet != null && packet.length > 0) opus.add(packet);
            }
            return opus.isEmpty() ? null : new VoiceClip(opus);
        } finally {
            encoder.close();
        }
    }

    short[] decode(final VoiceClip clip) {
        final VoicechatApi api = VoicechatClientApiImpl.instance();
        final OpusDecoder decoder = api.createDecoder();
        try {
            final short[] pcm = new short[clip.opusFrames().size() * VoiceClip.FRAME_SAMPLES];
            int at = 0;
            for (final byte[] packet : clip.opusFrames()) {
                final short[] frame = decoder.decode(packet);
                if (frame != null) {
                    System.arraycopy(frame, 0, pcm, at, Math.min(frame.length, VoiceClip.FRAME_SAMPLES));
                }
                at += VoiceClip.FRAME_SAMPLES;
            }
            return pcm;
        } finally {
            decoder.close();
        }
    }

    String lastError() { return micError; }

    private void releaseMic() {
        final MicThread thread = mic;
        if (thread == null) return;
        mic = null;
        try {
            thread.setMicrophoneLocked(false);
            if (ownMic) thread.close();
        } catch (final Throwable t) {
            SlateMultiplayer.LOGGER.debug("[Slate Multiplayer] mic release: {}", t.toString());
        }
    }
}
