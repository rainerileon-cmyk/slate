package dev.fallingcloud.slate.building.ops.server;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.config.ServerOps;
import dev.fallingcloud.slate.building.net.OpResult;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.ops.Clipboard;
import dev.fallingcloud.slate.building.ops.ModeParams;
import dev.fallingcloud.slate.building.ops.OpMessages;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * In-game self-test of the operation engine (dev harness scenario {@code ops}): drives {@link OpsServer} exactly as
 * the network handlers do, as the real player, in creative and then in survival (with {@code requireToolbox} off for
 * the run, so no toolbox items are needed), and checks block counts in the world, inventory deltas and history.
 * Each stage logs {@code [OpsSelfTest] PASS|FAIL <stage>: <detail>}; a summary line ends the run. Runs on the server
 * thread, one stage at a time, waiting for each operation to finish.
 */
public final class OpsSelfTest {

    private record Stage(String name, Consumer<ServerPlayer> action, Function<ServerPlayer, @Nullable String> check) {}

    private static @Nullable Run run;
    private static volatile boolean finished;
    private static volatile int passed;
    private static volatile int failed;
    /** {@code creativeMaxVolume} while a stage lowered it (restored after that stage and at the end), else -1. */
    private static int savedCreativeMax = -1;
    /** Whether the far chunk of the stack check was loaded before the stack ran (then nothing can be concluded). */
    private static boolean farChunkWasLoaded;

    /** Starts the self-test for {@code player} (server thread). */
    public static void start(final MinecraftServer server, final UUID player) {
        run = new Run(player, stages());
        finished = false;
        passed = 0;
        failed = 0;
        SlateBuilding.LOGGER.info("[OpsSelfTest] starting ({} stages)", run.stages.size());
    }

    public static boolean finished() {
        return finished;
    }

    public static int passed() {
        return passed;
    }

    public static int failed() {
        return failed;
    }

    public static void tick(final MinecraftServer server) {
        final Run r = run;
        if (r == null) return;
        final ServerPlayer player = server.getPlayerList().getPlayer(r.player);
        if (player == null) {
            SlateBuilding.LOGGER.error("[OpsSelfTest] player left, aborting");
            end(r);
            return;
        }
        try {
            r.tick(player);
        } catch (final RuntimeException e) {
            SlateBuilding.LOGGER.error("[OpsSelfTest] FAIL {}: threw", r.index < r.stages.size() ? r.stages.get(r.index).name() : "?", e);
            failed++;
            r.index++;
            r.started = false;
            r.wait = 5;
        }
        if (r.index >= r.stages.size()) end(r);
    }

    private static void end(final Run r) {
        run = null;
        r.restore();
        SlateBuilding.LOGGER.info("[OpsSelfTest] summary: {} passed, {} failed", passed, failed);
        finished = true;
    }

    private static final class Run {
        final UUID player;
        final List<Stage> stages;
        int index;
        int wait;
        boolean started;
        boolean requireToolbox = true;
        boolean touchedRules;

        Run(final UUID player, final List<Stage> stages) {
            this.player = player;
            this.stages = stages;
        }

        void tick(final ServerPlayer p) {
            if (wait > 0) {
                wait--;
                return;
            }
            if (OpsServer.isBusy(p)) return;
            final Stage stage = stages.get(index);
            if (!started) {
                started = true;
                if (stage.name().equals("survival setup")) {
                    final ServerOps ops = BuildingServerSettings.local().ops();
                    requireToolbox = ops.requireToolbox;
                    ops.requireToolbox = false;
                    touchedRules = true;
                }
                stage.action().accept(p);
                wait = 2;
                return;
            }
            final String problem = stage.check().apply(p);
            if (problem == null) {
                passed++;
                SlateBuilding.LOGGER.info("[OpsSelfTest] PASS {}", stage.name());
            } else {
                failed++;
                final OpResult last = OpsServer.lastResult(p);
                SlateBuilding.LOGGER.error("[OpsSelfTest] FAIL {}: {} (last result: {})", stage.name(), problem, last);
            }
            index++;
            started = false;
            wait = 5;
        }

        void restore() {
            if (touchedRules) BuildingServerSettings.local().ops().requireToolbox = requireToolbox;
            restoreCreativeMax();
        }
    }

    // ---- the scenario ----

    private static final int Y = -60;

