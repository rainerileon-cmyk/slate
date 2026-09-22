package dev.fallingcloud.slate.menu.client.options;

import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.ui.Flow;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.module.Modules;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.core.screen.SidebarPage;
import dev.fallingcloud.slate.core.screen.SidebarScreen;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateDropdown;
import dev.fallingcloud.slate.core.widget.SlateLabel;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateSeparator;
import dev.fallingcloud.slate.core.widget.SlateSlider;
import dev.fallingcloud.slate.core.widget.SlateToggle;
import dev.fallingcloud.slate.menu.MenuConfig;
import dev.fallingcloud.slate.menu.SlateMenu;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.NarratorStatus;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.CreditsAndAttributionScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OnlineOptionsScreen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.telemetry.TelemetryInfoScreen;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundLockDifficultyPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.jetbrains.annotations.Nullable;

/**
 * The Slate options hub ({@code slate_menu:options}): a sidebar whose first page inlines the most-used
 * vanilla options through their {@link OptionInstance}s (so every change goes through vanilla's own
 * update callbacks), and one page per vanilla category with its quick options plus a button that opens
 * the vanilla sub-screen (restyled by Core). Options are saved with a short debounce and on close.
 */
public final class SlateOptionsScreen extends SidebarScreen {

    private boolean dirty;
    private long dirtyMs;

    public SlateOptionsScreen(@Nullable final Screen parent) {
        super(Component.translatable("options.title"), parent, "slate_menu:options");
    }

    @Override
    protected void definePages(final List<SidebarPage> pages) {
        pages.add(new GeneralPage());
        pages.add(new Page("video", Component.translatable("options.video"), Icon.MONITOR, s -> new VideoSettingsScreen(s, Minecraft.getInstance(), opts()), this::videoQuick));
        pages.add(new Page("sounds", Component.translatable("options.sounds"), Icon.VOLUME, s -> new SoundOptionsScreen(s, opts()), this::soundQuick));
        pages.add(new Page("controls", Component.translatable("options.controls"), Icon.KEYBOARD, s -> new ControlsScreen(s, opts()), this::controlsQuick));
        pages.add(new Page("chat", Component.translatable("options.chat.title"), Icon.CHAT, s -> new ChatOptionsScreen(s, opts()), this::chatQuick));
        pages.add(new Page("skin", Component.translatable("options.skinCustomisation"), Icon.USER, s -> new SkinCustomizationScreen(s, opts()), this::skinQuick));
        pages.add(new Page("language", Component.translatable("options.language"), Icon.LANGUAGE, s -> new LanguageSelectScreen(s, opts(), Minecraft.getInstance().getLanguageManager()), this::languageQuick));
        pages.add(new Page("accessibility", Component.translatable("options.accessibility.title"), Icon.ACCESSIBILITY, s -> new AccessibilityOptionsScreen(s, opts()), this::accessibilityQuick));
        pages.add(new Page("online", Component.translatable("options.online"), Icon.WIFI, s -> new OnlineOptionsScreen(s, opts()), this::onlineQuick));
        pages.add(new Page("packs", Component.translatable("options.resourcepack"), Icon.PACK, s -> {
            final Minecraft mc = Minecraft.getInstance();
            return new PackSelectionScreen(mc.getResourcePackRepository(), repo -> { mc.options.updateResourcePacks(repo); mc.setScreen(s); },
                mc.getResourcePackDirectory(), Component.translatable("resourcePack.title"));
        }, null));
        pages.add(new Page("telemetry", Component.translatable("options.telemetry"), Icon.INFO, s -> new TelemetryInfoScreen(s, opts()), null));
        pages.add(new Page("credits", Component.translatable("options.credits_and_attribution"), Icon.HEART, CreditsAndAttributionScreen::new, null));
        pages.add(new SlatePage());
        pages.add(new MenuPage());
    }

    private static Options opts() { return Minecraft.getInstance().options; }

