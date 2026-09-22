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

    public static final int HEIGHT = 24;
    private static final Set<String> RESTART_TOASTED = new HashSet<>();

    private final OptionBinding binding;
    private final List<AbstractWidget> children = new ArrayList<>();
    private final SlateIconButton star;
    @Nullable private final AbstractWidget control;
    @Nullable private final SlateIconButton reset;
    @Nullable private Consumer<OptionRow> onChanged;
    private long flashStart;
    private final int depth;

    public OptionRow(final int x, final int y, final int width, final OptionBinding binding, final int depth) {
        super(x, y, width, HEIGHT, binding.label());
        this.binding = binding;
        this.depth = depth;
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
        final int resetW = hasReset ? 20 : 0;
        final int controlW = controlWidth(binding, width - labelX - resetW);
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

    private void layout() {
        final int x = getX(), y = getY(), w = getWidth();
        star.setX(x + 2 + depth * 8);
        star.setY(y + (HEIGHT - 16) / 2);
        int right = x + w - 2;
        if (reset != null) { reset.setX(right - 16); reset.setY(y + (HEIGHT - 16) / 2); right -= 20; }
        if (control != null) {
            control.setX(right - control.getWidth());
            control.setY(y + (HEIGHT - control.getHeight()) / 2);
        }
    }

    private int labelRight() {
        if (control != null) return control.getX() - 6;
        return reset != null ? reset.getX() - 6 : getX() + getWidth() - 4;
    }

    private boolean overLabel(final double mx, final double my) {
        final int lx = getX() + 22 + depth * 8;
        return mx >= lx && mx < labelRight() && my >= getY() && my < getY() + HEIGHT;
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
        final int x = getX(), y = getY(), w = getWidth();
        final boolean hov = inside(mouseX, mouseY) && g.containsPointInScissor(mouseX, mouseY);
        final boolean enabled = binding.enabled();
        if (hov) {
            if (t.isVanilla()) g.fill(x, y, x + w, y + HEIGHT, 0x18FFFFFF);
            else SlateDraw.pixelRound(g, x, y, w, HEIGHT, Colors.withAlpha(p.surfaceHover(), 0x70), t.radius());
        }
        // Flash outline
        if (flashStart > 0) {
            final float f = 1f - (Clock.nowMs() - flashStart) / 1500f;
            if (f <= 0) flashStart = 0;
            else SlateDraw.outline(g, x, y, w, HEIGHT, Colors.scaleAlpha(p.accent(), f), t.radius());
        }
        // Label
        final int lx = x + 22 + depth * 8;
        int fg = enabled ? p.text() : p.textDim();
        if (t.isVanilla()) fg = enabled ? 0xFFFFFFFF : 0xFFA0A0A0;
        final int labelW = Math.max(10, labelRight() - lx - (binding.requiresRestart() ? 46 : 0));
        g.drawString(SlateDraw.font(), SlateDraw.truncate(binding.label(), labelW), lx, y + (HEIGHT - 9) / 2 + 1, fg, t.isVanilla());
        int after = lx + Math.min(labelW, SlateDraw.width(binding.label())) + 6;
        if (binding.requiresRestart()) {
            after += SlateBadge.draw(g, Component.translatable("slate_config.row.restart"), after, y + (HEIGHT - 10) / 2, p.warning()) + 4;
        }
        if (binding.type() == OptionType.INFO) {
            final Component vt = binding.valueText(binding.get());
            g.drawString(SlateDraw.font(), SlateDraw.truncate(vt, Math.max(10, x + w - 4 - after)), after, y + (HEIGHT - 9) / 2 + 1, p.textMuted(), t.isVanilla());
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
