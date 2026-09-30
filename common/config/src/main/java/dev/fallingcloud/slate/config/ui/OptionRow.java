package dev.fallingcloud.slate.config.ui;

import dev.fallingcloud.slate.config.ConfigSettings;
import dev.fallingcloud.slate.config.SlateConfig;
import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateBadge;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import dev.fallingcloud.slate.core.widget.SlateTooltips;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * One settings row: favourite star, label (+ restart badge), the control, and a reset-to-default icon
 * that lights up while the value differs from its default. Hovering the label shows the description.
 * Both skins; keyboard reaches every child through the container focus chain.
 */
public class OptionRow extends AbstractContainerWidget {

    /**
     * How a row is drawn. {@link #ROW}: the classic line, label left and control right, lit under the pointer.
     * {@link #TILE}: the same on a plate of its own, for rows set side by side. {@link #STACKED}: a plate with the
     * label on top and the control under it, for columns too narrow to hold both on one line.
     */
    public enum Look { ROW, TILE, STACKED }

    public static final int HEIGHT = 24;
    /** Height of a {@link Look#STACKED} row. */
    public static final int STACKED_HEIGHT = 42;
    /** Narrower than this, a column stacks its rows' labels over their controls. */
    public static final int INLINE_MIN_WIDTH = 236;
    private static final Set<String> RESTART_TOASTED = new HashSet<>();

    private final OptionBinding binding;
    private final List<AbstractWidget> children = new ArrayList<>();
    private final SlateIconButton star;
    @Nullable private final AbstractWidget control;
    @Nullable private final SlateIconButton reset;
    @Nullable private Consumer<OptionRow> onChanged;
    private long flashStart;
    private final int depth;
    private final Look look;
    /** Entrance fade (tab switches, page switches): the row's own drawing; children run their own. */
    private final dev.fallingcloud.slate.core.gfx.Anim enter = new dev.fallingcloud.slate.core.gfx.Anim(1, 220, dev.fallingcloud.slate.core.gfx.Ease.OUT_CUBIC);
    private long enterStartMs;
    private final dev.fallingcloud.slate.core.gfx.Anim hoverGlow = new dev.fallingcloud.slate.core.gfx.Anim(0, 150, dev.fallingcloud.slate.core.gfx.Ease.OUT_CUBIC);

    public OptionRow(final int x, final int y, final int width, final OptionBinding binding, final int depth) {
        this(x, y, width, binding, depth, Look.ROW);
    }

    public OptionRow(final int x, final int y, final int width, final OptionBinding binding, final int depth, final Look look) {
        super(x, y, width, heightOf(look), binding.label());
        this.binding = binding;
        this.depth = depth;
        this.look = look;
        final int labelX = 22 + depth * 8;
        star = new SlateIconButton(0, 0, 16, ConfigSettings.isFavourite(binding.id()) ? Icon.STAR_FILLED : Icon.STAR,
            Component.translatable("slate_config.row.favourite"), null);
        star.onPress(() -> {
            ConfigSettings.toggleFavourite(binding.id());
            final boolean fav = ConfigSettings.isFavourite(binding.id());
            star.setIcon(fav ? Icon.STAR_FILLED : Icon.STAR).toggled(fav);
            if (onChanged != null) onChanged.accept(this);
        });
        star.toggled(ConfigSettings.isFavourite(binding.id()));
        children.add(star);

        final boolean hasReset = binding.hasDefault() && binding.type().snapshotable();
        final int resetW = 20;                                  // reserved even without a reset, so controls line up
        final int controlW = look == Look.STACKED ? stackedControlWidth(binding, width - labelX - 8) : controlWidth(binding, width - labelX - resetW);
        control = Controls.create(binding, 0, 0, controlW, this::apply);
        if (control != null) {
            control.active = binding.enabled();
            children.add(control);
        }
        if (hasReset) {
            reset = new SlateIconButton(0, 0, 16, Icon.UNDO, Component.translatable("slate_config.row.reset"), this::resetToDefault);
            reset.active = !binding.isDefault() && binding.enabled();
            children.add(reset);
        } else reset = null;
        layout();
    }

