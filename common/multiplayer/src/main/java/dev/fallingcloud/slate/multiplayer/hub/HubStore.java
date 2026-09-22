package dev.fallingcloud.slate.multiplayer.hub;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;
import dev.fallingcloud.slate.multiplayer.social.ChatMessage;
import dev.fallingcloud.slate.multiplayer.social.Threads;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the hub keeps on disk, under {@code <server dir>/slate-hub/}:
 * <pre>
 *   players/&lt;uuid&gt;.json   friends, requests, blocked, groups, nicknames, notes, read markers
 *   groups/&lt;id&gt;.json      a friend group
 *   threads/&lt;key&gt;.jsonl   DM / group history, one message per line, capped
 *   media/&lt;id&gt;(.json)     stored attachments + their meta
 *   names.json             uuid -&gt; last known name (for offline references and name lookups)
 * </pre>
 * Writes are atomic (temp + move). Records are dirtied in memory and flushed on a timer and at stop.
 * Server main thread only.
 */
public final class HubStore {

    static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    static final Gson PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    /** One player's social record. Uuids are strings so the file stays plain JSON. */
    public static final class PlayerRecord {
        public String uuid = "";
        public String name = "";
        public long lastSeenMs;
        public String status = "";
        public List<String> friends = new ArrayList<>();
        public Map<String, Long> friendsSince = new LinkedHashMap<>();
        public Map<String, Long> requestsIn = new LinkedHashMap<>();
        public Map<String, Long> requestsOut = new LinkedHashMap<>();
        public List<String> blocked = new ArrayList<>();
        public List<String> groups = new ArrayList<>();
        public Map<String, InviteRecord> groupInvites = new LinkedHashMap<>();
        public Map<String, String> nicknames = new LinkedHashMap<>();
        public Map<String, String> notes = new LinkedHashMap<>();
        public Map<String, Long> readMarkers = new LinkedHashMap<>();

        public UUID id() { return UUID.fromString(uuid); }

        public boolean isFriend(final UUID u) { return friends.contains(u.toString()); }

        public boolean hasBlocked(final UUID u) { return blocked.contains(u.toString()); }
    }

    public static final class InviteRecord {
        public String from = "";
        public long atMs;
    }

    public static final class GroupRecord {
        public String id = "";
        public String name = "";
        public String owner = "";
        public List<String> members = new ArrayList<>();
        public String voiceGroup = "";
        public long createdMs;
    }

    /** Meta of a stored attachment. */
    public static final class MediaMeta {
        public String id = "";
        public String kind = "";
        public String meta = "";
        public String sender = "";
        public String senderName = "";
        public String target = "";
        public int totalBytes;
        public int durationMs;
        public long atMs;
    }

    private final Path root;
    private final Map<UUID, PlayerRecord> players = new HashMap<>();
    private final Map<String, GroupRecord> groups = new HashMap<>();
    private final Map<String, HistoryLog> histories = new HashMap<>();
    private final Map<String, String> names = new LinkedHashMap<>();
    private final Map<String, String> namesLower = new HashMap<>();
    private final Map<String, MediaMeta> media = new LinkedHashMap<>();
    private final Set<UUID> dirtyPlayers = new HashSet<>();
    private final Set<String> dirtyGroups = new HashSet<>();
    private boolean namesDirty;
    private long mediaBytes;
    private final int historyCap;

    public HubStore(final Path root, final int historyCap) {
        this.root = root;
        this.historyCap = Math.max(50, historyCap);
        try {
            Files.createDirectories(root.resolve("players"));
            Files.createDirectories(root.resolve("groups"));
            Files.createDirectories(root.resolve("threads"));
            Files.createDirectories(root.resolve("media"));
        } catch (final IOException e) {
            SlateMultiplayer.LOGGER.error("[Slate Multiplayer] cannot create hub directory {}", root, e);
        }
        loadNames();
        loadGroups();
        loadMediaIndex();
    }

    public Path root() { return root; }

    // ------------------------------------------------------------------ names

