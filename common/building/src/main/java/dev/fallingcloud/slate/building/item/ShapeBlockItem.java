package dev.fallingcloud.slate.building.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * The item of one shape block. The material is NOT part of the item: it rides on the stack in the
 * {@code slate_building:material} data component (a block id, set at runtime, never in {@code Item.Properties}).
 * Placing copies it into the block entity (vanilla {@code BlockItem.place} applies the stack's components to the
 * block entity, see {@code ShapeBlockEntity}).
 *
 * <p>Owner: A (variants). Skeleton placeholder with vanilla BlockItem behaviour; A adds the name
 * ({@code "<material> <shape>"}), refusing to place without a material ("No material"), and placement via the
 * material-aware rules of design §2. Keep the constructor signature ({@code BuildingItems} constructs it).
 */
public class ShapeBlockItem extends BlockItem {

    public ShapeBlockItem(final Block block, final Item.Properties properties) {
        super(block, properties);
    }
}
