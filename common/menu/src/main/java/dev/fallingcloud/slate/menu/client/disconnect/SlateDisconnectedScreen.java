package dev.fallingcloud.slate.menu.client.disconnect;

import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateLabel;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.menu.client.LastPlayed;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Replacement for vanilla's disconnected screen: the reason in a card, "Reconnect" to the last server,
 * "Back" to wherever vanilla would have gone, plus the report-folder / bug-link buttons vanilla adds
 * when the disconnection details carry them.
 */
public final class SlateDisconnectedScreen extends SlateScreen {

    private final DisconnectionDetails details;
    private Rect card = new Rect(0, 0, 0, 0);

    public SlateDisconnectedScreen(@Nullable final Screen parent, final Component title, final DisconnectionDetails details) {
        super(title, parent);
        this.details = details;
        this.showHeader = false;
        this.showBack = false;
    }

    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    protected void build() {
        final Minecraft mc = Minecraft.getInstance();
        final int w = Math.min(width - 24, 320);
        final int inner = w - 24;
        final int textH = Math.min(Math.max(20, font.split(details.reason(), inner).size() * 10), Math.max(40, height - 150));
        final Optional<LastPlayed.Target> last = LastPlayed.quick().filter(t -> t.kind() == LastPlayed.Kind.SERVER);
        int buttons = 2 + (last.isPresent() ? 1 : 0) + (details.report().isPresent() ? 1 : 0) + (details.bugReportLink().isPresent() ? 1 : 0);
        final int h = 12 + 16 + textH + 10 + buttons * 24 + 4;
        final int x = (width - w) / 2, y = Math.max(4, (height - h) / 2);
        card = new Rect(x, y, w, h);

        final SlateScrollPanel panel = add(new SlateScrollPanel(x + 12, y + 30, inner, textH));
        final SlateLabel reason = new SlateLabel(0, 0, inner - 8, details.reason()).style(SlateLabel.Style.BODY).wrap(true);
        panel.add(reason, 0, 0);

        int by = y + 30 + textH + 10;
        if (last.isPresent()) {
            final LastPlayed.Target t = last.get();
            add(new SlateButton(x + 12, by, inner, Component.translatable("slate_menu.disconnected.reconnect", t.name() == null || t.name().isBlank() ? t.id() : t.name()),
                () -> LastPlayed.play(t, parent)).variant(SlateButton.Variant.PRIMARY).icon(Icon.REFRESH));
            by += 24;
        }
        add(new SlateButton(x + 12, by, inner, Component.translatable("gui.back"), this::back).icon(Icon.ARROW_LEFT)
            .variant(last.isPresent() ? SlateButton.Variant.SECONDARY : SlateButton.Variant.PRIMARY));
        by += 24;
        if (details.report().isPresent()) {
            final java.nio.file.Path report = details.report().get();
            add(new SlateButton(x + 12, by, inner, Component.translatable("gui.open_report_dir"), () -> Util.getPlatform().openPath(report.getParent())).icon(Icon.FOLDER));
            by += 24;
        }
        if (details.bugReportLink().isPresent()) {
            final java.net.URI uri = details.bugReportLink().get();
            add(new SlateButton(x + 12, by, inner, Component.translatable("gui.report_to_server"), () ->
                mc.setScreen(new ConfirmLinkScreen(ok -> { if (ok) Util.getPlatform().openUri(uri); mc.setScreen(this); }, uri.toString(), true))).icon(Icon.EXTERNAL));
            by += 24;
        }
        add(new SlateButton(x + 12, by, inner, Component.translatable("gui.toTitle"), () -> mc.setScreen(new TitleScreen())).variant(SlateButton.Variant.GHOST).icon(Icon.HOME));
    }

    @Override
    public void back() {
        Minecraft.getInstance().setScreen(parent != null ? parent : new TitleScreen());
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        final Theme t = Theme.current();
        final Palette p = t.palette();
        if (t.isVanilla()) {
            SlateDraw.vanillaPanel(g, card.x(), card.y(), card.w(), card.h(), false);
            SlateDraw.outline(g, card.x(), card.y(), card.w(), card.h(), 0xFF000000, 0);
            SlateDraw.outline(g, card.x() + 1, card.y() + 1, card.w() - 2, card.h() - 2, 0xFF8B8B8B, 0);
        } else {
            SlateDraw.shadow(g, card.x(), card.y(), card.w(), card.h(), 0.6f);
            SlateDraw.panel(g, card.x(), card.y(), card.w(), card.h(), p.surface(), p.borderStrong());
        }
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        Icons.draw(g, Icon.ERROR, card.x() + 12, card.y() + 11, 12, p.danger());
        g.drawString(font, SlateDraw.truncate(Fonts.heading(getTitle()), card.w() - 40), card.x() + 28, card.y() + 12, p.text(), t.isVanilla());
    }
}
