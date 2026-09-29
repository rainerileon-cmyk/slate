package dev.fallingcloud.slate.menu.client.title;

import com.mojang.math.Axis;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ElementType;
import dev.fallingcloud.slate.core.layout.ElementTypes;
import dev.fallingcloud.slate.core.layout.ScreenLayout;
import dev.fallingcloud.slate.core.module.Features;
import dev.fallingcloud.slate.core.module.KnownModules;
import dev.fallingcloud.slate.core.module.Modules;
import dev.fallingcloud.slate.core.screen.slot.CoreSlots;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.menu.MenuConfig;
import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.menu.client.MenuClient;
import dev.fallingcloud.slate.menu.client.ModsScreenOpener;
import dev.fallingcloud.slate.menu.client.screenshots.SlateScreenshotsScreen;
import dev.fallingcloud.slate.menu.mixin.SplashRendererAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The Slate title screen: panorama + logo (or Slate wordmark), a column of large nav buttons, the
 * continue card, the account card, the Multiplayer module's friends panel (opt-in), splash text,
 * footer. Layout adapts to the window: a two-column layout from ~400 px, compact rows below 300 px high.
 * Every button opens the vanilla screen class so the per-screen swap toggles decide what shows.
 */
public final class SlateTitleScreen extends SlateScreen {

    private static final int NAV_W = 176;

    private final LogoRenderer logo = new LogoRenderer(false);
    private final long openedMs = Clock.nowMs();
    @Nullable private String splash;
    @Nullable private ContinueCard continueCard;
    private int logoY;
    private int navBottom;
    private int navLeft;
    private boolean wide;

    public SlateTitleScreen() {
        super(Component.translatable("narrator.screen.title"), null);
        this.showHeader = false;
        this.showBack = false;
    }

    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override public boolean isPauseScreen() { return false; }

    // ------------------------------------------------------------------ build

    @Override
    protected void build() {
        final Minecraft mc = Minecraft.getInstance();
        final MenuConfig cfg = SlateMenu.config();
        if (continueCard != null) { continueCard.close(); continueCard = null; }
        if (splash == null && cfg.splash) {
            final SplashRenderer s = mc.getSplashManager().getSplash();
            splash = s == null ? null : ((SplashRendererAccessor) s).slate$splash();
        }
        final boolean compact = height < 300;
        wide = width >= 400;
        logoY = compact ? 4 : 20;
        final int top = logoY + 51 + (compact ? 8 : 14);
        final int rowH = compact ? 20 : NavButton.HEIGHT, gap = compact ? 2 : 4;
        final int contentW = Math.min(width - PAD * 2, 560);
        navLeft = wide ? (width - contentW) / 2 : (width - NAV_W) / 2;

        // ---- nav column
        int y = top;
        for (final NavButton b : navButtons(mc, cfg)) {
            b.setX(navLeft);
            b.setY(y);
            b.setHeight(rowH);
            add(b);
            y += rowH + gap;
        }
        navBottom = y - gap;

        // ---- right column (cards + icon row + friends panel) or stacked below the nav
        final int footerTop = height - 12;
        int cx, cy, cw;
        if (wide) { cx = navLeft + NAV_W + 20; cy = top; cw = contentW - NAV_W - 20; }
        else { cx = navLeft; cy = navBottom + 8; cw = NAV_W; }
        if (cfg.showContinueCard && cy + ContinueCard.HEIGHT <= footerTop) {
            continueCard = add(new ContinueCard(cx, cy, cw, ContinueCard.HEIGHT, this));
            cy += ContinueCard.HEIGHT + 8;
        }
        if (cfg.showAccountCard && cy + AccountCard.HEIGHT <= footerTop) {
            add(new AccountCard(cx, cy, cw, AccountCard.HEIGHT, this));
            cy += AccountCard.HEIGHT + 8;
        }
        if (cy + 20 <= footerTop) {
            int bx = cx;
            add(new SlateIconButton(bx, cy, 20, Icon.LANGUAGE, Component.translatable("options.language"),
                () -> mc.setScreen(new LanguageSelectScreen(this, mc.options, mc.getLanguageManager()))));
            bx += 24;
            add(new SlateIconButton(bx, cy, 20, Icon.ACCESSIBILITY, Component.translatable("options.accessibility.title"),
                () -> mc.setScreen(new AccessibilityOptionsScreen(this, mc.options))));
            bx += 24;
            if (mc.allowsRealms()) {
                add(new SlateIconButton(bx, cy, 20, Icon.REALMS, Component.translatable("menu.online"),
                    () -> mc.setScreen(new com.mojang.realmsclient.RealmsMainScreen(this))));
                bx += 24;
            }
            // No Slate button here: the custom layout reaches Slate through Options (vanilla's own title screen gets one).
            cy += 28;
        }
        if (wide && cfg.showFriendsPanel && Modules.isLoaded("slate_multiplayer")) {
            final int fh = footerTop - 6 - cy;
            if (fh >= 60) friendsPanel(cx, cy, cw, fh).ifPresent(this::add);
        }
    }