    // ------------------------------------------------------------------ saving

    private void markDirty() {
        dirty = true;
        dirtyMs = Clock.nowMs();
    }

    private void saveNow() {
        if (!dirty) return;
        dirty = false;
        opts().save();
    }

    @Override
    public void tick() {
        super.tick();
        if (dirty && Clock.nowMs() - dirtyMs > 800) saveNow();
    }

    @Override
    public void removed() {
        super.removed();
        saveNow();
    }

    // ------------------------------------------------------------------ option widget factories

    /** {min, max} of an int option, read through reflection because the ValueSet types are package-private. */
    private static int[] range(final OptionInstance<Integer> opt, final int defMin, final int defMax) {
        try {
            final Object v = OptionInstance.class.getMethod("values").invoke(opt);
            if (v instanceof OptionInstance.IntRange r) return new int[] { r.minInclusive(), r.maxInclusive() };
        } catch (final Throwable ignored) {}
        return new int[] { defMin, defMax };
    }

    private SlateSlider intSlider(final int w, final Component label, final OptionInstance<Integer> opt, final int defMin, final int defMax, final int step, @Nullable final DoubleFunction<String> fmt) {
        final int[] r = range(opt, defMin, defMax);
        return new SlateSlider(0, 0, w, label, r[0], r[1], step, opt.get(), fmt != null ? fmt : v -> Integer.toString((int) Math.round(v)),
            v -> { opt.set((int) Math.round(v)); markDirty(); }).compact(true);
    }

    private SlateSlider percentSlider(final int w, final Component label, final OptionInstance<Double> opt) {
        return new SlateSlider(0, 0, w, label, 0, 1, 0.01, opt.get(), v -> Math.round(v * 100) + "%",
            v -> { opt.set(v); markDirty(); }).compact(true);
    }

    private SlateToggle toggle(final int w, final Component label, final OptionInstance<Boolean> opt) {
        return new SlateToggle(0, 0, w, label, opt.get(), v -> { opt.set(v); markDirty(); });
    }

    private <T> SlateDropdown<T> dropdown(final int w, final Component label, final OptionInstance<T> opt, final List<T> values, final Function<T, Component> labeler) {
        return new SlateDropdown<>(0, 0, w, values, opt.get(), labeler, v -> { opt.set(v); markDirty(); }).label(label);
    }

    private SlateButton open(final int w, final Component label, final Icon icon, final Supplier<Screen> screen) {
        return new SlateButton(0, 0, w, label, () -> { saveNow(); Minecraft.getInstance().setScreen(screen.get()); }).icon(icon).variant(SlateButton.Variant.PRIMARY);
    }

    private static Component fovLabel(final double v) {
        final int i = (int) Math.round(v);
        if (i == 70) return Component.translatable("options.fov.min");
        if (i == 110) return Component.translatable("options.fov.max");
        return Component.literal(Integer.toString(i));
    }

    /** A page body: a scroll panel with a column flow; returns the panel + flow pair through the callback. */
    private void column(final SidebarScreen screen, final Rect area, final Consumer<Col> fill) {
        final SlateScrollPanel panel = screen.addPageWidget(new SlateScrollPanel(area.x(), area.y(), area.w(), area.h()));
        final Col col = new Col(panel, Math.min(area.w() - 10, 400));
        fill.accept(col);
        panel.setContentHeight(col.flow.maxY() + 4);
    }

    private static final class Col {
        final SlateScrollPanel panel;
        final Flow flow = Flow.column(0, 0, 6);
        final int w;

        Col(final SlateScrollPanel panel, final int w) { this.panel = panel; this.w = w; }

        <T extends net.minecraft.client.gui.components.AbstractWidget> T add(final T widget) {
            widget.setWidth(w);
            panel.add(flow.place(widget), widget.getX(), widget.getY());
            return widget;
        }

        void section(final Component caption) {
            add(new SlateSeparator(0, 0, w, caption));
        }

