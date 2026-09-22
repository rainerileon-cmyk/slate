package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.option.KeyBinding;
import dev.fallingcloud.slate.config.resolver.VanillaOptions;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.OptionRow;
import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SidebarScreen;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateKeybindButton;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.core.widget.SlateToggle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Controls: mouse and movement options, then every key mapping grouped by category (mods' categories
 * included) with conflict detection, an unbound filter and reset-all. Key capture is routed here by
 * the hub so a mouse button pressed anywhere on the screen binds.
 */
public final class ControlsPage extends OptionPageBase {

    private static final List<String> VANILLA_ORDER = List.of("key.categories.movement", "key.categories.gameplay", "key.categories.inventory",
        "key.categories.creative", "key.categories.multiplayer", "key.categories.ui", "key.categories.misc");

    private boolean unboundOnly;

    public ControlsPage() {
        super("controls", Component.translatable("slate_config.page.controls"), Icon.KEYBOARD);
    }

    @Override
    protected List<Section> sections() {
        final List<Section> out = new ArrayList<>();
        out.add(Section.of("mouse", Component.translatable("slate_config.controls.mouse"),
            VanillaOptions.all("mouseSensitivity", "invertYMouse", "rawMouseInput", "discrete_mouse_scroll", "mouseWheelSensitivity", "touchscreen")));
        out.add(Section.of("movement", Component.translatable("slate_config.controls.movement"),
            VanillaOptions.all("toggleSprint", "toggleCrouch", "autoJump")));
        final Section tools = Section.of("keys", Component.translatable("slate_config.controls.keys")).fixed();
        tools.custom(this::toolbar);
        out.add(tools);
        final Map<String, List<KeyMapping>> byCategory = new LinkedHashMap<>();
        final List<KeyMapping> all = new ArrayList<>(List.of(Minecraft.getInstance().options.keyMappings));
        all.sort(Comparator.comparing(KeyMapping::getCategory).thenComparing(m -> Component.translatable(m.getName()).getString()));
        for (final KeyMapping m : all) {
            if (unboundOnly && !m.isUnbound()) continue;
            byCategory.computeIfAbsent(m.getCategory(), k -> new ArrayList<>()).add(m);
        }
        final List<String> cats = new ArrayList<>(byCategory.keySet());
        cats.sort(Comparator.comparingInt((String c) -> { final int i = VANILLA_ORDER.indexOf(c); return i < 0 ? 100 : i; })
            .thenComparing(c -> Component.translatable(c).getString()));
        for (final String cat : cats) {
            final Section s = Section.of("keys." + cat, Component.translatable(cat));
            for (final KeyMapping m : byCategory.get(cat)) s.add(new KeyBinding(m));
            out.add(s);
        }
        return out;
    }

    private AbstractWidget toolbar(final int w) {
        final SlateToggle unbound = new SlateToggle(0, 0, 150, Component.translatable("slate_config.controls.unbound_only"), unboundOnly, v -> { unboundOnly = v; rebuild(); });
        final SlateButton reset = new SlateButton(0, 0, 120, Component.translatable("slate_config.controls.reset_keys"), () ->
            SlateModal.confirmDanger(Component.translatable("slate_config.controls.reset_keys"), Component.translatable("slate_config.controls.reset_keys.body"),
                Component.translatable("slate_config.controls.reset_keys"), () -> {
                    for (final KeyMapping m : Minecraft.getInstance().options.keyMappings) m.setKey(m.getDefaultKey());
                    KeyMapping.resetMapping();
                    Minecraft.getInstance().options.save();
                    rebuild();
                })).icon(Icon.UNDO).variant(SlateButton.Variant.DANGER);
        return new Toolbar(w, List.of(unbound, reset));
    }

    @Override
    public void build(final SidebarScreen screen, final Rect area) {
        super.build(screen, area);
        markConflicts();
    }

    private void markConflicts() {
        final List<KeyMapping> all = List.of(Minecraft.getInstance().options.keyMappings);
        for (final OptionRow row : rows()) {
            if (!(row.control() instanceof SlateKeybindButton btn)) continue;
            final KeyMapping m = btn.mapping();
            if (m.isUnbound()) { btn.conflict(false); continue; }
            final List<String> clashes = new ArrayList<>();
            for (final KeyMapping o : all) if (o != m && !o.isUnbound() && m.same(o)) clashes.add(Component.translatable(o.getName()).getString());
            btn.conflict(!clashes.isEmpty());
            if (!clashes.isEmpty()) btn.tip(Component.translatable("slate_config.controls.conflict", String.join(", ", clashes)));
            else btn.tip((Component) null);
        }
    }

    @Override
    protected void onRowChanged(final OptionRow row) {
        super.onRowChanged(row);
        markConflicts();
    }

    /** The key button currently waiting for input, if any. */
    @Nullable
    public SlateKeybindButton activeCapture() {
        for (final OptionRow row : rows()) if (row.control() instanceof SlateKeybindButton b && b.isCapturing()) return b;
        return null;
    }

    public boolean captureKey(final int key, final int scan, final int mods) {
        final SlateKeybindButton b = activeCapture();
        if (b == null) return false;
        b.keyPressed(key, scan, mods);
        markConflicts();
        return true;
    }

    public boolean captureMouse(final int button) {
        final SlateKeybindButton b = activeCapture();
        if (b == null) return false;
        b.captureMouse(button);
        markConflicts();
        return true;
    }

    /** A left-to-right strip of widgets. */
    static final class Toolbar extends net.minecraft.client.gui.components.AbstractContainerWidget {
        private final List<AbstractWidget> items;

        Toolbar(final int width, final List<AbstractWidget> items) {
            super(0, 0, width, 20, Component.empty());
            this.items = items;
        }

        private void layout() {
            int x = getX();
            for (final AbstractWidget w : items) { w.setX(x); w.setY(getY()); x += w.getWidth() + 8; }
        }

        @Override public List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children() { return items; }

        @Override
        public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
            layout();
            for (final AbstractWidget w : items) if (w.mouseClicked(mouseX, mouseY, button)) { setFocused(w); return true; }
            return false;
        }

        @Override
        protected void renderWidget(final net.minecraft.client.gui.GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            layout();
            for (final AbstractWidget w : items) w.render(g, mouseX, mouseY, partialTick);
        }

        @Override protected void updateWidgetNarration(final net.minecraft.client.gui.narration.NarrationElementOutput out) {}
    }
}
