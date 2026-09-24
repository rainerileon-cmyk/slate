package dev.fallingcloud.slate.building.client.mode;

import dev.fallingcloud.slate.building.ops.Clipboard;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The synced clipboard ({@code ClipboardSync}, stored in {@link ClientModeState#clipboard()}) decoded with the ops
 * server's own format ({@link Clipboard#fromTag}) for the paste preview, cached per sync.
 */
final class ClipboardReader {

    private static int cachedVersion = Integer.MIN_VALUE;
    private static @Nullable Clipboard cached;

    /** The current clipboard, decoded (null when empty or unreadable). */
    static @Nullable Clipboard current(final Level level) {
        final int version = ClientModeState.clipboardVersion();
        if (version != cachedVersion) {
            cachedVersion = version;
            final Clipboard clip = Clipboard.fromTag(ClientModeState.clipboard());
            cached = clip.isEmpty() ? null : clip;
        }
        return cached;
    }

    private ClipboardReader() {}
}
