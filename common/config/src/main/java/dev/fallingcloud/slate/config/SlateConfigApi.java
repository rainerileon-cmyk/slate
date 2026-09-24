package dev.fallingcloud.slate.config;

import dev.fallingcloud.slate.config.hub.ConfigHubScreen;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * What other modules call. {@link #openHub} opens the unified settings screen, optionally on a page given
 * as a path: a sidebar id ({@code video, audio, controls, gameplay, interface, multiplayer, customization,
 * language_accessibility, favourites, presets}, or a curated page id), {@code category/tab} for a tab of a
 * category ({@code multiplayer/chat}, {@code customization/shaders}, {@code gameplay/building}...), or
 * {@code page/tab} for a top tab of a page ({@code controls/keys}). The ids from before the categories
 * ({@code chat, online, packs, mods, shaders, language, accessibility}) still work as aliases. Modules add
 * tabs through {@link dev.fallingcloud.slate.config.api.SettingsTabs}. Slate modules whose JSON config is
 * edited from the hub register a reload hook so their changes apply live instead of showing a restart badge.
 */
public final class SlateConfigApi {

    private static final Map<String, Runnable> RELOAD_HOOKS = new ConcurrentHashMap<>();

    public static void openHub(@Nullable final Screen parent, @Nullable final String page) {
        Minecraft.getInstance().setScreen(new ConfigHubScreen(parent, page));
    }

    public static Screen hub(@Nullable final Screen parent, @Nullable final String page) {
        return new ConfigHubScreen(parent, page);
    }

    /** {@code module} is the short name ({@code menu}, {@code chat}, {@code multiplayer}); the hook re-reads {@code config/slate/<module>.json}. */
    public static void registerReloadHook(final String module, final Runnable reload) {
        RELOAD_HOOKS.put(module, reload);
    }

    public static boolean hasReloadHook(final String module) {
        return RELOAD_HOOKS.containsKey(module);
    }

    public static void runReloadHook(final String module) {
        final Runnable r = RELOAD_HOOKS.get(module);
        if (r == null) return;
        try { r.run(); } catch (final Exception e) { SlateConfig.LOGGER.error("[Slate Config] reload hook of {} failed", module, e); }
    }

    private SlateConfigApi() {}
}
