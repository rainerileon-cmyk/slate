package dev.fallingcloud.slate.building.config;

import com.google.gson.JsonParseException;
import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.net.ServerSettingsSync;
import dev.fallingcloud.slate.core.config.JsonConfig;
import dev.fallingcloud.slate.core.net.SlateNetwork;
import java.nio.charset.StandardCharsets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The server rules that apply to a given side: on the server (and in common code running server-side) the local
 * {@code building-server.json}; on a client, the copy the server sent in {@link ServerSettingsSync} (or the local
 * file in singleplayer / before the sync arrived). Use {@link #effective(Level)} from common code that runs on both
 * sides (planners, capability checks, previews) so client previews obey the same rules as the server.
 *
 * <p>Sync: {@link #sendTo} runs for every joining player ({@code SlateBuilding.init} registers it on
 * {@code PLAYER_JOINED}); call {@link #broadcast} after reloading the file ({@code /slatebuild reload}). The config
 * travels as its Gson JSON in a byte array ({@link #toTag}), so the client reads exactly what the server wrote,
 * booleans and field defaults included, with no 64 KiB NBT string limit.
 *
 * @param config the rules
 * @param remote true when these are a server's synced copy on a client
 */
public record BuildingServerSettings(BuildingServerConfig config, boolean remote) {

    private static final String TAG_JSON = "json";

    private static volatile @Nullable BuildingServerSettings local;

    public ServerVariants variants() { return config.variants; }

    public ServerOps ops() { return config.ops; }

    public ServerToolbox toolbox() { return config.toolbox; }

    public ServerChisel chisel() { return config.chisel; }

    /** The rules of the server running in this process (also correct for a client's integrated server). */
    public static BuildingServerSettings local() {
        final BuildingServerConfig cfg = SlateBuilding.serverConfig();
        BuildingServerSettings s = local;
        if (s == null || s.config != cfg) local = s = new BuildingServerSettings(cfg, false); // re-wrap after a reload
        return s;
    }

    /** The rules for code running on {@code level}'s side: the synced copy on a client level, else {@link #local()}. */
    public static BuildingServerSettings effective(final @Nullable Level level) {
        return level != null && level.isClientSide() ? ClientSide.get() : local();
    }

    public static BuildingServerSettings effective(final Player player) {
        return effective(player.level());
    }

    /** Serialises {@code config} for {@link ServerSettingsSync}. */
    public static CompoundTag toTag(final BuildingServerConfig config) {
        final CompoundTag tag = new CompoundTag();
        tag.putByteArray(TAG_JSON, JsonConfig.GSON.toJson(config).getBytes(StandardCharsets.UTF_8));
        return tag;
    }

    /** Reads a {@link #toTag} tag; defaults when it is missing or malformed (never throws). */
    public static BuildingServerConfig fromTag(final CompoundTag tag) {
        if (tag.contains(TAG_JSON, Tag.TAG_BYTE_ARRAY)) {
            try {
                final BuildingServerConfig cfg = JsonConfig.GSON.fromJson(new String(tag.getByteArray(TAG_JSON), StandardCharsets.UTF_8), BuildingServerConfig.class);
                if (cfg != null) return cfg;
            } catch (final JsonParseException e) {
                SlateBuilding.LOGGER.warn("[Slate Building] unreadable server settings from the server, using defaults: {}", e.toString());
            }
        }
        return new BuildingServerConfig();
    }

    /** Sends the local rules to {@code player} (no-op when their client lacks Slate Building). */
    public static void sendTo(final ServerPlayer player) {
        SlateNetwork.get().sendToPlayer(player, new ServerSettingsSync(toTag(SlateBuilding.serverConfig())));
    }

    /** Sends the local rules to everyone online, e.g. after the file was reloaded. */
    public static void broadcast(final MinecraftServer server) {
        final ServerSettingsSync payload = new ServerSettingsSync(toTag(SlateBuilding.serverConfig()));
        for (final ServerPlayer p : server.getPlayerList().getPlayers()) SlateNetwork.get().sendToPlayer(p, payload);
    }

    /** Client-only references live here so a dedicated server never resolves them. */
    private static final class ClientSide {
        static BuildingServerSettings get() { return dev.fallingcloud.slate.building.client.ServerSettingsClient.get(); }
    }
}
