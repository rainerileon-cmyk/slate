package dev.fallingcloud.slate.core.client.setup;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.client.preview.ScreenPreview;
import dev.fallingcloud.slate.core.client.settings.LayoutStyleRows;
import dev.fallingcloud.slate.core.config.CoreConfig;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.Reskin;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.screen.slot.CoreSlots;
import dev.fallingcloud.slate.core.screen.slot.Layout;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.core.screen.slot.Style;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateCard;
import dev.fallingcloud.slate.core.widget.SlateLabel;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The first-launch setup: three rows, each explained and applied the moment it changes, with live previews of what
 * they change, then Confirm. Shown once before the title screen ({@code MinecraftSetupMixin} adds it to the start-up
 * screen chain while {@code setupDone} is false) and again from Slate settings ("Run setup again").
 * <ul>
 *   <li><b>Layout</b> - Vanilla, Custom or Overhaul ({@code layout}). Without Slate UI only Vanilla can be chosen
 *       and the row says so.</li>
 *   <li><b>Menu style</b> - Slate or Vanilla ({@code skin}).</li>
 *   <li><b>Containers</b> - inventories and containers in Slate's panel ({@code reskinContainers}).</li>
 * </ul>
 * The menus preview is the real title screen of the chosen layout, rendered small ({@link ScreenPreview}); the
 * containers preview is painted from the container restyle's own drawing code. The screen itself follows the style
 * row live (its widgets read the theme every frame). The rows change only the global settings; per-menu overrides
 * live in the Menus table of the settings.
 */
public final class SlateSetupScreen extends SlateScreen {

    private static final int CARD_PAD = 10, GAP = 8, BOTTOM_H = 34;

    @Nullable private final Runnable onDone;
    private int titleY;
    private Rect bottom = new Rect(0, 0, 0, 0);
    /** Kept across rebuilds (a row change rebuilds the widgets so the descriptions follow), closed with the screen. */
    private final ScreenPreview menusPreview = new ScreenPreview(() -> MenuSlots.preview(CoreSlots.TITLE, TitleScreen::new));

    /**
     * @param parent the screen to return to, or null
     * @param onDone what continues the start-up chain (the next initial screen, then the title); null = just go back
     */
    public SlateSetupScreen(@Nullable final Screen parent, @Nullable final Runnable onDone) {
        super(Component.translatable("slate.setup.title"), parent);
        this.onDone = onDone;
        this.showHeader = false;
        this.showBack = false;
        this.panoramaBackground = true;
    }

    public SlateSetupScreen(@Nullable final Screen parent) {
        this(parent, null);
    }

    /** Whether the setup still has to run (first launch, or reset from the config file). */
    public static boolean wanted() {
        return !Slate.config().setupDone;
    }

    @Override public boolean isPauseScreen() { return false; }

    // ------------------------------------------------------------------ build

    @Override
    protected void build() {
        final CoreConfig cfg = Slate.config();
        final boolean compact = height < 260;
        titleY = compact ? 6 : 14;
        final int top = titleY + (compact ? 26 : 34);
        final int contentW = Math.min(width - PAD * 2, 680);
        final int x0 = (width - contentW) / 2;
        final Rect body = new Rect(x0, top, contentW, height - top - BOTTOM_H);
        bottom = new Rect(x0, height - BOTTOM_H, contentW, BOTTOM_H);

        final SlateScrollPanel panel = add(new SlateScrollPanel(body.x(), body.y(), body.w(), body.h()).padding(2));
        final int inner = panel.innerWidth();
        final boolean wide = inner >= 470 && !compact;
        final int cardsW = wide ? Math.round(inner * 0.52f) : inner;
        final int previewW = wide ? inner - cardsW - GAP : inner;

        int y = 0;
        y = layoutCard(panel, y, cardsW);
        y = styleCard(panel, y, cardsW);
        y = containersCard(panel, y, cardsW, cfg);
        final int cardsBottom = y - GAP;

        // Previews: beside the rows when there is room, below them otherwise. The menus preview keeps a window's
        // aspect (16:9), the container mock the inventory's 176 x 166.
        final int px = wide ? cardsW + GAP : 0;
        int py = wide ? 0 : y;
        final int menusH = Math.max(80, Math.round(previewW * 9 / 16f)) + PreviewWidget.CAPTION_H;
        panel.add(new PreviewWidget(previewW, menusH, PreviewWidget.Kind.MENUS), px, py);
        py += menusH + GAP;
        final int containerH = Math.max(80, Math.min(Math.round(previewW * 166 / 176f) + PreviewWidget.CAPTION_H, wide ? Math.max(80, cardsBottom - menusH - GAP) : 190));
        panel.add(new PreviewWidget(previewW, containerH, PreviewWidget.Kind.CONTAINER), px, py);
        py += containerH;
        panel.setContentHeight(Math.max(cardsBottom, py) + 2);

        final int bw = 110;
        add(new SlateButton(bottom.right() - bw, bottom.y() + (BOTTOM_H - SlateButton.HEIGHT) / 2, bw, Component.translatable("slate.setup.confirm"), this::confirm)
            .variant(SlateButton.Variant.PRIMARY).icon(Icon.CHECK));
    }

