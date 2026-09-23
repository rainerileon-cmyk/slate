package dev.fallingcloud.slate.building.client;

import dev.fallingcloud.slate.building.ops.ToolType;
import dev.fallingcloud.slate.building.toolbox.UpgradeType;
import dev.fallingcloud.slate.building.variant.Shape;
import dev.fallingcloud.slate.core.gfx.Icon;

/**
 * Every glyph Slate Building draws, in one place. They currently point at the closest EXISTING Core {@link Icon}s;
 * dedicated building glyphs are being drawn into Core's atlas and the lead remaps the constants here once they
 * land, so always go through these constants (or {@link #shape}/{@link #mode}/{@link #tool}/{@link #upgrade}),
 * never at {@code Icon} directly.
 *
 * <p>Pure constants over a plain enum: safe to reference from common code on a dedicated server (the enums'
 * {@code icon()} methods do).
 */
public final class BuildingIcons {

    // Shapes
    public static final Icon SHAPE_FULL = Icon.BLOCK;
    public static final Icon SHAPE_STAIRS = Icon.SORT;
    public static final Icon SHAPE_SLAB = Icon.ALIGN_BOTTOM;
    public static final Icon SHAPE_VERTICAL_SLAB = Icon.ALIGN_LEFT;
    public static final Icon SHAPE_VERTICAL_STAIRS = Icon.SORT;
    public static final Icon SHAPE_WALL = Icon.LAYERS;
    public static final Icon SHAPE_FENCE = Icon.GRID;
    public static final Icon SHAPE_STEP = Icon.ALIGN_BOTTOM;
    public static final Icon SHAPE_PANEL = Icon.PANEL;
    public static final Icon SHAPE_FENCE_GATE = Icon.GRID;
    public static final Icon SHAPE_VERTICAL_STEP = Icon.ALIGN_LEFT;
    public static final Icon SHAPE_POST = Icon.ALIGN_CENTER;
    public static final Icon SHAPE_LAYER = Icon.LAYERS;
    public static final Icon SHAPE_PANE = Icon.WINDOWED;

    // Building modes
    public static final Icon MODE_FILL = Icon.BLOCK;
    public static final Icon MODE_WALLS = Icon.PANEL;
    public static final Icon MODE_LINE = Icon.EDIT;
    public static final Icon MODE_EXTEND = Icon.PLUS;
    public static final Icon MODE_HOLLOW = Icon.FULLSCREEN;
    public static final Icon MODE_OUTLINE = Icon.RESIZE;
    public static final Icon MODE_CYLINDER = Icon.DOT;
    public static final Icon MODE_SPHERE = Icon.WORLD;
    public static final Icon MODE_REPLACE = Icon.REFRESH;
    public static final Icon MODE_OVERLAY = Icon.LAYERS;
    public static final Icon MODE_CLEAR = Icon.TRASH;
    public static final Icon MODE_RESHAPE = Icon.WRENCH;
    public static final Icon MODE_COPY = Icon.COPY;
    public static final Icon MODE_PASTE = Icon.IMPORT;
    public static final Icon MODE_CUT = Icon.CLOSE;
    public static final Icon MODE_STACK = Icon.DUPLICATE;
    public static final Icon MODE_MOVE = Icon.MOVE;
    public static final Icon MODE_MIRROR = Icon.SNAP;
    public static final Icon MODE_RADIAL = Icon.COMPASS;
    public static final Icon MODE_MEASURE = Icon.SLIDERS;

    // Tools
    public static final Icon TOOL_TROWEL = Icon.BLOCK;
    public static final Icon TOOL_HAMMER = Icon.WRENCH;
    public static final Icon TOOL_BRUSH = Icon.BRUSH;
    public static final Icon TOOL_BLUEPRINT = Icon.MAP;
    public static final Icon TOOL_SQUARE = Icon.SNAP;
    public static final Icon TOOL_CHISEL = Icon.EDIT;

    // Upgrades
    public static final Icon UPG_REACH = Icon.EXTERNAL;
    public static final Icon UPG_CAPACITY = Icon.PLUS;
    public static final Icon UPG_SPEED = Icon.BOLT;
    public static final Icon UPG_MEMORY = Icon.HISTORY;
    public static final Icon UPG_SUPPLY_LINK = Icon.LINK;
    public static final Icon UPG_MAGNET = Icon.DOWNLOAD;
    public static final Icon UPG_EFFICIENCY = Icon.SPARKLE;

    // Misc
    public static final Icon TOOLBOX = Icon.PACK;
    public static final Icon WHEEL = Icon.DOTS;
    public static final Icon SELECTION = Icon.RESIZE;
    public static final Icon CHISEL = Icon.EDIT;

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
