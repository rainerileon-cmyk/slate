package dev.fallingcloud.slate.building.neoforge;

import dev.fallingcloud.slate.building.block.VerticalSlabBlock;
import dev.fallingcloud.slate.building.platform.BuildingPlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * NeoForge implementation of {@link BuildingPlatform} (ServiceLoader, see this jar's {@code META-INF/services}).
 * Breaks and placements go through the same events a player's own mining / placing fires, so claim and protection
 * mods (FTB Chunks, Open Parties and Claims, ...) cover building operations too.
 */
public final class NeoForgeBuildingPlatform implements BuildingPlatform {

    @Override
    public boolean canBreak(final ServerPlayer p, final ServerLevel level, final BlockPos pos, final BlockState state) {
        // The BreakEvent claim and protection mods listen to, posted directly. Not CommonHooks.fireBlockBreak: that also
        // applies the held item's canAttackBlock (a creative player holding a sword could clear nothing) and sends
        // block updates to the client for a break that has not happened yet. Game-master blocks keep vanilla's rule.
        if (state.getBlock() instanceof GameMasterBlock && !p.canUseGameMasterBlocks()) return false;
        // KleeSlabs (DF pack) performs its half-slab break INSIDE this event (sets the single state, drops the half,
        // cancels), so posting a double slab would split it. This is a permission probe, not the break: listeners see
        // a single slab (claim mods only look at the position and the player).
        return !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, probeState(state), p)).isCanceled();
    }

    private static BlockState probeState(final BlockState state) {
        if (state.hasProperty(SlabBlock.TYPE) && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) return state.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        if (state.getBlock() instanceof VerticalSlabBlock && !state.getValue(VerticalSlabBlock.SINGLE)) return state.setValue(VerticalSlabBlock.SINGLE, true);
        return state;
    }

    @Override
    public boolean tryPlace(final ServerPlayer p, final ServerLevel level, final BlockPos pos, final BlockState state,
                            final Direction face, final int flags) {
        // The snapshot must be taken BEFORE the change: listeners compare it with the placed block, and it is what
        // a cancelled placement is restored from (block entity data included).
        final BlockSnapshot before = BlockSnapshot.create(level.dimension(), level, pos, flags);
        if (!level.setBlock(pos, state, flags)) return false;
        if (EventHooks.onBlockPlace(p, before, face)) {
            before.restore(flags | Block.UPDATE_CLIENTS);
            return false;
        }
        return true;
    }

    @Override
    public void openMenu(final ServerPlayer p, final MenuProvider provider) {
        p.openMenu(provider);
    }

    @Override
    public boolean isFakePlayer(final Player p) {
        return p instanceof FakePlayer;
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return CreativeModeTab.builder();
    }
}
