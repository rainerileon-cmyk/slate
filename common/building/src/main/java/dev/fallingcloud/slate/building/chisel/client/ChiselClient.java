package dev.fallingcloud.slate.building.chisel.client;

import dev.fallingcloud.slate.building.net.ChiselGroupsSync;

/**
 * Client side of chisel groups: keeps the synced index behind {@code ChiselGroups.client()}.
 *
 * <p>Owner: I (chisel). Skeleton stub (empty); {@link #init()} is called from {@code BuildingClient.init()},
 * {@link #onGroupsSync} from {@code ClientActions}.
 */
public final class ChiselClient {

    public static void init() {
    }

    /** The server's chisel index arrived (join / datapack reload). */
    public static void onGroupsSync(final ChiselGroupsSync payload) {
    }

    private ChiselClient() {}
}
