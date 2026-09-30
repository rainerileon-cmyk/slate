package dev.fallingcloud.slate.building.chisel;

import dev.fallingcloud.slate.building.block.ShapeBlockEntity;
import dev.fallingcloud.slate.building.compat.SubLevels;
import dev.fallingcloud.slate.building.net.ChiselHeld;
import dev.fallingcloud.slate.building.net.ChiselTarget;
import dev.fallingcloud.slate.building.ops.ToolType;
import dev.fallingcloud.slate.building.platform.BuildingPlatform;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import dev.fallingcloud.slate.building.variant.Variant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Server handlers of the chisel payloads (server thread, from {@code BuildingNetwork}). Only 1:1 swaps inside one
 * group are allowed (design §9); everything the client sent is re-validated: the slot, group membership, the chisel
 * tier, reach and protection, and for blocks in the world that the loot table is not bypassed.
 *
 * <p>Refusals explain themselves on the action bar (the wheels normally never offer a refused swap, so these are rare).
 *
 * <p>Owner: I (chisel).
 */
public final class ChiselActions {

    /** Swap the stack in {@code p.slot()} to group member {@code p.material()}, keeping shape and count. */
    public static void chiselHeld(final ChiselHeld p, final ServerPlayer player) {
        if (player.isSpectator()) return;
        final int slot = p.slot();
        if (!(slot >= 0 && slot < Inventory.INVENTORY_SIZE) && slot != Inventory.SLOT_OFFHAND) return;
        final Block target = BuiltInRegistries.BLOCK.getOptional(p.material()).orElse(null);
        if (target == null) return;

        final Component lock = ChiselSwap.heldLock(player);
        if (lock != null) { refuse(player, lock); return; }

        final Inventory inventory = player.getInventory();
        final ItemStack stack = inventory.getItem(slot);
        if (stack.isEmpty()) { refuse(player, Component.translatable("slate_building.chisel.fail.nothing")); return; }
        final Variant v = ChiselSwap.identify(stack).orElse(null);
        final ChiselGroups groups = ChiselGroups.server(player.server);
        if (v == null || !groups.contains(v.material())) {
            refuse(player, Component.translatable("slate_building.chisel.fail.not_chiselable", stack.getHoverName()));
            return;
        }
        if (v.material() == target) return;
        if (!groups.linked(v.material(), target)) {
            refuse(player, Component.translatable("slate_building.chisel.fail.not_in_group", v.material().getName(), target.getName()));
            return;
        }
        final ItemStack result = ChiselSwap.stackFor(target, v.shape(), stack.getCount());
        if (result.isEmpty()) {
            refuse(player, Component.translatable("slate_building.chisel.fail.no_shape", target.getName(), v.shape().displayName()));
            return;
        }

        inventory.setItem(slot, result);
        inventory.setChanged();
        player.containerMenu.broadcastChanges();
        if (!ToolboxAccess.of(player).creative()) ToolboxAccess.damageTool(player, ToolType.CHISEL, result.getCount());
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_STONECUTTER_TAKE_RESULT,
            SoundSource.PLAYERS, 0.6F, 0.95F + player.getRandom().nextFloat() * 0.15F);
    }

    /** Chisel the block at {@code p.pos()} into group member {@code p.material()}. */
    public static void chiselTarget(final ChiselTarget p, final ServerPlayer player) {
        if (player.isSpectator()) return;
        final Block target = BuiltInRegistries.BLOCK.getOptional(p.material()).orElse(null);
        if (target == null) return;
        final Component lock = ChiselSwap.inWorldLock(player);
        if (lock != null) { refuse(player, lock); return; }

        final ServerLevel level = player.serverLevel();
        final BlockPos pos = p.pos();
        if (!level.isLoaded(pos) || !player.canInteractWithBlock(pos, 1.0)) {
            refuse(player, Component.translatable("slate_building.chisel.fail.too_far"));
            return;
        }
        if (!player.mayBuild() || !level.mayInteract(player, pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            refuse(player, Component.translatable("slate_building.chisel.fail.protected"));
            return;
        }

        final BlockState old = level.getBlockState(pos);
        final BlockEntity be = level.getBlockEntity(pos);
        final Variant v = ChiselSwap.identify(old, be).orElse(null);
        final ChiselGroups groups = ChiselGroups.server(player.server);
        // Block entities other than our own shape blocks' would lose (or duplicate) their contents.
        final boolean foreignEntity = be != null && !(be instanceof ShapeBlockEntity);
        if (v == null || foreignEntity || old.getDestroySpeed(level, pos) < 0 || !groups.contains(v.material())) {
            refuse(player, Component.translatable("slate_building.chisel.fail.not_chiselable", old.getBlock().getName()));
            return;
        }
        if (v.material() == target) return;
        if (!groups.linked(v.material(), target)) {
            refuse(player, Component.translatable("slate_building.chisel.fail.not_in_group", v.material().getName(), target.getName()));
            return;
        }
        final ChiselSwap.WorldResult result = ChiselSwap.inWorld(old, be, v, target);
        if (result == null) {
            refuse(player, Component.translatable("slate_building.chisel.fail.no_shape", target.getName(), v.shape().displayName()));
            return;
        }

        final boolean creative = ToolboxAccess.of(player).creative();
        if (!creative) {
            // Loot guard (design §9): the block must drop exactly its own worth before AND after, or chiselling in place
            // would turn stone (drops cobblestone) into stone bricks (drop themselves): free silk touch.
            final int units = ChiselSwap.units(old, be);
            if (!ChiselSwap.dropsOwnWorth(level, pos, old, be, v.material(), units)) {
                refuse(player, Component.translatable("slate_building.chisel.fail.drops", old.getBlock().getName()));
                return;
            }
            final boolean ownShape = result.state().getBlock() instanceof ShapeBlock;   // drops come from its stored material
            if (!ownShape && !ChiselSwap.dropsOwnWorth(level, pos, result.state(), null, target, units)) {
                refuse(player, Component.translatable("slate_building.chisel.fail.target_drops", target.getName()));
                return;
            }
        }

        if (!BuildingPlatform.get().canBreak(player, level, pos, old)) {
            refuse(player, Component.translatable("slate_building.chisel.fail.protected"));
            return;
        }
        if (result.state() != old
            && !BuildingPlatform.get().tryPlace(player, level, pos, result.state(), facingToward(player, pos), Block.UPDATE_ALL)) {
            refuse(player, Component.translatable("slate_building.chisel.fail.protected"));
            return;
        }
        if (result.material() != null && level.getBlockEntity(pos) instanceof ShapeBlockEntity shaped) shaped.setMaterial(result.material());
        // Walls, fences and panes of the new material settle their own connections.
        final BlockState placed = level.getBlockState(pos);
        final BlockState settled = Block.updateFromNeighbourShapes(placed, level, pos);
        if (settled != placed) level.setBlock(pos, settled, Block.UPDATE_ALL);

        effects(level, pos, old, level.getBlockState(pos));
        if (!creative) ToolboxAccess.damageTool(player, ToolType.CHISEL, 1);
    }

    /** Chips of the old block, the new block's tap and the stonecutter's rasp. */
    private static void effects(final ServerLevel level, final BlockPos pos, final BlockState old, final BlockState now) {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, old), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
            20, 0.3, 0.3, 0.3, 0.08);
        final SoundType sound = now.getSoundType();
        level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 3.0F, sound.getPitch() * 0.85F);
        level.playSound(null, pos, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 0.7F, 0.9F + level.getRandom().nextFloat() * 0.2F);
    }

    /** The face of {@code pos} that points at the player (the "clicked" face for the loader's place event). */
    private static Direction facingToward(final ServerPlayer player, final BlockPos pos) {
        final Vec3 d = SubLevels.toSpaceOf(player.level(), pos, player.getEyePosition()).subtract(Vec3.atCenterOf(pos));
        return Direction.getNearest(d.x, d.y, d.z);
    }

    private static void refuse(final ServerPlayer player, final Component why) {
        player.displayClientMessage(why, true);
    }

    private ChiselActions() {}
}
