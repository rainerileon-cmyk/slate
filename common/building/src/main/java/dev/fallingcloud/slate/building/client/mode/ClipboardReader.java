package dev.fallingcloud.slate.building.client.mode;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.ops.Clipboard;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Turns the synced clipboard ({@code ClipboardSync}, stored in {@link ClientModeState#clipboard()}) into a
 * {@link Clipboard} for the paste / move preview, cached per sync.
 *
 * <p><b>Integration shim (D2), for the lead.</b> The clipboard's NBT format belongs to the ops server (D1), whose
 * decoder was not known while this was written. So, first, any public static method on {@link Clipboard} that
 * returns a {@code Clipboard} from a {@code CompoundTag} (plus optionally one registry argument: a
 * {@code HolderGetter<Block>}, {@code HolderLookup.Provider}, {@code RegistryAccess} or {@code Level}) is used, found
 * by signature so its name does not matter. Only when there is none, or it fails, a structure-template style tag is
 * read: {@code size} (3 ints), {@code palette} (block states as {@code NbtUtils.writeBlockState}) and {@code blocks}
 * ({@code pos} as 3 ints, {@code state} palette index, optional {@code material} palette index or {@code nbt.material}
 * block state). Once D1's decoder name is fixed this class can call it directly.
 */
final class ClipboardReader {

    private static int cachedVersion = Integer.MIN_VALUE;
    private static @Nullable Clipboard cached;
    private static @Nullable Method opsDecoder;
    private static boolean opsDecoderResolved;

    /** The current clipboard, decoded (null when empty or unreadable). */
    static @Nullable Clipboard current(final Level level) {
        final int version = ClientModeState.clipboardVersion();
        if (version != cachedVersion) {
            cachedVersion = version;
            cached = decode(ClientModeState.clipboard(), level);
        }
        return cached;
    }

    static @Nullable Clipboard decode(final @Nullable CompoundTag tag, final Level level) {
        if (tag == null || tag.isEmpty()) return null;
        final Clipboard viaOps = viaOpsDecoder(tag, level);
        if (viaOps != null) return viaOps;
        try {
            return readTemplate(tag, level.registryAccess().lookupOrThrow(Registries.BLOCK));
        } catch (final RuntimeException e) {
            SlateBuilding.LOGGER.warn("[Slate Building] could not read the synced clipboard: {}", e.toString());
            return null;
        }
    }

    private static @Nullable Clipboard viaOpsDecoder(final CompoundTag tag, final Level level) {
        final Method m = opsDecoder();
        if (m == null) return null;
        try {
            final Class<?>[] types = m.getParameterTypes();
            final Object[] args = new Object[types.length];
            args[0] = tag;
            for (int i = 1; i < types.length; i++) args[i] = argument(types[i], level);
            return (Clipboard) m.invoke(null, args);
        } catch (final ReflectiveOperationException | RuntimeException e) {
            SlateBuilding.LOGGER.warn("[Slate Building] Clipboard.{} failed on the synced clipboard: {}", m.getName(), e.toString());
            return null;
        }
    }

    private static @Nullable Method opsDecoder() {
        if (opsDecoderResolved) return opsDecoder;
        opsDecoderResolved = true;
        for (final Method m : Clipboard.class.getMethods()) {
            if (!Modifier.isStatic(m.getModifiers()) || !Clipboard.class.isAssignableFrom(m.getReturnType())) continue;
            final Class<?>[] p = m.getParameterTypes();
            if (p.length == 0 || p.length > 2 || !p[0].isAssignableFrom(CompoundTag.class)) continue;
            if (p.length == 2 && !resolvable(p[1])) continue;
            opsDecoder = m;
            SlateBuilding.LOGGER.debug("[Slate Building] clipboard preview decodes with Clipboard.{}", m.getName());
            break;
        }
        return opsDecoder;
    }

    private static boolean resolvable(final Class<?> type) {
        return type.isAssignableFrom(HolderLookup.RegistryLookup.class) || type == HolderGetter.class
            || type == HolderLookup.Provider.class || type == RegistryAccess.class || Level.class.isAssignableFrom(type);
    }

    private static Object argument(final Class<?> type, final Level level) {
        if (Level.class.isAssignableFrom(type)) return level;
        if (type == HolderLookup.Provider.class || type == RegistryAccess.class) return level.registryAccess();
        return level.registryAccess().lookupOrThrow(Registries.BLOCK);
    }

    private static Clipboard readTemplate(final CompoundTag tag, final HolderGetter<Block> blocks) {
        final int[] size = ints(tag, "size");
        final List<BlockState> palette = new ArrayList<>();
        for (final Tag t : tag.getList("palette", Tag.TAG_COMPOUND)) palette.add(NbtUtils.readBlockState(blocks, (CompoundTag) t));
        final List<Clipboard.Entry> entries = new ArrayList<>();
        for (final Tag t : tag.getList("blocks", Tag.TAG_COMPOUND)) {
            final CompoundTag b = (CompoundTag) t;
            final int[] pos = ints(b, "pos");
            final int index = b.getInt("state");
            if (pos.length < 3 || index < 0 || index >= palette.size()) continue;
            BlockState material = null;
            if (b.contains("material", Tag.TAG_ANY_NUMERIC)) {
                final int mi = b.getInt("material");
                if (mi >= 0 && mi < palette.size()) material = palette.get(mi);
            } else if (b.getCompound("nbt").contains("material", Tag.TAG_COMPOUND)) {
                material = NbtUtils.readBlockState(blocks, b.getCompound("nbt").getCompound("material"));
            }
            entries.add(new Clipboard.Entry(new BlockPos(pos[0], pos[1], pos[2]), palette.get(index), material));
        }
        final Vec3i dims = size.length >= 3 ? new Vec3i(size[0], size[1], size[2]) : new Vec3i(1, 1, 1);
        return new Clipboard(dims, entries);
    }

    /** Three ints stored as an int array or a list of int tags. */
    private static int[] ints(final CompoundTag tag, final String key) {
        if (tag.contains(key, Tag.TAG_INT_ARRAY)) return tag.getIntArray(key);
        final ListTag list = tag.getList(key, Tag.TAG_INT);
        final int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.getInt(i);
        return out;
    }

    private ClipboardReader() {}
}
