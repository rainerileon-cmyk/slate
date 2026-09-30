package dev.fallingcloud.slate.core.client.settings;

import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.module.Modules;
import dev.fallingcloud.slate.core.screen.slot.Layout;
import dev.fallingcloud.slate.core.screen.slot.MenuSlot;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.core.screen.slot.Style;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateCard;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateSegmented;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * One row of the Menus table (Options › Interface, and Core's settings): a menu slot's name and what implements it,
 * a Layout row listing the three layouts (only the ones the slot can show right now can be chosen; the others say
 * why), a Style row, and a reset back to the global settings while the slot has an override. Two lines: the name
 * line, then the two controls side by side (stacked when the row is narrow).
 */
public final class MenuSlotRow extends SlateCard {

    public static final int LINE = 22, GAP = 6;
    private static final int NARROW = 330;
    /** Room between the row's edge and what stands in it: the row sits in a card, whose border is its edge. */
    private static final int PAD = 6;

    private final MenuSlot slot;
    private final SlateSegmented<Layout> layout;
    private final SlateSegmented<Style> style;
    private final SlateIconButton reset;
    @Nullable private final Runnable onChange;

    public MenuSlotRow(final int width, final MenuSlot slot, @Nullable final Runnable onChange) {
        super(0, 0, width, LINE * 2);
        flat();
        this.slot = slot;
        this.onChange = onChange;
        final boolean stacked = width < NARROW;
        final int inner = width - PAD * 2;
        final int layoutW = stacked ? inner : Math.min(240, (inner - GAP) * 3 / 5);
        final int styleW = stacked ? Math.min(inner, 160) : Math.min(160, inner - GAP - layoutW);
        layout = new SlateSegmented<>(0, 0, layoutW, List.of(Layout.values()), MenuSlots.requested(slot.id()),
            LayoutStyleRows::layoutName, l -> { MenuSlots.setLayoutOverride(slot.id(), l); changed(); });
        layout.disable(l -> !MenuSlots.available(slot.id()).contains(l));
        layout.optionTip(this::layoutTip);
        style = new SlateSegmented<>(0, 0, styleW, List.of(Style.values()), MenuSlots.style(slot.id()),
            LayoutStyleRows::styleName, s -> { MenuSlots.setStyleOverride(slot.id(), s); changed(); });
        style.optionTip(LayoutStyleRows::styleDescription);
        reset = new SlateIconButton(0, 0, 16, Icon.REFRESH, Component.translatable("slate.slot.follow_global"),
            () -> { MenuSlots.clearOverrides(slot.id()); changed(); });
        add(reset, width - 16 - PAD, (LINE - 16) / 2);
        add(layout, PAD, LINE + 1);
        if (stacked) { add(style, PAD, LINE * 2 + GAP + 1); setHeight(LINE * 3 + GAP + 6); }
        else { add(style, PAD + layoutW + GAP, LINE + 1); setHeight(LINE * 2 + 6); }
        sync();
    }

    public MenuSlot slot() { return slot; }

    /** Re-reads the config into the controls (after a change made elsewhere). */
    public void sync() {
        layout.setValue(MenuSlots.requested(slot.id()));
        style.setValue(MenuSlots.style(slot.id()));
        reset.visible = MenuSlots.hasOverride(slot.id());
    }

    private void changed() {
        sync();
        if (onChange != null) onChange.run();
    }

    /** Why a layout is greyed out, or what it does when it can be chosen. */
    private Component layoutTip(final Layout l) {
        final Set<Layout> avail = MenuSlots.available(slot.id());
        if (avail.contains(l)) return LayoutStyleRows.layoutDescription(l);
        if (l == Layout.VANILLA) return Component.translatable("slate.slot.no_vanilla");
        if (!MenuSlots.uiModuleLoaded()) return Component.translatable("slate.layout.needs_ui");
        if (!Modules.isLoaded(slot.owner())) return Component.translatable("slate.slot.needs_module", LayoutStyleRows.ownerName(slot));
        return Component.translatable("slate.layout.not_yet");
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int x, final int y, final int w, final int h, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        final float a = alpha * enterProgress();
        final int ty = SlateDraw.textY(y, LINE);
        final Component name = Fonts.heading(slot.name());
        g.drawString(SlateDraw.font(), SlateDraw.truncate(name, w - 24 - PAD * 2), x + PAD, ty, Colors.scaleAlpha(van ? 0xFFFFFFFF : p.text(), a), van);
        // Right of the name: what the slot shows when the choice cannot be honoured, or the module it waits for.
        Component note = null;
        int noteColor = van ? 0xFFC0C0C0 : p.textMuted();
        final Layout requested = MenuSlots.requested(slot.id()), effective = MenuSlots.effective(slot.id());
        if (!Modules.isLoaded(slot.owner()) && MenuSlots.available(slot.id()).size() <= 1) {
            note = Component.translatable("slate.slot.needs_module", LayoutStyleRows.ownerName(slot));
            noteColor = van ? 0xFF8B8B8B : p.textDim();
        } else if (requested != effective) {
            note = Component.translatable("slate.slot.shows", LayoutStyleRows.layoutName(effective));
            noteColor = p.warning();
        } else if (MenuSlots.hasOverride(slot.id())) {
            note = Component.translatable("slate.slot.custom");
            noteColor = p.accent();
        }
        if (note != null) {
            final int nx = x + PAD + SlateDraw.width(name) + 8, maxW = w - 24 - PAD - (nx - x);
            if (maxW > 30) g.drawString(SlateDraw.font(), SlateDraw.truncate(note, maxW), nx, ty, Colors.scaleAlpha(noteColor, a), van);
        }
        SlateDraw.hline(g, x + PAD, y + h - 1, w - PAD * 2, Colors.scaleAlpha(van ? 0x30FFFFFF : Colors.withAlpha(p.border(), 0x90), a));
    }
}
