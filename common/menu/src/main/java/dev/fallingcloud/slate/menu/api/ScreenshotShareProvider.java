package dev.fallingcloud.slate.menu.api;

import java.nio.file.Path;

/**
 * Plugged in by the Multiplayer module so the screenshot gallery offers "Share". The provider owns the
 * whole flow (pick a friend, upload, toast); the gallery only hands over the file.
 */
@FunctionalInterface
public interface ScreenshotShareProvider {

    void share(Path screenshot);
}
