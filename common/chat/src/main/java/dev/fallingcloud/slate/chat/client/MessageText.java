package dev.fallingcloud.slate.chat.client;

import dev.fallingcloud.slate.chat.ChatConfig;
import dev.fallingcloud.slate.core.media.Attachment;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Turns a received component into what the local player sees: the sender prefix dropped (the header row
 * or inline name carries it), media links shortened to a marker (the card below IS the link's content),
 * and {@code :emote:} tokens swapped for glyphs. Only the local display copy changes; the wire text, the
 * chat log and copy-to-clipboard all use the original.
 */
public final class MessageText {

    /** The message without its sender prefix. Unknown formats come back unchanged. */
    public static Component body(final Component content, final String sender) {
        if (sender == null || sender.isEmpty()) return content;
        if (content.getContents() instanceof TranslatableContents tc) {
            final Object[] args = tc.getArgs();
            switch (tc.getKey()) {
                case "chat.type.text" -> { if (args.length >= 2) return withSiblings(arg(args[1]), content); }
                case "chat.type.team.text" -> {
                    if (args.length >= 3) return withSiblings(Component.empty().append(arg(args[0])).append(" ").append(arg(args[2])), content);
                }
                default -> {}
            }
        }
        final int end = SenderResolver.prefixEnd(content.getString(), sender);
        return end > 0 ? drop(content, end) : content;
    }

    private static Component arg(final Object a) {
        return a instanceof Component c ? c : Component.literal(String.valueOf(a));
    }

    /** A translatable's extracted argument plus the siblings appended after the translatable itself. */
    private static Component withSiblings(final Component core, final Component original) {
        if (original.getSiblings().isEmpty()) return core;
        final MutableComponent out = Component.empty().append(core);
        for (final Component s : original.getSiblings()) out.append(s);
        return out;
    }

    /** Copies a component minus its first {@code chars} characters, keeping every style and event. */
    public static Component drop(final Component content, final int chars) {
        final MutableComponent out = Component.empty();
        final int[] skip = { chars };
        content.visit((style, text) -> {
            if (skip[0] >= text.length()) { skip[0] -= text.length(); return Optional.empty(); }
            final String kept = skip[0] > 0 ? text.substring(skip[0]) : text;
            skip[0] = 0;
            if (!kept.isEmpty()) out.append(Component.literal(kept).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    /**
     * The display form of a message body: link markers and emote glyphs applied per styled segment so click
     * and hover events survive. {@code attachments} are the parsed attachments of the ORIGINAL text.
     */
    public static Component display(final Component body, final List<Attachment> attachments) {
        final ChatConfig cfg = ChatConfig.get();
        final boolean emotes = cfg.emotes;
        boolean anyLink = false;
        for (final Attachment a : attachments) if (a.url() != null) { anyLink = true; break; }
        final String plain = body.getString();
        if (!anyLink && !(emotes && Emotes.containsToken(plain))) return body;
        final List<Attachment> links = anyLink ? attachments : List.of();
        final MutableComponent out = Component.empty();
        final int marker = Theme.current().palette().textMuted() & 0xFFFFFF;
        body.visit((style, text) -> {
            appendSegment(out, text, style, links, emotes, marker);
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    /** One styled segment: link urls become muted markers, the rest goes through the emote rewrite. */
    private static void appendSegment(final MutableComponent out, final String text, final Style style,
                                      final List<Attachment> links, final boolean emotes, final int markerColor) {
        String rest = text;
        while (!rest.isEmpty()) {
            Attachment hit = null;
            int at = Integer.MAX_VALUE;
            for (final Attachment a : links) {
                if (a.url() == null) continue;
                final int i = rest.indexOf(a.url());
                if (i >= 0 && i < at) { at = i; hit = a; }
            }
            if (hit == null) break;
            final String before = rest.substring(0, at);
            if (!before.isEmpty()) plain(out, before, style, emotes);
            final String m = hit.kind() == Attachment.Kind.VIDEO_URL ? "[video]" : hit.isGif() ? "[gif]" : "[image]";
            out.append(Component.literal(m).withStyle(style.withColor(markerColor)));
            rest = rest.substring(at + hit.url().length());
        }
        if (!rest.isEmpty()) plain(out, rest, style, emotes);
    }

    private static void plain(final MutableComponent out, final String text, final Style style, final boolean emotes) {
        if (emotes) Emotes.rewriteSegment(text, style, out);
        else out.append(Component.literal(text).withStyle(style));
    }

    private MessageText() {}
}
