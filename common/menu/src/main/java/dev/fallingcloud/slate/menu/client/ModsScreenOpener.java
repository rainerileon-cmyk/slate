package dev.fallingcloud.slate.menu.client;

import dev.fallingcloud.slate.menu.SlateMenu;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.gui.screens.Screen;

/**
 * Finds the loader's mod-list screen without a hard reference: NeoForge's built-in
 * {@code ModListScreen}, or ModMenu's {@code ModsScreen} on Fabric. Resolved once by reflection; a
 * missing class simply means no Mods button.
 */
public final class ModsScreenOpener {

    private static final List<String> CANDIDATES = List.of(
        "net.neoforged.neoforge.client.gui.ModListScreen",
        "com.terraformersmc.modmenu.gui.ModsScreen");

    private static Optional<Function<Screen, Screen>> cached;

    public static synchronized Optional<Function<Screen, Screen>> factory() {
        if (cached != null) return cached;
        cached = Optional.empty();
        for (final String name : CANDIDATES) {
            try {
                final Class<?> cls = Class.forName(name);
                final Constructor<?> ctor = cls.getConstructor(Screen.class);
                cached = Optional.of(parent -> {
                    try {
                        return (Screen) ctor.newInstance(parent);
                    } catch (final ReflectiveOperationException e) {
                        throw new IllegalStateException("cannot open " + name, e);
                    }
                });
                SlateMenu.LOGGER.info("[Slate Menu] mod list screen: {}", name);
                break;
            } catch (final Throwable ignored) {
                // not on this loader / not installed
            }
        }
        return cached;
    }

    public static boolean available() {
        return factory().isPresent();
    }

    private ModsScreenOpener() {}
}
