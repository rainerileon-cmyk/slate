package dev.fallingcloud.slate.multiplayer.social;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Thread keys. A DM thread is {@code dm:<lower uuid>_<higher uuid>} (so both sides compute the same key),
 * a group thread is {@code g:<group id>}. Blob targets use the matching prefixes {@code p:<uuid>} (one
 * player) and {@code g:<id>}.
 */
public final class Threads {

    public static final String DM = "dm:";
    public static final String GROUP = "g:";

    public static String dm(final UUID a, final UUID b) {
        return a.toString().compareTo(b.toString()) <= 0 ? DM + a + "_" + b : DM + b + "_" + a;
    }

    public static String group(final String groupId) { return GROUP + groupId; }

    public static boolean isDm(final String key) { return key != null && key.startsWith(DM); }

    public static boolean isGroup(final String key) { return key != null && key.startsWith(GROUP); }

    /** The group id of a group thread key, or null. */
    @Nullable
    public static String groupId(final String key) {
        return isGroup(key) ? key.substring(GROUP.length()) : null;
    }

    /** The two members of a DM key, or null when malformed. */
    @Nullable
    public static UUID[] dmMembers(final String key) {
        if (!isDm(key)) return null;
        final String[] parts = key.substring(DM.length()).split("_");
        if (parts.length != 2) return null;
        try {
            return new UUID[] { UUID.fromString(parts[0]), UUID.fromString(parts[1]) };
        } catch (final IllegalArgumentException e) {
            return null;
        }
    }

    /** In a DM thread, the member that is not {@code self}; null when self is not a member. */
    @Nullable
    public static UUID dmOther(final String key, final UUID self) {
        final UUID[] m = dmMembers(key);
        if (m == null) return null;
        if (m[0].equals(self)) return m[1];
        if (m[1].equals(self)) return m[0];
        return null;
    }

    /** Blob target for a thread: {@code p:<other>} for DMs, {@code g:<id>} for groups. */
    public static String blobTarget(final String key, final UUID self) {
        if (isGroup(key)) return key;
        final UUID other = dmOther(key, self);
        return other == null ? "" : "p:" + other;
    }

    /** A file-system safe name for a thread key. */
    public static String fileName(final String key) {
        return key.replace(':', '.').replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private Threads() {}
}
