package dev.fallingcloud.slate.menu.client.pause;

import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.menu.MenuConfig;
import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.menu.client.Fmt;
import dev.fallingcloud.slate.menu.client.LastPlayed;
import dev.fallingcloud.slate.menu.client.MenuClient;
import dev.fallingcloud.slate.menu.client.screenshots.SlateScreenshotsScreen;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerLinksScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerLinks;
import net.minecraft.util.CommonLinks;

/**
 * The Slate pause menu: a compact centred card (dark) or stone buttons (vanilla) with every vanilla
 * entry wired to the same vanilla code paths, plus Screenshots, Friends and the Slate hub. Shows the
 * world/server name and the time played this session; disconnecting asks first (configurable).
 */
public final class SlatePauseScreen extends SlateScreen {

    private static final int CARD_W = 220, PADDING = 10, ROW = 20, GAP = 4;

    private Rect card = new Rect(0, 0, 0, 0);
    private boolean disconnecting;

    public SlatePauseScreen() {
        super(Component.translatable("menu.game"), null);
        this.showHeader = false;
        this.showBack = false;
    }

    // ------------------------------------------------------------------ build

    @Override
    protected void build() {
        final Minecraft mc = Minecraft.getInstance();
        final MenuConfig cfg = SlateMenu.config();
        final boolean sp = mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null;
        final boolean lanOpen = sp && mc.getSingleplayerServer().isPublished();
        final ServerLinks links = mc.player != null ? mc.player.connection.serverLinks() : ServerLinks.EMPTY;

        final int bw = CARD_W - PADDING * 2, half = (bw - GAP) / 2;
        final List<AbstractWidget[]> rows = new ArrayList<>();
        rows.add(new AbstractWidget[] { new SlateButton(0, 0, bw, Component.translatable("menu.returnToGame"), () -> mc.setScreen(null))
            .variant(SlateButton.Variant.PRIMARY).icon(Icon.PLAY) });
        rows.add(new AbstractWidget[] {
            new SlateButton(0, 0, half, Component.translatable("gui.advancements"), () -> { if (mc.player != null) mc.setScreen(new AdvancementsScreen(mc.player.connection.getAdvancements(), this)); }).icon(Icon.TROPHY),
            new SlateButton(0, 0, half, Component.translatable("gui.stats"), () -> { if (mc.player != null) mc.setScreen(new StatsScreen(this, mc.player.getStats())); }).icon(Icon.HISTORY) });
        final SlateButton second;
        if (sp) {
            second = new SlateButton(0, 0, half, Component.translatable("menu.shareToLan"), () -> mc.setScreen(new ShareToLanScreen(this))).icon(Icon.LAN);
            second.enabled(!lanOpen);
            if (lanOpen) second.tip(Component.translatable("slate_menu.pause.lan_open"));
        } else if (!links.isEmpty()) {
            second = new SlateButton(0, 0, half, Component.translatable("menu.server_links"), () -> mc.setScreen(new ServerLinksScreen(this, links))).icon(Icon.LINK);
        } else {
            second = new SlateButton(0, 0, half, Component.translatable("menu.playerReporting"), () -> mc.setScreen(new net.minecraft.client.gui.screens.social.SocialInteractionsScreen(this))).icon(Icon.FRIENDS);
        }
        rows.add(new AbstractWidget[] {
            new SlateButton(0, 0, half, Component.translatable("menu.options"), () -> mc.setScreen(new OptionsScreen(this, mc.options))).icon(Icon.SETTINGS), second });
        final SlateButton shots = new SlateButton(0, 0, half, Component.translatable("slate_menu.screenshots.title"), () -> mc.setScreen(new SlateScreenshotsScreen(this))).icon(Icon.CAMERA);
        final SlateButton social = MenuClient.friendsScreenId()
            .map(id -> new SlateButton(0, 0, half, Component.translatable("slate_menu.title.friends"), () -> CoreActions.openScreen(id)).icon(Icon.FRIENDS))
            .orElseGet(() -> new SlateButton(0, 0, half, Component.translatable("slate.hub.title"), () -> CoreActions.openScreen("slate:hub")).icon(Icon.SLATE));
        rows.add(new AbstractWidget[] { shots, social });
        if (MenuClient.friendsScreenId().isPresent()) {
            rows.add(new AbstractWidget[] { new SlateButton(0, 0, bw, Component.translatable("slate.hub.title"), () -> CoreActions.openScreen("slate:hub"))
                .variant(SlateButton.Variant.GHOST).icon(Icon.SLATE) });
        }
        // Feedback links, as vanilla offers them.
        final URI feedback = SharedConstants.getCurrentVersion().isStable() ? CommonLinks.RELEASE_FEEDBACK : CommonLinks.SNAPSHOT_FEEDBACK;
        rows.add(new AbstractWidget[] {
            new SlateButton(0, 0, half, 16, Component.translatable("menu.sendFeedback"), () -> openLink(feedback)).variant(SlateButton.Variant.GHOST),
            new SlateButton(0, 0, half, 16, Component.translatable("menu.reportBugs"), () -> openLink(CommonLinks.SNAPSHOT_BUGS_FEEDBACK)).variant(SlateButton.Variant.GHOST) });
        rows.add(new AbstractWidget[] { new SlateButton(0, 0, bw, Component.translatable(sp ? "menu.returnToMenu" : "menu.disconnect"), this::confirmDisconnect)
            .variant(SlateButton.Variant.DANGER).icon(Icon.EXIT) });

        final int headerH = 30;
        int total = PADDING + headerH;
        for (final AbstractWidget[] r : rows) total += r[0].getHeight() + GAP;
        total += PADDING - GAP;
        final int x0 = (width - CARD_W) / 2;
        final int y0 = Math.max(4, (height - total) / 2);
        card = new Rect(x0, y0, CARD_W, total);
        int y = y0 + PADDING + headerH;
        for (final AbstractWidget[] r : rows) {
            int x = x0 + PADDING;
            for (final AbstractWidget w : r) {
                w.setX(x);
                w.setY(y);
                add(w);
                x += w.getWidth() + GAP;
            }
            y += r[0].getHeight() + GAP;
        }
        if (!cfg.showSessionTime) { /* header still shows the name */ }
    }

