package dev.fallingcloud.slate.building.client;

import dev.fallingcloud.slate.building.ops.ToolType;
import dev.fallingcloud.slate.building.toolbox.UpgradeType;
import dev.fallingcloud.slate.building.variant.Shape;
import dev.fallingcloud.slate.core.gfx.Icon;

/**
 * Every glyph Slate Building draws, in one place: the dedicated building glyphs of Core's atlas (shapes, modes, tools,
 * upgrades), plus a few general Core icons where a mode has no glyph of its own (copy, move). Always go through these
 * constants (or {@link #shape}/{@link #mode}/{@link #tool}/{@link #upgrade}),
 * never at {@code Icon} directly.
 *
 * <p>Pure constants over a plain enum: safe to reference from common code on a dedicated server (the enums'
 * {@code icon()} methods do).
 */
public final class BuildingIcons {

    // Shapes
    public static final Icon SHAPE_FULL = Icon.BLOCK;          // no SHAPE_FULL glyph: the isometric block
    public static final Icon SHAPE_STAIRS = Icon.SHAPE_STAIRS;
    public static final Icon SHAPE_SLAB = Icon.SHAPE_SLAB;
    public static final Icon SHAPE_VERTICAL_SLAB = Icon.SHAPE_VSLAB;
    public static final Icon SHAPE_VERTICAL_STAIRS = Icon.SHAPE_VSTAIRS;
    public static final Icon SHAPE_WALL = Icon.SHAPE_WALL;
    public static final Icon SHAPE_FENCE = Icon.SHAPE_FENCE;
    public static final Icon SHAPE_STEP = Icon.SHAPE_STEP;
    public static final Icon SHAPE_PANEL = Icon.SHAPE_PANEL;
    public static final Icon SHAPE_FENCE_GATE = Icon.SHAPE_GATE;
    public static final Icon SHAPE_VERTICAL_STEP = Icon.SHAPE_VSTEP;
    public static final Icon SHAPE_POST = Icon.SHAPE_POST;
    public static final Icon SHAPE_LAYER = Icon.SHAPE_LAYER;
    public static final Icon SHAPE_PANE = Icon.SHAPE_PANE;

    // Building modes
    public static final Icon MODE_FILL = Icon.MODE_FILL;
    public static final Icon MODE_WALLS = Icon.MODE_WALLS;
    public static final Icon MODE_LINE = Icon.MODE_LINE;
    public static final Icon MODE_EXTEND = Icon.MODE_EXTEND;
    public static final Icon MODE_HOLLOW = Icon.MODE_HOLLOW;
    public static final Icon MODE_OUTLINE = Icon.MODE_OUTLINE;
    public static final Icon MODE_CYLINDER = Icon.MODE_CYLINDER;
    public static final Icon MODE_SPHERE = Icon.MODE_SPHERE;
    public static final Icon MODE_REPLACE = Icon.MODE_REPLACE;
    public static final Icon MODE_OVERLAY = Icon.MODE_OVERLAY;
    public static final Icon MODE_CLEAR = Icon.MODE_CLEAR;
    public static final Icon MODE_RESHAPE = Icon.MODE_RESHAPE;
    public static final Icon MODE_COPY = Icon.COPY;
    public static final Icon MODE_PASTE = Icon.MODE_PASTE;
    public static final Icon MODE_CUT = Icon.MODE_CUT;
    public static final Icon MODE_STACK = Icon.MODE_STACK;
    public static final Icon MODE_MOVE = Icon.MOVE;
    public static final Icon MODE_MIRROR = Icon.MODE_MIRROR;
    public static final Icon MODE_RADIAL = Icon.MODE_RADIAL;
    public static final Icon MODE_MEASURE = Icon.MODE_MEASURE;

    // Tools
    public static final Icon TOOL_TROWEL = Icon.TROWEL;
    public static final Icon TOOL_HAMMER = Icon.HAMMER;
    public static final Icon TOOL_BRUSH = Icon.BRUSH;
    public static final Icon TOOL_BLUEPRINT = Icon.BLUEPRINT;
    public static final Icon TOOL_SQUARE = Icon.SQUARE;
    public static final Icon TOOL_CHISEL = Icon.CHISEL;

