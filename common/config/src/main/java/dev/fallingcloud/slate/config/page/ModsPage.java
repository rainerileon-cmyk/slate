package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.mods.ModConfigTargets;
import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.search.SearchIndex;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.gfx.Textures;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.platform.ModInfo;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.core.screen.SidebarPage;
import dev.fallingcloud.slate.core.screen.SidebarScreen;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateCard;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateLabel;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.config.ui.ConfigSearchField;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import dev.fallingcloud.slate.core.widget.popup.MenuPopup;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Mods: every installed mod as a card (icon, name, version, authors, expandable description) with a
 * Configure button that opens the best editor - or a menu when several exist. Icons load lazily when
 * a card first renders, so a 400-mod pack does not decode 400 PNGs up front.
 */
public final class ModsPage extends SidebarPage implements dev.fallingcloud.slate.config.ui.Enterable {

    private String search = "";
    private final Set<String> expanded = new HashSet<>();
    private final Map<String, List<ModConfigTargets.Target>> targets = new HashMap<>();
    @Nullable private List<ModInfo> mods;
    private SidebarScreen screen;
    private double keepScroll = -1;
    @Nullable private SlateScrollPanel panel;
    private int enterDir;
    @Nullable private ConfigSearchField searchField;

    public ModsPage() {
        super("mods", Component.translatable("slate_config.page.mods"), Icon.MODS);
    }

    private List<ModInfo> mods() {
        if (mods == null) {
            mods = new ArrayList<>(SlatePlatform.get().allMods());
            mods.sort(Comparator.comparing(m -> m.name().toLowerCase(Locale.ROOT)));
        }
        return mods;
    }

    private List<ModConfigTargets.Target> targetsOf(final ModInfo m) {
        return targets.computeIfAbsent(m.id(), ModConfigTargets::forMod);
    }

    public void filter(final String text) {
        final String t = text == null ? "" : text;
        if (t.equals(search)) return;
        search = t;
        keepScroll = 0;
        if (screen != null && (screen.currentPage() == this || (screen.currentPage() instanceof dev.fallingcloud.slate.config.ui.CategoryHost c && c.activeChild() == this))) screen.refreshPage();
    }

    /** Set the search for the next build without rebuilding (a category handing the header search over). */
    public void filterSilently(final String text) {
        search = text == null ? "" : text;
    }

    @Override
    public void enterFrom(final int dir) {
        enterDir = Integer.signum(dir);
        keepScroll = 0;
    }

    private void rebuild() {
        keepScroll = panel != null ? panel.scrollAmount() : -1;
        if (screen != null) screen.refreshPage();
    }

