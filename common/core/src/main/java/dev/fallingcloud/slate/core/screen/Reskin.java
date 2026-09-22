package dev.fallingcloud.slate.core.screen;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.config.CoreConfig;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * Scope rules for the generic restyle of vanilla widgets, backgrounds, lists and tooltips. The actual
 * drawing lives in the {@code core.mixin} widget mixins and the {@code core.screen.reskin} helpers; they
 * all ask {@link #active()} first ({@link #dark()} for the dark-only replacements). Container screens
 * (inventories, chests, JEI, ...) are never touched.
 */
public final class Reskin {

    private static Screen cachedScreen;
    private static boolean cachedResult;

    /** Whether the currently open screen is being restyled. Cheap: cached per screen instance. */
    public static boolean active() {
        final Screen s = Minecraft.getInstance().screen;
        if (s == null) return false;
        if (s == cachedScreen) return cachedResult;
        cachedScreen = s;
        cachedResult = inScope(s);
        return cachedResult;
    }

    /** {@link #active()} and the dark skin is on: vanilla drawing gets replaced by Slate's. */
    public static boolean dark() {
        return !Theme.current().isVanilla() && active();
    }

    /** {@link #active()} and the vanilla skin is on: vanilla sprites stay, only motion is added. */
    public static boolean vanillaMotion() {
        return Theme.current().isVanilla() && active();
    }

    public static boolean inScope(@Nullable final Screen s) {
        if (s == null || ScreenIds.isContainer(s)) return false;
        final CoreConfig cfg = Slate.config();
        final String cls = s.getClass().getName();
        for (final String deny : cfg.reskinDenylist) if (!deny.isEmpty() && cls.startsWith(deny)) return false;
        return switch (cfg.reskinScope == null ? "" : cfg.reskinScope.toUpperCase(java.util.Locale.ROOT)) {
            case "NONE" -> false;
            case "ALL_NON_CONTAINER" -> true;
            case "VANILLA_AND_SLATE" -> ScreenIds.isVanilla(s) || ScreenIds.isSlate(s);
            default -> {
                if (ScreenIds.isVanilla(s) || ScreenIds.isSlate(s)) yield true;
                for (final String allow : cfg.reskinAllowlist) {
                    if (allow.isEmpty()) continue;
                    if (cls.startsWith(allow) || ScreenIds.guessNamespace(cls).equals(allow)) yield true;
                }
                yield false;
            }
        };
    }

    /** Drop the cache (config changed). */
    public static void invalidate() {
        cachedScreen = null;
    }

    private Reskin() {}
}
