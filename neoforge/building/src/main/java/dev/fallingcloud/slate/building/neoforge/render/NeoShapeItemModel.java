package dev.fallingcloud.slate.building.neoforge.render;

import dev.fallingcloud.slate.building.client.model.ShapeItemModels;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * The item model of every shape item on NeoForge: its JSON model (display transforms + placeholder cube) with
 * overrides that resolve the stack's material into a cached per-material model ({@link ShapeItemModels}).
 */
final class NeoShapeItemModel extends BakedModelWrapper<BakedModel> {

    private static final ItemOverrides OVERRIDES = new Overrides();

    NeoShapeItemModel(final BakedModel json) {
        super(json);
    }

    @Override
    public ItemOverrides getOverrides() {
        return OVERRIDES;
    }

    private static final class Overrides extends ItemOverrides {
        @Override
        public @Nullable BakedModel resolve(final BakedModel model, final ItemStack stack, final @Nullable ClientLevel level,
                                            final @Nullable LivingEntity entity, final int seed) {
            return ShapeItemModels.resolve(model, stack);
        }
    }
}