    private void openLink(final URI uri) {
        final Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ConfirmLinkScreen(ok -> {
            if (ok) Util.getPlatform().openUri(uri);
            mc.setScreen(this);
        }, uri.toString(), true));
    }

    // ------------------------------------------------------------------ disconnect (vanilla's exact sequence)

    private void confirmDisconnect() {
        final boolean sp = Minecraft.getInstance().hasSingleplayerServer();
        if (!SlateMenu.config().confirmQuit) { disconnect(); return; }
        SlateModal.confirm(Component.translatable(sp ? "menu.returnToMenu" : "menu.disconnect"),
            Component.translatable(sp ? "slate_menu.pause.confirm_quit_sp" : "slate_menu.pause.confirm_quit_mp"),
            Component.translatable(sp ? "menu.returnToMenu" : "menu.disconnect"), this::disconnect);
    }

    private void disconnect() {
        if (disconnecting) return;
        disconnecting = true;
        final Minecraft mc = Minecraft.getInstance();
        final boolean local = mc.isLocalServer();
        final ServerData server = mc.getCurrentServer();
        if (mc.level != null) mc.level.disconnect();
        if (local) mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
        else mc.disconnect();
        final TitleScreen title = new TitleScreen();
        if (local) mc.setScreen(title);
        else if (server != null && server.isRealm()) mc.setScreen(new com.mojang.realmsclient.RealmsMainScreen(title));
        else mc.setScreen(new JoinMultiplayerScreen(title));
    }

    // ------------------------------------------------------------------ render

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        final Theme t = Theme.current();
        if (!t.isVanilla()) {
            final Palette p = t.palette();
            SlateDraw.shadow(g, card.x(), card.y(), card.w(), card.h(), 0.5f);
            SlateDraw.panel(g, card.x(), card.y(), card.w(), card.h(), p.surface(), p.borderStrong());
        }
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        final Minecraft mc = Minecraft.getInstance();
        String name;
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) name = mc.getSingleplayerServer().getWorldData().getLevelName();
        else if (mc.getCurrentServer() != null) name = mc.getCurrentServer().name;
        else name = Component.translatable("menu.game").getString();
        if (name == null || name.isBlank()) name = Component.translatable("menu.game").getString();
        final Component heading = Fonts.heading(Component.literal(name));
        final int cx = card.centerX();
        g.drawString(font, SlateDraw.truncate(heading, CARD_W - PADDING * 2), cx - Math.min(font.width(heading), CARD_W - PADDING * 2) / 2, card.y() + PADDING, p.text(), van);
        if (SlateMenu.config().showSessionTime) {
            final long ms = LastPlayed.sessionMs();
            final Component sub = ms > 0
                ? Component.translatable("slate_menu.pause.session", Fmt.duration(ms))
                : Component.translatable("menu.paused");
            SlateDraw.textCentered(g, sub, cx, card.y() + PADDING + 14, van ? 0xFFC0C0C0 : p.textMuted());
        } else {
            SlateDraw.textCentered(g, Component.translatable("menu.game"), cx, card.y() + PADDING + 14, van ? 0xFFC0C0C0 : p.textMuted());
        }
    }
}
