package dev.fallingcloud.slate.building.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * In-world overlays for selections and modes (design §6): glowing boxes, markers, lines, mirror planes and labels.
 * Immediate mode like {@link GhostRenderer}: submit per frame, drawn once in
 * {@code SlateRenderEvents.AFTER_TRANSLUCENT}. Coordinates are world space; colours ARGB (use
 * {@code Theme.current().palette().accent()} for the default accent).
 *
 * <p>Owner: B (render). Skeleton stub with the final API: submissions are accepted and dropped.
 */
public final class OverlayRenderer {

    /** A box with glowing (optionally animated) edges and faint faces, e.g. an area selection. */
    public static void box(final AABB box, final int argb, final boolean animated) {
    }

    /** A block-sized marker, e.g. a selection anchor. */
    public static void marker(final BlockPos pos, final int argb) {
    }

    /** A line between two points, e.g. the line mode preview or a measure. */
    public static void line(final Vec3 from, final Vec3 to, final int argb) {
    }

    /** A plane perpendicular to {@code axis} at {@code coord}, clipped to {@code bounds}, drawn with a grid (mirror planes). */
    public static void plane(final Direction.Axis axis, final double coord, final AABB bounds, final int argb) {
    }

    /** A camera-facing, readable text label at {@code pos}. */
    public static void label(final Vec3 pos, final Component text, final int argb) {
    }

    private OverlayRenderer() {}
}
