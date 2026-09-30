package dev.fallingcloud.slate.menu.client.pause;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.gfx.Textures;
import dev.fallingcloud.slate.core.module.Features;
import dev.fallingcloud.slate.core.module.KnownModules;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.screen.slot.CoreSlots;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateAvatar;
import dev.fallingcloud.slate.core.widget.SlateCard;
import dev.fallingcloud.slate.menu.client.title.NavButton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * The Custom layout's escape menu, laid out as the Custom main menu is: a column of large buttons on one plate, and
 * beside it what there is to know, on cards. The column holds what the menu does ({@link PauseActions}), Back to game
 * first and marked as the one the column is there for, leaving last and red. The cards are the world (its picture,
 * its name, the mode, the dimension, the day and hour, the difficulty) and the player (their face and name, how they
 * are doing, where they stand). A narrow window keeps the column alone.
 */
public final class SlatePauseScreen extends SlateScreen {

    private static final int NAV_W = 176, GUTTER = 16, WORLD_H = 66, PLAYER_H = 66;

    private final Anim open = new Anim(0, 260, Ease.OUT_CUBIC);
    private int left, top, navTop, navBottom, contentW;
    private boolean compact;

    public SlatePauseScreen() {
        super(Component.translatable("menu.game"), null);
        this.showHeader = false;
        this.showBack = false;
    }

    // ------------------------------------------------------------------ build

    @Override
    protected void build() {
        if (open.target() == 0f) open.set(1f);
        final List<PauseActions.Action> actions = PauseActions.actions(this, false);
        compact = height < 300;
        final boolean wide = width >= 400;
        final int rowH = compact ? 20 : NavButton.HEIGHT, gap = compact ? 2 : 4;
        final int navH = actions.size() * rowH + (actions.size() - 1) * gap;
        final int headH = compact ? 20 : 34;
        contentW = wide ? Math.min(width - PAD * 2, 540) : NAV_W;
        left = (width - contentW) / 2;
        top = Math.max(6, (height - headH - navH) / 2);
        navTop = top + headH;

        int y = navTop;
        for (final PauseActions.Action a : actions) {
            final NavButton b = new NavButton(left, y, NAV_W, a.icon(), a.label(), a.run());
            b.setHeight(rowH);
            if (a.kind() == PauseActions.Kind.PRIMARY) b.primary();
            else if (a.kind() == PauseActions.Kind.DANGER) b.danger();
            if (!a.enabled()) b.enabled(false);
            if (a.tip() != null) b.tip(a.tip());
            add(b);
            y += rowH + gap;
        }
        navBottom = y - gap;

        if (!wide) return;
        final int cx = left + NAV_W + GUTTER, cw = contentW - NAV_W - GUTTER;
        int cy = navTop;
        if (cy + WORLD_H <= height - 6) {
            add(new WorldCard(cx, cy, cw, WORLD_H));
            cy += WORLD_H + 8;
        }
        if (cy + PLAYER_H <= height - 6) add(new PlayerCard(cx, cy, cw, PLAYER_H, this));
    }

    // ------------------------------------------------------------------ render

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        // The column rests on one plate, as the main menu's does.
        if (!Theme.current().isVanilla() && navBottom > 0) {
            SlateDraw.floatingPanel(g, left - 6, navTop - 6, NAV_W + 12, navBottom - navTop + 12, open.get());
        }
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        final float a = open.get();
        final float big = compact ? 1f : 2f;
        final int slide = Math.round((1f - a) * 5f);

        // What this is, where the main menu has its logo: large, with the accent's mark under it.
        final FormattedCharSequence heading = SlateDraw.truncate(Fonts.heading(getTitle()), Math.round(contentW / big));
        g.pose().pushPose();
        g.pose().translate(left, top + slide, 0f);
        g.pose().scale(big, big, 1f);
        g.drawString(font, heading, 0, 0, Colors.scaleAlpha(van ? 0xFFFFFFFF : p.text(), a), van);
        g.pose().popPose();
        if (!van) SlateDraw.accentCap(g, left, top + slide + Math.round(9 * big) + 2, Math.min(contentW, Math.max(24, Math.round(font.width(heading) * big * 0.5f))), a);

