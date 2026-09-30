package dev.fallingcloud.slate.menu.client.overhaul.play.map;

import com.mojang.blaze3d.platform.NativeImage;
import dev.fallingcloud.slate.core.gfx.Textures;
import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.menu.client.overhaul.play.PlayIo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * The map of one saved world, a region at a time: tiles of 512 × 512 blocks, painted from the world's region files
 * when first looked at ({@link RegionMap}) or, for a server, read from the tiles a map mod has saved on this
 * computer ({@link #journeyMap}). Tiles load in the background, newest request first; a view only ever has a
 * handful on screen, so the dozen most recently used are kept and the rest given back.
 *
 * <p>One instance per world and screen. {@link #close} frees every texture.</p>
 */
public final class MapTiles implements AutoCloseable {

    /** How a tile is come by. */
    private interface Source {
        /** The picture of region {@code (x, z)}, or null when there is none. Worker thread. */
        @Nullable NativeImage load(int x, int z);
    }

    private static final int KEEP = 14;

    private enum State { LOADING, READY, EMPTY }

    private static final class Tile {
        State state = State.LOADING;
        @Nullable Textures.Loaded texture;
        long usedAt;
    }

    private final Source source;
    private final Map<Long, Tile> tiles = new HashMap<>();
    private boolean closed;
    private long clock;
    private int loaded;

    private MapTiles(final Source source) {
        this.source = source;
    }

    /** The overworld of a saved world, from its region files. */
    public static MapTiles ofWorld(final Path worldFolder) {
        final Path regions = worldFolder.resolve("region");
        return new MapTiles((x, z) -> {
            final RegionMap.Picture p = RegionMap.render(regions.resolve("r." + x + "." + z + ".mca"));
            if (p == null) return null;
            final NativeImage img = new NativeImage(RegionMap.SIZE, RegionMap.SIZE, false);
            final int[] px = p.abgr();
            for (int y = 0; y < RegionMap.SIZE; y++) {
                for (int i = 0; i < RegionMap.SIZE; i++) img.setPixelRGBA(i, y, px[y * RegionMap.SIZE + i]);
            }
            return img;
        });
    }

    /**
     * A folder of JourneyMap's day tiles ({@code <x>,<z>.png}, 512 × 512 each): what that mod has mapped of a world or
     * a server. Null when the folder does not exist.
     */
    @Nullable
    public static MapTiles journeyMap(final Path dayFolder) {
        if (!Files.isDirectory(dayFolder)) return null;
        return new MapTiles((x, z) -> {
            final Path file = dayFolder.resolve(x + "," + z + ".png");
            if (!Files.isRegularFile(file)) return null;
            try (var in = Files.newInputStream(file)) {
                return NativeImage.read(in);
            } catch (final Exception e) {
                return null;
            }
        });
    }

    /** The texture of region {@code (x, z)} if it is ready; asks for it otherwise. Render thread. */
    @Nullable
    public Textures.Loaded tile(final int x, final int z) {
        if (closed) return null;
        final long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        Tile t = tiles.get(key);
        if (t == null) {
            t = new Tile();
            tiles.put(key, t);
            request(key, x, z, t);
            trim();
        }
        t.usedAt = ++clock;
        return t.state == State.READY ? t.texture : null;
    }

    /** Whether region {@code (x, z)} is still being read. */
    public boolean loading(final int x, final int z) {
        final Tile t = tiles.get(((long) x << 32) ^ (z & 0xFFFFFFFFL));
        return t != null && t.state == State.LOADING;
    }

    /** How many tiles turned out to hold something: 0 after the first ones loaded means there is no map to show. */
    public int loadedCount() { return loaded; }

    public boolean anyLoading() {
        for (final Tile t : tiles.values()) if (t.state == State.LOADING) return true;
        return false;
    }

    private void request(final long key, final int x, final int z, final Tile tile) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return source.load(x, z);
            } catch (final Exception e) {
                SlateMenu.LOGGER.debug("[Slate Menu] map tile {},{} failed: {}", x, z, e.toString());
                return null;
            }
        }, PlayIo.POOL).thenAcceptAsync(img -> {
            if (closed || tiles.get(key) != tile) {
                if (img != null) img.close();
                return;
            }
            if (img == null) {
                tile.state = State.EMPTY;
                return;
            }
            tile.texture = Textures.register(img, "map");
            tile.state = State.READY;
            loaded++;
        }, Minecraft.getInstance());
    }

    /** Gives back the tiles looked at longest ago. Empty ones cost nothing and stay, so they are not read twice. */
    private void trim() {
        int ready = 0;
        for (final Tile t : tiles.values()) if (t.state == State.READY) ready++;
        if (ready <= KEEP) return;
        final List<Map.Entry<Long, Tile>> byAge = new ArrayList<>(tiles.entrySet());
        byAge.sort((a, b) -> Long.compare(a.getValue().usedAt, b.getValue().usedAt));
        for (final Map.Entry<Long, Tile> e : byAge) {
            if (ready <= KEEP) break;
            final Tile t = e.getValue();
            if (t.state != State.READY) continue;
            if (t.texture != null) Textures.release(t.texture);
            tiles.remove(e.getKey());
            ready--;
        }
    }

    @Override
    public void close() {
        closed = true;
        for (final Tile t : tiles.values()) if (t.texture != null) Textures.release(t.texture);
        tiles.clear();
    }
}
