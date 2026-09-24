package dev.fallingcloud.slate.building.mixin.variant;

import dev.fallingcloud.slate.building.variant.VariantCreativeTab;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Unify hook: with {@code deleteNativeVariants}, native variant items leave every tab and the search. Late priority
 * and TAIL, so the loaders' own tab events have added their entries first.
 */
@Mixin(value = CreativeModeTab.class, priority = 1500)
public abstract class CreativeModeTabMixin {

    @Inject(method = "buildContents", at = @At("TAIL"))
    private void slateBuilding$hideNativeVariants(final CreativeModeTab.ItemDisplayParameters parameters, final CallbackInfo ci) {
        VariantCreativeTab.stripNatives((CreativeModeTab) (Object) this);
    }
}
