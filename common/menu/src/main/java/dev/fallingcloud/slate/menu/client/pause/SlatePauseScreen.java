package dev.fallingcloud.slate.menu.client.pause;

import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.module.Features;
import dev.fallingcloud.slate.core.module.KnownModules;
import dev.fallingcloud.slate.core.screen.slot.CoreSlots;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.menu.client.Fmt;
import dev.fallingcloud.slate.menu.client.LastPlayed;
import dev.fallingcloud.slate.menu.client.MenuClient;
import dev.fallingcloud.slate.menu.client.screenshots.SlateScreenshotsScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
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

/**
 * The Slate pause menu (the custom layout's escape menu): one floating card in the build menu's style over the
 * blurred world. Its header names the world or server, shows the time played this session and a row of chips
 * (game mode, dimension, in-game day and time). Below it the vanilla actions grouped by rules: Back to game;
 * Advancements | Statistics; Options | Open to LAN (or server links / player reporting); Screenshots | Friends;
 * and Save & quit / Disconnect, which asks first (configurable). Vanilla's feedback and bug-report links are gone,
 * and Slate itself is reached through Options (its settings hub), so the card carries no Slate button.
 */
public final class SlatePauseScreen extends SlateScreen {

    private static final int CARD_W = 236, PADDING = 12, GAP = 4, RULE_H = 9, HEADER_H = 46;

    private final Anim open = new Anim(0, 240, Ease.OUT_CUBIC);
    private Rect card = new Rect(0, 0, 0, 0);
    private final List<Integer> rules = new ArrayList<>();
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
        if (open.target() == 0f) open.set(1f);
        rules.clear();
        final boolean sp = mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null;
        final boolean lanOpen = sp && mc.getSingleplayerServer().isPublished();
        final ServerLinks links = mc.player != null ? mc.player.connection.serverLinks() : ServerLinks.EMPTY;

        final int bw = CARD_W - PADDING * 2, half = (bw - GAP) / 2;
        // Rows of widgets; a null row is a rule between groups.
        final List<AbstractWidget[]> rows = new ArrayList<>();
        rows.add(new AbstractWidget[] { new SlateButton(0, 0, bw, SlateButton.HEIGHT_LARGE, Component.translatable("menu.returnToGame"), () -> mc.setScreen(null))
            .variant(SlateButton.Variant.PRIMARY).icon(Icon.PLAY) });
        rows.add(null);
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
        // Friends needs the Multiplayer module: the Custom layout leaves the button out when it is missing (R3).
        final SlateButton friends = Features.present(KnownModules.MULTIPLAYER)
            ? new SlateButton(0, 0, half, Component.translatable("slate_menu.title.friends"), () -> MenuSlots.open(CoreSlots.FRIENDS, this)).icon(Icon.FRIENDS)
            : null;
        if (friends != null) rows.add(new AbstractWidget[] { shots, friends });
        else { shots.setWidth(bw); rows.add(new AbstractWidget[] { shots }); }
        rows.add(null);
        rows.add(new AbstractWidget[] { new SlateButton(0, 0, bw, Component.translatable(sp ? "menu.returnToMenu" : "menu.disconnect"), this::confirmDisconnect)
            .variant(SlateButton.Variant.DANGER).icon(Icon.EXIT) });

        int total = PADDING + HEADER_H;
        for (final AbstractWidget[] r : rows) total += (r == null ? RULE_H : r[0].getHeight()) + GAP;
        total += PADDING - GAP;
        final int x0 = (width - CARD_W) / 2;
        final int y0 = Math.max(4, (height - total) / 2);
        card = new Rect(x0, y0, CARD_W, total);
        int y = y0 + PADDING + HEADER_H;
        for (final AbstractWidget[] r : rows) {
            if (r == null) { rules.add(y + RULE_H / 2); y += RULE_H + GAP; continue; }
            int x = x0 + PADDING;
            for (final AbstractWidget w : r) {
                w.setX(x);
                w.setY(y);
                add(w);
                x += w.getWidth() + GAP;
            }
            y += r[0].getHeight() + GAP;
        }
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
        final float a = open.get();
        SlateDraw.floatingPanel(g, card.x(), card.y(), card.w(), card.h(), a);     // the one plate, in either style
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        final float a = open.get();
        final Minecraft mc = Minecraft.getInstance();
        final int muted = van ? 0xFFC0C0C0 : p.textMuted();
        final int inner = CARD_W - PADDING * 2, lx = card.x() + PADDING;

