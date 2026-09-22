package dev.fallingcloud.slate.core.net.blob;

import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/**
 * Decides who receives a relayed blob on the server. Modules register routers for their target
 * prefixes (Multiplayer: {@code p:}, {@code g:}, {@code s:}); the default routes {@code ""} to every
 * other player and drops unknown targets.
 */
@FunctionalInterface
public interface BlobRouter {

    /** Recipients of a transfer (never the sender). Empty = drop. */
    List<ServerPlayer> recipients(ServerPlayer sender, BlobPayloads.Start start);
}