    /** The height a row of this look takes. */
    public static int heightOf(final Look look) { return look == Look.STACKED ? STACKED_HEIGHT : HEIGHT; }

    public Look look() { return look; }

    /** Under its label a control has the whole plate to itself, but never grows past a comfortable reach. */
    private static int stackedControlWidth(final OptionBinding b, final int available) {
        return switch (b.type()) {
            case BOOLEAN -> 22;
            case INFO -> 0;
            default -> Math.max(60, Math.min(220, available));
        };
    }

    private static int controlWidth(final OptionBinding b, final int available) {
        return switch (b.type()) {
            case BOOLEAN -> 22;
            case INFO -> 0;
            default -> Math.max(60, Math.min(Controls.WIDTH, available - 40));
        };
    }

    public OptionBinding binding() { return binding; }

    public OptionRow onChanged(final Consumer<OptionRow> c) { this.onChanged = c; return this; }

    @Nullable public AbstractWidget control() { return control; }

    /** Accent outline that fades over ~1.5 s (search "jump to"). */
    public void flash() { flashStart = Clock.nowMs(); }

    /** Fade the row in after {@code delayMs} like a Slate widget's entrance (honours the motion setting). */
    public void playEntrance(final int delayMs) {
        for (final AbstractWidget c : children) if (c instanceof dev.fallingcloud.slate.core.widget.SlateWidget w) w.playEntrance(delayMs);
        final float motion = Theme.current().motion();
        if (motion <= 0) { enter.snap(1); enterStartMs = 0; return; }
        enter.snap(0);
        enterStartMs = Clock.nowMs() + Math.max(0, Math.round(delayMs * motion));
    }

    private float enterProgress() {
        if (enterStartMs != 0) {
            if (Theme.current().motion() <= 0) { enterStartMs = 0; enter.snap(1); return 1f; }
            if (Clock.nowMs() < enterStartMs) return 0f;
            enterStartMs = 0;
            enter.set(1f, 220);
        }
        return Math.min(1f, Math.max(0f, enter.get()));
    }

    private void apply(final Object value) {
        try {
            binding.set(value);
        } catch (final Exception e) {
            SlateConfig.LOGGER.error("[Slate Config] cannot set {}", binding.id(), e);
            SlateToasts.show(Component.translatable("slate_config.toast.set_failed"), Component.literal(String.valueOf(e.getMessage())), Icon.ERROR);
        }
        if (binding.requiresRestart() && RESTART_TOASTED.add(binding.id())) {
            SlateToasts.show(Component.translatable("slate_config.toast.restart_title"), Component.translatable("slate_config.toast.restart_body", binding.label()), Icon.WARNING);
        }
        if (reset != null) reset.active = !binding.isDefault() && binding.enabled();
        if (onChanged != null) onChanged.accept(this);
    }

    private void resetToDefault() {
        binding.reset();
        refresh();
        if (onChanged != null) onChanged.accept(this);
    }

    /** Re-read the value into the control (after presets, reset-all, external edits). */
    public void refresh() {
        Controls.refresh(binding, control);
        if (reset != null) reset.active = !binding.isDefault() && binding.enabled();
        final boolean fav = ConfigSettings.isFavourite(binding.id());
        star.setIcon(fav ? Icon.STAR_FILLED : Icon.STAR).toggled(fav);
    }

    /** The line the label stands on: the whole row, or the upper part of a stacked one. */
    private int labelLineHeight() { return look == Look.STACKED ? 21 : HEIGHT; }

