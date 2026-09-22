package dev.fallingcloud.slate.config.neoforge;

import dev.fallingcloud.slate.config.ConfigPlatform;
import dev.fallingcloud.slate.config.doc.FileDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;

/** NeoForge: every {@code ModConfigSpec} FML tracks, as editable documents (one instance per config file). */
public final class NeoForgeConfigPlatform implements ConfigPlatform {

    private static final Map<String, FileDocument> DOCS = new ConcurrentHashMap<>();

    @Override
    public List<FileDocument> nativeConfigs(final String modId) {
        final List<FileDocument> out = new ArrayList<>();
        try {
            for (final ModConfig mc : ModConfigs.getModConfigs(modId)) doc(mc).ifPresent(out::add);
        } catch (final Throwable ignored) {}
        return out;
    }

    @Override
    public Optional<FileDocument> nativeConfig(final String fileName) {
        try {
            final ModConfig mc = ModConfigs.getFileMap().get(fileName);
            return mc == null ? Optional.empty() : doc(mc);
        } catch (final Throwable t) {
            return Optional.empty();
        }
    }

    private static Optional<FileDocument> doc(final ModConfig mc) {
        if (!(mc.getSpec() instanceof ModConfigSpec spec)) return Optional.empty();
        return Optional.of(DOCS.computeIfAbsent(mc.getFileName(), k -> new NeoForgeSpecDocument(mc, spec)));
    }
}
