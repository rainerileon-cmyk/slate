package dev.fallingcloud.slate.menu.client;

import dev.fallingcloud.slate.menu.MenuConfig;
import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.menu.client.servers.ServerActions;
import dev.fallingcloud.slate.menu.client.worlds.WorldActions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelSummary;
import org.jetbrains.annotations.Nullable;

/**
 * "Continue where you left off": the most recent of the newest world (from the level summaries) and the
 * last server joined (recorded in menu.json from CLIENT_JOINED_SERVER). Worlds carry their own
 * last-played stamp so the disk is the source of truth for them; servers only exist in our record.
 */
public final class LastPlayed {

    public enum Kind { WORLD, SERVER }

    /** @param id world folder or server address; {@code summary} present for worlds resolved from disk */
    public record Target(Kind kind, String id, String name, long at, @Nullable LevelSummary summary) {}

    private static long sessionStartMs;

    /** From the config only, no disk access (dev-mode placeholders, quick actions). */
    public static Optional<Target> quick() {
        final MenuConfig c = SlateMenu.config();
        if ("server".equals(c.lastKind) && !c.lastServerAddress.isBlank()) {
            return Optional.of(new Target(Kind.SERVER, c.lastServerAddress, c.lastServerName, c.lastPlayedAt, null));
        }
        if ("world".equals(c.lastKind) && !c.lastWorld.isBlank()) {
            return Optional.of(new Target(Kind.WORLD, c.lastWorld, c.lastWorldName, c.lastPlayedAt, null));
        }
        return Optional.empty();
    }

    /** Scans the saves folder; the callback runs on the render thread. */
    public static void resolve(final Consumer<Optional<Target>> callback) {
        final Optional<Target> server = quick().filter(t -> t.kind() == Kind.SERVER);
        WorldActions.loadAll().whenCompleteAsync((list, err) -> {
            Target world = null;
            if (list != null) {
                for (final LevelSummary s : list) {
                    if (s.isDisabled() || s.isLocked()) continue;
                    if (world == null || s.getLastPlayed() > world.at()) world = new Target(Kind.WORLD, s.getLevelId(), s.getLevelName(), s.getLastPlayed(), s);
                }
            }
            Optional<Target> best = Optional.ofNullable(world);
            if (server.isPresent() && (world == null || server.get().at() >= world.at())) best = server;
            callback.accept(best);
        }, Minecraft.getInstance());
    }

    public static void play(final Target t, @Nullable final Screen parent) {
        if (t == null) return;
        if (t.kind() == Kind.WORLD) {
            if (t.summary() != null) WorldActions.play(t.summary(), parent);
            else WorldActions.play(t.id(), parent);
        } else {
            ServerActions.join(t.id(), t.name(), parent);
        }
    }

    /** CLIENT_JOINED_SERVER: remember what we joined and start the session clock. */
    public static void recordJoin() {
        final Minecraft mc = Minecraft.getInstance();
        sessionStartMs = System.currentTimeMillis();
        final long now = sessionStartMs;
        try {
            if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                final IntegratedServer s = mc.getSingleplayerServer();
                String folder = "";
                try {
                    folder = s.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
                } catch (final Exception ignored) {}
                final String f = folder;
                final String name = s.getWorldData().getLevelName();
                SlateMenu.configFile().update(c -> {
                    c.lastKind = "world";
                    c.lastWorld = f;
                    c.lastWorldName = name;
                    c.lastPlayedAt = now;
                });
            } else if (mc.getCurrentServer() != null && !mc.getCurrentServer().isLan() && !mc.getCurrentServer().isRealm()) {
                final ServerData d = mc.getCurrentServer();
                final String address = d.ip, name = d.name == null ? d.ip : d.name;
                SlateMenu.configFile().update(c -> {
                    c.lastKind = "server";
                    c.lastServerAddress = address;
                    c.lastServerName = name;
                    c.lastPlayedAt = now;
                    final List<MenuConfig.RecentServer> recents = new ArrayList<>();
                    recents.add(new MenuConfig.RecentServer(name, address, now));
                    for (final MenuConfig.RecentServer r : c.recentServers) {
                        if (r == null || r.address == null) continue;
                        if (ServerActions.normalize(r.address).equals(ServerActions.normalize(address))) continue;
                        recents.add(r);
                    }
                    final int max = Math.max(1, c.recentServersMax);
                    c.recentServers = recents.size() > max ? new ArrayList<>(recents.subList(0, max)) : recents;
                });
            }
        } catch (final Exception e) {
            SlateMenu.LOGGER.warn("[Slate Menu] could not record last played: {}", e.toString());
        }
    }

    /** Milliseconds since the current world/server was joined (0 when not in a world). */
    public static long sessionMs() {
        if (Minecraft.getInstance().level == null || sessionStartMs == 0) return 0;
        return System.currentTimeMillis() - sessionStartMs;
    }

    public static void clearSession() {
        sessionStartMs = 0;
    }

    private LastPlayed() {}
}
