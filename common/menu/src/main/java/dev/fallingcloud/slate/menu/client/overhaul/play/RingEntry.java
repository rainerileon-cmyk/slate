package dev.fallingcloud.slate.menu.client.overhaul.play;

import dev.fallingcloud.slate.core.stage.node.CubePlanetNode;
import dev.fallingcloud.slate.core.stage.node.ItemNode;
import dev.fallingcloud.slate.core.stage.node.PivotNode;
import dev.fallingcloud.slate.menu.client.play.model.ServerActions;
import dev.fallingcloud.slate.menu.client.play.model.ServerMeta;
import dev.fallingcloud.slate.menu.client.play.model.ServerPinger;
import dev.fallingcloud.slate.menu.client.play.model.WorldEntry;
import dev.fallingcloud.slate.menu.client.play.model.WorldFavorites;
import java.util.Locale;
import net.minecraft.client.multiplayer.ServerData;
import org.jetbrains.annotations.Nullable;

/**
 * One planet on a ring of the Play screen: a world or a server, with what the ring needs to sort, search and show
 * it. The stage nodes are made when the planet comes near the front of its ring and given back when it has gone
 * round the back, so a ring of hundreds holds only a dozen planets at a time.
 */
abstract class RingEntry {

    /** Where the planet sits on its ring: carries the tilt towards the viewer. Null while off the stage. */
    @Nullable PivotNode holder;
    @Nullable CubePlanetNode planet;
    @Nullable ItemNode star;
    /** Eases in when the planet enters the ring's visible part, out when it leaves. */
    float shown;

    /** Stable across sessions: the world's folder, the server's address. */
    abstract String id();

    abstract String name();

    abstract boolean favorite();

    abstract void favorite(boolean on);

    /** Milliseconds since the epoch, 0 when unknown. */
    abstract long lastPlayed();

    abstract CubePlanetNode grow();

    boolean matches(final String query) {
        if (query == null || query.isBlank()) return true;
        final String q = query.toLowerCase(Locale.ROOT).trim();
        return name().toLowerCase(Locale.ROOT).contains(q) || id().toLowerCase(Locale.ROOT).contains(q);
    }

    /** A world of the saves folder. */
    static final class World extends RingEntry {
        final WorldEntry world;

        World(final WorldEntry world) { this.world = world; }

        @Override String id() { return world.id(); }

        @Override String name() { return world.name(); }

        @Override boolean favorite() { return world.favorite; }

        @Override void favorite(final boolean on) {
            WorldFavorites.setFavorite(world.id(), on);
            world.favorite = on;
        }

        @Override long lastPlayed() { return world.summary.getLastPlayed(); }

        @Override boolean matches(final String query) { return world.matches(query); }

        @Override CubePlanetNode grow() { return CubePlanetNode.create(CubePlanetNode.seedOf("world:" + world.id())); }
    }

    /** A server of the server list. */
    static final class Server extends RingEntry {
        static final int PINGING = 0xFF8A8880, ONLINE = 0xFF5CD07F, OUTDATED = 0xFFE0A458, OFFLINE = 0xFFE5484D;

        final ServerData data;
        final long lastJoined;

        Server(final ServerData data, final long lastJoined) {
            this.data = data;
            this.lastJoined = lastJoined;
        }

        @Override String id() { return ServerActions.normalize(data.ip); }

        @Override String name() { return data.name == null || data.name.isBlank() ? data.ip : data.name; }

        @Override boolean favorite() { return ServerMeta.isFavorite(data.ip); }

        @Override void favorite(final boolean on) { ServerMeta.setFavorite(data.ip, on); }

        @Override long lastPlayed() { return lastJoined; }

        @Override boolean matches(final String query) {
            if (super.matches(query)) return true;
            final String q = query.toLowerCase(Locale.ROOT).trim();
            return data.motd != null && data.motd.getString().toLowerCase(Locale.ROOT).contains(q);
        }

        @Override CubePlanetNode grow() { return CubePlanetNode.server(CubePlanetNode.seedOf("server:" + id())); }

        /** The colour of the planet's ring of lights: how the server is doing. */
        int statusColor() {
            return switch (data.state()) {
                case SUCCESSFUL -> ONLINE;
                case INCOMPATIBLE -> OUTDATED;
                case UNREACHABLE -> OFFLINE;
                default -> PINGING;
            };
        }

        boolean online() { return ServerPinger.isOnline(data); }
    }
}
