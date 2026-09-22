package dev.fallingcloud.slate.core.net.blob;

import dev.fallingcloud.slate.core.Slate;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The three payloads of a chunked blob transfer (lifted from Chatterbox, generalised with a target).
 *
 * <p>One payload must stay well under the ~2 MiB on-wire frame ceiling (NOT enforced on singleplayer's
 * in-memory pipe, so never verify sizes there). Blobs are cut into 24 000-byte chunks: {@link Start}
 * announces id/kind/size/target, {@link Chunk} carries data, {@link End} completes. The same payloads flow
 * client -> server (upload) and server -> client (relay); the relay stamps the sender's name into the
 * client-bound Start so receivers never trust a client-written identity.</p>
 *
 * <p>{@code target} decides routing on the server: {@code ""} = every other player (public chat media),
 * {@code p:<uuid>} = one player, {@code g:<id>} = a group, {@code s:<id>} = a stream's viewers, or anything
 * a module's {@link BlobRouter} understands. {@code meta} is free-form for the kind (mime, name...).</p>
 */
public final class BlobPayloads {

    public static final int CHUNK_BYTES = 24_000;

    public record Start(String id, String kind, int totalBytes, int durationMs, String sender, String target, String meta)
        implements CustomPacketPayload {
        public static final Type<Start> TYPE = new Type<>(Slate.id("blob_start"));
        // Seven fields: past StreamCodec.composite's arity, so hand-written.
        public static final StreamCodec<ByteBuf, Start> CODEC = StreamCodec.of(
            (buf, s) -> {
                final FriendlyByteBuf f = new FriendlyByteBuf(buf);
                f.writeUtf(s.id, 16);
                f.writeUtf(s.kind, 32);
                f.writeVarInt(s.totalBytes);
                f.writeVarInt(s.durationMs);
                f.writeUtf(s.sender, 64);
                f.writeUtf(s.target, 96);
                f.writeUtf(s.meta, 512);
            },
            buf -> {
                final FriendlyByteBuf f = new FriendlyByteBuf(buf);
                return new Start(f.readUtf(16), f.readUtf(32), f.readVarInt(), f.readVarInt(), f.readUtf(64), f.readUtf(96), f.readUtf(512));
            });

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

        public Start withSender(final String name) {
            return new Start(id, kind, totalBytes, durationMs, name, target, meta);
        }
    }

    public record Chunk(String id, int index, byte[] data) implements CustomPacketPayload {
        public static final Type<Chunk> TYPE = new Type<>(Slate.id("blob_chunk"));
        public static final StreamCodec<ByteBuf, Chunk> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16), Chunk::id,
            ByteBufCodecs.VAR_INT, Chunk::index,
            ByteBufCodecs.byteArray(CHUNK_BYTES), Chunk::data,
            Chunk::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record End(String id) implements CustomPacketPayload {
        public static final Type<End> TYPE = new Type<>(Slate.id("blob_end"));
        public static final StreamCodec<ByteBuf, End> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16), End::id, End::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 8 lowercase hex chars. */
    public static boolean validId(final String id) {
        if (id == null || id.length() != 8) return false;
        for (int i = 0; i < 8; i++) {
            final char c = id.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) return false;
        }
        return true;
    }

    private BlobPayloads() {}
}