    private void layout() {
        final int x = getX(), y = getY(), w = getWidth();
        final int line = labelLineHeight();
        star.setX(x + 2 + depth * 8);
        star.setY(y + (line - 16) / 2);
        int right = x + w - 2;
        if (reset != null) { reset.setX(right - 16); reset.setY(y + (line - 16) / 2); }
        right -= 20;
        if (control == null) return;
        if (look == Look.STACKED) {
            // Under the label, from where the label starts.
            control.setX(x + 22 + depth * 8 - (binding.type() == OptionType.BOOLEAN ? 10 : 0));
            control.setY(y + line - 2 + (STACKED_HEIGHT - line - 2 - control.getHeight()) / 2);
        } else {
            control.setX(right - control.getWidth());
            control.setY(y + (HEIGHT - control.getHeight()) / 2);
        }
    }

    private int labelRight() {
        if (look == Look.STACKED) return reset != null ? reset.getX() - 4 : getX() + getWidth() - 6;
        if (control != null) return control.getX() - 6;
        return reset != null ? reset.getX() - 6 : getX() + getWidth() - 4;
    }

    private boolean overLabel(final double mx, final double my) {
        final int lx = getX() + 22 + depth * 8;
        return mx >= lx && mx < labelRight() && my >= getY() && my < getY() + labelLineHeight();
    }

    // ------------------------------------------------------------------ container plumbing

    @Override public List<? extends GuiEventListener> children() { return children; }

    private boolean inside(final double mx, final double my) {
        return mx >= getX() && mx < getX() + getWidth() && my >= getY() && my < getY() + getHeight();
    }

