package dev.fallingcloud.slate.multiplayer.hub;

import dev.fallingcloud.slate.multiplayer.MultiplayerServerConfig;
import dev.fallingcloud.slate.multiplayer.social.BlobFrames;
import dev.fallingcloud.slate.multiplayer.social.SocialCodec;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage;
import io.netty.channel.Channel;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A client on the hub's TCP listener. Frames are encoded on the calling thread and handed to the netty
 * channel, which is thread-safe; the length prefix is added by the pipeline's prepender.
 */
final class RelaySession extends AbstractHubSession {

    private final Channel channel;
    private volatile boolean closed;

    RelaySession(final Channel channel, final UUID uuid, final String name, final MultiplayerServerConfig cfg) {
        super(uuid, name, cfg);
        this.channel = channel;
    }

    Channel channel() { return channel; }

    @Override
    public void send(final SocialMessage message) {
        if (closed || !channel.isActive()) return;
        channel.writeAndFlush(SocialCodec.encode(message), channel.voidPromise());
    }

    @Override
    public void sendBlob(final CustomPacketPayload blobPayload) {
        final SocialMessage.BlobPassthrough frame = BlobFrames.wrap(blobPayload);
        if (frame != null) send(frame);
    }

    @Override public boolean isPayload() { return false; }

    @Override public boolean isOpen() { return !closed && channel.isActive(); }

    @Override public boolean canTakeMore() { return channel.isWritable(); }

    @Override
    public void close(final String reason) {
        if (closed) return;
        send(new SocialMessage.Error(SocialMessage.Error.REPLACED, reason, "session"));
        closed = true;
        channel.close();
    }
}
