package dev.fallingcloud.slate.building.neoforge.compat;

import dev.fallingcloud.slate.building.client.input.BuildKeys;
import dev.fallingcloud.slate.building.client.input.ExclusiveKeys;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Create's "Access Nearby Toolboxes" radial ({@code AllKeys.TOOLBELT}, Left Alt by default) does not go through
 * {@code KeyMapping.set}: {@code ToolboxHandlerClient.onKeyInput} reacts to the raw {@code InputEvent.Key} and matches
 * the key code itself, so {@code ExclusiveKeys} cannot keep it off our swap key. With a Create toolbox in range, Alt
 * while holding a block would open Create's radial screen on top of our wheel (and close the wheel, since a screen
 * opened). This vetoes exactly that screen while our swap key holds Alt exclusively; with an empty hand (no claim)
 * Create's radial opens as usual. Client only.
 */
final class CreateToolbeltCompat {

    static final String RADIAL = "com.simibubi.create.content.equipment.toolbox.RadialToolboxMenu";

    /** Dev harness switch, to show the conflict without this compat. */
    static volatile boolean enabled = true;

    static void register() {
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Opening.class, CreateToolbeltCompat::onOpening);
    }

    private static void onOpening(final ScreenEvent.Opening event) {
        final Screen screen = event.getNewScreen();
        if (!enabled || screen == null || !RADIAL.equals(screen.getClass().getName())) return;
        if (ExclusiveKeys.isHeldExclusively(BuildKeys.SWAP)) event.setCanceled(true);
    }

    private CreateToolbeltCompat() {}
}
