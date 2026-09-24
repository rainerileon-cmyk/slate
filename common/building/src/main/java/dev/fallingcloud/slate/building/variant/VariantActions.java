package dev.fallingcloud.slate.building.variant;

import dev.fallingcloud.slate.building.block.ShapeBehaviour;
import dev.fallingcloud.slate.building.block.ShapeBlockEntity;
import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.config.ServerVariants;
import dev.fallingcloud.slate.building.net.ReshapeTarget;
import dev.fallingcloud.slate.building.net.SwapHeld;
import dev.fallingcloud.slate.building.ops.ToolType;
import dev.fallingcloud.slate.building.platform.BuildingPlatform;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server handlers of the variant payloads (called on the server thread by {@code BuildingNetwork}). Nothing from the
 * client is trusted: slot, stack, shape availability, reach, protection, tools and the economy of design §1 are all
 * checked here. Failures tell the player why on the action bar.
 */
public final class VariantActions {

    private VariantActions() {}

    /** Turns the stack in {@code p.slot()} into shape {@code p.shape()} of the same material, count kept (1:1). */
    public static void swapHeld(final SwapHeld p, final ServerPlayer player) {
        if (player.isSpectator() || !player.isAlive()) return;
        final Shape target = Shape.byId(p.shape());
        final int slot = p.slot();
        if (target == null || !((slot >= 0 && slot < Inventory.INVENTORY_SIZE) || slot == Inventory.SLOT_OFFHAND)) return;
        final Inventory inventory = player.getInventory();
        final ItemStack stack = inventory.getItem(slot);
        final VariantRegistry registry = VariantRegistry.get();
        final Optional<Variant> variant = registry.identify(stack);
        if (variant.isEmpty()) {
            fail(player, Component.translatable("slate_building.variant.not_a_variant"));
            return;
        }
        final Block material = variant.get().material();
        if (variant.get().shape() == target) return;
        final ServerVariants rules = BuildingServerSettings.local().variants();
        if (rules != null && rules.swapNeedsTool && !player.getAbilities().instabuild && ToolboxAccess.of(player).tier(ToolType.HAMMER) < 1) {
            fail(player, Component.translatable("slate_building.variant.needs_hammer"));
            return;
        }
        final ItemStack swapped = registry.stackFor(material, target, stack.getCount());
        if (swapped.isEmpty()) {
            fail(player, Component.translatable("slate_building.variant.unavailable", material.getName(), target.displayName()));
            return;
        }
        inventory.setItem(slot, swapped);
        inventory.setChanged();
        player.containerMenu.broadcastChanges();
        final SoundType sound = material.defaultBlockState().getSoundType();
        player.playNotifySound(sound.getPlaceSound(), SoundSource.PLAYERS, 0.3F, sound.getPitch() * 1.35F);
    }

    /** Reshapes the block at {@code p.pos()} in the world, charging / refunding the unit difference. */
    public static void reshapeTarget(final ReshapeTarget p, final ServerPlayer player) {
        if (player.isSpectator() || !player.isAlive()) return;
        final Shape target = Shape.byId(p.shape());
        if (target == null) return;
        final ServerLevel level = player.serverLevel();
        final BlockPos pos = p.pos();
        if (!level.isLoaded(pos) || !player.canInteractWithBlock(pos, 1.0)) return;
        if (!player.mayBuild() || !level.mayInteract(player, pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            fail(player, Component.translatable("slate_building.variant.protected"));
            return;
        }

        final VariantRegistry registry = VariantRegistry.get();
        final BlockState old = level.getBlockState(pos);
        final BlockEntity oldEntity = level.getBlockEntity(pos);
        final Optional<Variant> variant = registry.identify(old, oldEntity);
        if (variant.isEmpty() || variant.get().shape() == target) return;
        final Block material = variant.get().material();

        final boolean creative = player.getAbilities().instabuild;
        if (ToolboxAccess.of(player).tier(ToolType.HAMMER) < 1) {
            fail(player, Component.translatable("slate_building.variant.needs_hammer"));
            return;
        }
        if (!registry.isAvailable(material, target)) {
            fail(player, Component.translatable("slate_building.variant.unavailable", material.getName(), target.displayName()));
            return;
        }
        final BlockState materialState = registry.materialState(old, oldEntity);
        final BlockState next = registry.reshape(level, pos, old, oldEntity, target);
        if (materialState == null || next == null || !next.canSurvive(level, pos)) {
            fail(player, Component.translatable("slate_building.variant.does_not_fit"));
            return;
        }
        if (!BuildingPlatform.get().canBreak(player, level, pos, old)) {
            fail(player, Component.translatable("slate_building.variant.protected"));
            return;
        }

        final int difference = registry.units(next, null) - registry.units(old, oldEntity);
        if (!creative && difference > 0 && !VariantEconomy.charge(player, material, difference)) {
            fail(player, Component.translatable("slate_building.variant.missing_material", difference, material.getName()));
            return;
        }
        if (!BuildingPlatform.get().tryPlace(player, level, pos, next, Direction.UP, Block.UPDATE_ALL)) {
            if (!creative && difference > 0) VariantEconomy.refund(player, material, difference);
            fail(player, Component.translatable("slate_building.variant.protected"));
            return;
        }
        if (next.getBlock() instanceof ShapeBlock && level.getBlockEntity(pos) instanceof ShapeBlockEntity be) {
            be.setMaterial(ShapeBehaviour.orientMaterial(materialState, next));
        }
        if (!creative) {
            if (difference < 0) VariantEconomy.refund(player, material, -difference);
            ToolboxAccess.damageTool(player, ToolType.HAMMER, 1);
        }

        final SoundType sound = materialState.getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1F) / 2F, sound.getPitch() * 0.9F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, materialState),
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 14, 0.3, 0.3, 0.3, 0.05);
    }

    private static void fail(final ServerPlayer player, final Component message) {
        player.displayClientMessage(message, true);
    }
}
