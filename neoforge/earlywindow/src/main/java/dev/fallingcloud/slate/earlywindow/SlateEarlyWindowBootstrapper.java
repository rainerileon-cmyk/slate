package dev.fallingcloud.slate.earlywindow;

import net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper;

/**
 * Runs before FML picks its start-up window: with Slate's loading screens on ({@link SlateLook#enabled}) and FML's
 * default window configured, picks {@link SlateEarlyWindow} for this launch. A pack that chose another window (Drippy
 * Loading Screen's) keeps it, and with Slate's loading screens off NeoForge's own screen stays.
 */
public class SlateEarlyWindowBootstrapper implements GraphicsBootstrapper {

    @Override
    public String name() {
        return SlateEarlyWindow.NAME;
    }

    @Override
    public void bootstrap(final String[] arguments) {
        SlateLook.choose(SlateEarlyWindow.NAME, SlateLook.enabled());
    }
}
