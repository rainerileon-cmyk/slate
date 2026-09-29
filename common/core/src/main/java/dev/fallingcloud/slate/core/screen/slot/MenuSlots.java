package dev.fallingcloud.slate.core.screen.slot;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.config.CoreConfig;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.module.Modules;
import dev.fallingcloud.slate.core.screen.ScreenSwaps;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The registry of menu slots and the resolution of which layout and style each one shows in. Core defines the
 * slots ({@link CoreSlots}); modules {@link #provide} a screen factory per layout they implement, and Core installs
 * one screen swap per vanilla class a slot lists, so a vanilla menu is replaced by the effective layout's screen at
 * {@code Minecraft.setScreen}. New menus (no vanilla class) are opened through {@link #open}.
 *
 * <p>Resolution: a slot's layout is its override in {@code core.json} ({@code screens.<id>.layout}) or the global
 * {@code layout}, clamped to what is {@link #available}: vanilla menus fall back downwards (Overhaul, Custom,
 * Vanilla), new menus fall back to Custom. Overhaul layouts exist only while the UI module is loaded, and so do the
 * Custom layouts of vanilla menus (without the UI module every vanilla menu is vanilla, R4). The style is the slot's
 * override or the global one; {@code Theme} switches to it before the slot's screen is shown.</p>
 *
 * <p>Registration is thread-safe: NeoForge constructs mods in parallel and modules provide from their constructors.</p>
 */
public final class MenuSlots {

    /** The UI module: its presence enables the Overhaul layouts and the Custom layouts of vanilla menus. */
    public static final String UI_MODULE = "slate_menu";

    private record Provider(int priority, Function<Screen, Screen> factory) {}

    private static final Object LOCK = new Object();
    private static final Map<String, MenuSlot> SLOTS = new LinkedHashMap<>();
    private static final Set<String> PLACEHOLDERS = new java.util.HashSet<>();
    private static final Map<String, Map<Layout, Provider>> PROVIDERS = new HashMap<>();
    private static final Map<String, EnumSet<Layout>> SUPPORTED = new HashMap<>();
    /** Screen class name -> slot id: the vanilla classes of every slot plus every screen a provider created. */
    private static final Map<String, String> CLASS_TO_SLOT = new ConcurrentHashMap<>();
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    static {
        CoreSlots.register();
    }

    // ------------------------------------------------------------------ registration

    /** Defines a slot. The first real definition wins; a placeholder created by an early {@link #provide} is replaced. */
    public static void register(final MenuSlot slot) {
        synchronized (LOCK) {
            final MenuSlot existing = SLOTS.get(slot.id());
            if (existing != null && !PLACEHOLDERS.remove(slot.id())) return;
            SLOTS.put(slot.id(), slot);
            for (final String cls : slot.vanillaClasses()) {
                CLASS_TO_SLOT.put(cls, slot.id());
                ScreenSwaps.registerByName(cls, s -> swap(slot.id(), s));
            }
        }
        changed();
    }

    /**
     * Registers the screen of one layout of a slot. For a vanilla menu the factory receives the vanilla screen being
     * replaced (so it can copy its parent and state); for a new menu it receives the parent screen. Returning null
     * declines (the next plainer layout is tried, then the vanilla screen shows). Priority 0.
     */
    public static void provide(final String slotId, final Layout layout, final Function<Screen, Screen> factory) {
        provide(slotId, layout, 0, factory);
    }

    /** {@link #provide(String, Layout, Function)} with a priority: the highest registered provider for a layout wins. */
    public static void provide(final String slotId, final Layout layout, final int priority, final Function<Screen, Screen> factory) {
        if (layout == Layout.VANILLA) throw new IllegalArgumentException("the vanilla layout is Minecraft's own screen");
        synchronized (LOCK) {
            ensure(slotId);
            final Map<Layout, Provider> m = PROVIDERS.computeIfAbsent(slotId, k -> new EnumMap<>(Layout.class));
            final Provider current = m.get(layout);
            if (current == null || priority >= current.priority()) m.put(layout, new Provider(priority, factory));
        }
        changed();
    }

    /**
     * Marks a layout as implemented for a slot whose screens are drawn over rather than swapped (the loading screens,
     * the start-up window), or takes the mark away again ({@code on = false}).
     */
    public static void support(final String slotId, final Layout layout, final boolean on) {
        synchronized (LOCK) {
            ensure(slotId);
            final EnumSet<Layout> set = SUPPORTED.computeIfAbsent(slotId, k -> EnumSet.noneOf(Layout.class));
            if (on) set.add(layout); else set.remove(layout);
        }
        changed();
    }

    private static void ensure(final String slotId) {
        if (SLOTS.containsKey(slotId)) return;
        // A module provided before Core defined the slot (or a slot Core does not know): a new-menu placeholder.
        final int colon = slotId.indexOf(':');
        SLOTS.put(slotId, MenuSlot.custom(slotId, Component.literal(colon < 0 ? slotId : slotId.substring(colon + 1)), colon < 0 ? "" : slotId.substring(0, colon)));
        PLACEHOLDERS.add(slotId);
    }

    /** Runs whenever slots, providers or the layout settings change (the early-window properties are rewritten). */
    public static void onChange(final Runnable listener) { LISTENERS.add(listener); }

    private static void changed() {
        for (final Runnable r : LISTENERS) {
            try { r.run(); } catch (final Exception e) { Slate.LOGGER.warn("[Slate] menu slot listener threw: {}", e.toString()); }
        }
    }

    // ------------------------------------------------------------------ queries

    public static List<MenuSlot> all() {
        synchronized (LOCK) { return new ArrayList<>(SLOTS.values()); }
    }

    public static Optional<MenuSlot> get(final String slotId) {
        synchronized (LOCK) { return Optional.ofNullable(SLOTS.get(slotId)); }
    }

    public static boolean uiModuleLoaded() { return Modules.isLoaded(UI_MODULE); }

    private static boolean has(final String slotId, final Layout layout) {
        final Map<Layout, Provider> m = PROVIDERS.get(slotId);
        if (m != null && m.containsKey(layout)) return true;
        final EnumSet<Layout> s = SUPPORTED.get(slotId);
        return s != null && s.contains(layout);
    }

    /** The layouts the slot can show right now, given the installed modules. */
    public static Set<Layout> available(final String slotId) {
        synchronized (LOCK) {
            final MenuSlot slot = SLOTS.get(slotId);
            final EnumSet<Layout> out = EnumSet.noneOf(Layout.class);
            if (slot == null) return out;
            final boolean ui = uiModuleLoaded();
            if (slot.vanillaMenu()) out.add(Layout.VANILLA);
            if (has(slotId, Layout.CUSTOM) && (ui || !slot.vanillaMenu())) out.add(Layout.CUSTOM);
            if (has(slotId, Layout.OVERHAUL) && ui) out.add(Layout.OVERHAUL);
            return out;
        }
    }

    /** What the user asked for this slot: its override, else the global layout. */
    public static Layout requested(final String slotId) {
        return layoutOverride(slotId).orElseGet(MenuSlots::globalLayout);
    }

    /** The layout the slot shows: {@link #requested} clamped to {@link #available}. */
    public static Layout effective(final String slotId) {
        final Layout requested = requested(slotId);
        final Set<Layout> avail = available(slotId);
        if (avail.contains(requested)) return requested;
        for (Layout l = requested.lower(); l != null; l = l.lower()) if (avail.contains(l)) return l;
        for (final Layout l : Layout.values()) if (avail.contains(l)) return l;
        final MenuSlot slot = get(slotId).orElse(null);
        return slot == null || slot.vanillaMenu() ? Layout.VANILLA : Layout.CUSTOM;
    }

    /** The style the slot's screens are painted in: its override, else the global style. */
    public static Style style(final String slotId) {
        return styleOverride(slotId).orElseGet(() -> Slate.config().style());
    }

    /** Whether a screen's slot deviates from the global settings (the settings tables show a "follow global" reset). */
    public static boolean hasOverride(final String slotId) {
        return layoutOverride(slotId).isPresent() || styleOverride(slotId).isPresent();
    }

    public static Optional<Layout> layoutOverride(final String slotId) {
        final CoreConfig.ScreenOverride o = Slate.config().override(slotId);
        return o == null || o.layout == null ? Optional.empty() : Optional.of(Layout.parse(o.layout, globalLayout()));
    }

    public static Optional<Style> styleOverride(final String slotId) {
        final CoreConfig.ScreenOverride o = Slate.config().override(slotId);
        return o == null || o.style == null ? Optional.empty() : Optional.of(Style.parse(o.style, Slate.config().style()));
    }

    public static Layout globalLayout() { return Slate.config().layout(); }

    /** The slot a screen belongs to: a vanilla class a slot lists, or a screen a provider created; null for others. */
    @Nullable
    public static String slotOf(@Nullable final Screen screen) {
        return screen == null ? null : CLASS_TO_SLOT.get(screen.getClass().getName());
    }

    /** The style a screen is painted in: its slot's, or the global one for screens outside any slot. */
    public static Style styleFor(@Nullable final Screen screen) {
        final String slot = slotOf(screen);
        return slot == null ? Slate.config().style() : style(slot);
    }

    // ------------------------------------------------------------------ settings

    /** Sets the global layout, saves and applies it (the next screen open shows it). */
    public static void setGlobalLayout(final Layout layout) {
        Slate.configFile().update(c -> c.setLayout(layout));
        refresh();
    }

    /** Sets the global style, saves and applies it live. */
    public static void setGlobalStyle(final Style style) {
        Slate.configFile().update(c -> c.setStyle(style));
        refresh();
    }

    /** Sets one slot's layout override (null = follow the global layout), saves and applies it. */
    public static void setLayoutOverride(final String slotId, @Nullable final Layout layout) {
        Slate.configFile().update(c -> {
            if (layout == null) { final CoreConfig.ScreenOverride o = c.override(slotId); if (o != null) o.layout = null; }
            else c.overrideOrCreate(slotId).layout = layout.name();
            c.clearEmptyOverrides();
        });
        refresh();
    }

    /** Sets one slot's style override (null = follow the global style), saves and applies it live. */
    public static void setStyleOverride(final String slotId, @Nullable final Style style) {
        Slate.configFile().update(c -> {
            if (style == null) { final CoreConfig.ScreenOverride o = c.override(slotId); if (o != null) o.style = null; }
            else c.overrideOrCreate(slotId).style = style.name();
            c.clearEmptyOverrides();
        });
        refresh();
    }

    /** Clears both overrides of a slot. */
    public static void clearOverrides(final String slotId) {
        Slate.configFile().update(c -> { if (c.screens != null) c.screens.remove(slotId); });
        refresh();
    }

    /** Re-resolves the theme for the open screen and tells the listeners (after config edits made elsewhere). */
    public static void refresh() {
        Theme.reload();
        changed();
    }

    // ------------------------------------------------------------------ opening and swapping

    /**
     * Opens a slot's menu in its effective layout. A vanilla menu is opened through its vanilla screen (Core's screen
     * factories know {@code minecraft:title}, {@code minecraft:options}, ...), so the swap below picks the layout; a
     * new menu goes straight to its provider, which gets {@code parent}.
     */
    public static void open(final String slotId, @Nullable final Screen parent) {
        final Minecraft mc = Minecraft.getInstance();
        final MenuSlot slot = get(slotId).orElse(null);
        if (slot != null && slot.vanillaMenu()) {
            final Function<Screen, Screen> vanilla = CoreActions.SCREEN_FACTORIES.get(slotId);
            if (vanilla != null) { mc.setScreen(vanilla.apply(parent)); return; }
        }
        final Screen s = create(slotId, parent);
        if (s != null) { mc.setScreen(s); return; }
        Slate.LOGGER.warn("[Slate] nothing provides the menu slot {}", slotId);
        SlateToasts.show(Component.translatable("slate.slot.unavailable"), Component.literal(slotId), Icon.WARNING);
    }

    /**
     * The screen a vanilla menu's slot shows right now, built for a preview: the effective layout's screen, or the
     * vanilla screen {@code vanilla} supplies when the layout is vanilla (or nothing provides the slot).
     */
    public static Screen preview(final String slotId, final java.util.function.Supplier<Screen> vanilla) {
        final Screen v = vanilla.get();
        final Screen s = swap(slotId, v);
        return s != null ? s : v;
    }

    /** Builds the effective layout's screen for a new menu (null when nothing provides it). */
    @Nullable
    public static Screen create(final String slotId, @Nullable final Screen parent) {
        for (Layout l = effective(slotId); l != null && l != Layout.VANILLA; l = l.lower()) {
            final Screen s = build(slotId, l, parent);
            if (s != null) return s;
        }
        return null;
    }

    /** The swap installed for a slot's vanilla classes: the effective layout's screen, or null to keep vanilla's. */
    @Nullable
    static Screen swap(final String slotId, final Screen vanilla) {
        for (Layout l = effective(slotId); l != null && l != Layout.VANILLA; l = l.lower()) {
            final Screen s = build(slotId, l, vanilla);
            if (s != null) return s;
        }
        return null;
    }

    @Nullable
    private static Screen build(final String slotId, final Layout layout, @Nullable final Screen input) {
        final Provider p;
        synchronized (LOCK) {
            final Map<Layout, Provider> m = PROVIDERS.get(slotId);
            p = m == null ? null : m.get(layout);
        }
        if (p == null) return null;
        try {
            final Screen s = p.factory().apply(input);
            if (s != null) CLASS_TO_SLOT.put(s.getClass().getName(), slotId);
            return s;
        } catch (final Exception e) {
            Slate.LOGGER.error("[Slate] the {} layout of {} failed to build; falling back", layout.key(), slotId, e);
            return null;
        }
    }

    private MenuSlots() {}
}
