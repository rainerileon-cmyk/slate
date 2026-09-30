package dev.fallingcloud.slate.menu.client.loading.journey;

import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.menu.client.overhaul.create.LandSampler;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The land as the player last saw it: taken from the chunks the game holds when a world is left, kept in the
 * world's folder, and laid into the {@link Land} of the loading screen the next time that world is opened. It is
 * what makes the model show the house that was built and the field that was dug, not only the hills the world was
 * made with.
 *
 * <p>Every cell is the column at its middle: how high the topmost block that stops movement lies, what that block
 * shows on top and on its side, and the water standing on it. Ground that grew keeps its earth and stone
 * underneath; what was built is of its own material all the way down. What is thin (a stalk of bamboo, a fence) is
 * looked through, and a tree is taken for what the model makes of trees everywhere else: a trunk and a crown on the
 * ground it stands on.</p>
 */
final class LandSnapshot {

    /** The file in a world's folder. */
    static final String FILE = "slate/land.bin";

    private static final int MAGIC = 0x534C4E44, VERSION = 2;
    private static final int WHITE = 0xFFFFFFFF;
    /** How far down thin things are looked through for the land under them, in blocks. */
    private static final int THIN = 32;
    /** How far down a tree is looked through for the ground it stands on, in blocks. */
    private static final int TREE = 48;
    /** How deep water is looked through for its bed, in blocks. */
    private static final int DEPTH = 48;

    /** What one kind of block shows. */
    private record Face(String top, boolean topTinted, int topIndex, String side, boolean sideTinted, int sideIndex) {}

    private LandSnapshot() {}

    // ------------------------------------------------------------------ taking

