package dev.fallingcloud.slate.building.toolbox;

import dev.fallingcloud.slate.building.client.BuildingIcons;
import dev.fallingcloud.slate.core.gfx.Icon;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Toolbox upgrades (design §8). Item ids are {@code slate_building:<id>_upgrade}; lang
 * {@code slate_building.upgrade.<id>} (+ {@code .desc}).
 * <ul>
 *   <li>REACH: longer anchor reach for area modes.</li>
 *   <li>CAPACITY: larger volume / span limits.</li>
 *   <li>SPEED: more blocks per tick.</li>
 *   <li>MEMORY: deeper undo history.</li>
 *   <li>SUPPLY_LINK: pull materials from a linked container.</li>
 *   <li>MAGNET: refunds and drops go to the pouch / linked container first.</li>
 *   <li>EFFICIENCY: halves tool durability use.</li>
 * </ul>
 * Skeleton-declared; owner E. Never reorder or rename.
 */
public enum UpgradeType {
    REACH, CAPACITY, SPEED, MEMORY, SUPPLY_LINK, MAGNET, EFFICIENCY;

    private static final UpgradeType[] VALUES = values();

    private final String id = name().toLowerCase(Locale.ROOT);

    public String id() { return id; }

    /** Registry path of the upgrade item. */
    public String itemPath() { return id + "_upgrade"; }

    public Component displayName() { return Component.translatable("slate_building.upgrade." + id); }

    public Component description() { return Component.translatable("slate_building.upgrade." + id + ".desc"); }

    public Icon icon() { return BuildingIcons.upgrade(this); }

    public static @Nullable UpgradeType byId(final @Nullable String id) {
        if (id == null) return null;
        final String lower = id.toLowerCase(Locale.ROOT);
        for (final UpgradeType t : VALUES) if (t.id.equals(lower)) return t;
        return null;
    }
}