    private void loadNames() {
        final Path f = root.resolve("names.json");
        if (!Files.isRegularFile(f)) return;
        try {
            final Map<String, String> m = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), new TypeToken<Map<String, String>>() {}.getType());
            if (m != null) m.forEach(this::rememberName);
        } catch (final Exception e) {
            SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] bad names.json: {}", e.toString());
        }
    }

    private void rememberName(final String uuid, final String name) {
        if (name == null || name.isEmpty()) return;
        final String old = names.put(uuid, name);
        if (old != null) namesLower.remove(old.toLowerCase(Locale.ROOT));
        namesLower.put(name.toLowerCase(Locale.ROOT), uuid);
    }

    /** Record a uuid/name pair (login, lookup result). */
    public void setName(final UUID uuid, final String name) {
        if (name == null || name.isEmpty()) return;
        if (name.equals(names.get(uuid.toString()))) return;
        rememberName(uuid.toString(), name);
        namesDirty = true;
    }

    public String name(final UUID uuid) {
        final String n = names.get(uuid.toString());
        return n == null ? "" : n;
    }

    @Nullable
    public UUID findByName(final String name) {
        if (name == null) return null;
        final String u = namesLower.get(name.trim().toLowerCase(Locale.ROOT));
        try { return u == null ? null : UUID.fromString(u); } catch (final IllegalArgumentException e) { return null; }
    }

    // ------------------------------------------------------------------ players

    /** Loads (or creates) a player's record. */
    public PlayerRecord player(final UUID uuid) {
        PlayerRecord r = players.get(uuid);
        if (r != null) return r;
        final Path f = root.resolve("players").resolve(uuid + ".json");
        if (Files.isRegularFile(f)) {
            try {
                r = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), PlayerRecord.class);
            } catch (final Exception e) {
                SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] bad player file {}: {}", f, e.toString());
            }
        }
        if (r == null) {
            r = new PlayerRecord();
            r.uuid = uuid.toString();
            r.name = name(uuid);
        }
        if (r.uuid == null || r.uuid.isEmpty()) r.uuid = uuid.toString();
        sanitize(r);
        players.put(uuid, r);
        return r;
    }

    private static void sanitize(final PlayerRecord r) {
        if (r.friends == null) r.friends = new ArrayList<>();
        if (r.friendsSince == null) r.friendsSince = new LinkedHashMap<>();
        if (r.requestsIn == null) r.requestsIn = new LinkedHashMap<>();
        if (r.requestsOut == null) r.requestsOut = new LinkedHashMap<>();
        if (r.blocked == null) r.blocked = new ArrayList<>();
        if (r.groups == null) r.groups = new ArrayList<>();
        if (r.groupInvites == null) r.groupInvites = new LinkedHashMap<>();
        if (r.nicknames == null) r.nicknames = new LinkedHashMap<>();
        if (r.notes == null) r.notes = new LinkedHashMap<>();
        if (r.readMarkers == null) r.readMarkers = new LinkedHashMap<>();
        if (r.name == null) r.name = "";
        if (r.status == null) r.status = "";
    }

    public boolean playerExists(final UUID uuid) {
        return players.containsKey(uuid) || Files.isRegularFile(root.resolve("players").resolve(uuid + ".json"));
    }

    public void dirty(final PlayerRecord r) { dirtyPlayers.add(r.id()); }

    // ------------------------------------------------------------------ groups

    private void loadGroups() {
        final Path dir = root.resolve("groups");
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                try {
                    final GroupRecord g = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), GroupRecord.class);
                    if (g != null && g.id != null && !g.id.isEmpty()) {
                        if (g.members == null) g.members = new ArrayList<>();
                        if (g.name == null) g.name = "";
                        if (g.owner == null) g.owner = "";
                        if (g.voiceGroup == null) g.voiceGroup = "";
                        groups.put(g.id, g);
                    }
                } catch (final Exception e) {
                    SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] bad group file {}: {}", p, e.toString());
                }
            });
        } catch (final IOException ignored) {}
    }

    @Nullable public GroupRecord group(final String id) { return groups.get(id); }

    public GroupRecord createGroup(final String id, final String name, final UUID owner, final long now) {
        final GroupRecord g = new GroupRecord();
        g.id = id;
        g.name = name;
        g.owner = owner.toString();
        g.members.add(owner.toString());
        g.createdMs = now;
        groups.put(id, g);
        dirtyGroups.add(id);
        return g;
    }

    public void dirty(final GroupRecord g) { dirtyGroups.add(g.id); }

    public void deleteGroup(final String id) {
        groups.remove(id);
        dirtyGroups.remove(id);
        try {
            Files.deleteIfExists(root.resolve("groups").resolve(id + ".json"));
        } catch (final IOException ignored) {}
        final HistoryLog h = histories.remove(Threads.group(id));
        if (h != null) h.delete();
        try {
            Files.deleteIfExists(root.resolve("threads").resolve(Threads.fileName(Threads.group(id)) + ".jsonl"));
        } catch (final IOException ignored) {}
    }

    // ------------------------------------------------------------------ history

    public HistoryLog history(final String threadKey) {
        return histories.computeIfAbsent(threadKey, k -> new HistoryLog(root.resolve("threads").resolve(Threads.fileName(k) + ".jsonl"), historyCap));
    }

    /** Whether a thread has any history without loading it (cheap file check when not cached). */
    public boolean hasHistory(final String threadKey) {
        final HistoryLog h = histories.get(threadKey);
        if (h != null) return h.size() > 0;
        return Files.isRegularFile(root.resolve("threads").resolve(Threads.fileName(threadKey) + ".jsonl"));
    }

    // ------------------------------------------------------------------ media

    private void loadMediaIndex() {
        final Path dir = root.resolve("media");
        if (!Files.isDirectory(dir)) return;
        final List<MediaMeta> all = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                try {
                    final MediaMeta m = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), MediaMeta.class);
                    if (m != null && m.id != null && Files.isRegularFile(dir.resolve(m.id))) all.add(m);
                } catch (final Exception ignored) {}
            });
        } catch (final IOException ignored) {}
        all.sort((a, b) -> Long.compare(a.atMs, b.atMs));
        for (final MediaMeta m : all) { media.put(m.id, m); mediaBytes += m.totalBytes; }
    }

    @Nullable public MediaMeta mediaMeta(final String id) { return media.get(id); }

    @Nullable
    public byte[] mediaBytes(final String id) {
        if (!media.containsKey(id)) return null;
        try {
            return Files.readAllBytes(root.resolve("media").resolve(id));
        } catch (final IOException e) {
            return null;
        }
    }

    /** Stores an attachment, evicting the oldest ones past {@code capBytes}. */
    public void storeMedia(final MediaMeta meta, final byte[] bytes, final long capBytes) {
        final Path dir = root.resolve("media");
        try {
            atomicWrite(dir.resolve(meta.id), bytes);
            atomicWrite(dir.resolve(meta.id + ".json"), GSON.toJson(meta).getBytes(StandardCharsets.UTF_8));
        } catch (final IOException e) {
            SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] cannot store media {}: {}", meta.id, e.toString());
            return;
        }
        media.put(meta.id, meta);
        mediaBytes += bytes.length;
        while (mediaBytes > capBytes && media.size() > 1) {
            final String oldest = media.keySet().iterator().next();
            final MediaMeta m = media.remove(oldest);
            mediaBytes -= m.totalBytes;
            try {
                Files.deleteIfExists(dir.resolve(oldest));
                Files.deleteIfExists(dir.resolve(oldest + ".json"));
            } catch (final IOException ignored) {}
        }
    }

    // ------------------------------------------------------------------ flushing

    public void flush() {
        for (final UUID u : new ArrayList<>(dirtyPlayers)) {
            final PlayerRecord r = players.get(u);
            if (r == null) continue;
            try {
                atomicWrite(root.resolve("players").resolve(u + ".json"), PRETTY.toJson(r).getBytes(StandardCharsets.UTF_8));
            } catch (final IOException e) {
                SlateMultiplayer.LOGGER.error("[Slate Multiplayer] cannot save player {}", u, e);
            }
        }
        dirtyPlayers.clear();
        for (final String id : new ArrayList<>(dirtyGroups)) {
            final GroupRecord g = groups.get(id);
            if (g == null) continue;
            try {
                atomicWrite(root.resolve("groups").resolve(id + ".json"), PRETTY.toJson(g).getBytes(StandardCharsets.UTF_8));
            } catch (final IOException e) {
                SlateMultiplayer.LOGGER.error("[Slate Multiplayer] cannot save group {}", id, e);
            }
        }
        dirtyGroups.clear();
        if (namesDirty) {
            namesDirty = false;
            try {
                atomicWrite(root.resolve("names.json"), GSON.toJson(names).getBytes(StandardCharsets.UTF_8));
            } catch (final IOException e) {
                SlateMultiplayer.LOGGER.error("[Slate Multiplayer] cannot save names", e);
            }
        }
        for (final HistoryLog h : histories.values()) h.flush();
    }

    /** Drop history logs nobody touched for a while (memory), keeping files. */
    public void evictIdle(final long now, final long idleMs) {
        histories.values().removeIf(h -> now - h.lastTouchedMs() > idleMs && h.flush());
    }

    public boolean isDirty() { return !dirtyPlayers.isEmpty() || !dirtyGroups.isEmpty() || namesDirty; }

    static void atomicWrite(final Path file, final byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        final Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (final IOException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ------------------------------------------------------------------ history log

    /**
     * One thread's messages: a JSON-lines file with an in-memory copy (bounded by the cap). Appends go to
     * the file immediately; when the file grows past 1.5x the cap it is rewritten with the newest cap lines.
     */
    public static final class HistoryLog {
        private final Path file;
        private final int cap;
        private final List<ChatMessage> messages = new ArrayList<>();
        private boolean loaded;
        private int fileLines;
        private long lastTouched = System.currentTimeMillis();
        private boolean rewritePending;

        HistoryLog(final Path file, final int cap) {
            this.file = file;
            this.cap = cap;
        }

        long lastTouchedMs() { return lastTouched; }

        private void load() {
            if (loaded) return;
            loaded = true;
            if (!Files.isRegularFile(file)) return;
            try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
                lines.forEach(line -> {
                    if (line.isBlank()) return;
                    fileLines++;
                    try {
                        final ChatMessage m = GSON.fromJson(line, ChatMessage.class);
                        if (m != null) messages.add(m);
                    } catch (final Exception ignored) {}
                });
            } catch (final IOException e) {
                SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] cannot read history {}: {}", file, e.toString());
            }
            if (messages.size() > cap) messages.subList(0, messages.size() - cap).clear();
        }

        public int size() { load(); return messages.size(); }

        @Nullable
        public ChatMessage last() {
            load();
            return messages.isEmpty() ? null : messages.get(messages.size() - 1);
        }

        public void append(final ChatMessage m) {
            load();
            lastTouched = System.currentTimeMillis();
            messages.add(m);
            if (messages.size() > cap) messages.remove(0);
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(m) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                fileLines++;
            } catch (final IOException e) {
                SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] cannot append history {}: {}", file, e.toString());
            }
            if (fileLines > cap * 3 / 2) rewritePending = true;
        }

        /** Messages older than {@code beforeMs} (0 = newest), newest {@code limit}, returned oldest first. */
        public List<ChatMessage> page(final long beforeMs, final int limit, final boolean[] more) {
            load();
            lastTouched = System.currentTimeMillis();
            int end = messages.size();
            if (beforeMs > 0) {
                while (end > 0 && messages.get(end - 1).atMs() >= beforeMs) end--;
            }
            final int start = Math.max(0, end - limit);
            more[0] = start > 0;
            return new ArrayList<>(messages.subList(start, end));
        }

        /** Messages after {@code marker} not sent by {@code self}. */
        public int unreadSince(final long marker, final UUID self) {
            load();
            int n = 0;
            for (int i = messages.size() - 1; i >= 0; i--) {
                final ChatMessage m = messages.get(i);
                if (m.atMs() <= marker) break;
                if (!m.from().uuid().equals(self)) n++;
            }
            return n;
        }

        /** Rewrites the file when it grew past the cap. Returns true when nothing is pending afterwards. */
        boolean flush() {
            if (!rewritePending) return true;
            rewritePending = false;
            final StringBuilder sb = new StringBuilder();
            for (final ChatMessage m : messages) sb.append(GSON.toJson(m)).append('\n');
            try {
                atomicWrite(file, sb.toString().getBytes(StandardCharsets.UTF_8));
                fileLines = messages.size();
            } catch (final IOException e) {
                SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] cannot compact history {}: {}", file, e.toString());
            }
            return true;
        }

        void delete() {
            messages.clear();
            try { Files.deleteIfExists(file); } catch (final IOException ignored) {}
        }
    }
}
