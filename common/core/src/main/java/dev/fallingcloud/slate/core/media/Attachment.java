package dev.fallingcloud.slate.core.media;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One renderable attachment on a chat message, and the parsing that finds them. Side-neutral (no client
 * classes): the server-side relay and the client renderer share the grammar.
 *
 * <p>Two sources:</p>
 * <ul>
 *   <li><b>Tokens</b> - {@code [voice 0:07 #a1b2c3d4]}, {@code [image #a1b2c3d4]}, {@code [file name.zip #a1b2c3d4]},
 *       written into the chat text by a sender whose media travelled over the blob channel. The token IS the
 *       unmodded-player fallback, so it stays in the wire text; a rich card draws on extra rows below.</li>
 *   <li><b>Links</b> - ordinary pasted URLs. Image/GIF links embed a preview (policy-gated, see
 *       {@link #embedLinks}: fetching a link tells that host your IP, like clicking it would); video links get
 *       a click-to-open card and never download anything.</li>
 * </ul>
 *
 * @param kind       what the card shows
 * @param id         8 lowercase hex chars: the blob id for tokens, a hash of the URL for links
 * @param url        the link for URL kinds, {@code null} for blobs
 * @param durationMs clip length for voice, 0 otherwise
 * @param name       file name for {@link Kind#FILE_BLOB}, {@code ""} otherwise
 */
public record Attachment(Kind kind, String id, String url, int durationMs, String name) {

    public enum Kind { VOICE, IMAGE_BLOB, IMAGE_URL, VIDEO_URL, FILE_BLOB }

    /** Policy: detect image/GIF links as embeds (downloads them). Modules set it from their config. */
    public static volatile boolean embedLinks = true;
    /** Policy: card height of image previews, in chat lines. */
    public static volatile int thumbRows = 6;
    /** Most attachments one message may carry (bounds abuse). */
    public static final int MAX_PER_MESSAGE = 3;
    /** Longest file name kept in a token. */
    public static final int MAX_NAME = 40;

    private static final Pattern TOKEN =
        Pattern.compile("\\[(voice|image)(?: (\\d+):(\\d{2}))? #([0-9a-f]{8})\\]");
    private static final Pattern FILE_TOKEN =
        Pattern.compile("\\[file ([^\\[\\]#]{1,60}?) #([0-9a-f]{8})\\]");
    private static final Pattern IMAGE_URL =
        Pattern.compile("https?://\\S+?\\.(?:png|jpe?g|gif|webp)(?:\\?\\S*)?(?=\\s|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VIDEO_URL =
        Pattern.compile("https?://(?:\\S*?(?:youtube\\.com/watch\\S*|youtu\\.be/\\S+|youtube\\.com/shorts/\\S+)|\\S+?\\.(?:mp4|webm|mov)(?:\\?\\S*)?)(?=\\s|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_URL =
        Pattern.compile("https?://[^\\s<>\"']+", Pattern.CASE_INSENSITIVE);

    public Attachment(final Kind kind, final String id, final String url, final int durationMs) {
        this(kind, id, url, durationMs, "");
    }

    /** How many whole chat lines this attachment's card occupies below the message text. */
    public int rows() {
        return switch (kind) {
            case VOICE -> 2;
            case FILE_BLOB -> 2;
            case VIDEO_URL -> 3;
            case IMAGE_BLOB, IMAGE_URL -> Math.max(3, Math.min(12, thumbRows));
        };
    }

    public boolean isGif() {
        return url != null && url.toLowerCase(Locale.ROOT).contains(".gif");
    }

    /** True for kinds whose bytes arrive over the blob channel (not a link). */
    public boolean isBlob() {
        return kind == Kind.VOICE || kind == Kind.IMAGE_BLOB || kind == Kind.FILE_BLOB;
    }

    public boolean isImage() {
        return kind == Kind.IMAGE_BLOB || kind == Kind.IMAGE_URL;
    }

    /** All attachments in one chat message, tokens first, in text order. At most {@link #MAX_PER_MESSAGE}. */
    public static List<Attachment> parse(final String message) {
        return parse(message, embedLinks);
    }

    public static List<Attachment> parse(final String message, final boolean links) {
        final List<Attachment> out = new ArrayList<>(2);
        if (message == null || message.isEmpty()) return out;
        final Matcher t = TOKEN.matcher(message);
        while (t.find() && out.size() < MAX_PER_MESSAGE) {
            final boolean voice = t.group(1).equals("voice");
            int duration = 0;
            if (t.group(2) != null) {
                try {
                    duration = (Integer.parseInt(t.group(2)) * 60 + Integer.parseInt(t.group(3))) * 1000;
                } catch (final NumberFormatException ignored) {}
            }
            out.add(new Attachment(voice ? Kind.VOICE : Kind.IMAGE_BLOB, t.group(4), null, duration));
        }
        final Matcher f = FILE_TOKEN.matcher(message);
        while (f.find() && out.size() < MAX_PER_MESSAGE) {
            out.add(new Attachment(Kind.FILE_BLOB, f.group(2), null, 0, f.group(1).trim()));
        }
        if (links) {
            final Matcher img = IMAGE_URL.matcher(message);
            while (img.find() && out.size() < MAX_PER_MESSAGE) {
                out.add(new Attachment(Kind.IMAGE_URL, idForUrl(img.group()), img.group(), 0));
            }
        }
        final Matcher vid = VIDEO_URL.matcher(message);
        while (vid.find() && out.size() < MAX_PER_MESSAGE) {
            out.add(new Attachment(Kind.VIDEO_URL, idForUrl(vid.group()), vid.group(), 0));
        }
        return out;
    }

    /** Builds the chat token a sender posts alongside an uploaded blob (voice / image kinds). */
    public static String token(final Kind kind, final String id, final int durationMs) {
        return token(kind, id, durationMs, "");
    }

    /** Builds the chat token; {@code name} is only used for {@link Kind#FILE_BLOB}. */
    public static String token(final Kind kind, final String id, final int durationMs, final String name) {
        switch (kind) {
            case VOICE -> {
                final int totalSec = Math.round(durationMs / 1000.0f);
                return "[voice %d:%02d #%s]".formatted(totalSec / 60, totalSec % 60, id);
            }
            case FILE_BLOB -> {
                return "[file %s #%s]".formatted(safeName(name), id);
            }
            default -> {
                return "[image #%s]".formatted(id);
            }
        }
    }

    /** A file name that survives the token grammar: no brackets/hashes/newlines, bounded length. */
    public static String safeName(final String name) {
        String n = name == null ? "file" : name.replaceAll("[\\[\\]#\\r\\n\\t]", "_").trim();
        if (n.isEmpty()) n = "file";
        if (n.length() > MAX_NAME) {
            final int dot = n.lastIndexOf('.');
            final String ext = dot > 0 && n.length() - dot <= 8 ? n.substring(dot) : "";
            n = n.substring(0, Math.max(1, MAX_NAME - ext.length() - 1)) + "~" + ext;
        }
        return n;
    }

    /** Stable 8-hex id for URL attachments, so the cache and click registry key uniformly. */
    public static String idForUrl(final String url) {
        return "%08x".formatted(url.hashCode());
    }

    /** Every http(s) link in the text, in order (for "open link" actions). */
    public static List<String> links(final String text) {
        final List<String> out = new ArrayList<>(1);
        if (text == null) return out;
        final Matcher m = ANY_URL.matcher(text);
        while (m.find()) out.add(m.group());
        return out;
    }

    /** True when the text contains at least one token or link. */
    public static boolean hasAny(final String text) {
        return text != null && (TOKEN.matcher(text).find() || FILE_TOKEN.matcher(text).find() || ANY_URL.matcher(text).find());
    }
}
