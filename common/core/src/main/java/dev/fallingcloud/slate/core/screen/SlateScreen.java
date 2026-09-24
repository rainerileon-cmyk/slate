package dev.fallingcloud.slate.core.screen;

import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateCard;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateTextField;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Base for every Slate screen. Gives you: the themed background (solid dark with a vignette, or blurred
 * world + overlay in-game; the panorama when {@link #panoramaBackground} is set; vanilla panel /
 * panorama on the vanilla skin), a header with title, back button and right-side actions, a content
 * rect, staggered entrance animation, Esc-to-back, {@code /} to focus a search field, and the standard
 * padding. Subclasses implement {@link #build()} (called on init and resize) and optionally
 * {@link #renderContent}.
 *
 * <p>Popups, toasts, tooltips and the dev-mode editor are drawn by Core's global screen hooks, so a
 * SlateScreen needs no code for them.</p>
 */
public abstract class SlateScreen extends Screen {

    public static final int PAD = 12;
    public static final int HEADER_H = 32;
    /** Entrance stagger between consecutive widgets and its cap. */
    public static final int STAGGER_MS = 18, STAGGER_MAX_MS = 200;

    @Nullable protected final Screen parent;
    protected boolean showHeader = true;
    protected boolean showBack = true;
    /** Draw the title-screen panorama behind the content when no world is loaded (title-like screens). */
    protected boolean panoramaBackground = false;
    protected int maxContentWidth = 0;              // 0 = full width
    /** A text field that {@code /} focuses (search boxes); null = none. */
    @Nullable protected EditBox slashFocusTarget;
    private boolean firstBuild = true;
    private final List<AbstractWidget> headerActions = new ArrayList<>();
    private int entranceIndex;
    /** Own copy of the renderables (vanilla's list is private; NeoForge widens it, Fabric does not). */
    private final List<Renderable> slateRenderables = new ArrayList<>();

    @Override
    protected <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(final T widget) {
        slateRenderables.add(widget);
        return super.addRenderableWidget(widget);
    }

    @Override
    protected <T extends Renderable> T addRenderableOnly(final T renderable) {
        slateRenderables.add(renderable);
        return super.addRenderableOnly(renderable);
    }

    @Override
    protected void removeWidget(final GuiEventListener listener) {
        slateRenderables.remove(listener);
        headerActions.remove(listener);
        super.removeWidget(listener);
    }

    @Override
    protected void clearWidgets() {
        slateRenderables.clear();
        headerActions.clear();
        slashFocusTarget = null;
        super.clearWidgets();
    }

    /** Everything added through addRenderableWidget/addRenderableOnly, in order. */
    public List<Renderable> renderableList() { return slateRenderables; }

    protected SlateScreen(final Component title, @Nullable final Screen parent) {
        super(title);
        this.parent = parent;
    }

    // ------------------------------------------------------------------ layout

    /** The area below the header, inset by PAD and limited to {@link #maxContentWidth}. */
    public Rect contentRect() {
        int w = width - PAD * 2;
        int x = PAD;
        if (maxContentWidth > 0 && w > maxContentWidth) { w = maxContentWidth; x = (width - w) / 2; }
        final int top = showHeader ? HEADER_H + 4 : PAD;
        return new Rect(x, top, w, height - top - PAD);
    }

    public Rect headerRect() { return new Rect(0, 0, width, HEADER_H); }

    /** Where the header title starts (after the back button). */
    protected int headerTitleX() { return PAD + (showBack && parent != null ? 22 : 0); }

    @Override
    protected final void init() {
        headerActions.clear();
        entranceIndex = 0;
        build();
        if (showHeader && showBack && parent != null) {
            add(new SlateIconButton(PAD - 4, (HEADER_H - 20) / 2, 20, Icon.ARROW_LEFT, Component.translatable("gui.back"), this::back));
        }
        if (firstBuild) {
            firstBuild = false;
            int i = 0;
            for (final Renderable r : slateRenderables) i = entrance(r, i);
        }
    }

    /** Create and add widgets. Runs on every init/resize with a clean widget list. */
    protected abstract void build();

    /** Shortcut for addRenderableWidget. */
    protected <T extends GuiEventListener & Renderable & NarratableEntry> T add(final T widget) {
        return addRenderableWidget(widget);
    }

    /** Adds a button to the right side of the header (added right-to-left). */
    protected <T extends AbstractWidget> T addHeaderAction(final T widget) {
        int x = width - PAD;
        for (final AbstractWidget a : headerActions) x = a.getX();
        widget.setX(x - widget.getWidth() - (headerActions.isEmpty() ? 0 : 4));
        widget.setY((HEADER_H - widget.getHeight()) / 2);
        headerActions.add(widget);
        addRenderableWidget(widget);
        return widget;
    }

    /**
     * Plays the staggered entrance on a renderable (and its children for cards and scroll panels);
     * {@code index} numbers the stagger. Returns the next index.
     */
    public static int entrance(final Renderable r, int index) {
        if (r instanceof SlateWidget w) {
            w.playEntrance(Math.min(STAGGER_MAX_MS, index++ * STAGGER_MS));
        } else if (r instanceof SlateCard c) {
            c.playEntrance(Math.min(STAGGER_MAX_MS, index++ * STAGGER_MS));
        } else if (r instanceof SlateScrollPanel panel) {
            for (final GuiEventListener child : panel.children()) if (child instanceof Renderable cr) index = entrance(cr, index);
        }
        return index;
    }

    // ------------------------------------------------------------------ navigation

    public void back() {
        Popups.closeAll();
        this.minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        back();
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == 256 && shouldCloseOnEsc()) { back(); return true; }
        if (keyCode == 47 && slashFocusTarget != null && !(getFocused() instanceof EditBox eb && eb.isFocused())) {
            setFocused(slashFocusTarget);
            if (slashFocusTarget instanceof SlateTextField f) f.setFocused(true);
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return this.minecraft == null || this.minecraft.level == null || super.isPauseScreen();
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final boolean inWorld = this.minecraft.level != null;
        if (t.isVanilla()) {
            if (!inWorld && panoramaBackground) {
                // Title-like: the panorama, no blur, no tile - just a gentle veil so text stays readable.
                renderPanorama(g, partialTick);
                SlateDraw.vignette(g, 0, 0, width, height, 0.3f);
                return;
            }
            super.renderBackground(g, mouseX, mouseY, partialTick);
            return;
        }
        if (inWorld) {
            if (t.blurInGame()) this.renderBlurredBackground(partialTick);
            g.fill(0, 0, width, height, t.palette().overlay());
        } else if (panoramaBackground) {
            renderPanorama(g, partialTick);
            g.fill(0, 0, width, height, Colors.withAlpha(t.bg(), 0x99));
            SlateDraw.vignette(g, 0, 0, width, height, 0.45f);
        } else {
            g.fill(0, 0, width, height, t.bg());
            // Subtle vignette: darker edges so panels read as lifted.
            SlateDraw.vignette(g, 0, 0, width, height, 0.3f);
        }
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);   // background + widgets
        if (showHeader) renderHeader(g);
        renderContent(g, mouseX, mouseY, partialTick);
    }

    protected void renderHeader(final GuiGraphics g) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final int tx = headerTitleX();
        int right = width - PAD;
        for (final AbstractWidget a : headerActions) right = Math.min(right, a.getX() - 8);
        final int ty = SlateDraw.textY(0, HEADER_H);
        if (t.isVanilla()) {
            g.drawString(font, SlateDraw.truncate(Fonts.heading(getTitle()), right - tx), tx, ty, 0xFFFFFFFF, true);
            SlateDraw.vanillaSeparator(g, 0, HEADER_H - 2, width, true, this.minecraft.level != null);
        } else {
            // In a world the header sits on the blurred scene: a translucent band gives it a plate to rest on.
            if (this.minecraft.level != null) SlateDraw.rect(g, 0, 0, width, HEADER_H, Colors.withAlpha(p.bg(), 0x90));
            final net.minecraft.util.FormattedCharSequence title = SlateDraw.truncate(Fonts.heading(getTitle()), right - tx);
            g.drawString(font, title, tx, ty, p.text(), false);
            SlateDraw.hline(g, 0, HEADER_H - 1, width, p.border());
            // The heading's accent cap: a short underline that fades out, the same mark the build menu's titles carry.
            SlateDraw.accentCap(g, tx, HEADER_H - 2, Math.max(16, Math.min(font.width(title), 48)), 1f);
        }
    }

    /** Extra drawing after widgets (labels, dividers, stats). */
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {}

    // ------------------------------------------------------------------ helpers

    protected int nextEntranceDelay() { return Math.min(STAGGER_MAX_MS, entranceIndex++ * STAGGER_MS); }

    protected Theme theme() { return Theme.current(); }

    protected Palette palette() { return Theme.current().palette(); }

    protected boolean inWorld() { return this.minecraft != null && this.minecraft.level != null; }
}
