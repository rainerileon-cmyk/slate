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

    // ---- accessors the Chat module reaches reflectively (see slate_chat's MultiplayerBridge):
    // id() / name() / unread() / messages() / send(String) / blobTarget().

    public String id() { return key; }

    public String name() {
        return title != null && !title.isEmpty() ? title : SocialClient.get().threadTitle(this);
    }

    public int unread() { return unread; }

    public List<ChatMessage> messages() { return messages; }

    public void send(final String text) { SocialClient.get().sendChat(key, text); }

    public String blobTarget() { return Threads.blobTarget(key, SocialClient.get().selfUuid()); }

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
