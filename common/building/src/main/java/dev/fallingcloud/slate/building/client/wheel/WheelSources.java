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
import org.jetbrains.annotations.Nullable;

/**
 * Where the wheels get their knowledge of variants and chisel groups: {@link VariantRegistry} and
 * {@link ChiselGroups#client()} in play. The dev harness can layer a {@link Source} on top (for screenshots before
 * every area is merged); the overlay asks the live source first, so a layered source only fills gaps.
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

    private static @Nullable Source fallback;

    /** The source the UI uses: live answers first, then the layered fallback (if any) for what the live one lacks. */
    public static Source get() {
        final Source fb = fallback;
        if (fb == null) return LIVE;
        return new Source() {
            @Override public Optional<Variant> identify(final ItemStack stack) {
                final Optional<Variant> v = LIVE.identify(stack);
                return v.isPresent() ? v : fb.identify(stack);
            }

            @Override public Optional<Variant> identify(final BlockGetter level, final BlockPos pos) {
                final Optional<Variant> v = LIVE.identify(level, pos);
                return v.isPresent() ? v : fb.identify(level, pos);
            }

            @Override public boolean isAvailable(final Block material, final Shape shape) {
                return LIVE.isAvailable(material, shape) || fb.isAvailable(material, shape);
            }

            @Override public ItemStack stackFor(final Block material, final Shape shape, final int count) {
                final ItemStack s = LIVE.stackFor(material, shape, count);
                return !s.isEmpty() ? s : fb.stackFor(material, shape, count);
            }

            @Override public List<ChiselGroups.Group> chiselGroups(final Block material) {
                final List<ChiselGroups.Group> g = LIVE.chiselGroups(material);
                return !g.isEmpty() ? g : fb.chiselGroups(material);
            }
        };
    }

    /** Dev harness only: answers for what the live registries do not know yet. Null removes it. */
    public static void layer(final @Nullable Source source) {
        fallback = source;
    }

    private WheelSources() {}
}