    @Override
    public boolean isMouseOver(final double mouseX, final double mouseY) {
        return this.active && this.visible && inside(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (!this.visible || !inside(mouseX, mouseY)) return false;
        layout();
        for (final AbstractWidget c : children) {
            if (c.mouseClicked(mouseX, mouseY, button)) {
                setFocused(c);
                if (button == 0) setDragging(true);
                return true;
            }
        }
        // Clicking the label of a toggle row flips it, like vanilla's whole-row buttons.
        if (button == 0 && binding.type() == OptionType.BOOLEAN && control != null && control.active && overLabel(mouseX, mouseY)) {
            control.mouseClicked(control.getX() + 1, control.getY() + 1, 0);
            setFocused(control);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (!inside(mouseX, mouseY)) return false;
        layout();
        for (final AbstractWidget c : children) if (c.isMouseOver(mouseX, mouseY) && c.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        return false;
    }

    @Override
    protected void renderWidget(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        layout();
        final float ea = enterProgress();
        final int x = getX(), w = getWidth();
        final int y = getY() + Math.round((1f - ea) * dev.fallingcloud.slate.core.widget.SlateWidget.ENTER_SLIDE);
        final boolean hov = inside(mouseX, mouseY) && g.containsPointInScissor(mouseX, mouseY);
        final boolean enabled = binding.enabled();
        final int h = getHeight(), line = labelLineHeight();
        hoverGlow.set(hov);
        final float hv = hoverGlow.get();
        if (look != Look.ROW && ea > 0.01f) {
            // A plate of its own: there all the time, brighter under the pointer, its left edge in the accent.
            if (t.isVanilla()) {
                g.fill(x, y, x + w, y + h, Colors.scaleAlpha(Colors.lerp(0x50000000, 0x70202020, hv), ea));
                SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(Colors.lerp(0xFF000000, 0xFFFFFFFF, hv), ea), 0);
            } else {
                SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(Colors.lerp(Colors.withAlpha(p.surface(), 0x9C), Colors.withAlpha(p.surfaceHover(), 0xE0), hv), ea), t.radius());
                SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(Colors.lerp(Colors.withAlpha(p.border(), 0x90), Colors.withAlpha(p.accent(), 0xA0), hv), ea), t.radius());
            }
        } else if (hov && ea > 0.01f) {
            if (t.isVanilla()) g.fill(x, y, x + w, y + HEIGHT, Colors.scaleAlpha(0x18FFFFFF, ea));
            else SlateDraw.pixelRound(g, x, y, w, HEIGHT, Colors.scaleAlpha(Colors.withAlpha(p.surfaceHover(), 0x70), ea), t.radius());
        }
        // Flash outline
        if (flashStart > 0) {
            final float f = 1f - (Clock.nowMs() - flashStart) / 1500f;
            if (f <= 0) flashStart = 0;
            else SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(p.accent(), f), t.radius());
        }
        // Label
        final int lx = x + 22 + depth * 8;
        int fg = enabled ? p.text() : p.textDim();
        if (t.isVanilla()) fg = enabled ? 0xFFFFFFFF : 0xFFA0A0A0;
        final int labelW = Math.max(10, labelRight() - lx - (binding.requiresRestart() ? 46 : 0));
        // Text with alpha under ~4/255 is drawn opaque by vanilla's font renderer: skip it while fading in.
        if (ea > 0.03f) {
            final int ty = y + (line - 9) / 2 + 1;
            g.drawString(SlateDraw.font(), SlateDraw.truncate(binding.label(), labelW), lx, ty, Colors.scaleAlpha(fg, ea), t.isVanilla());
            int after = lx + Math.min(labelW, SlateDraw.width(binding.label())) + 6;
            if (binding.requiresRestart() && ea > 0.5f) {
                after += SlateBadge.draw(g, Component.translatable("slate_config.row.restart"), after, y + (line - 10) / 2, p.warning()) + 4;
            }
            if (binding.type() == OptionType.INFO) {
                final Component vt = binding.valueText(binding.get());
                if (look == Look.STACKED) {
                    g.drawString(SlateDraw.font(), SlateDraw.truncate(vt, Math.max(10, w - 28)), lx, y + line + 3, Colors.scaleAlpha(p.textMuted(), ea), t.isVanilla());
                } else {
                    g.drawString(SlateDraw.font(), SlateDraw.truncate(vt, Math.max(10, x + w - 4 - after)), after, ty, Colors.scaleAlpha(p.textMuted(), ea), t.isVanilla());
                }
            } else if (look == Look.STACKED && binding.type() == OptionType.BOOLEAN && control != null) {
                // A switch alone under its label would say nothing: it says what it is set to.
                final boolean on = dev.fallingcloud.slate.config.option.OptionValues.asBoolean(binding.get(), false);
                g.drawString(SlateDraw.font(), Component.translatable(on ? "options.on" : "options.off"), control.getX() + control.getWidth() + 4,
                    control.getY() + (control.getHeight() - 9) / 2 + 1, Colors.scaleAlpha(on ? (t.isVanilla() ? 0xFFFFFFFF : p.text()) : (t.isVanilla() ? 0xFFA0A0A0 : p.textMuted()), ea), t.isVanilla());
            }
        }
        for (final AbstractWidget c : children) if (c.visible) c.render(g, mouseX, mouseY, partialTick);
        if (hov && overLabel(mouseX, mouseY)) {
            final List<Component> tip = tooltipLines();
            if (!tip.isEmpty()) SlateTooltips.request(tip, this);
        }
    }

    private List<Component> tooltipLines() {
        final List<Component> lines = new ArrayList<>();
        final Component tip = binding.tooltip();
        if (tip != null) {
            for (final String l : tip.getString().split("\n")) lines.add(Component.literal(l));
        }
        if (binding.hasDefault() && binding.type() != OptionType.ACTION) {
            lines.add(Component.translatable("slate_config.row.default", binding.valueText(binding.defaultValue())).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        if (binding.requiresRestart()) lines.add(Component.translatable("slate_config.row.restart_tip").withStyle(net.minecraft.ChatFormatting.GOLD));
        return lines;
    }

    @Override
    protected void updateWidgetNarration(final NarrationElementOutput out) {
        out.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, binding.label());
    }
}
