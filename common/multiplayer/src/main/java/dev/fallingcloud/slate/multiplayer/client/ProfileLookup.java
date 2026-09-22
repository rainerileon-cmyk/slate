package dev.fallingcloud.slate.multiplayer.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;
import dev.fallingcloud.slate.multiplayer.social.PlayerRef;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * Name to uuid, for "Add friend": first the player list of the current server (free and works offline),
 * then Mojang's public profile API on a worker thread. The hub can resolve names too (its own profile
 * cache), which is the fallback when both fail.
 */
public final class ProfileLookup {

    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();

    public static boolean validName(final String name) {
        return name != null && NAME.matcher(name.trim()).matches();
    }

    /** Resolves on a worker; the future completes on that worker (hop to the main thread yourself). */
    public static CompletableFuture<Optional<PlayerRef>> resolve(final String rawName) {
        final String name = rawName == null ? "" : rawName.trim();
        if (!validName(name)) return CompletableFuture.completedFuture(Optional.empty());
        final Optional<PlayerRef> local = fromPlayerList(name);
        if (local.isPresent()) return CompletableFuture.completedFuture(local);
        return CompletableFuture.supplyAsync(() -> fromMojang(name), Util.ioPool());
    }

    private static Optional<PlayerRef> fromPlayerList(final String name) {
        final ClientPacketListener c = Minecraft.getInstance().getConnection();
        if (c == null) return Optional.empty();
        for (final PlayerInfo p : c.getOnlinePlayers()) {
            if (p.getProfile().getName().equalsIgnoreCase(name)) return Optional.of(new PlayerRef(p.getProfile().getId(), p.getProfile().getName()));
        }
        return Optional.empty();
    }

    private static Optional<PlayerRef> fromMojang(final String name) {
        try {
            final HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + URLEncoder.encode(name, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(6)).header("Accept", "application/json").GET().build();
            final HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200 || res.body() == null || res.body().isBlank()) return Optional.empty();
            final JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
            if (!o.has("id") || !o.has("name")) return Optional.empty();
            return Optional.of(new PlayerRef(undashed(o.get("id").getAsString()), o.get("name").getAsString()));
        } catch (final Exception e) {
            SlateMultiplayer.LOGGER.debug("[Slate Multiplayer] profile lookup for {} failed: {}", name, e.toString());
            return Optional.empty();
        }
    }

    /** Parses a uuid with or without dashes. */
    public static UUID undashed(final String s) {
        final String h = s.replace("-", "");
        if (h.length() != 32) throw new IllegalArgumentException("not a uuid: " + s);
        return new UUID(Long.parseUnsignedLong(h.substring(0, 16), 16), Long.parseUnsignedLong(h.substring(16), 16));
    }

    private ProfileLookup() {}
}
