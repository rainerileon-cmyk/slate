package dev.fallingcloud.slate.chat.client;

import dev.fallingcloud.slate.chat.ChatConfig;
import dev.fallingcloud.slate.chat.net.ChatPayloads;
import dev.fallingcloud.slate.core.gfx.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * "X is typing" both ways. Outbound is edge-triggered and rate limited: one {@code typing=true} when the
 * input first gets text and then at most every 3 s while it keeps changing, one {@code typing=false} when
 * the input empties, sends, or the screen closes; commands never count. Inbound names expire after 5 s
 * without a refresh so a crashed client cannot type forever. Client main thread only.
 */
public final class TypingIndicator {

    private static final long REMOTE_TTL_MS = 5_000;
    private static final long RESEND_MS = 3_000;

    private static final Map<String, Long> TYPERS = new LinkedHashMap<>();
    private static boolean localTyping;
    private static long lastSentMs;
    private static String lastValue = "";

    public static void onRemote(final String name, final boolean typing) {
        if (name == null || name.isEmpty()) return;
        final Minecraft mc = Minecraft.getInstance();
        if (name.equalsIgnoreCase(mc.getUser().getName())) return;
        if (typing) TYPERS.put(name, Clock.nowMs() + REMOTE_TTL_MS);
        else TYPERS.remove(name);
    }

    /** Called every frame the chat screen is open with the input's current text. */
    public static void onLocalEdit(final String value) {
        if (!ChatConfig.get().typingIndicator) return;
        final String v = value == null ? "" : value;
        final boolean isTyping = !v.isEmpty() && !v.startsWith("/");
        final boolean changed = !v.equals(lastValue);
        lastValue = v;
        if (!isTyping) {
            if (localTyping) send(false);
            return;
        }
        if (!localTyping || (changed && Clock.nowMs() - lastSentMs > RESEND_MS)) send(true);
    }

    /** The screen closed or the line was sent. */
    public static void stopped() {
        lastValue = "";
        if (localTyping) send(false);
    }

    private static void send(final boolean typing) {
        localTyping = typing;
        lastSentMs = Clock.nowMs();
        ChatPayloads.sendTyping(typing);
    }

    public static void tick() {
        if (TYPERS.isEmpty()) return;
        final long now = Clock.nowMs();
        TYPERS.values().removeIf(t -> t < now);
    }

    public static void clear() {
        TYPERS.clear();
        localTyping = false;
        lastValue = "";
    }

    public static boolean any() {
        tick();
        return !TYPERS.isEmpty();
    }

    /** "A is typing", "A and B are typing", "Several people are typing" - without the animated dots. */
    public static Component text() {
        tick();
        final List<String> names = new ArrayList<>(TYPERS.keySet());
        return switch (names.size()) {
            case 0 -> Component.empty();
            case 1 -> Component.translatable("slate_chat.typing.one", names.get(0));
            case 2 -> Component.translatable("slate_chat.typing.two", names.get(0), names.get(1));
            default -> Component.translatable("slate_chat.typing.many");
        };
    }

    /** Animated ellipsis: 1-3 dots cycling. */
    public static String dots() {
        final int n = (int) ((Clock.nowMs() / 400) % 3) + 1;
        return ".".repeat(n);
    }

    private TypingIndicator() {}
}