    /**
     * The land around the player, from the chunks that are loaded. Null where there is nothing to take: no world, or
     * one with a roof over it (the land under the Nether's bedrock cannot be seen from above). Render thread.
     */
    static @Nullable Land take(final Minecraft mc) {
        final ClientLevel level = mc.level;
        if (level == null || mc.player == null || level.dimensionType().hasCeiling()) return null;
        try {
            final Land land = new Land(mc.player.getBlockX(), mc.player.getBlockZ());
            land.dayTime = (int) Math.floorMod(level.getDayTime(), 24000L);
            final int sea = level.getSeaLevel();
            final int table = Math.max(level.getMinBuildHeight(), sea - LandSampler.DEEP);
            land.sea = cells(sea, table);
            final Map<BlockState, Face> faces = new HashMap<>();
            final Map<BlockState, Boolean> thin = new HashMap<>();
            final RandomSource random = RandomSource.create(42L);
            final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            LevelChunk chunk = null;
            for (int z = 0; z < Land.DEPTH; z++) {
                for (int x = 0; x < Land.WIDTH; x++) {
                    final int wx = land.blockX(x), wz = land.blockZ(z);
                    if (chunk == null || chunk.getPos().x != wx >> 4 || chunk.getPos().z != wz >> 4) {
                        chunk = level.getChunkSource().getChunk(wx >> 4, wz >> 4, ChunkStatus.FULL, false);
                    }
                    if (chunk == null || chunk.isEmpty()) continue;
                    int y = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, wx & 15, wz & 15);
                    if (y < level.getMinBuildHeight()) continue;
                    // What is thin is not land: a stalk of bamboo, a fence post, a lightning rod would stand as a tower.
                    final int stop = Math.max(level.getMinBuildHeight(), y - THIN);
                    while (y > stop && thin.computeIfAbsent(chunk.getBlockState(pos.set(wx, y, wz)), s -> thin(s, level, pos))) y--;
                    // A crown of leaves: the land is the ground the tree stands on, and the tree stands on it in the model.
                    String trunk = null, canopy = null;
                    int canopyTint = WHITE;
                    final BlockState top = chunk.getBlockState(pos.set(wx, y, wz));
                    if (top.is(BlockTags.LEAVES)) {
                        final Face leaf = faces.computeIfAbsent(top, s -> face(mc, s, random));
                        canopy = leaf.top();
                        canopyTint = leaf.topTinted() ? tint(mc, level, top, pos, leaf.topIndex()) : WHITE;
                        final int floor = Math.max(level.getMinBuildHeight(), y - TREE);
                        while (y > floor) {
                            final BlockState s = chunk.getBlockState(pos.set(wx, y, wz));
                            if (s.is(BlockTags.LOGS)) {
                                if (trunk == null) trunk = faces.computeIfAbsent(s, b -> face(mc, b, random)).side();
                            } else if (!s.is(BlockTags.LEAVES) && s.getFluidState().isEmpty() && s.blocksMotion()
                                && !thin.computeIfAbsent(s, b -> thin(b, level, pos))) {
                                break;
                            } else if (!s.getFluidState().isEmpty()) {
                                break;
                            }
                            y--;
                        }
                        if (trunk == null) trunk = "oak_log";
                    }
                    Land.Cell cell = cell(mc, level, chunk, pos.set(wx, y, wz), table, faces, random);
                    if (cell != null && canopy != null) cell = cell.withTree(trunk, canopy, canopyTint);
                    land.lay(x, z, cell);
                }
            }
            land.close();
            return land.any() ? land : null;
        } catch (final Exception e) {
            SlateMenu.LOGGER.warn("[Slate Menu] the land could not be taken down: {}", e.toString());
            return null;
        }
    }

    /** Whether a block is too thin to be land: it stops movement, but over less than most of its cell. */
    private static boolean thin(final BlockState state, final ClientLevel level, final BlockPos pos) {
        if (!state.getFluidState().isEmpty()) return false;
        final VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return true;
        final AABB box = shape.bounds();
        return box.getXsize() < 0.6 || box.getZsize() < 0.6;
    }

    private static int cells(final int y, final int table) {
        return Land.sunk(Math.round((y - table) / (float) LandSampler.RISE));
    }

    /** Water as the light of the scene leaves it readable: its own colour, a third of the way to white. */
    private static int lively(final int rgb) {
        final int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        return 0xFF000000 | r + (255 - r) / 3 << 16 | g + (255 - g) / 3 << 8 | b + (255 - b) / 3;
    }

    private static @Nullable Land.Cell cell(final Minecraft mc, final ClientLevel level, final LevelChunk chunk, final BlockPos.MutableBlockPos pos,
                                            final int table, final Map<BlockState, Face> faces, final RandomSource random) {
        final int surface = pos.getY();
        BlockState state = chunk.getBlockState(pos);
        int water = 0, waterTint = WHITE;
        if (state.getFluidState().is(FluidTags.WATER) && !state.isFaceSturdy(level, pos, Direction.UP)) {
            // Water: what counts as ground is its bed.
            waterTint = lively(BiomeColors.getAverageWaterColor(level, pos));
            int y = surface;
            while (y > level.getMinBuildHeight() && surface - y < DEPTH) {
                y--;
                state = chunk.getBlockState(pos.setY(y));
                if (!state.getFluidState().is(FluidTags.WATER) && state.blocksMotion()) break;
            }
            water = Math.max(1, cells(surface + 1, table) - cells(y + 1, table));
        }
        if (state.isAir()) return null;
        final Face face = faces.computeIfAbsent(state, s -> face(mc, s, random));
        final int ground = cells(pos.getY() + 1, table);
        final int topTint = face.topTinted() ? tint(mc, level, state, pos, face.topIndex()) : WHITE;
        if (state.is(BlockTags.DIRT) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.FARMLAND) || state.is(BlockTags.SNOW)) {
            return new Land.Cell(ground, face.top(), topTint, "dirt", WHITE, Land.NATURAL, water, waterTint, false, null, null, WHITE);
        }
        if (state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)) {
            final String under = state.is(Blocks.RED_SAND) ? "red_sandstone" : state.is(BlockTags.SAND) ? "sandstone" : "stone";
            return new Land.Cell(ground, face.top(), topTint, under, WHITE, Land.NATURAL, water, waterTint, false, null, null, WHITE);
        }
        if (state.is(BlockTags.BASE_STONE_OVERWORLD)) {
            return new Land.Cell(ground, face.top(), topTint, face.side(), WHITE, Land.NATURAL, water, waterTint, false, null, null, WHITE);
        }
        final int sideTint = face.sideTinted() ? tint(mc, level, state, pos, face.sideIndex()) : WHITE;
        return new Land.Cell(ground, face.top(), topTint, face.side(), sideTint, Land.BUILT, water, waterTint, false, null, null, WHITE);
    }

    private static int tint(final Minecraft mc, final ClientLevel level, final BlockState state, final BlockPos pos, final int index) {
        final int rgb = mc.getBlockColors().getColor(state, level, pos, index);
        return rgb == -1 ? WHITE : rgb | 0xFF000000;
    }

    /** What a block shows on top and on its side: the first quad its model has for each. */
    private static Face face(final Minecraft mc, final BlockState state, final RandomSource random) {
        if (!state.getFluidState().isEmpty() && state.getBlock() == Blocks.LAVA) return new Face("lava_still", false, 0, "lava_still", false, 0);
        final BakedModel model = mc.getBlockRenderer().getBlockModel(state);
        final BakedQuad top = quad(model, state, Direction.UP, random), side = quad(model, state, Direction.NORTH, random);
        final String particle = model.getParticleIcon().contents().name().toString();
        final String t = top == null ? particle : top.getSprite().contents().name().toString();
        final String s = side == null ? t : side.getSprite().contents().name().toString();
        return new Face(t, top != null && top.isTinted(), top == null ? 0 : top.getTintIndex(), s, side != null && side.isTinted(),
            side == null ? 0 : side.getTintIndex());
    }

    private static @Nullable BakedQuad quad(final BakedModel model, final BlockState state, final Direction direction, final RandomSource random) {
        random.setSeed(42L);
        List<BakedQuad> quads = model.getQuads(state, direction, random);
        if (quads.isEmpty()) {
            // A model that is not a full cube keeps its faces where they are not culled.
            random.setSeed(42L);
            quads = model.getQuads(state, null, random);
            for (final BakedQuad q : quads) if (q.getDirection() == direction) return q;
            return null;
        }
        return quads.get(0);
    }

    // ------------------------------------------------------------------ keeping

    /** Writes the land where the world keeps it. Off the render thread. */
    static void write(final Land land, final Path file, final String dimension) {
        // The cells are read here, on the caller's thread: nothing lays any more once a land has been taken.
        final List<String> names = new ArrayList<>();
        final Map<String, Integer> index = new HashMap<>();
        final Land.Cell[] cells = new Land.Cell[Land.WIDTH * Land.DEPTH];
        for (int z = 0; z < Land.DEPTH; z++) for (int x = 0; x < Land.WIDTH; x++) {
            final Land.Cell c = land.at(x, z);
            cells[z * Land.WIDTH + x] = c;
            if (c == null) continue;
            for (final String name : new String[] {c.top(), c.under(), c.trunk(), c.canopy()}) {
                if (name != null && !index.containsKey(name)) {
                    index.put(name, names.size());
                    names.add(name);
                }
            }
        }
        Util.ioPool().execute(() -> {
            try {
                Files.createDirectories(file.getParent());
                final Path part = file.resolveSibling(file.getFileName() + ".part");
                try (OutputStream raw = Files.newOutputStream(part); DataOutputStream out = new DataOutputStream(new GZIPOutputStream(raw))) {
                    out.writeInt(MAGIC);
                    out.writeInt(VERSION);
                    out.writeInt(Land.WIDTH);
                    out.writeInt(Land.DEPTH);
                    out.writeInt(Land.STEP);
                    out.writeInt(land.centerX);
                    out.writeInt(land.centerZ);
                    out.writeInt(land.dayTime);
                    out.writeInt(land.sea);
                    out.writeUTF(dimension);
                    out.writeInt(names.size());
                    for (final String name : names) out.writeUTF(name);
                    for (final Land.Cell c : cells) {
                        out.writeBoolean(c != null);
                        if (c == null) continue;
                        out.writeShort(c.ground());
                        out.writeShort(index.get(c.top()));
                        out.writeInt(c.topTint());
                        out.writeShort(index.get(c.under()));
                        out.writeInt(c.underTint());
                        out.writeShort(Math.min(c.strata(), Short.MAX_VALUE));
                        out.writeShort(c.water());
                        out.writeInt(c.waterTint());
                        final boolean tree = c.trunk() != null && c.canopy() != null;
                        out.writeBoolean(tree);
                        if (tree) {
                            out.writeShort(index.get(c.trunk()));
                            out.writeShort(index.get(c.canopy()));
                            out.writeInt(c.canopyTint());
                        }
                    }
                }
                Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (final IOException e) {
                SlateMenu.LOGGER.warn("[Slate Menu] the land could not be kept in {}: {}", file, e.toString());
            }
        });
    }

    /** What was kept of a world, with the dimension it was taken in; null where nothing was kept, or not by this version. */
    record Kept(Land land, String dimension) {}

    /** Reads what {@link #write} kept. The future never fails: it holds null where there is nothing to read. */
    static CompletableFuture<@Nullable Kept> read(final Path file) {
        return CompletableFuture.supplyAsync(() -> {
            if (!Files.isRegularFile(file)) return null;
            try (InputStream raw = Files.newInputStream(file); DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
                if (in.readInt() != MAGIC || in.readInt() != VERSION) return null;
                if (in.readInt() != Land.WIDTH || in.readInt() != Land.DEPTH || in.readInt() != Land.STEP) return null;
                final Land land = new Land(in.readInt(), in.readInt());
                land.dayTime = in.readInt();
                land.sea = in.readInt();
                final String dimension = in.readUTF();
                final String[] names = new String[in.readInt()];
                for (int i = 0; i < names.length; i++) names[i] = in.readUTF();
                for (int i = 0; i < Land.WIDTH * Land.DEPTH; i++) {
                    if (!in.readBoolean()) continue;
                    final int ground = in.readShort();
                    final String top = names[in.readShort()];
                    final int topTint = in.readInt();
                    final String under = names[in.readShort()];
                    final int underTint = in.readInt();
                    final int strata = in.readShort();
                    final int water = in.readShort();
                    final int waterTint = in.readInt();
                    final boolean tree = in.readBoolean();
                    final String trunk = tree ? names[in.readShort()] : null, canopy = tree ? names[in.readShort()] : null;
                    final int canopyTint = tree ? in.readInt() : WHITE;
                    land.lay(i % Land.WIDTH, i / Land.WIDTH, new Land.Cell(Math.max(1, ground), top, topTint, under, underTint,
                        strata == Short.MAX_VALUE ? Land.BUILT : strata, Math.max(0, water), waterTint, false, trunk, canopy, canopyTint));
                }
                return land.any() ? new Kept(land, dimension) : null;
            } catch (final Exception e) {
                SlateMenu.LOGGER.warn("[Slate Menu] the land kept in {} could not be read: {}", file, e.toString());
                return null;
            }
        }, Util.ioPool()).exceptionally(t -> null);
    }
}
