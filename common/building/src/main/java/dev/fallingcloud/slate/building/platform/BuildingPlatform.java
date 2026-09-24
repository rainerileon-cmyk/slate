package dev.fallingcloud.slate.building.platform;

import dev.fallingcloud.slate.building.block.VerticalSlabBlock;
import dev.fallingcloud.slate.core.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The few things Slate Building needs from the mod loader, found through {@code ServiceLoader} like Core's
 * {@code SlatePlatform} (implementations: {@code NeoForgeBuildingPlatform}, {@code FabricBuildingPlatform}, each
 * declared in its loader's {@code META-INF/services}).
 *
 * <p>Protection: area operations and symmetry go through {@link #canBreak} / {@link #tryPlace} so claim and
 * protection mods see them as the player's own actions. The vanilla checks ({@code level.mayInteract(p, pos)},
 * {@code p.mayBuild()}, world border, {@code level.isLoaded(pos)}) are done by common code BEFORE calling these.
 */
public interface BuildingPlatform {

    static BuildingPlatform get() {
        return Services.load(BuildingPlatform.class);
    }

    /**
     * Whether {@code p} may break the block at {@code pos} as far as the loader's protection hooks are concerned.
     * NeoForge posts {@code BlockEvent.BreakEvent} directly (not {@code CommonHooks.fireBlockBreak}, whose held-item
     * rule refuses every break while a creative player holds a sword); Fabric asks {@code PlayerBlockBreakEvents.BEFORE}.
     * Both refuse game-master blocks to players who may not use them. Does not break anything.
     */
    boolean canBreak(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state);

    /**
     * The state {@link #canBreak} shows the loader's break listeners: a double slab (native or our vertical one) as a single
     * one. KleeSlabs (DF pack; NeoForge BreakEvent, Fabric through Balm) performs its half-slab break INSIDE that event:
     * it sets the single state, drops the half and cancels, so probing a double slab would split it. This is a
     * permission probe, not the break; claim mods only look at the position and the player.
     */
    static BlockState permissionProbe(final BlockState state) {
        if (state.hasProperty(SlabBlock.TYPE) && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) return state.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        if (state.getBlock() instanceof VerticalSlabBlock && !state.getValue(VerticalSlabBlock.SINGLE)) return state.setValue(VerticalSlabBlock.SINGLE, true);
        return state;
    }

    /**
     * Sets the block (with {@code flags}) if the loader's protection hooks let {@code p} place it there. NeoForge: sets
     * it, fires the place event ({@code EventHooks.onBlockPlace} with a pre-change snapshot) and reverts + returns false
     * if a listener cancelled it. Fabric has no place event: it asks what it can before writing (spawn protection,
     * adventure rules, the Common Protection API when installed, {@code PlayerBlockBreakEvents.BEFORE} for the position;
     * see {@code FabricBuildingPlatform}). {@code face} is the clicked face as in
     * {@code BlockPlaceContext.getClickedFace()}: the block it was placed against is {@code pos.relative(face.getOpposite())}.
     * Returns false as well when {@code setBlock} itself refused. Block-entity data (e.g. our shape material) is set by
     * the caller afterwards.
     */
    boolean tryPlace(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState state, Direction face, int flags);

    /** Opens a container menu for {@code p} (vanilla {@code ServerPlayer.openMenu} on both loaders). */
    void openMenu(ServerPlayer p, MenuProvider provider);

    /** Whether {@code p} is a loader fake player (machines, deployers); building ops refuse those. */
    boolean isFakePlayer(Player p);

    /**
     * A builder for a mod creative tab positioned by the loader's own tab paging (NeoForge
     * {@code CreativeModeTab.builder()}, Fabric {@code FabricItemGroup.builder()}); vanilla's
     * {@code builder(Row, int)} would claim a fixed slot and collide with other mods' tabs.
     */
    CreativeModeTab.Builder creativeTabBuilder();
}
