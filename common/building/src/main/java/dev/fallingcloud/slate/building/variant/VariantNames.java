package dev.fallingcloud.slate.building.variant;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/**
 * Display names of variants, the way vanilla names its own: the material's name loses the words that only say "this
 * is the block form" before the shape is added, so grass block + stairs reads "Grass Stairs" (not "Grass Block
 * Stairs"), oak planks + vertical slab "Oak Vertical Slab" (as oak_stairs is "Oak Stairs"), stone bricks + wall
 * "Stone Brick Wall", deepslate tiles + step "Deepslate Tile Step" and block of quartz + slab "Quartz Slab".
 *
 * <p>The rules read the resolved English words; a name in another language is left whole (the shape is still
 * appended). The result is a literal, resolved in the language of the side that asks, which is the client for every
 * name a player sees (tooltips, the wheel, JEI); a server-side use (a chat message) resolves in the server's.
 */
public final class VariantNames {

    private VariantNames() {}

    /** "{material} {shape}" for a shape, e.g. "Grass Stairs"; the material's own name for FULL. */
    public static Component of(final Block material, final Shape shape) {
        if (shape == Shape.FULL) return material.getName();
        return Component.translatable("slate_building.variant_name", Component.literal(stem(material.getName().getString())), shape.displayName());
    }

    /** "Grass Block" → "Grass", "Oak Planks" → "Oak", "Stone Bricks" → "Stone Brick", "Block of Quartz" → "Quartz". */
    static String stem(final String name) {
        String s = name.trim();
        if (s.toLowerCase(Locale.ROOT).startsWith("block of ")) s = s.substring("block of ".length());
        s = stripSuffix(s, " Blocks");
        s = stripSuffix(s, " Block");
        s = stripSuffix(s, " Planks");
        if (endsWith(s, "Bricks") || endsWith(s, "Tiles")) s = s.substring(0, s.length() - 1);   // Bricks → Brick, Tiles → Tile
        s = s.trim();
        return s.isEmpty() ? name : s;
    }

    private static String stripSuffix(final String s, final String suffix) {
        return endsWith(s, suffix) ? s.substring(0, s.length() - suffix.length()) : s;
    }

    private static boolean endsWith(final String s, final String suffix) {
        return s.length() > suffix.length() && s.regionMatches(true, s.length() - suffix.length(), suffix, 0, suffix.length());
    }
}
