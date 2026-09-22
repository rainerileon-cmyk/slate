package dev.fallingcloud.slate.chat.client;

import dev.fallingcloud.slate.chat.ChatConfig;
import dev.fallingcloud.slate.core.media.Attachment;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Theme;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * Row geometry and grouping: how tall each row type is, how far text is indented under a head, whether a
 * message starts a new group, and the row list the wrap step hands vanilla (header/gap markers first,
 * text rows, attachment rows last - vanilla inserts each at index 0, so the LAST element becomes the
 * bottom-most drawn row and the first the top-most).
 *
 * <p>All sizes are in chat units (the scaled space vanilla lays lines out in).</p>
 */
public final class ChatLayout {

    /** The sizes for the current config + line height. */
    public record Geometry(int lineH, int headerH, int gapH, int indent, int avatar, boolean compact, boolean heads) {
        public static Geometry of(final int lineH) {
            final ChatConfig cfg = ChatConfig.get();
            final boolean compact = cfg.isCompact();
            final boolean heads = cfg.heads;
            if (compact) return new Geometry(lineH, 0, 0, heads ? 11 : 0, 8, true, heads);
            return new Geometry(lineH, lineH + 4, 3, heads ? 14 : 0, 10, false, heads);
        }

        /** Text indent for a message: only player messages hang under a head/name. */
        public int indentFor(@Nullable final ChatMeta.Meta m) {
            return m != null && m.hasSender() && (heads || !compact) ? indent : 0;
        }

        public int rowHeight(final GuiMessage.Line line) {
            final FormattedCharSequence c = line.content();
            if (c instanceof ChatRows.HeaderRow) return headerH;
            if (c instanceof ChatRows.GapRow) return gapH;
            return lineH;
        }
    }

    // ------------------------------------------------------------------ grouping state (wrap order)

    @Nullable private static ChatMeta.Meta previous;

    /** {@code refreshTrimmedMessages} / {@code clearMessages}: the next message starts fresh. */
    public static void resetGrouping() {
        previous = null;
    }

    /** Decides (and records) whether this message begins a new group. Call once per wrapped message. */
    public static boolean groupStart(final ChatMeta.Meta meta) {
        final ChatConfig cfg = ChatConfig.get();
        final boolean start = !cfg.grouping || !meta.sameGroup(previous, Math.max(1, cfg.groupWindowMinutes));
        previous = meta;
        return start;
    }

    // ------------------------------------------------------------------ wrap-step helpers

    /** The text vanilla should wrap for this message (sender prefix dropped, links shortened, emotes). */
    public static FormattedText displayText(final GuiMessage message, final ChatMeta.Meta meta, final boolean groupStart, final Geometry geo) {
        if (meta.system) return MessageText.display(message.content(), meta.attachments);
        final Component body = MessageText.display(meta.body, meta.attachments);
        if (geo.compact() && groupStart) {
            final MutableComponent out = Component.empty();
            out.append(Component.literal(meta.sender).withStyle(Style.EMPTY.withColor(nameColor(meta) & 0xFFFFFF)));
            out.append(Component.literal(": ").withStyle(Style.EMPTY.withColor(Theme.current().palette().textDim() & 0xFFFFFF)));
            out.append(body);
            return out;
        }
        return body;
    }

    /** Builds the full row list for a message from its wrapped text lines. */
    public static List<FormattedCharSequence> rows(final GuiMessage message, final ChatMeta.Meta meta, final List<FormattedCharSequence> wrapped,
                                                   final boolean groupStart, final Geometry geo) {
        final List<FormattedCharSequence> out = new ArrayList<>(wrapped.size() + 4);
        final boolean cozyHeader = !geo.compact() && meta.hasSender() && groupStart;
        if (cozyHeader) {
            if (geo.gapH() > 0) out.add(new ChatRows.GapRow(message));
            out.add(new ChatRows.HeaderRow(message, meta));
        }
        for (int i = 0; i < wrapped.size(); i++) {
            out.add(new ChatRows.TextRow(wrapped.get(i), message, i, i == 0, i == wrapped.size() - 1, groupStart));
        }
        for (final Attachment att : meta.attachments) {
            final int rows = att.rows();
            for (int r = 0; r < rows; r++) out.add(new ChatRows.AttachmentRow(message, att, r, rows));
        }
        return out;
    }

    // ------------------------------------------------------------------ scrolling

    /** Height of the visible page in chat units (vanilla: lines per page * line height). */
    public static int pageHeight(final ChatAccess a) {
        final int lineH = Math.max(1, a.slate$lineHeight());
        return Math.max(lineH, (a.slate$height() / lineH) * lineH);
    }

    /** The largest scroll offset that still fills the page (0 when everything fits). */
    public static int maxScroll(final ChatAccess a) {
        final List<GuiMessage.Line> lines = a.slate$lines();
        if (lines.isEmpty()) return 0;
        final Geometry geo = Geometry.of(a.slate$lineHeight());
        final int page = pageHeight(a);
        int sum = 0;
        for (int i = lines.size() - 1; i >= 0; i--) {
            sum += geo.rowHeight(lines.get(i));
            if (sum >= page) return i;
        }
        return 0;
    }

    /** Vanilla's scrollChat clamp, on real row heights. */
    public static void scroll(final ChatAccess a, final int amount) {
        int pos = a.slate$scroll() + amount;
        pos = Math.min(pos, maxScroll(a));
        if (pos <= 0) {
            pos = 0;
            a.slate$setNewSinceScroll(false);
        }
        a.slate$setScroll(pos);
    }

    // ------------------------------------------------------------------ colours & time

    /** A stable, readable hue per sender (accent for yourself). */
    public static int nameColor(final ChatMeta.Meta meta) {
        if (meta.self) return Theme.current().accent();
        final int h = meta.sender.toLowerCase(java.util.Locale.ROOT).hashCode();
        final float hue = ((h % 360) + 360) % 360 / 360f;
        return Colors.hsvToRgb(hue, Theme.current().isVanilla() ? 0.35f : 0.42f, 0.96f);
    }

    private static DateTimeFormatter formatter;
    private static String formatterPattern = "";

    public static String time(final long ms) {
        final String pattern = ChatConfig.get().timeFormat == null || ChatConfig.get().timeFormat.isBlank() ? "HH:mm" : ChatConfig.get().timeFormat;
        if (formatter == null || !formatterPattern.equals(pattern)) {
            formatterPattern = pattern;
            try {
                formatter = DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault());
            } catch (final Exception e) {
                formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
            }
        }
        try {
            return formatter.format(Instant.ofEpochMilli(ms));
        } catch (final Exception e) {
            return "";
        }
    }

    private ChatLayout() {}
}
