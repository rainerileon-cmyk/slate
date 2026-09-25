package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.resolver.CoreBindings;
import dev.fallingcloud.slate.config.resolver.VanillaOptions;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.config.JsonConfig;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.module.Modules;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateSegmented;
import dev.fallingcloud.slate.core.widget.SlateSounds;
import dev.fallingcloud.slate.core.widget.SlateTooltips;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/** Interface: Slate's theme (live), vanilla GUI options, Menu module toggles, dev mode. */
public final class InterfacePage extends OptionPageBase {

    public InterfacePage() {
        super("interface", Component.translatable("slate_config.page.interface"), Icon.PALETTE);
    }

    @Override
    protected List<Section> sections() {
        final List<Section> out = new ArrayList<>();
        // The three switches of the setup screen: layout, menu style, container style, plus the way back to that screen.
        final Section modes = Section.of("modes", Component.translatable("slate_config.interface.modes"));
        modes.addAll(CoreBindings.all("customLayout"));
        modes.custom(w -> new SlateSegmented<>(0, 0, Math.min(w, 260), List.of("DARK", "VANILLA"), Slate.config().isVanillaSkin() ? "VANILLA" : "DARK",
            s -> Component.translatable("slate.skin." + s.toLowerCase(java.util.Locale.ROOT)),
            s -> { CoreBindings.get("skin").ifPresent(b -> b.set(s)); refreshRows(); }));
        modes.addAll(CoreBindings.all("reskinContainers"));
        modes.add(Binding.of("interface:run_setup", OptionType.ACTION, Component.translatable("slate_config.interface.setup"))
            .tooltip(Component.translatable("slate_config.interface.setup.tip"))
            .actionIcon(Icon.SPARKLE)
            .action(Component.translatable("slate_config.row.open"), () -> {
                final net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                mc.setScreen(new dev.fallingcloud.slate.core.client.setup.SlateSetupScreen(mc.screen));
            })
            .searchWords("setup layout style containers vanilla custom"));
        out.add(modes);
        final Section look = Section.of("look", Component.translatable("slate_config.interface.look"));
        look.custom(SwatchRow::new);
        look.addAll(CoreBindings.all("accent", "radius", "headingFont", "pixelFont", "blurInGame"));
        out.add(look);
        out.add(Section.of("motion", Component.translatable("slate_config.interface.motion"), CoreBindings.all("motion", "transitions", "uiSounds", "toasts")));
        out.add(Section.of("restyle", Component.translatable("slate_config.interface.restyle"),
            CoreBindings.all("reskinScope", "reskinAllowlist", "reskinDenylist")));
        out.add(Section.of("vanilla", Component.translatable("slate_config.interface.vanilla"), VanillaOptions.all(
            // Narrator/contrast/fonts live under Language & Accessibility, main hand and the operator tab under Gameplay.
            "guiScale", "darkMojangStudiosBackground", "hideSplashTexts", "panoramaScrollSpeed", "reducedDebugInfo")));
        if (Modules.isLoaded("slate_menu")) out.add(SimplePages.slateModule("menu", "slate_menu", "slate_config.interface.menu"));
        final Section dev = Section.of("dev", Component.translatable("slate_config.interface.dev"), CoreBindings.all("devMode", "devGrid", "devSnap"));
        dev.add(Binding.of("interface:open_config_folder", OptionType.ACTION, Component.translatable("slate.settings.open_config_folder"))
            .actionIcon(Icon.FOLDER)
            .action(Component.translatable("slate_config.row.open"), () -> net.minecraft.Util.getPlatform().openPath(JsonConfig.dir()))
            .searchWords("folder config slate"));
        out.add(dev);
        return out;
    }

    /** The named accent presets as clickable swatches; the current accent is outlined. */
    final class SwatchRow extends SlateWidget {
        private static final int SW = 16, GAP = 4;
        private int hovered = -1;

        SwatchRow(final int width) {
            super(0, 0, width, 22, Component.translatable("slate_config.core.accent_presets"));
        }

        private int indexAt(final double mx) {
            final int i = (int) ((mx - getX() - 2) / (SW + GAP));
            return i >= 0 && i < Palette.ACCENTS.size() && mx >= getX() + 2 ? i : -1;
        }

        private void choose(final int i) {
            if (i < 0 || i >= Palette.ACCENTS.size()) return;
            CoreBindings.get("accent").ifPresent(b -> b.set(Palette.ACCENTS.get(i).color()));
            SlateSounds.tick();
            refreshRows();
        }

        @Override
        public void onClick(final double mouseX, final double mouseY) {
            super.onClick(mouseX, mouseY);
            choose(indexAt(mouseX));
        }

        @Override
        public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
            if (!this.active || !this.visible) return false;
            final int cur = currentIndex();
            if (keyCode == 263) { choose(Math.max(0, cur - 1)); return true; }
            if (keyCode == 262) { choose(Math.min(Palette.ACCENTS.size() - 1, cur + 1)); return true; }
            return false;
        }

        private int currentIndex() {
            final int accent = Theme.current().accent();
            for (int i = 0; i < Palette.ACCENTS.size(); i++) if (Palette.ACCENTS.get(i).color() == accent) return i;
            return -1;
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
            final Palette p = Theme.current().palette();
            final float a = effectiveAlpha();
            final int y = getY() + enterOffset() + 3;
            hovered = this.isHovered() ? indexAt(mouseX) : -1;
            final int cur = currentIndex();
            for (int i = 0; i < Palette.ACCENTS.size(); i++) {
                final int sx = getX() + 2 + i * (SW + GAP);
                if (sx + SW > getX() + getWidth()) break;
                final int c = Palette.ACCENTS.get(i).color();
                SlateDraw.pixelRound(g, sx, y, SW, SW, Colors.scaleAlpha(c, a), vanilla ? 0 : 2);
                if (i == cur) SlateDraw.outline(g, sx - 1, y - 1, SW + 2, SW + 2, Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.text(), a), vanilla ? 0 : 3);
                else if (i == hovered) SlateDraw.outline(g, sx, y, SW, SW, Colors.scaleAlpha(0xFFFFFFFF, 0.6f * a), vanilla ? 0 : 2);
            }
            if (hovered >= 0) SlateTooltips.request(Component.literal(Palette.ACCENTS.get(hovered).name()), this);
            SlateDraw.focusRing(g, getX(), y, Math.min(getWidth(), Palette.ACCENTS.size() * (SW + GAP)), SW, focus() * a);
        }
    }
}