        // How long the player has been at it, at the other end of the same line.
        final Component session = PauseActions.session();
        if (session != null && contentW > NAV_W) {
            final int w = font.width(session);
            if (w < contentW - Math.round(font.width(heading) * big) - 12) {
                g.drawString(font, session, left + contentW - w, top + slide + Math.round(9 * big) - 8, Colors.scaleAlpha(van ? 0xFFC0C0C0 : p.textMuted(), a), van);
            }
        }
    }

    // ------------------------------------------------------------------ the cards

    /** A chip if it fits before {@code right}; returns the next x, or {@code right} once one did not fit so later chips skip too. */
    private static int chip(final GuiGraphics g, @Nullable final Component text, final int x, final int y, final int right, final int color) {
        if (text == null) return x;
        if (x + SlateDraw.width(text) + 8 > right) return right;
        return x + SlateDraw.chip(g, text, x, y, color, 1f) + 4;
    }

    /** The world being played: its picture (the save's own, or the server's), its name, and the state it is in. */
    private static final class WorldCard extends SlateCard {

        @Nullable private Textures.Loaded icon;

        WorldCard(final int x, final int y, final int w, final int h) {
            super(x, y, w, h);
            flat();
            final Minecraft mc = Minecraft.getInstance();
            if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
                final Path file = mc.getSingleplayerServer().getWorldScreenshotFile().orElse(null);
                if (file != null && Files.isRegularFile(file)) Textures.load(file, l -> icon = l);
            } else {
                final ServerData server = mc.getCurrentServer();
                final byte[] bytes = server == null ? null : server.getIconBytes();
                if (bytes != null) icon = Textures.fromBytes(bytes, "slate_menu:favicon:" + server.ip + ":" + bytes.length).orElse(null);
            }
        }

        @Override
        protected void renderContent(final GuiGraphics g, final int x, final int y, final int w, final int h, final int mouseX, final int mouseY, final float partialTick) {
            final Theme t = Theme.current();
            final Palette p = t.palette();
            final boolean van = t.isVanilla();
            final Minecraft mc = Minecraft.getInstance();
            final boolean sp = PauseActions.singleplayer();
            final int muted = van ? 0xFFC0C0C0 : p.textMuted();
            g.drawString(SlateDraw.font(), Component.translatable(sp ? "slate_menu.pause.card.world" : "slate_menu.pause.card.server"), x + 10, y + 7,
                van ? 0xFFA0A0A0 : p.textDim(), van);

            final int ix = x + 10, iy = y + 21, is = 32;
            if (icon != null) {
                RenderSystem.enableBlend();
                g.blit(icon.id(), ix, iy, is, is, 0, 0, icon.width(), icon.height(), icon.width(), icon.height());
                RenderSystem.disableBlend();
            } else {
                SlateDraw.pixelRound(g, ix, iy, is, is, van ? 0x80000000 : p.bg2(), t.radius());
                Icons.draw(g, sp ? Icon.WORLD : Icon.SERVER, ix + 8, iy + 8, 16, muted);
            }
            SlateDraw.outline(g, ix, iy, is, is, van ? 0xFF000000 : p.border(), t.radius());

            final int tx = ix + is + 8, right = x + w - 10;
            g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(PauseActions.worldName()), right - tx), tx, y + 22, van ? 0xFFFFFFFF : p.text(), van);
            final Component dimension = PauseActions.dimension();
            Component sub = Component.translatable(sp ? "slate_menu.title.singleplayer" : "slate_menu.title.multiplayer");
            if (!sp && mc.getCurrentServer() != null) sub = Component.empty().append(sub).append(" · ").append(mc.getCurrentServer().ip);
            else if (dimension != null) sub = Component.empty().append(sub).append(" · ").append(dimension);
            g.drawString(SlateDraw.font(), SlateDraw.truncate(sub, right - tx), tx, y + 34, muted, van);

            int cx = chip(g, PauseActions.mode(), tx, y + 46, right, p.accent());
            if (mc.level != null && mc.level.getLevelData().isHardcore()) cx = chip(g, Component.translatable("slate_menu.worlds.hardcore"), cx, y + 46, right, p.danger());
            cx = chip(g, PauseActions.dayTime(), cx, y + 46, right, p.warning());
            if (mc.level != null) chip(g, mc.level.getDifficulty().getDisplayName(), cx, y + 46, right, p.textMuted());
        }
    }

    /**
     * The player: their face and name, how they are doing (hearts, armour, hunger, level: what the HUD would say),
     * and where they stand. A click opens their profile, or vanilla's skin options where Slate Profile is not there.
     */
    private static final class PlayerCard extends SlateCard {

        PlayerCard(final int x, final int y, final int w, final int h, final Screen parent) {
            super(x, y, w, h);
            final Minecraft mc = Minecraft.getInstance();
            add(new SlateAvatar(0, 0, 32, mc.getGameProfile()), 10, 21);
            onClick(() -> {
                if (Features.present(KnownModules.PROFILE)) MenuSlots.open(CoreSlots.PROFILE, parent);
                else mc.setScreen(new SkinCustomizationScreen(parent, mc.options));
            });
        }

        @Override
        protected void renderContent(final GuiGraphics g, final int x, final int y, final int w, final int h, final int mouseX, final int mouseY, final float partialTick) {
            final Theme t = Theme.current();
            final Palette p = t.palette();
            final boolean van = t.isVanilla();
            final Minecraft mc = Minecraft.getInstance();
            final int muted = van ? 0xFFC0C0C0 : p.textMuted();
            g.drawString(SlateDraw.font(), Component.translatable("slate_menu.pause.card.you"), x + 10, y + 7, van ? 0xFFA0A0A0 : p.textDim(), van);
            Icons.draw(g, Icon.EDIT, x + w - 18, y + 7, 10, van ? 0xFFA0A0A0 : p.textDim());

            // Three lines beside the face, as the world's card has three beside its picture: who, how, where.
            final int tx = x + 50, right = x + w - 10;
            g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(mc.getUser().getName()), right - tx), tx, y + 22, van ? 0xFFFFFFFF : p.text(), van);
            if (PauseVitals.width() > 0) PauseVitals.draw(g, tx, y + 35, 1f, van ? 0xFFFFFFFF : p.text());
            else if (PauseActions.mode() != null) g.drawString(SlateDraw.font(), PauseActions.mode(), tx, y + 35, muted, van);

            final Component position = PauseActions.position(), biome = PauseActions.biome();
            Component where = position;
            if (position != null && biome != null) where = Component.empty().append(position).append(" · ").append(biome);
            else if (biome != null) where = biome;
            if (where != null) g.drawString(SlateDraw.font(), SlateDraw.truncate(where, right - tx), tx, y + 48, muted, van);
        }
    }
}
