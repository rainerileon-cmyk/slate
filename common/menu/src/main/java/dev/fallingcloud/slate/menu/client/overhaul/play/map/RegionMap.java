package dev.fallingcloud.slate.menu.client.overhaul.play.map;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

/**
 * Paints one region file of a saved world as a map, the way the game's own map item would: the colour of the top
 * block of every column, lighter or darker by the slope towards the north, water by its depth. Read straight from
 * the {@code .mca} file, which is only ever opened for reading, so a world that is not loaded (or that another game
 * has open) is never touched. Runs on a worker thread; nothing here talks to the renderer.
 *
 * <p>A region is 32 × 32 chunks, so the picture is 512 × 512: one pixel a block. Chunks that were never generated
 * stay transparent.</p>
 */
public final class RegionMap {

    public static final int SIZE = 512;
    private static final int SECTOR = 4096;

    /** What {@link #render} returns: the pixels (ABGR, as {@code NativeImage} wants them), or null for an empty region. */
    public record Picture(int[] abgr, int chunks) {}

    /** Renders {@code r.<x>.<z>.mca}; null when the file is missing, empty or unreadable. */
    @Nullable
    public static Picture render(final Path regionFile) {
        if (!Files.isRegularFile(regionFile)) return null;
        final int[] pixels = new int[SIZE * SIZE];
        final short[] heights = new short[SIZE * SIZE];
        final byte[] colours = new byte[SIZE * SIZE];        // map colour id, 0 = nothing
        final short[] depths = new short[SIZE * SIZE];       // water depth
        java.util.Arrays.fill(heights, Short.MIN_VALUE);
        int chunks = 0;
        try (FileChannel ch = FileChannel.open(regionFile, StandardOpenOption.READ)) {
            final long size = ch.size();
            if (size < SECTOR * 2L) return null;
            final ByteBuffer header = ByteBuffer.allocate(SECTOR);
            readFully(ch, header, 0);
            header.flip();
            for (int i = 0; i < 1024; i++) {
                final int entry = header.getInt(i * 4);
                final int offset = entry >>> 8, sectors = entry & 0xFF;
                if (offset < 2 || sectors == 0) continue;
                final long at = (long) offset * SECTOR;
                if (at + 5 > size) continue;
                final ByteBuffer head = ByteBuffer.allocate(5);
                readFully(ch, head, at);
                head.flip();
                final int length = head.getInt();
                final int compression = head.get() & 0xFF;
                // An external chunk (bit 7) lives in its own .mcc file: rare, and not worth the map's time.
                if (length <= 1 || length > sectors * SECTOR || (compression & 0x80) != 0 || at + 4 + length > size) continue;
                final ByteBuffer body = ByteBuffer.allocate(length - 1);
                readFully(ch, body, at + 5);
                try {
                    final CompoundTag chunk = decode(body.array(), compression);
                    if (chunk != null && paint(chunk, i & 31, i >> 5, heights, colours, depths)) chunks++;
                } catch (final Exception ignored) {
                    // One broken chunk leaves a hole, not a broken map.
                }
            }
        } catch (final IOException e) {
            return null;
        }
        if (chunks == 0) return null;
        shade(pixels, heights, colours, depths);
        return new Picture(pixels, chunks);
    }

    private static void readFully(final FileChannel ch, final ByteBuffer into, final long at) throws IOException {
        long pos = at;
        while (into.hasRemaining()) {
            final int n = ch.read(into, pos);
            if (n < 0) break;
            pos += n;
        }
    }

    @Nullable
    private static CompoundTag decode(final byte[] data, final int compression) throws IOException {
        final InputStream raw = new ByteArrayInputStream(data);
        final InputStream in = switch (compression) {
            case 1 -> new GZIPInputStream(raw);
            case 2 -> new InflaterInputStream(raw);
            case 3 -> raw;
            default -> null;                     // LZ4 and whatever comes next: left blank
        };
        if (in == null) return null;
        try (DataInputStream din = new DataInputStream(new java.io.BufferedInputStream(in, 1 << 14))) {
            return NbtIo.read(din, NbtAccounter.unlimitedHeap());
        }
    }

