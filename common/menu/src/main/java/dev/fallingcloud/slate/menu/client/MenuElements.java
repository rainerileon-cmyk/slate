package dev.fallingcloud.slate.menu.client;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ElementType;
import dev.fallingcloud.slate.core.layout.ElementTypes;
import dev.fallingcloud.slate.core.layout.ScreenLayout;
import dev.fallingcloud.slate.core.layout.action.ActionType.Arg;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateList;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import dev.fallingcloud.slate.menu.client.servers.ServerActions;
import dev.fallingcloud.slate.menu.client.servers.ServerCard;
import dev.fallingcloud.slate.menu.client.servers.ServerPinger;
import dev.fallingcloud.slate.menu.client.title.AccountCard;
import dev.fallingcloud.slate.menu.client.title.ContinueCard;
import dev.fallingcloud.slate.menu.client.worlds.WorldActions;
import dev.fallingcloud.slate.menu.client.worlds.WorldCard;
import dev.fallingcloud.slate.menu.client.worlds.WorldEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;

/**
 * Layout element types for the dev mode: {@code slate_menu:continue_card}, {@code slate_menu:account_card},
 * {@code slate_menu:world_list} (compact list that plays on activate) and {@code slate_menu:server_status}
 * (live status of one address; click joins unless the element has its own actions).
 */
public final class MenuElements {

    @FunctionalInterface
    interface Factory {
        AbstractWidget create(Screen screen, ScreenLayout.Element e, int x, int y, int w, int h, Runnable run);
    }

    private record Type(String id, String labelText, Icon icon, int[] size, List<Arg> props, boolean clickable, Factory factory) implements ElementType {
        @Override public Component label() { return Component.literal(labelText); }
        @Override public int[] defaultSize() { return size; }
        @Override public List<Arg> props() { return props; }
        @Override public boolean clickable() { return clickable; }
        @Override public AbstractWidget create(final Screen screen, final ScreenLayout.Element element, final int x, final int y, final int w, final int h, final Runnable runActions) {
            return factory.create(screen, element, x, y, w, h, runActions);
        }
    }

    static void registerAll() {
        ElementTypes.register(new Type("slate_menu:continue_card", "Continue card", Icon.PLAY, new int[] { 220, ContinueCard.HEIGHT }, List.of(), false,
            (screen, e, x, y, w, h, run) -> new ContinueCard(x, y, w, h, screen)));
        ElementTypes.register(new Type("slate_menu:account_card", "Account card", Icon.USER, new int[] { 220, AccountCard.HEIGHT }, List.of(), false,
            (screen, e, x, y, w, h, run) -> new AccountCard(x, y, w, h, screen)));
        ElementTypes.register(new Type("slate_menu:world_list", "World list", Icon.WORLD, new int[] { 220, 120 },
            List.of(Arg.number("max", Component.literal("Max worlds (0 = all)"), "5")), false,
            (screen, e, x, y, w, h, run) -> {
                int max = 5;
                try { max = Integer.parseInt(e.props.getOrDefault("max", "5").trim()); } catch (final NumberFormatException ignored) {}
                return new WorldListElement(x, y, w, h, max, screen);
            }));
        ElementTypes.register(new Type("slate_menu:server_status", "Server status", Icon.SERVER, new int[] { 220, 44 },
            List.of(Arg.text("address", Component.literal("Address"), "play.example.com"),
                    Arg.text("name", Component.literal("Name"), "My server")), true,
            (screen, e, x, y, w, h, run) -> new ServerStatusElement(x, y, w, h, e.props.getOrDefault("address", ""), e.props.getOrDefault("name", ""),
                e.actions.isEmpty() ? null : run, screen)));
    }

    /** A compact list of the newest worlds; Enter / double-click plays. */
    static final class WorldListElement extends SlateList<WorldEntry> {
        WorldListElement(final int x, final int y, final int w, final int h, final int max, final Screen screen) {
            super(x, y, w, h, 36, (g, item, index, rx, ry, rw, rh, hovered, selected, mx, my) -> WorldCard.drawSummary(g, item, rx + 4, ry + 2, rw - 8, 32, false, mx, my));
            emptyText(Component.translatable("slate_menu.worlds.loading"));
            onActivate(e -> WorldActions.play(e.summary, screen));
            WorldActions.loadAll().whenCompleteAsync((list, err) -> {
                final List<WorldEntry> out = new ArrayList<>();
                if (list != null) {
                    final List<LevelSummary> sorted = new ArrayList<>(list);
                    sorted.sort(Comparator.comparingLong(LevelSummary::getLastPlayed).reversed());
                    for (final LevelSummary s : sorted) {
                        if (max > 0 && out.size() >= max) break;
                        out.add(new WorldEntry(s));
                    }
                }
                items(out);
                emptyText(Component.translatable("slate_menu.worlds.empty"));
            }, Minecraft.getInstance());
        }
    }

    /** Live status of one server address. */
    static final class ServerStatusElement extends SlateWidget {
        private final ServerData data;
        private final ServerPinger pinger = new ServerPinger();
        private final Runnable run;
        private final Screen screen;

        ServerStatusElement(final int x, final int y, final int w, final int h, final String address, final String name, final Runnable run, final Screen screen) {
            super(x, y, w, h, Component.literal(name.isEmpty() ? address : name));
            this.data = ServerActions.find(address).orElseGet(() -> new ServerData(name.isEmpty() ? address : name, address, ServerData.Type.OTHER));
            this.data.setState(ServerData.State.INITIAL);
            this.run = run;
            this.screen = screen;
            this.active = !address.isBlank();
        }

        @Override
        public void onClick(final double mouseX, final double mouseY) {
            super.onClick(mouseX, mouseY);
            if (run != null) run.run();
            else ServerActions.join(data, screen);
        }

        private void draw(final GuiGraphics g, final int mouseX, final int mouseY, final boolean van) {
            pinger.tick();
            if (!data.ip.isBlank()) pinger.ping(data, () -> {});
            final Theme t = Theme.current();
            final Palette p = t.palette();
            final float a = effectiveAlpha();
            final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
            if (van) {
                g.fill(x, y, x + w, y + h, Colors.scaleAlpha(0x90000000, a));
                SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(Colors.lerp(0xFF8B8B8B, 0xFFFFFFFF, hover()), a), 0);
            } else {
                SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(Colors.lerp(p.surface(), p.surfaceHover(), hover()), a), t.radius());
                SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(Colors.lerp(p.border(), p.borderStrong(), hover()), a), t.radius());
            }
            Icons.draw(g, Icon.SERVER, x + 8, y + (h - 16) / 2, 16, Colors.scaleAlpha(van ? 0xFFFFFFFF : p.textMuted(), a));
            final int tx = x + 30, tw = w - 30 - 80;
            g.drawString(SlateDraw.font(), SlateDraw.truncate(getMessage(), tw), tx, y + 6, Colors.scaleAlpha(p.text(), a), van);
            final Component motd = data.motd == null ? Component.literal(data.ip) : data.motd;
            g.drawString(SlateDraw.font(), SlateDraw.truncate(motd, tw), tx, y + 18, Colors.scaleAlpha(van ? 0xFFC0C0C0 : p.textMuted(), a), van);
            ServerCard.drawStatus(g, data, x + w - 78, y + 6, 72, mouseX, mouseY, false);
            SlateDraw.focusRing(g, x, y, w, h, focus() * a);
        }

        @Override protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) { draw(g, mouseX, mouseY, false); }

        @Override protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) { draw(g, mouseX, mouseY, true); }
    }

    private MenuElements() {}
}
