package dev.fallingcloud.slate.multiplayer.client;

import dev.fallingcloud.slate.multiplayer.social.ChatMessage;
import dev.fallingcloud.slate.multiplayer.social.Threads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** One DM or group thread as the client sees it: messages (oldest first), unread count, who is typing. */
public final class ThreadModel {

    public final String key;
    public String title = "";
    public final List<ChatMessage> messages = new ArrayList<>();
    public int unread;
    /** Older messages may exist on the hub. */
    public boolean more = true;
    public boolean historyPending;
    public long lastActivityMs;
    public final Map<UUID, Long> typingUntil = new HashMap<>();

    public ThreadModel(final String key) { this.key = key; }

    public boolean isGroup() { return Threads.isGroup(key); }

    @Nullable public String groupId() { return Threads.groupId(key); }

    @Nullable public UUID other(final UUID self) { return Threads.dmOther(key, self); }

    @Nullable public ChatMessage last() { return messages.isEmpty() ? null : messages.get(messages.size() - 1); }

    /** Adds a message in timestamp order, ignoring duplicates by id. Returns true when added. */
    public boolean add(final ChatMessage m) {
        if (!m.id().isEmpty()) for (final ChatMessage e : messages) if (e.id().equals(m.id())) return false;
        int i = messages.size();
        while (i > 0 && messages.get(i - 1).atMs() > m.atMs()) i--;
        messages.add(i, m);
        lastActivityMs = Math.max(lastActivityMs, m.atMs());
        return true;
    }

    /** Merges a page of older history. */
    public void addOlder(final List<ChatMessage> page) {
        for (final ChatMessage m : page) add(m);
    }

    public long oldestMs() { return messages.isEmpty() ? 0L : messages.get(0).atMs(); }

    public void trim(final int keep) {
        if (messages.size() > keep) { messages.subList(0, messages.size() - keep).clear(); more = true; }
    }

    /** Players typing right now (expired entries dropped). */
    public List<UUID> typingNow(final long now) {
        typingUntil.values().removeIf(t -> t < now);
        return new ArrayList<>(typingUntil.keySet());
    }
}
