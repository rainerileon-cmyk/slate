package dev.fallingcloud.slate.building.chisel.client;

import dev.fallingcloud.slate.building.chisel.ChiselGroups;
import dev.fallingcloud.slate.building.chisel.ChiselSwap;
import dev.fallingcloud.slate.building.chisel.ChiselSync;
import dev.fallingcloud.slate.building.net.ChiselGroupsSync;
import dev.fallingcloud.slate.building.net.ChiselHeld;
import dev.fallingcloud.slate.building.net.ChiselTarget;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.net.SlateNetwork;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Client side of chisel groups: keeps the synced index behind {@code ChiselGroups.client()} and sends the chisel
 * requests. In singleplayer (and as a LAN host) the integrated server's own index is read directly, so the host never
 * waits for or decodes a copy.
 *
 * <p>For the wheels (owner C): {@link #pages(ItemStack)} lists what the held stack can become, page by page;
 * {@link #chiselHeld} / {@link #chiselTarget} ask the server to do it. Locks: {@link ChiselSwap#heldLock} and
 * {@link ChiselSwap#inWorldLock}.
 *
 * <p>Owner: I (chisel). {@link #init()} is called from {@code BuildingClient.init()}, {@link #onGroupsSync} from
 * {@code ClientActions}.
 */
public final class ChiselClient {

    private static final ChiselSync.Assembler ASSEMBLER = new ChiselSync.Assembler();
    private static volatile ChiselGroups synced = ChiselGroups.empty();

    public static void init() {
        SlateEvents.CLIENT_LEFT_SERVER.register(ChiselClient::clear);
        ChiselHarness.register();
    }

    /** The server's chisel index arrived (join / datapack reload), possibly in several parts. */
    public static void onGroupsSync(final ChiselGroupsSync payload) {
        final ChiselGroups done = ASSEMBLER.accept(payload.tag());
        if (done != null) synced = done;
    }

    /** The index this client uses (see {@link ChiselGroups#client()}). */
    public static ChiselGroups groups() {
        final IntegratedServer local = Minecraft.getInstance().getSingleplayerServer();
        if (local != null) {
            final ChiselGroups own = ChiselGroups.builtFor(local);
            if (own != null) return own;
        }
        return synced;
    }

    /** The chisel pages for a held stack (members that exist in its shape), for the wheels. */
    public static List<ChiselSwap.Page> pages(final ItemStack held) {
        return ChiselSwap.pages(groups(), held);
    }

    /** Asks the server to chisel the stack in inventory slot {@code slot} (0..35, 40 = offhand) into {@code material}. */
    public static void chiselHeld(final int slot, final Block material) {
        SlateNetwork.get().sendToServer(new ChiselHeld(slot, BuiltInRegistries.BLOCK.getKey(material)));
    }

    /** Asks the server to chisel the block at {@code pos} into {@code material} (Chisel tier 2). */
    public static void chiselTarget(final BlockPos pos, final Block material) {
        SlateNetwork.get().sendToServer(new ChiselTarget(pos.immutable(), BuiltInRegistries.BLOCK.getKey(material)));
    }

    private static void clear() {
        ASSEMBLER.reset();
        synced = ChiselGroups.empty();
    }

    private ChiselClient() {}
}
