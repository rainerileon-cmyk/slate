package dev.fallingcloud.slate.building.variant;

import dev.fallingcloud.slate.building.SlateBuilding;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Whether a block may change shape IN PLACE without skipping its loot table (design §1/§9): reshaping turns a block
 * into one that drops its material, so natural stone (drops cobblestone), glass and ice (nothing), ores or grass would
 * come back as themselves, a free silk touch. The hammer's in-world reshape and the area Reshape mode refuse those in
 * survival.
 *
 * <p>The server checks the real block ({@link VariantDrops#dropsOwnWorth}). Clients have no loot tables, so the server
 * also works out once (on start and after {@code /reload}) which variant blocks fail on their default state, and ships
 * that list inside {@code ServerSettingsSync}; the build preview reads it to show those positions as invalid.
 */
public final class LootGuard {

    private static final String TAG = "lootUnsafe";
    /** Rolls per block: loot with a chance (gravel's flint) must pass every time to count as safe. */
    private static final int ROLLS = 3;

    /** Server side: variant blocks whose no-tool drops are not their own worth (default state). */
    private static volatile Set<Block> server = Set.of();
    /** Client side: the copy the server sent; null until one arrived (the host of an integrated server reads {@link #server}). */
    private static volatile @Nullable Set<Block> remote;

    private LootGuard() {}

    /** Recomputes the server list (server start, datapack reload). Server thread. */
    public static void rebuild(final MinecraftServer mcServer) {
        try {
            compute(mcServer);
        } catch (final RuntimeException e) {
            SlateBuilding.LOGGER.error("[Slate Building] could not work out the loot guard list", e);
        }
    }

    private static void compute(final MinecraftServer mcServer) {
        final long start = System.nanoTime();
        final ServerLevel level = mcServer.overworld();
        final VariantRegistry registry = VariantRegistry.get();
        final Set<Block> unsafe = Collections.newSetFromMap(new IdentityHashMap<>());
        int checked = 0;
        for (final Block block : BuiltInRegistries.BLOCK) {
            if (block instanceof ShapeBlock) continue;
            final BlockState state = block.defaultBlockState();
            final Variant v = registry.identify(state, null).orElse(null);
            if (v == null) continue;
            checked++;
            final int units = registry.units(state, null);
            for (int i = 0; i < ROLLS; i++) {
                if (!VariantDrops.dropsOwnWorth(level, BlockPos.ZERO, state, null, v.material(), units)) {
                    unsafe.add(block);
                    break;
                }
            }
        }
        server = Collections.unmodifiableSet(unsafe);
        SlateBuilding.LOGGER.info("[Slate Building] loot guard: {} of {} variant blocks do not drop their own worth ({} ms)",
            unsafe.size(), checked, (System.nanoTime() - start) / 1_000_000);
    }

    /** The server is gone: nothing to guard with (a new one recomputes). */
    public static void forget() {
        server = Set.of();
    }

    /**
     * Whether {@code state} at {@code pos} can change shape in place and keep its loot honest. Our own shapes always
     * can (their drops come from the stored material). On a server level the real block is checked; on a client level
     * the server's list decides (a block it does not list counts as fine: the server re-checks anyway).
     */
    public static boolean keepsLoot(final Level level, final BlockPos pos, final BlockState state, final @Nullable BlockEntity be) {
        if (state.getBlock() instanceof ShapeBlock) return true;
        if (level instanceof ServerLevel serverLevel) {
            final VariantRegistry registry = VariantRegistry.get();
            final Variant v = registry.identify(state, be).orElse(null);
            return v != null && VariantDrops.dropsOwnWorth(serverLevel, pos, state, be, v.material(), registry.units(state, be));
        }
        final Set<Block> synced = remote;
        return !(synced != null ? synced : server).contains(state.getBlock());
    }

    // ------------------------------------------------------------------ sync (inside ServerSettingsSync)

    /** Adds the server list to a {@code ServerSettingsSync} tag. */
    public static CompoundTag write(final CompoundTag tag) {
        final ListTag ids = new ListTag();
        for (final Block b : server) ids.add(StringTag.valueOf(BuiltInRegistries.BLOCK.getKey(b).toString()));
        tag.put(TAG, ids);
        return tag;
    }

    /** Client: reads the list from a {@code ServerSettingsSync} tag (ids this client does not know are skipped). */
    public static void accept(final CompoundTag tag) {
        if (!tag.contains(TAG, Tag.TAG_LIST)) {
            remote = null;
            return;
        }
        final ListTag ids = tag.getList(TAG, Tag.TAG_STRING);
        final Set<Block> out = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < ids.size(); i++) {
            final ResourceLocation id = ResourceLocation.tryParse(ids.getString(i));
            if (id != null) BuiltInRegistries.BLOCK.getOptional(id).ifPresent(out::add);
        }
        remote = Collections.unmodifiableSet(out);
    }

    /** Client: left the server. */
    public static void clearRemote() {
        remote = null;
    }
}