        void note(final Component text) {
            add(new SlateLabel(0, 0, w, text).style(SlateLabel.Style.MUTED).wrap(true));
        }
    }

    // ------------------------------------------------------------------ pages

    /** The inline "most used" page. */
    private final class GeneralPage extends SidebarPage {
        GeneralPage() { super("general", Component.translatable("slate_menu.options.general"), Icon.SLIDERS); }

        @Override
        public void build(final SidebarScreen screen, final Rect area) {
            final Minecraft mc = Minecraft.getInstance();
            final Options o = opts();
            column(screen, area, c -> {
                c.section(Component.translatable("slate_menu.options.section.play"));
                c.add(intSlider(c.w, Component.translatable("options.fov"), o.fov(), 30, 110, 1, v -> fovLabel(v).getString()));
                c.add(intSlider(c.w, Component.translatable("options.renderDistance"), o.renderDistance(), 2, 32, 1, v -> Component.translatable("options.chunks", (int) Math.round(v)).getString()));
                if (mc.level != null) {
                    final boolean canChange = mc.hasSingleplayerServer() && !mc.level.getLevelData().isDifficultyLocked();
                    final SlateDropdown<Difficulty> diff = new SlateDropdown<>(0, 0, c.w, Arrays.asList(Difficulty.values()), mc.level.getDifficulty(),
                        Difficulty::getDisplayName, d -> { if (mc.getConnection() != null) mc.getConnection().send(new ServerboundChangeDifficultyPacket(d)); })
                        .label(Component.translatable("options.difficulty"));
                    diff.enabled(canChange);
                    if (!canChange) diff.tip(Component.translatable(mc.level.getLevelData().isDifficultyLocked() ? "difficulty.lock.title" : "slate_menu.options.difficulty_server"));
                    c.add(diff);
                    if (canChange) {
                        c.add(new SlateButton(0, 0, c.w, Component.translatable("difficulty.lock.title"), () ->
                            SlateModal.confirmDanger(Component.translatable("difficulty.lock.title"), Component.translatable("difficulty.lock.question", mc.level.getDifficulty().getDisplayName()),
                                Component.translatable("difficulty.lock.title"), () -> { if (mc.getConnection() != null) mc.getConnection().send(new ServerboundLockDifficultyPacket(true)); refreshPage(); }))
                            .icon(Icon.LOCK).variant(SlateButton.Variant.GHOST));
                    }
                }
                c.section(Component.translatable("slate_menu.options.section.display"));
                final int maxScale = mc.getWindow().calculateScale(0, mc.isEnforceUnicode());
                final List<Integer> scales = new ArrayList<>();
                for (int i = 0; i <= maxScale; i++) scales.add(i);
                c.add(dropdown(c.w, Component.translatable("options.guiScale"), o.guiScale(), scales,
                    i -> i == 0 ? Component.translatable("options.guiScale.auto") : Component.literal(Integer.toString(i))));
                c.add(toggle(c.w, Component.translatable("options.fullscreen"), o.fullscreen()));
                c.add(toggle(c.w, Component.translatable("options.vsync"), o.enableVsync()));
                c.add(intSlider(c.w, Component.translatable("options.framerateLimit"), o.framerateLimit(), 10, 260, 10,
                    v -> Math.round(v) >= 260 ? Component.translatable("options.framerateLimit.max").getString() : Component.translatable("options.framerate", (int) Math.round(v)).getString()));
                c.section(Component.translatable("slate_menu.options.section.sound"));
                c.add(percentSlider(c.w, Component.translatable("soundCategory.master"), o.getSoundSourceOptionInstance(SoundSource.MASTER)));
                c.add(percentSlider(c.w, Component.translatable("soundCategory.music"), o.getSoundSourceOptionInstance(SoundSource.MUSIC)));
            });
        }
    }

    /** A vanilla category: quick options + "Open" button. */
    private final class Page extends SidebarPage {
        private final Function<Screen, Screen> opener;
        @Nullable private final Consumer<Col> quick;

