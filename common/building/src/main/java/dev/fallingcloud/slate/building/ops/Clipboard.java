package dev.fallingcloud.slate.building.ops;

import dev.fallingcloud.slate.building.block.ShapeBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A copied region: its size and the blocks relative to its minimum corner. The server keeps one per player; the
 * client gets a copy through {@code ClipboardSync} ({@link #toTag} / {@link #fromTag}) for the paste preview.
 *
 * <p>Air is only stored when the copy was made with {@code includeAir} (then pasting clears those positions).
 * Block-entity contents are kept only for creative copies and never leave the server ({@link #toTag} drops them).
 */
public record Clipboard(Vec3i size, List<Entry> entries) {

    private static final int FORMAT = 1;
    private static final String TAG_FORMAT = "v";
    private static final String TAG_SIZE = "size";
    private static final String TAG_PALETTE = "palette";
    private static final String TAG_STATE = "state";
    private static final String TAG_MATERIAL = "material";
    private static final String TAG_INDICES = "indices";
    private static final String TAG_WIDE = "wide";

    /**
     * One copied block.
     *
     * @param offset      position relative to the clipboard's minimum corner
     * @param state       the block state
     * @param material    the material when the block is one of our shape blocks, else null
     * @param blockEntity block-entity data (creative copies of containers, signs, ...), else null; server only
     */
    public record Entry(BlockPos offset, BlockState state, @Nullable BlockState material, @Nullable CompoundTag blockEntity) {

        public Entry {
            offset = offset.immutable();
        }

        public Entry(final BlockPos offset, final BlockState state, final @Nullable BlockState material) {
            this(offset, state, material, null);
        }
    }

    public static final Clipboard EMPTY = new Clipboard(Vec3i.ZERO, List.of());

    public Clipboard {
        entries = List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Stored blocks that are not air. */
    public int blockCount() {
        int n = 0;
        for (final Entry e : entries) if (!e.state().isAir()) n++;
        return n;
    }

    // ---- reading the world ----

    /**
     * Copies the box {@code min..max} (inclusive) of {@code level}. Air is kept only with {@code includeAir};
     * block-entity data only with {@code blockEntityData} (creative), never data only operators may set (command
     * blocks, spawners ...). Our shape blocks keep their material. Callers make sure the box is loaded.
     */
    public static Clipboard read(final Level level, final BlockPos min, final BlockPos max, final boolean includeAir,
                                 final boolean blockEntityData) {
        return read(level, min, max, includeAir, blockEntityData, false);
    }

    /**
     * {@link #read(Level, BlockPos, BlockPos, boolean, boolean)}; {@code opData}: also keep block-entity data only
     * operators may set ({@code BlockEntity.onlyOpCanSetNbt}), for copiers who may use game-master blocks.
     */
    public static Clipboard read(final Level level, final BlockPos min, final BlockPos max, final boolean includeAir,
                                 final boolean blockEntityData, final boolean opData) {
        final List<Entry> out = new ArrayList<>();
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    p.set(x, y, z);
                    final BlockState state = level.getBlockState(p);
                    if (state.isAir() && !includeAir) continue;
                    final BlockPos offset = new BlockPos(x - min.getX(), y - min.getY(), z - min.getZ());
                    final BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(p) : null;
                    BlockState material = null;
                    CompoundTag data = null;
                    if (be instanceof ShapeBlockEntity shape) material = shape.material();
                    else if (be != null && blockEntityData && (opData || !be.onlyOpCanSetNbt())) data = be.saveWithoutMetadata(level.registryAccess());
                    out.add(new Entry(offset, state, material, data));
                }
            }
        }
        return new Clipboard(new Vec3i(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1), out);
    }

    // ---- transforms ----

    /** The clockwise rotation a {@code rotation} parameter value ("0", "90", "180", "270") names. */
    public static Rotation rotation(final String degrees) {
        return switch (degrees) {
            case "90" -> Rotation.CLOCKWISE_90;
            case "180" -> Rotation.CLOCKWISE_180;
            case "270" -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /**
     * The mirror a {@code mirror} parameter value names: "X" flips along the X axis (east ↔ west,
     * {@link Mirror#FRONT_BACK}), "Z" along the Z axis (north ↔ south, {@link Mirror#LEFT_RIGHT}).
     */
    public static Mirror mirror(final String axis) {
        return switch (axis) {
            case "X" -> Mirror.FRONT_BACK;
            case "Z" -> Mirror.LEFT_RIGHT;
            default -> Mirror.NONE;
        };
    }

    /** Size after rotating: quarter turns swap X and Z. */
    public Vec3i transformedSize(final Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90
            ? new Vec3i(size.getZ(), size.getY(), size.getX()) : size;
    }

    /**
     * This clipboard mirrored, then rotated clockwise (seen from above) inside its own box; offsets stay relative to
     * the new minimum corner and every state is mirrored/rotated with it (stairs keep facing the right way).
     */
    public Clipboard transformed(final Rotation rotation, final Mirror mirror) {
        if (rotation == Rotation.NONE && mirror == Mirror.NONE) return this;
        final List<Entry> out = new ArrayList<>(entries.size());
        for (final Entry e : entries) {
            final BlockPos o = transformOffset(e.offset(), rotation, mirror);
            final BlockState state = e.state().mirror(mirror).rotate(rotation);
            out.add(new Entry(o, state, e.material(), e.blockEntity()));
        }
        return new Clipboard(transformedSize(rotation), out);
    }

    /** Where {@code offset} lands after {@link #transformed}. */
    public BlockPos transformOffset(final BlockPos offset, final Rotation rotation, final Mirror mirror) {
        int x = offset.getX();
        int z = offset.getZ();
        final int sx = size.getX();
        final int sz = size.getZ();
        if (mirror == Mirror.FRONT_BACK) x = sx - 1 - x;
        else if (mirror == Mirror.LEFT_RIGHT) z = sz - 1 - z;
        final int nx;
        final int nz;
        switch (rotation) {
            case CLOCKWISE_90 -> { nx = sz - 1 - z; nz = x; }
            case CLOCKWISE_180 -> { nx = sx - 1 - x; nz = sz - 1 - z; }
            case COUNTERCLOCKWISE_90 -> { nx = z; nz = sx - 1 - x; }
            default -> { nx = x; nz = z; }
        }
        return new BlockPos(nx, offset.getY(), nz);
    }

    // ---- NBT (client sync) ----

    /**
     * Compact form for {@code ClipboardSync}: a palette of (state, material) pairs plus one index per position of
     * the box (bytes while the palette fits, else 16-bit pairs in an int array). No block-entity data. An empty
     * clipboard is an empty tag.
     */
    public CompoundTag toTag() {
        final CompoundTag tag = new CompoundTag();
        if (entries.isEmpty()) return tag;
        tag.putInt(TAG_FORMAT, FORMAT);
        tag.putIntArray(TAG_SIZE, new int[] {size.getX(), size.getY(), size.getZ()});
        final Map<Key, Integer> ids = new HashMap<>();
        final ListTag palette = new ListTag();
        final int volume = size.getX() * size.getY() * size.getZ();
        final int[] index = new int[volume];
        for (final Entry e : entries) {
            final int slot = slot(e.offset());
            if (slot < 0 || slot >= volume) continue;
            final Key key = new Key(e.state(), e.material());
            Integer id = ids.get(key);
            if (id == null) {
                if (ids.size() >= 0xFFFE) continue;
                id = ids.size();
                ids.put(key, id);
                final CompoundTag p = new CompoundTag();
                p.put(TAG_STATE, NbtUtils.writeBlockState(e.state()));
                if (e.material() != null) p.put(TAG_MATERIAL, NbtUtils.writeBlockState(e.material()));
                palette.add(p);
            }
            index[slot] = id + 1;
        }
        tag.put(TAG_PALETTE, palette);
        if (ids.size() < 255) {
            final byte[] bytes = new byte[volume];
            for (int i = 0; i < volume; i++) bytes[i] = (byte) index[i];
            tag.putByteArray(TAG_INDICES, bytes);
        } else {
            final int[] packed = new int[(volume + 1) / 2];
            for (int i = 0; i < volume; i++) packed[i >> 1] |= (index[i] & 0xFFFF) << ((i & 1) * 16);
            tag.putIntArray(TAG_INDICES, packed);
            tag.putBoolean(TAG_WIDE, true);
        }
        return tag;
    }

    /** Reads a {@link #toTag} tag; {@link #EMPTY} for an empty or unreadable tag (never throws). */
    public static Clipboard fromTag(final @Nullable CompoundTag tag) {
        if (tag == null || !tag.contains(TAG_SIZE, Tag.TAG_INT_ARRAY)) return EMPTY;
        try {
            final int[] s = tag.getIntArray(TAG_SIZE);
            if (s.length != 3 || s[0] <= 0 || s[1] <= 0 || s[2] <= 0) return EMPTY;
            final long volumeL = (long) s[0] * s[1] * s[2];
            if (volumeL > 16_777_216L) return EMPTY;
            final int volume = (int) volumeL;
            final HolderGetter<Block> blocks = BuiltInRegistries.BLOCK.asLookup();
            final ListTag paletteTag = tag.getList(TAG_PALETTE, Tag.TAG_COMPOUND);
            final BlockState[] states = new BlockState[paletteTag.size()];
            final BlockState[] materials = new BlockState[paletteTag.size()];
            for (int i = 0; i < states.length; i++) {
                final CompoundTag p = paletteTag.getCompound(i);
                states[i] = NbtUtils.readBlockState(blocks, p.getCompound(TAG_STATE));
                materials[i] = p.contains(TAG_MATERIAL, Tag.TAG_COMPOUND) ? NbtUtils.readBlockState(blocks, p.getCompound(TAG_MATERIAL)) : null;
            }
            final boolean wide = tag.getBoolean(TAG_WIDE);
            final byte[] bytes = wide ? null : tag.getByteArray(TAG_INDICES);
            final int[] packed = wide ? tag.getIntArray(TAG_INDICES) : null;
            final List<Entry> out = new ArrayList<>();
            for (int i = 0; i < volume; i++) {
                final int id;
                if (wide) id = i >> 1 < packed.length ? (packed[i >> 1] >>> ((i & 1) * 16)) & 0xFFFF : 0;
                else id = i < bytes.length ? bytes[i] & 0xFF : 0;
                if (id == 0 || id > states.length) continue;
                final int x = i % s[0];
                final int z = (i / s[0]) % s[2];
                final int y = i / (s[0] * s[2]);
                final BlockState material = materials[id - 1];
                out.add(new Entry(new BlockPos(x, y, z), states[id - 1], material == null || material.isAir() ? null : material));
            }
            out.sort(Comparator.comparingInt((Entry e) -> e.offset().getY()));
            return new Clipboard(new Vec3i(s[0], s[1], s[2]), out);
        } catch (final RuntimeException e) {
            return EMPTY;
        }
    }

    /** Linear index of an offset: x fastest, then z, then y (so reading back yields bottom-up order). */
    private int slot(final BlockPos o) {
        if (o.getX() < 0 || o.getY() < 0 || o.getZ() < 0 || o.getX() >= size.getX() || o.getY() >= size.getY() || o.getZ() >= size.getZ()) return -1;
        return (o.getY() * size.getZ() + o.getZ()) * size.getX() + o.getX();
    }

    private record Key(BlockState state, @Nullable BlockState material) {
        @Override public boolean equals(final Object o) {
            return o instanceof Key k && k.state == state && Objects.equals(k.material, material);
        }

        @Override public int hashCode() {
            return System.identityHashCode(state) * 31 + Objects.hashCode(material);
        }
    }
}
