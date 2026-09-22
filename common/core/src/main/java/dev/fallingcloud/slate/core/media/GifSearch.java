package dev.fallingcloud.slate.core.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.fallingcloud.slate.core.Slate;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/**
 * Online GIF search via Tenor's v2 API (the backend Discord uses). Tenor v2 requires an API key (the old
 * open v1 endpoint is gone); keys are free from Google's Tenor console and live in {@link MediaConfig}.
 * Results come back as plain GIF URLs, which flow through the same embed pipeline as any GIF link pasted
 * into chat.
 */
public final class GifSearch {

    /** One search outcome; exactly one of the states is meaningful. */
    public record Result(State state, List<String> urls) {
        public enum State { NO_KEY, ERROR, OK }
    }

    public static boolean hasKey() {
        final String k = MediaConfig.get().tenorApiKey;
        return k != null && !k.isBlank();
    }

    /** Runs off-thread; the callback lands on the render thread. */
    public static void search(final String query, final Consumer<Result> callback) {
        run("https://tenor.googleapis.com/v2/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8), callback);
    }

    /** Tenor's featured/trending set - what the picker shows before a query is typed. */
    public static void trending(final Consumer<Result> callback) {
        run("https://tenor.googleapis.com/v2/featured?", callback);
    }

    private static void run(final String base, final Consumer<Result> callback) {
        final String key = MediaConfig.get().tenorApiKey;
        if (key == null || key.isBlank()) {
            callback.accept(new Result(Result.State.NO_KEY, List.of()));
            return;
        }
        final Thread worker = new Thread(() -> {
            Result result;
            try {
                final String url = base + "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8)
                    + "&limit=30&media_filter=gif,tinygif&contentfilter=medium";
                final HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", MediaConfig.get().userAgent);
                try (InputStream in = conn.getInputStream()) {
                    final JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    final JsonArray results = root.getAsJsonArray("results");
                    final List<String> urls = new ArrayList<>(results == null ? 0 : results.size());
                    if (results != null) {
                        for (int i = 0; i < results.size(); i++) {
                            final JsonObject formats = results.get(i).getAsJsonObject().getAsJsonObject("media_formats");
                            if (formats == null) continue;
                            // Full gif preferred; tinygif is the fallback some entries only carry.
                            final JsonObject gif = formats.has("gif") ? formats.getAsJsonObject("gif")
                                : formats.has("tinygif") ? formats.getAsJsonObject("tinygif") : null;
                            if (gif != null && gif.has("url")) urls.add(gif.get("url").getAsString());
                        }
                    }
                    result = new Result(Result.State.OK, urls);
                }
            } catch (final Exception e) {
                Slate.LOGGER.warn("[Slate] gif search failed: {}", e.toString());
                result = new Result(Result.State.ERROR, List.of());
            }
            final Result r = result;
            Minecraft.getInstance().execute(() -> callback.accept(r));
        }, "slate-gifsearch");
        worker.setDaemon(true);
        worker.start();
    }

    private GifSearch() {}
}
