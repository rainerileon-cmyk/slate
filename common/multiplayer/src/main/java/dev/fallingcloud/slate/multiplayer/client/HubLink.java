package dev.fallingcloud.slate.multiplayer.client;

import dev.fallingcloud.slate.core.net.blob.BlobSender;
import dev.fallingcloud.slate.multiplayer.MultiplayerConfigs;
import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;
import dev.fallingcloud.slate.multiplayer.social.BlobFrames;
import dev.fallingcloud.slate.multiplayer.social.SocialCodec;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage.Error;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The TCP link to the home hub. Works from the title screen and stays up in-game. Reconnects with
 * backoff (2 s doubling to 60 s; a refused login waits a full minute). Handshake mirrors vanilla login:
 * the hub says {@link SocialMessage.HubInfo}; if it runs in online mode the client calls
 * {@code joinServer(uuid, accessToken, serverId)} with a random serverId, then sends {@link SocialMessage.Hello}
 * and waits for {@link SocialMessage.Welcome}. Every received message is handed to the client main
 * thread; sends may come from any thread.
 */
final class HubLink implements SocialLink {

    private static final int MAX_FRAME = 2 * 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static EventLoopGroup group;

    private final String host;
    private final int port;
    private final Consumer<SocialMessage> onMessage;
    private final BiConsumer<LinkState, String> onState;
    private volatile Channel channel;
    private volatile LinkState state = LinkState.NONE;
    private volatile boolean closed;
    private long nextAttemptMs;
    private int attempts;
    private long lastPingMs;
    private volatile long latencyMs = -1;
    private volatile String lastError = "";

    /** Ids of in-flight screen-share frames: the only blobs that may be dropped under backpressure. */
    private final java.util.Set<String> frameBlobs = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final BlobSender.Link blobLink = new BlobSender.Link() {
        @Override public boolean ready() { return state == LinkState.CONNECTED; }

        @Override
        public void send(final CustomPacketPayload payload) {
            final Channel ch = channel;
            if (payload instanceof dev.fallingcloud.slate.core.net.blob.BlobPayloads.Start s) {
                if ("frame".equals(s.kind())) {
                    if (ch == null || !ch.isWritable()) return;          // skip the whole frame
                    frameBlobs.add(s.id());
                } else frameBlobs.remove(s.id());
            } else if (payload instanceof dev.fallingcloud.slate.core.net.blob.BlobPayloads.Chunk c) {
                if (frameBlobs.contains(c.id()) && (ch == null || !ch.isWritable())) { frameBlobs.remove(c.id()); return; }
            } else if (payload instanceof dev.fallingcloud.slate.core.net.blob.BlobPayloads.End e) {
                frameBlobs.remove(e.id());
            }
            final SocialMessage.BlobPassthrough frame = BlobFrames.wrap(payload);
            if (frame != null) HubLink.this.send(frame);
        }
    };

    HubLink(final String host, final int port, final Consumer<SocialMessage> onMessage, final BiConsumer<LinkState, String> onState) {
        this.host = host;
        this.port = port;
        this.onMessage = onMessage;
        this.onState = onState;
    }

    private static synchronized EventLoopGroup group() {
        if (group == null) group = new NioEventLoopGroup(1, new DefaultThreadFactory("slate-hub-client", true));
        return group;
    }

    String host() { return host; }

    int port() { return port; }

    LinkState state() { return state; }

    String lastError() { return lastError; }

    @Override public String describe() { return "hub " + host + ":" + port; }

    @Override public boolean isConnected() { return state == LinkState.CONNECTED && channel != null && channel.isActive(); }

    @Override public long latencyMs() { return latencyMs; }

    @Override public BlobSender.Link blobLink() { return blobLink; }

    private void setState(final LinkState s, final String detail) {
        state = s;
        if (detail != null) lastError = detail;
        final String d = detail == null ? "" : detail;
        Minecraft.getInstance().execute(() -> onState.accept(s, d));
    }

    /** Reconnect now (settings "Connect" button). */
    void connectNow() {
        attempts = 0;
        nextAttemptMs = 0;
        if (channel != null) { channel.close(); channel = null; }
        if (state == LinkState.CONNECTED || state == LinkState.AUTHENTICATING || state == LinkState.CONNECTING) state = LinkState.NONE;
    }

    @Override
    public void tick() {
        if (closed) return;
        final long now = System.currentTimeMillis();
        if (state == LinkState.NONE || state == LinkState.FAILED) {
            if (now >= nextAttemptMs) connect();
            return;
        }
        if (state == LinkState.CONNECTED && now - lastPingMs > 20_000) {
            lastPingMs = now;
            send(new SocialMessage.Ping(now));
        }
    }

