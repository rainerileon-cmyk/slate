package dev.fallingcloud.slate.core.module;

import dev.fallingcloud.slate.core.screen.slot.Layout;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The one place that decides what a screen shows for a feature another module provides (rule R3): with the module
 * installed the feature is there; without it, an Overhaul screen shows the element <b>locked</b> (greyed out, a
 * lock glyph, the tooltip "Install X to get this feature"), a Custom screen leaves it out, and a vanilla screen adds
 * nothing (its extra buttons are only ever added for installed modules).
 */
public final class Features {

    public enum State {
        /** The module is installed: the element behaves normally. */
        AVAILABLE,
        /** Missing module on an Overhaul screen: shown greyed out with the install tooltip. */
        LOCKED,
        /** Missing module on a Custom (or vanilla) screen: not shown at all. */
        HIDDEN;

        public boolean shown() { return this != HIDDEN; }
    }

    /** Whether the module is loaded right now. */
    public static boolean present(final String moduleId) {
        return Modules.isLoaded(moduleId);
    }

    /** R3 for a feature of {@code moduleId} on a screen shown in {@code layout}. */
    public static State state(final String moduleId, final Layout layout) {
        if (present(moduleId)) return State.AVAILABLE;
        return layout == Layout.OVERHAUL ? State.LOCKED : State.HIDDEN;
    }

    /** R3 for a feature of {@code moduleId} on the screen of menu slot {@code slotId} (in that slot's effective layout). */
    public static State state(final String moduleId, final String slotId) {
        return state(moduleId, MenuSlots.effective(slotId));
    }

    /**
     * Builds the widget for a feature of {@code moduleId} through {@code factory} and applies R3: the widget as built
     * when the module is present, the same widget locked on an Overhaul screen, and null (skip it) otherwise.
     */
    @Nullable
    public static <T extends SlateWidget> T gate(final String moduleId, final Layout layout, final Supplier<T> factory) {
        return switch (state(moduleId, layout)) {
            case AVAILABLE -> factory.get();
            case LOCKED -> { final T w = factory.get(); w.locked(moduleId); yield w; }
            case HIDDEN -> null;
        };
    }

    /** {@link #gate(String, Layout, Supplier)} for the screen of menu slot {@code slotId}. */
    @Nullable
    public static <T extends SlateWidget> T gate(final String moduleId, final String slotId, final Supplier<T> factory) {
        return gate(moduleId, MenuSlots.effective(slotId), factory);
    }

    /** "Install &lt;module&gt; to get this feature". */
    public static Component lockedTooltip(final String moduleId) {
        return Component.translatable("slate.feature.locked", KnownModules.name(moduleId));
    }

    private Features() {}
}
