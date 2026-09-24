package dev.fallingcloud.slate.building.fabric.render;

import dev.fallingcloud.slate.building.client.model.ShapeItemModels;
import java.util.List;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The item model of every shape item on Fabric: its JSON model (display transforms + placeholder cube) with
 * overrides that resolve the stack's material into a cached per-material model ({@link ShapeItemModels}); those
 * are plain vanilla models, so items take the vanilla item path.
 */
final class FabricShapeItemModel extends ForwardingBakedModel {

    private static final ItemOverrides OVERRIDES = new Overrides();

    FabricShapeItemModel(final BakedModel json) {
        this.wrapped = json;
    }

    @Override
    public ItemOverrides getOverrides() {
        return OVERRIDES;
    }

    /**
     * Vanilla's constructor with no overrides touches neither the baker nor the model, so nulls are safe here (Fabric
     * keeps the no-argument constructor private).
     */
    private static final class Overrides extends ItemOverrides {
        Overrides() {
            super(null, null, List.of());
        }

        @Override
        public @Nullable BakedModel resolve(final BakedModel model, final ItemStack stack, final @Nullable ClientLevel level,
                                            final @Nullable LivingEntity entity, final int seed) {
            return ShapeItemModels.resolve(model, stack);
        }
    }
}
