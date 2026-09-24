package dev.fallingcloud.slate.config.api;

import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.core.gfx.Icon;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

/**
 * Lets other Slate modules add top tabs to a category of the Slate Config hub, e.g. Slate Building adds
 * "Building" under {@link #GAMEPLAY}. Each tab is an option page built from {@link Section}s; the supplier is
 * called every time the tab is (re)built, so it can read live config.
 *
 * <p>Call {@link #register} from a class that is only loaded after {@code slate_config} is known to be
 * present ({@code SlatePlatform.get().isModLoaded("slate_config")}), during {@code initClient}. Registering the
 * same category + tab id again replaces the earlier tab.
 */
public final class SettingsTabs {

    /** Category ids of the hub's sidebar that accept contributed tabs. */
    public static final String GAMEPLAY = "gameplay";
    public static final String INTERFACE = "interface";
    public static final String MULTIPLAYER = "multiplayer";
    public static final String CUSTOMIZATION = "customization";

    /**
     * @param id       tab id, unique within its category (also used for search crumbs and "last tab" memory)
     * @param title    tab label
     * @param icon     tab icon
     * @param sections builds the tab's sections; called on every rebuild
     * @param order    lower sorts first; the category's own built-in tabs use 0..99
     */
    public record Tab(String id, Component title, Icon icon, Supplier<List<Section>> sections, int order) {}

    private static final Map<String, Map<String, Tab>> TABS = new LinkedHashMap<>();

    public static synchronized void register(final String categoryId, final Tab tab) {
        TABS.computeIfAbsent(categoryId, k -> new LinkedHashMap<>()).put(tab.id(), tab);
    }

    /** Registered tabs of a category, sorted by {@link Tab#order()} then registration order. */
    public static synchronized List<Tab> tabs(final String categoryId) {
        final Map<String, Tab> m = TABS.get(categoryId);
        if (m == null) return List.of();
        final List<Tab> out = new ArrayList<>(m.values());
        out.sort(Comparator.comparingInt(Tab::order));
        return out;
    }

    private SettingsTabs() {}
}
