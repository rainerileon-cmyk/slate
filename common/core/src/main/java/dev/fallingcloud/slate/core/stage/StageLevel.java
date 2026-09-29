package dev.fallingcloud.slate.core.stage;

import dev.fallingcloud.slate.core.Slate;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;
import org.jetbrains.annotations.Nullable;

/**
 * A tiny client-side {@code Level} that lives inside a {@link Stage}: a hash map of blocks, their block entities and a
 * handful of entities, no chunks, no ticking scheduler, no server. It exists because vanilla's renderers refuse to
 * work without one: entities cannot even be constructed without a level, block entities need one for their tickers
 * and for chest/sign lookups, and the block mesher asks its {@code BlockAndTintGetter} for neighbours (culling,
 * ambient occlusion), shade, light and biome tints. All of those answer here from the stage's {@link StageLighting}
 * and a fixed plains biome, so grass, leaves and water get their real colours.
 *
 * <p>Nodes that need level space call {@link #allocateRegion()}: regions are 4096 blocks apart on X so their
 * neighbour lookups never see each other. Everything is on the render thread; nothing here is thread-safe.</p>
 *
 * <p>The four {@code *DayTime*} methods at the end are NeoForge additions to {@code Level}; they carry no
 * {@code @Override} so the same source compiles on Fabric.</p>
 */
public final class StageLevel extends Level {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState VOID = Blocks.VOID_AIR.defaultBlockState();

    private final Long2ObjectOpenHashMap<BlockState> blocks = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<BlockEntity> blockEntities = new Long2ObjectOpenHashMap<>();
    private final List<Entity> entities = new ArrayList<>();
    private final StageChunkSource chunkSource = new StageChunkSource(this);
    private final StageEntityGetter entityGetter = new StageEntityGetter(entities);
    private final Holder<Biome> biome = StageRegistries.biome();
    private final Scoreboard scoreboard = new Scoreboard();
    private final TickRateManager tickRate = new TickRateManager();
    private final DifficultyInstance difficulty = new DifficultyInstance(Difficulty.PEACEFUL, 0L, 0L, 0f);
    private final RandomSource random = RandomSource.create(20260929L);
    private final StageLighting lighting;
    @Nullable private RecipeManager recipes;
    private long subTick;
    private int nextRegionX;
    private int nextEntityId = 1;
    private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();

    public StageLevel(final StageLighting lighting) {
        super(new ClientLevel.ClientLevelData(Difficulty.PEACEFUL, false, false), Level.OVERWORLD, StageRegistries.access(),
            StageRegistries.dimensionType(), () -> InactiveProfiler.INSTANCE, true, false, 0L, 0);
        this.lighting = lighting;
    }

    public StageLighting lighting() { return lighting; }

    // ------------------------------------------------------------------ blocks

    /** A fresh origin far from every other region, so meshing and AO never see a neighbour they should not. */
    public BlockPos allocateRegion() {
        final BlockPos p = new BlockPos(nextRegionX, 0, 0);
        nextRegionX += 4096;
        return p;
    }

    /** Places a block and, for entity blocks, its block entity (from {@code nbt} when given, else a fresh one). */
    public void place(final BlockPos pos, final BlockState state, @Nullable final CompoundTag nbt) {
        final long key = pos.asLong();
        final BlockEntity old = blockEntities.remove(key);
        if (old != null) old.setRemoved();
        if (state.isAir()) {
            blocks.remove(key);
            return;
        }
        final BlockPos immutable = pos.immutable();
        blocks.put(key, state);
        if (state.hasBlockEntity() && state.getBlock() instanceof EntityBlock eb) {
            BlockEntity be = null;
            try {
                be = nbt != null ? BlockEntity.loadStatic(immutable, state, nbt, registryAccess()) : eb.newBlockEntity(immutable, state);
            } catch (final Exception e) {
                Slate.LOGGER.warn("[Slate] stage: block entity for {} failed: {}", state, e.toString());
            }
            if (be != null) {
                be.setLevel(this);
                blockEntities.put(key, be);
            }
        }
    }

