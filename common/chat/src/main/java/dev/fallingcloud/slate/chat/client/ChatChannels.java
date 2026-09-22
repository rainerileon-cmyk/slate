package dev.fallingcloud.slate.chat.client;

import dev.fallingcloud.slate.chat.ChatConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The channel tabs above the input: Global (everything the server sent), System (non-player lines only)
 * and - when Slate Multiplayer's client is present - one tab per open DM/group thread. A tab is a filter
 * over vanilla's message list (Global/System) or a synthetic list injected from the thread (DM/group);
 * the search field (Ctrl+F) is one more filter on top. Switching re-wraps the chat through vanilla's own
 * refresh so scrolling, fading and click handling stay vanilla's.
 */
public final class ChatChannels {

    public enum Kind { GLOBAL, SYSTEM, THREAD }

    public record Channel(Kind kind, String id, Component label, int unread, @Nullable MultiplayerBridge.Thread thread) {
        public boolean isThread() { return kind == Kind.THREAD; }
    }

    private static final Channel GLOBAL = new Channel(Kind.GLOBAL, "", Component.translatable("slate_chat.tab.global"), 0, null);
    private static final Channel SYSTEM = new Channel(Kind.SYSTEM, "system", Component.translatable("slate_chat.tab.system"), 0, null);

    private static Channel current = GLOBAL;
    private static String search = "";
    private static List<Channel> cached = List.of(GLOBAL);
    private static long cachedAt;

    public static Channel current() { return current; }

    public static boolean isThread() { return current.isThread(); }

    public static String search() { return search; }

    /** The tabs in display order (thread list refreshed at most every 500 ms). */
    public static List<Channel> channels() {
        final long now = System.currentTimeMillis();
        if (now - cachedAt < 500) return cached;
        cachedAt = now;
        final List<Channel> out = new ArrayList<>();
        out.add(GLOBAL);
        if (ChatConfig.get().systemTab) out.add(SYSTEM);
        for (final MultiplayerBridge.Thread t : MultiplayerBridge.threads()) {
            out.add(new Channel(Kind.THREAD, t.id(), t.label(), t.unread(), t));
        }
        cached = out;
        // Keep the selected thread valid.
        if (current.isThread() && out.stream().noneMatch(c -> c.id().equals(current.id()))) select(GLOBAL);
        return out;
    }

    public static void select(final Channel c) {
        if (c == null) return;
        final boolean same = c.kind() == current.kind() && c.id().equals(current.id());
        current = c;
        if (!same) refresh();
    }

    public static void setSearch(final String query) {
        final String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.equals(search)) return;
        search = q;
        refresh();
    }

    /** Whether a message belongs in the current tab (and matches the search). */
    public static boolean accepts(final ChatMeta.Meta m) {
        final boolean tab = switch (current.kind()) {
            case GLOBAL -> !m.synthetic;
            case SYSTEM -> !m.synthetic && m.system;
            case THREAD -> m.synthetic && m.channel.equals(current.id());
        };
        if (!tab) return false;
        return search.isEmpty() || m.plain.toLowerCase(Locale.ROOT).contains(search) || m.sender.toLowerCase(Locale.ROOT).contains(search);
    }

    @Nullable
    private static ChatAccess access() {
        final Minecraft mc = Minecraft.getInstance();
        return mc.gui != null && mc.gui.getChat() instanceof ChatAccess a ? a : null;
    }

    /** Re-wrap through vanilla, then inject the thread's lines when a thread tab is selected. */
    public static void refresh() {
        final ChatAccess a = access();
        if (a == null) return;
        a.slate$refresh();
        if (current.isThread() && current.thread() != null) {
            final Minecraft mc = Minecraft.getInstance();
            final int tick = mc.gui.getGuiTicks();
            for (final MultiplayerBridge.ThreadMessage m : MultiplayerBridge.messages(current.thread())) {
                final ChatMeta.Preset p = new ChatMeta.Preset();
                p.sender = m.sender();
                p.uuid = m.senderId();
                p.timeMs = m.timeMs();
                p.synthetic = true;
                p.channel = current.id();
                ChatMeta.preset(p);
                final GuiMessage gm = new GuiMessage(tick, Component.literal(m.text()), null, ChatMeta.THREAD_TAG);
                ChatMeta.prepare(gm.content(), gm.tag());
                a.slate$addToDisplay(gm);
            }
        }
    }

    /** Left the server: back to Global, no search. */
    public static void reset() {
        current = GLOBAL;
        search = "";
        cached = List.of(GLOBAL);
        cachedAt = 0;
    }

    private ChatChannels() {}
}