    private List<NavButton> navButtons(final Minecraft mc, final MenuConfig cfg) {
        final List<NavButton> nav = new ArrayList<>();
        nav.add(new NavButton(0, 0, NAV_W, Icon.SINGLEPLAYER, Component.translatable("menu.singleplayer"), () -> mc.setScreen(new SelectWorldScreen(this))));
        final NavButton mp = new NavButton(0, 0, NAV_W, Icon.MULTIPLAYER, Component.translatable("menu.multiplayer"), () -> mc.setScreen(new JoinMultiplayerScreen(this)));
        if (!mc.allowsMultiplayer()) {
            mp.enabled(false);
            mp.tip(mc.isNameBanned() ? Component.translatable("title.multiplayer.disabled.banned.name") : Component.translatable("title.multiplayer.disabled"));
        }
        nav.add(mp);
        // Friends needs the Multiplayer module: the Custom layout leaves the button out when it is missing (R3).
        if (Features.present(KnownModules.MULTIPLAYER)) {
            nav.add(new NavButton(0, 0, NAV_W, Icon.FRIENDS, Component.translatable("slate_menu.title.friends"), () -> MenuSlots.open(CoreSlots.FRIENDS, this)));
        }
        nav.add(new NavButton(0, 0, NAV_W, Icon.CAMERA, Component.translatable("slate_menu.screenshots.title"), () -> mc.setScreen(new SlateScreenshotsScreen(this))));
        if (cfg.showModsButton) {
            final Optional<Function<Screen, Screen>> mods = ModsScreenOpener.factory();
            mods.ifPresent(f -> nav.add(new NavButton(0, 0, NAV_W, Icon.MODS, Component.translatable("slate_menu.title.mods"), () -> mc.setScreen(f.apply(this)))));
        }
        nav.add(new NavButton(0, 0, NAV_W, Icon.SETTINGS, Component.translatable("menu.options"), () -> mc.setScreen(new OptionsScreen(this, mc.options))));
        nav.add(new NavButton(0, 0, NAV_W, Icon.QUIT, Component.translatable("menu.quit"), () -> quit(mc)).danger());
        return nav;
    }

    /** The danger dialog has no PRIMARY button, so a stray Enter cannot confirm it. */
    private static void quit(final Minecraft mc) {
        if (!SlateMenu.config().confirmQuitGame) { mc.stop(); return; }
        SlateModal.confirmDanger(Component.translatable("menu.quit"), Component.translatable("slate_menu.title.confirm_quit"),
            Component.translatable("menu.quit"), mc::stop);
    }

