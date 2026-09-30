package dev.fallingcloud.slate.menu.client.overhaul.pause;

import dev.fallingcloud.slate.core.stage.Stage;
import dev.fallingcloud.slate.core.stage.node.BlocksNode;
import dev.fallingcloud.slate.menu.SlateMenu;
import java.util.ArrayDeque;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The piece of the world the player stands on, cut out and set on a stage: a slab of the ground under their feet,
 * and on it, behind them, what they were looking at. It is cut the way a doll's house is: nothing stands between
 * the viewer and the figure, a room has no ceiling, and of its walls the one on the viewer's side is taken away.
 * Nothing in it hangs in the air: what does not stand on its ground, or hold on to something that does, is left out.
 * The piece is turned so that what the player faced is at its back, which is where the viewer looks.
 *
 * <p>The blocks are the world's own block states (turned with the piece), in the biome's colours, with what is
 * written on a sign or lies in a chest carried over. A mod's block is taken when it is a plain model; one that
 * needs its block entity to be drawn is left out, because nothing promises that it can be drawn outside a world.</p>
 */
final class Diorama {

    /** Blocks to either side of the player, behind the figure (where the player looked), before it, under the feet, and over them. */
    static final int SIDE = 4, BACK = 5, FRONT = 2, DEEP = 3, HIGH = 6;
    static final int WIDTH = SIDE * 2 + 1, LENGTH = BACK + FRONT + 1, HEIGHT = DEEP + HIGH;

    /**
     * @param feet   how far under the top of the slab the player's feet are (0 on a full block, half a block on a
     *               slab's lower half): what the figure is lowered by
     * @param bottom the underside of the lowest block of the piece, in blocks from the top of the slab
     * @param top    the top of its highest block
     */
    record Piece(BlocksNode land, float feet, float bottom, float top) {}

    /**
     * The piece round {@code player}, or null where there is nothing to cut it from (in the air, in the void).
     *
     * @param round how far round to the right of the piece the viewer stands, in degrees: what decides which wall of
     *              a room is in the way
     */
    @Nullable
    static Piece cut(final Stage stage, final LocalPlayer player, final ClientLevel level, final float round) {
        try {
            return build(stage, player, level, round);
        } catch (final RuntimeException e) {
            SlateMenu.LOGGER.warn("[Slate Menu] the pause menu's piece of the world could not be made, left out: {}", e.toString());
            return null;
        }
    }

