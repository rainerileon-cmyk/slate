package dev.fallingcloud.slate.core.stage.scene;

import dev.fallingcloud.slate.core.stage.mesh.StageModels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * What the {@code /slate scene ...} client command does, loader-independent; the loader glue only builds the
 * Brigadier tree and prints the returned component.
 * <ul>
 *   <li>{@code /slate scene capture <template> <scene> [x y z]}: {@link SceneCapture};</li>
 *   <li>{@code /slate scene list}: scene overrides in {@code config/slate/scenes};</li>
 *   <li>{@code /slate scene reload}: drops the scene and model caches so edited files show on the next open.</li>
 * </ul>
 */
public final class SceneCommandHandler {

    public Component capture(final String template, final String scene, @Nullable final BlockPos origin) {
        return SceneCapture.capture(template, scene, origin);
    }

    public Component list() {
        final Path dir = SceneLoader.configDir();
        final List<String> names = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> names.add(p.getFileName().toString().replace(".json", "")));
            } catch (final Exception ignored) {}
        }
        if (names.isEmpty()) return Component.translatable("slate.stage.scenes.none", dir.toString());
        return Component.translatable("slate.stage.scenes.list", names.size(), String.join(", ", names));
    }

    public Component reload() {
        SceneLoader.clear();
        StageModels.clear();
        return Component.translatable("slate.stage.scenes.reloaded");
    }
}