        // Name, session, chips.
        String name;
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) name = mc.getSingleplayerServer().getWorldData().getLevelName();
        else if (mc.getCurrentServer() != null) name = mc.getCurrentServer().name;
        else name = Component.translatable("menu.game").getString();
        if (name == null || name.isBlank()) name = Component.translatable("menu.game").getString();
        final Component heading = Fonts.heading(Component.literal(name));
        final net.minecraft.util.FormattedCharSequence title = SlateDraw.truncate(heading, inner);
        g.drawString(font, title, lx, card.y() + PADDING, Colors.scaleAlpha(van ? 0xFFFFFFFF : p.text(), a), van);
        if (!van) SlateDraw.accentCap(g, lx, card.y() + PADDING + 11, Math.max(16, Math.min(font.width(title), 40)), a);
        final Component sub;
        if (SlateMenu.config().showSessionTime && LastPlayed.sessionMs() > 0) sub = Component.translatable("slate_menu.pause.session", Fmt.duration(LastPlayed.sessionMs()));
        else sub = Component.translatable("menu.paused");
        g.drawString(font, SlateDraw.truncate(sub, inner), lx, card.y() + PADDING + 15, Colors.scaleAlpha(muted, a), van);
        chips(g, lx, card.y() + PADDING + 28, inner, a, p, mc);

        // Rules between the groups, as the build menu separates its header, table and options.
        final int ruleCol = Colors.scaleAlpha(van ? 0x40FFFFFF : p.border(), a);
        for (final int ry : rules) SlateDraw.hline(g, lx, ry, inner, ruleCol);
    }

    /** Game mode, dimension and in-game clock as chips; each is skipped when it does not fit. */
    private void chips(final GuiGraphics g, int x, final int y, final int w, final float a, final Palette p, final Minecraft mc) {
        final int right = x + w;
        if (mc.gameMode != null) x = chip(g, mc.gameMode.getPlayerMode().getShortDisplayName(), x, y, right, p.accent(), a);
        if (mc.level != null) {
            x = chip(g, Component.literal(pretty(mc.level.dimension().location().getPath())), x, y, right, p.success(), a);
            final long time = mc.level.getDayTime();
            final long day = time / 24000L + 1;
            final long tod = time % 24000L;
            final int hour = (int) ((tod / 1000L + 6L) % 24L), minute = (int) ((tod % 1000L) * 60L / 1000L);
            x = chip(g, Component.translatable("slate_menu.pause.day_time", day, String.format(Locale.ROOT, "%02d:%02d", hour, minute)), x, y, right, p.warning(), a);
            chip(g, mc.level.getDifficulty().getDisplayName(), x, y, right, p.textMuted(), a);
        }
    }

    /** A chip if it fits before {@code right}; returns the next x (or {@code right} once one did not fit so later chips skip too). */
    private static int chip(final GuiGraphics g, final Component text, final int x, final int y, final int right, final int color, final float a) {
        if (x + SlateDraw.width(text) + 8 > right) return right;
        return x + SlateDraw.chip(g, text, x, y, color, a) + 4;
    }

    /** {@code the_nether} -> {@code The Nether}. */
    private static String pretty(final String path) {
        final StringBuilder sb = new StringBuilder(path.length());
        boolean up = true;
        for (final char c : path.toCharArray()) {
            if (c == '_' || c == '/' || c == ':') { sb.append(' '); up = true; continue; }
            sb.append(up ? Character.toUpperCase(c) : c);
            up = false;
        }
        return sb.toString();
    }
}