    // Upgrades
    public static final Icon UPG_REACH = Icon.UPG_REACH;
    public static final Icon UPG_CAPACITY = Icon.UPG_CAPACITY;
    public static final Icon UPG_SPEED = Icon.UPG_SPEED;
    public static final Icon UPG_MEMORY = Icon.UPG_MEMORY;
    public static final Icon UPG_SUPPLY_LINK = Icon.UPG_LINK;
    public static final Icon UPG_MAGNET = Icon.UPG_MAGNET;
    public static final Icon UPG_EFFICIENCY = Icon.UPG_EFFICIENCY;

    // Misc
    public static final Icon TOOLBOX = Icon.TOOLBOX;
    public static final Icon WHEEL = Icon.WHEEL;
    public static final Icon SELECTION = Icon.SELECTION;
    public static final Icon CHISEL = Icon.CHISEL;

    public static Icon shape(final Shape shape) {
        return switch (shape) {
            case FULL -> SHAPE_FULL;
            case STAIRS -> SHAPE_STAIRS;
            case SLAB -> SHAPE_SLAB;
            case VERTICAL_SLAB -> SHAPE_VERTICAL_SLAB;
            case VERTICAL_STAIRS -> SHAPE_VERTICAL_STAIRS;
            case WALL -> SHAPE_WALL;
            case FENCE -> SHAPE_FENCE;
            case STEP -> SHAPE_STEP;
            case PANEL -> SHAPE_PANEL;
            case FENCE_GATE -> SHAPE_FENCE_GATE;
            case VERTICAL_STEP -> SHAPE_VERTICAL_STEP;
            case POST -> SHAPE_POST;
            case LAYER -> SHAPE_LAYER;
            case PANE -> SHAPE_PANE;
        };
    }

    /** Glyph of a building mode by id; {@link #SELECTION} for unknown ids. */
    public static Icon mode(final String modeId) {
        return switch (modeId) {
            case "fill" -> MODE_FILL;
            case "walls" -> MODE_WALLS;
            case "line" -> MODE_LINE;
            case "extend" -> MODE_EXTEND;
            case "hollow" -> MODE_HOLLOW;
            case "outline" -> MODE_OUTLINE;
            case "cylinder" -> MODE_CYLINDER;
            case "sphere" -> MODE_SPHERE;
            case "replace" -> MODE_REPLACE;
            case "overlay" -> MODE_OVERLAY;
            case "clear" -> MODE_CLEAR;
            case "reshape" -> MODE_RESHAPE;
            case "copy" -> MODE_COPY;
            case "paste" -> MODE_PASTE;
            case "cut" -> MODE_CUT;
            case "stack" -> MODE_STACK;
            case "move" -> MODE_MOVE;
            case "mirror" -> MODE_MIRROR;
            case "radial" -> MODE_RADIAL;
            case "measure" -> MODE_MEASURE;
            default -> SELECTION;
        };
    }

    public static Icon tool(final ToolType tool) {
        return switch (tool) {
            case TROWEL -> TOOL_TROWEL;
            case HAMMER -> TOOL_HAMMER;
            case BRUSH -> TOOL_BRUSH;
            case BLUEPRINT -> TOOL_BLUEPRINT;
            case SQUARE -> TOOL_SQUARE;
            case CHISEL -> TOOL_CHISEL;
        };
    }

    public static Icon upgrade(final UpgradeType upgrade) {
        return switch (upgrade) {
            case REACH -> UPG_REACH;
            case CAPACITY -> UPG_CAPACITY;
            case SPEED -> UPG_SPEED;
            case MEMORY -> UPG_MEMORY;
            case SUPPLY_LINK -> UPG_SUPPLY_LINK;
            case MAGNET -> UPG_MAGNET;
            case EFFICIENCY -> UPG_EFFICIENCY;
        };
    }

    private BuildingIcons() {}
}
