package dev.fallingcloud.slate.multiplayer.social;

import org.jetbrains.annotations.Nullable;

/** A thread's headline for the message list: its key, display title, last message (if any) and unread count. */
public record ThreadSummary(String key, String title, @Nullable ChatMessage last, int unread) {

    public ThreadSummary {
        if (key == null) key = "";
        if (title == null) title = "";
    }
}
