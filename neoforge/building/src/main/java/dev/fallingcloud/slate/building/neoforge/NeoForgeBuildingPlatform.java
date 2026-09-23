package dev.fallingcloud.slate.building.neoforge;

import dev.fallingcloud.slate.building.platform.BuildingPlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;

/**
 * NeoForge implementation of {@link BuildingPlatform} (ServiceLoader, see this jar's {@code META-INF/services}).
 * Breaks and placements go through the same events a player's own mining / placing fires, so claim and protection
 * mods (FTB Chunks, Open Parties and Claims, ...) cover building operations too.
 */
public final class NeoForgeBuildingPlatform implements BuildingPlatform {

    @Override
    public boolean canBreak(final ServerPlayer p, final ServerLevel level, final BlockPos pos, final BlockState state) {
        // Also applies the held item's canAttackBlock, adventure-mode rules and game-master blocks, and resends the
        // block to the client when a listener cancels.
        return !CommonHooks.fireBlockBreak(level, p.gameMode.getGameModeForPlayer(), p, pos, state).isCanceled();
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