    private static List<Stage> stages() {
        final List<Stage> s = new ArrayList<>();
        final BlockPos fillA = new BlockPos(-30, Y, 10), fillB = new BlockPos(-26, Y + 4, 14);
        final BlockPos hollowA = new BlockPos(-22, Y, 10), hollowB = new BlockPos(-18, Y + 4, 14);
        final BlockPos wallsA = new BlockPos(-14, Y, 10), wallsB = new BlockPos(-10, Y + 4, 14);
        final BlockPos lineA = new BlockPos(-6, Y, 12), lineB = new BlockPos(3, Y + 3, 12);
        final BlockPos sphereC = new BlockPos(10, Y + 5, 14), sphereB = new BlockPos(14, Y + 5, 14);
        final BlockPos copyA = new BlockPos(20, Y, 10), copyB = new BlockPos(22, Y, 11);
        final BlockPos pasteAt = new BlockPos(30, Y - 1, 12);
        final BlockPos stackA = new BlockPos(36, Y, 10), stackB = new BlockPos(36, Y + 1, 10);
        final BlockPos mirrorCentre = new BlockPos(50, Y - 1, 12), mirrorClick = new BlockPos(47, Y - 1, 12);

        // ---------- creative ----------
        s.add(new Stage("creative setup", p -> {
            // Other scenarios of the same run (render showcase, camera barriers) may have built here: start from air.
            final ServerLevel level = p.serverLevel();
            final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int y = Y; y <= Y + 12; y++) {
                for (int z = 8; z <= 44; z++) {
                    for (int x = -34; x <= 56; x++) {
                        if (!level.getBlockState(m.set(x, y, z)).isAir()) level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
            p.setGameMode(GameType.CREATIVE);
            p.getInventory().clearContent();
            p.getInventory().setItem(0, new ItemStack(Items.STONE, 64));
            p.getInventory().selected = 0;
        }, p -> p.isCreative() ? null : "not in creative"));

        s.add(new Stage("fill 5x5x5", p -> apply(p, BuildModes.FILL, params(BuildModes.FILL), Direction.UP, fillA, fillB),
            p -> expect(count(p, fillA, fillB, Blocks.STONE), 125, "stone in the box")));

        s.add(new Stage("hollow 5x5x5", p -> apply(p, BuildModes.HOLLOW_BOX, params(BuildModes.HOLLOW_BOX), Direction.UP, hollowA, hollowB),
            p -> firstProblem(expect(count(p, hollowA, hollowB, Blocks.STONE), 98, "shell blocks"),
                expect(count(p, hollowA.offset(1, 1, 1), hollowB.offset(-1, -1, -1), Blocks.AIR), 27, "air inside"))));

        s.add(new Stage("walls 5x5x5", p -> apply(p, BuildModes.WALLS, params(BuildModes.WALLS), Direction.UP, wallsA, wallsB),
            p -> expect(count(p, wallsA, wallsB, Blocks.STONE), 80, "wall blocks")));

        s.add(new Stage("line 10", p -> apply(p, BuildModes.LINE, params(BuildModes.LINE), Direction.UP, lineA, lineB),
            p -> expect(count(p, lineA, lineB, Blocks.STONE), 10, "line blocks")));

        s.add(new Stage("sphere r4", p -> apply(p, BuildModes.SPHERE, params(BuildModes.SPHERE), Direction.UP, sphereC, sphereB),
            p -> expect(count(p, sphereC.offset(-4, -4, -4), sphereC.offset(4, 4, 4), Blocks.STONE), sphereCount(4), "sphere blocks")));

        s.add(new Stage("replace stone -> planks", p -> {
            p.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 64));
            apply(p, BuildModes.REPLACE_BLOCKS, params(BuildModes.REPLACE_BLOCKS), Direction.UP, fillA, fillB);
        }, p -> firstProblem(expect(count(p, fillA, fillB, Blocks.OAK_PLANKS), 125, "planks"), expect(count(p, fillA, fillB, Blocks.STONE), 0, "stone left"))));

        // A left-click (breaking) Fill: only the geometry decides. Holding the very block the box is made of, with the
        // default replace policy (air and plants), used to break nothing: both filtered every solid position out.
        s.add(new Stage("breaking fill, held block, default replace", p -> apply(p, BuildModes.FILL, params(BuildModes.FILL), Direction.UP, true, fillA, fillB),
            p -> expect(nonAir(p, fillA, fillB), 0, "blocks left")));

        // ... and with an empty hand (the geometry runs with a stone stand-in, which used to skip stone as already there).
        final BlockPos emptyB = fillA.offset(2, 2, 2);
        s.add(new Stage("breaking fill, empty hand", p -> {
            for (final BlockPos pos : BlockPos.betweenClosed(fillA, emptyB)) p.serverLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            final int slot = p.getInventory().selected;
            final ItemStack held = p.getInventory().getItem(slot);
            p.getInventory().setItem(slot, ItemStack.EMPTY);
            apply(p, BuildModes.FILL, params(BuildModes.FILL), Direction.UP, true, fillA, emptyB);
            p.getInventory().setItem(slot, held);          // planned at once; later stages keep their block
        }, p -> expect(nonAir(p, fillA, emptyB), 0, "blocks left")));

        s.add(new Stage("clear hollow box", p -> apply(p, BuildModes.CLEAR, params(BuildModes.CLEAR), Direction.UP, hollowA, hollowB),
            p -> expect(nonAir(p, hollowA, hollowB), 0, "blocks left")));

        s.add(new Stage("copy L-shape", p -> {
            final ServerLevel level = p.serverLevel();
            level.setBlockAndUpdate(copyA, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(copyA.east(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(copyA.east(2), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(copyA.south(), Blocks.GOLD_BLOCK.defaultBlockState());
            apply(p, BuildModes.COPY, params(BuildModes.COPY), Direction.UP, copyA, copyB);
        }, p -> {
            final Clipboard clip = OpsServer.clipboard(p);
            return clip == null ? "no clipboard" : expect(clip.blockCount(), 4, "copied blocks");
        }));

        s.add(new Stage("paste rotated 90", p -> {
            final ModeParams params = params(BuildModes.PASTE).set("rotation", "90");
            apply(p, BuildModes.PASTE, params, Direction.UP, pasteAt);
        }, p -> {
            final ServerLevel level = p.serverLevel();
            return firstProblem(
                is(level, new BlockPos(30, Y, 11), Blocks.STONE), is(level, new BlockPos(30, Y, 12), Blocks.STONE),
                is(level, new BlockPos(30, Y, 13), Blocks.STONE), is(level, new BlockPos(29, Y, 11), Blocks.GOLD_BLOCK),
                expect(nonAir(p, new BlockPos(28, Y, 10), new BlockPos(32, Y, 14)), 4, "pasted blocks"));
        }));

        s.add(new Stage("stack x3", p -> {
            p.serverLevel().setBlockAndUpdate(stackA, Blocks.STONE.defaultBlockState());
            p.serverLevel().setBlockAndUpdate(stackB, Blocks.STONE.defaultBlockState());
            final ModeParams params = params(BuildModes.STACK).set("count", 3).set("spacing", 1).set("direction", "E");
            apply(p, BuildModes.STACK, params, Direction.UP, stackA, stackB);
        }, p -> expect(count(p, stackA.east(2), stackB.east(6), Blocks.STONE), 6, "stacked copies")));

        s.add(new Stage("mirror a placement", p -> {
            p.getInventory().setItem(0, new ItemStack(Items.STONE, 64));
            Symmetry.set(p, BuildModes.MIRROR_MODE.id(), params(BuildModes.MIRROR_MODE).set("axis", "X").toTag(), mirrorCentre);
            final BlockHitResult hit = new BlockHitResult(new Vec3(mirrorClick.getX() + 0.5, mirrorClick.getY() + 1.0, mirrorClick.getZ() + 0.5),
                Direction.UP, mirrorClick, false);
            click(p, hit);
        }, p -> firstProblem(is(p.serverLevel(), mirrorClick.above(), Blocks.STONE),
            is(p.serverLevel(), new BlockPos(53, Y, 12), Blocks.STONE))));

        s.add(new Stage("mirror a break", p -> {
            // Re-armed in the same tick: the client's mode controller switches a symmetry it did not ask for off again.
            Symmetry.set(p, BuildModes.MIRROR_MODE.id(), params(BuildModes.MIRROR_MODE).set("axis", "X").toTag(), mirrorCentre);
            p.gameMode.destroyBlock(mirrorClick.above());
        },
            p -> {
                final String problem = firstProblem(is(p.serverLevel(), mirrorClick.above(), Blocks.AIR),
                    is(p.serverLevel(), new BlockPos(53, Y, 12), Blocks.AIR));
                Symmetry.set(p, "", new net.minecraft.nbt.CompoundTag(), BlockPos.ZERO);
                return problem;
            }));

        s.add(new Stage("undo 1 (stack)", OpsServer::undo,
            p -> firstProblem(expect(count(p, stackA.east(2), stackB.east(6), Blocks.STONE), 0, "stacked copies left"),
                expect(OpsServer.redoCount(p), 1, "redo depth"))));

        s.add(new Stage("undo 2 (paste)", OpsServer::undo,
            p -> expect(nonAir(p, new BlockPos(28, Y, 10), new BlockPos(32, Y, 14)), 0, "pasted blocks left")));

        s.add(new Stage("redo 1 (paste)", OpsServer::redo,
            p -> firstProblem(is(p.serverLevel(), new BlockPos(29, Y, 11), Blocks.GOLD_BLOCK),
                expect(nonAir(p, new BlockPos(28, Y, 10), new BlockPos(32, Y, 14)), 4, "pasted blocks"),
                expect(OpsServer.redoCount(p), 1, "redo depth"))));

        // ---------- survival ----------
        final BlockPos sA = new BlockPos(-30, Y, 24), sB = new BlockPos(-28, Y + 2, 26);
        final BlockPos bigA = new BlockPos(-20, Y, 24), bigB = new BlockPos(-16, Y + 4, 28);
        final BlockPos noneA = new BlockPos(-10, Y, 24), noneB = new BlockPos(-9, Y + 1, 25);
        s.add(new Stage("survival setup", p -> {
            p.setGameMode(GameType.SURVIVAL);
            p.getInventory().clearContent();
            p.getInventory().setItem(0, new ItemStack(Items.STONE, 64));
            p.getInventory().selected = 0;
        }, p -> p.isCreative() ? "still creative" : null));

        s.add(new Stage("survival fill 3x3x3 pays 27", p -> apply(p, BuildModes.FILL, params(BuildModes.FILL), Direction.UP, sA, sB),
            p -> firstProblem(expect(count(p, sA, sB, Blocks.STONE), 27, "blocks"), expect(items(p, Items.STONE), 37, "stone in inventory"))));

        s.add(new Stage("survival clear drops cobblestone", p -> apply(p, BuildModes.CLEAR, params(BuildModes.CLEAR), Direction.UP, sA, sB),
            p -> firstProblem(expect(nonAir(p, sA, sB), 0, "blocks left"), expect(items(p, Items.COBBLESTONE), 27, "cobblestone"),
                expect(items(p, Items.STONE), 37, "stone"))));

        s.add(new Stage("survival undo clear charges drops", OpsServer::undo,
            p -> firstProblem(expect(count(p, sA, sB, Blocks.STONE), 27, "blocks back"), expect(items(p, Items.COBBLESTONE), 0, "cobblestone"))));

        s.add(new Stage("survival undo fill refunds", OpsServer::undo,
            p -> firstProblem(expect(nonAir(p, sA, sB), 0, "blocks left"), expect(items(p, Items.STONE), 64, "stone"))));

        s.add(new Stage("survival redo fill charges", OpsServer::redo,
            p -> firstProblem(expect(count(p, sA, sB, Blocks.STONE), 27, "blocks"), expect(items(p, Items.STONE), 37, "stone"))));

        s.add(new Stage("survival place what you can", p -> apply(p, BuildModes.FILL, params(BuildModes.FILL), Direction.UP, bigA, bigB),
            p -> {
                final OpResult r = OpsServer.lastResult(p);
                return firstProblem(expect(count(p, bigA, bigB, Blocks.STONE), 37, "blocks"), expect(items(p, Items.STONE), 0, "stone"),
                    r != null && r.messageKey().endsWith(".short") ? null : "result should report missing materials");
            }));

        s.add(new Stage("survival refuses without blocks", p -> apply(p, BuildModes.FILL, params(BuildModes.FILL), Direction.UP, noneA, noneB),
            p -> {
                final OpResult r = OpsServer.lastResult(p);
                return firstProblem(expect(nonAir(p, noneA, noneB), 0, "blocks"),
                    r != null && r.messageKey().startsWith("slate_building.plan.") ? null : "expected a refusal, got " + r);
            }));

        final BlockPos liftA = new BlockPos(-10, Y, 30), liftB = liftA.above(), liftTo = new BlockPos(-6, Y - 1, 30);
        s.add(new Stage("survival move pays itself", p -> {
            p.serverLevel().setBlockAndUpdate(liftA, Blocks.STONE.defaultBlockState());
            p.serverLevel().setBlockAndUpdate(liftB, Blocks.GOLD_BLOCK.defaultBlockState());
            apply(p, BuildModes.MOVE, params(BuildModes.MOVE), Direction.UP, liftA, liftB, liftTo);
        }, p -> firstProblem(is(p.serverLevel(), liftTo.above(), Blocks.STONE), is(p.serverLevel(), liftTo.above(2), Blocks.GOLD_BLOCK),
            expect(nonAir(p, liftA, liftB), 0, "blocks left at the source"), expect(items(p, Items.STONE), 0, "stone in inventory"),
            expect(items(p, Items.GOLD_BLOCK), 0, "gold in inventory"))));

        // Regressions of the economy review: what a copy costs, what undo refunds, what a failed move gives.
        final BlockPos cpA = new BlockPos(2, Y, 26), cpB = new BlockPos(4, Y + 1, 26), pasteClick = new BlockPos(10, Y - 1, 26);
        s.add(new Stage("survival copy candles, cauldron, pot", p -> {
            final ServerLevel level = p.serverLevel();
            level.setBlockAndUpdate(cpA, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(cpA.above(), Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 4));
            level.setBlockAndUpdate(cpA.east(), Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
            level.setBlockAndUpdate(cpA.east(2), Blocks.POTTED_DANDELION.defaultBlockState());
            apply(p, BuildModes.COPY, params(BuildModes.COPY), Direction.UP, cpA, cpB);
        }, p -> {
            final Clipboard clip = OpsServer.clipboard(p);
            return clip == null ? "no clipboard" : expect(clip.blockCount(), 4, "copied blocks");
        }));

        s.add(new Stage("survival paste pays 4 candles, skips item-less blocks", p -> {
            p.getInventory().clearContent();
            p.getInventory().setItem(0, new ItemStack(Items.STONE, 1));
            p.getInventory().setItem(1, new ItemStack(Items.CANDLE, 8));
            p.getInventory().selected = 0;
            apply(p, BuildModes.PASTE, params(BuildModes.PASTE), Direction.UP, pasteClick);
        }, p -> {
            final ServerLevel level = p.serverLevel();
            final BlockPos o = new BlockPos(9, Y, 26);
            final BlockState candle = level.getBlockState(o.above());
            return firstProblem(is(level, o, Blocks.STONE), is(level, o.above(), Blocks.CANDLE),
                candle.is(Blocks.CANDLE) ? expect(candle.getValue(CandleBlock.CANDLES), 4, "pasted candles") : null,
                is(level, o.east(), Blocks.AIR), is(level, o.east(2), Blocks.AIR),
                expect(items(p, Items.CANDLE), 4, "candles left (4 paid)"), expect(items(p, Items.STONE), 0, "stone left"));
        }));

        final BlockPos cakeAt = new BlockPos(14, Y, 26);
        s.add(new Stage("survival fill places a cake", p -> {
            p.getInventory().clearContent();
            p.getInventory().setItem(0, new ItemStack(Items.CAKE, 1));
            p.getInventory().selected = 0;
            apply(p, BuildModes.FILL, params(BuildModes.FILL), Direction.UP, cakeAt, cakeAt);
        }, p -> firstProblem(is(p.serverLevel(), cakeAt, Blocks.CAKE), expect(items(p, Items.CAKE), 0, "cakes left"))));

        s.add(new Stage("survival undo leaves an eaten cake alone", p -> {
            p.serverLevel().setBlockAndUpdate(cakeAt, Blocks.CAKE.defaultBlockState().setValue(CakeBlock.BITES, 6));
            OpsServer.undo(p);
        }, p -> firstProblem(is(p.serverLevel(), cakeAt, Blocks.CAKE), expect(items(p, Items.CAKE), 0, "cakes refunded"))));

        final BlockPos budAt = new BlockPos(16, Y, 30), budTo = new BlockPos(20, Y - 1, 30);
        s.add(new Stage("survival move that cannot land gives loot, not the block", p -> {
            final ServerLevel level = p.serverLevel();
            level.setBlockAndUpdate(budAt, Blocks.BUDDING_AMETHYST.defaultBlockState());
            final ArmorStand stand = new ArmorStand(level, budTo.getX() + 0.5, budTo.getY() + 1, budTo.getZ() + 0.5);
            level.addFreshEntity(stand);
            apply(p, BuildModes.MOVE, params(BuildModes.MOVE), Direction.UP, budAt, budAt, budTo);
        }, p -> {
            final ServerLevel level = p.serverLevel();
            for (final ArmorStand a : level.getEntitiesOfClass(ArmorStand.class, new AABB(budTo.above()).inflate(1))) a.discard();
            return firstProblem(is(level, budAt, Blocks.AIR), is(level, budTo.above(), Blocks.AIR),
                expect(items(p, Items.BUDDING_AMETHYST), 0, "budding amethyst items"));
        }));

        s.add(new Stage("survival undo puts the unlanded block back", OpsServer::undo,
            p -> firstProblem(is(p.serverLevel(), budAt, Blocks.BUDDING_AMETHYST), expect(items(p, Items.BUDDING_AMETHYST), 0, "budding amethyst items"))));

        // Reshape changes blocks in place: natural stone (drops cobblestone) must not come back as stone stairs.
        final BlockPos rsA = new BlockPos(24, Y, 30), rsB = rsA.east();
        s.add(new Stage("survival reshape skips natural stone, reshapes stone bricks", p -> {
            final ServerLevel level = p.serverLevel();
            level.setBlockAndUpdate(rsA, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(rsB, Blocks.STONE_BRICKS.defaultBlockState());
            p.getInventory().clearContent();
            p.getInventory().setItem(0, new ItemStack(Items.STONE_BRICK_STAIRS, 1));
            p.getInventory().selected = 0;
            apply(p, BuildModes.RESHAPE, params(BuildModes.RESHAPE), Direction.UP, rsA, rsB);
        }, p -> {
            final OpResult r = OpsServer.lastResult(p);
            return firstProblem(is(p.serverLevel(), rsA, Blocks.STONE), is(p.serverLevel(), rsB, Blocks.STONE_BRICK_STAIRS),
                r != null && r.skipped() >= 1 ? null : "the stone should count as skipped, got " + r,
                expect(items(p, Items.COBBLESTONE) + items(p, Items.STONE), 0, "stone / cobblestone handed out"));
        }));

        s.add(new Stage("back to creative", p -> {
            p.setGameMode(GameType.CREATIVE);
            p.getInventory().setItem(0, new ItemStack(Items.STONE, 64));
        }, p -> p.isCreative() ? null : "not in creative"));

        // ---------- more creative modes ----------
        final BlockPos outA = new BlockPos(-30, Y, 36), outB = new BlockPos(-26, Y + 4, 40);
        s.add(new Stage("outline 5x5x5", p -> apply(p, BuildModes.OUTLINE, params(BuildModes.OUTLINE), Direction.UP, outA, outB),
            p -> expect(count(p, outA, outB, Blocks.STONE), 44, "edge blocks")));

        final BlockPos cylA = new BlockPos(-18, Y, 38), cylB = new BlockPos(-15, Y + 3, 38);
        s.add(new Stage("cylinder r3 h4", p -> apply(p, BuildModes.CYLINDER, params(BuildModes.CYLINDER), Direction.UP, cylA, cylB),
            p -> expect(count(p, cylA.offset(-3, 0, -3), cylA.offset(3, 3, 3), Blocks.STONE), 4 * discCount(3), "cylinder blocks")));

        final BlockPos rowA = new BlockPos(-8, Y, 38);
        s.add(new Stage("extend a row upwards", p -> {
            for (int i = 0; i < 3; i++) p.serverLevel().setBlockAndUpdate(rowA.east(i), Blocks.STONE.defaultBlockState());
            apply(p, BuildModes.EXTEND, params(BuildModes.EXTEND), Direction.UP, rowA.east());
        }, p -> expect(count(p, rowA.above(), rowA.above().east(2), Blocks.STONE), 3, "extended blocks")));

        final BlockPos ovA = new BlockPos(0, Y - 1, 36), ovB = new BlockPos(4, Y - 1, 40);
        s.add(new Stage("overlay planks on grass", p -> {
            p.getInventory().setItem(0, new ItemStack(Items.OAK_PLANKS, 64));
            apply(p, BuildModes.OVERLAY, params(BuildModes.OVERLAY), Direction.UP, ovA, ovB);
        }, p -> expect(count(p, ovA.above(), ovB.above(), Blocks.OAK_PLANKS), 25, "overlay blocks")));

        final BlockPos mvA = new BlockPos(10, Y, 36), mvB = mvA.above(), mvTo = new BlockPos(14, Y - 1, 36);
        s.add(new Stage("move a pillar", p -> {
            p.serverLevel().setBlockAndUpdate(mvA, Blocks.GOLD_BLOCK.defaultBlockState());
            p.serverLevel().setBlockAndUpdate(mvB, Blocks.STONE.defaultBlockState());
            apply(p, BuildModes.MOVE, params(BuildModes.MOVE), Direction.UP, mvA, mvB, mvTo);
        }, p -> firstProblem(is(p.serverLevel(), mvTo.above(), Blocks.GOLD_BLOCK), is(p.serverLevel(), mvTo.above(2), Blocks.STONE),
            expect(nonAir(p, mvA, mvB), 0, "blocks left at the source"))));

        final BlockPos cutAt = new BlockPos(18, Y - 1, 36);
        s.add(new Stage("cut the pillar", p -> apply(p, BuildModes.CUT, params(BuildModes.CUT), Direction.UP, mvTo.above(), mvTo.above(2)),
            p -> {
                final Clipboard clip = OpsServer.clipboard(p);
                return firstProblem(expect(nonAir(p, mvTo.above(), mvTo.above(2)), 0, "blocks left"),
                    clip == null ? "no clipboard" : expect(clip.blockCount(), 2, "clipboard blocks"));
            }));

        s.add(new Stage("paste the cut", p -> apply(p, BuildModes.PASTE, params(BuildModes.PASTE), Direction.UP, cutAt),
            p -> firstProblem(is(p.serverLevel(), cutAt.above(), Blocks.GOLD_BLOCK), is(p.serverLevel(), cutAt.above(2), Blocks.STONE))));

        final BlockPos chA = new BlockPos(22, Y, 36), chB = new BlockPos(24, Y, 38);
        s.add(new Stage("hotbar checker fill", p -> {
            p.getInventory().setItem(0, new ItemStack(Items.STONE, 64));
            p.getInventory().setItem(1, new ItemStack(Items.OAK_PLANKS, 64));
            apply(p, BuildModes.FILL, params(BuildModes.FILL).set("palette", "HOTBAR_CHECKER"), Direction.UP, chA, chB);
        }, p -> {
            for (final BlockPos pos : BlockPos.betweenClosed(chA, chB)) {
                final Block want = Math.floorMod(pos.getX() + pos.getY() + pos.getZ(), 2) == 0 ? Blocks.STONE : Blocks.OAK_PLANKS;
                final String problem = is(p.serverLevel(), pos, want);
                if (problem != null) return problem;
            }
            return null;
        }));

        final BlockPos radC = new BlockPos(40, Y - 1, 40), radClick = new BlockPos(43, Y - 1, 40);
        s.add(new Stage("radial x4 placement", p -> {
            Symmetry.set(p, BuildModes.RADIAL.id(), params(BuildModes.RADIAL).set("slices", 4).toTag(), radC);
            final BlockHitResult hit = new BlockHitResult(new Vec3(radClick.getX() + 0.5, radClick.getY() + 1.0, radClick.getZ() + 0.5),
                Direction.UP, radClick, false);
            click(p, hit);
        }, p -> {
            final ServerLevel level = p.serverLevel();
            final String problem = firstProblem(is(level, new BlockPos(43, Y, 40), Blocks.STONE), is(level, new BlockPos(40, Y, 43), Blocks.STONE),
                is(level, new BlockPos(37, Y, 40), Blocks.STONE), is(level, new BlockPos(40, Y, 37), Blocks.STONE));
            Symmetry.set(p, "", new net.minecraft.nbt.CompoundTag(), BlockPos.ZERO);
            return problem;
        }));

        // Planners never load chunks: a stack reaching ~640 blocks east leaves the far copies out instead.
        final BlockPos stFar = new BlockPos(40, Y, 20), stFarB = new BlockPos(71, Y, 20), farCopy = new BlockPos(40 + 40 * 16, Y, 20);
        s.add(new Stage("stack does not load far chunks", p -> {
            final ServerLevel level = p.serverLevel();
            level.setBlockAndUpdate(stFar, Blocks.STONE.defaultBlockState());
            farChunkWasLoaded = level.hasChunk(farCopy.getX() >> 4, farCopy.getZ() >> 4);
            final ModeParams params = params(BuildModes.STACK).set("count", 16).set("spacing", 8).set("direction", "E");
            apply(p, BuildModes.STACK, params, Direction.UP, stFar, stFarB);
        }, p -> {
            final ServerLevel level = p.serverLevel();
            final OpResult r = OpsServer.lastResult(p);
            return firstProblem(r != null && !OpMessages.isError(r) ? null : "stack failed: " + r,
                r != null && r.placed() > 0 ? null : "no copy placed",
                farChunkWasLoaded || !level.hasChunk(farCopy.getX() >> 4, farCopy.getZ() >> 4) ? null : "the far chunk got loaded");
        }));

        // The volume limit refuses before anything is walked (here: creative cap lowered to 100 for one fill of 125).
        final BlockPos volA = new BlockPos(24, Y, 24), volB = new BlockPos(28, Y + 4, 28);
        s.add(new Stage("volume limit refuses an oversized fill", p -> {
            final ServerOps ops = BuildingServerSettings.local().ops();
            savedCreativeMax = ops.creativeMaxVolume;
            ops.creativeMaxVolume = 100;
            apply(p, BuildModes.FILL, params(BuildModes.FILL), Direction.UP, volA, volB);
        }, p -> {
            restoreCreativeMax();
            final OpResult r = OpsServer.lastResult(p);
            return firstProblem(r != null && r.messageKey().equals("slate_building.plan.too_many") ? null : "expected too_many, got " + r,
                expect(nonAir(p, volA, volB), 0, "blocks placed"));
        }));

        s.add(new Stage("undo twice in one tick is throttled", p -> {
            OpsServer.undo(p);
            OpsServer.undo(p);
        }, p -> {
            final OpResult r = OpsServer.lastResult(p);
            return r != null && r.messageKey().equals("slate_building.error.too_fast") ? null : "expected too_fast, got " + r;
        }));
        return s;
    }

    private static void restoreCreativeMax() {
        if (savedCreativeMax < 0) return;
        BuildingServerSettings.local().ops().creativeMaxVolume = savedCreativeMax;
        savedCreativeMax = -1;
    }

    /** A right click on a block through the server's own handler (so NeoForge's snapshot capture and the symmetry flush run). */
    private static void click(final ServerPlayer p, final BlockHitResult hit) {
        p.gameMode.useItemOn(p, p.serverLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
    }

    /** Positions of a disc of radius {@code r} under the "radius + ½" rule, counted independently of the planner. */
    private static int discCount(final int r) {
        final double limit = (r + 0.5) * (r + 0.5);
        int n = 0;
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) if (x * x + z * z < limit) n++;
        return n;
    }

    // ---- helpers ----

    private static ModeParams params(final BuildMode mode) {
        return ModeParams.defaults(mode);
    }

    /** Stands the player a few blocks in front of (north of) the site, then applies like an {@code ApplyOp}. */
    private static void apply(final ServerPlayer p, final BuildMode mode, final ModeParams params, final Direction face, final BlockPos... anchors) {
        apply(p, mode, params, face, false, anchors);
    }

    /** {@code destructive}: a left-click selection, the plan breaks what it would have placed. */
    private static void apply(final ServerPlayer p, final BuildMode mode, final ModeParams params, final Direction face, final boolean destructive,
                              final BlockPos... anchors) {
        final BlockPos a = anchors[0];
        p.teleportTo(p.serverLevel(), a.getX() + 0.5, Y, Math.min(a.getZ(), anchors[anchors.length - 1].getZ()) - 5.5, 0F, 30F);
        OpsServer.apply(p, mode.id(), params.toTag(), List.of(anchors), face, p.getInventory().selected, destructive);
    }

    private static int count(final ServerPlayer p, final BlockPos a, final BlockPos b, final Block block) {
        int n = 0;
        for (final BlockPos pos : BlockPos.betweenClosed(a, b)) if (p.serverLevel().getBlockState(pos).is(block)) n++;
        return n;
    }

    private static int nonAir(final ServerPlayer p, final BlockPos a, final BlockPos b) {
        int n = 0;
        for (final BlockPos pos : BlockPos.betweenClosed(a, b)) if (!p.serverLevel().getBlockState(pos).isAir()) n++;
        return n;
    }

    private static int items(final ServerPlayer p, final Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            final ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    /** Positions of a solid ball of radius {@code r} under the "radius + ½" rule, counted independently of the planner. */
    private static int sphereCount(final int r) {
        final double limit = (r + 0.5) * (r + 0.5);
        int n = 0;
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) if (x * x + y * y + z * z < limit) n++;
        return n;
    }

    private static @Nullable String expect(final int actual, final int expected, final String what) {
        return actual == expected ? null : what + ": expected " + expected + ", got " + actual;
    }

    private static @Nullable String is(final ServerLevel level, final BlockPos pos, final Block block) {
        return level.getBlockState(pos).is(block) ? null : pos.toShortString() + " should be " + block.getName().getString() + ", is " + level.getBlockState(pos);
    }

    private static @Nullable String firstProblem(final @Nullable String... problems) {
        for (final String p : problems) if (p != null) return p;
        return null;
    }

    private OpsSelfTest() {}
}