    /** Layout: the three-way row, the chosen layout's description, and why the others are greyed out. */
    private int layoutCard(final SlateScrollPanel panel, final int y, final int w) {
        final Layout current = Slate.config().layout();
        final Component note = !MenuSlots.uiModuleLoaded() ? Component.translatable("slate.setup.needs_ui")
            : !LayoutStyleRows.overhaulExists() ? Component.translatable("slate.setup.no_overhaul_yet") : null;
        return optionCard(panel, y, w, Icon.LAYERS, Component.translatable("slate.setup.row.layout"),
            LayoutStyleRows.layoutSegmented(0, 0, w - CARD_PAD * 2 - 22, l -> changed()),
            LayoutStyleRows.layoutDescription(current), note);
    }

    private int styleCard(final SlateScrollPanel panel, final int y, final int w) {
        final Style current = Slate.config().style();
        return optionCard(panel, y, w, Icon.PALETTE, Component.translatable("slate.setup.row.style"),
            LayoutStyleRows.styleSegmented(0, 0, w - CARD_PAD * 2 - 22, s -> changed()),
            LayoutStyleRows.styleDescription(current), null);
    }

    private int containersCard(final SlateScrollPanel panel, final int y, final int w, final CoreConfig cfg) {
        final AbstractWidget toggle = LayoutStyleRows.containersToggle(0, 0, w - CARD_PAD * 2 - 22, v -> changed());
        return optionCard(panel, y, w, Icon.BLOCK, null, toggle, Component.translatable("slate.setup.containers.desc"), null);
    }

    /**
     * One row card: an accent glyph, a title (or the control's own label), the control, a wrapped description and an
     * optional muted note. Returns the y after the card and its gap.
     */
    private int optionCard(final SlateScrollPanel panel, final int y, final int w, final Icon icon, @Nullable final Component title,
                           final AbstractWidget control, final Component description, @Nullable final Component note) {
        final SlateCard card = new SlateCard(0, 0, w, 10).flat();
        final int inner = w - CARD_PAD * 2;
        final int textX = CARD_PAD + 22;
        card.add(new IconGlyph(icon), CARD_PAD, CARD_PAD + 2);
        int cy = CARD_PAD;
        if (title != null) {
            card.add(new SlateLabel(0, 0, inner - 22, Fonts.heading(title)).style(SlateLabel.Style.TITLE), textX, cy);
            cy += 16;
        }
        card.add(control, textX, cy);
        cy += control.getHeight() + 6;
        final SlateLabel desc = new SlateLabel(0, 0, inner - 22, description).style(SlateLabel.Style.MUTED).wrap(true);
        card.add(desc, textX, cy);
        cy += desc.getHeight();
        if (note != null) {
            cy += 4;
            final SlateLabel n = new SlateLabel(0, 0, inner - 22, note).style(SlateLabel.Style.CAPTION).wrap(true);
            card.add(n, textX, cy);
            cy += n.getHeight();
        }
        card.setHeight(cy + CARD_PAD);
        panel.add(card, 0, y);
        return y + card.getHeight() + GAP;
    }

    /** A 16 px accent glyph (white on vanilla), not focusable. */
    private static final class IconGlyph extends SlateWidget {
        private final Icon icon;

