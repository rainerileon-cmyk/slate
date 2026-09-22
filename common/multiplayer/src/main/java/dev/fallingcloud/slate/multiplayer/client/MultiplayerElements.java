package dev.fallingcloud.slate.multiplayer.client;

import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ElementType;
import dev.fallingcloud.slate.core.layout.ElementTypes;
import dev.fallingcloud.slate.core.layout.Placeholders;
import dev.fallingcloud.slate.core.layout.ScreenLayout;
import dev.fallingcloud.slate.core.layout.action.ActionType.Arg;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import dev.fallingcloud.slate.multiplayer.MultiplayerConfigs;
import dev.fallingcloud.slate.multiplayer.client.ui.FriendsHubScreen;
import dev.fallingcloud.slate.multiplayer.client.ui.PlayerCardPopup;
import dev.fallingcloud.slate.multiplayer.client.ui.UiUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Layout element types for the dev mode: {@code slate_multiplayer:friends_panel} (a compact online
 * list with heads, made for the title screen) and {@code slate_multiplayer:status_pill} (a one-line
 * "n friends online" chip). Both open the Friends hub; the panel's rows open player cards.
 */
final class MultiplayerElements {

    static void registerAll() {
        ElementTypes.register(new Type("slate_multiplayer:friends_panel", "Friends panel", Icon.FRIENDS, new int[] { 150, 120 },
            List.of(Arg.text("title", Component.literal("Title"), "Friends"),
                    Arg.number("max_rows", Component.literal("Rows"), "8"),
                    Arg.bool("show_offline", Component.literal("Show offline friends"), false),
                    Arg.bool("compact", Component.literal("Compact rows (no presence line)"), false)),
            (screen, e, x, y, w, h, run) -> new FriendsPanelWidget(x, y, w, h, e, run)));
        ElementTypes.register(new Type("slate_multiplayer:status_pill", "Friends status pill", Icon.ONLINE, new int[] { 90, 14 },
            List.of(Arg.text("label", Component.literal("Label ({friends_online} = count)"), "{friends_online} online")),
            (screen, e, x, y, w, h, run) -> new StatusPillWidget(x, y, w, h, e, run)));
    }

    @FunctionalInterface
    interface Factory {
        AbstractWidget create(Screen screen, ScreenLayout.Element e, int x, int y, int w, int h, Runnable run);
    }

    private record Type(String id, String name, Icon icon, int[] size, List<Arg> props, Factory factory) implements ElementType {
        @Override public Component label() { return Component.literal(name); }
        @Override public int[] defaultSize() { return size; }
        @Override public AbstractWidget create(final Screen screen, final ScreenLayout.Element element, final int x, final int y, final int w, final int h, final Runnable runActions) {
            return factory.create(screen, element, x, y, w, h, runActions);
        }
    }

    private static int intProp(final ScreenLayout.Element e, final String key, final int def) {
        try { return Integer.parseInt(e.props.getOrDefault(key, Integer.toString(def)).trim()); } catch (final NumberFormatException ex) { return def; }
    }

    /** The compact friends list. */
    static final class FriendsPanelWidget extends SlateWidget {
        private static final int ROW = 22, ROW_COMPACT = 14, HEADER = 16;
        private final ScreenLayout.Element e;
        private final Runnable run;
        private final List<Friend> rows = new ArrayList<>();
        private int rowH;

        FriendsPanelWidget(final int x, final int y, final int w, final int h, final ScreenLayout.Element e, final Runnable run) {
            super(x, y, w, h, Component.literal("Friends"));
            this.e = e;
            this.run = run;
            this.silent();
        }

        private void refresh() {
            rows.clear();
            final boolean offline = Boolean.parseBoolean(e.props.getOrDefault("show_offline", Boolean.toString(MultiplayerConfigs.client().panelShowOffline)));
            final int max = Math.max(1, intProp(e, "max_rows", MultiplayerConfigs.client().panelMaxRows));
            final List<Friend> all = new ArrayList<>(SocialClient.get().friends());
            all.sort(Comparator.comparing((Friend f) -> !f.online()).thenComparing(f -> f.display().toLowerCase()));
            for (final Friend f : all) {
                if (!offline && !f.online()) break;
                rows.add(f);
                if (rows.size() >= max) break;
            }
            rowH = Boolean.parseBoolean(e.props.getOrDefault("compact", "false")) ? ROW_COMPACT : ROW;
        }

        private int rowAt(final double my) {
            final int i = (int) ((my - getY() - HEADER - 2) / rowH);
            return i >= 0 && i < rows.size() && my >= getY() + HEADER ? i : -1;
        }

        @Override
        public void onClick(final double mouseX, final double mouseY) {
            super.onClick(mouseX, mouseY);
            final int i = rowAt(mouseY);
            if (i >= 0) { PlayerCardPopup.open(rows.get(i).uuid, rows.get(i).name, (int) mouseX, (int) mouseY); return; }
            if (!e.actions.isEmpty()) run.run(); else FriendsHubScreen.open("friends");
        }

        @Override
        public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
            if (button == 1 && this.visible && this.active && contains(mouseX, mouseY)) {
                final int i = rowAt(mouseY);
                if (i >= 0) { UiUtil.openFriendMenu(rows.get(i), mouseX, mouseY); return true; }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            draw(g, mouseX, mouseY, false);
        }

