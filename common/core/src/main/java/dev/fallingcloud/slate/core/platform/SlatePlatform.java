package dev.fallingcloud.slate.core.platform;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.gui.screens.Screen;

/**
 * The loader seam. One implementation per loader, discovered through {@link java.util.ServiceLoader}
 * ({@code META-INF/services/dev.fallingcloud.slate.core.platform.SlatePlatform}).
 */
public interface SlatePlatform {

    static SlatePlatform get() {
        return Services.load(SlatePlatform.class);
    }

    Loader loader();

    boolean isModLoaded(String modId);

    Optional<ModInfo> modInfo(String modId);

    List<ModInfo> allMods();

    Path configDir();

    Path gameDir();

    /** True on the physical client (integrated server included). */
    boolean isClient();

    boolean isDedicatedServer();

    boolean isDevelopmentEnvironment();

    /**
     * Another mod's own config screen, if it registered one with the loader (NeoForge
     * {@code IConfigScreenFactory}, Fabric ModMenu). Client only. The function takes the parent screen.
     */
    Optional<Function<Screen, Screen>> otherModConfigScreen(String modId);

    /** Opens a URI in the system browser. */
    void openUri(URI uri);
}
