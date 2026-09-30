package dev.fallingcloud.slate.earlywindow;

import dev.fallingcloud.slate.earlywindow.scene.Assets;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LibraryFinder;

/**
 * The assets of the game and of the Create mod, for the Overhaul start-up screen: it shows the game's logo, and
 * builds its factory of Create's blocks when Create is installed. Nothing is loaded this early, so the jars are found
 * where they lie (the mod in the mods folder, the game's resources among the libraries) and read as the zip files
 * they are. Read-only, and closed again once the scene has what it needs.
 */
final class ModJars implements Assets, AutoCloseable {

    /** A file only Create's own jar has: mods that add to Create have its name in theirs, but not this. */
    private static final String CREATE = "assets/create/models/block/belt/start.json";

    private final Map<String, ZipFile> jars = new HashMap<>();

    /** Looks for the game's resources and for Create among the mods; what is not found is not there to be read. */
    static ModJars find(final String mcVersion, final String neoFormVersion) {
        final ModJars found = new ModJars();
        final ZipFile game = game(mcVersion, neoFormVersion);
        if (game != null) found.jars.put("minecraft", game);
        final ZipFile create = create();
        if (create != null) found.jars.put("create", create);
        return found;
    }

    private static ZipFile create() {
        final List<Path> named = new ArrayList<>(), others = new ArrayList<>();
        try (DirectoryStream<Path> mods = Files.newDirectoryStream(FMLPaths.MODSDIR.get(), "*.jar")) {
            for (final Path jar : mods) {
                final String name = jar.getFileName().toString().toLowerCase(Locale.ROOT);
                (name.startsWith("create-") ? named : others).add(jar);
            }
        } catch (final IOException | RuntimeException e) {
            return null;
        }
        // The jars that are called Create first: the one that is Create is nearly always among them.
        named.addAll(others);
        for (final Path jar : named) {
            ZipFile zip = null;
            try {
                zip = new ZipFile(jar.toFile());
                if (zip.getEntry(CREATE) != null) return zip;
                zip.close();
            } catch (final IOException | RuntimeException e) {
                if (zip != null) {
                    try {
                        zip.close();
                    } catch (final IOException ignored) {
                        // Not a jar this can read: the next one.
                    }
                }
            }
        }
        return null;
    }

    /** The jar with the game's own textures, as an installed NeoForge keeps it among its libraries. */
    private static ZipFile game(final String mcVersion, final String neoFormVersion) {
        try {
            final Path jar = LibraryFinder.findPathForMaven("net.minecraft", "client", "", "extra", mcVersion + "-" + neoFormVersion);
            return Files.isRegularFile(jar) ? new ZipFile(jar.toFile()) : null;
        } catch (final IOException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    @Override
    public byte[] read(final String namespace, final String path) {
        final String file = "assets/" + namespace + "/" + path;
        final ZipFile jar = jars.get(namespace);
        try {
            if (jar != null) {
                final ZipEntry entry = jar.getEntry(file);
                if (entry == null) return null;
                try (InputStream in = jar.getInputStream(entry)) {
                    return in.readAllBytes();
                }
            }
            // A development run has the game's resources on its class path.
            if (namespace.equals("minecraft")) {
                try (InputStream in = ClassLoader.getSystemResourceAsStream(file)) {
                    return in == null ? null : in.readAllBytes();
                }
            }
            return null;
        } catch (final IOException | RuntimeException e) {
            return null;
        }
    }

    @Override
    public void close() {
        for (final ZipFile jar : jars.values()) {
            try {
                jar.close();
            } catch (final IOException ignored) {
                // Read-only: nothing is lost.
            }
        }
        jars.clear();
    }
}
