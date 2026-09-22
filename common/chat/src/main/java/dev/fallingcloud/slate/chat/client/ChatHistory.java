package dev.fallingcloud.slate.chat.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.fallingcloud.slate.chat.ChatConfig;
import dev.fallingcloud.slate.chat.SlateChat;
import dev.fallingcloud.slate.core.config.JsonConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import org.jetbrains.annotations.Nullable;

/**
 * Per-server chat history on disk: {@code config/slate/chat/history-<server>.jsonl}, one JSON object per
 * line ({@code t} epoch ms, {@code n} sender, {@code u} uuid, {@code c} the component). Appends happen on a
 * single worker so the render thread never touches the file; the last {@code historySize} lines are
 * restored (dimmed, tagged) right after joining, before the server's own greeting arrives.
 *
 * <p>Components are encoded with {@link ComponentSerialization#CODEC} on plain JSON ops; the rare hover
 * event that needs registries falls back to the plain text, which still reads fine.</p>
 */
public final class ChatHistory {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, "slate-chat-history");
        t.setDaemon(true);
        return t;
    });
    private static volatile String currentKey = "";

    public static Path dir() {
        return JsonConfig.dir().resolve("chat");
    }

    /** A file-safe key for the current server or singleplayer world; "" when not connected. */
    public static String serverKey() {
        final Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                return "sp-" + sanitize(mc.getSingleplayerServer().getWorldData().getLevelName());
            }
            final ServerData sd = mc.getCurrentServer();
            if (sd != null && sd.ip != null && !sd.ip.isBlank()) return sanitize(sd.ip);
        } catch (final Exception ignored) {}
        return "";
    }

    private static String sanitize(final String s) {
        final String out = s == null ? "" : s.replaceAll("[^A-Za-z0-9._-]", "_");
        return out.length() > 60 ? out.substring(0, 60) : out;
    }

    public static Path fileFor(final String key) {
        return dir().resolve("history-" + key + ".jsonl");
    }

    // ------------------------------------------------------------------ append

    /** Records a real (not restored, not synthetic) message. Cheap: serialises on the caller, writes async. */
    public static void append(final ChatMeta.Meta meta, final Component content) {
        final ChatConfig cfg = ChatConfig.get();
        if (cfg.historySize <= 0 || meta.history || meta.synthetic) return;
        final String key = currentKey.isEmpty() ? serverKey() : currentKey;
        if (key.isEmpty()) return;
        currentKey = key;
        final JsonObject o = new JsonObject();
        o.addProperty("t", meta.timeMs);
        o.addProperty("n", meta.sender);
        if (meta.uuid != null) o.addProperty("u", meta.uuid.toString());
        o.add("c", encode(content));
        final String line = JsonConfig.GSON.toJson(o).replace('\n', ' ');
        IO.submit(() -> {
            try {
                Files.createDirectories(dir());
                Files.writeString(fileFor(key), line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (final IOException e) {
                SlateChat.LOGGER.debug("[Slate Chat] history append failed: {}", e.toString());
            }
        });
    }

    private static JsonElement encode(final Component c) {
        try {
            return ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, c).result().orElseGet(() -> plain(c));
        } catch (final Exception e) {
            return plain(c);
        }
    }

    private static JsonElement plain(final Component c) {
        final JsonObject o = new JsonObject();
        o.addProperty("text", c.getString());
        return o;
    }

    @Nullable
    private static Component decode(final JsonElement e) {
        try {
            return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, e).result().orElse(null);
        } catch (final Exception ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------ restore

    /** One stored line. */
    public record Stored(long timeMs, String sender, @Nullable UUID uuid, Component content) {}

    /** Reads the last {@code max} lines of a server's history (blocking; small file). */
    public static List<Stored> read(final String key, final int max) {
        final List<Stored> out = new ArrayList<>();
        final Path f = fileFor(key);
        if (max <= 0 || !Files.isRegularFile(f)) return out;
        try {
            final List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
            final int from = Math.max(0, lines.size() - max);
            for (int i = from; i < lines.size(); i++) {
                final String l = lines.get(i).trim();
                if (l.isEmpty()) continue;
                try {
                    final JsonObject o = JsonParser.parseString(l).getAsJsonObject();
                    final Component c = o.has("c") ? decode(o.get("c")) : null;
                    if (c == null) continue;
                    UUID u = null;
                    if (o.has("u")) { try { u = UUID.fromString(o.get("u").getAsString()); } catch (final Exception ignored) {} }
                    out.add(new Stored(o.has("t") ? o.get("t").getAsLong() : 0, o.has("n") ? o.get("n").getAsString() : "", u, c));
                } catch (final Exception ignored) {}
            }
            // Keep the file bounded: rewrite with the tail when it grew past twice the budget.
            if (lines.size() > max * 2) {
                final List<String> tail = lines.subList(from, lines.size());
                final Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
                Files.write(tmp, tail, StandardCharsets.UTF_8);
                Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (final IOException e) {
            SlateChat.LOGGER.debug("[Slate Chat] history read failed: {}", e.toString());
        }
        return out;
    }

    /** Joined a world/server: replay the stored tail into the chat as dimmed, tagged lines. */
    public static void restore() {
        final ChatConfig cfg = ChatConfig.get();
        currentKey = serverKey();
        if (!cfg.restoreHistory || cfg.historySize <= 0 || currentKey.isEmpty()) return;
        final String key = currentKey;
        IO.submit(() -> {
            final List<Stored> stored = read(key, cfg.historySize);
            if (stored.isEmpty()) return;
            Minecraft.getInstance().execute(() -> {
                if (!key.equals(currentKey)) return;               // switched servers meanwhile
                final Minecraft mc = Minecraft.getInstance();
                if (mc.gui == null) return;
                for (final Stored s : stored) {
                    final ChatMeta.Preset p = new ChatMeta.Preset();
                    p.sender = s.sender();
                    p.uuid = s.uuid();
                    p.timeMs = s.timeMs();
                    p.history = true;
                    ChatMeta.preset(p);
                    mc.gui.getChat().addMessage(s.content(), null, ChatMeta.HISTORY_TAG);
                }
                ChatRenderState.markRead();
            });
        });
    }

    public static void onLeave() {
        currentKey = "";
    }

    /** Deletes the current server's history file (settings action). */
    public static void clearCurrent() {
        final String key = currentKey.isEmpty() ? serverKey() : currentKey;
        if (key.isEmpty()) return;
        IO.submit(() -> {
            try { Files.deleteIfExists(fileFor(key)); } catch (final IOException ignored) {}
        });
    }

    private ChatHistory() {}
}
