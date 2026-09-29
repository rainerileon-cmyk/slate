package dev.fallingcloud.slate.core.client.setup;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.config.CoreConfig;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.Reskin;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateCard;
import dev.fallingcloud.slate.core.widget.SlateLabel;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateToggle;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The first-launch setup: three switches, each explained, each applied the moment it is flipped, with live previews
 * of what they change, then Confirm. Shown once before the title screen ({@code MinecraftSetupMixin} adds it to
 * the start-up screen chain while {@code setupDone} is false) and again from Slate settings ("Run setup again").
 * <ul>
 *   <li><b>Custom layout</b> - Slate Menu's rebuilt screens instead of vanilla's (the LAYOUT, {@code customLayout}).</li>
 *   <li><b>Custom menu style</b> - the dark modern look instead of vanilla's stone (the STYLE, {@code skin}).</li>
 *   <li><b>Custom container style</b> - inventories and containers in Slate's panel ({@code reskinContainers}).</li>
 * </ul>
 * The screen itself follows the style switch live (its widgets read the theme every frame), so flipping "menu
 * style" restyles the page you are looking at, and the previews redraw from the config every frame.
 */
public final class SlateSetupScreen extends SlateScreen {

    private static final int CARD_PAD = 10, GAP = 8, BOTTOM_H = 34;

    @Nullable private final Runnable onDone;
    private int titleY;
    private Rect bottom = new Rect(0, 0, 0, 0);

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
        final int contentW = Math.min(width - PAD * 2, 640);
        final int x0 = (width - contentW) / 2;
        final Rect body = new Rect(x0, top, contentW, height - top - BOTTOM_H);
        bottom = new Rect(x0, height - BOTTOM_H, contentW, BOTTOM_H);

        final SlateScrollPanel panel = add(new SlateScrollPanel(body.x(), body.y(), body.w(), body.h()).padding(2));
        final int inner = panel.innerWidth();
        final boolean wide = inner >= 460 && !compact;
        final int cardsW = wide ? Math.round(inner * 0.5f) : inner;
        final int previewW = wide ? inner - cardsW - GAP : inner;

        int y = 0;
        y = optionCard(panel, 0, y, cardsW, Icon.LAYERS, "layout", cfg.hasCustomLayout(), v -> apply(c -> c.setCustomLayout(v)));
        y = optionCard(panel, 0, y, cardsW, Icon.PALETTE, "style", cfg.customStyle(), v -> apply(c -> c.setCustomStyle(v)));
        y = optionCard(panel, 0, y, cardsW, Icon.BLOCK, "containers", cfg.reskinContainers, v -> apply(c -> c.reskinContainers = v));
        final int cardsBottom = y - GAP;

        // Previews: beside the cards when there is room, below them otherwise. The menus mock is 3:2, the container
        // mock is the inventory's 176 x 166.
        final int px = wide ? cardsW + GAP : 0;
        int py = wide ? 0 : y;
        final int menusH = Math.max(90, Math.min(Math.round(previewW * 2 / 3f) + 14, wide ? Math.max(90, cardsBottom * 3 / 5) : 150));
        panel.add(new PreviewWidget(previewW, menusH, PreviewWidget.Kind.MENUS), px, py);
        py += menusH + GAP;
        final int containerH = Math.max(80, Math.min(Math.round(previewW * 166 / 176f) + 14, wide ? Math.max(80, cardsBottom - menusH - GAP) : 190));
        panel.add(new PreviewWidget(previewW, containerH, PreviewWidget.Kind.CONTAINER), px, py);
        py += containerH;
        panel.setContentHeight(Math.max(cardsBottom, py) + 2);

