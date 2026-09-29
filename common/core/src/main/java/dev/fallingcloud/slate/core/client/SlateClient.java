package dev.fallingcloud.slate.core.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.event.SlateKeys;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.layout.LayoutApplier;
import dev.fallingcloud.slate.core.layout.editor.LayoutEditor;
import dev.fallingcloud.slate.core.screen.ScreenIds;
import dev.fallingcloud.slate.core.screen.slot.EarlyWindowProps;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.core.screen.Transitions;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import dev.fallingcloud.slate.core.widget.SlateTooltips;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * Client bootstrap for Core: keys, built-in actions and element types, and the glue between the
 * mixin/loader hooks and the systems (layouts, popups, toasts, tooltips, transitions, editor).
 */
public final class SlateClient {

    public static final KeyMapping EDITOR_KEY = new KeyMapping("key.slate.editor", InputConstants.Type.KEYSYM, InputConstants.KEY_F7, SlateKeys.CATEGORY);
    public static final KeyMapping HUB_KEY = new KeyMapping("key.slate.hub", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), SlateKeys.CATEGORY);

    private static boolean initialised;
    private static boolean propsWritten;
    @Nullable private static Screen lastScreen;

    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        Slate.init();
        // Menu slots: the open screen's slot decides the style variant, and the start-up window's settings file
        // follows every change of theme, slot or provider.
        Theme.setActiveStyleResolver(() -> MenuSlots.styleFor(Minecraft.getInstance().screen));
        Theme.onChange(EarlyWindowProps::write);
        MenuSlots.onChange(EarlyWindowProps::write);
        Theme.reload();
        SlateKeys.register(EDITOR_KEY);
        SlateKeys.register(HUB_KEY);
        CoreActions.registerAll();
        CoreElements.registerAll();
        ScreenIds.register(SlateHubScreen.class, "slate:hub", "Slate hub");
        ScreenIds.register(CoreSettingsScreen.class, "slate:settings", "Slate settings");
        ScreenIds.register(dev.fallingcloud.slate.core.client.setup.SlateSetupScreen.class, "slate:setup", "Slate setup");
        ScreenIds.register(FeatureTestScreen.class, "slate:feature_test", "Feature gates (harness)");
        CoreActions.SCREEN_FACTORIES.put("slate:feature_test", FeatureTestScreen::new);
        CoreActions.SCREEN_FACTORIES.put("slate:setup", p -> new dev.fallingcloud.slate.core.client.setup.SlateSetupScreen(p));
        VanillaScreenButtons.init();
        DevModeButton.init();
        SlateEvents.CLIENT_TICK_END.register(SlateClient::tick);
        SlateEvents.KEY_PRESSED.register((key, scan, mods) -> {
            if (HUB_KEY.matches(key, scan) && !HUB_KEY.isUnbound()) { Minecraft.getInstance().setScreen(new SlateHubScreen(null)); return true; }
            return false;
        });
        DevHarness.init();
        dev.fallingcloud.slate.core.stage.StageBootstrap.init();
        Slate.LOGGER.info("[Slate] client ready ({} skin)", Theme.current().skin());
    }

    private static void tick() {
        // Once every module has provided its layouts: the settings file the NeoForge start-up window reads.
        if (!propsWritten) { propsWritten = true; EarlyWindowProps.write(); }
        // The editor key works in-game (no screen) and on screens (handled in ScreenInput).
        while (EDITOR_KEY.consumeClick()) {
            if (Minecraft.getInstance().screen == null && Slate.config().devMode) LayoutEditor.toggle();
        }
    }

    // ------------------------------------------------------------------ hooks called by mixins

    /** Minecraft.setScreen decided which screen shows (before its init): switch the theme to its slot's style. */
    public static void beforeScreenShown(@Nullable final Screen screen) {
        Theme.activate(screen == null ? null : MenuSlots.styleFor(screen));
    }

    /** Minecraft.setScreen completed. */
    public static void onScreenChanged(@Nullable final Screen screen) {
        Popups.onScreenChanged(screen);
        LayoutEditor.onScreenChanged(screen);
        Transitions.onScreenChanged(lastScreen, screen);
        lastScreen = screen;
        SlateEvents.SCREEN_OPENED.invoke(l -> l.accept(screen));
    }

    /** A screen's init finished (first init and every resize/rebuild). */
    public static void onScreenInit(final Screen screen) {
        LayoutApplier.apply(screen);
        SlateEvents.SCREEN_INIT_POST.invoke(l -> l.accept(screen));
        LayoutEditor.onScreenInit(screen);
    }

    /** A screen rendered (renderWithTooltip TAIL): overlays in z-order. */
    public static void onScreenRendered(final Screen screen, final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        Clock.onFrame();
        SlateEvents.SCREEN_RENDER_POST.invoke(l -> l.render(screen, g, mouseX, mouseY, partialTick));
        LayoutEditor.render(g, screen, mouseX, mouseY, partialTick);
        Popups.render(g, mouseX, mouseY, partialTick, screen.width, screen.height);
        SlateToasts.render(g, mouseX, mouseY, screen.width);
        SlateTooltips.render(g, mouseX, mouseY, screen.width, screen.height);
        Transitions.render(g, screen, screen.width, screen.height);
    }

    /** The in-game HUD rendered (no screen open): toasts still show. */
    public static void onHudRendered(final GuiGraphics g, final float partialTick) {
        if (Minecraft.getInstance().screen == null) Clock.onFrame();
        SlateEvents.HUD_RENDER.invoke(l -> l.render(g, partialTick));
        if (Minecraft.getInstance().screen == null) SlateToasts.render(g, -1, -1, g.guiWidth());
    }

    private SlateClient() {}
}
