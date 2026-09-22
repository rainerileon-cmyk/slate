package dev.fallingcloud.slate.chat.client;

import dev.fallingcloud.slate.chat.ChatConfig;
import dev.fallingcloud.slate.core.media.Attachment;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * What Slate knows about a chat message beyond vanilla's {@link GuiMessage}: who sent it, when, whether it
 * mentions us, its attachments, and where it came from (server chat, restored history, a DM thread).
 *
 * <p>Kept in an identity map beside vanilla's record instead of a duck interface on {@code GuiMessage} so
 * other chat mods (ImmediatelyFast, chat_heads) that mixin the same records never see a changed shape. The
 * map is pruned against the live message list whenever it grows.</p>
 *
 * <p>Life cycle: {@link #prepare} runs at {@code addMessage} HEAD (before the record exists) and parks the
 * result; the first {@code addMessageToDisplayQueue} call binds it to the new record. Re-wraps of old
 * messages find their meta by identity. Client main thread only.</p>
 */
public final class ChatMeta {

    /** Tag on restored history lines; its text is the hover tooltip. */
    public static final GuiMessageTag HISTORY_TAG = new GuiMessageTag(0x6E6C66, null, Component.translatable("slate_chat.history.tag"), "SlateHistory");
    /** Tag on messages injected from a Multiplayer DM/group thread. */
    public static final GuiMessageTag THREAD_TAG = new GuiMessageTag(0x5C7CFA, null, null, "SlateThread");

    public static final class Meta {
        /** Display name of the sender; empty for system/unknown. */
        public String sender = "";
        @Nullable public UUID uuid;
        public long timeMs = System.currentTimeMillis();
        /** No known player behind it (server messages, plugin output, join/leave lines). */
        public boolean system = true;
        public boolean self;
        public boolean history;
        public boolean mention;
        /** Injected from a Multiplayer thread rather than received from the server. */
        public boolean synthetic;
        /** Thread id for synthetic messages; empty = server chat. */
        public String channel = "";
        public List<Attachment> attachments = List.of();
        /** Plain text of the original content (with the sender prefix). */
        public String plain = "";
        /** The message without its sender prefix (what grouped display shows). */
        public Component body = Component.empty();

        public boolean hasSender() { return !system && !sender.isEmpty(); }

        /** Grouping key: same sender + same channel. */
        public boolean sameGroup(final Meta other, final int windowMinutes) {
            if (other == null || system || other.system) return false;
            if (!sender.equalsIgnoreCase(other.sender) || !channel.equals(other.channel)) return false;
            return Math.abs(timeMs - other.timeMs) <= windowMinutes * 60_000L;
        }
    }

    /** A preset for the next message (history restore, thread injection) - overrides sender detection. */
    public static final class Preset {
        public String sender = "";
        @Nullable public UUID uuid;
        public long timeMs;
        public boolean history;
        public boolean synthetic;
        public String channel = "";
    }

    private static final Map<GuiMessage, Meta> METAS = new IdentityHashMap<>();
    @Nullable private static Meta next;
    @Nullable private static Preset preset;
    private static int sinceHousekeeping;

    /** Arms a preset for the very next {@code addMessage}. */
    public static void preset(final Preset p) {
        preset = p;
    }

    /** {@code addMessage} HEAD: derive everything from the component + pending sender, park it for binding. */
    public static Meta prepare(final Component content, @Nullable final GuiMessageTag tag) {
        final Meta m = new Meta();
        final Preset p = preset;
        preset = null;
        m.plain = content.getString();
        if (p != null) {
            m.sender = p.sender == null ? "" : p.sender;
            m.uuid = p.uuid;
            m.timeMs = p.timeMs > 0 ? p.timeMs : System.currentTimeMillis();
            m.history = p.history;
            m.synthetic = p.synthetic;
            m.channel = p.channel == null ? "" : p.channel;
            m.system = m.sender.isEmpty();
            SenderResolver.clear();
        } else {
            final SenderResolver.Sender s = SenderResolver.resolve(content, tag);
            m.sender = s.name();
            m.uuid = s.uuid();
            m.system = s.system();
        }
        final Minecraft mc = Minecraft.getInstance();
        final String me = mc.getUser().getName();
        m.self = !m.system && m.sender.equalsIgnoreCase(me);
        m.body = m.system ? content : MessageText.body(content, m.sender);
        m.attachments = Attachment.parse(m.plain);
        m.mention = !m.self && !m.history && !m.system && isMention(m.plain, me);
        next = m;
        return m;
    }

    /** {@code addMessageToDisplayQueue} HEAD: attach the parked meta (or compute one) to this record. */
    public static Meta bind(final GuiMessage message) {
        Meta m = METAS.get(message);
        if (m != null) { next = null; return m; }
        m = next != null ? next : prepare(message.content(), message.tag());
        next = null;
        METAS.put(message, m);
        if (++sinceHousekeeping > 64) housekeeping();
        return m;
    }

    /** The meta of a message, computing a best-effort one for records we never saw added. */
    public static Meta of(final GuiMessage message) {
        final Meta m = METAS.get(message);
        return m != null ? m : bind(message);
    }

    @Nullable
    public static Meta peek(final GuiMessage message) {
        return METAS.get(message);
    }

    /** Drops metas whose message vanilla already forgot. */
    public static void prune(final List<GuiMessage> live) {
        if (METAS.size() <= live.size()) { sinceHousekeeping = 0; return; }
        final Map<GuiMessage, Boolean> keep = new IdentityHashMap<>();
        for (final GuiMessage g : live) keep.put(g, Boolean.TRUE);
        METAS.keySet().removeIf(g -> !keep.containsKey(g));
        sinceHousekeeping = 0;
    }

    private static void housekeeping() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null && mc.gui.getChat() instanceof ChatAccess a) prune(a.slate$all());
        else sinceHousekeeping = 0;
    }

    public static void clear() {
        METAS.clear();
        next = null;
        preset = null;
    }

    // ------------------------------------------------------------------ mentions

    private static Pattern mentionPattern;
    private static String mentionFor = "";
    private static boolean mentionBare;

    static boolean isMention(final String plain, final String me) {
        final ChatConfig cfg = ChatConfig.get();
        if (!cfg.mentions || me == null || me.isEmpty()) return false;
        if (mentionPattern == null || !mentionFor.equals(me) || mentionBare != cfg.mentionOnName) {
            mentionFor = me;
            mentionBare = cfg.mentionOnName;
            final String q = Pattern.quote(me);
            mentionPattern = Pattern.compile(cfg.mentionOnName ? "(?<![\\w<])@?" + q + "(?![\\w>])" : "(?<!\\w)@" + q + "(?!\\w)", Pattern.CASE_INSENSITIVE);
        }
        // Skip the sender prefix "<name> " so a message FROM us (already excluded) or a "<name>" in a
        // join line does not count; the body is what people write.
        final String text = plain.toLowerCase(Locale.ROOT);
        return mentionPattern.matcher(text).find();
    }

    private ChatMeta() {}
}
