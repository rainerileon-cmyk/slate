package dev.fallingcloud.slate.building.ops.server;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Drops of blocks an operation removes: the block's own loot table, rolled with an unenchanted vanilla tool of the
 * player's hammer tier (copper = stone, iron, diamond, netherite), the right kind for the block (pickaxe, axe, shovel
 * or hoe). No enchantments, so no free silk touch or fortune. Blocks that tool cannot harvest are not broken at all.
 */
public final class Drops {

    private static final Item[][] TOOLS = {
        {Items.STONE_PICKAXE, Items.STONE_AXE, Items.STONE_SHOVEL, Items.STONE_HOE},
        {Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE},
        {Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL, Items.DIAMOND_HOE},
        {Items.NETHERITE_PICKAXE, Items.NETHERITE_AXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE},
    };

    /** The tool a hammer of {@code tier} (0 = no hammer: bare hands) breaks {@code state} with. */
    public static ItemStack tool(final int tier, final BlockState state) {
        if (tier <= 0) return ItemStack.EMPTY;
        final Item[] set = TOOLS[Math.min(tier, TOOLS.length) - 1];
        for (final Item item : set) {
            final ItemStack stack = new ItemStack(item);
            if (stack.isCorrectToolForDrops(state)) return stack;
        }
        return new ItemStack(set[0]);
    }

    /** Whether breaking {@code state} with {@code tool} yields its drops (blocks that need the right tool). */
    public static boolean canHarvest(final BlockState state, final ItemStack tool) {
        return !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
    }

    static List<ItemStack> of(final ServerLevel level, final BlockPos pos, final BlockState state, final @Nullable BlockEntity be,
                              final ServerPlayer player, final ItemStack tool) {
        return Block.getDrops(state, level, pos, be, player, tool);
    }

    private Drops() {}
}
