package dev.fallingcloud.slate.core.layout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The saved customisation of one screen ({@code config/slate/layouts/<screen id>.json}). Plain POJO for
 * Gson. Everything is optional; an empty layout changes nothing.
 */
public final class ScreenLayout {

    /** Format version, for future migrations. */
    public int version = 1;
    /** The screen id this applies to (informational; the file name is authoritative). */
    public String screen = "";
    /** Vanilla widgets (by {@link WidgetKey} string) that are hidden. */
    public List<String> hidden = new ArrayList<>();
    /** Vanilla widgets that were moved/resized: key -> placement. */
    public Map<String, Placement> moved = new LinkedHashMap<>();
    /** Custom elements added to the screen, in z-order (first = bottom). */
    public List<Element> elements = new ArrayList<>();
    /** Optional background override for the whole screen. */
    public Background background = null;

    /** Where a widget sits: anchor + offsets; w/h of 0 keep the widget's own size. */
    public static final class Placement {
        public String anchor = "TOP_LEFT";
        public int x, y, w, h;

        public Placement() {}

        public Placement(final String anchor, final int x, final int y, final int w, final int h) {
            this.anchor = anchor; this.x = x; this.y = y; this.w = w; this.h = h;
        }

        public Placement copy() { return new Placement(anchor, x, y, w, h); }
    }

    /** A custom element. {@code props} are type-specific strings; {@code actions} run on click in order. */
    public static final class Element {
        public String id = "";
        public String type = "button";
        public Placement place = new Placement();
        public boolean visible = true;
        public boolean locked = false;
        public Map<String, String> props = new LinkedHashMap<>();
        public List<Action> actions = new ArrayList<>();

        public Element copy() {
            final Element e = new Element();
            e.id = id; e.type = type; e.place = place.copy(); e.visible = visible; e.locked = locked;
            e.props = new LinkedHashMap<>(props);
            for (final Action a : actions) e.actions.add(a.copy());
            return e;
        }
    }

    /** One saved action invocation. */
    public static final class Action {
        public String type = "";
        public Map<String, String> args = new LinkedHashMap<>();

        public Action() {}

        public Action(final String type, final Map<String, String> args) { this.type = type; this.args = new LinkedHashMap<>(args); }

        public Action copy() { return new Action(type, args); }
    }

    /**
     * Background: {@code kind} = {@code default | color | image | panorama | none};
     * {@code value} = hex colour or image path (relative to the game dir or a resource location).
     */
    public static final class Background {
        public String kind = "default";
        public String value = "";
        public boolean dim = true;
    }

    public boolean isEmpty() {
        return hidden.isEmpty() && moved.isEmpty() && elements.isEmpty() && background == null;
    }

    public ScreenLayout copy() {
        final ScreenLayout l = new ScreenLayout();
        l.version = version;
        l.screen = screen;
        l.hidden = new ArrayList<>(hidden);
        moved.forEach((k, v) -> l.moved.put(k, v.copy()));
        for (final Element e : elements) l.elements.add(e.copy());
        if (background != null) { l.background = new Background(); l.background.kind = background.kind; l.background.value = background.value; l.background.dim = background.dim; }
        return l;
    }
}