        Page(final String id, final Component title, final Icon icon, final Function<Screen, Screen> opener, @Nullable final Consumer<Col> quick) {
            super(id, title, icon);
            this.opener = opener;
            this.quick = quick;
        }

        @Override
        public void build(final SidebarScreen screen, final Rect area) {
            column(screen, area, c -> {
                c.add(open(c.w, Component.translatable("slate_menu.options.open", title()), icon(), () -> opener.apply(SlateOptionsScreen.this)));
                c.note(Component.translatable("slate_menu.options.open_hint"));
                if (quick != null) {
                    c.section(Component.translatable("slate_menu.options.section.quick"));
                    quick.accept(c);
                }
            });
        }
    }

    private void videoQuick(final Col c) {
        final Options o = opts();
        c.add(intSlider(c.w, Component.translatable("options.renderDistance"), o.renderDistance(), 2, 32, 1, v -> Component.translatable("options.chunks", (int) Math.round(v)).getString()));
        c.add(intSlider(c.w, Component.translatable("options.simulationDistance"), o.simulationDistance(), 5, 32, 1, v -> Component.translatable("options.chunks", (int) Math.round(v)).getString()));
        c.add(percentSlider(c.w, Component.translatable("options.gamma"), o.gamma()));
        c.add(dropdown(c.w, Component.translatable("options.graphics"), o.graphicsMode(), Arrays.asList(net.minecraft.client.GraphicsStatus.values()), net.minecraft.client.GraphicsStatus::getCaption));
        c.add(toggle(c.w, Component.translatable("options.vsync"), o.enableVsync()));
        c.add(toggle(c.w, Component.translatable("options.fullscreen"), o.fullscreen()));
        c.add(intSlider(c.w, Component.translatable("options.framerateLimit"), o.framerateLimit(), 10, 260, 10,
            v -> Math.round(v) >= 260 ? Component.translatable("options.framerateLimit.max").getString() : Component.translatable("options.framerate", (int) Math.round(v)).getString()));
        c.add(toggle(c.w, Component.translatable("options.viewBobbing"), o.bobView()));
        c.add(toggle(c.w, Component.translatable("options.entityShadows"), o.entityShadows()));
    }

    private void soundQuick(final Col c) {
        final Options o = opts();
        for (final SoundSource s : SoundSource.values()) {
            c.add(percentSlider(c.w, Component.translatable("soundCategory." + s.getName()), o.getSoundSourceOptionInstance(s)));
        }
        c.add(toggle(c.w, Component.translatable("options.showSubtitles"), o.showSubtitles()));
        c.add(toggle(c.w, Component.translatable("options.directionalAudio"), o.directionalAudio()));
    }

    private void controlsQuick(final Col c) {
        final Options o = opts();
        c.add(open(c.w, Component.translatable("controls.keybinds.title"), Icon.KEYBOARD, () -> new KeyBindsScreen(this, o)).variant(SlateButton.Variant.SECONDARY));
        c.add(percentSlider(c.w, Component.translatable("options.sensitivity"), o.sensitivity()));
        c.add(toggle(c.w, Component.translatable("options.invertMouse"), o.invertYMouse()));
        c.add(toggle(c.w, Component.translatable("options.rawMouseInput"), o.rawMouseInput()));
        c.add(toggle(c.w, Component.translatable("options.autoJump"), o.autoJump()));
        c.add(toggle(c.w, Component.translatable("options.toggleCrouch"), o.toggleCrouch()));
        c.add(toggle(c.w, Component.translatable("options.discrete_mouse_scroll"), o.discreteMouseScroll()));
    }

