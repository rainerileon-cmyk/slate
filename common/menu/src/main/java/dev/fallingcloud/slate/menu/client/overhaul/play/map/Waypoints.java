package dev.fallingcloud.slate.menu.client.overhaul.play.map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

/**
 * The waypoints a map mod has saved for a world or a server, read from that mod's own files: Xaero's Minimap
 * ({@code xaero/minimap/<world>/dim%0/*.txt}, and the older {@code XaeroWaypoints} folder) and JourneyMap
 * ({@code journeymap/data/<sp|mp>/<world>/waypoints/*.json}). Only the overworld's. Neither mod's code is touched,
 * so this works while no world is loaded; files that are missing or in a form this does not know give no waypoints.
 */
public final class Waypoints {

    public record Waypoint(String name, String initials, int x, int y, int z, int argb) {}

    /** The sixteen chat colours Xaero's numbers its waypoints by. */
    private static final int[] XAERO_COLOURS = {
        0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA, 0xFFAA0000, 0xFFAA00AA, 0xFFFFAA00, 0xFFAAAAAA,
        0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF, 0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF};

    public static boolean xaeroInstalled() {
        return SlatePlatform.get().isModLoaded("xaerominimap") || SlatePlatform.get().isModLoaded("xaeroworldmap")
            || SlatePlatform.get().isModLoaded("xaerobetterpvp");
    }

    public static boolean journeyMapInstalled() {
        return SlatePlatform.get().isModLoaded("journeymap");
    }

    /** Whether a map mod is installed: the Play screen's map then shows that mod's waypoints (and its tiles for servers). */
    public static boolean mapModInstalled() {
        return xaeroInstalled() || journeyMapInstalled();
    }

    /** A saved world's waypoints. {@code folder} is the world's folder name, {@code name} its display name. Worker thread. */
    public static List<Waypoint> ofWorld(final String folder, final String name) {
        final Path game = SlatePlatform.get().gameDir();
        final List<Waypoint> out = new ArrayList<>();
        xaero(game.resolve("xaero").resolve("minimap").resolve(folder), out);
        if (out.isEmpty()) xaero(game.resolve("XaeroWaypoints").resolve(folder), out);
        journeyMap(game.resolve("journeymap").resolve("data").resolve("sp").resolve(name).resolve("waypoints"), out);
        return out;
    }

    /** A server's waypoints, by its address as the server list has it. Worker thread. */
    public static List<Waypoint> ofServer(final String address, final String name) {
        final Path game = SlatePlatform.get().gameDir();
        final List<Waypoint> out = new ArrayList<>();
        final String host = address.replace(':', '_');
        for (final String root : new String[] {"xaero/minimap", "XaeroWaypoints"}) {
            final Path base = game.resolve(root);
            if (!Files.isDirectory(base)) continue;
            try (Stream<Path> s = Files.list(base)) {
                for (final Path p : (Iterable<Path>) s::iterator) {
                    final String n = p.getFileName().toString();
                    if (n.startsWith("Multiplayer_") && n.substring(12).equalsIgnoreCase(host)) xaero(p, out);
                }
            } catch (final Exception ignored) {}
            if (!out.isEmpty()) break;
        }
        journeyMap(game.resolve("journeymap").resolve("data").resolve("mp").resolve(name).resolve("waypoints"), out);
        return out;
    }

    /** JourneyMap's day tiles of the overworld for a saved world, or null. */
    @Nullable
    public static Path journeyMapTilesOfWorld(final String name) {
        return journeyMapTiles(SlatePlatform.get().gameDir().resolve("journeymap").resolve("data").resolve("sp").resolve(name));
    }

    /** JourneyMap's day tiles of the overworld for a server, or null. */
    @Nullable
    public static Path journeyMapTilesOfServer(final String name) {
        return journeyMapTiles(SlatePlatform.get().gameDir().resolve("journeymap").resolve("data").resolve("mp").resolve(name));
    }

    @Nullable
    private static Path journeyMapTiles(final Path world) {
        for (final String dim : new String[] {"overworld", "DIM0", "minecraft~overworld"}) {
            final Path day = world.resolve(dim).resolve("day");
            if (Files.isDirectory(day)) return day;
        }
        return null;
    }

    // ------------------------------------------------------------------ Xaero's

    private static void xaero(final Path world, final List<Waypoint> out) {
        if (!Files.isDirectory(world)) return;
        for (final String dim : new String[] {"dim%0", "dim%minecraft$overworld", "null"}) {
            final Path dir = world.resolve(dim);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> s = Files.list(dir)) {
                for (final Path file : (Iterable<Path>) s::iterator) {
                    if (file.getFileName().toString().endsWith(".txt")) xaeroFile(file, out);
                }
            } catch (final Exception ignored) {}
            if (!out.isEmpty()) return;
        }
    }

    /** {@code waypoint:name:initials:x:y:z:colour:disabled:type:set:...}; a colon in a name is written as {@code §§}. */
    private static void xaeroFile(final Path file, final List<Waypoint> out) {
        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.startsWith("waypoint:")) continue;
                final String[] p = line.split(":");
                if (p.length < 8) continue;
                try {
                    if (Boolean.parseBoolean(p[7])) continue;                       // switched off by the player
                    final int y = p[4].equals("~") ? 64 : Integer.parseInt(p[4]);
                    final int colour = Integer.parseInt(p[6]);
                    out.add(new Waypoint(p[1].replace("§§", ":"), p[2].replace("§§", ":"), Integer.parseInt(p[3]), y, Integer.parseInt(p[5]),
                        XAERO_COLOURS[Math.floorMod(colour, XAERO_COLOURS.length)]));
                } catch (final NumberFormatException ignored) {}
            }
        } catch (final Exception ignored) {}
    }

    // ------------------------------------------------------------------ JourneyMap

    private static void journeyMap(final Path dir, final List<Waypoint> out) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> s = Files.list(dir)) {
            for (final Path file : (Iterable<Path>) s::iterator) {
                if (!file.getFileName().toString().endsWith(".json")) continue;
                try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    final JsonElement el = JsonParser.parseReader(r);
                    if (!el.isJsonObject()) continue;
                    final JsonObject o = el.getAsJsonObject();
                    if (o.has("enable") && !o.get("enable").getAsBoolean()) continue;
                    if (o.has("enabled") && !o.get("enabled").getAsBoolean()) continue;
                    final String name = o.has("name") ? o.get("name").getAsString() : "";
                    // Older files keep x, y, z at the top; newer ones in a "pos" object. The colour likewise.
                    final JsonObject pos = o.has("pos") && o.get("pos").isJsonObject() ? o.getAsJsonObject("pos") : o;
                    if (!pos.has("x") || !pos.has("z")) continue;
                    final int x = pos.get("x").getAsInt(), z = pos.get("z").getAsInt();
                    final int y = pos.has("y") ? pos.get("y").getAsInt() : 64;
                    int argb = 0xFFFFFFFF;
                    if (o.has("r") && o.has("g") && o.has("b")) {
                        argb = 0xFF000000 | (o.get("r").getAsInt() & 255) << 16 | (o.get("g").getAsInt() & 255) << 8 | (o.get("b").getAsInt() & 255);
                    } else if (o.has("color")) {
                        argb = 0xFF000000 | (o.get("color").getAsInt() & 0xFFFFFF);
                    }
                    out.add(new Waypoint(name, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT), x, y, z, argb));
                } catch (final Exception ignored) {}
            }
        } catch (final Exception ignored) {}
    }

    private Waypoints() {}
}
