package dev.fallingcloud.slate.chat.client;

import dev.fallingcloud.slate.core.media.Attachment;
import net.minecraft.client.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
import org.jetbrains.annotations.Nullable;

/**
 * The row types Slate puts into vanilla's line list. Text lines are wrapped ({@link TextRow}: draws exactly
 * like the wrapped line it holds) and glyph-less markers reserve space for things vanilla has no row type
 * for: a group header (head + name + time), a gap between groups, and attachment cards (N whole lines under
 * the text).
 *
 * <p>Markers render nothing and the renderer recognises them by type. Being row <em>types</em> rather than
 * metadata on {@code GuiMessage.Line} keeps the scheme out of vanilla's records - no duck interfaces,
 * nothing for other chat mods to trip on. The wrap step builds them, so re-wraps on resize keep them.</p>
 */
public final class ChatRows {

    /** Every Slate row knows the message it belongs to (hover actions, click hit-testing, grouping). */
    public interface Row extends FormattedCharSequence {
        GuiMessage message();
    }

    /**
     * A wrapped text line of a message. {@code index} counts from the first line; {@code groupStart} is true
     * on the lines of a message that begins a sender group (compact density draws the head there).
     */
    public record TextRow(FormattedCharSequence text, GuiMessage message, int index, boolean first, boolean last, boolean groupStart) implements Row {
        @Override
        public boolean accept(final FormattedCharSink sink) { return text.accept(sink); }
    }

    /** Head + name + timestamp above a group (cozy density). Renders nothing itself. */
    public record HeaderRow(GuiMessage message, ChatMeta.Meta meta) implements Row {
        @Override
        public boolean accept(final FormattedCharSink sink) { return true; }
    }

    /** Breathing room before a group (cozy density). */
    public record GapRow(GuiMessage message) implements Row {
        @Override
        public boolean accept(final FormattedCharSink sink) { return true; }
    }

    /** One of {@code totalRows} lines an attachment card occupies below the message text (top = 0). */
    public record AttachmentRow(GuiMessage message, Attachment attachment, int rowIndex, int totalRows) implements Row {
        @Override
        public boolean accept(final FormattedCharSink sink) { return true; }
    }

    /** The message behind any row, or null for lines we never wrapped (another mod's insertions). */
    @Nullable
    public static GuiMessage messageOf(final FormattedCharSequence line) {
        return line instanceof Row r ? r.message() : null;
    }

    /** The drawable text of a row (the wrapped line for text rows, the row itself otherwise). */
    public static FormattedCharSequence text(final FormattedCharSequence line) {
        return line instanceof TextRow t ? t.text() : line;
    }

    private ChatRows() {}
}