    @Override
    public void build(final SidebarScreen screen, final Rect area) {
        this.screen = screen;
        final ConfigSearchField field = new ConfigSearchField(area.x(), area.y(), Math.min(260, area.w()), this::filter);
        field.setValue(search);
        field.placeholder(Component.translatable("slate_config.mods.search"));
        final boolean hadFocus = searchField != null && searchField.isFocused();
        searchField = field;
        screen.addPageWidget(field);
        if (hadFocus) screen.setFocused(field);        // typing rebuilds the page: keep the caret in the new field
        final dev.fallingcloud.slate.config.ui.TabPanel p = new dev.fallingcloud.slate.config.ui.TabPanel(area.x(), area.y() + 26, area.w(), area.h() - 26);
        panel = p;
        final int w = area.w() - 8;
        final String q = search.toLowerCase(Locale.ROOT).trim();
        int y = 0, shown = 0;
        for (final ModInfo m : mods()) {
            if (!q.isEmpty()) {
                final String hay = (m.id() + " " + m.name() + " " + String.join(" ", m.authors()) + " " + m.description()).toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) continue;
            }
            final boolean open = expanded.contains(m.id());
            final int textW = Math.max(60, w - 40 - 140);           // icon column, Configure button, chevron
            final String description = m.description() == null ? "" : m.description().trim();
            final List<net.minecraft.util.FormattedCharSequence> desc = SlateDraw.font().split(Component.literal(description), textW);
            final int h = open ? 44 + Math.max(1, desc.size()) * 10 + 4 : 44;
            final SlateCard card = new SlateCard(0, y, w, h).flat();
            card.add(new ModIcon(m), 8, 10);
            card.add(new SlateLabel(40, 7, textW, Component.literal(m.name())).style(SlateLabel.Style.TITLE), 40, 7);
            final String meta = "v" + m.version() + (m.authors().isEmpty() ? "" : "  ·  " + String.join(", ", m.authors()));
            card.add(new SlateLabel(40, 19, textW, Component.literal(meta)).style(SlateLabel.Style.CAPTION), 40, 19);
            if (!open) {
                final Component first = desc.isEmpty() ? Component.empty() : Component.literal(description.split("\\r?\\n")[0]);
                card.add(new SlateLabel(40, 30, textW, first).style(SlateLabel.Style.MUTED), 40, 30);
            } else {
                card.add(new SlateLabel(40, 32, textW, Component.literal(description)).style(SlateLabel.Style.MUTED).wrap(true), 40, 32);
            }
            final List<ModConfigTargets.Target> ts = targetsOf(m);
            final SlateButton cfg = new SlateButton(w - 128, 8, 96, Component.translatable("slate_config.mods.configure"), () -> openTargets(m, ts)).icon(Icon.SETTINGS);
            cfg.enabled(!ts.isEmpty());
            if (ts.isEmpty()) cfg.tip(Component.translatable("slate_config.mods.no_config"));
            else if (ts.size() > 1) cfg.tip(Component.translatable("slate_config.mods.count", ts.size()));
            card.add(cfg, w - 128, 8);
            card.add(new SlateIconButton(w - 26, 10, 16, open ? Icon.CHEVRON_UP : Icon.CHEVRON_DOWN,
                Component.translatable(open ? "slate_config.mods.collapse" : "slate_config.mods.expand"), () -> { if (!expanded.remove(m.id())) expanded.add(m.id()); rebuild(); }), w - 26, 10);
            p.add(card, 0, y);
            y += h + 6;
            shown++;
        }
        if (shown == 0) p.add(new SlateLabel(4, 8, w - 8, Component.translatable("slate_config.search.no_matches")).style(SlateLabel.Style.MUTED), 4, 8);
        p.setContentHeight(y + 4);
        screen.addPageWidget(p);
        if (keepScroll >= 0) { p.snapScroll(keepScroll); keepScroll = -1; }
        if (enterDir != 0) {
            p.slideFrom(enterDir);
            dev.fallingcloud.slate.config.ui.Entrance.play(p, dev.fallingcloud.slate.config.ui.Entrance.play(field, 0));
            enterDir = 0;
        }
    }

    private void openTargets(final ModInfo m, final List<ModConfigTargets.Target> ts) {
        if (ts.isEmpty()) return;
        if (ts.size() == 1) { ts.get(0).open().run(); return; }
        final List<MenuPopup.Item> items = new ArrayList<>();
        for (final ModConfigTargets.Target t : ts) items.add(MenuPopup.Item.of(t.label(), t.icon(), t.open()));
        final var mc = net.minecraft.client.Minecraft.getInstance();
        final double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
        final double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
        Popups.open(new MenuPopup((int) mx, (int) my, items, 160));
    }

    public List<SearchIndex.Entry> searchEntries() {
        return searchEntries(id(), title(), null);
    }

    public List<SearchIndex.Entry> searchEntries(final String path, final Component pageTitle, @Nullable final Component tabTitle) {
        final List<SearchIndex.Entry> out = new ArrayList<>();
        for (final ModInfo m : mods()) {
            out.add(new SearchIndex.Entry(path, pageTitle, tabTitle, Component.translatable("slate_config.page.mods"),
                Binding.of("mod:" + m.id(), OptionType.ACTION, Component.literal(m.name()))
                    .tooltip(m.description() == null || m.description().isBlank() ? null : Component.literal(m.description().trim()))
                    .action(Component.translatable("slate_config.mods.configure"), () -> { filter(m.name()); })
                    .searchWords(m.id() + " mod")));
        }
        return out;
    }

    /** Draws the mod's icon PNG (loaded on first render) or a placeholder glyph. */
    static final class ModIcon extends SlateWidget {
        private final ModInfo mod;
        @Nullable private Textures.Loaded tex;
        private boolean requested;

        ModIcon(final ModInfo mod) {
            super(0, 0, 24, 24, Component.literal(mod.name()));
            this.mod = mod;
            this.active = false;
        }

        @Override public boolean mouseClicked(final double mouseX, final double mouseY, final int button) { return false; }

        @Override
        public net.minecraft.client.gui.ComponentPath nextFocusPath(final net.minecraft.client.gui.navigation.FocusNavigationEvent event) { return null; }

        @Override protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) { draw(g); }

        @Override protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) { draw(g); }

        private void draw(final GuiGraphics g) {
            if (!requested) {
                requested = true;
                mod.iconPath().ifPresent(p -> Textures.load(p, l -> tex = l));
            }
            final int x = getX(), y = getY(), s = getWidth();
            if (tex != null) {
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                g.setColor(1, 1, 1, effectiveAlpha());
                g.blit(tex.id(), x, y, s, s, 0, 0, tex.width(), tex.height(), tex.width(), tex.height());
                g.setColor(1, 1, 1, 1);
                com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            } else {
                final int c = Theme.current().isVanilla() ? 0xFF3A3A3A : Theme.current().palette().surfaceActive();
                SlateDraw.pixelRound(g, x, y, s, s, Colors.scaleAlpha(c, effectiveAlpha()), Theme.current().radius());
                Icons.draw(g, Icon.MODS, x + 4, y + 4, 16, Colors.scaleAlpha(Theme.current().palette().textDim(), effectiveAlpha()));
            }
        }
    }
}