        // Bottom bar: the confirmation, and where to find the switches later.
        final int bw = 110;
        add(new SlateButton(bottom.right() - bw, bottom.y() + (BOTTOM_H - SlateButton.HEIGHT) / 2, bw, Component.translatable("slate.setup.confirm"), this::confirm)
            .variant(SlateButton.Variant.PRIMARY).icon(Icon.CHECK));
    }

    /** One switch card: icon, the switch with its title, and a wrapped explanation. Returns the y after the card + gap. */
    private int optionCard(final SlateScrollPanel panel, final int x, final int y, final int w, final Icon icon, final String key,
                           final boolean value, final Consumer<Boolean> onChange) {
        final SlateCard card = new SlateCard(0, 0, w, 10).flat();
        final int inner = w - CARD_PAD * 2;
        final int textX = CARD_PAD + 22;
        card.add(new IconGlyph(icon), CARD_PAD, CARD_PAD + 2);
        card.add(new SlateToggle(0, 0, inner - 22, Component.translatable("slate.setup." + key), value, onChange), textX, CARD_PAD);
        final SlateLabel desc = new SlateLabel(0, 0, inner - 22, Component.translatable("slate.setup." + key + ".desc")).style(SlateLabel.Style.MUTED).wrap(true);
        card.add(desc, textX, CARD_PAD + 22);
        card.setHeight(CARD_PAD + 22 + desc.getHeight() + CARD_PAD);
        panel.add(card, x, y);
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

    /** A framed live mock, redrawn from the config every frame ({@link SetupPreview}). Not focusable. */
    private static final class PreviewWidget extends SlateWidget {
        enum Kind { MENUS, CONTAINER }

        private static final int CAPTION_H = 14;
        private final Kind kind;

        PreviewWidget(final int w, final int h, final Kind kind) {
            super(0, 0, w, h, Component.translatable(kind == Kind.MENUS ? "slate.setup.preview.menus" : "slate.setup.preview.containers"));
            this.kind = kind;
            this.active = false;
        }

        @Override public boolean mouseClicked(final double mx, final double my, final int button) { return false; }

        @Override public net.minecraft.client.gui.ComponentPath nextFocusPath(final net.minecraft.client.gui.navigation.FocusNavigationEvent e) { return null; }

        @Override protected void renderDark(final GuiGraphics g, final int mx, final int my, final float pt) { draw(g); }

        @Override protected void renderVanilla(final GuiGraphics g, final int mx, final int my, final float pt) { draw(g); }

        private void draw(final GuiGraphics g) {
            final float a = effectiveAlpha();
            if (a <= 0.004f) return;
            final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
            final CoreConfig cfg = Slate.config();
            final Component caption = kind == Kind.MENUS
                ? Component.translatable("slate.setup.preview.menus." + (cfg.hasCustomLayout() ? "custom" : "vanilla") + (cfg.customStyle() ? "_custom" : "_vanilla"))
                : Component.translatable("slate.setup.preview.containers." + (cfg.reskinContainers ? "custom" : "vanilla"));
            SlateDraw.sectionRule(g, caption, x, y, w, a);
            final Rect frame = new Rect(x, y + CAPTION_H, w, h - CAPTION_H);
            SlateDraw.floatingPanel(g, frame.x(), frame.y(), frame.w(), frame.h(), a);
            final Rect in = frame.inset(3);
            if (kind == Kind.MENUS) SetupPreview.menus(g, in, cfg.hasCustomLayout(), cfg.customStyle(), a);
            else SetupPreview.container(g, in, cfg.reskinContainers, a);
        }
    }

    // ------------------------------------------------------------------ actions

    /** Writes one switch and applies it everywhere at once (theme, restyle scope); the screen restyles itself live. */
    private void apply(final Consumer<CoreConfig> edit) {
        Slate.configFile().update(edit);
        Theme.reload();
        Reskin.invalidate();
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
        // Bottom-left hint next to Confirm.
        final Component hint = Component.translatable("slate.setup.hint");
        final int hintW = bottom.w() - 120;
        if (hintW > 60) {
            g.drawString(font, SlateDraw.truncate(hint, hintW), x0, SlateDraw.textY(bottom.y(), BOTTOM_H), van ? 0xFFA0A0A0 : p.textDim(), van);
        }
    }
}
