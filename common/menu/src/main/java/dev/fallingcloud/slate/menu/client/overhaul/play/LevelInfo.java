package dev.fallingcloud.slate.menu.client.overhaul.play;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/**
 * What a saved world's {@code level.dat} says beyond the summary the world list already has: the seed, the days
 * that have passed, how long it was played, where the player stands and in what state, where the world spawns, the
 * data packs. Read for the Play screen's details, which show the summary first and these when scrolled to.
 *
 * <p>Everything is optional: a field a world does not have reads as its "unknown" value, and a file that cannot be
 * read gives {@link #UNKNOWN}.</p>
 */
public record LevelInfo(boolean known, long seed, boolean hasSeed, long dayTime, long gameTime, int difficulty, boolean difficultyLocked,
                        int spawnX, int spawnY, int spawnZ, boolean hasPlayer, double playerX, double playerY, double playerZ, String dimension,
                        float health, int food, int xpLevel, boolean raining, boolean thundering, List<String> dataPacks) {

    public static final LevelInfo UNKNOWN = new LevelInfo(false, 0, false, 0, 0, -1, false, 0, 64, 0, false, 0, 64, 0, "", 20f, 20, 0, false, false, List.of());

    /** Days since the world began. */
    public long days() { return gameTime / 24000L; }

    /** Where the map should open: on the player when the world remembers one in the overworld, else on the spawn. */
    public int centreX() { return hasPlayer && overworld() ? (int) Math.floor(playerX) : spawnX; }

    public int centreZ() { return hasPlayer && overworld() ? (int) Math.floor(playerZ) : spawnZ; }

    public boolean overworld() { return dimension.isEmpty() || dimension.endsWith("overworld"); }

    /** Reads {@code level.dat} of the world at {@code folder} on the pool; the result arrives on the render thread. */
    public static CompletableFuture<LevelInfo> read(final Path folder) {
        return CompletableFuture.supplyAsync(() -> parse(folder.resolve("level.dat")), PlayIo.POOL)
            .thenApplyAsync(i -> i, Minecraft.getInstance());
    }

    private static LevelInfo parse(final Path file) {
        if (!Files.isRegularFile(file)) return UNKNOWN;
        try {
            final CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            final CompoundTag data = root.getCompound("Data");
            final CompoundTag gen = data.getCompound("WorldGenSettings");
            final boolean hasSeed = gen.contains("seed", Tag.TAG_ANY_NUMERIC);
            final CompoundTag player = data.getCompound("Player");
            final boolean hasPlayer = player.contains("Pos", Tag.TAG_LIST);
            double px = 0, py = 64, pz = 0;
            if (hasPlayer) {
                final ListTag pos = player.getList("Pos", Tag.TAG_DOUBLE);
                if (pos.size() == 3) { px = pos.getDouble(0); py = pos.getDouble(1); pz = pos.getDouble(2); }
            }
            final List<String> packs = new ArrayList<>();
            final ListTag enabled = data.getCompound("DataPacks").getList("Enabled", Tag.TAG_STRING);
            for (int i = 0; i < enabled.size(); i++) {
                final String p = enabled.getString(i);
                if (!p.equals("vanilla")) packs.add(p);
            }
            return new LevelInfo(true, gen.getLong("seed"), hasSeed, data.getLong("DayTime"), data.getLong("Time"),
                data.contains("Difficulty", Tag.TAG_ANY_NUMERIC) ? data.getByte("Difficulty") : -1, data.getBoolean("DifficultyLocked"),
                data.getInt("SpawnX"), data.getInt("SpawnY"), data.getInt("SpawnZ"), hasPlayer, px, py, pz, player.getString("Dimension"),
                player.contains("Health", Tag.TAG_ANY_NUMERIC) ? player.getFloat("Health") : 20f,
                player.contains("foodLevel", Tag.TAG_ANY_NUMERIC) ? player.getInt("foodLevel") : 20, player.getInt("XpLevel"),
                data.getBoolean("raining"), data.getBoolean("thundering"), List.copyOf(packs));
        } catch (final Exception e) {
            return UNKNOWN;
        }
    }
}
