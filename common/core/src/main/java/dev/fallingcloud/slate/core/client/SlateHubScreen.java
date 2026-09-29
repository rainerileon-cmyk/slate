package dev.fallingcloud.slate.core.client;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.client.settings.LayoutStyleRows;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.editor.LayoutEditor;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.module.Modules;
import dev.fallingcloud.slate.core.module.SlateModule;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.core.screen.Reskin;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateCard;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateLabel;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateSwatches;
import dev.fallingcloud.slate.core.widget.SlateToggle;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The Slate hub: the theme at a glance (skin + accent, applied live), one card per installed module
 * with its quick actions, and the dev-mode shortcuts. Reachable from the pause/title menus, the hub key
 * and the {@code slate:hub} action.
 */
public final class SlateHubScreen extends SlateScreen {

    private static final int CARD_PAD = 10, CARD_GAP = 8, ROW_H = 20;

    public SlateHubScreen(@Nullable final Screen parent) {
        super(Component.translatable("slate.hub.title"), parent);
        this.maxContentWidth = 440;
    }

    /** A 16 px module icon in the accent colour (vanilla: white). */
    private static final class IconLabel extends SlateWidget {
        private final Icon icon;

        IconLabel(final Icon icon) {
            super(0, 0, 16, 16, Component.empty());
            this.icon = icon;
            this.active = false;
        }

        @Override public boolean mouseClicked(final double mx, final double my, final int button) { return false; }

        @Override public net.minecraft.client.gui.ComponentPath nextFocusPath(final net.minecraft.client.gui.navigation.FocusNavigationEvent e) { return null; }

        @Override protected void renderDark(final GuiGraphics g, final int mx, final int my, final float pt) {
            Icons.draw(g, icon, getX(), getY() + enterOffset(), 16, Colors.scaleAlpha(Theme.current().accent(), effectiveAlpha()));
        }

        @Override protected void renderVanilla(final GuiGraphics g, final int mx, final int my, final float pt) {
            Icons.draw(g, icon, getX(), getY() + enterOffset(), 16, Colors.scaleAlpha(0xFFFFFFFF, effectiveAlpha()));
        }
    }

