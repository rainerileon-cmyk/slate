package dev.fallingcloud.slate.core.fabric;

import dev.fallingcloud.slate.core.platform.Loader;
import dev.fallingcloud.slate.core.platform.ModInfo;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.Util;
import net.minecraft.client.gui.screens.Screen;

public final class FabricPlatform implements SlatePlatform {

    @Override public Loader loader() { return Loader.FABRIC; }

    @Override public boolean isModLoaded(final String modId) { return FabricLoader.getInstance().isModLoaded(modId); }

    @Override
    public Optional<ModInfo> modInfo(final String modId) {
        return FabricLoader.getInstance().getModContainer(modId).map(FabricPlatform::convert);
    }

    @Override
    public List<ModInfo> allMods() {
        return FabricLoader.getInstance().getAllMods().stream().map(FabricPlatform::convert).toList();
    }

    private static ModInfo convert(final ModContainer c) {
        final ModMetadata m = c.getMetadata();
        final List<String> authors = m.getAuthors().stream().map(Person::getName).toList();
        final Optional<Path> icon = m.getIconPath(64).flatMap(c::findPath);
        return new ModInfo(m.getId(), m.getName(), m.getVersion().getFriendlyString(), m.getDescription(), authors, icon);
    }

    @Override public Path configDir() { return FabricLoader.getInstance().getConfigDir(); }

    @Override public Path gameDir() { return FabricLoader.getInstance().getGameDir(); }

    @Override public boolean isClient() { return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT; }

    @Override public boolean isDedicatedServer() { return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER; }

    @Override public boolean isDevelopmentEnvironment() { return FabricLoader.getInstance().isDevelopmentEnvironment(); }

    @Override
    public Optional<Function<Screen, Screen>> otherModConfigScreen(final String modId) {
        if (!isClient() || !isModLoaded("modmenu")) return Optional.empty();
        try {
            return ModMenuBridge.factoryFor(modId);
        } catch (final Throwable t) {
            return Optional.empty();
        }
    }

    @Override public void openUri(final URI uri) { Util.getPlatform().openUri(uri); }
}