        @Override
        protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            draw(g, mouseX, mouseY, true);
        }

        private void draw(final GuiGraphics g, final int mouseX, final int mouseY, final boolean vanilla) {
            refresh();
            final Theme t = Theme.current();
            final Palette p = t.palette();
            final float a = effectiveAlpha();
            final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
            if (vanilla) {
                g.fill(x, y, x + w, y + h, Colors.scaleAlpha(0x90000000, a));
                SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(0xFF8B8B8B, a), 0);
            } else {
                SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(Colors.withAlpha(p.surface(), 0xE6), a), t.radius());
                SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(Colors.lerp(p.border(), p.borderStrong(), hover()), a), t.radius());
            }
            final SocialClient sc = SocialClient.get();
            final String title = Placeholders.apply(e.props.getOrDefault("title", "Friends"));
            g.drawString(SlateDraw.font(), Fonts.heading(Component.literal(title)), x + 6, y + 4, Colors.scaleAlpha(p.text(), a), vanilla);
            final String count = sc.connected() ? sc.onlineFriends() + " online" : sc.linkState().name().toLowerCase();
            SlateDraw.textRight(g, Component.literal(count), x + w - 6, y + 4, Colors.scaleAlpha(sc.connected() ? p.textMuted() : p.textDim(), a));
            SlateDraw.hline(g, x + 4, y + HEADER - 1, w - 8, Colors.scaleAlpha(p.border(), a));
            SlateDraw.scissor(g, x, y + HEADER, w, h - HEADER);
            final int hov = this.isHovered() ? rowAt(mouseY) : -1;
            int ry = y + HEADER + 2;
            if (rows.isEmpty()) {
                final String empty = sc.friends().isEmpty() ? (sc.linkState().connected() ? "No friends yet" : "Not connected") : "Nobody online";
                g.drawString(SlateDraw.font(), empty, x + 6, ry + 2, Colors.scaleAlpha(p.textDim(), a), vanilla);
            }
            for (int i = 0; i < rows.size() && ry < y + h; i++, ry += rowH) {
                final Friend f = rows.get(i);
                if (i == hov) SlateDraw.pixelRound(g, x + 2, ry - 1, w - 4, rowH, Colors.scaleAlpha(vanilla ? 0x30FFFFFF : p.surfaceHover(), a), t.radius() > 0 ? 2 : 0);
                final int head = rowH == ROW ? 16 : 10;
                UiUtil.drawHead(g, f.uuid, f.name, x + 6, ry + (rowH - head) / 2, head, f.presence, a);
                final int tx = x + 6 + head + 5;
                final int fg = f.online() ? p.text() : p.textDim();
                g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(f.display()), w - (tx - x) - 6), tx, ry + (rowH == ROW ? 1 : 2), Colors.scaleAlpha(fg, a), vanilla);
                if (rowH == ROW) g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(UiUtil.presenceLine(f)), w - (tx - x) - 6), tx, ry + 11, Colors.scaleAlpha(p.textDim(), a), vanilla);
            }
            SlateDraw.unscissor(g);
        }
    }

    /** "3 friends online" chip. */
    static final class StatusPillWidget extends SlateWidget {
        private final ScreenLayout.Element e;
        private final Runnable run;

        StatusPillWidget(final int x, final int y, final int w, final int h, final ScreenLayout.Element e, final Runnable run) {
            super(x, y, w, h, Component.literal("Friends"));
            this.e = e;
            this.run = run;
        }

        @Override
        public void onClick(final double mouseX, final double mouseY) {
            super.onClick(mouseX, mouseY);
            if (!e.actions.isEmpty()) run.run(); else FriendsHubScreen.open("friends");
        }

        @Override
        protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            final Theme t = Theme.current();
            final Palette p = t.palette();
            final float a = effectiveAlpha();
            final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
            SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(Colors.lerp(p.surface(), p.surfaceHover(), hover()), a), Math.min(h / 2, 4));
            SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(p.border(), a), Math.min(h / 2, 4));
            drawContent(g, x, y, w, h, a, false);
        }

        @Override
        protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            final float a = effectiveAlpha();
            final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
            SlateDraw.vanillaButton(g, x, y, w, h, hover(), true, a);
            drawContent(g, x, y, w, h, a, true);
        }

        private void drawContent(final GuiGraphics g, final int x, final int y, final int w, final int h, final float a, final boolean shadow) {
            final SocialClient sc = SocialClient.get();
            final Palette p = Theme.current().palette();
            final int dot = sc.connected() ? p.success() : sc.linkState() == LinkState.FAILED ? p.danger() : p.textDim();
            g.fill(x + 6, y + h / 2 - 2, x + 10, y + h / 2 + 2, Colors.scaleAlpha(dot, a));
            final String label = Placeholders.apply(e.props.getOrDefault("label", "{friends_online} online"));
            g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(label), w - 18), x + 14, y + (h - 8) / 2, Colors.scaleAlpha(p.text(), a), shadow);
            Icons.draw(g, Icon.FRIENDS, x + w - 12, y + (h - 8) / 2, 8, Colors.scaleAlpha(p.textMuted(), a));
        }
    }

    private MultiplayerElements() {}
}
