package dev.fallingcloud.slate.building.variant;

import net.minecraft.world.phys.AABB;

/**
 * A render box that is not axis-aligned: a diagonal arm of a fence, wall or pane. {@code box} (block pixels, 0..16)
 * is the arm as it would be pointing NORTH; the baker cuts the material's quads to it like any box, then stretches
 * them by √2 along Z about the block centre (so the arm reaches the block corner, the way the Diagonal mods' models
 * rescale theirs) and turns them by {@code yaw} degrees about the block's vertical axis.
 */
public record RotatedBox(AABB box, float yaw) {}
