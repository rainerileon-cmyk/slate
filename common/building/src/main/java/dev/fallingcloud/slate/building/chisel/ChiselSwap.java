package dev.fallingcloud.slate.building.chisel;

import dev.fallingcloud.slate.building.block.ShapeBlockEntity;
import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.config.ServerChisel;
import dev.fallingcloud.slate.building.ops.ToolType;
import dev.fallingcloud.slate.building.registry.BuildingBlocks;
import dev.fallingcloud.slate.building.registry.RegistryRef;
import dev.fallingcloud.slate.building.toolbox.ToolTier;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.building.variant.Shape;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import dev.fallingcloud.slate.building.variant.Variant;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * What a chisel does, shared by the server's actions and the client's wheels so both agree: who may chisel, what a
 * held stack or a placed block becomes, and which group members a wheel should offer.
 *
 * <p>A chisel swap changes the MATERIAL and keeps the SHAPE: a held stone slab chiselled to stone bricks becomes a stone
 * brick slab (the native one where it exists, else Slate Building's own slab holding stone bricks), count unchanged.
 * Variants come from {@link VariantRegistry}; a plain block item of a group member is that member as a full block.
 *
 * <p>Owner: I (chisel). Wheels (owner C) use {@link #heldLock}, {@link #pages} and send {@code ChiselHeld}.
 */
public final class ChiselSwap {

    /** Chisel tier needed to chisel held stacks. */
    public static final int HELD_TIER = 1;
    /** Chisel tier needed to chisel blocks in the world. */
    public static final int WORLD_TIER = 2;

    private ChiselSwap() {}

    // ------------------------------------------------------------------ permission

    /** Why {@code p} cannot chisel held stacks right now ("Needs: Copper Chisel"), or null when they can. Both sides. */
    public static @Nullable Component heldLock(final Player p) {
        final ServerChisel rules = BuildingServerSettings.effective(p).chisel();
        if (!rules.enabled) return Component.translatable("slate_building.chisel.lock.disabled");
        return ToolboxAccess.of(p).tier(ToolType.CHISEL) >= HELD_TIER ? null : needs(HELD_TIER);
    }

    /** Why {@code p} cannot chisel blocks in the world right now, or null when they can. Both sides. */
    public static @Nullable Component inWorldLock(final Player p) {
        final ServerChisel rules = BuildingServerSettings.effective(p).chisel();
        if (!rules.enabled) return Component.translatable("slate_building.chisel.lock.disabled");
        if (!rules.inWorld) return Component.translatable("slate_building.chisel.lock.in_world_disabled");
        return ToolboxAccess.of(p).tier(ToolType.CHISEL) >= WORLD_TIER ? null : needs(WORLD_TIER);
    }

    private static Component needs(final int tier) {
        return Component.translatable("slate_building.lock.needs_tool",
            Component.translatable("slate_building.tool_tiered", ToolTier.byLevel(tier).displayName(), ToolType.CHISEL.displayName()));
    }

    // ------------------------------------------------------------------ identification

    /** The material and shape a stack stands for, as far as the chisel is concerned. */
    public static Optional<Variant> identify(final ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        final Optional<Variant> v = VariantRegistry.get().identify(stack);
        if (v.isPresent()) return v;
        if (!(stack.getItem() instanceof BlockItem item)) return Optional.empty();
        final Block block = item.getBlock();
        if (block instanceof ShapeBlock) return Optional.empty();       // a shape item without a (known) material
        return Optional.of(Variant.full(block));
    }

    /** The material and shape of a placed block. */
    public static Optional<Variant> identify(final BlockState state, final @Nullable BlockEntity be) {
        if (state.isAir()) return Optional.empty();
        final Optional<Variant> v = VariantRegistry.get().identify(state, be);
        if (v.isPresent()) return v;
        final Block block = state.getBlock();
        if (block instanceof ShapeBlock) return Optional.empty();
        return Optional.of(Variant.full(block));
    }

    /** Material units a placed block is worth (2 for a double slab, n for n layers, else 1). */
    public static int units(final BlockState state, final @Nullable BlockEntity be) {
        return VariantRegistry.get().units(state, be);
    }

    /** The native block realising ({@code material}, {@code shape}), or null (FULL is the material itself). */
    public static @Nullable Block nativeBlock(final Block material, final Shape shape) {
        if (shape == Shape.FULL) return material;
        final Block b = VariantRegistry.get().nativeBlock(material, shape);
        return b;
    }

    /** {@code count} of (material, shape) as items: the native item, else our shape item; EMPTY when that shape does not exist. */
    public static ItemStack stackFor(final Block material, final Shape shape, final int count) {
        final ItemStack s = VariantRegistry.get().stackFor(material, shape, count);
        if (!s.isEmpty()) return s;
        if (shape == Shape.FULL) return material.asItem() == Items.AIR ? ItemStack.EMPTY : new ItemStack(material, count);
        return ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------ held stacks

    /**
     * What {@code held} becomes when chiselled into {@code target}: same shape, same count. EMPTY when the stack is not
     * a variant, the two are not in one group, or {@code target} has no such shape.
     */
    public static ItemStack result(final ChiselGroups groups, final ItemStack held, final Block target) {
        final Variant v = identify(held).orElse(null);
        if (v == null || !groups.linked(v.material(), target)) return ItemStack.EMPTY;
        if (v.material() == target) return held.copy();
        return stackFor(target, v.shape(), held.getCount());
    }

    /**
     * One wheel page for a held stack.
     *
     * @param group   the group (its name is the page title)
     * @param options the members that exist in the held shape, in group order
     */
    public record Page(ChiselGroups.Group group, List<Option> options) {}

    /**
     * One slice.
     *
     * @param member  the group member (the material to send in {@code ChiselHeld})
     * @param stack   a one-item preview of the result (the variant the held stack would become)
     * @param current whether this is the held stack's own material
     */
    public record Option(Block member, ItemStack stack, boolean current) {}

    /**
     * The chisel pages for {@code held}: one per group of its material, each listing the members that exist in the
     * held shape (a slab page only offers materials with a slab). Pages with nothing else to pick are left out.
     */
    public static List<Page> pages(final ChiselGroups groups, final ItemStack held) {
        final Variant v = identify(held).orElse(null);
        if (v == null) return List.of();
        final List<Page> pages = new ArrayList<>();
        for (final ChiselGroups.Group g : groups.groupsOf(v.material())) {
            final List<Option> options = new ArrayList<>(g.members().size());
            for (final Block member : g.members()) {
                final ItemStack preview = stackFor(member, v.shape(), 1);
                if (!preview.isEmpty()) options.add(new Option(member, preview, member == v.material()));
            }
            if (options.size() >= 2) pages.add(new Page(g, List.copyOf(options)));
        }
        return pages;
    }

    // ------------------------------------------------------------------ placed blocks

    /**
     * The result of chiselling a placed block.
     *
     * @param state    the block state to set (shared properties copied: facing, half, axis, waterlogged ...)
     * @param material for Slate Building's own shape blocks: the material to store in the block entity, else null
     */
    public record WorldResult(BlockState state, @Nullable BlockState material) {}

    /**
     * What the placed {@code old} (identified as {@code v}) becomes as {@code target}, keeping its shape and
     * orientation; null when {@code target} has no such shape.
     */
    public static @Nullable WorldResult inWorld(final BlockState old, final @Nullable BlockEntity be, final Variant v, final Block target) {
        if (v.isFull()) return new WorldResult(ChiselRules.copyShared(old, target.defaultBlockState()), null);
        final Block nativeShape = nativeBlock(target, v.shape());
        if (nativeShape != null && !(nativeShape instanceof ShapeBlock)) {
            return new WorldResult(ChiselRules.copyShared(old, nativeShape.defaultBlockState()), null);
        }
        if (!VariantRegistry.get().isAvailable(target, v.shape())) return null;
        final RegistryRef<? extends Block> ours = BuildingBlocks.forShape(v.shape());
        if (ours == null || !ours.isBound()) return null;
        final Block shapeBlock = ours.get();
        final BlockState state = old.getBlock() == shapeBlock ? old : ChiselRules.copyShared(old, shapeBlock.defaultBlockState());
        final BlockState oldMaterial = be instanceof ShapeBlockEntity shaped ? shaped.material() : null;
        final BlockState material = oldMaterial != null
            ? ChiselRules.copyShared(oldMaterial, target.defaultBlockState())
            : target.defaultBlockState();
        return new WorldResult(state, material);
    }

    /**
     * Whether {@code state} at {@code pos} drops exactly what it is worth in its own material, with no tool: the
     * material (or a variant of it) totalling {@code units}. Stone drops cobblestone, glass drops nothing, leaves drop
     * saplings: chiselling those in place would skip their loot table (free silk touch), so it is refused.
     */
    public static boolean dropsOwnWorth(final ServerLevel level, final BlockPos pos, final BlockState state, final @Nullable BlockEntity be,
                                        final Block material, final int units) {
        final List<ItemStack> drops;
        try {
            drops = Block.getDrops(state, level, pos, be);
        } catch (final RuntimeException e) {
            return false;
        }
        int total = 0;
        for (final ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            final Variant v = identify(drop).orElse(null);
            if (v == null || v.material() != material) return false;
            total += drop.getCount();
        }
        return total == units;
    }
}
