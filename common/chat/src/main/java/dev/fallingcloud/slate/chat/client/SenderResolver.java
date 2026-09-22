package dev.fallingcloud.slate.chat.client;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jetbrains.annotations.Nullable;

/**
 * Works out who sent a chat line.
 *
 * <p>Three sources, best first: the {@link GameProfile} vanilla's {@code ChatListener} hands to
 * {@code showMessageToPlayer} (captured by a mixin right before {@code addMessage}); the sender argument of
 * a {@code chat.type.*} translatable; and finally the {@code <name>} / {@code name:} prefix of a plain
 * system line (NoChatReports rewrites player chat into system messages, so this path carries most modded
 * servers). Prefix guesses are only trusted when the name is in the tab list. Unknown stays unknown - the
 * renderer then shows the line without a head, never breaks.</p>
 */
public final class SenderResolver {

    public record Sender(String name, @Nullable UUID uuid, boolean system) {
        static final Sender SYSTEM = new Sender("", null, true);
    }

    /** Raw string prefix forms, tolerant of legacy colour codes between the pieces. */
    private static final Pattern ANGLE = Pattern.compile("^(?:§.)*<(?:§.)*([A-Za-z0-9_]{1,16})(?:§.)*>(?:§.)*\\s");
    private static final Pattern COLON = Pattern.compile("^(?:§.)*(?:\\[[^\\]]{1,24}\\](?:§.)*\\s*)*(?:§.)*([A-Za-z0-9_]{1,16})(?:§.)*\\s*(?::|»|>)(?:§.)*\\s");

    @Nullable private static GameProfile pendingProfile;
    @Nullable private static String pendingName;

    /** The ChatListener mixin: a player message is about to be added. */
    public static void pending(@Nullable final GameProfile profile) {
        pendingProfile = profile;
        pendingName = null;
    }

    /** Disguised chat (a name without a profile). */
    public static void pendingName(@Nullable final String name) {
        pendingProfile = null;
        pendingName = name;
    }

    public static void clear() {
        pendingProfile = null;
        pendingName = null;
    }

    public static Sender resolve(final Component content, @Nullable final GuiMessageTag tag) {
        final GameProfile p = pendingProfile;
        final String n = pendingName;
        clear();
        if (p != null && p.getName() != null && !p.getName().isEmpty()) {
            return new Sender(p.getName(), p.getId(), false);
        }
        if (n != null && !n.isEmpty()) {
            return new Sender(n, lookupUuid(n), false);
        }
        // Translatable chat formats: chat.type.text (<%s> %s), chat.type.team.text ([team] <%s> %s), chat.type.emote.
        if (content.getContents() instanceof TranslatableContents tc) {
            final String key = tc.getKey();
            final Object[] args = tc.getArgs();
            if (("chat.type.text".equals(key) || "chat.type.emote".equals(key)) && args.length >= 1) {
                final String name = argString(args[0]);
                if (validName(name)) return new Sender(name, lookupUuid(name), false);
            }
            if ("chat.type.team.text".equals(key) && args.length >= 2) {
                final String name = argString(args[1]);
                if (validName(name)) return new Sender(name, lookupUuid(name), false);
            }
        }
        // Plain text prefixes (NoChatReports and most chat plugins). Only trusted for players we can see.
        final String raw = content.getString();
        Matcher m = ANGLE.matcher(raw);
        if (m.find()) {
            final String name = m.group(1);
            final UUID id = lookupUuid(name);
            if (id != null || isTagSystem(tag)) return new Sender(name, id, false);
        }
        m = COLON.matcher(raw);
        if (m.find()) {
            final String name = m.group(1);
            final UUID id = lookupUuid(name);
            if (id != null) return new Sender(name, id, false);
        }
        return Sender.SYSTEM;
    }

    /** Where a prefix match ends in the raw string (0 = no prefix), so the display can drop it. */
    public static int prefixEnd(final String raw, final String sender) {
        if (sender == null || sender.isEmpty() || raw == null) return 0;
        Matcher m = ANGLE.matcher(raw);
        if (m.find() && m.group(1).equalsIgnoreCase(sender)) return m.end();
        m = COLON.matcher(raw);
        if (m.find() && m.group(1).equalsIgnoreCase(sender)) return m.end();
        return 0;
    }

    private static boolean isTagSystem(@Nullable final GuiMessageTag tag) {
        return tag != null && tag.logTag() != null && tag.logTag().startsWith("System");
    }

    private static String argString(final Object arg) {
        if (arg instanceof Component c) return ChatFormatting.stripFormatting(c.getString());
        return arg == null ? "" : ChatFormatting.stripFormatting(String.valueOf(arg));
    }

    private static boolean validName(final String name) {
        return name != null && !name.isEmpty() && name.length() <= 16 && name.matches("[A-Za-z0-9_]+");
    }

    /** The tab-list uuid for a name, or null when nobody by that name is online. */
    @Nullable
    public static UUID lookupUuid(final String name) {
        final ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn == null || name == null) return null;
        final PlayerInfo info = conn.getPlayerInfo(name);
        if (info != null) return info.getProfile().getId();
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && name.equalsIgnoreCase(mc.getUser().getName())) return mc.getUser().getProfileId();
        return null;
    }

    /** A stable uuid to fetch a skin with when the real one is unknown (yields the default Steve/Alex). */
    public static UUID placeholderUuid(final String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private SenderResolver() {}
}
