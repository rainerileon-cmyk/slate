package dev.fallingcloud.slate.menu.client.servers;

import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.menu.client.MenuIo;
import java.net.UnknownHostException;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Vanilla's {@link ServerStatusPinger} with the bookkeeping the vanilla list keeps in its entries:
 * pings run on the module pool (DNS resolution blocks), the {@link ServerData.State} is advanced like
 * vanilla does, and a watchdog turns refused connections into UNREACHABLE (vanilla's pinger only writes
 * the "can't connect" MOTD on failure and leaves the state at PINGING). One instance per screen: call
 * {@link #tick()} every tick and {@link #close()} when the screen goes away.
 */
public final class ServerPinger {

    private static final long TIMEOUT_MS = 12_000;

    private final ServerStatusPinger pinger = new ServerStatusPinger();
    private final Map<ServerData, Long> inFlight = new IdentityHashMap<>();
    private final Map<ServerData, Runnable> callbacks = new IdentityHashMap<>();

    /** Starts a ping if the server has not been pinged yet (state INITIAL). {@code onChanged} runs on the render thread. */
    public void ping(final ServerData data, final Runnable onChanged) {
        if (data.state() != ServerData.State.INITIAL) return;
        data.setState(ServerData.State.PINGING);
        data.motd = CommonComponents.EMPTY;
        data.status = CommonComponents.EMPTY;
        synchronized (inFlight) { inFlight.put(data, System.currentTimeMillis()); callbacks.put(data, onChanged); }
        MenuIo.POOL.submit(() -> {
            try {
                pinger.pingServer(data, () -> MenuIo.onClient(onChanged), () -> {
                    data.setState(data.protocol == SharedConstants.getCurrentVersion().getProtocolVersion()
                        ? ServerData.State.SUCCESSFUL : ServerData.State.INCOMPATIBLE);
                    finish(data);
                    MenuIo.onClient(onChanged);
                });
            } catch (final UnknownHostException e) {
                fail(data, Component.translatable("multiplayer.status.cannot_resolve").withStyle(ChatFormatting.RED), onChanged);
            } catch (final Exception e) {
                SlateMenu.LOGGER.debug("[Slate Menu] ping {} failed: {}", data.ip, e.toString());
                fail(data, Component.translatable("multiplayer.status.cannot_connect").withStyle(ChatFormatting.RED), onChanged);
            }
        });
    }

    /** Forget the last result and ping again. */
    public void refresh(final ServerData data, final Runnable onChanged) {
        if (data.state() == ServerData.State.PINGING) return;
        data.setState(ServerData.State.INITIAL);
        ping(data, onChanged);
    }

    private void fail(final ServerData data, final Component motd, final Runnable onChanged) {
        data.setState(ServerData.State.UNREACHABLE);
        data.motd = motd;
        data.status = CommonComponents.EMPTY;
        data.playerList = java.util.List.of();
        finish(data);
        MenuIo.onClient(onChanged);
    }

    private void finish(final ServerData data) {
        synchronized (inFlight) { inFlight.remove(data); callbacks.remove(data); }
    }

    /** Ticks vanilla's connections and expires pings that never came back. */
    public void tick() {
        pinger.tick();
        final long now = System.currentTimeMillis();
        final String cantConnect = I18n.get("multiplayer.status.cannot_connect");
        final Map<ServerData, Runnable> expired = new IdentityHashMap<>();
        synchronized (inFlight) {
            for (final Map.Entry<ServerData, Long> e : inFlight.entrySet()) {
                final ServerData d = e.getKey();
                final boolean refused = d.motd != null && cantConnect.equals(d.motd.getString());
                if (d.state() != ServerData.State.PINGING || refused || now - e.getValue() > TIMEOUT_MS) expired.put(d, callbacks.get(d));
            }
        }
        for (final Map.Entry<ServerData, Runnable> e : expired.entrySet()) {
            final ServerData d = e.getKey();
            if (d.state() == ServerData.State.PINGING) {
                d.setState(ServerData.State.UNREACHABLE);
                d.status = CommonComponents.EMPTY;
                if (d.motd == null || d.motd.getString().isEmpty()) d.motd = Component.translatable("multiplayer.status.cannot_connect").withStyle(ChatFormatting.RED);
            }
            finish(d);
            if (e.getValue() != null) e.getValue().run();
        }
    }

    public void close() {
        pinger.removeAll();
        synchronized (inFlight) { inFlight.clear(); callbacks.clear(); }
    }

    /** A ping came back (compatible or not). */
    public static boolean isOnline(final ServerData d) {
        return d.state() == ServerData.State.SUCCESSFUL || d.state() == ServerData.State.INCOMPATIBLE;
    }

    /** "12/50" or empty while unknown. */
    public static String players(final ServerData d) {
        return d.players == null ? "" : d.players.online() + "/" + d.players.max();
    }
}