    private static Piece build(final Stage stage, final LocalPlayer player, final ClientLevel level, final float round) {
        final float viewX = -Mth.sin((float) Math.toRadians(round)), viewZ = -Mth.cos((float) Math.toRadians(round));
        // What the player faces goes to the back of the piece, which is north on the stage.
        final Rotation turn = switch (player.getDirection()) {
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.CLOCKWISE_90;
            default -> Rotation.NONE;
        };
        final Rotation back = switch (turn) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> turn;
        };
        final int px = Mth.floor(player.getX()), pz = Mth.floor(player.getZ());
        // The first level that is not ground: a block the feet are in the lower half of is ground too.
        final int base = Mth.ceil(player.getY() - 1.0E-4);
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // A room has no ceiling: the piece ends under whatever is over the player's head.
        int top = HIGH - 1;
        boolean roofed = false;
        for (int y = 2; y < HIGH; y++) {
            pos.set(px, base + y, pz);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                top = y - 1;
                roofed = true;
                break;
            }
        }

        // What stands over the ground behind the figure, as the piece has it: which places are filled, to say what
        // hides what.
        final boolean[][][] filled = new boolean[WIDTH][HIGH + 1][BACK];
        for (int lx = -SIDE; lx <= SIDE; lx++) {
            for (int lz = -BACK; lz < 0; lz++) {
                final BlockPos offset = new BlockPos(lx, 0, lz).rotate(back);
                for (int ly = 0; ly <= top; ly++) {
                    pos.set(px + offset.getX(), base + ly, pz + offset.getZ());
                    filled[lx + SIDE][ly][lz + BACK] = !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
                }
            }
        }

        // What belongs to the piece, place by place.
        final BlockState[][][] kept = new BlockState[WIDTH][HEIGHT][LENGTH];
        final ArrayDeque<int[]> reach = new ArrayDeque<>();
        for (int lx = -SIDE; lx <= SIDE; lx++) {
            for (int lz = -BACK; lz <= FRONT; lz++) {
                final BlockPos offset = new BlockPos(lx, 0, lz).rotate(back);
                for (int ly = -DEEP; ly <= top; ly++) {
                    pos.set(px + offset.getX(), base + ly, pz + offset.getZ());
                    final BlockState state = level.getBlockState(pos);
                    if (state.isAir() || !wanted(level, pos, state, lx, ly, lz) || !drawable(state)) continue;
                    // Indoors (and in a cave) the wall on the viewer's side goes, as the front one has: it would hide the room.
                    if (roofed && ly >= 0 && lz < 0 && hides(filled, lx, ly, lz, viewX, viewZ)) continue;
                    kept[lx + SIDE][ly + DEEP][lz + BACK] = state;
                    // The ground is what everything else has to stand on.
                    if (ly < 0) reach.add(new int[] {lx + SIDE, ly + DEEP, lz + BACK});
                }
            }
        }
        // Only what stands on the ground of the piece, or hangs together with something that does: the crown of a tree
        // whose trunk is outside, a lamp that hung from the ceiling, would hang in the air.
        final boolean[][][] standing = new boolean[WIDTH][HEIGHT][LENGTH];
        for (final int[] seed : reach) standing[seed[0]][seed[1]][seed[2]] = true;
        while (!reach.isEmpty()) {
            final int[] at = reach.poll();
            for (final Direction d : Direction.values()) {
                final int x = at[0] + d.getStepX(), y = at[1] + d.getStepY(), z = at[2] + d.getStepZ();
                if (x < 0 || y < 0 || z < 0 || x >= WIDTH || y >= HEIGHT || z >= LENGTH || standing[x][y][z] || kept[x][y][z] == null) continue;
                standing[x][y][z] = true;
                reach.add(new int[] {x, y, z});
            }
        }

        final BlocksNode land = new BlocksNode(stage.level(), WIDTH, HEIGHT, LENGTH);
        int placed = 0, lowest = 0, highest = -DEEP;
        for (int lx = -SIDE; lx <= SIDE; lx++) {
            for (int lz = -BACK; lz <= FRONT; lz++) {
                final BlockPos offset = new BlockPos(lx, 0, lz).rotate(back);
                for (int ly = -DEEP; ly <= top; ly++) {
                    final BlockState state = kept[lx + SIDE][ly + DEEP][lz + BACK];
                    if (state == null || !standing[lx + SIDE][ly + DEEP][lz + BACK]) continue;
                    pos.set(px + offset.getX(), base + ly, pz + offset.getZ());
                    land.put(lx + SIDE, ly + DEEP, lz + BACK, state.rotate(turn), data(level, pos, state));
                    placed++;
                    lowest = Math.min(lowest, ly);
                    highest = Math.max(highest, ly);
                }
            }
        }
        if (placed == 0) {
            land.done();
            land.dispose();
            return null;
        }
        final BlockPos at = player.blockPosition();
        stage.level().tintRegion(land.origin(), BiomeColors.getAverageGrassColor(level, at), BiomeColors.getAverageFoliageColor(level, at),
            BiomeColors.getAverageWaterColor(level, at));
        land.done();
        return new Piece(land, (float) (player.getY() - base), lowest, highest + 1f);
    }

    /**
     * Whether a block of the world belongs to the piece. The ground does, all of it. Over the ground, everything
     * behind the figure does; beside and before it only what is small enough to stand in nobody's way (grass, a
     * flower, a torch), and nothing where the figure itself stands.
     */
    private static boolean wanted(final ClientLevel level, final BlockPos pos, final BlockState state, final int lx, final int ly, final int lz) {
        if (ly < 0 || lz < 0) return true;
        if (ly > 0 || lx == 0 && lz == 0) return false;
        return state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty() && level.getBlockState(pos.above()).isAir();
    }

    /** Whether a place over the ground behind the figure is filled; outside the piece nothing is. */
    private static boolean filled(final boolean[][][] filled, final int lx, final int ly, final int lz) {
        return lx >= -SIDE && lx <= SIDE && lz >= -BACK && lz < 0 && ly >= 0 && ly <= HIGH && filled[lx + SIDE][ly][lz + BACK];
    }

    /** Wall, as against furniture: at least two blocks of it, one on the other. */
    private static boolean wall(final boolean[][][] filled, final int lx, final int ly, final int lz) {
        return filled(filled, lx, ly, lz) && (ly >= 1 || filled(filled, lx, ly + 1, lz));
    }

    /**
     * Whether a block behind the figure hides something of the room from the viewer: it is part of a wall, and on
     * the line from the viewer through it and on into the piece there is room, or a thing that stands in the room.
     * The line is followed from the block's middle and from its far edge, as far as the piece goes; walls behind
     * walls hide nothing, and neither does the wall at the back, which has only the outside behind it.
     *
     * @param viewX the way the viewer looks across the piece, from above: its part to the side
     * @param viewZ and its part to the back
     */
    private static boolean hides(final boolean[][][] filled, final int lx, final int ly, final int lz, final float viewX, final float viewZ) {
        if (!wall(filled, lx, ly, lz)) return false;
        final float edge = viewX < 0f ? -0.5f : 0.5f;
        for (int ray = 0; ray < 2; ray++) {
            final float x0 = lx + (ray == 0 ? 0f : edge), z0 = lz;
            for (float t = 0.5f; t <= BACK + SIDE; t += 0.5f) {
                final int cx = Math.round(x0 + viewX * t), cz = Math.round(z0 + viewZ * t);
                if (cx == lx && cz == lz) continue;
                if (cx < -SIDE || cx > SIDE || cz < -BACK || cz >= 0) break;
                if (!wall(filled, cx, ly, cz)) return true;
            }
        }
        return false;
    }

    /** The game's own blocks all are; a mod's is when it is a model and needs no block entity to be drawn. */
    private static boolean drawable(final BlockState state) {
        if ("minecraft".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())) return true;
        return !state.hasBlockEntity() && state.getRenderShape() == RenderShape.MODEL;
    }

    /** What a block entity knows (the writing on a sign, the pattern of a banner), for the copy of it on the stage. */
    @Nullable
    private static CompoundTag data(final ClientLevel level, final BlockPos pos, final BlockState state) {
        if (!state.hasBlockEntity()) return null;
        try {
            final BlockEntity be = level.getBlockEntity(pos);
            // With its id: that is what says which kind of block entity the copy is to be.
            return be == null ? null : be.saveWithId(level.registryAccess());
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private Diorama() {}
}
