package dev.fallingcloud.slate.building.mixin.variant;

import com.google.gson.JsonElement;
import dev.fallingcloud.slate.building.variant.RecipeRebalancer;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Unify hook: before recipes are parsed, recipes that make a native variant at better than one material unit per
 * item are rebalanced (or removed with {@code deleteNativeVariants}), see {@link RecipeRebalancer}. The map is
 * replaced rather than edited, so an immutable map handed over by another mod cannot break the reload.
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @ModifyVariable(
        method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Map<ResourceLocation, JsonElement> slateBuilding$unifyRecipes(final Map<ResourceLocation, JsonElement> recipes) {
        return RecipeRebalancer.process(recipes);
    }
}