    private void connect() {
        attempts++;
        setState(LinkState.CONNECTING, null);
        try {
            final Bootstrap b = new Bootstrap()
                .group(group())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 8000)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(256 * 1024, 1024 * 1024))
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(final SocketChannel ch) {
                        ch.pipeline()
                            .addLast("idle", new IdleStateHandler(75, 0, 0, TimeUnit.SECONDS))
                            .addLast("frame", new LengthFieldBasedFrameDecoder(MAX_FRAME, 0, 4, 0, 4))
                            .addLast("prepend", new LengthFieldPrepender(4))
                            .addLast("hub", new Handler());
                    }
                });
            b.connect(host, port).addListener(f -> {
                if (f.isSuccess()) return;
                fail("cannot reach " + host + ":" + port + " (" + brief(f.cause()) + ")", false);
            });
        } catch (final Exception e) {
            fail("cannot connect: " + brief(e), false);
        }
    }

    private static String brief(final Throwable t) {
        if (t == null) return "unknown";
        final String m = t.getMessage();
        return m == null || m.isBlank() ? t.getClass().getSimpleName() : m;
    }

    private void fail(final String why, final boolean refused) {
        channel = null;
        final long backoff = refused ? 60_000L : Math.min(60_000L, 2000L << Math.min(5, Math.max(0, attempts - 1)));
        nextAttemptMs = System.currentTimeMillis() + backoff;
        setState(LinkState.FAILED, why);
    }

    @Override
    public void send(final SocialMessage message) {
        final Channel ch = channel;
        if (ch == null || !ch.isActive()) return;
        ch.writeAndFlush(SocialCodec.encode(message), ch.voidPromise());
    }

    @Override
    public void close() {
        closed = true;
        final Channel ch = channel;
        channel = null;
        if (ch != null) ch.close();
        state = LinkState.NONE;
    }

    // ------------------------------------------------------------------ handshake

    private void handshake(final SocialMessage.HubInfo info) {
        final Minecraft mc = Minecraft.getInstance();
        final User user = mc.getUser();
        final UUID uuid = user.getProfileId();
        final String name = user.getName();
        final String serverId = "%08x%08x%04x".formatted(RANDOM.nextInt(), RANDOM.nextInt(), RANDOM.nextInt() & 0xFFFF);
        if (info.protocol() != SocialMessage.PROTOCOL) {
            fail("hub speaks protocol " + info.protocol() + ", this client " + SocialMessage.PROTOCOL, true);
            final Channel ch = channel;
            if (ch != null) ch.close();
            return;
        }
        setState(LinkState.AUTHENTICATING, null);
        if (info.onlineMode() && MultiplayerConfigs.client().hubAuth) {
            Util.ioPool().execute(() -> {
                try {
                    mc.getMinecraftSessionService().joinServer(uuid, user.getAccessToken(), serverId);
                } catch (final Exception e) {
                    // Offline / dev account: send Hello anyway; the hub decides.
                    lastError = "Mojang session: " + brief(e);
                    SlateMultiplayer.LOGGER.info("[Slate Multiplayer] joinServer failed ({}), hub will decide", brief(e));
                }
                send(new SocialMessage.Hello(uuid, name, SocialMessage.PROTOCOL, serverId));
            });
        } else {
            send(new SocialMessage.Hello(uuid, name, SocialMessage.PROTOCOL, serverId));
        }
    }

    /** Runs on the netty thread. */
    private final class Handler extends SimpleChannelInboundHandler<ByteBuf> {

        @Override
        public void channelActive(final ChannelHandlerContext ctx) {
            channel = ctx.channel();
            lastPingMs = System.currentTimeMillis();
            if (closed) ctx.close();
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) {
            if (channel == ctx.channel()) channel = null;
            if (closed) return;
            if (state == LinkState.CONNECTED || state == LinkState.AUTHENTICATING || state == LinkState.CONNECTING) {
                fail("connection lost", false);
            }
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, final ByteBuf buf) {
            final SocialMessage m;
            try {
                m = SocialCodec.decode(buf);
            } catch (final Exception e) {
                SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] bad frame from hub: {}", e.toString());
                return;
            }
            switch (m) {
                case SocialMessage.HubInfo info -> { handshake(info); return; }
                case SocialMessage.Welcome w -> { attempts = 0; setState(LinkState.CONNECTED, ""); }
                case SocialMessage.Pong p -> latencyMs = Math.max(0, System.currentTimeMillis() - p.sentMs());
                case Error e when "hello".equals(e.context()) || "session".equals(e.context()) -> {
                    final boolean replaced = e.code() == Error.REPLACED;
                    fail(e.message(), true);
                    if (replaced) { closed = true; SlateMultiplayer.LOGGER.info("[Slate Multiplayer] hub link closed: {}", e.message()); }
                    ctx.close();
                }
                default -> {}
            }
            Minecraft.getInstance().execute(() -> onMessage.accept(m));
        }

        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
            if (evt instanceof IdleStateEvent) { lastError = "hub stopped answering"; ctx.close(); }
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
            lastError = brief(cause);
            ctx.close();
        }
    }

    /** True once the hub refused this identity for good (replaced by a newer connection) or after close(). */
    boolean isDead() { return closed; }
}
