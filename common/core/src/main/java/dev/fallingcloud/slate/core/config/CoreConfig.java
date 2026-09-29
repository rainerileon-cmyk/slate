package dev.fallingcloud.slate.core.config;

import dev.fallingcloud.slate.core.screen.slot.Layout;
import dev.fallingcloud.slate.core.screen.slot.Style;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * {@code config/slate/core.json}. Public fields with initialiser defaults (see {@link JsonConfig}).
 * The Config module edits these through its Interface page; Core's own small settings page covers the
 * essentials when Config is not installed.
 */
public final class CoreConfig {

    /**
     * The layout and style model (the first-launch setup screen's rows), each independent of the others:
     * <ul>
     *   <li>{@link #layout} - the LAYOUT every menu shows in unless overridden per menu: {@code VANILLA} (Minecraft's
     *       own screens, with a Slate button added to the title and pause screens so Slate stays reachable),
     *       {@code CUSTOM} (Slate's rebuilt screens) or {@code OVERHAUL} (the scene-based screens; needs the UI
     *       module). Read through {@link #layout()}.</li>
     *   <li>{@link #skin} - the STYLE of menus: {@code DARK} (Slate's modern look) or {@code VANILLA} (stone buttons,
     *       still animated). {@link #style()} reads it as {@link Style}; {@link #customStyle()} as a switch.</li>
     *   <li>{@link #reskinContainers} - the STYLE of in-game containers (inventory, chests, furnaces, machines):
     *       Slate's panel and slot wells, in any skin.</li>
     *   <li>{@link #screens} - per-menu overrides of the layout and the style, keyed by menu slot id.</li>
     * </ul>
     */
    public String layout = "CUSTOM";
    /**
     * Per-menu overrides keyed by slot id ({@code minecraft:title}, {@code slate_multiplayer:hub}, ...). A missing entry
     * or a null field follows the global value. Edited through {@code MenuSlots}.
     */
    public Map<String, ScreenOverride> screens = new LinkedHashMap<>();
    /**
     * LEGACY, kept for one release: mirrors {@code layout != VANILLA} so older jars and the start-up window's fallback
     * keep reading it. {@link #migrate()} turns it into {@link #layout} once; {@link #setLayout} keeps it in step.
     */
    public boolean customLayout = true;
    /** The one-time migration from {@link #customLayout} (and Menu's per-screen flags) ran. */
    public boolean layoutMigrated = false;
    /** The first-launch setup screen was confirmed (or skipped); until then it shows before the title screen. */
    public boolean setupDone = false;

    /** {@code DARK} or {@code VANILLA}. */
    public String skin = "DARK";
    /** Accent colour as {@code #RRGGBB}. */
    public String accent = "#D9805E";
    /** Animation speed multiplier: 0 disables motion, 1 default, 2 slow. */
    public double motion = 1.0;
    /** Use the pixel heading font (false = vanilla font everywhere). */
    public boolean headingFont = true;
    /** Which pixel font headings use: {@code pixeloid} (default), {@code monocraft} or {@code pixelify}; others read as the default. */
    public String pixelFont = "pixeloid";
    /** Cross-screen fade. */
    public boolean transitions = true;
    /** Subtle click/hover sounds on Slate widgets. */
    public boolean uiSounds = true;
    /** Slate toasts (friend online, message received, ...). */
    public boolean toasts = true;
    /** Corner radius in pixels for the dark skin (0-4). */
    public int radius = 3;
    /** Blur the world behind in-game menus (vanilla does; some players prefer a plain dim). */
    public boolean blurInGame = true;

    /** Dev mode: the layout editor is reachable (pencil button on editable screens + the keybind). */
    public boolean devMode = false;
    /** Show the editor's grid by default. */
    public boolean devGrid = true;
    /** Editor snap size in pixels. */
    public int devSnap = 4;

