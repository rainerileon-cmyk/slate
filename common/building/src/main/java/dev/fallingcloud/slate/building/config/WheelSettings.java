package dev.fallingcloud.slate.building.config;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code building.json → wheel}: the Alt quick-swap wheel (design §5) and related input behaviour.
 *
 * <p>Owner: C (ui). Skeleton declares the fields and defaults of design §10.
 */
public final class WheelSettings {

    /** One wheel page: a name (shown under the wheel) and its slices as {@code Shape} ids, in order. */
    public static final class Wheel {
        public String name = "";
        public List<String> entries = new ArrayList<>();

        public Wheel() {}

        public Wheel(final String name, final List<String> entries) {
            this.name = name;
            this.entries = new ArrayList<>(entries);
        }
    }

    /** Master switch for the Alt wheel. */
    public boolean swapEnabled = true;
    /** The wheel pages in order. The dynamic "Chisel" page is added after these when {@link #includeChiselPage}. */
    public List<Wheel> wheels = defaultWheels();
    /** Slices per page (4..12); longer wheels spill onto extra pages. */
    public int maxSlices = 8;
    /** Hide shapes the held material does not have (true) or show them greyed out (false). */
    public boolean hideUnavailable = true;
    /** Mouse wheel steps the selection along the ring while the wheel is open. */
    public boolean scrollSelects = true;
    /** LMB / RMB switch to the previous / next page while the wheel is open. */
    public boolean clickSwitchesWheel = true;
    /** Releasing the swap key applies the hovered slice (false: click to apply). */
    public boolean releaseToSelect = true;
    /** Number keys 1..9 pick a slice directly while the wheel is open. */
    public boolean numberKeys = true;
    /** Size multiplier of the wheel overlay. */
    public double wheelScale = 1.0;
    /** Show the name + count label under the wheel. */
    public boolean labels = true;
    /** Add the material's chisel group as a wheel page (when chisel is unlocked). */
    public boolean includeChiselPage = true;
    /** Middle-click on a variant picks the hotbar slot with that material and swaps it to the picked shape. */
    public boolean pickBlockSwaps = true;
    /** The swap key takes Alt for itself while it would open the wheel (other mods on Alt do not fire). */
    public boolean exclusiveSwapKey = true;
    /** When the build-menu key claims its key: {@code SMART} (holding a block / variant / tool / toolbox, or a mode is active) or {@code ALWAYS}. */
    public String menuKeyContext = "SMART";

    /** The default pages: "Shapes" (8 common shapes) and "More" (the rest). */
    public static List<Wheel> defaultWheels() {
        final List<Wheel> out = new ArrayList<>();
        out.add(new Wheel("Shapes", List.of("stairs", "slab", "vertical_slab", "vertical_stairs", "wall", "fence", "step", "panel")));
        out.add(new Wheel("More", List.of("fence_gate", "vertical_step", "post", "layer", "pane")));
        return out;
    }
}
