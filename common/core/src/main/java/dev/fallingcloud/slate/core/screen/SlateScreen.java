package dev.fallingcloud.slate.core.screen;

import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Base for every Slate screen. Gives you: the themed background (solid dark, or blurred world + overlay
 * in-game; vanilla panel on the vanilla skin), a header with title, back button and right-side actions,
 * a content rect, staggered entrance animation, Esc-to-back, and the standard padding. Subclasses
 * implement {@link #build()} (called on init and resize) and optionally {@link #renderContent}.
 *
 * <p>Popups, toasts, tooltips and the dev-mode editor are drawn by Core's global screen hooks, so a
 * SlateScreen needs no code for them.</p>
 */
public abstract class SlateScreen extends Screen {

    public static final int PAD = 12;
    public static final int HEADER_H = 32;

    @Nullable protected final Screen parent;
    protected boolean showHeader = true;
    protected boolean showBack = true;
    protected int maxContentWidth = 0;              // 0 = full width
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
        super.removeWidget(listener);
    }

    @Override
    protected void clearWidgets() {
        slateRenderables.clear();
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
        final int top = showHeader ? HEADER_H : PAD;
        return new Rect(x, top, w, height - top - PAD);
    }

    public Rect headerRect() { return new Rect(0, 0, width, HEADER_H); }

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
            for (final Renderable r : slateRenderables) {
                if (r instanceof SlateWidget w) w.playEntrance(Math.min(200, i++ * 18));
                else if (r instanceof dev.fallingcloud.slate.core.widget.SlateCard c) c.playEntrance(Math.min(200, i++ * 18));
            }
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
            super.renderBackground(g, mouseX, mouseY, partialTick);
            return;
        }
        if (inWorld) {
            if (t.blurInGame()) this.renderBlurredBackground(partialTick);
            g.fill(0, 0, width, height, t.palette().overlay());
        } else {
            g.fill(0, 0, width, height, t.bg());
            // Subtle vignette: darker corners so panels read as lifted.
            SlateDraw.vgradient(g, 0, 0, width, 40, Colors.withAlpha(0x000000, 0x30), 0);
            SlateDraw.vgradient(g, 0, height - 60, width, 60, 0, Colors.withAlpha(0x000000, 0x40));
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
        final int tx = PAD + (showBack && parent != null ? 22 : 0);
        if (t.isVanilla()) {
            g.drawString(font, Fonts.heading(getTitle()), tx, (HEADER_H - 9) / 2, 0xFFFFFFFF, true);
            SlateDraw.vanillaSeparator(g, 0, HEADER_H - 2, width, true, this.minecraft.level != null);
        } else {
            g.drawString(font, Fonts.heading(getTitle()), tx, (HEADER_H - 9) / 2, p.text(), false);
            SlateDraw.hline(g, 0, HEADER_H - 1, width, p.border());
        }
    }

    /** Extra drawing after widgets (labels, dividers, stats). */
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {}

    // ------------------------------------------------------------------ helpers

    protected int nextEntranceDelay() { return Math.min(200, entranceIndex++ * 18); }

    protected Theme theme() { return Theme.current(); }

    protected Palette palette() { return Theme.current().palette(); }
}
