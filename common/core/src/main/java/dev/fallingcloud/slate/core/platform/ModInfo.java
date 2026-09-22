package dev.fallingcloud.slate.core.platform;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Loader-agnostic description of an installed mod.
 *
 * @param iconPath a readable path to the mod's icon PNG, if the loader can hand one out
 * (NeoForge: inside the jar's file system; Fabric: via the mod container's {@code findPath})
 */
public record ModInfo(String id, String name, String version, String description, List<String> authors,
                      Optional<Path> iconPath) {}
