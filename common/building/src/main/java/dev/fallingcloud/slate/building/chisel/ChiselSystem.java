package dev.fallingcloud.slate.building.chisel;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.net.ChiselGroupsSync;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.net.SlateNetwork;
import java.util.List;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Common (both sides) init of chisel groups: index rebuild on {@code SERVER_STARTED} and datapack reload
 * ({@code PlayerList.reloadResources} TAIL, {@code mixin.chisel.PlayerListReloadMixin}), sync to joining players and
 * to everyone after a rebuild. Called from {@code SlateBuilding.init()}.
 *
 * <p>Singleplayer fast path: the host of an integrated server (singleplayer or LAN) reads the server's index directly
 * ({@link ChiselGroups#client()}), so it is never sent to them; LAN guests get the sync like on a dedicated server.
 *
 * <p>Owner: I (chisel).
 */
public final class ChiselSystem {

    public static void init() {
        SlateEvents.SERVER_STARTED.register(ChiselSystem::rebuild);
        SlateEvents.SERVER_STOPPING.register(ChiselGroups::forget);
        SlateEvents.PLAYER_JOINED.register(ChiselSystem::sendTo);
    }

    /**
     * Rebuilds the index from the current recipes, tags, datapacks and {@code building-chisel.json}, then resends it
     * to everyone online. Server thread. Also the hook for commands that reload the server config.
     */
    public static void rebuild(final MinecraftServer server) {
        try {
            ChiselGroups.rebuild(server);
        } catch (final RuntimeException e) {
            SlateBuilding.LOGGER.error("[Slate Building] could not build the chisel groups", e);
            return;
        }
        broadcast(server);
    }

    /** Datapacks were reloaded ({@code /reload}); recipes and tags may have changed. */
    public static void onDatapackReload(final MinecraftServer server) {
        rebuild(server);
    }

    /** Sends the index to {@code player}, unless they host this integrated server (they read it directly). */
    public static void sendTo(final ServerPlayer player) {
        if (player.server.isSingleplayerOwner(player.getGameProfile())) return;
        for (final ChiselGroupsSync payload : ChiselSync.payloads(ChiselGroups.server(player.server))) {
            SlateNetwork.get().sendToPlayer(player, payload);
        }
    }

    /** Sends the index to everyone online (after a rebuild). */
    public static void broadcast(final MinecraftServer server) {
        List<ChiselGroupsSync> payloads = null;
        for (final ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (server.isSingleplayerOwner(player.getGameProfile())) continue;
            if (payloads == null) payloads = ChiselSync.payloads(ChiselGroups.server(server));
            for (final ChiselGroupsSync payload : payloads) SlateNetwork.get().sendToPlayer(player, payload);
        }
    }

    /**
     * Loader hook: the unwaxed form of a block when the loader keeps modded copper outside the vanilla maps (NeoForge's
     * waxables data map). Returning null falls back to vanilla's map.
     */
    public static void setUnwaxLookup(final @Nullable Function<Block, Block> lookup) {
        ChiselRules.unwaxLookup = lookup;
    }

    private ChiselSystem() {}
}
