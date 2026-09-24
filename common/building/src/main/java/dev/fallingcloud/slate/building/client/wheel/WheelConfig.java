package dev.fallingcloud.slate.building.client.wheel;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.config.BuildingConfig;
import dev.fallingcloud.slate.building.config.HudSettings;
import dev.fallingcloud.slate.building.config.WheelSettings;
import dev.fallingcloud.slate.building.variant.Shape;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Null-safe access to the UI sections of {@code building.json} ({@code wheel}, {@code hud}): a hand-edited file may
 * say {@code "wheel": null} or drop the wheel list, which Gson keeps as null. Repairs in place and clamps ranges so
 * the UI can read fields without checks. Also the "the wheel config changed" signal for open overlays and screens.
 */
public final class WheelConfig {

    public static final int MIN_SLICES = 4;
    public static final int MAX_SLICES = 12;

    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    public static WheelSettings wheel() {
        final BuildingConfig cfg = SlateBuilding.config();
        if (cfg.wheel == null) cfg.wheel = new WheelSettings();
        final WheelSettings w = cfg.wheel;
        if (w.wheels == null) w.wheels = WheelSettings.defaultWheels();
        w.wheels.removeIf(x -> x == null);
        for (int i = 0; i < w.wheels.size(); i++) {
            final WheelSettings.Wheel x = w.wheels.get(i);
            if (x.name == null) x.name = "";
            if (x.entries == null) x.entries = new ArrayList<>();
            // Files written before the default names became lang keys: pick up the translation too.
            if (i == 0 && "Shapes".equals(x.name)) x.name = WheelSettings.DEFAULT_SHAPES_NAME;
            else if (i == 1 && "More".equals(x.name)) x.name = WheelSettings.DEFAULT_MORE_NAME;
        }
        if (w.menuKeyContext == null) w.menuKeyContext = "SMART";
        return w;
    }

    /**
     * How a wheel's name reads: "Wheel 3" when blank, translated when it is one of our lang keys (the defaults), else
     * the text the player typed.
     */
    public static Component displayName(final WheelSettings.Wheel wheel, final int index) {
        final String name = wheel.name == null ? "" : wheel.name;
        if (name.isBlank()) return Component.translatable("slate_building.ui.wheel.unnamed", index + 1);
        if (name.startsWith("slate_building.") && Language.getInstance().has(name)) return Component.translatable(name);
        return Component.literal(name);
    }

    public static HudSettings hud() {
        final BuildingConfig cfg = SlateBuilding.config();
        if (cfg.hud == null) cfg.hud = new HudSettings();
        if (cfg.hud.anchor == null) cfg.hud.anchor = "TOP";
        return cfg.hud;
    }

    /** {@code maxSlices} clamped to 4..12. */
    public static int maxSlices() {
        return Mth.clamp(wheel().maxSlices, MIN_SLICES, MAX_SLICES);
    }

    /** {@code wheelScale} clamped to 0.5..2. */
    public static float wheelScale() {
        final double s = wheel().wheelScale;
        return Double.isFinite(s) ? (float) Mth.clamp(s, 0.5, 2.0) : 1f;
    }

    public static float hudScale() {
        final double s = hud().scale;
        return Double.isFinite(s) ? (float) Mth.clamp(s, 0.5, 2.0) : 1f;
    }

    /** The shapes of a configured wheel: known ids, no FULL (the centre), no duplicates. */
    public static List<Shape> shapesOf(final WheelSettings.Wheel wheel) {
        final List<Shape> out = new ArrayList<>();
        for (final String id : wheel.entries) {
            final Shape s = Shape.byId(id);
            if (s != null && s != Shape.FULL && !out.contains(s)) out.add(s);
        }
        return out;
    }

    private static volatile boolean dirty;
    private static boolean initialised;

    /** Saves pending edits once per client tick (sliders change values every frame while dragged). */
    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        dev.fallingcloud.slate.core.event.SlateEvents.CLIENT_TICK_END.register(() -> {
            if (!dirty) return;
            dirty = false;
            SlateBuilding.configFile().save();
        });
    }

    /** Marks {@code building.json} for saving (at the end of this tick) and tells listeners (open wheels rebuild). */
    public static void saveAndNotify() {
        dirty = true;
        if (!initialised) {
            dirty = false;
            SlateBuilding.configFile().save();
        }
        changed();
    }

    /** Tells listeners the wheel/HUD settings changed (after a reload or an edit). */
    public static void changed() {
        for (final Runnable r : LISTENERS) {
            try {
                r.run();
            } catch (final RuntimeException e) {
                SlateBuilding.LOGGER.error("[Slate Building] wheel config listener failed", e);
            }
        }
    }

    public static void onChange(final Runnable listener) {
        LISTENERS.add(listener);
    }

    private WheelConfig() {}
}
