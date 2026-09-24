package dev.fallingcloud.slate.building.chisel;

import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Chisel groups (design §9): sets of full blocks that are 1:1 texture variants of each other (stone ↔ stone bricks
 * ↔ chiseled stone bricks, a Chipped/Rechiseled group, ...). Each provider yields its own groups (never merged),
 * shown as separate wheel pages. The server builds the index ({@link #server}); clients get a copy
 * ({@link #client}), or in singleplayer read the integrated server's index directly.
 *
 * <p>Instances are immutable, so a reference can be kept and compared: a new index (after {@code /reload}, a server
 * change or a resync) is a new object with a higher {@link #generation()}.
 *
 * <p>Owner: I (chisel).
 */
public final class ChiselGroups {

    /**
     * One group.
     *
     * @param source  provider id ({@code overrides}, {@code rechiseled}, {@code chipped}, {@code chisel},
     *                {@code stonecutter}, {@code families})
     * @param name    display name of the group (the wheel page title: "Stonecutter", "Rechiseled", "Stone", ...)
     * @param members the blocks, in wheel order
     */
    public record Group(String source, Component name, List<Block> members) {
        public Group {
            members = List.copyOf(members);
        }

        public boolean contains(final Block block) {
            for (final Block b : members) if (b == block) return true;
            return false;
        }
    }

    private static final AtomicInteger GENERATIONS = new AtomicInteger();
    private static final ChiselGroups EMPTY = new ChiselGroups(List.of(), Set.of(), 0);

    /** The index of the server running in this process, with the server it belongs to. */
    private record ServerIndex(MinecraftServer server, ChiselGroups groups) {}

    private static volatile @Nullable ServerIndex serverIndex;

    private final List<Group> groups;
    private final Map<Block, List<Group>> byBlock;
    private final Set<Block> inWorld;
    private final int generation;

    ChiselGroups(final List<Group> groups, final Set<Block> inWorld, final int generation) {
        this.groups = List.copyOf(groups);
        this.generation = generation;
        final Set<Block> flags = Collections.newSetFromMap(new IdentityHashMap<>());
        flags.addAll(inWorld);
        this.inWorld = Collections.unmodifiableSet(flags);
        final Map<Block, List<Group>> by = new IdentityHashMap<>();
        for (final Group g : this.groups) {
            for (final Block b : g.members()) by.computeIfAbsent(b, k -> new ArrayList<>()).add(g);
        }
        by.replaceAll((block, list) -> List.copyOf(dropCovered(list)));
        this.byBlock = Collections.unmodifiableMap(by);
    }

    /**
     * Pages of one block without repeats: a group all of whose members are already in an EARLIER page (e.g. the
     * stonecutter's {stone, stone bricks, chiseled stone bricks} under the shipped "Stone" group) adds nothing to
     * choose from, so it is left out. Later sources never hide earlier ones, so the server's own groups always show.
     */
    private static List<Group> dropCovered(final List<Group> list) {
        final List<Group> kept = new ArrayList<>(list.size());
        outer:
        for (final Group g : list) {
            for (final Group k : kept) {
                if (ChiselRules.asSet(k.members()).containsAll(g.members())) continue outer;
            }
            kept.add(g);
        }
        return kept;
    }

    // ------------------------------------------------------------------ access

    /** The empty index (no groups). */
    public static ChiselGroups empty() {
        return EMPTY;
    }

    /**
     * The index the client can use: in singleplayer (and as a LAN host) the integrated server's own index, else the
     * copy the server synced (empty before the first sync and on servers without Slate Building). Client only.
     */
    public static ChiselGroups client() {
        return ClientSide.get();
    }

    /** The server's index, built on first use (normally at server start and after every datapack reload). */
    public static ChiselGroups server(final MinecraftServer server) {
        final ChiselGroups built = builtFor(server);
        return built != null ? built : rebuild(server);
    }

    /** The index already built for {@code server}, or null (never builds; safe from any thread). */
    public static @Nullable ChiselGroups builtFor(final MinecraftServer server) {
        final ServerIndex idx = serverIndex;
        return idx != null && idx.server() == server ? idx.groups() : null;
    }

    /** Rebuilds the server index from the current recipes, tags, datapacks and config. Server thread. */
    static synchronized ChiselGroups rebuild(final MinecraftServer server) {
        final ChiselGroups groups = ChiselIndexBuilder.build(server, BuildingServerSettings.local().chisel(), GENERATIONS.incrementAndGet());
        serverIndex = new ServerIndex(server, groups);
        return groups;
    }

    /** Forgets the index of a stopping server. */
    static synchronized void forget(final MinecraftServer server) {
        final ServerIndex idx = serverIndex;
        if (idx != null && idx.server() == server) serverIndex = null;
    }

    // ------------------------------------------------------------------ queries

    /** Every group {@code material} belongs to, one per provider that knows it (pages without repeats). */
    public List<Group> groupsOf(final Block material) {
        return byBlock.getOrDefault(material, List.of());
    }

    /** Whether {@code a} can be chiselled into {@code b} (both in one group; a member is linked to itself). */
    public boolean linked(final Block a, final Block b) {
        if (a == b) return byBlock.containsKey(a);
        for (final Group g : groupsOf(a)) if (g.contains(b)) return true;
        return false;
    }

    /** Whether {@code block} is in any group. */
    public boolean contains(final Block block) {
        return byBlock.containsKey(block);
    }

    /**
     * Whether {@code block} may be chiselled in the world, as the server judged it: its loot table drops exactly the
     * block itself (else chiselling it in place would be free silk touch, e.g. stone → stone bricks). The server
     * re-checks the real block at the moment of the chisel; this is for greying out wheel slices.
     */
    public boolean inWorld(final Block block) {
        return inWorld.contains(block);
    }

    /** All groups in page order. */
    public List<Group> groups() {
        return groups;
    }

    /** Every block that is in at least one group. */
    public Set<Block> members() {
        return byBlock.keySet();
    }

    Set<Block> inWorldBlocks() {
        return inWorld;
    }

    public boolean isEmpty() {
        return groups.isEmpty();
    }

    /** Increases with every server rebuild; a client copy carries the server's number. 0 = {@link #empty()}. */
    public int generation() {
        return generation;
    }

    @Override
    public String toString() {
        return "ChiselGroups[gen " + generation + ", " + groups.size() + " groups, " + byBlock.size() + " blocks]";
    }

    /** Client-only references live here so a dedicated server never resolves them. */
    private static final class ClientSide {
        static ChiselGroups get() { return dev.fallingcloud.slate.building.chisel.client.ChiselClient.groups(); }
    }
}