    /** Removes every block (and block entity) in the inclusive box. */
    public void clear(final BlockPos min, final BlockPos max) {
        for (int x = min.getX(); x <= max.getX(); x++)
            for (int y = min.getY(); y <= max.getY(); y++)
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    final long key = BlockPos.asLong(x, y, z);
                    blocks.remove(key);
                    final BlockEntity be = blockEntities.remove(key);
                    if (be != null) be.setRemoved();
                }
    }

    public boolean isEmptyAt(final int x, final int y, final int z) {
        return !blocks.containsKey(BlockPos.asLong(x, y, z));
    }

    public List<BlockEntity> blockEntitiesIn(final BlockPos min, final BlockPos max, final List<BlockEntity> out) {
        out.clear();
        for (final BlockEntity be : blockEntities.values()) {
            final BlockPos p = be.getBlockPos();
            if (p.getX() >= min.getX() && p.getX() <= max.getX() && p.getY() >= min.getY() && p.getY() <= max.getY()
                && p.getZ() >= min.getZ() && p.getZ() <= max.getZ()) out.add(be);
        }
        return out;
    }

    @Override
    public BlockState getBlockState(final BlockPos pos) {
        if (isOutsideBuildHeight(pos)) return VOID;
        final BlockState s = blocks.get(pos.asLong());
        return s == null ? AIR : s;
    }

    public BlockState getBlockState(final int x, final int y, final int z) {
        final BlockState s = blocks.get(BlockPos.asLong(x, y, z));
        return s == null ? AIR : s;
    }

    @Override
    public FluidState getFluidState(final BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    @Nullable
    public BlockEntity getBlockEntity(final BlockPos pos) {
        return blockEntities.get(pos.asLong());
    }

    @Override
    public boolean setBlock(final BlockPos pos, final BlockState state, final int flags, final int recursion) {
        if (isOutsideBuildHeight(pos)) return false;
        place(pos, state, null);
        return true;
    }

    @Override
    public boolean removeBlock(final BlockPos pos, final boolean moving) {
        return setBlock(pos, AIR, 3, 512);
    }

    @Override
    public boolean destroyBlock(final BlockPos pos, final boolean drop, @Nullable final Entity entity, final int recursion) {
        return setBlock(pos, AIR, 3, 512);
    }

    @Override
    public void setBlockEntity(final BlockEntity be) {
        be.setLevel(this);
        blockEntities.put(be.getBlockPos().asLong(), be);
    }

    @Override
    public void removeBlockEntity(final BlockPos pos) {
        final BlockEntity be = blockEntities.remove(pos.asLong());
        if (be != null) be.setRemoved();
    }

    /** Vanilla marks the chunk dirty here; there are no chunks. */
    @Override
    public void blockEntityChanged(final BlockPos pos) {}

    @Override
    public void sendBlockUpdated(final BlockPos pos, final BlockState old, final BlockState state, final int flags) {}

    // ------------------------------------------------------------------ light, shade, tint

    @Override
    public float getShade(final Direction direction, final boolean shade) {
        return lighting.shade(direction, shade);
    }

    @Override
    public int getBrightness(final LightLayer layer, final BlockPos pos) {
        return layer == LightLayer.SKY ? lighting.skyLight() : lighting.blockLight();
    }

    @Override
    public int getRawBrightness(final BlockPos pos, final int amount) {
        return Math.max(lighting.skyLight() - amount, lighting.blockLight());
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return chunkSource.getLightEngine();
    }

    @Override
    public int getBlockTint(final BlockPos pos, final ColorResolver resolver) {
        return resolver.getColor(biome.value(), pos.getX(), pos.getZ());
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(final int x, final int y, final int z) {
        return biome;
    }

    @Override
    public int getSkyDarken() { return 0; }

    // ------------------------------------------------------------------ chunks (there are none)

    @Override
    @Nullable
    public ChunkAccess getChunk(final int x, final int z, final ChunkStatus status, final boolean create) {
        return null;
    }

    @Override
    public boolean hasChunk(final int x, final int z) { return true; }

    @Override
    public int getHeight(final Heightmap.Types type, final int x, final int z) {
        int top = getMinBuildHeight();
        for (int y = getMaxBuildHeight() - 1; y >= getMinBuildHeight(); y--) {
            if (blocks.containsKey(BlockPos.asLong(x, y, z))) { top = y + 1; break; }
        }
        return top;
    }

    @Override
    public int getSeaLevel() { return 63; }

    @Override
    public FeatureFlagSet enabledFeatures() { return FeatureFlags.DEFAULT_FLAGS; }

    @Override
    public ChunkSource getChunkSource() { return chunkSource; }

    @Override
    public LevelTickAccess<Block> getBlockTicks() { return BlackholeTickAccess.emptyLevelList(); }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() { return BlackholeTickAccess.emptyLevelList(); }

    @Override
    public long nextSubTickCount() { return subTick++; }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(final BlockPos pos) { return difficulty; }

    @Override
    @Nullable
    public MinecraftServer getServer() { return null; }

    @Override
    public RandomSource getRandom() { return random; }

    @Override
    public String gatherChunkSourceStats() { return "stage"; }

    // ------------------------------------------------------------------ sounds, particles, events: silent

    @Override
    public void playSound(@Nullable final Player player, final BlockPos pos, final SoundEvent sound, final SoundSource source, final float volume, final float pitch) {}

    @Override
    public void playSeededSound(@Nullable final Player player, final double x, final double y, final double z, final Holder<SoundEvent> sound,
                                final SoundSource source, final float volume, final float pitch, final long seed) {}

    @Override
    public void playSeededSound(@Nullable final Player player, final Entity entity, final Holder<SoundEvent> sound, final SoundSource source,
                                final float volume, final float pitch, final long seed) {}

    @Override
    public void addParticle(final ParticleOptions options, final double x, final double y, final double z, final double dx, final double dy, final double dz) {}

    @Override
    public void levelEvent(@Nullable final Player player, final int type, final BlockPos pos, final int data) {}

    @Override
    public void gameEvent(final Holder<GameEvent> event, final Vec3 pos, final GameEvent.Context context) {}

    @Override
    public void destroyBlockProgress(final int id, final BlockPos pos, final int progress) {}

    // ------------------------------------------------------------------ misc level services

    @Override
    public TickRateManager tickRateManager() { return tickRate; }

    @Override
    @Nullable
    public MapItemSavedData getMapData(final MapId id) { return null; }

    @Override
    public void setMapData(final MapId id, final MapItemSavedData data) {}

    @Override
    public MapId getFreeMapId() { return new MapId(0); }

    @Override
    public Scoreboard getScoreboard() { return scoreboard; }

    @Override
    public RecipeManager getRecipeManager() {
        if (recipes == null) recipes = new RecipeManager(registryAccess());
        return recipes;
    }

    @Override
    public PotionBrewing potionBrewing() { return PotionBrewing.EMPTY; }

    // ------------------------------------------------------------------ entities

    @Override
    protected LevelEntityGetter<Entity> getEntities() { return entityGetter; }

    @Override
    @Nullable
    public Entity getEntity(final int id) {
        for (final Entity e : entities) if (e.getId() == id) return e;
        return null;
    }

    @Override
    public List<? extends Player> players() { return List.of(); }

    /** Creates an entity of {@code type} in this level at the position (null when the type cannot be created here). */
    @Nullable
    public <T extends Entity> T spawn(final EntityType<T> type, final double x, final double y, final double z, final float yaw) {
        final T e;
        try {
            e = type.create(this);
        } catch (final Exception ex) {
            Slate.LOGGER.warn("[Slate] stage: cannot create {}: {}", EntityType.getKey(type), ex.toString());
            return null;
        }
        if (e == null) return null;
        e.setId(nextEntityId++);
        e.moveTo(x, y, z, yaw, 0f);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
        e.setOldPosAndRot();
        entities.add(e);
        return e;
    }

    public void discard(final Entity e) {
        entities.remove(e);
    }

    public List<Entity> entities() { return entities; }

    // ------------------------------------------------------------------ NeoForge additions (no @Override: absent on Fabric)

    public void setDayTimeFraction(final float fraction) {}

    public float getDayTimeFraction() { return 0f; }

    public float getDayTimePerTick() { return 1f; }

    public void setDayTimePerTick(final float perTick) {}

    // ------------------------------------------------------------------ helpers

    /** No chunks: nothing to load, everything "exists". */
    private static final class StageChunkSource extends ChunkSource {
        private final StageLevel level;
        private final LevelLightEngine light;

        StageChunkSource(final StageLevel level) {
            this.level = level;
            this.light = new LevelLightEngine(this, false, false);
        }

        @Override
        @Nullable
        public ChunkAccess getChunk(final int x, final int z, final ChunkStatus status, final boolean create) { return null; }

        @Override
        @Nullable
        public LightChunk getChunkForLighting(final int x, final int z) { return null; }

        @Override
        public void tick(final BooleanSupplier hasTime, final boolean tickChunks) {}

        @Override
        public String gatherStats() { return "stage"; }

        @Override
        public int getLoadedChunksCount() { return 0; }

        @Override
        public LevelLightEngine getLightEngine() { return light; }

        @Override
        public BlockGetter getLevel() { return level; }
    }

    /** The stage's entity list, in the shape {@code Level} wants. */
    private static final class StageEntityGetter implements LevelEntityGetter<Entity> {
        private final List<Entity> entities;

        StageEntityGetter(final List<Entity> entities) { this.entities = entities; }

        @Override
        @Nullable
        public Entity get(final int id) {
            for (final Entity e : entities) if (e.getId() == id) return e;
            return null;
        }

        @Override
        @Nullable
        public Entity get(final UUID uuid) {
            for (final Entity e : entities) if (e.getUUID().equals(uuid)) return e;
            return null;
        }

        @Override
        public Iterable<Entity> getAll() { return entities; }

        @Override
        public <U extends Entity> void get(final EntityTypeTest<Entity, U> test, final AbortableIterationConsumer<U> consumer) {
            for (final Entity e : entities) {
                final U u = test.tryCast(e);
                if (u != null && consumer.accept(u).shouldAbort()) return;
            }
        }

        @Override
        public void get(final AABB box, final Consumer<Entity> consumer) {
            for (final Entity e : entities) if (e.getBoundingBox().intersects(box)) consumer.accept(e);
        }

        @Override
        public <U extends Entity> void get(final EntityTypeTest<Entity, U> test, final AABB box, final AbortableIterationConsumer<U> consumer) {
            for (final Entity e : entities) {
                if (!e.getBoundingBox().intersects(box)) continue;
                final U u = test.tryCast(e);
                if (u != null && consumer.accept(u).shouldAbort()) return;
            }
        }
    }
}