        IconGlyph(final Icon icon) {
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

    /** A framed live preview: the real title screen (or the painted mock when it cannot be drawn), or the container mock. */
    private final class PreviewWidget extends SlateWidget {
        enum Kind { MENUS, CONTAINER }

        static final int CAPTION_H = 14;
        private final Kind kind;

        PreviewWidget(final int w, final int h, final Kind kind) {
            super(0, 0, w, h, Component.translatable(kind == Kind.MENUS ? "slate.setup.preview.menus" : "slate.setup.preview.containers"));
            this.kind = kind;
            this.active = false;
        }

        @Override public boolean mouseClicked(final double mx, final double my, final int button) { return false; }

        @Override public net.minecraft.client.gui.ComponentPath nextFocusPath(final net.minecraft.client.gui.navigation.FocusNavigationEvent e) { return null; }

        @Override protected void renderDark(final GuiGraphics g, final int mx, final int my, final float pt) { draw(g, pt); }

        @Override protected void renderVanilla(final GuiGraphics g, final int mx, final int my, final float pt) { draw(g, pt); }

        private void draw(final GuiGraphics g, final float partialTick) {
            final float a = effectiveAlpha();
            if (a <= 0.004f) return;
            final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
            final CoreConfig cfg = Slate.config();
            final Component caption = kind == Kind.MENUS
                ? Component.translatable("slate.setup.preview.menus.caption", LayoutStyleRows.layoutName(MenuSlots.effective(CoreSlots.TITLE)), LayoutStyleRows.styleName(MenuSlots.style(CoreSlots.TITLE)))
                : Component.translatable("slate.setup.preview.containers." + (cfg.reskinContainers ? "custom" : "vanilla"));
            SlateDraw.sectionRule(g, caption, x, y, w, a);
            final Rect frame = new Rect(x, y + CAPTION_H, w, h - CAPTION_H);
            SlateDraw.floatingPanel(g, frame.x(), frame.y(), frame.w(), frame.h(), a);
            final Rect in = frame.inset(3);
            if (kind == Kind.MENUS) {
                if (!menusPreview.render(g, in.x(), in.y(), in.w(), in.h(), a, partialTick)) {
                    SetupPreview.menus(g, in, cfg.hasCustomLayout(), cfg.customStyle(), a);
                }
            } else {
                SetupPreview.container(g, in, cfg.reskinContainers, a);
            }
        }
    }

    // ------------------------------------------------------------------ actions

    /** A row changed: the setting is already applied and live; rebuild so the descriptions and the preview follow. */
    private void changed() {
        Reskin.invalidate();
        menusPreview.invalidate();
        rebuildWidgets();
    }

    private void confirm() {
        Slate.configFile().update(c -> c.setupDone = true);
        Theme.reload();
        Reskin.invalidate();
        if (onDone != null) onDone.run();
        else this.minecraft.setScreen(parent);
    }

    /** Esc and the close path confirm too: the choices on screen are already applied, so leaving keeps them. */
    @Override
    public void back() {
        confirm();
    }

    @Override
    public void tick() {
        super.tick();
        menusPreview.tick();
    }

    @Override
    public void removed() {
        super.removed();
        menusPreview.close();
    }

    // ------------------------------------------------------------------ render

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        final int x0 = bottom.x();
        final Component title = Fonts.heading(Component.translatable("slate.setup.title"));
        g.drawString(font, title, x0, titleY, van ? 0xFFFFFFFF : p.text(), van);
        if (!van) SlateDraw.accentCap(g, x0, titleY + 11, Math.min(48, font.width(title)), 1f);
        final int subY = titleY + (height < 260 ? 12 : 16);
        g.drawString(font, SlateDraw.truncate(Component.translatable("slate.setup.subtitle"), bottom.w()), x0, subY, van ? 0xFFC0C0C0 : p.textMuted(), van);
        final Component hint = Component.translatable("slate.setup.hint");
        final int hintW = bottom.w() - 120;
        if (hintW > 60) {
            g.drawString(font, SlateDraw.truncate(hint, hintW), x0, SlateDraw.textY(bottom.y(), BOTTOM_H), van ? 0xFFA0A0A0 : p.textDim(), van);
        }
    }
}
