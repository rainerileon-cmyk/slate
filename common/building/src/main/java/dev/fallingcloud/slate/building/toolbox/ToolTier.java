package dev.fallingcloud.slate.building.toolbox;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Tool tiers 1..4 and their item-name prefix ({@code slate_building:<id>_<tool>}). {@link #level()} is the number
 * the rest of the mod compares ({@code BuildMode.minTier()}, {@code Capabilities.tier(ToolType)}); tier-indexed
 * server settings arrays are read with {@link #index(int[], int)}. Lang: {@code slate_building.tier.<id>}.
 *
 * <p>Skeleton-declared (design §8); owner E may add behaviour (repair items etc.), never reorder.
 */
public enum ToolTier {
    COPPER(1, 250),
    IRON(2, 750),
    DIAMOND(3, 2000),
    NETHERITE(4, 5000);

    public static final int MIN = 1;
    public static final int MAX = 4;

    private static final ToolTier[] VALUES = values();

    private final int level;
    private final int durability;
    private final String id = name().toLowerCase(Locale.ROOT);

    ToolTier(final int level, final int durability) {
        this.level = level;
        this.durability = durability;
    }

    /** 1 (copper) .. 4 (netherite). */
    public int level() { return level; }

    /** Default item durability (the registered max damage). */
    public int durability() { return durability; }

    public String id() { return id; }

    public Component displayName() { return Component.translatable("slate_building.tier." + id); }

    /** The tier of a level, clamped into 1..4. */
    public static ToolTier byLevel(final int level) {
        return VALUES[Mth.clamp(level, MIN, MAX) - 1];
    }

    public static @Nullable ToolTier byId(final @Nullable String id) {
        if (id == null) return null;
        final String lower = id.toLowerCase(Locale.ROOT);
        for (final ToolTier t : VALUES) if (t.id.equals(lower)) return t;
        return null;
    }

    /**
     * Reads a tier-indexed settings array ({@code [tier1, tier2, tier3, tier4]}) for {@code level}; levels are
     * clamped and a short/empty array falls back to its last entry / 0, so a hand-edited config never crashes.
     */
    public static int index(final int[] perTier, final int level) {
        if (perTier == null || perTier.length == 0) return 0;
        return perTier[Math.min(Mth.clamp(level, MIN, MAX), perTier.length) - 1];
    }
}
