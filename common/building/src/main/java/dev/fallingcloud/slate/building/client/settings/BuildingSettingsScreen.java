package dev.fallingcloud.slate.building.client.settings;

import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.widget.SlateDropdown;
import dev.fallingcloud.slate.core.widget.SlateLabel;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateSeparator;
import dev.fallingcloud.slate.core.widget.SlateSlider;
import dev.fallingcloud.slate.core.widget.SlateTextField;
import dev.fallingcloud.slate.core.widget.SlateToggle;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The Building settings when Slate Config is not installed ({@code BuildingClient.SETTINGS_SCREEN}): the same
 * {@link BuildingSettings} groups as the Config tab, as one scrolling column of Slate widgets (toggles, sliders,
 * dropdowns, the "Edit wheels…" row). Values apply as they change.
 */
public final class BuildingSettingsScreen extends SlateScreen {

    private static final int ROW_H = 24, MAX_W = 380;
    private double keepScroll;
    private @Nullable SlateScrollPanel panel;
    private final List<Runnable> refreshers = new ArrayList<>();

    public BuildingSettingsScreen(final @Nullable Screen parent) {
        super(Component.translatable("slate_building.settings.title"), parent);
        this.maxContentWidth = MAX_W;
    }

    @Override
    protected void build() {
        refreshers.clear();
        final Rect c = contentRect();
        final SlateScrollPanel sp = new SlateScrollPanel(c.x(), c.y(), c.w(), c.h());
        sp.edgeFades(true);
        panel = sp;
        final int w = sp.innerWidth() - 4;
        int y = 0;
        // The menu style stays reachable from every module's own settings (R6), Slate UI or not.
        sp.add(new SlateSeparator(0, 0, w, Component.translatable("slate.settings.section.look")), 0, y);
        y += 16;
        sp.add(dev.fallingcloud.slate.core.client.settings.SettingRow.of(w, dev.fallingcloud.slate.core.client.settings.LayoutStyleRows.styleLabel(), null,
            dev.fallingcloud.slate.core.client.settings.LayoutStyleRows.styleSegmented(0, 0, dev.fallingcloud.slate.core.client.settings.SettingRow.controlWidth(w), null)), 0, y);
        y += dev.fallingcloud.slate.core.client.settings.SettingRow.HEIGHT + 10;
        for (final BuildingSettings.Group g : BuildingSettings.groups()) {
            sp.add(new SlateSeparator(0, 0, w, g.title()), 0, y);
            y += 16;
            if (g.description() != null) {
                final SlateLabel desc = new SlateLabel(0, 0, w, g.description()).style(SlateLabel.Style.MUTED).wrap(true);
                sp.add(desc, 0, y);
                y += desc.getHeight() + 4;
            }
            for (final BuildingSettings.Setting s : g.settings()) {
                if (s.type() == BuildingSettings.Type.TIERS) {
                    // Label on the left, the four per-tier values as text on the right.
                    final int fw = Math.min(150, w / 2);
                    final SlateLabel l = new SlateLabel(0, 0, w - fw - 6, s.label());
                    if (s.tooltip() != null) l.tip(s.tooltip());
                    final SlateTextField f = new SlateTextField(0, 0, fw, s.label()).text(String.valueOf(s.get().get()));
                    f.onChange(v -> s.set().accept(v));
                    f.active = s.enabled().getAsBoolean();
                    f.setEditable(f.active);
                    sp.add(l, 0, y + (ROW_H - l.getHeight()) / 2);
                    sp.add(f, w - fw, y + (ROW_H - f.getHeight()) / 2);
                    y += ROW_H;
                    continue;
                }
                final AbstractWidget row = row(s, w);
                if (row == null) continue;
                sp.add(row, 0, y + (ROW_H - row.getHeight()) / 2);
                y += ROW_H;
            }
            if (g.wheelEditor()) {
                sp.add(new WheelsRow(w), 0, y);
                y += ROW_H;
            }
            y += 10;
        }
        sp.setContentHeight(y);
        add(sp);
        sp.snapScroll(keepScroll);
    }

    private @Nullable AbstractWidget row(final BuildingSettings.Setting s, final int w) {
        final AbstractWidget out;
        switch (s.type()) {
            case BOOL -> {
                final SlateToggle t = new SlateToggle(0, 0, w, s.label(), Boolean.TRUE.equals(s.get().get()), v -> s.set().accept(v));
                refreshers.add(() -> t.setValue(Boolean.TRUE.equals(s.get().get())));
                out = t;
            }
            case INT, DOUBLE -> {
                final double v = s.get().get() instanceof Number n ? n.doubleValue() : 0;
                final SlateSlider sl = new SlateSlider(0, 0, w, s.label(), s.min(), s.max(), s.step(), v,
                    d -> s.format() != null ? s.format().apply(d).getString()
                        : s.type() == BuildingSettings.Type.INT ? Integer.toString((int) Math.round(d)) : String.format(Locale.ROOT, "%.2f", d),
                    d -> s.set().accept(s.type() == BuildingSettings.Type.INT ? (Object) (int) Math.round(d) : (Object) d)).compact(true);
                out = sl;
            }
            case CHOICE -> {
                final String cur = String.valueOf(s.get().get());
                out = new SlateDropdown<>(0, 0, w, s.choices(), cur, s.choiceLabel(), v -> s.set().accept(v)).label(s.label());
            }
            case INFO -> {
                final Object v = s.get().get();
                out = new SlateLabel(0, 0, w, v instanceof Component comp ? comp : Component.literal(String.valueOf(v)))
                    .style(SlateLabel.Style.MUTED).wrap(true);
            }
            default -> {
                return null;
            }
        }
        out.active = s.enabled().getAsBoolean();
        if (s.tooltip() != null && out instanceof SlateWidget sw && s.type() != BuildingSettings.Type.INFO) sw.tip(s.tooltip());
        return out;
    }

    @Override
    public void tick() {
        super.tick();
        for (final Runnable r : refreshers) r.run();
    }

    @Override
    public void removed() {
        super.removed();
        if (panel != null) keepScroll = panel.scrollAmount();
    }
}
