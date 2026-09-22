package dev.fallingcloud.slate.multiplayer.client;

import dev.fallingcloud.slate.core.net.SlateNetwork;
import dev.fallingcloud.slate.core.net.blob.BlobPayloads;
import dev.fallingcloud.slate.core.net.blob.BlobSender;
import dev.fallingcloud.slate.multiplayer.social.BlobFrames;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage;
import dev.fallingcloud.slate.multiplayer.social.SocialPayload;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The social link to the server currently played on, over the {@code slate:social} payload channel.
 * Blobs addressed to social targets ({@code p:}, {@code g:}, {@code s:}) are wrapped into passthrough
 * frames so the hub relays them itself (it can then reach friends connected over TCP and store DM
 * attachments); anything else keeps flowing through Core's blob channel untouched.
 */
final class PayloadLink implements SocialLink {

    private final Set<String> socialBlobs = new HashSet<>();
    private final String address;

    private final BlobSender.Link blobLink = new BlobSender.Link() {
        @Override public boolean ready() { return isConnected(); }

        @Override
        public void send(final CustomPacketPayload payload) {
            if (payload instanceof BlobPayloads.Start s) {
                if (isSocialTarget(s.target())) socialBlobs.add(s.id()); else socialBlobs.remove(s.id());
                route(payload, socialBlobs.contains(s.id()));
            } else if (payload instanceof BlobPayloads.Chunk c) {
                route(payload, socialBlobs.contains(c.id()));
            } else if (payload instanceof BlobPayloads.End e) {
                route(payload, socialBlobs.remove(e.id()));
            } else {
                SlateNetwork.get().sendToServer(payload);
            }
        }
    };

    PayloadLink(final String address) {
        this.address = address == null ? "" : address;
    }

    static boolean isSocialTarget(final String target) {
        return target != null && (target.startsWith("p:") || target.startsWith("g:") || target.startsWith("s:"));
    }

    private void route(final CustomPacketPayload payload, final boolean social) {
        if (social) {
            final SocialMessage.BlobPassthrough frame = BlobFrames.wrap(payload);
            if (frame != null) send(frame);
        } else {
            SlateNetwork.get().sendToServer(payload);
        }
    }

    @Override public String describe() { return "server " + address; }

    @Override
    public boolean isConnected() {
        return Minecraft.getInstance().getConnection() != null && SlateNetwork.get().serverHasChannel(SocialPayload.TYPE);
    }

    @Override
    public void send(final SocialMessage message) {
        if (isConnected()) SlateNetwork.get().sendToServer(new SocialPayload(message));
    }

    @Override public BlobSender.Link blobLink() { return blobLink; }

    @Override public void tick() {}

    @Override public void close() { socialBlobs.clear(); }
}
