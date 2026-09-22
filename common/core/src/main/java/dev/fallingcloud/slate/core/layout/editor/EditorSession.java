package dev.fallingcloud.slate.core.layout.editor;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.config.JsonConfig;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.layout.ElementType;
import dev.fallingcloud.slate.core.layout.LayoutApplier;
import dev.fallingcloud.slate.core.layout.LayoutStore;
import dev.fallingcloud.slate.core.layout.ScreenLayout;
import dev.fallingcloud.slate.core.layout.action.ActionType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * The editing state of one screen: the working layout, undo/redo, dirtiness, clipboard.
 *
 * <p>The working layout IS the instance cached by {@link LayoutStore}: {@code LayoutStore.get(id)} hands
 * out its cached object, so mutating it in place means {@link LayoutApplier#apply} (which reads the store
 * on every init) always renders exactly what will be saved, with no second code path. Undo snapshots are
 * {@link ScreenLayout#copy()}s; restoring one copies its contents back into the cached instance.
 * Dirtiness is measured against the file on disk, so unsaved edits that stayed in memory after the
 * screen closed are still reported (and offered for saving) the next time the editor opens.</p>
 */
final class EditorSession {

    private static final int UNDO_CAP = 100;
    /** Elements copied with Ctrl+C; survives screen changes so elements can be moved between screens. */
    static final List<ScreenLayout.Element> CLIPBOARD = new ArrayList<>();
    @Nullable private static EditorSession resumable;

    final Screen screen;
    final String layoutId;
    private ScreenLayout working;
    private String savedJson;
    private final Deque<ScreenLayout> undo = new ArrayDeque<>();
    private final Deque<ScreenLayout> redo = new ArrayDeque<>();
    @Nullable private String lastKey;
    private long lastKeyMs;
    private int revision;
    private int dirtyRevision = -1;
    private boolean dirtyCached;

    // UI state remembered across an F7 "Cancel" resume.
    List<String> selection = List.of();
    boolean showProps = true, showLayers, grid, snap = true;

    EditorSession(final Screen screen) {
        this.screen = screen;
        this.layoutId = LayoutApplier.layoutId(screen);
        this.working = LayoutStore.get(layoutId);
        normalize(working, layoutId);
        this.savedJson = diskJson();
    }

    ScreenLayout layout() { return working; }

    // ------------------------------------------------------------------ dirtiness

    static String json(final ScreenLayout l) { return JsonConfig.GSON.toJson(l); }

    private String diskJson() {
        ScreenLayout disk = null;
        final Path f = LayoutStore.fileFor(layoutId);
        if (Files.isRegularFile(f)) {
            try { disk = JsonConfig.GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), ScreenLayout.class); } catch (final Exception ignored) {}
        }
        if (disk == null) disk = new ScreenLayout();
        normalize(disk, layoutId);
        return json(disk);
    }

    /** Something changed (or may have); the dirty flag is recomputed lazily. */
    void touch() { revision++; }

    boolean dirty() {
        if (dirtyRevision != revision) {
            dirtyRevision = revision;
            try { dirtyCached = !json(working).equals(savedJson); } catch (final Exception e) { dirtyCached = true; }
        }
        return dirtyCached;
    }

    // ------------------------------------------------------------------ undo / redo

    /**
     * Snapshot before a mutation. Consecutive mutations with the same non-null {@code coalesceKey}
     * within 1.5 s share one undo step (typing in a field, arrow nudging).
     */
    void pushUndo(@Nullable final String coalesceKey) {
        final long now = Clock.nowMs();
        if (coalesceKey != null && coalesceKey.equals(lastKey) && now - lastKeyMs < 1500) {
            lastKeyMs = now;
            touch();
            return;
        }
        lastKey = coalesceKey;
        lastKeyMs = now;
        undo.push(working.copy());
        while (undo.size() > UNDO_CAP) undo.removeLast();
        redo.clear();
        touch();
    }

    boolean canUndo() { return !undo.isEmpty(); }

    boolean canRedo() { return !redo.isEmpty(); }

    boolean undo() {
        if (undo.isEmpty()) return false;
        redo.push(working.copy());
        assign(working, undo.pop());
        lastKey = null;
        touch();
        return true;
    }

    boolean redo() {
        if (redo.isEmpty()) return false;
        undo.push(working.copy());
        assign(working, redo.pop());
        lastKey = null;
        touch();
        return true;
    }

    // ------------------------------------------------------------------ persistence

    void save() {
        LayoutStore.save(layoutId, working);
        savedJson = json(working);
        touch();
    }

    /** Drop the in-memory edits and reload what is on disk (re-applies to the screen). */
    void discard() {
        LayoutStore.invalidate();
        working = LayoutStore.get(layoutId);
        normalize(working, layoutId);
        undo.clear();
        redo.clear();
        lastKey = null;
        savedJson = diskJson();
        reapply();
    }

    /** Re-run the applier so the screen shows the working layout. */
    void reapply() {
        try {
            LayoutApplier.apply(screen);
        } catch (final Exception e) {
            Slate.LOGGER.error("[Slate] editor: applying layout {} failed", layoutId, e);
        }
        touch();
    }

    /** Replace the whole layout (import, reset). Undoable. */
    void replaceWith(final ScreenLayout other) {
        pushUndo(null);
        assign(working, other);
        normalize(working, layoutId);
        touch();
    }

    // ------------------------------------------------------------------ elements

    /** A fresh unique element id: {@code base_1}, {@code base_2}, ... */
    String newElementId(final String base) {
        String b = base == null ? "element" : base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        b = b.replaceAll("_\\d+$", "");
        if (b.isEmpty()) b = "element";
        final Set<String> used = new HashSet<>();
        for (final ScreenLayout.Element e : working.elements) used.add(e.id);
        for (int n = 1; ; n++) {
            final String id = b + "_" + n;
            if (!used.contains(id)) return id;
        }
    }

    boolean idInUse(final String id, @Nullable final ScreenLayout.Element except) {
        for (final ScreenLayout.Element e : working.elements) if (e != except && e.id.equals(id)) return true;
        return false;
    }

    /** A new element of a type, with the type's default props, anchored at the screen centre. */
    ScreenLayout.Element newElement(final ElementType type) {
        final ScreenLayout.Element e = new ScreenLayout.Element();
        e.type = type.id();
        e.id = newElementId(type.id().substring(type.id().indexOf(':') + 1));
        final int[] d = type.defaultSize();
        e.place = new ScreenLayout.Placement("CENTER", 0, 0, d[0], d[1]);
        for (final ActionType.Arg a : type.props()) e.props.put(a.key(), a.defaultValue() == null ? "" : a.defaultValue());
        return e;
    }

    static LinkedHashMap<String, String> defaultArgs(final ActionType type) {
        final LinkedHashMap<String, String> m = new LinkedHashMap<>();
        for (final ActionType.Arg a : type.args()) m.put(a.key(), a.defaultValue() == null ? "" : a.defaultValue());
        return m;
    }

    // ------------------------------------------------------------------ resume (F7 with unsaved changes)

    static void setResumable(@Nullable final EditorSession s) { resumable = s; }

    /** The session parked by {@code close()} for this screen, if any (consumed). */
    @Nullable
    static EditorSession takeResumable(final Screen screen) {
        final EditorSession s = resumable;
        resumable = null;
        return s != null && s.screen == screen ? s : null;
    }

    static void clearResumable(final EditorSession s) {
        if (resumable == s) resumable = null;
    }

    // ------------------------------------------------------------------ layout plumbing

    /** Fills nulls a hand-written file may contain and gives every element a unique id. Deterministic. */
    static void normalize(final ScreenLayout l, final String id) {
        if (l.hidden == null) l.hidden = new ArrayList<>();
        l.hidden.removeIf(Objects::isNull);
        if (l.moved == null) l.moved = new LinkedHashMap<>();
        l.moved.values().removeIf(Objects::isNull);
        for (final ScreenLayout.Placement p : l.moved.values()) if (p.anchor == null) p.anchor = "TOP_LEFT";
        if (l.elements == null) l.elements = new ArrayList<>();
        l.elements.removeIf(Objects::isNull);
        final Set<String> ids = new HashSet<>();
        for (final ScreenLayout.Element e : l.elements) {
            if (e.type == null || e.type.isBlank()) e.type = "slate:button";
            if (e.place == null) e.place = new ScreenLayout.Placement();
            if (e.place.anchor == null) e.place.anchor = "TOP_LEFT";
            if (e.props == null) e.props = new LinkedHashMap<>();
            e.props.values().removeIf(Objects::isNull);
            if (e.actions == null) e.actions = new ArrayList<>();
            e.actions.removeIf(Objects::isNull);
            for (final ScreenLayout.Action a : e.actions) {
                if (a.type == null) a.type = "";
                if (a.args == null) a.args = new LinkedHashMap<>();
                a.args.values().removeIf(Objects::isNull);
            }
            if (e.id == null || e.id.isBlank() || !ids.add(e.id)) {
                final String base = e.type.substring(e.type.indexOf(':') + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
                for (int n = 1; ; n++) {
                    final String cand = base + "_" + n;
                    if (ids.add(cand)) { e.id = cand; break; }
                }
            }
        }
        if (l.background != null) {
            if (l.background.kind == null) l.background.kind = "default";
            if (l.background.value == null) l.background.value = "";
            if ("default".equalsIgnoreCase(l.background.kind.trim())) l.background = null;
        }
        l.screen = id;
        if (l.version <= 0) l.version = 1;
    }

    /** In-place copy of {@code src} into {@code target} (the cached instance keeps its identity). */
    static void assign(final ScreenLayout target, final ScreenLayout src) {
        target.version = src.version;
        target.screen = src.screen;
        target.hidden.clear();
        if (src.hidden != null) target.hidden.addAll(src.hidden);
        target.moved.clear();
        if (src.moved != null) src.moved.forEach((k, v) -> { if (v != null) target.moved.put(k, v.copy()); });
        target.elements.clear();
        if (src.elements != null) for (final ScreenLayout.Element e : src.elements) if (e != null) target.elements.add(e.copy());
        target.background = copyBackground(src.background);
    }

    @Nullable
    static ScreenLayout.Background copyBackground(@Nullable final ScreenLayout.Background b) {
        if (b == null) return null;
        final ScreenLayout.Background c = new ScreenLayout.Background();
        c.kind = b.kind;
        c.value = b.value;
        c.dim = b.dim;
        return c;
    }
}
