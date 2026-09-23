package dev.fallingcloud.slate.building.toolbox;

import dev.fallingcloud.slate.building.ops.ToolType;
import net.minecraft.world.item.Item;

/**
 * One building tool, e.g. {@code slate_building:iron_hammer}: a {@link ToolType} at a {@link ToolTier}. Tools unlock
 * building modes while they sit in a toolbox the player carries, lose durability as operations run and are repaired
 * in an anvil with the tier material.
 *
 * <p>Owner: E (toolbox). Skeleton placeholder; {@code BuildingItems} registers 24 of these (6 types x 4 tiers) with
 * {@code stacksTo(1)} and the tier's durability. Keep the constructor signature and the two accessors.
 */
public class BuildingToolItem extends Item {

    private final ToolType type;
    private final ToolTier tier;

    public BuildingToolItem(final ToolType type, final ToolTier tier, final Item.Properties properties) {
        super(properties);
        this.type = type;
        this.tier = tier;
    }

    public ToolType type() { return type; }

    public ToolTier tier() { return tier; }

    /** Shorthand for {@code tier().level()} (1..4). */
    public int level() { return tier.level(); }
}
