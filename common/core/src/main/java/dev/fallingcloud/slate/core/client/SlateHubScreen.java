package dev.fallingcloud.slate.core.client;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.editor.LayoutEditor;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.module.Modules;
import dev.fallingcloud.slate.core.module.SlateModule;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateCard;
import dev.fallingcloud.slate.core.widget.SlateLabel;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateToggle;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The Slate hub: one card per installed module with its quick actions, plus the global toggles
 * (skin, dev mode). Reachable from the pause/title menus, the hub key and the {@code slate:hub} action.
 */
public final class SlateHubScreen extends SlateScreen {

    public SlateHubScreen(@Nullable final Screen parent) {
        super(Component.translatable("slate.hub.title"), parent);
        this.maxContentWidth = 440;
    }

    @Override
    protected void build() {
        final Rect c = contentRect();
        final SlateScrollPanel panel = add(new SlateScrollPanel(c.x(), c.y() + 4, c.w(), c.h() - 4));
        int y = 0;
        final int w = c.w() - 8;

        // Global toggles
        final SlateCard global = new SlateCard(0, y, w, 62).flat();
        global.add(new SlateLabel(10, 8, w - 20, Component.translatable("slate.hub.global")).style(SlateLabel.Style.TITLE), 10, 8);
        global.add(new SlateToggle(10, 24, w / 2 - 16, Component.translatable("slate.settings.vanilla_skin"), Theme.current().isVanilla(),
            v -> { Slate.configFile().update(cfg -> cfg.skin = v ? "VANILLA" : "DARK"); Theme.reload(); }), 10, 22);
        global.add(new SlateToggle(w / 2 + 4, 24, w / 2 - 16, Component.translatable("slate.settings.dev_mode"), Slate.config().devMode,
            v -> Slate.configFile().update(cfg -> cfg.devMode = v)), w / 2 + 4, 22);
        global.add(new SlateButton(10, 42, 110, 16, Component.translatable("slate.hub.settings"), () -> minecraft.setScreen(new CoreSettingsScreen(this)))
            .icon(Icon.SETTINGS).variant(SlateButton.Variant.GHOST).leftAligned(), 10, 40);
        global.add(new SlateButton(124, 42, 110, 16, Component.translatable("slate.hub.edit_layout"), () -> {
            minecraft.setScreen(parent);
            if (minecraft.screen != null) LayoutEditor.start(minecraft.screen);
        }).icon(Icon.EDIT).variant(SlateButton.Variant.GHOST).leftAligned().enabled(Slate.config().devMode && parent != null), 124, 40);
        panel.add(global, 0, y);
        y += 70;

        for (final SlateModule m : Modules.all()) {
            final List<SlateModule.HubEntry> entries = m.hubEntries();
            final int rows = (entries.size() + 1) / 2;
            final int h = 30 + rows * 20 + (rows > 0 ? 4 : 0);
            final SlateCard card = new SlateCard(0, y, w, h).flat();
            card.add(new SlateLabel(28, 9, w - 40, m.displayName()).style(SlateLabel.Style.TITLE), 28, 9);
            card.add(new SlateLabel(w - 60, 10, 52, Component.literal(version(m.id()))).style(SlateLabel.Style.CAPTION).align(SlateLabel.Align.RIGHT), w - 62, 10);
            final Icon icon = m.icon();
            card.add(new SlateLabel(10, 10, 16, Component.empty()) {
                @Override protected void renderDark(final GuiGraphics g, final int mx, final int my, final float pt) { Icons.draw(g, icon, getX(), getY() - 2, 12, Theme.current().accent()); }
                @Override protected void renderVanilla(final GuiGraphics g, final int mx, final int my, final float pt) { Icons.draw(g, icon, getX(), getY() - 2, 12, 0xFFFFFFFF); }
            }, 10, 10);
            int i = 0;
            for (final SlateModule.HubEntry e : entries) {
                final int bx = 10 + (i % 2) * ((w - 20) / 2 + 4), by = 28 + (i / 2) * 20;
                card.add(new SlateButton(bx, by, (w - 24) / 2, 16, e.label(), e.onClick()).icon(e.icon()).variant(SlateButton.Variant.GHOST).leftAligned(), bx, by);
                i++;
            }
            panel.add(card, 0, y);
            y += h + 8;
        }
        panel.setContentHeight(y);
    }

    private static String version(final String modId) {
        return dev.fallingcloud.slate.core.platform.SlatePlatform.get().modInfo(modId).map(i -> "v" + i.version()).orElse("");
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Rect c = contentRect();
        if (!Theme.current().isVanilla()) {
            SlateDraw.textRight(g, Fonts.heading(Component.literal("Slate " + Slate.version())), c.right(), (HEADER_H - 9) / 2, Theme.current().palette().textDim());
        }
    }
}
