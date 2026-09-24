package dev.fallingcloud.slate.building.mixin.variant;

import dev.fallingcloud.slate.building.variant.VariantStacking;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Unify hook for pickups: {@code Inventory.add} tops up a matching stack, else takes an empty slot. In between, a shape
 * item with no exact stack to join goes into a stack of another shape of its material ({@link VariantStacking}), 1:1,
 * the way vanilla tops up a slot; whatever does not fit carries on to vanilla (an empty slot, as itself). Every
 * pickup path ends in this private per-slot step, after the loaders' pickup events.
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {

    @Inject(method = "addResource(Lnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"), cancellable = true)
    private void slateBuilding$joinAcrossShapes(final ItemStack stack, final CallbackInfoReturnable<Integer> cir) {
        final Inventory self = (Inventory) (Object) this;
        if (self.getSlotWithRemainingSpace(stack) != -1) return;
        final int slot = VariantStacking.joinSlot(self, stack);
        if (slot < 0) return;
        final ItemStack dest = self.getItem(slot);
        final int n = Math.min(stack.getCount(), self.getMaxStackSize(dest) - dest.getCount());
        if (n <= 0) return;
        dest.grow(n);
        dest.setPopTime(5);
        cir.setReturnValue(stack.getCount() - n);
    }
}
