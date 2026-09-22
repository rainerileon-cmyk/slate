package dev.fallingcloud.slate.config.ui;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateList;
import dev.fallingcloud.slate.core.widget.SlateModal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

/**
 * Modal editor for list options: a list of the current items (select + Delete removes, double-click
 * loads into the field), a text field (Enter adds / replaces) and Save/Cancel. Works for string, number
 * and id lists alike; the binding coerces element types on save.
 */
public final class ListEditor {

    public static void open(final Component title, final List<String> initial, final Consumer<List<String>> onSave) {
        final List<String> items = new ArrayList<>(initial);
        final int[] editing = { -1 };
        final SlateList<String> list = new SlateList<String>(0, 0, SlateModal.WIDTH - 24, 110, 14, (g, item, index, x, y, w, h, hovered, selected, mx, my) -> {
            final Palette p = Theme.current().palette();
            g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(item), w - 8), x + 4, y + 3, selected ? p.text() : p.textMuted(), Theme.current().isVanilla());
        }).gap(1).emptyText(Component.translatable("slate_config.list.empty"));
        list.items(items);
        final ConfigTextField field = new ConfigTextField(0, 0, SlateModal.WIDTH - 24, Component.translatable("slate_config.list.value"));
        field.placeholder(Component.translatable("slate_config.list.placeholder"));
        field.maxLength(1024);
        final Runnable commit = () -> {
            final String v = field.getValue().trim();
            if (v.isEmpty()) return;
            if (editing[0] >= 0 && editing[0] < items.size()) items.set(editing[0], v);
            else items.add(v);
            editing[0] = -1;
            field.setValue("");
            list.items(items);
        };
        field.onEnter(commit);
        list.onActivate(item -> { editing[0] = list.selectedIndex(); field.setValue(item); field.setFocused(true); });
        final SlateButton add = new SlateButton(0, 0, 70, Component.translatable("slate_config.list.add"), commit).icon(Icon.PLUS);
        final SlateButton remove = new SlateButton(0, 0, 70, Component.translatable("slate_config.list.remove"), () -> {
            final int i = list.selectedIndex();
            if (i >= 0 && i < items.size()) { items.remove(i); list.items(items); list.select(Math.min(i, items.size() - 1)); }
        }).icon(Icon.MINUS).variant(SlateButton.Variant.GHOST);
        final SlateButton up = new SlateButton(0, 0, 24, Component.empty(), () -> move(list, items, -1)).icon(Icon.ARROW_UP).variant(SlateButton.Variant.GHOST);
        final SlateButton down = new SlateButton(0, 0, 24, Component.empty(), () -> move(list, items, 1)).icon(Icon.ARROW_DOWN).variant(SlateButton.Variant.GHOST);
        final SlateModal modal = new SlateModal(title, Component.translatable("slate_config.list.hint"), Icon.LIST)
            .width(300)
            .extra(list)
            .extra(field)
            .extra(new ButtonRow(SlateModal.WIDTH - 24, List.of(add, remove, up, down)));
        modal.button(Component.translatable("gui.cancel"), SlateButton.Variant.SECONDARY, null)
            .button(Component.translatable("slate_config.list.save"), SlateButton.Variant.PRIMARY, () -> onSave.accept(new ArrayList<>(items)))
            .show();
    }

    private static void move(final SlateList<String> list, final List<String> items, final int dir) {
        final int i = list.selectedIndex();
        final int j = i + dir;
        if (i < 0 || j < 0 || j >= items.size()) return;
        final String tmp = items.get(i);
        items.set(i, items.get(j));
        items.set(j, tmp);
        list.items(items);
        list.select(j);
    }

    /** A horizontal strip of buttons that lays out its children when the modal positions it. */
    static final class ButtonRow extends net.minecraft.client.gui.components.AbstractContainerWidget {
        private final List<SlateButton> buttons;

        ButtonRow(final int width, final List<SlateButton> buttons) {
            super(0, 0, width, 20, Component.empty());
            this.buttons = buttons;
        }

        private void layout() {
            int x = getX();
            for (final SlateButton b : buttons) { b.setX(x); b.setY(getY()); x += b.getWidth() + 4; }
        }

        @Override public List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() { return buttons; }

        @Override
        public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
            layout();
            for (final SlateButton b : buttons) if (b.mouseClicked(mouseX, mouseY, button)) return true;
            return false;
        }

        @Override
        protected void renderWidget(final net.minecraft.client.gui.GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            layout();
            for (final SlateButton b : buttons) b.render(g, mouseX, mouseY, partialTick);
        }

        @Override protected void updateWidgetNarration(final net.minecraft.client.gui.narration.NarrationElementOutput out) {}
    }

    private ListEditor() {}
}
