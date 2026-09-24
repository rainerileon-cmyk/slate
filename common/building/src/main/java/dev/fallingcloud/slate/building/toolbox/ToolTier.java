package dev.fallingcloud.slate.building.toolbox;

import java.util.Locale;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Tool tiers 1..4 and their item-name prefix ({@code slate_building:<id>_<tool>}). {@link #level()} is the number
 * the rest of the mod compares ({@code BuildMode.minTier()}, {@code Capabilities.tier(ToolType)}); tier-indexed
 * server settings arrays are read with {@link #index(int[], int)}. Lang: {@code slate_building.tier.<id>}.
 *
 * <p>Skeleton-declared (design §8); owner E adds the repair material, the tier colour and enchantability. Never reorder.
 */
public enum ToolTier {
    COPPER(1, 250, "ingots/copper", Items.COPPER_INGOT, 0xFFE0885A, 13),
    IRON(2, 750, "ingots/iron", Items.IRON_INGOT, 0xFFD6D8DD, 14),
    DIAMOND(3, 2000, "gems/diamond", Items.DIAMOND, 0xFF4DE0D0, 10),
    NETHERITE(4, 5000, "ingots/netherite", Items.NETHERITE_INGOT, 0xFF9C8990, 15);

    public static final int MIN = 1;
    public static final int MAX = 4;

    private static final ToolTier[] VALUES = values();

    private final int level;
    private final int durability;
    private final TagKey<Item> repairTag;
    private final Item repairItem;
    private final int color;
    private final int enchantability;
    private final String id = name().toLowerCase(Locale.ROOT);

    ToolTier(final int level, final int durability, final String repairTag, final Item repairItem, final int color, final int enchantability) {
        this.level = level;
        this.durability = durability;
        this.repairTag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", repairTag));
        this.repairItem = repairItem;
        this.color = color;
        this.enchantability = enchantability;
    }

    /** 1 (copper) .. 4 (netherite). */
    public int level() { return level; }

    /** Default item durability (the registered max damage). */
    public int durability() { return durability; }

    public String id() { return id; }

    public Component displayName() { return Component.translatable("slate_building.tier." + id); }

    /** The tier's signature colour (ARGB): tooltip pips, the toolbox screen's tier meters. */
    public int color() { return color; }

    /** Enchanting-table value (Unbreaking is the only table enchantment building tools take). */
    public int enchantability() { return enchantability; }

    /** Whether {@code stack} repairs a tool of this tier in an anvil (the {@code c:} material tag, or the vanilla item). */
    public boolean isRepairMaterial(final ItemStack stack) {
        return stack.is(repairTag) || stack.is(repairItem);
    }

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
