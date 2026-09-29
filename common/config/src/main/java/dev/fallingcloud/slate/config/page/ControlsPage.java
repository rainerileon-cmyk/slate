package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.mods.ModConfigTargets;
import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.KeyBinding;
import dev.fallingcloud.slate.config.option.KeySearch;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.resolver.VanillaOptions;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.OptionRow;
import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.config.ui.ConfigSearchField;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.platform.ModInfo;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
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
 * Controls: Mouse, Movement and Key binds tabs; key binds are grouped by category under headers (mods' categories
 * included) with conflict detection, an unbound filter and reset-all. Key capture is routed here by
 * the hub so a mouse button pressed anywhere on the screen binds.
 */
public final class ControlsPage extends OptionPageBase {

    private static final List<String> VANILLA_ORDER = List.of("key.categories.movement", "key.categories.gameplay", "key.categories.inventory",
        "key.categories.creative", "key.categories.multiplayer", "key.categories.ui", "key.categories.misc");

    /** The key binds tab: toolbar plus one collapsible header per key category (too many for tabs). */
    public static final String KEYS_TAB = "keys";

    private boolean unboundOnly;
    /** The key binds tab's own search: a key ("r", "left alt", "ctrl+r", "mouse 4") or words of a name / category ({@link KeySearch}). */
    private String keySearch = "";
    private boolean keySearchFocus;
    @Nullable private ConfigSearchField keyField;
    @Nullable private Toolbar keyToolbar;

    public ControlsPage() {
        super("controls", Component.translatable("slate_config.page.controls"), Icon.KEYBOARD);
    }

    /** Whether {@code m} matches the key search. */
    static boolean keyMatches(final KeyMapping m, final String query) {
        return KeySearch.matches(m, query, "");
    }

    @Override
    protected List<Section> sections() {
        final List<Section> out = new ArrayList<>();
        out.add(Section.of("mouse", Component.translatable("slate_config.controls.mouse"),
            VanillaOptions.all("mouseSensitivity", "invertYMouse", "rawMouseInput", "discrete_mouse_scroll", "mouseWheelSensitivity", "touchscreen")));
        out.add(Section.of("movement", Component.translatable("slate_config.controls.movement"),
            VanillaOptions.all("toggleSprint", "toggleCrouch", "autoJump")));
        final Component keysTitle = Component.translatable("slate_config.controls.keys");
        final Section tools = Section.of("keys", Component.empty()).fixed().tab(KEYS_TAB, keysTitle);
        tools.custom(this::toolbar);
        out.add(tools);
        final Map<String, List<KeyMapping>> byCategory = new LinkedHashMap<>();
        final List<KeyMapping> all = new ArrayList<>(List.of(Minecraft.getInstance().options.keyMappings));
        all.sort(Comparator.comparing(KeyMapping::getCategory).thenComparing(m -> Component.translatable(m.getName()).getString()));
        for (final KeyMapping m : all) {
            if (unboundOnly && !m.isUnbound()) continue;
            if (!keyMatches(m, keySearch)) continue;
            byCategory.computeIfAbsent(m.getCategory(), k -> new ArrayList<>()).add(m);
        }
        final List<String> cats = new ArrayList<>(byCategory.keySet());
        cats.sort(Comparator.comparingInt((String c) -> { final int i = VANILLA_ORDER.indexOf(c); return i < 0 ? 100 : i; })
            .thenComparing(c -> Component.translatable(c).getString()));
        for (final String cat : cats) {
            final Section s = Section.of("keys." + cat, Component.translatable(cat)).tab(KEYS_TAB, keysTitle);
            for (final KeyMapping m : byCategory.get(cat)) s.add(new KeyBinding(m));
            out.add(s);
        }
        final Section controller = controllerSection();
        if (controller != null) out.add(controller);
        return out;
    }

    /** Mods that add controller support; the first one installed gets the Controller tab. */
    private static final List<String> CONTROLLER_MODS = List.of("controlify", "controllable", "midnightcontrols");

    /**
     * Controls › Controller, only when a controller mod is installed: which mod provides it and the ways into its own
     * settings (its screen, its config files), so a pad player never has to hunt through the Mods page.
     */
    @Nullable
    private static Section controllerSection() {
        for (final String modId : CONTROLLER_MODS) {
            if (!SlatePlatform.get().isModLoaded(modId)) continue;
            String name = modId, version = "";
            for (final ModInfo m : SlatePlatform.get().allMods()) {
                if (m.id().equals(modId)) { name = m.name(); version = m.version(); break; }
            }
            final Component title = Component.translatable("slate_config.controls.controller");
            final Section s = Section.of("controller", title).fixed().tab("controller", title);
            final String provider = name + (version.isBlank() ? "" : " " + version);
            s.add(Binding.of("controls:controller", OptionType.INFO, Component.translatable("slate_config.controls.controller.info"))
                .getter(() -> provider)
                .searchWords("controller gamepad joystick " + modId));
            int i = 0;
            for (final ModConfigTargets.Target t : ModConfigTargets.forMod(modId)) {
                s.add(Binding.of("controls:controller_" + i++, OptionType.ACTION, t.label())
                    .tooltip(Component.translatable("slate_config.controls.controller.open", name))
                    .actionIcon(t.icon())
                    .action(Component.translatable("slate_config.row.open"), t.open())
                    .searchWords("controller gamepad " + modId));
            }
            return s;
        }
        return null;
    }

    @Override
    protected boolean pills(final String tabKey) { return !KEYS_TAB.equals(tabKey); }

    private AbstractWidget toolbar(final int w) {
        final int searchW = Math.max(90, w - 150 - 120 - 16);
        final ConfigSearchField search = new ConfigSearchField(0, 0, searchW, s -> {
            if (s.equals(keySearch)) return;
            keySearch = s;
            keySearchFocus = true;                 // the rebuild recreates this field: give it the focus back
            rebuild();
        });
        search.setValue(keySearch);
        search.placeholder(Component.translatable("slate_config.controls.search_keys"));
        keyField = search;
        final SlateToggle unbound = new SlateToggle(0, 0, 150, Component.translatable("slate_config.controls.unbound_only"), unboundOnly, v -> { unboundOnly = v; rebuild(); });
        final SlateButton reset = new SlateButton(0, 0, 120, Component.translatable("slate_config.controls.reset_keys"), () ->
            SlateModal.confirmDanger(Component.translatable("slate_config.controls.reset_keys"), Component.translatable("slate_config.controls.reset_keys.body"),
                Component.translatable("slate_config.controls.reset_keys"), () -> {
                    for (final KeyMapping m : Minecraft.getInstance().options.keyMappings) m.setKey(m.getDefaultKey());
                    KeyMapping.resetMapping();
                    Minecraft.getInstance().options.save();
                    rebuild();
                })).icon(Icon.UNDO).variant(SlateButton.Variant.DANGER);
        final Toolbar t = new Toolbar(w, List.of(search, unbound, reset));
        keyToolbar = t;
        return t;
    }

    @Override
    public void build(final SidebarScreen screen, final Rect area) {
        keyField = null;
        keyToolbar = null;
        super.build(screen, area);
        markConflicts();
        if (keySearchFocus) {
            keySearchFocus = false;
            if (keyField != null && keyToolbar != null) {
                screen.setFocused(keyToolbar);
                keyToolbar.setFocused(keyField);
                keyField.setFocused(true);
            }
        }
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
