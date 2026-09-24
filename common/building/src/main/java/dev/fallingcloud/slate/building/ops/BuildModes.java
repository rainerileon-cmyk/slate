package dev.fallingcloud.slate.building.ops;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Every building mode (design §7), in build-menu order. Names and one-line descriptions come from lang
 * ({@code slate_building.mode.<id>} / {@code .desc}); each mode has its own keybind
 * ({@code key.slate_building.mode.<id>}, unbound by default).
 *
 * <p>Skeleton-declared: ids, kinds, tools, tiers and parameters are the contract between the client preview (D2),
 * the server planner/executor (D1), the build menu (C) and the toolbox unlocks (E). Tuning defaults is fine; ids
 * must stay stable.
 */
public final class BuildModes {

    private static final Map<String, BuildMode> BY_ID = new LinkedHashMap<>();

    // Parameters shared by several modes (one lang entry each).
    /** Which existing blocks may be overwritten: only air, air + replaceables (grass, water, snow...), or anything. */
    public static final ModeParam REPLACE = ModeParam.choice("replace", "REPLACEABLE", "AIR", "REPLACEABLE", "ALL");
    /** Where the placed blocks come from: the held stack, a random or a checkered mix of the hotbar. */
    public static final ModeParam PALETTE = ModeParam.choice("palette", "HELD", "HELD", "HOTBAR_RANDOM", "HOTBAR_CHECKER");
    public static final ModeParam HOLLOW = ModeParam.bool("hollow", false);
    public static final ModeParam INCLUDE_AIR = ModeParam.bool("includeAir", false);
    /** Clockwise rotation in degrees of a paste / move. */
    public static final ModeParam ROTATION = ModeParam.choice("rotation", "0", "0", "90", "180", "270");
    /** Mirror of a paste / move across the X or Z axis. */
    public static final ModeParam MIRROR = ModeParam.choice("mirror", "NONE", "NONE", "X", "Z");
    /** Whether symmetry also replays block breaking. */
    public static final ModeParam MIRROR_BREAKING = ModeParam.bool("mirrorBreaking", true);

    private static ModeParam thickness(final int max) {
        return ModeParam.integer("thickness", 1, 1, max);
    }

