package dev.fallingcloud.slate.core.stage.platform;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.platform.Services;
import dev.fallingcloud.slate.core.stage.scene.SceneCommandHandler;

/**
 * The one loader-specific piece of the stage toolkit: registering the {@code /slate scene ...} client command.
 * NeoForge does that through {@code RegisterClientCommandsEvent}, Fabric through
 * {@code ClientCommandRegistrationCallback}; both implementations live in the loader trees and are found through
 * {@code META-INF/services/dev.fallingcloud.slate.core.stage.platform.StageCommands}. They only build the Brigadier
 * tree; the behaviour is in {@link SceneCommandHandler}. A missing service degrades to "no command" (the dev-mode
 * action still works).
 */
public interface StageCommands {

    StageCommands NONE = handler -> Slate.LOGGER.info("[Slate] stage: no client command service on this loader; use the slate:scene_capture action");

    static StageCommands get() {
        try {
            return Services.load(StageCommands.class);
        } catch (final IllegalStateException | java.util.ServiceConfigurationError e) {
            return NONE;
        }
    }

    /** Registers the command tree that calls back into {@code handler}. Called once during client init. */
    void register(SceneCommandHandler handler);
}
