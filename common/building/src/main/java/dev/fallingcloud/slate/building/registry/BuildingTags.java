package dev.fallingcloud.slate.building.registry;

import dev.fallingcloud.slate.building.SlateBuilding;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Slate Building's tags (data files: {@code data/slate_building/tags/{item,block}/}). */
public final class BuildingTags {

    /** Items the BetterInventory toolbox slot accepts. */
    public static final TagKey<Item> TOOLBOXES = TagKey.create(Registries.ITEM, SlateBuilding.id("toolboxes"));
    /** Every building tool. */
    public static final TagKey<Item> TOOLS = TagKey.create(Registries.ITEM, SlateBuilding.id("tools"));
    /** Every toolbox upgrade. */
    public static final TagKey<Item> UPGRADES = TagKey.create(Registries.ITEM, SlateBuilding.id("upgrades"));

    /** Forces a block to count as a material even when the automatic rules (full cube, model, item, ...) reject it. */
    public static final TagKey<Block> MATERIAL = TagKey.create(Registries.BLOCK, SlateBuilding.id("material"));
    /** Never a material (bedrock, barrier, command/structure blocks, spawner, ...). */
    public static final TagKey<Block> NOT_MATERIAL = TagKey.create(Registries.BLOCK, SlateBuilding.id("not_material"));

    private BuildingTags() {}
}
