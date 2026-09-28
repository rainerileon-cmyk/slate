package dev.fallingcloud.slate.config.mods;

import dev.fallingcloud.slate.config.ConfigPlatform;
import dev.fallingcloud.slate.config.doc.ConfigFiles;
import dev.fallingcloud.slate.config.doc.Documents;
import dev.fallingcloud.slate.config.doc.FileDocument;
import dev.fallingcloud.slate.config.editor.FileEditorScreen;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The ways a mod's settings can be edited, best first: its own screen (NeoForge IConfigScreenFactory /
 * ModMenu), the loader's native config documents (NeoForge ModConfigSpec), then generic file editors for
 * whatever {@code config/} holds under its id.
 */
public final class ModConfigTargets {

    public record Target(Component label, Icon icon, Runnable open) {}

    public static List<Target> forMod(final String modId) {
        final List<Target> out = new ArrayList<>();
        final Minecraft mc = Minecraft.getInstance();
        final Optional<Function<Screen, Screen>> own = SlatePlatform.get().otherModConfigScreen(modId);
        own.ifPresent(f -> out.add(new Target(Component.translatable("slate_config.mods.own_screen"), Icon.EXTERNAL, () -> {
            final Screen parent = mc.screen;
            try {
                final Screen s = f.apply(parent);
                if (s != null) mc.setScreen(s);
            } catch (final Exception e) {
                dev.fallingcloud.slate.config.SlateConfig.LOGGER.warn("[Slate Config] {}'s config screen failed: {}", modId, e.toString());
            }
        })));
        final Set<String> covered = new HashSet<>();
        for (final FileDocument doc : ConfigPlatform.get().nativeConfigs(modId)) {
            covered.add(key(doc.path()));
            out.add(new Target(Component.literal(doc.title().getString()), Icon.SLIDERS, () -> mc.setScreen(new FileEditorScreen(mc.screen, doc))));
        }
        for (final Path p : ConfigFiles.forMod(modId)) {
            if (covered.contains(key(p))) continue;
            // Read when opened, not here: the Mods page asks for every installed mod's targets at once, and parsing
            // each mod's files up front (hundreds in a big pack) froze it.
            out.add(new Target(Component.literal(p.getFileName().toString()), Icon.CODE,
                () -> Documents.open(p, modId).ifPresent(doc -> mc.setScreen(new FileEditorScreen(mc.screen, doc)))));
        }
        return out;
    }

    private static String key(final Path p) {
        try { return p.toAbsolutePath().normalize().toString(); } catch (final Exception e) { return String.valueOf(p); }
    }

    private ModConfigTargets() {}
}
