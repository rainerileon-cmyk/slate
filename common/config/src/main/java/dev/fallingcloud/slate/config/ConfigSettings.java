package dev.fallingcloud.slate.config;

import com.google.gson.JsonObject;
import dev.fallingcloud.slate.core.config.JsonConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * {@code config/slate/config.json}: favourites, user presets and small UI memory. Public fields with
 * defaults, edited through {@link #file()} and saved atomically by Core's JsonConfig.
 */
public final class ConfigSettings {

    /** Option ids pinned to the Favourites page, in pin order. */
    public List<String> favourites = new ArrayList<>();
    /** User presets ("Performance"/"Quality" ship as resources and are not stored here). */
    public List<Preset> presets = new ArrayList<>();
    /** Music volume before the Audio page's music toggle muted it (restored when toggled back on). */
    public double musicVolumeBeforeMute = 1.0;
    /** Section ids collapsed by the user (page:section), for the collapsible headers inside a tab (key categories). */
    public List<String> collapsedSections = new ArrayList<>();
    /** Route vanilla's option sub-screens (video, sound, controls, chat, language, accessibility, online) and Sodium's screen to the hub. Read at startup. */
    public boolean swapVanillaScreens = true;
    /**
     * The tab last shown on each page, so a page reopens where it was left: {@code page -> tab id} for the top
     * tabs ({@code category/tab -> ...} for pages hosted in a category) and {@code page/tab -> section id} for
     * the small secondary tabs.
     */
    public Map<String, String> lastTabs = new LinkedHashMap<>();
    /** One-time migration done: the retired "DF pack" curated page ({@code pages/df.json}) was moved aside. */
    public boolean dfPageRetired = false;

    /** A named snapshot of option values keyed by option id (JSON values as produced by OptionValues.toJson). */
    public static final class Preset {
        public String name = "";
        public String description = "";
        public JsonObject values = new JsonObject();
        /** Not saved: true for the presets shipped in the jar. */
        public transient boolean builtin;

        public Preset() {}

        public Preset(final String name, final JsonObject values, final boolean builtin) {
            this.name = name;
            this.values = values;
            this.builtin = builtin;
        }
    }

    private static JsonConfig<ConfigSettings> file;

    public static synchronized JsonConfig<ConfigSettings> file() {
        if (file == null) file = JsonConfig.of("config", ConfigSettings.class, ConfigSettings::new);
        return file;
    }

    public static ConfigSettings get() {
        return file().get();
    }

    public static boolean isFavourite(final String id) {
        return get().favourites.contains(id);
    }

    public static void toggleFavourite(final String id) {
        file().update(c -> { if (!c.favourites.remove(id)) c.favourites.add(id); });
    }

    @Nullable
    public static String lastTab(final String key) {
        final Map<String, String> m = get().lastTabs;
        return m == null ? null : m.get(key);
    }

    public static void setLastTab(final String key, final String tab) {
        if (tab.equals(lastTab(key))) return;
        file().update(c -> {
            if (c.lastTabs == null) c.lastTabs = new LinkedHashMap<>();
            c.lastTabs.put(key, tab);
        });
    }

    public static boolean isCollapsed(final String key) {
        return get().collapsedSections.contains(key);
    }

    public static void setCollapsed(final String key, final boolean collapsed) {
        file().update(c -> { if (collapsed) { if (!c.collapsedSections.contains(key)) c.collapsedSections.add(key); } else c.collapsedSections.remove(key); });
    }
}
