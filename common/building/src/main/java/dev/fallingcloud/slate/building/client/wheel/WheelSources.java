package dev.fallingcloud.slate.building.client.wheel;

import dev.fallingcloud.slate.building.chisel.ChiselGroups;
import dev.fallingcloud.slate.building.variant.Shape;
import dev.fallingcloud.slate.building.variant.Variant;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;

/**
 * Where the wheels get their knowledge of variants and chisel groups: {@link VariantRegistry} and
 * {@link ChiselGroups#client()}.
 */
public final class WheelSources {

    /** The questions the wheels ask. */
    public interface Source {
        Optional<Variant> identify(ItemStack stack);

        Optional<Variant> identify(BlockGetter level, BlockPos pos);

        boolean isAvailable(Block material, Shape shape);

        /** The stack to render / expect for (material, shape); may be empty. */
        ItemStack stackFor(Block material, Shape shape, int count);

        List<ChiselGroups.Group> chiselGroups(Block material);
    }

    /** The real registries. */
    public static final Source LIVE = new Source() {
        @Override public Optional<Variant> identify(final ItemStack stack) { return VariantRegistry.get().identify(stack); }

        @Override public Optional<Variant> identify(final BlockGetter level, final BlockPos pos) { return VariantRegistry.get().identify(level, pos); }

        @Override public boolean isAvailable(final Block material, final Shape shape) { return VariantRegistry.get().isAvailable(material, shape); }

        @Override public ItemStack stackFor(final Block material, final Shape shape, final int count) {
            return VariantRegistry.get().stackFor(material, shape, Math.max(1, count));
        }

        @Override public List<ChiselGroups.Group> chiselGroups(final Block material) { return ChiselGroups.client().groupsOf(material); }
    };

    /** The source the UI uses: the real registries. */
    public static Source get() {
        return LIVE;
    }

    private WheelSources() {}
}
