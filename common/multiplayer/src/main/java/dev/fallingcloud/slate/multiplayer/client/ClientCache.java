package dev.fallingcloud.slate.multiplayer.client;

import dev.fallingcloud.slate.core.config.JsonConfig;
import dev.fallingcloud.slate.multiplayer.MultiplayerConfigs;
import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;
import dev.fallingcloud.slate.multiplayer.social.ChatMessage;
import dev.fallingcloud.slate.multiplayer.social.FriendInfo;
import dev.fallingcloud.slate.multiplayer.social.GroupInfo;
import dev.fallingcloud.slate.multiplayer.social.PlayerRef;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * The last-known social state per hub, {@code config/slate/multiplayer/cache-<hub>.json}, so the title
 * screen shows friends and recent messages before (or without) a connection. Presence is stored as
 * offline; only the hub can say who is online.
 */
public final class ClientCache {

    public long savedMs;
    public String hub = "";
    public PlayerRef self = new PlayerRef(PlayerRef.NIL, "");
    public List<FriendInfo> friends = new ArrayList<>();
    public List<GroupInfo> groups = new ArrayList<>();
    public Map<String, String> titles = new LinkedHashMap<>();
    public Map<String, Integer> unread = new LinkedHashMap<>();
    public Map<String, List<ChatMessage>> messages = new LinkedHashMap<>();

    public static Path fileFor(final String key) {
        final String k = key == null || key.isEmpty() ? "local" : key;
        return MultiplayerConfigs.clientDataDir().resolve("cache-" + k + ".json");
    }

    @Nullable
    public static ClientCache load(final String key) {
        final Path f = fileFor(key);
        if (!Files.isRegularFile(f)) return null;
        try {
            final ClientCache c = JsonConfig.GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), ClientCache.class);
            if (c == null) return null;
            if (c.friends == null) c.friends = new ArrayList<>();
            if (c.groups == null) c.groups = new ArrayList<>();
            if (c.titles == null) c.titles = new LinkedHashMap<>();
            if (c.unread == null) c.unread = new LinkedHashMap<>();
            if (c.messages == null) c.messages = new LinkedHashMap<>();
            if (c.self == null) c.self = new PlayerRef(PlayerRef.NIL, "");
            return c;
        } catch (final Exception e) {
            SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] bad cache {}: {}", f, e.toString());
            return null;
        }
    }

    public void save(final String key) {
        final Path f = fileFor(key);
        try {
            Files.createDirectories(f.getParent());
            final Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, JsonConfig.GSON.toJson(this), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (final IOException e) {
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (final IOException e) {
            SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] cannot save cache {}: {}", f, e.toString());
        }
    }
}
