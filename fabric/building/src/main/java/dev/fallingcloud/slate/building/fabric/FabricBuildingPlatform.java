package dev.fallingcloud.slate.building.fabric;

import dev.fallingcloud.slate.building.platform.BuildingPlatform;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric implementation of {@link BuildingPlatform} (ServiceLoader, see this jar's {@code META-INF/services}).
 * Protection mods on Fabric hook {@code PlayerBlockBreakEvents.BEFORE}; there is no placement event in Fabric API, so
 * placements are plain {@code setBlock} (claim mods that need more patch {@code BlockItem} themselves).
 */
public final class FabricBuildingPlatform implements BuildingPlatform {

    @Override
    public boolean canBreak(final ServerPlayer p, final ServerLevel level, final BlockPos pos, final BlockState state) {
        if (state.getBlock() instanceof GameMasterBlock && !p.canUseGameMasterBlocks()) return false;   // vanilla's rule, as on NeoForge
        return PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, p, pos, state, level.getBlockEntity(pos));
    }

    @Override
    public boolean tryPlace(final ServerPlayer p, final ServerLevel level, final BlockPos pos, final BlockState state,
                            final Direction face, final int flags) {
        return level.setBlock(pos, state, flags);
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
        return FabricItemGroup.builder();
    }
}
