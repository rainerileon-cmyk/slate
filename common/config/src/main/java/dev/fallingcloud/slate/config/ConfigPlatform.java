package dev.fallingcloud.slate.config;

import dev.fallingcloud.slate.config.doc.FileDocument;
import dev.fallingcloud.slate.core.platform.Services;
import java.util.List;
import java.util.Optional;

/**
 * The Config module's loader seam (ServiceLoader, like Core's SlatePlatform): access to configs the
 * loader itself tracks. NeoForge exposes every mod's {@code ModConfigSpec} natively; Fabric has no
 * equivalent, so it returns nothing and the generic file editors take over.
 */
public interface ConfigPlatform {

    static ConfigPlatform get() {
        return Services.load(ConfigPlatform.class);
    }

    /** Native config documents registered by a mod (NeoForge: its client/common/server/startup TOMLs). */
    List<FileDocument> nativeConfigs(String modId);

    /** The native document for a config file name such as {@code lucid-client.toml}, if the loader tracks it. */
    Optional<FileDocument> nativeConfig(String fileName);

    /**
     * The modifier a key bind needs held with its key: {@code "ctrl"}, {@code "shift"}, {@code "alt"}, or {@code ""}.
     * NeoForge key binds can carry one; vanilla's (Fabric) cannot.
     */
    default String keyModifier(final net.minecraft.client.KeyMapping mapping) {
        return "";
    }
}