    private void chatQuick(final Col c) {
        final Options o = opts();
        c.add(dropdown(c.w, Component.translatable("options.chat.visibility"), o.chatVisibility(), Arrays.asList(ChatVisiblity.values()), ChatVisiblity::getCaption));
        c.add(percentSlider(c.w, Component.translatable("options.chat.opacity"), o.chatOpacity()));
        c.add(percentSlider(c.w, Component.translatable("options.chat.scale"), o.chatScale()));
        c.add(toggle(c.w, Component.translatable("options.chat.links"), o.chatLinks()));
        c.add(toggle(c.w, Component.translatable("options.chat.links.prompt"), o.chatLinksPrompt()));
    }

    private void skinQuick(final Col c) {
        c.add(dropdown(c.w, Component.translatable("options.mainHand"), opts().mainHand(), Arrays.asList(HumanoidArm.values()), HumanoidArm::getCaption));
    }

    private void languageQuick(final Col c) {
        final Minecraft mc = Minecraft.getInstance();
        final LanguageInfo info = mc.getLanguageManager().getLanguage(mc.getLanguageManager().getSelected());
        c.note(Component.translatable("slate_menu.options.current_language", info == null ? Component.literal(mc.getLanguageManager().getSelected()) : info.toComponent()));
        c.add(toggle(c.w, Component.translatable("options.forceUnicodeFont"), opts().forceUnicodeFont()));
    }

    private void accessibilityQuick(final Col c) {
        final Options o = opts();
        c.add(dropdown(c.w, Component.translatable("options.narrator"), o.narrator(), Arrays.asList(NarratorStatus.values()), NarratorStatus::getName));
        c.add(toggle(c.w, Component.translatable("options.accessibility.high_contrast"), o.highContrast()));
        c.add(toggle(c.w, Component.translatable("options.reducedDebugInfo"), o.reducedDebugInfo()));
        c.add(toggle(c.w, Component.translatable("options.hideLightningFlashes"), o.hideLightningFlash()));
        c.add(toggle(c.w, Component.translatable("options.darkMojangStudiosBackgroundColor"), o.darkMojangStudiosBackground()));
        c.add(percentSlider(c.w, Component.translatable("options.accessibility.panorama_speed"), o.panoramaSpeed()));
        c.add(percentSlider(c.w, Component.translatable("options.screenEffectScale"), o.screenEffectScale()));
        c.add(percentSlider(c.w, Component.translatable("options.fovEffectScale"), o.fovEffectScale()));
    }

    private void onlineQuick(final Col c) {
        final Options o = opts();
        c.add(toggle(c.w, Component.translatable("options.realmsNotifications"), o.realmsNotifications()));
        c.add(toggle(c.w, Component.translatable("options.allowServerListing"), o.allowServerListing()));
    }

    /** Slate's own screens. */
    private final class SlatePage extends SidebarPage {
        SlatePage() { super("slate", Component.translatable("slate.name"), Icon.SLATE); }

        @Override
        public void build(final SidebarScreen screen, final Rect area) {
            column(screen, area, c -> {
                c.add(open(c.w, Component.translatable("slate.settings.title"), Icon.PALETTE, () -> CoreActions.SCREEN_FACTORIES.get("slate:settings").apply(SlateOptionsScreen.this)));
                c.add(open(c.w, Component.translatable("slate.hub.title"), Icon.SLATE, () -> CoreActions.SCREEN_FACTORIES.get("slate:hub").apply(SlateOptionsScreen.this)).variant(SlateButton.Variant.SECONDARY));
                if (Modules.isLoaded("slate_config")) {
                    final Function<Screen, Screen> hub = CoreActions.SCREEN_FACTORIES.get("slate_config:hub");
                    if (hub != null) {
                        c.add(open(c.w, Component.translatable("slate_menu.options.config_hub"), Icon.SETTINGS, () -> hub.apply(SlateOptionsScreen.this)).variant(SlateButton.Variant.SECONDARY));
                    } else {
                        SlatePlatform.get().otherModConfigScreen("slate_config").ifPresent(f ->
                            c.add(open(c.w, Component.translatable("slate_menu.options.config_hub"), Icon.SETTINGS, () -> f.apply(SlateOptionsScreen.this)).variant(SlateButton.Variant.SECONDARY)));
                    }
                }
                c.note(Component.translatable("slate_menu.options.slate_hint"));
            });
        }
    }

