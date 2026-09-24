package dev.fallingcloud.slate.building.neoforge.compat;

import dev.fallingcloud.slate.building.block.ShapeBehaviour;
import dev.fallingcloud.slate.building.variant.Shape;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import dev.fallingcloud.slate.building.variant.Variant;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * KleeSlabs (DF pack, {@code mode = ALWAYS}) breaks only the half of a double slab you look at: inside the break event
 * it spawns {@code new ItemStack(Item.byBlock(block))} for that half (only when {@code player.hasCorrectToolForDrops})
 * and sets the single state, then cancels the event. Two consequences for the unified economy are settled here, after
 * everyone else ({@link EventPriority#LOWEST}, cancelled events included):
 * <ul>
 *   <li><b>Native double slabs</b> ({@code oak_slab}, ...): the half dropped as the native slab item; like every other
 *       broken variant it becomes the material item (one unit), which is what the loot hook gives for a full break.</li>
 *   <li><b>Our double slabs / vertical slabs</b>: {@code ShapeBehaviour.healSplitDrops} already turned the drop into
 *       the material. But our blocks carry no tool requirement of their own (it depends on the material), so KleeSlabs'
 *       harvest check always passes and a half would drop even when mined by hand. Such drops are removed when the
 *       player could not harvest the material, matching a full break.</li>
 * </ul>
 * Only item entities spawned during this event (age 0) inside the block are touched.
 */
final class KleeSlabsCompat {

    /** Dev harness switch, to show the behaviour without this compat. */
    static volatile boolean enabled = true;

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, BlockEvent.BreakEvent.class, KleeSlabsCompat::afterBreak);
    }

    private static void afterBreak(final BlockEvent.BreakEvent event) {
        if (!enabled || !event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        final Player player = event.getPlayer();
        if (player == null || player.getAbilities().instabuild) return;
        final BlockPos pos = event.getPos();
        final BlockState before = event.getState();
        final BlockState after = level.getBlockState(pos);
        if (!after.is(before.getBlock())) return;

        if (before.getBlock() instanceof ShapeBlock shape) {
            if (shape.units(after) >= shape.units(before) || ShapeBehaviour.canHarvest(level, pos, player)) return;
            final BlockState material = ShapeBlock.material(level, pos);
            final Item materialItem = material == null ? Items.AIR : material.getBlock().asItem();
            final Item shapeItem = before.getBlock().asItem();
            forFreshDrops(level, pos, stack -> stack.is(shapeItem) || (materialItem != Items.AIR && stack.is(materialItem)), ItemEntity::discard);
            return;
        }

        if (!(before.getBlock() instanceof SlabBlock) || !before.hasProperty(SlabBlock.TYPE)
            || before.getValue(SlabBlock.TYPE) != SlabType.DOUBLE || after.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) return;
        final VariantRegistry registry = VariantRegistry.get();
        if (!registry.unifies()) return;
        final Item slabItem = before.getBlock().asItem();
        final Optional<Variant> variant = registry.identify(new ItemStack(slabItem));
        if (variant.isEmpty() || variant.get().shape() != Shape.SLAB) return;
        final Item materialItem = variant.get().material().asItem();
        if (materialItem == Items.AIR) return;
        forFreshDrops(level, pos, stack -> stack.is(slabItem),
            drop -> drop.setItem(new ItemStack(materialItem, drop.getItem().getCount())));
    }

    private static void forFreshDrops(final ServerLevel level, final BlockPos pos, final Predicate<ItemStack> match,
                                      final java.util.function.Consumer<ItemEntity> action) {
        for (final ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(0.5),
            e -> e.tickCount == 0 && e.isAlive() && match.test(e.getItem()))) {
            action.accept(drop);
        }
    }

    private KleeSlabsCompat() {}
}
