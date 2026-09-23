package dev.fallingcloud.slate.building.client;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.net.ServerSettingsSync;
import dev.fallingcloud.slate.core.event.SlateEvents;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * The server rules as this client sees them ({@link #get()}): the copy the current server sent, or the local
 * {@code building-server.json} in singleplayer (the integrated server's live rules, so edits in the settings tab apply
 * at once) and before the first sync. Common code reads it through {@code BuildingServerSettings.effective(level)}.
 */
public final class ServerSettingsClient {

    private static volatile @Nullable BuildingServerSettings synced;
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static boolean initialised;

    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        SlateEvents.CLIENT_LEFT_SERVER.register(() -> {
            synced = null;
            fire();
        });
    }

    public static BuildingServerSettings get() {
        final BuildingServerSettings s = synced;
        if (s == null || Minecraft.getInstance().hasSingleplayerServer()) return BuildingServerSettings.local();
        return s;
    }

    /** Whether the current rules came from a remote server. */
    public static boolean isRemote() {
        return synced != null && !Minecraft.getInstance().hasSingleplayerServer();
    }

    /** Runs {@code listener} whenever the effective server rules may have changed (sync arrived, left the server). */
    public static void onChange(final Runnable listener) {
        LISTENERS.add(listener);
    }

    /** {@link ServerSettingsSync} handler (render thread). */
    public static void accept(final ServerSettingsSync payload) {
        synced = new BuildingServerSettings(BuildingServerSettings.fromTag(payload.tag()), true);
        SlateBuilding.LOGGER.debug("[Slate Building] received server settings");
        fire();
    }

    private static void fire() {
        for (final Runnable r : LISTENERS) {
            try {
                r.run();
            } catch (final RuntimeException e) {
                SlateBuilding.LOGGER.error("[Slate Building] server settings listener failed", e);
            }
        }
    }

    private ServerSettingsClient() {}
}
