package dev.fallingcloud.slate.multiplayer.hub;

import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.yggdrasil.ProfileResult;
import dev.fallingcloud.slate.multiplayer.MultiplayerServerConfig;
import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;
import dev.fallingcloud.slate.multiplayer.social.SocialCodec;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage.Error;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * The hub's TCP listener (netty, the copy bundled with Minecraft). Frames are {@code [len:4][kind:1]
 * [fields]} as produced by {@link SocialCodec}; the pipeline strips/adds the length. Handshake: the hub
 * sends {@link SocialMessage.HubInfo}, the client answers {@link SocialMessage.Hello}; in online mode the
 * hub verifies the Mojang session on a worker thread (exactly vanilla's login dance: the client called
 * {@code joinServer} with the same serverId first), then every frame is marshalled onto the server main
 * thread. A port that is already taken logs and leaves the hub payload-only.
 */
final class HubListener {

    private static final int MAX_FRAME = 2 * 1024 * 1024;
    private static final java.util.regex.Pattern NAME = java.util.regex.Pattern.compile("^[A-Za-z0-9_]{1,16}$");

    private final SocialHub hub;
    private final MinecraftServer server;
    private final MultiplayerServerConfig cfg;
    private final AtomicInteger connections = new AtomicInteger();
    @Nullable private EventLoopGroup boss, workers;
    @Nullable private Channel channel;

    HubListener(final SocialHub hub, final MinecraftServer server, final MultiplayerServerConfig cfg) {
        this.hub = hub;
        this.server = server;
        this.cfg = cfg;
    }