    /**
     * Which screens Core restyles (widgets, backgrounds, tooltips, lists):
     * {@code VANILLA_AND_SLATE} (vanilla screens + Slate's own), {@code ALLOWLIST} (plus screens whose
     * class package starts with one of {@link #reskinAllowlist}), {@code ALL_NON_CONTAINER}, {@code NONE}.
     */
    public String reskinScope = "ALLOWLIST";
    /** Package prefixes / mod ids whose screens get restyled under ALLOWLIST. */
    public List<String> reskinAllowlist = new ArrayList<>(List.of(
        "me.shedaniel.clothconfig2", "dev.isxander.yacl3", "com.mrcrayfish.configured",
        "net.neoforged.neoforge.client.gui", "com.terraformersmc.modmenu", "com.blamejared.controlling",
        "com.mojang.realmsclient", "net.caffeinemc.mods.sodium.client.gui", "net.irisshaders.iris.gui"));
    /** Screens (class name prefixes) never restyled even under ALL_NON_CONTAINER. */
    public List<String> reskinDenylist = new ArrayList<>(List.of(
        "de.keksuccino.fancymenu", "mezz.jei", "com.simibubi.create", "xaero"));
    /**
     * Inventory and container screens (chests, furnaces, machines, ...) get Slate's panel in place of their background
     * texture, slot wells, an accent slot highlight and light labels ({@code ContainerReskin}), whatever the skin. A
     * screen that draws its background in pieces keeps its own look; the deny-list is never touched.
     */
    public boolean reskinContainers = true;

    /** One menu's overrides; a null field follows the global value. */
    public static final class ScreenOverride {
        /** {@code VANILLA}, {@code CUSTOM} or {@code OVERHAUL}; null follows {@link CoreConfig#layout}. */
        @Nullable public String layout;
        /** {@code SLATE} or {@code VANILLA}; null follows {@link CoreConfig#skin}. */
        @Nullable public String style;

        public boolean isEmpty() { return layout == null && style == null; }
    }

    // ------------------------------------------------------------------ layout

    /** The global layout ({@code CUSTOM} when the value is unreadable). */
    public Layout layout() {
        return Layout.parse(layout, Layout.CUSTOM);
    }

    /** Sets the global layout and keeps the legacy {@link #customLayout} mirror in step. */
    public void setLayout(final Layout l) {
        layout = l.name();
        customLayout = l != Layout.VANILLA;
    }

    /** The legacy two-state switch: on = any Slate layout, off = vanilla. */
    public boolean hasCustomLayout() {
        return layout() != Layout.VANILLA;
    }

    /** The legacy two-state switch as a setter: off = {@code VANILLA}; on keeps the current Slate layout, or picks {@code CUSTOM}. */
    public void setCustomLayout(final boolean on) {
        setLayout(on ? (layout() == Layout.VANILLA ? Layout.CUSTOM : layout()) : Layout.VANILLA);
    }

    @Nullable
    public ScreenOverride override(final String slotId) {
        return screens == null ? null : screens.get(slotId);
    }

    public ScreenOverride overrideOrCreate(final String slotId) {
        if (screens == null) screens = new LinkedHashMap<>();
        return screens.computeIfAbsent(slotId, k -> new ScreenOverride());
    }

    /** Drops overrides that follow the global value in both fields, so the file stays readable. */
    public void clearEmptyOverrides() {
        if (screens == null) return;
        for (final Iterator<Map.Entry<String, ScreenOverride>> it = screens.entrySet().iterator(); it.hasNext();) {
            final ScreenOverride o = it.next().getValue();
            if (o == null || o.isEmpty()) it.remove();
        }
    }

    /**
     * The one-time move from the {@link #customLayout} switch to {@link #layout}: a file saved before the layout model
     * existed has no {@code layout} key and reads its default, so the old switch decides. Returns whether anything
     * changed (the caller saves). Menu migrates its own per-screen flags into {@link #screens}.
     */
    public boolean migrate() {
        if (layoutMigrated) return false;
        setLayout(customLayout ? Layout.CUSTOM : Layout.VANILLA);
        layoutMigrated = true;
        return true;
    }

    // ------------------------------------------------------------------ style

    public boolean isVanillaSkin() {
        return "VANILLA".equalsIgnoreCase(skin);
    }

    /** The global style: {@link Style#SLATE} for the dark skin, {@link Style#VANILLA} for the vanilla one. */
    public Style style() {
        return isVanillaSkin() ? Style.VANILLA : Style.SLATE;
    }

    public void setStyle(final Style s) {
        skin = s == Style.VANILLA ? "VANILLA" : "DARK";
    }

    /** The menu-style switch: true = the dark skin, false = the vanilla skin. */
    public boolean customStyle() {
        return !isVanillaSkin();
    }

    public void setCustomStyle(final boolean on) {
        skin = on ? "DARK" : "VANILLA";
    }
}
