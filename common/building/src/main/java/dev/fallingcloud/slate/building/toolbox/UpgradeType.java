package dev.fallingcloud.slate.building.toolbox;

import dev.fallingcloud.slate.building.client.BuildingIcons;
import dev.fallingcloud.slate.core.gfx.Icon;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Toolbox upgrades (design §8). Item ids are {@code slate_building:<id>_upgrade}; lang
 * {@code slate_building.upgrade.<id>} (+ {@code .desc}). Each upgrade slot holds one item; several of one type count as
 * levels up to {@link #maxLevel()} (a toolbox refuses more).
 * <ul>
 *   <li>REACH: anchor reach bonus x2 per level (at least +8 blocks per level), up to 2.</li>
 *   <li>CAPACITY: operation volume x2 and span +50% per level, up to 2.</li>
 *   <li>SPEED: blocks per tick x2 per level, up to 2.</li>
 *   <li>MEMORY: {@code undoPerMemory} more undo steps per level, up to 4.</li>
 *   <li>SUPPLY_LINK: pull materials from a linked container.</li>
 *   <li>MAGNET: refunds and drops go to the pouch / linked container first.</li>
 *   <li>EFFICIENCY: halves tool durability use.</li>
 * </ul>
 * Skeleton-declared; owner E. Never reorder or rename.
 */
public enum UpgradeType {
    REACH(2, 0xFF5CC46A), CAPACITY(2, 0xFFE59A45), SPEED(2, 0xFFF2CC3A), MEMORY(4, 0xFFA884E8),
    SUPPLY_LINK(1, 0xFF45C8D6), MAGNET(1, 0xFFE0525A), EFFICIENCY(1, 0xFF4FD08A);

    private static final UpgradeType[] VALUES = values();

    private final String id = name().toLowerCase(Locale.ROOT);
    private final int maxLevel;
    private final int color;

    UpgradeType(final int maxLevel, final int color) {
        this.maxLevel = maxLevel;
        this.color = color;
    }

    public String id() { return id; }

    /** Registry path of the upgrade item. */
    public String itemPath() { return id + "_upgrade"; }

    /** How many of this upgrade count (and fit) in one toolbox. */
    public int maxLevel() { return maxLevel; }

    /** The colour of the upgrade card's stripe (ARGB), reused by the toolbox screen's readout. */
    public int color() { return color; }

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
