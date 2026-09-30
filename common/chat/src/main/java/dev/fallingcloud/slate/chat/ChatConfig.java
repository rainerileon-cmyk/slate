package dev.fallingcloud.slate.chat;

import dev.fallingcloud.slate.core.config.JsonConfig;

/**
 * {@code config/slate/chat.json} - the client side of Slate Chat. Public fields with initialiser defaults
 * (see {@link JsonConfig}). Values of {@code -1} / {@code 0} on the size and opacity fields mean "use the
 * vanilla chat option" so the vanilla settings screen keeps working for people who never open ours.
 *
 * <p>Privacy note on {@link #embedLinks}: rendering an image/GIF link inline downloads it, which tells that
 * host your IP - exactly like clicking the link would. On a private server with people you know that is
 * fine, which is why it defaults on; turn it off somewhere public. Media sent as a Slate attachment
 * (voice, pasted images, files) never touches the web and is unaffected.</p>
 */
public final class ChatConfig {

    // ---- look
    /** Panel/background opacity 0..1; -1 = vanilla's "text background opacity" option. */
    public double opacity = -1;
    /** Chat width in GUI px; 0 = vanilla's chat width option. */
    public int width = 0;
    /** Chat height (open) in GUI px; 0 = vanilla's focused height option. */
    public int height = 0;
    /** Chat height (closed) in GUI px; 0 = vanilla's unfocused height option. */
    public int heightUnfocused = 0;
    /** {@code COZY} (Discord-style header rows, indented text) or {@code COMPACT} (name inline, head in the margin). */
    public String density = "COZY";
    /** Show the time of the first message of a group (cozy) / the message line (compact). */
    public boolean timestamps = true;
    /** {@link java.time.format.DateTimeFormatter} pattern for timestamps. */
    public String timeFormat = "HH:mm";
    /** Group consecutive messages by the same sender within {@link #groupWindowMinutes}. */
    public boolean grouping = true;
    public int groupWindowMinutes = 5;
    /** Draw player heads next to messages. */
    public boolean heads = true;
    /** Fade messages out in the closed chat (vanilla behaviour); off keeps the last ones visible until scrolled. */
    public boolean fadeUnfocused = true;

    // ---- attention
    /** Highlight messages that mention you and play a sound. */
    public boolean mentions = true;
    /** Treat your bare name (not only {@code @name}) as a mention. */
    public boolean mentionOnName = true;
    public boolean pingSound = true;
    public double pingVolume = 0.6;
    /** Small "N new" badge over the closed chat when messages arrived while it was closed. */
    public boolean unreadBadge = true;

    // ---- motion
    /** Slide new messages in from below (replaces the ChatAnimation mod). */
    public boolean animation = true;
    public int animationMs = 180;

    // ---- media
    /** Render image/GIF links as inline previews (downloads them - see the class note). */
    public boolean embedLinks = true;
    /** Largest linked file downloaded for an inline preview, in KB. */
    public int maxDownloadKb = 8192;
    /** Height of inline image previews, in chat lines. 6 is about Discord-sized. */
    public int thumbRows = 6;
    /** Longest voice message you can record, in seconds. */
    public int maxRecordSeconds = 30;
    /** A recorded GIF: how long it may be in seconds, how many frames it has a second, and how wide it is in pixels. */
    public int gifSeconds = 6;
    public int gifFps = 12;
    public int gifWidth = 480;

    // ---- history & extras
    /** Messages kept per server on disk and restored (greyed) when you rejoin. 0 disables. */
    public int historySize = 200;
    public boolean restoreHistory = true;
    /** Render {@code :smile:} style emotes and show the picker button. */
    public boolean emotes = true;
    /** Send/show "X is typing" (needs the module on the server). */
    public boolean typingIndicator = true;
    /** Copy / reply / open-link buttons on the hovered message while the chat is open. */
    public boolean hoverActions = true;
    /** The "System" tab that filters to non-player messages. */
    public boolean systemTab = true;

    public boolean isCompact() {
        return "COMPACT".equalsIgnoreCase(density);
    }

    private static JsonConfig<ChatConfig> file;

    public static synchronized JsonConfig<ChatConfig> file() {
        if (file == null) file = JsonConfig.of("chat", ChatConfig.class, ChatConfig::new);
        return file;
    }

    public static ChatConfig get() {
        return file().get();
    }
}