    public static final BuildMode FILL = add("fill", ModeKind.AREA, ToolType.TROWEL, 1, true, true, REPLACE, PALETTE);
    public static final BuildMode WALLS = add("walls", ModeKind.AREA, ToolType.TROWEL, 1, true, true, thickness(4), REPLACE, PALETTE);
    public static final BuildMode LINE = add("line", ModeKind.AREA, ToolType.TROWEL, 1, true, true, thickness(3), PALETTE);
    /** Construction-wand extension; the max number of blocks comes from the trowel tier (16/64/256/1024). */
    public static final BuildMode EXTEND = add("extend", ModeKind.POINT, ToolType.TROWEL, 1, true, false,
        ModeParam.choice("match", "BLOCK", "EXACT", "BLOCK", "ANY"),
        ModeParam.choice("lock", "NONE", "NONE", "HORIZONTAL", "VERTICAL"));
    public static final BuildMode HOLLOW_BOX = add("hollow", ModeKind.AREA, ToolType.TROWEL, 2, true, true, thickness(4), REPLACE, PALETTE);
    public static final BuildMode OUTLINE = add("outline", ModeKind.AREA, ToolType.TROWEL, 2, true, true, REPLACE, PALETTE);
    /** A = base centre, B = radius + height. */
    public static final BuildMode CYLINDER = add("cylinder", ModeKind.AREA, ToolType.TROWEL, 2, true, true, HOLLOW, PALETTE, REPLACE);
    /** A = centre, B = a point on the surface. */
    public static final BuildMode SPHERE = add("sphere", ModeKind.AREA, ToolType.TROWEL, 3, true, true, HOLLOW,
        ModeParam.choice("part", "FULL", "FULL", "DOME", "BOWL"), PALETTE, REPLACE);
    /** Keeps shape and orientation when both the old block and the held one are variants ({@code keepShape}). */
    public static final BuildMode REPLACE_BLOCKS = add("replace", ModeKind.AREA, ToolType.BRUSH, 1, true, true,
        ModeParam.choice("filter", "CLICKED", "CLICKED", "ANY_SOLID", "OFFHAND"),
        ModeParam.bool("keepShape", true));
    public static final BuildMode OVERLAY = add("overlay", ModeKind.AREA, ToolType.BRUSH, 2, true, true,
        ModeParam.integer("depth", 1, 1, 3),
        ModeParam.choice("mode", "ON_TOP", "ON_TOP", "REPLACE_TOP"));
    /** Drops go to the inventory. */
    public static final BuildMode CLEAR = add("clear", ModeKind.AREA, ToolType.HAMMER, 1, false, true,
        ModeParam.choice("filter", "ALL", "ALL", "CLICKED", "PLANTS"),
        ModeParam.bool("keepFluids", true));
    /** Changes the shape of every variant / material block to the held shape, keeping each block's material. */
    public static final BuildMode RESHAPE = add("reshape", ModeKind.AREA, ToolType.HAMMER, 2, true, true,
        ModeParam.choice("filter", "ALL", "ALL", "CLICKED_MATERIAL"));
    /** No block-entity contents in survival. */
    public static final BuildMode COPY = add("copy", ModeKind.AREA, ToolType.BLUEPRINT, 1, false, false, INCLUDE_AIR);
    public static final BuildMode PASTE = add("paste", ModeKind.POINT, ToolType.BLUEPRINT, 1, true, true, ROTATION, MIRROR, INCLUDE_AIR, REPLACE);
    public static final BuildMode CUT = add("cut", ModeKind.AREA, ToolType.BLUEPRINT, 2, false, true);
    public static final BuildMode STACK = add("stack", ModeKind.AREA, ToolType.BLUEPRINT, 3, true, true,
        ModeParam.integer("count", 2, 1, 16),
        ModeParam.integer("spacing", 0, 0, 8),
        ModeParam.choice("direction", "LOOK", "LOOK", "UP", "DOWN", "N", "S", "E", "W"));
    public static final BuildMode MOVE = add("move", ModeKind.MOVE, ToolType.BLUEPRINT, 3, true, true, ROTATION, MIRROR);
    /** Mirror plane through the centre; its radius comes from the square tier (16/32/64/128). */
    public static final BuildMode MIRROR_MODE = add("mirror", ModeKind.TOGGLE, ToolType.SQUARE, 1, true, true,
        ModeParam.choice("axis", "X", "X", "Z", "XZ"), MIRROR_BREAKING);
    public static final BuildMode RADIAL = add("radial", ModeKind.TOGGLE, ToolType.SQUARE, 3, true, true,
        ModeParam.integer("slices", 4, 2, 8), MIRROR_BREAKING);
    /** Normal placing and breaking at the corner reach of the building modes (the toolbox reach bonus, applied by the server as an attribute modifier while on). */
    public static final BuildMode EXTENDED = add("extended", ModeKind.REACH, null, 0, false, false);
    public static final BuildMode MEASURE = add("measure", ModeKind.MEASURE, null, 0, false, false);

    private static final List<BuildMode> ALL = Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));

    /** Every mode in build-menu order. */
    public static List<BuildMode> all() {
        return ALL;
    }

    public static @Nullable BuildMode byId(final @Nullable String id) {
        return id == null ? null : BY_ID.get(id);
    }

    private static BuildMode add(final String id, final ModeKind kind, final @Nullable ToolType tool, final int minTier,
                                 final boolean places, final boolean breaks, final ModeParam... params) {
        final BuildMode mode = new BuildMode(id, kind, tool, minTier, List.of(params), places, breaks);
        if (BY_ID.putIfAbsent(id, mode) != null) throw new IllegalStateException("Duplicate building mode " + id);
        return mode;
    }

    private BuildModes() {}
}
