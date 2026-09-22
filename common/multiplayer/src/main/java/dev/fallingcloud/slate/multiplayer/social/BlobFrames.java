package dev.fallingcloud.slate.multiplayer.social;

import dev.fallingcloud.slate.core.net.blob.BlobPayloads;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * Converts Core's blob payloads to and from {@link SocialMessage.BlobPassthrough} frames using the
 * payloads' own codecs, so a blob relayed over the TCP link is byte-identical to one on the in-game channel.
 */
public final class BlobFrames {

    /** Wraps a Start/Chunk/End; null for any other payload. */
    @Nullable
    public static SocialMessage.BlobPassthrough wrap(final CustomPacketPayload payload) {
        final ByteBuf buf = Unpooled.buffer(64);
        try {
            if (payload instanceof BlobPayloads.Start s) {
                BlobPayloads.Start.CODEC.encode(buf, s);
                return new SocialMessage.BlobPassthrough(SocialMessage.BlobPassthrough.START, bytes(buf));
            }
            if (payload instanceof BlobPayloads.Chunk c) {
                BlobPayloads.Chunk.CODEC.encode(buf, c);
                return new SocialMessage.BlobPassthrough(SocialMessage.BlobPassthrough.CHUNK, bytes(buf));
            }
            if (payload instanceof BlobPayloads.End e) {
                BlobPayloads.End.CODEC.encode(buf, e);
                return new SocialMessage.BlobPassthrough(SocialMessage.BlobPassthrough.END, bytes(buf));
            }
            return null;
        } finally {
            buf.release();
        }
    }

    /** Decodes a frame back into the blob payload; null when malformed. */
    @Nullable
    public static CustomPacketPayload unwrap(final SocialMessage.BlobPassthrough frame) {
        final ByteBuf buf = Unpooled.wrappedBuffer(frame.data());
        try {
            return switch (frame.kind()) {
                case SocialMessage.BlobPassthrough.START -> BlobPayloads.Start.CODEC.decode(buf);
                case SocialMessage.BlobPassthrough.CHUNK -> BlobPayloads.Chunk.CODEC.decode(buf);
                case SocialMessage.BlobPassthrough.END -> BlobPayloads.End.CODEC.decode(buf);
                default -> null;
            };
        } catch (final Exception e) {
            return null;
        } finally {
            buf.release();
        }
    }

    private static byte[] bytes(final ByteBuf buf) {
        final byte[] out = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), out);
        return out;
    }

    private BlobFrames() {}
}
