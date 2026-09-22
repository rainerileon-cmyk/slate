package dev.fallingcloud.slate.multiplayer.voice.clips;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A recorded voice message: a sequence of independently-decodable 20 ms Opus packets (lifted from
 * Chatterbox, same wire format).
 *
 * <p>Opus is a packet codec: each 960-sample frame encodes to its own small packet and the decoder consumes
 * them one at a time. The serialized form is {@code [count][len,bytes]...} with two-byte lengths, which keeps
 * the whole clip self-describing without any container format.</p>
 */
public record VoiceClip(List<byte[]> opusFrames) {

    /** 48 kHz mono, 960 samples per frame = 20 ms - Simple Voice Chat's native format. */
    public static final int SAMPLE_RATE = 48_000;
    public static final int FRAME_SAMPLES = 960;
    public static final int FRAME_MS = 20;

    public int durationMs() {
        return opusFrames.size() * FRAME_MS;
    }

    public byte[] serialize() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(opusFrames.size() * 100);
        writeVarInt(out, opusFrames.size());
        for (final byte[] frame : opusFrames) {
            out.write((frame.length >>> 8) & 0xFF);
            out.write(frame.length & 0xFF);
            out.writeBytes(frame);
        }
        return out.toByteArray();
    }

    /** @return the clip, or null for anything malformed - a bad payload must never throw into the renderer. */
    public static VoiceClip deserialize(final byte[] data) {
        try {
            final int[] pos = { 0 };
            final int count = readVarInt(data, pos);
            if (count <= 0 || count > 6000) return null;          // > 2 minutes is not one of ours
            final List<byte[]> frames = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                final int len = ((data[pos[0]] & 0xFF) << 8) | (data[pos[0] + 1] & 0xFF);
                pos[0] += 2;
                if (len <= 0 || len > 1500 || pos[0] + len > data.length) return null;
                final byte[] frame = new byte[len];
                System.arraycopy(data, pos[0], frame, 0, len);
                pos[0] += len;
                frames.add(frame);
            }
            return new VoiceClip(frames);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private static void writeVarInt(final ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static int readVarInt(final byte[] data, final int[] pos) {
        int value = 0, shift = 0;
        while (true) {
            final byte b = data[pos[0]++];
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
            shift += 7;
        }
    }
}