    boolean start() {
        boss = new NioEventLoopGroup(1, new DefaultThreadFactory("slate-hub-boss", true));
        workers = new NioEventLoopGroup(2, new DefaultThreadFactory("slate-hub-io", true));
        final int idle = Math.max(30, cfg.idleTimeoutSeconds);
        try {
            final ServerBootstrap b = new ServerBootstrap()
                .group(boss, workers)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(256 * 1024, 1024 * 1024))
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(final SocketChannel ch) {
                        ch.pipeline()
                            .addLast("idle", new IdleStateHandler(idle, 0, 0, TimeUnit.SECONDS))
                            .addLast("frame", new LengthFieldBasedFrameDecoder(MAX_FRAME, 0, 4, 0, 4))
                            .addLast("prepend", new LengthFieldPrepender(4))
                            .addLast("hub", new Handler());
                    }
                });
            final String bind = cfg.bindAddress == null || cfg.bindAddress.isBlank() ? "0.0.0.0" : cfg.bindAddress.trim();
            channel = b.bind(bind, cfg.port).syncUninterruptibly().channel();
            return true;
        } catch (final Exception e) {
            SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] cannot listen on {}:{} ({}); hub stays payload-only", cfg.bindAddress, cfg.port, e.toString());
            stop();
            return false;
        }
    }

    void stop() {
        if (channel != null) {
            try { channel.close().syncUninterruptibly(); } catch (final Exception ignored) {}
            channel = null;
        }
        if (boss != null) { boss.shutdownGracefully(0, 500, TimeUnit.MILLISECONDS); boss = null; }
        if (workers != null) { workers.shutdownGracefully(0, 500, TimeUnit.MILLISECONDS); workers = null; }
    }

    private static ByteBuf frame(final SocialMessage m) { return SocialCodec.encode(m); }

    /** One TCP connection. Runs on the channel's event loop; hub work is handed to the server thread. */
    private final class Handler extends SimpleChannelInboundHandler<ByteBuf> {

        @Nullable private RelaySession session;
        private boolean authed;
        private boolean authInFlight;

        @Override
        public void channelActive(final ChannelHandlerContext ctx) {
            if (connections.incrementAndGet() > cfg.maxConnections) {
                ctx.writeAndFlush(frame(new Error(Error.TOO_MANY, "hub is full", "hello"))).addListener(ChannelFutureListener.CLOSE);
                return;
            }
            ctx.writeAndFlush(frame(new SocialMessage.HubInfo(hub.hubName(), SocialMessage.PROTOCOL, cfg.onlineMode)));
        }

        @Override
        public void channelInactive(final ChannelHandlerContext ctx) {
            connections.decrementAndGet();
            final RelaySession s = session;
            if (s != null) server.execute(() -> { if (SocialHub.current() == hub) hub.closed(s); });
        }

        @Override
        protected void channelRead0(final ChannelHandlerContext ctx, final ByteBuf buf) {
            final SocialMessage m;
            try {
                m = SocialCodec.decode(buf);
            } catch (final Exception e) {
                SlateMultiplayer.LOGGER.debug("[Slate Multiplayer] bad frame from {}: {}", ctx.channel().remoteAddress(), e.toString());
                ctx.close();
                return;
            }
            if (!authed) {
                if (m instanceof SocialMessage.Hello h) { if (!authInFlight) hello(ctx, h); }
                else if (m instanceof SocialMessage.Ping p) ctx.writeAndFlush(frame(new SocialMessage.Pong(p.sentMs())));
                else if (!authInFlight) ctx.writeAndFlush(frame(new Error(Error.UNAUTHORIZED, "say hello first", "session")));
                return;
            }
            final RelaySession s = session;
            if (s == null) return;
            server.execute(() -> { if (SocialHub.current() == hub) hub.handle(s, m); });
        }

        private void hello(final ChannelHandlerContext ctx, final SocialMessage.Hello h) {
            if (h.protocol() != SocialMessage.PROTOCOL) {
                reject(ctx, Error.PROTOCOL, "protocol " + h.protocol() + " not supported (hub speaks " + SocialMessage.PROTOCOL + ")");
                return;
            }
            if (h.uuid() == null || SocialCodec.isNil(h.uuid()) || !NAME.matcher(h.name()).matches()) {
                reject(ctx, Error.BAD_REQUEST, "invalid identity");
                return;
            }
            if (!cfg.onlineMode) {
                accept(ctx, h.uuid(), h.name());
                return;
            }
            authInFlight = true;
            final String name = h.name(), serverId = h.serverId();
            final UUID uuid = h.uuid();
            Util.backgroundExecutor().execute(() -> {
                try {
                    final ProfileResult r = server.getSessionService().hasJoinedServer(name, serverId, null);
                    if (r != null && r.profile() != null && uuid.equals(r.profile().getId())) accept(ctx, uuid, r.profile().getName());
                    else reject(ctx, Error.UNAUTHORIZED, "session not verified by Mojang (offline account, or the hub is in online mode and you are not)");
                } catch (final AuthenticationUnavailableException e) {
                    reject(ctx, Error.INTERNAL, "session servers unavailable, try again");
                } catch (final Exception e) {
                    reject(ctx, Error.UNAUTHORIZED, "session check failed: " + e.getMessage());
                }
            });
        }

        private void accept(final ChannelHandlerContext ctx, final UUID uuid, final String name) {
            ctx.executor().execute(() -> {
                authInFlight = false;
                if (!ctx.channel().isActive()) return;
                final RelaySession s = new RelaySession(ctx.channel(), uuid, name, cfg);
                session = s;
                authed = true;
                server.execute(() -> {
                    if (SocialHub.current() == hub) hub.open(s);
                    else s.close("hub stopped");
                });
            });
        }

        private void reject(final ChannelHandlerContext ctx, final int code, final String message) {
            ctx.executor().execute(() -> {
                authInFlight = false;
                SlateMultiplayer.LOGGER.info("[Slate Multiplayer] refused hub client {}: {}", ctx.channel().remoteAddress(), message);
                ctx.writeAndFlush(frame(new Error(code, message, "hello"))).addListener(ChannelFutureListener.CLOSE);
            });
        }

        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
            if (evt instanceof IdleStateEvent) ctx.close();
        }

        @Override
        public void exceptionCaught(final ChannelHandlerContext ctx, final Throwable cause) {
            SlateMultiplayer.LOGGER.debug("[Slate Multiplayer] hub connection {} dropped: {}", ctx.channel().remoteAddress(), cause.toString());
            ctx.close();
        }
    }
}
