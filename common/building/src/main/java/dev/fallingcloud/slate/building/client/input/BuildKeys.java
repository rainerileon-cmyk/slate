package dev.fallingcloud.slate.building.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.core.event.SlateKeys;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Slate Building's key mappings (category {@code key.categories.slate_building}; they appear in the vanilla and
 * Slate Config controls pages automatically):
 * <ul>
 *   <li>{@link #SWAP} (Left Alt): hold for the quick-swap wheel;</li>
 *   <li>{@link #BUILD_MENU} (R): the build menu;</li>
 *   <li>{@link #CANCEL} (Q): clears the pending selection or stops the running operation;</li>
 *   <li>{@link #UNDO}, {@link #REDO}, {@link #CONFIRM} (apply the pending selection; the click also confirms),
 *       {@link #EXIT_MODE}, {@link #TOGGLE_ACCURATE_PLACEMENT}, {@link #TOGGLE_FAST_BREAKING}: unbound by default;</li>
 *   <li>one per building mode, {@code key.slate_building.mode.<id>} ({@link #forMode}), unbound by default.</li>
 * </ul>
 * Both default keys are shared with other mods in the DF pack, so {@code KeyClaims} claims them through
 * {@link ExclusiveKeys} only while they would do something. Consumers poll {@code consumeClick()} / {@code isDown()}
 * (e.g. in {@code SlateEvents.CLIENT_TICK_END}).
 */
public final class BuildKeys {

    public static final String CATEGORY = "key.categories.slate_building";

    public static final KeyMapping SWAP = key("swap", GLFW.GLFW_KEY_LEFT_ALT);
    public static final KeyMapping BUILD_MENU = key("build_menu", GLFW.GLFW_KEY_R);
    public static final KeyMapping UNDO = key("undo", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping REDO = key("redo", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping CONFIRM = key("confirm", InputConstants.UNKNOWN.getValue());
    /** Q, vanilla's drop key: claimed through {@code KeyClaims} only while a selection or an operation is there to cancel. */
    public static final KeyMapping CANCEL = key("cancel", GLFW.GLFW_KEY_Q);
    public static final KeyMapping EXIT_MODE = key("exit_mode", InputConstants.UNKNOWN.getValue());
    /** Accurate placement / fast breaking on and off ({@code client.place.AccuratePlacement}). */
    public static final KeyMapping TOGGLE_ACCURATE_PLACEMENT = key("toggle_accurate_placement", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping TOGGLE_FAST_BREAKING = key("toggle_fast_breaking", InputConstants.UNKNOWN.getValue());

    private static final Map<String, KeyMapping> MODES = new LinkedHashMap<>();

    static {
        for (final BuildMode mode : BuildModes.all()) {
            MODES.put(mode.id(), new KeyMapping(mode.keyName(), InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY));
        }
    }

    private static boolean registered;

    /** Registers every mapping with Core's {@link SlateKeys} (queued until the loader's registration). Idempotent. */
    public static synchronized void init() {
        if (registered) return;
        registered = true;
        for (final KeyMapping k : new KeyMapping[] {SWAP, BUILD_MENU, UNDO, REDO, CONFIRM, CANCEL, EXIT_MODE,
            TOGGLE_ACCURATE_PLACEMENT, TOGGLE_FAST_BREAKING}) {
            SlateKeys.register(k);
        }
        for (final KeyMapping k : MODES.values()) SlateKeys.register(k);
    }

    /** The keybind that activates {@code mode}. */
    public static KeyMapping forMode(final BuildMode mode) {
        return MODES.get(mode.id());
    }

    /** Mode id → keybind, in build-menu order. */
    public static Map<String, KeyMapping> modeKeys() {
        return Collections.unmodifiableMap(MODES);
    }

    private static KeyMapping key(final String name, final int glfwKey) {
        return new KeyMapping("key.slate_building." + name, InputConstants.Type.KEYSYM, glfwKey, CATEGORY);
    }

    private BuildKeys() {}
}
