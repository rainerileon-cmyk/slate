package dev.fallingcloud.slate.building.toolbox;

import net.minecraft.world.item.Item;

/**
 * A toolbox upgrade, e.g. {@code slate_building:memory_upgrade}. Upgrades only act while inside a toolbox's upgrade
 * slots; stacked upgrades of one type count as levels ({@code Capabilities.upgrades()}).
 *
 * <p>Owner: E (toolbox). Skeleton placeholder; keep the constructor signature and {@link #type()}.
 */
public class UpgradeItem extends Item {

    private final UpgradeType type;

    public UpgradeItem(final UpgradeType type, final Item.Properties properties) {
        super(properties);
        this.type = type;
    }

    public UpgradeType type() { return type; }
}
