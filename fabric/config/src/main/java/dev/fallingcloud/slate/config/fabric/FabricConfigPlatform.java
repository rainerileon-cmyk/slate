package dev.fallingcloud.slate.config.fabric;

import dev.fallingcloud.slate.config.ConfigPlatform;
import dev.fallingcloud.slate.config.doc.FileDocument;
import java.util.List;
import java.util.Optional;

/** Fabric has no loader-level config registry; mods' files are found by name and edited generically. */
public final class FabricConfigPlatform implements ConfigPlatform {

    @Override public List<FileDocument> nativeConfigs(final String modId) { return List.of(); }

    @Override public Optional<FileDocument> nativeConfig(final String fileName) { return Optional.empty(); }
}