    @Override
    protected void build() {
        final Rect c = contentRect();
        addHeaderAction(new SlateIconButton(0, 0, 20, Icon.SETTINGS, Component.translatable("slate.hub.settings"), () -> minecraft.setScreen(new CoreSettingsScreen(this))));
        final SlateScrollPanel panel = add(new SlateScrollPanel(c.x(), c.y(), c.w(), c.h()).padding(4));
        final int w = panel.innerWidth();
        final int inner = w - CARD_PAD * 2;
        int y = 0;

        // Theme card (layout, style, containers, accent): only in the vanilla layout, where the hub is the way to Slate. The
        // Slate layouts reach the same rows through Options > Interface (A6.8, Q9).
        if (dev.fallingcloud.slate.core.screen.slot.MenuSlots.globalLayout() == dev.fallingcloud.slate.core.screen.slot.Layout.VANILLA) {
            final SlateCard card = new SlateCard(0, 0, w, 0).flat();
            int cy = CARD_PAD;
            card.add(new SlateLabel(0, 0, inner, Component.translatable("slate.hub.theme")).style(SlateLabel.Style.TITLE), CARD_PAD, cy);
            cy += 16;
            // The three rows of the setup screen (layout, menu style, container style): Core's own controls, applied live.
            final boolean twoCols = inner >= 300;
            final int colW = twoCols ? (inner - 8) / 2 : inner;
            card.add(new SlateLabel(0, 0, inner, LayoutStyleRows.layoutLabel()).style(SlateLabel.Style.CAPTION), CARD_PAD, cy);
            cy += 11;
            card.add(LayoutStyleRows.layoutSegmented(0, 0, inner, l -> rebuildWidgets()), CARD_PAD, cy);
            cy += 26;
            card.add(new SlateLabel(0, 0, colW, LayoutStyleRows.styleLabel()).style(SlateLabel.Style.CAPTION), CARD_PAD, cy);
            cy += 11;
            card.add(LayoutStyleRows.styleSegmented(0, 0, colW, s -> rebuildWidgets()), CARD_PAD, cy);
            final SlateToggle containers = LayoutStyleRows.containersToggle(0, 0, colW, v -> {});
            containers.tip(Component.translatable("slate.setup.containers.desc"));
            if (twoCols) { card.add(containers, CARD_PAD + colW + 8, cy + 4); cy += 24; }
            else { cy += 24; card.add(containers, CARD_PAD, cy); cy += ROW_H; }
            cy += 4;
            final int accent = Colors.fromHex(Slate.config().accent, Palette.DEFAULT_ACCENT);
            final SlateSwatches sw = new SlateSwatches(0, 0, inner, Palette.ACCENTS, accent,
                argb -> { Slate.configFile().update(cfg -> cfg.accent = Colors.toHex(argb)); Theme.reload(); });
            card.add(sw, CARD_PAD, cy);
            cy += sw.getHeight() + 8;
            card.add(new SlateButton(0, 0, 130, 16, Component.translatable("slate.hub.settings"), () -> minecraft.setScreen(new CoreSettingsScreen(this)))
                .icon(Icon.SETTINGS).variant(SlateButton.Variant.GHOST).leftAligned(), CARD_PAD - 4, cy);
            card.add(new SlateButton(0, 0, 130, 16, Component.translatable("slate.settings.run_setup"),
                () -> minecraft.setScreen(new dev.fallingcloud.slate.core.client.setup.SlateSetupScreen(this)))
                .icon(Icon.SPARKLE).variant(SlateButton.Variant.GHOST).leftAligned(), CARD_PAD + 134, cy);
            cy += 16 + CARD_PAD;
            card.setHeight(cy);
            panel.add(card, 0, y);
            y += cy + CARD_GAP;
        }

        // Modules
        final List<SlateModule> modules = Modules.all();
        if (modules.isEmpty()) {
            final SlateCard card = new SlateCard(0, 0, w, 0).flat();
            card.add(new SlateLabel(0, 0, inner, Component.translatable("slate.hub.no_modules")).style(SlateLabel.Style.TITLE), CARD_PAD, CARD_PAD);
            final SlateLabel body = new SlateLabel(0, 0, inner, Component.translatable("slate.hub.no_modules.body")).style(SlateLabel.Style.MUTED).wrap(true);
            card.add(body, CARD_PAD, CARD_PAD + 18);
            card.setHeight(CARD_PAD + 18 + body.getHeight() + CARD_PAD);
            panel.add(card, 0, y);
            y += card.getHeight() + CARD_GAP;
        }
        for (final SlateModule m : modules) {
            final List<SlateModule.HubEntry> entries = m.hubEntries();
            final String desc = SlatePlatform.get().modInfo(m.id()).map(i -> i.description()).orElse("");
            final SlateCard card = new SlateCard(0, 0, w, 0).flat();
            int cy = CARD_PAD;
            card.add(new IconLabel(m.icon()), CARD_PAD, cy);
            card.add(new SlateLabel(0, 0, inner - 90, m.displayName()).style(SlateLabel.Style.TITLE), CARD_PAD + 22, cy + 1);
            card.add(new SlateLabel(0, 0, 60, Component.literal(version(m.id()))).style(SlateLabel.Style.CAPTION).align(SlateLabel.Align.RIGHT), CARD_PAD + inner - 60, cy + 3);
            cy += 18;
            if (!desc.isEmpty()) {
                final SlateLabel d = new SlateLabel(0, 0, inner, Component.literal(desc)).style(SlateLabel.Style.MUTED).wrap(true);
                card.add(d, CARD_PAD, cy);
                cy += Math.min(d.getHeight(), 30) + 4;
            }
            if (!entries.isEmpty()) {
                cy += 2;
                final int cols = inner >= 300 ? 2 : 1;
                final int bw = (inner - (cols - 1) * 8) / cols;
                for (int i = 0; i < entries.size(); i++) {
                    final SlateModule.HubEntry e = entries.get(i);
                    final int bx = CARD_PAD + (i % cols) * (bw + 8), by = cy + (i / cols) * (ROW_H + 2);
                    card.add(new SlateButton(0, 0, bw, ROW_H - 2, e.label(), e.onClick()).icon(e.icon()).variant(SlateButton.Variant.GHOST).leftAligned(), bx - 4, by);
                }
                cy += ((entries.size() + cols - 1) / cols) * (ROW_H + 2);
            }
            cy += CARD_PAD - 2;
            card.setHeight(cy);
            panel.add(card, 0, y);
            y += cy + CARD_GAP;
        }

        // Dev mode
        {
            final SlateCard card = new SlateCard(0, 0, w, 0).flat();
            int cy = CARD_PAD;
            card.add(new SlateLabel(0, 0, inner, Component.translatable("slate.hub.dev")).style(SlateLabel.Style.TITLE), CARD_PAD, cy);
            cy += 16;
            final SlateButton edit = new SlateButton(0, 0, 140, 16, Component.translatable("slate.hub.edit_layout"), () -> {
                minecraft.setScreen(parent);
                if (minecraft.screen != null) LayoutEditor.start(minecraft.screen);
            }).icon(Icon.EDIT).variant(SlateButton.Variant.GHOST).leftAligned();
            edit.enabled(Slate.config().devMode && parent != null);
            edit.tip(Component.translatable("slate.hub.edit_layout.tip"));
            card.add(new SlateToggle(0, 0, inner - 150, Component.translatable("slate.settings.dev_mode"), Slate.config().devMode,
                v -> { Slate.configFile().update(cfg -> cfg.devMode = v); edit.enabled(v && parent != null); }), CARD_PAD, cy);
            card.add(edit, CARD_PAD + inner - 140 - 4, cy + 2);
            cy += ROW_H + CARD_PAD;
            card.setHeight(cy);
            panel.add(card, 0, y);
            y += cy;
        }
        panel.setContentHeight(y);
    }

    private static String version(final String modId) {
        return SlatePlatform.get().modInfo(modId).map(i -> "v" + i.version()).orElse("");
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Component tag = Fonts.heading(Component.literal("Slate " + Slate.version()));
        // Sits in the header, left of the header actions.
        SlateDraw.textRight(g, tag, width - PAD - 28, SlateDraw.textY(0, HEADER_H), t.isVanilla() ? 0xFFA0A0A0 : t.palette().textDim(), t.isVanilla());
    }
}