    /** menu.json toggles. */
    private final class MenuPage extends SidebarPage {
        MenuPage() { super("menu", Component.translatable("slate_menu.name"), Icon.HOME); }

        private SlateToggle cfgToggle(final int w, final String key, final boolean value, final Consumer<Boolean> set) {
            return new SlateToggle(0, 0, w, Component.translatable("slate_menu.options.menu." + key), value, v -> SlateMenu.configFile().update(c -> set.accept(v)));
        }

        @Override
        public void build(final SidebarScreen screen, final Rect area) {
            final MenuConfig m = SlateMenu.config();
            column(screen, area, c -> {
                c.section(Component.translatable("slate_menu.options.menu.section.screens"));
                c.add(cfgToggle(c.w, "title_screen", m.titleScreen, v -> m.titleScreen = v));
                c.add(cfgToggle(c.w, "worlds_screen", m.worldsScreen, v -> m.worldsScreen = v));
                c.add(cfgToggle(c.w, "servers_screen", m.serversScreen, v -> m.serversScreen = v));
                c.add(cfgToggle(c.w, "pause_screen", m.pauseScreen, v -> m.pauseScreen = v));
                c.add(cfgToggle(c.w, "options_screen", m.optionsScreen, v -> m.optionsScreen = v));
                c.add(cfgToggle(c.w, "disconnected_screen", m.disconnectedScreen, v -> m.disconnectedScreen = v));
                c.section(Component.translatable("slate_menu.options.menu.section.title"));
                c.add(cfgToggle(c.w, "panorama", m.panorama, v -> m.panorama = v));
                c.add(cfgToggle(c.w, "splash", m.splash, v -> m.splash = v));
                c.add(cfgToggle(c.w, "show_logo", m.showLogo, v -> m.showLogo = v));
                c.add(cfgToggle(c.w, "continue_card", m.showContinueCard, v -> m.showContinueCard = v));
                c.add(cfgToggle(c.w, "account_card", m.showAccountCard, v -> m.showAccountCard = v));
                c.add(cfgToggle(c.w, "friends_panel", m.showFriendsPanel, v -> m.showFriendsPanel = v));
                c.add(cfgToggle(c.w, "mods_button", m.showModsButton, v -> m.showModsButton = v));
                c.add(cfgToggle(c.w, "footer", m.showFooter, v -> m.showFooter = v));
                c.section(Component.translatable("slate_menu.options.menu.section.lists"));
                c.add(cfgToggle(c.w, "world_details", m.worldsShowDetails, v -> m.worldsShowDetails = v));
                c.add(cfgToggle(c.w, "auto_refresh", m.serverAutoRefresh, v -> m.serverAutoRefresh = v));
                c.add(new SlateSlider(0, 0, c.w, Component.translatable("slate_menu.options.menu.refresh_seconds"), 5, 120, 5, m.serverRefreshSeconds,
                    v -> (int) v + " s", v -> SlateMenu.configFile().update(x -> x.serverRefreshSeconds = (int) v)).compact(true));
                c.add(cfgToggle(c.w, "show_lan", m.showLan, v -> m.showLan = v));
                c.add(cfgToggle(c.w, "show_recent", m.showRecent, v -> m.showRecent = v));
                c.add(cfgToggle(c.w, "show_community", m.showCommunity, v -> m.showCommunity = v));
                c.section(Component.translatable("slate_menu.options.menu.section.pause"));
                c.add(cfgToggle(c.w, "confirm_quit", m.confirmQuit, v -> m.confirmQuit = v));
                c.add(cfgToggle(c.w, "session_time", m.showSessionTime, v -> m.showSessionTime = v));
            });
        }
    }
}