    private Optional<AbstractWidget> friendsPanel(final int x, final int y, final int w, final int h) {
        final ElementType type = ElementTypes.get("slate_multiplayer:friends_panel").orElse(null);
        if (type == null) return Optional.empty();
        try {
            final ScreenLayout.Element e = new ScreenLayout.Element();
            e.id = "title_friends";
            e.type = type.id();
            e.place = new ScreenLayout.Placement("TOP_LEFT", x, y, w, h);
            return Optional.ofNullable(type.create(this, e, x, y, w, h, () -> {}));
        } catch (final Exception ex) {
            SlateMenu.LOGGER.warn("[Slate Menu] friends panel failed to build: {}", ex.toString());
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void tick() {
        super.tick();
        if (continueCard != null) continueCard.tick();
    }

    @Override
    public void removed() {
        super.removed();
        if (continueCard != null) continueCard.close();
    }

    // ------------------------------------------------------------------ rendering

    private float fadeIn() {
        if (Theme.current().motion() <= 0) return 1f;
        return Mth.clamp((Clock.nowMs() - openedMs) / 700f, 0f, 1f);
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final MenuConfig cfg = SlateMenu.config();
        final Theme t = Theme.current();
        final float fade = fadeIn();
        if (cfg.panorama) {
            PANORAMA.render(g, width, height, fade, partialTick);
            if (!t.isVanilla()) {
                g.fill(0, 0, width, height, Colors.withAlpha(0x0E0E0D, Math.round(0x60 * fade)));
                SlateDraw.hgradient(g, 0, 0, Math.min(width, 240), height, Colors.withAlpha(0x000000, Math.round(0x58 * fade)), 0);
                SlateDraw.vgradient(g, 0, height - 70, width, 70, 0, Colors.withAlpha(0x000000, Math.round(0x90 * fade)));
            }
        } else if (t.isVanilla()) {
            SlateDraw.vanillaPanel(g, 0, 0, width, height, false);
        } else {
            super.renderBackground(g, mouseX, mouseY, partialTick);
        }
        renderLogo(g, fade);
        // The nav column rests on a translucent plate (the build menu's floating panel), so the buttons form one
        // block over the panorama instead of floating loose.
        if (!t.isVanilla() && navBottom > 0) {
            final int plateTop = logoY + 51 + (height < 300 ? 8 : 14) - 6;
            SlateDraw.floatingPanel(g, navLeft - 6, plateTop, NAV_W + 12, navBottom - plateTop + 6, fade);
        }
    }

    private void renderLogo(final GuiGraphics g, final float alpha) {
        if (SlateMenu.config().showLogo) {
            logo.renderLogo(g, width, alpha, logoY);
            return;
        }
        final Palette p = Theme.current().palette();
        g.pose().pushPose();
        g.pose().translate(width / 2f, logoY + 8, 0);
        g.pose().scale(3f, 3f, 1f);
        final Component word = Fonts.heading(Component.literal("Slate"));
        g.drawString(font, word, -font.width(word) / 2, 0, Colors.scaleAlpha(p.text(), alpha), Theme.current().isVanilla());
        g.pose().popPose();
        final Component sub = Component.translatable("slate_menu.title.wordmark_sub");
        g.drawString(font, sub, width / 2 - font.width(sub) / 2, logoY + 40, Colors.scaleAlpha(Theme.current().isVanilla() ? 0xFFC0C0C0 : p.textMuted(), alpha), Theme.current().isVanilla());
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float fade = fadeIn();
        if (splash != null && !splash.isEmpty()) renderSplash(g, fade);
        if (!SlateMenu.config().showFooter) return;
        // Footer: version + loader + mod count; copyright as vanilla requires.
        final int fg = Colors.scaleAlpha(t.isVanilla() ? 0xFFFFFFFF : p.textMuted(), fade);
        final Component version = Component.literal("Minecraft " + SharedConstants.getCurrentVersion().getName() + " · "
            + SlatePlatform.get().loader().displayName + " · " + SlatePlatform.get().allMods().size() + " mods · Slate " + Slate.version());
        final Component copyright = Component.translatable("title.credits");
        final int footerY = height - 11;
        if (navBottom + 4 <= footerY && width - font.width(copyright) - PAD * 2 > navLeft + font.width(version)) {
            g.drawString(font, version, navLeft, footerY, fg, t.isVanilla());
            g.drawString(font, copyright, width - PAD - font.width(copyright), footerY, fg, t.isVanilla());
        } else {
            g.drawString(font, copyright, width - PAD - font.width(copyright), footerY, fg, t.isVanilla());
            g.drawString(font, version, width - PAD - font.width(version), footerY - 10, fg, t.isVanilla());
        }
    }

    /** Vanilla's splash placement and pulse, plus a scale-in on open; accent-coloured on the dark skin. */
    private void renderSplash(final GuiGraphics g, final float fade) {
        final Theme t = Theme.current();
        final float in = t.motion() <= 0 ? 1f : dev.fallingcloud.slate.core.gfx.Ease.OUT_BACK.apply(Mth.clamp((Clock.nowMs() - openedMs - 300) / 400f, 0f, 1f));
        if (in <= 0.01f) return;
        g.pose().pushPose();
        g.pose().translate(width / 2f + 123f, logoY + 39f, 0f);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-20f));
        float s = 1.8f - Mth.abs(Mth.sin((float) (Util.getMillis() % 1000L) / 1000f * ((float) Math.PI * 2f)) * 0.1f);
        s = s * 100f / (font.width(splash) + 32);
        s *= in;
        g.pose().scale(s, s, s);
        final int color = t.isVanilla() ? 0xFFFFFF00 : t.accent();
        g.drawCenteredString(font, splash, 0, -8, Colors.scaleAlpha(color, fade));
        g.pose().popPose();
    }
}
