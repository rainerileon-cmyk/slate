package dev.fallingcloud.slate.core.net.blob;

import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.net.Flow;
import dev.fallingcloud.slate.core.net.NetContext;
import dev.fallingcloud.slate.core.net.SlateNetwork;
import dev.fallingcloud.slate.core.platform.SlatePlatform;

/** Registers the blob payloads (both sides) and wires the relay/receiver. Called from Slate.init(). */
public final class BlobChannel {

    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        final SlateNetwork net = SlateNetwork.get();
        net.register(BlobPayloads.Start.TYPE, BlobPayloads.Start.CODEC, Flow.BOTH, (p, ctx) -> {
            if (ctx.isClient()) ClientSide.start(p); else BlobRelay.onStart(p, ctx.sender());
        });
        net.register(BlobPayloads.Chunk.TYPE, BlobPayloads.Chunk.CODEC, Flow.BOTH, (p, ctx) -> {
            if (ctx.isClient()) ClientSide.chunk(p); else BlobRelay.onChunk(p, ctx.sender());
        });
        net.register(BlobPayloads.End.TYPE, BlobPayloads.End.CODEC, Flow.BOTH, (p, ctx) -> {
            if (ctx.isClient()) ClientSide.end(p); else BlobRelay.onEnd(p, ctx.sender());
        });
        SlateEvents.PLAYER_LEFT.register(p -> BlobRelay.onDisconnect(p.getUUID()));
        if (SlatePlatform.get().isClient()) ClientSide.init();
    }

    /** Client-only references live here so a dedicated server never resolves them. */
    private static final class ClientSide {
        static void init() {
            SlateEvents.CLIENT_TICK_END.register(() -> { BlobSender.tick(); BlobReceiver.tick(); });
            SlateEvents.CLIENT_LEFT_SERVER.register(BlobReceiver::clear);
        }
        static void start(final BlobPayloads.Start p) { BlobReceiver.handleStart(p); }
        static void chunk(final BlobPayloads.Chunk p) { BlobReceiver.handleChunk(p); }
        static void end(final BlobPayloads.End p) { BlobReceiver.handleEnd(p); }
    }

    private BlobChannel() {}
}
