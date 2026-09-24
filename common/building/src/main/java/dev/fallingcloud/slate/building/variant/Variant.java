package dev.fallingcloud.slate.building.variant;

import net.minecraft.world.level.block.Block;

/**
 * One material in one shape, e.g. (oak planks, STAIRS). {@code material} is always the FULL block (the thing a
 * variant drops and is paid with); a native block such as {@code minecraft:oak_stairs} and Slate Building's own
 * {@code slate_building:stairs} holding oak planks both identify as the same Variant.
 *
 * <p>Owner: A (variants). Record shape is part of the contract; add methods, do not change the components.
 */
public record Variant(Block material, Shape shape) {

    /** The material itself as a variant (shape FULL). */
    public static Variant full(final Block material) {
        return new Variant(material, Shape.FULL);
    }

    public boolean isFull() { return shape == Shape.FULL; }

    public Variant withShape(final Shape newShape) {
        return new Variant(material, newShape);
    }
}
