package dev.fallingcloud.slate.core.client.settings;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.module.KnownModules;
import dev.fallingcloud.slate.core.screen.Reskin;
import dev.fallingcloud.slate.core.screen.slot.Layout;
import dev.fallingcloud.slate.core.screen.slot.MenuSlot;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.core.screen.slot.Style;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateSegmented;
import dev.fallingcloud.slate.core.widget.SlateToggle;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The three global switches of the layout model as ready-made controls, so the setup screen, Core's settings, the
 * Config module's Interface page and every module's own settings screen (rule R6: the style must be reachable from
 * each of them) build the same rows from one place:
 * <ul>
 *   <li><b>Layout</b>: Vanilla | Custom | Overhaul. Without Slate UI the Slate layouts are disabled ("Needs Slate UI");
 *       Overhaul is disabled while no menu has an Overhaul screen yet.</li>
 *   <li><b>Style</b>: Slate | Vanilla, applied live.</li>
 *   <li><b>Containers</b>: Slate's container look on or off, applied live.</li>
 * </ul>
 * Every setter writes the config, reloads the theme and drops the restyle cache, so the change shows at once.
 */
public final class LayoutStyleRows {

    private LayoutStyleRows() {}

    // ------------------------------------------------------------------ labels

    public static Component layoutLabel() { return Component.translatable("slate.settings.layout"); }

    public static Component styleLabel() { return Component.translatable("slate.settings.style"); }

    public static Component containersLabel() { return Component.translatable("slate.setup.containers"); }

    public static Component layoutName(final Layout l) { return Component.translatable("slate.layout." + l.key()); }

    public static Component styleName(final Style s) { return Component.translatable("slate.style." + s.key()); }

    /** One line on what a layout does (the setup screen shows it under the row). */
    public static Component layoutDescription(final Layout l) { return Component.translatable("slate.layout." + l.key() + ".desc"); }

    public static Component styleDescription(final Style s) { return Component.translatable("slate.style." + s.key() + ".desc"); }

    // ------------------------------------------------------------------ availability

    /** Whether any menu can show the Overhaul layout right now (Slate UI installed and at least one Overhaul screen). */
    public static boolean overhaulExists() {
        if (!MenuSlots.uiModuleLoaded()) return false;
        for (final MenuSlot s : MenuSlots.all()) if (MenuSlots.available(s.id()).contains(Layout.OVERHAUL)) return true;
        return false;
    }

    /** Why a global layout cannot be chosen, or null when it can. */
    @Nullable
    public static Component layoutBlocker(final Layout l) {
        if (l == Layout.VANILLA) return null;
        if (!MenuSlots.uiModuleLoaded()) return Component.translatable("slate.layout.needs_ui");
        if (l == Layout.OVERHAUL && !overhaulExists()) return Component.translatable("slate.layout.not_yet");
        return null;
    }

    // ------------------------------------------------------------------ controls

    /** The global layout row. {@code onChange} runs after the layout was applied (null = nothing more). */
    public static SlateSegmented<Layout> layoutSegmented(final int x, final int y, final int width, @Nullable final Consumer<Layout> onChange) {
        final SlateSegmented<Layout> s = new SlateSegmented<>(x, y, width, List.of(Layout.values()), Slate.config().layout(),
            LayoutStyleRows::layoutName, l -> { applyLayout(l); if (onChange != null) onChange.accept(l); });
        s.disable(l -> layoutBlocker(l) != null);
        s.optionTip(l -> { final Component b = layoutBlocker(l); return b != null ? b : layoutDescription(l); });
        return s;
    }

    /** The global style row, applied live. */
    public static SlateSegmented<Style> styleSegmented(final int x, final int y, final int width, @Nullable final Consumer<Style> onChange) {
        final SlateSegmented<Style> s = new SlateSegmented<>(x, y, width, List.of(Style.values()), Slate.config().style(),
            LayoutStyleRows::styleName, st -> { applyStyle(st); if (onChange != null) onChange.accept(st); });
        s.optionTip(LayoutStyleRows::styleDescription);
        return s;
    }

    /** The container-style switch, applied live. */
    public static SlateToggle containersToggle(final int x, final int y, final int width, @Nullable final Consumer<Boolean> onChange) {
        return new SlateToggle(x, y, width, containersLabel(), Slate.config().reskinContainers,
            v -> { applyContainers(v); if (onChange != null) onChange.accept(v); });
    }

    // ------------------------------------------------------------------ setters

    public static void applyLayout(final Layout l) {
        MenuSlots.setGlobalLayout(l);
        Reskin.invalidate();
    }

    public static void applyStyle(final Style s) {
        MenuSlots.setGlobalStyle(s);
        Reskin.invalidate();
    }

    public static void applyContainers(final boolean on) {
        Slate.configFile().update(c -> c.reskinContainers = on);
        Theme.reload();
        Reskin.invalidate();
    }

    /** The name of the module a slot is waiting for, for "Install X" notes. */
    public static Component ownerName(final MenuSlot slot) {
        return KnownModules.name(slot.owner());
    }
}
