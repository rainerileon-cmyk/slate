package dev.fallingcloud.slate.core.net;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Where a payload arrived. Handlers always run on the receiving side's main thread.
 *
 * @param sender the sending player for a serverbound payload, {@code null} when clientbound
 */
public record NetContext(boolean isClient, @Nullable ServerPlayer sender) {

    public static NetContext client() { return new NetContext(true, null); }

    public static NetContext server(final ServerPlayer sender) { return new NetContext(false, sender); }
}
