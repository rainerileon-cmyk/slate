package dev.fallingcloud.slate.building.chisel;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;

/**
 * Chisel groups (design §9): sets of full blocks that are 1:1 texture variants of each other (stone ↔ stone bricks
 * ↔ chiseled stone bricks, a Chipped/Rechiseled group, ...). Each provider yields its own groups (never merged),
 * shown as separate wheel pages. The server builds the index; clients get a copy.
 *
 * <p>Owner: I (chisel). Skeleton stub with the final API: no groups anywhere.
 */
public final class ChiselGroups {

    /**
     * One group.
     *
     * @param source  provider id ({@code overrides}, {@code rechiseled}, {@code chipped}, {@code stonecutter}, {@code families}, ...)
     * @param name    display name of the group
     * @param members the blocks, in wheel order
     */
    public record Group(String source, Component name, List<Block> members) {
        public Group {
            members = List.copyOf(members);
        }
    }

    private static final ChiselGroups EMPTY = new ChiselGroups();

    /** The index the client received from the server (empty before the first sync). */
    public static ChiselGroups client() {
        return EMPTY;
    }

    /** The server's index. */
    public static ChiselGroups server(final MinecraftServer server) {
        return EMPTY;
    }

    /** Every group {@code material} belongs to, one per provider that knows it. */
    public List<Group> groupsOf(final Block material) {
        return List.of();
    }
}