    /** Writes one chunk's columns into the region's arrays. False for a chunk that is not fully generated. */
    private static boolean paint(final CompoundTag chunk, final int cx, final int cz, final short[] heights, final byte[] colours, final short[] depths) {
        final String status = chunk.getString("Status");
        if (!status.endsWith("full")) return false;
        final CompoundTag maps = chunk.getCompound("Heightmaps");
        final long[] surface = maps.getLongArray("WORLD_SURFACE");
        if (surface.length == 0) return false;
        final long[] floor = maps.getLongArray("OCEAN_FLOOR");
        final ListTag sections = chunk.getList("sections", Tag.TAG_COMPOUND);
        int minSection = chunk.contains("yPos", Tag.TAG_ANY_NUMERIC) ? chunk.getInt("yPos") : -4;
        final Map<Integer, Section> byY = new HashMap<>();
        for (int i = 0; i < sections.size(); i++) {
            final CompoundTag s = sections.getCompound(i);
            final int y = s.getByte("Y");
            minSection = Math.min(minSection, y);
            if (s.contains("block_states", Tag.TAG_COMPOUND)) byY.put(y, new Section(s.getCompound("block_states")));
        }
        final int minY = minSection * 16;
        // Heights are packed as small as the world's height allows: 37 longs hold 256 of them, 7 to a long at 9 bits.
        final int bits = Math.max(1, surface.length == 0 ? 9 : 64 / (int) Math.ceil(256.0 / surface.length));
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                final int h = unpack(surface, bits, z * 16 + x);
                if (h <= 0) continue;
                int y = minY + h - 1;
                BlockState state = block(byY, x, y, z);
                // Walk down through what a map would not show (air that a stale heightmap still counts, barriers, glass).
                for (int guard = 0; guard < 24 && y > minY && state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) == MapColor.NONE; guard++) {
                    y--;
                    state = block(byY, x, y, z);
                }
                final MapColor colour = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                if (colour == MapColor.NONE) continue;
                final int at = (cz * 16 + z) * SIZE + cx * 16 + x;
                heights[at] = (short) y;
                colours[at] = (byte) colour.id;
                if (!state.getFluidState().isEmpty() && floor.length == surface.length) {
                    depths[at] = (short) Math.max(1, h - unpack(floor, bits, z * 16 + x));
                }
            }
        }
        return true;
    }

    private static int unpack(final long[] data, final int bits, final int index) {
        final int perLong = 64 / bits;
        final int li = index / perLong;
        if (li >= data.length) return 0;
        return (int) ((data[li] >>> ((index % perLong) * bits)) & ((1L << bits) - 1));
    }

    private static BlockState block(final Map<Integer, Section> sections, final int x, final int y, final int z) {
        final Section s = sections.get(y >> 4);
        return s == null ? Blocks.AIR.defaultBlockState() : s.get(x, y & 15, z);
    }

    /** One 16-cube of a chunk: its palette, resolved to block states once, and the packed indices into it. */
    private static final class Section {
        private final BlockState[] palette;
        private final long[] data;
        private final int bits;

        Section(final CompoundTag states) {
            final ListTag list = states.getList("palette", Tag.TAG_COMPOUND);
            palette = new BlockState[Math.max(1, list.size())];
            for (int i = 0; i < palette.length; i++) {
                BlockState s = Blocks.AIR.defaultBlockState();
                if (i < list.size()) {
                    try {
                        s = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), list.getCompound(i));
                    } catch (final Exception ignored) {
                        // A block of a mod that is not installed: stone stands in, so the land keeps its shape.
                        s = Blocks.STONE.defaultBlockState();
                    }
                }
                palette[i] = s;
            }
            data = states.getLongArray("data");
            bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.length - 1));
        }

        BlockState get(final int x, final int y, final int z) {
            if (palette.length == 1 || data.length == 0) return palette[0];
            final int index = (y << 8) | (z << 4) | x;
            final int id = unpack(data, bits, index);
            return id < palette.length ? palette[id] : palette[0];
        }
    }

    /** Turns heights and colours into pixels, with the game's map shading. */
    private static void shade(final int[] pixels, final short[] heights, final byte[] colours, final short[] depths) {
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                final int at = z * SIZE + x;
                final int id = colours[at] & 0xFF;
                if (id == 0) continue;
                MapColor.Brightness b = MapColor.Brightness.NORMAL;
                if (depths[at] > 0) {
                    // Water: light in the shallows, dark over the deep, dithered where the two meet.
                    final double d = depths[at] * 0.1 + ((x + z & 1) * 0.2);
                    b = d < 0.5 ? MapColor.Brightness.HIGH : d > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                } else if (z > 0 && heights[at - SIZE] != Short.MIN_VALUE) {
                    final int slope = heights[at] - heights[at - SIZE];
                    b = slope > 0 ? MapColor.Brightness.HIGH : slope < 0 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                }
                pixels[at] = MapColor.byId(id).calculateRGBColor(b);
            }
        }
    }

    private RegionMap() {}
}
