package dev.fallingcloud.slate.core.neoforge;

import dev.fallingcloud.slate.core.platform.Loader;
import dev.fallingcloud.slate.core.platform.ModInfo;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;

public final class NeoForgePlatform implements SlatePlatform {

    @Override public Loader loader() { return Loader.NEOFORGE; }

    @Override public boolean isModLoaded(final String modId) { return ModList.get().isLoaded(modId); }

    @Override
    public Optional<ModInfo> modInfo(final String modId) {
        return ModList.get().getModContainerById(modId).map(c -> convert(c.getModInfo()));
    }

    @Override
    public List<ModInfo> allMods() {
        return ModList.get().getMods().stream().map(NeoForgePlatform::convert).toList();
    }

    private static ModInfo convert(final IModInfo info) {
        final List<String> authors = info.getConfig().getConfigElement("authors")
            .map(o -> List.of(String.valueOf(o).split(",\\s*"))).orElse(List.of());
        final Optional<Path> icon = info.getLogoFile().flatMap(logo -> {
            try {
                final Path p = info.getOwningFile().getFile().findResource(logo);
                return java.nio.file.Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
            } catch (final Exception e) {
                return Optional.empty();
            }
        });
        return new ModInfo(info.getModId(), info.getDisplayName(), info.getVersion().toString(), info.getDescription(), authors, icon);
    }

    @Override public Path configDir() { return FMLPaths.CONFIGDIR.get(); }

    @Override public Path gameDir() { return FMLPaths.GAMEDIR.get(); }

    @Override public boolean isClient() { return FMLEnvironment.dist.isClient(); }

    @Override public boolean isDedicatedServer() { return FMLEnvironment.dist.isDedicatedServer(); }

    @Override public boolean isDevelopmentEnvironment() { return !FMLLoader.isProduction(); }

    @Override
    public Optional<Function<Screen, Screen>> otherModConfigScreen(final String modId) {
        if (!isClient()) return Optional.empty();
        return ModList.get().getModContainerById(modId).flatMap(NeoForgeConfigScreens::factoryFor);
    }

    @Override public void openUri(final URI uri) { Util.getPlatform().openUri(uri); }

    /** Separate class so the client-only IConfigScreenFactory type is never resolved on a server. */
    static final class NeoForgeConfigScreens {
        static Optional<Function<Screen, Screen>> factoryFor(final ModContainer container) {
            return container.getCustomExtension(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class)
                .map(f -> parent -> f.createScreen(container, parent));
        }
    }
}
