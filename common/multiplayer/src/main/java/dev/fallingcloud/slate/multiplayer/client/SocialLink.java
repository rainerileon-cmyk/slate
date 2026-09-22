package dev.fallingcloud.slate.multiplayer.client;

import dev.fallingcloud.slate.core.net.blob.BlobSender;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage;

/**
 * The client's transport to a hub: the TCP link to the home hub, or the payload channel of the server
 * currently played on. {@link SocialClient} owns exactly one at a time.
 */
public interface SocialLink {

    /** Human readable target ("hub play.example.com:25580" / "server play.example.com"). */
    String describe();

    boolean isConnected();

    void send(SocialMessage message);

    /** Where {@code BlobSender} pushes blobs while this link is active. */
    BlobSender.Link blobLink();

    /** Once per client tick. */
    void tick();

    /** Round-trip time to the hub in ms, or -1 when unknown. */
    default long latencyMs() { return -1; }

    void close();
}
