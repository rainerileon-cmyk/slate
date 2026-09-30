package dev.fallingcloud.slate.building.client.mode;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.client.BuildingHarness;
import dev.fallingcloud.slate.building.client.input.BuildInput;
import dev.fallingcloud.slate.building.client.render.GhostRenderer;
import dev.fallingcloud.slate.building.compat.SubLevels;
import dev.fallingcloud.slate.building.ops.BuildModes;
import java.util.List;
import java.util.Objects;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Dev harness scenario for building on a Sable sub-level (run with {@code -PbuildingHarness=sable}; Sable has to be
 * in the run's mods folder, without it the first check says so and the rest fails). A deck of stone bricks is built
 * in the air and made a sub-level, which is then turned and tipped so that nothing of it lies along the world's axes;
 * physics is paused, so it stays where it is put. Every click is a real one: the crosshair is turned to a point of
 * the deck, and what the game and the mode make of it is checked ({@code [BuildingHarness] CHECK ok|FAILED ...}) and
 * photographed. Then physics is let go, to see the preview go with the falling deck, and a block is moved off the
 * deck into the world, which is one plan in two spaces.
 */
final class SableHarness {

    /** The deck's middle block as its plot has it: known once the crosshair has found it. */
    private static @Nullable BlockPos deck;
    private static long mark;

    static void register() {
        BuildingHarness.register("sable", SableHarness::sable);
    }

    private static void sable(final BuildingHarness.Script s) {
        ModeHarness.stage(s);
        s.log("sable: a deck in the air becomes a sub-level")
            .run(() -> {
                deck = null;
                check(SubLevels.present(), "Sable answers through its companion library");
            })
            .command("sable paused true")
            .command("fill -2 -57 4 2 -57 8 minecraft:stone_bricks")
            .command("setblock 2 -56 8 minecraft:gold_block")           // marks the deck's south-east corner
            .command("tp @s 0.5 -54.5 4.5 0 50")
            .wait(10)
            // For comparison with the ghost on the sub-level: the same ghost while the deck is still of the world.
            .command("item replace entity @s hotbar.0 with minecraft:stone_brick_stairs 64")
            .run(() -> ModeHarness.aim(new Vec3(-0.5, -56.02, 5.5)))
            .wait(25)
            .screenshot("sable-ghost-world")
            .command("sable assemble area -2 -57 4 2 -56 8")
            .wait(40)
            .run(() -> ModeHarness.aim(new Vec3(0.5, -56.02, 6.5)))
            .wait(6)
            .run(() -> {
                final BlockHitResult hit = hit();
                final ClientLevel level = level();
                check(hit != null && SubLevels.renderAt(hit.getBlockPos()) != null,
                    "the crosshair finds the deck in a sub-level (" + (hit == null ? "nothing" : hit.getBlockPos().toShortString()) + ")");
                if (hit == null) return;
                deck = hit.getBlockPos();
                check(level.getBlockState(deck).is(Blocks.STONE_BRICKS) && level.getBlockState(new BlockPos(0, -57, 6)).isAir(),
                    "its blocks are in the plot and no longer in the world");
                check(level.getBlockState(on(2, 1, 2)).is(Blocks.GOLD_BLOCK), "the plot keeps the deck's own axes");
            })
            .command("sable teleport @e 0.5 -56.5 6.5 60 14")
            .wait(40)
            .run(() -> {
                final SubLevels.Pose pose = Objects.requireNonNull(SubLevels.renderAt(on(0, 0, 0)), "no pose");
                final Vec3 south = pose.dirToWorld(new Vec3(0, 0, 1));
                SlateBuilding.LOGGER.info("[BuildingHarness] sable: the deck's middle is at {}, its south points to {}",
                    pose.toWorld(Vec3.atCenterOf(on(0, 0, 0))), south);
                check(Math.abs(south.z) < 0.9 && Math.abs(south.y) > 0.05, "the deck is turned and tipped");
                check(SubLevels.same(level(), on(0, 0, 0), on(2, 1, 2)) && !SubLevels.same(level(), on(0, 0, 0), new BlockPos(0, -61, 6)),
                    "two blocks of the deck are in one space, the floor of the world in another");
            });

        s.log("sable: the placement ghost, and a placement")
            .command("item replace entity @s hotbar.0 with minecraft:stone_brick_stairs 64")
            .run(() -> aimAt(on(-1, 0, -1), Direction.UP))
            .wait(25)
            .run(() -> {
                final BlockHitResult hit = hit();
                check(hit != null && hit.getBlockPos().equals(on(-1, 0, -1)) && hit.getDirection() == Direction.UP,
                    "the crosshair is on the deck block it was turned to (" + describe(hit) + ")");
                if (hit == null) return;
                // The game's own point of the hit against the bridge's arithmetic: the view line, taken into the plot.
                final SubLevels.Pose pose = Objects.requireNonNull(SubLevels.renderAt(hit.getBlockPos()), "no pose");
                final Vec3 eye = pose.toLocal(player().getEyePosition()), look = pose.dirToLocal(player().getLookAngle());
                final Vec3 met = eye.add(look.scale((on(-1, 1, -1).getY() - eye.y) / look.y));
                final Vec3 point = hit.getLocation();
                final double plot = point.distanceTo(met), world = point.distanceTo(pose.toWorld(met));
                check(Math.min(plot, world) < 0.05, "the bridge puts the hit where the game does (" + String.format("%.3f", Math.min(plot, world))
                    + " blocks apart; the game gives it in the " + (plot < world ? "plot" : "world") + ")");
            })
            .screenshot("sable-ghost")
            // The same through the path a shader pack takes.
            .run(() -> GhostRenderer.debugFallbackPath(true))
            .wait(10)
            .screenshot("sable-ghost-fallback")
            .run(() -> GhostRenderer.debugFallbackPath(false))
            .run(() -> {
                final Minecraft mc = Minecraft.getInstance();
                final BlockHitResult hit = hit();
                if (hit == null || mc.gameMode == null || mc.player == null) { check(false, "there is a block to place on"); return; }
                final InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                check(result.consumesAction(), "the placement is accepted (" + result + ")");
            })
            .wait(20)
            .run(() -> {
                final LocalPlayer player = player();
                final BlockState placed = level().getBlockState(on(-1, 1, -1));
                check(placed.is(Blocks.STONE_BRICK_STAIRS), "the stairs stand on the deck, in its plot (" + placed + ")");
                if (!placed.hasProperty(StairBlock.FACING)) return;
                final Direction ours = ModeTarget.horizontalFacing(player, on(-1, 1, -1));
                check(placed.getValue(StairBlock.FACING) == ours, "they face where the player looks as the deck has it (stairs "
                    + placed.getValue(StairBlock.FACING) + ", the deck's " + ours + ", the world's " + player.getDirection() + ")");
            })
            .screenshot("sable-placed");

        s.log("sable: fill, both corners on the deck")
            .command("item replace entity @s hotbar.0 with minecraft:oak_planks 64")
            .run(() -> check(ClientModeState.activate(BuildModes.FILL), "fill activates"))
            .run(() -> aimAt(on(-2, 0, -2), Direction.UP))
            .wait(8)
            .run(() -> check(BuildInput.fireUse(), "the first click is the mode's"))
            .run(() -> check(ClientModeState.pending() == ClientModeState.Pending.FIRST_ANCHOR
                && ClientModeState.anchors().equals(List.of(on(-2, 1, -2))), "corner A is the block on the deck, in its plot (" + anchors() + ")"))
            .run(() -> aimAt(on(1, 0, 0), Direction.UP))
            .wait(30)
            .run(() -> {
                ModeHarness.state("sable following");
                final ModeTarget t = ModeController.INSTANCE.target();
                check(t != null && !t.air() && t.pos().equals(on(1, 1, 0)), "the box follows the crosshair over the deck");
            })
            .screenshot("sable-following")
            .run(() -> check(BuildInput.fireUse(), "the second click is the mode's"))
            .run(() -> check(ClientModeState.pending() == ClientModeState.Pending.SELECTED
                && ClientModeState.anchors().equals(List.of(on(-2, 1, -2), on(1, 1, 0))), "corner B is on the deck too (" + anchors() + ")"))
            .wait(30)
            .run(() -> {
                ModeHarness.state("sable selected");
                ModeHarness.checkSize(4, 1, 3);
            })
            .screenshot("sable-preview")
            .run(() -> GhostRenderer.debugFallbackPath(true))
            .wait(10)
            .screenshot("sable-preview-fallback")
            .run(() -> GhostRenderer.debugFallbackPath(false))
            .run(() -> ModeController.INSTANCE.confirm())
            .waitUntil(() -> ClientModeState.pending() != ClientModeState.Pending.APPLYING, 240)
            .wait(40)
            .run(() -> {
                ModeHarness.state("sable applied");
                final ClientLevel level = level();
                check(level.getBlockState(on(-2, 1, -2)).is(Blocks.OAK_PLANKS) && level.getBlockState(on(1, 1, 0)).is(Blocks.OAK_PLANKS)
                    && level.getBlockState(on(0, 1, -1)).is(Blocks.OAK_PLANKS), "the planks lie on the deck");
                check(level.getBlockState(on(-1, 1, -1)).is(Blocks.STONE_BRICK_STAIRS), "and the stairs were left alone");
            })
            .screenshot("sable-applied")
            .run(() -> ModeController.INSTANCE.undo())
            .wait(40)
            .run(() -> check(level().getBlockState(on(-2, 1, -2)).isAir() && level().getBlockState(on(1, 1, 0)).isAir()
                && level().getBlockState(on(-1, 1, -1)).is(Blocks.STONE_BRICK_STAIRS), "undo takes the planks off the deck again"))
            .run(() -> ModeController.INSTANCE.redo())
            .wait(40)
            .run(() -> check(level().getBlockState(on(-2, 1, -2)).is(Blocks.OAK_PLANKS) && level().getBlockState(on(1, 1, 0)).is(Blocks.OAK_PLANKS),
                "and redo lays them back"));

        s.log("sable: the second corner in the air, and a block of the world in the way")
            .command("setblock 0 -53 8 minecraft:red_wool")
            .run(() -> aimAt(on(2, 0, 1), Direction.UP))
            .wait(8)
            .run(() -> check(BuildInput.fireUse(), "the first click is the mode's"))
            // Whichever block of the deck the crosshair met first (a plank may stand before the one it was turned to).
            .run(() -> check(ClientModeState.anchors().size() == 1 && SubLevels.same(level(), ClientModeState.anchors().get(0), on(0, 0, 0)),
                "corner A is on the deck (" + anchors() + ")"))
            // Out over the deck's east side, at the corner's own height: nothing is there.
            .run(() -> aimAtPoint(Vec3.atCenterOf(new BlockPos(on(4, 0, 0).getX(), ClientModeState.anchors().get(0).getY(), on(0, 0, 0).getZ()))))
            .wait(30)
            .run(() -> {
                ModeHarness.state("sable air");
                checkAirCorner("beside the deck");
            })
            .screenshot("sable-air")
            .run(() -> ModeHarness.aim(new Vec3(0.5, -52.5, 8.02)))
            .wait(30)
            .run(() -> {
                ModeHarness.state("sable world block");
                final BlockHitResult hit = hit();
                check(hit != null && hit.getBlockPos().equals(new BlockPos(0, -53, 8)), "the crosshair is on a block of the world (" + describe(hit) + ")");
                checkAirCorner("behind which a block of the world stands");
            })
            .screenshot("sable-world-block")
            .run(() -> {
                ModeController.INSTANCE.cancelSelection();
                check(!ClientModeState.selectionPending(), "the selection is cancelled");
            })
            .command("setblock 0 -53 8 minecraft:air");

        s.log("sable: mirror on the deck")
            .command("item replace entity @s hotbar.0 with minecraft:stone_brick_stairs 64")
            .run(() -> aimAt(on(0, 1, 0), Direction.UP))
            .wait(8)
            .run(() -> check(ClientModeState.activate(BuildModes.MIRROR_MODE), "mirror activates"))
            .run(() -> check(ClientModeState.anchors().equals(List.of(on(0, 1, 0))), "the centre is the block of the deck under the crosshair (" + anchors() + ")"))
            .wait(30)
            .screenshot("sable-mirror")
            .run(() -> aimAt(on(1, 1, 0), Direction.UP))
            .wait(25)
            .screenshot("sable-mirror-ghost")
            .run(() -> {
                final Minecraft mc = Minecraft.getInstance();
                final BlockHitResult hit = hit();
                check(hit != null && hit.getBlockPos().equals(on(1, 1, 0)) && hit.getDirection() == Direction.UP,
                    "the crosshair is on the plank beside the centre (" + describe(hit) + ")");
                if (hit == null || mc.gameMode == null || mc.player == null) return;
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            })
            .wait(30)
            .run(() -> {
                final BlockState placed = level().getBlockState(on(1, 2, 0)), image = level().getBlockState(on(-1, 2, 0));
                check(placed.is(Blocks.STONE_BRICK_STAIRS), "the stairs are placed (" + placed + ")");
                check(image.is(Blocks.STONE_BRICK_STAIRS), "and their mirror image, across the deck's own plane (" + image + ")");
                if (!placed.is(Blocks.STONE_BRICK_STAIRS) || !image.is(Blocks.STONE_BRICK_STAIRS)) return;
                final Direction facing = placed.getValue(StairBlock.FACING);
                check(image.getValue(StairBlock.FACING) == (facing.getAxis() == Direction.Axis.X ? facing.getOpposite() : facing),
                    "the image is turned as a mirror turns it (" + facing + " and " + image.getValue(StairBlock.FACING) + ")");
            })
            .screenshot("sable-mirror-placed")
            .run(ClientModeState::deactivate);

        s.log("sable: the preview goes with a moving sub-level")
            .command("item replace entity @s hotbar.0 with minecraft:oak_planks 64")
            .run(() -> check(ClientModeState.activate(BuildModes.FILL), "fill activates"))
            .run(() -> ModeController.INSTANCE.debugClick(on(-2, 1, 2), Direction.UP))
            .run(() -> ModeController.INSTANCE.debugClick(on(1, 2, 2), Direction.UP))
            .wait(30)
            .screenshot("sable-still")
            .command("sable paused false")
            .run(() -> mark = Util.getMillis())
            .waitUntil(() -> Util.getMillis() - mark > 450, 2000)
            .screenshot("sable-falling")
            .waitUntil(() -> Util.getMillis() - mark > 4000, 8000)
            .run(() -> {
                final SubLevels.Pose pose = Objects.requireNonNull(SubLevels.renderAt(on(0, 0, 0)), "no pose");
                final Vec3 middle = pose.toWorld(Vec3.atCenterOf(on(0, 0, 0)));
                SlateBuilding.LOGGER.info("[BuildingHarness] sable: after the fall the deck's middle is at {}", middle);
                check(middle.y < -58.0, "the deck fell");
                ModeHarness.state("sable landed");
            })
            .command("tp @s 0.5 -56 1.5 0 35")
            .wait(30)
            .screenshot("sable-landed")
            .run(ClientModeState::deactivate)
            .command("sable paused true");

        final BlockPos floor = new BlockPos(-3, -61, 0);
        s.log("sable: a move off the deck, into the world")
            .command("tp @s 0.5 -58 0.5 0 35")
            .wait(20)
            .run(() -> check(ClientModeState.activate(BuildModes.MOVE), "move activates"))
            .run(() -> ModeController.INSTANCE.debugClick(on(1, 2, 0), Direction.UP))
            .run(() -> ModeController.INSTANCE.debugClick(on(1, 2, 0), Direction.UP))
            .run(() -> check(ClientModeState.pending() == ClientModeState.Pending.DESTINATION, "a block of the deck is selected, the destination follows"))
            .run(() -> ModeHarness.aim(new Vec3(floor.getX() + 0.5, floor.getY() + 0.98, floor.getZ() + 0.5)))
            .wait(20)
            .run(() -> {
                final BlockHitResult hit = hit();
                check(hit != null && hit.getBlockPos().equals(floor) && hit.getDirection() == Direction.UP,
                    "the crosshair is on the floor of the world (" + describe(hit) + ")");
                check(BuildInput.fireUse(), "the destination click is the mode's");
                check(ClientModeState.pending() == ClientModeState.Pending.SELECTED
                    && ClientModeState.anchors().equals(List.of(on(1, 2, 0), on(1, 2, 0), floor)), "the destination is a block of the world (" + anchors() + ")");
            })
            .wait(30)
            .run(() -> {
                ModeHarness.state("sable move");
                final AABB placed = ModePreview.placedBounds();
                check(placed != null && placed.equals(new AABB(floor.above())), "the block lands on the floor of the world (" + placed + ")");
            })
            .screenshot("sable-move-preview")
            // Both ends of the plan from further off: what is taken away on the deck, where it lands in the world, and
            // the line from the one to the other.
            .command("tp @s -1.5 -54.5 -4.5 -11 38")
            .wait(30)
            .screenshot("sable-move-both")
            .command("tp @s 0.5 -58 0.5 0 35")
            .wait(15)
            .run(() -> ModeController.INSTANCE.confirm())
            .waitUntil(() -> ClientModeState.pending() != ClientModeState.Pending.APPLYING, 240)
            .wait(40)
            .run(() -> {
                ModeHarness.state("sable moved");
                check(level().getBlockState(floor.above()).is(Blocks.STONE_BRICK_STAIRS), "the stairs stand in the world (" + level().getBlockState(floor.above()) + ")");
                check(level().getBlockState(on(1, 2, 0)).isAir(), "and are gone from the deck");
            })
            .screenshot("sable-move-applied")
            // With the deck gone, undo takes back what it did in the world and leaves the deck's plot alone.
            .command("sable remove @e")
            .wait(40)
            .run(() -> check(SubLevels.renderAt(on(0, 0, 0)) == null, "the deck is gone"))
            .run(() -> ModeController.INSTANCE.undo())
            .wait(40)
            .run(() -> {
                ModeHarness.state("sable undo without the deck");
                check(level().getBlockState(floor.above()).isAir(), "undo takes the stairs out of the world again");
            })
            .run(ClientModeState::deactivate);
    }

    // ---- helpers ----

    /** A block of the deck by its place from the middle one, as the plot has it. */
    private static BlockPos on(final int dx, final int dy, final int dz) {
        return Objects.requireNonNull(deck, "the deck was not found").offset(dx, dy, dz);
    }

    /** Turns the crosshair to the middle of {@code face} of the plot's block {@code pos}, where the sub-level shows it. */
    private static void aimAt(final BlockPos pos, final Direction face) {
        aimAtPoint(Vec3.atCenterOf(pos).relative(face, 0.49));
    }

    /** Turns the crosshair to a point of the deck's plot, where the sub-level shows it. */
    private static void aimAtPoint(final Vec3 plot) {
        final SubLevels.Pose pose = SubLevels.renderAt(on(0, 0, 0));
        ModeHarness.aim(pose == null ? plot : pose.toWorld(plot));
    }

    /**
     * The corner the crosshair would set is one in the air, in the deck's space, on a plane through corner A, and on
     * the line the player looks along.
     */
    private static void checkAirCorner(final String where) {
        final ModeTarget t = ModeController.INSTANCE.target();
        final LocalPlayer player = player();
        final List<BlockPos> anchors = ClientModeState.anchors();
        if (t == null || anchors.isEmpty()) { check(false, "there is a target " + where); return; }
        final BlockPos a = anchors.get(0);
        check(t.air(), "the corner " + where + " is one in the air (" + t.pos().toShortString() + ")");
        check(SubLevels.same(level(), a, t.pos()), "it lies in the deck's space");
        check(t.pos().getX() == a.getX() || t.pos().getY() == a.getY() || t.pos().getZ() == a.getZ(), "on a plane through corner A");
        final SubLevels.Pose pose = SubLevels.renderAt(a);
        final Vec3 centre = pose == null ? Vec3.atCenterOf(t.pos()) : pose.toWorld(Vec3.atCenterOf(t.pos()));
        final Vec3 eye = player.getEyePosition();
        final Vec3 look = player.getLookAngle();
        final Vec3 to = centre.subtract(eye);
        final double off = to.subtract(look.scale(to.dot(look))).length();
        check(off < 0.9, "and on the line the player looks along (" + String.format("%.2f", off) + " blocks off it)");
    }

    private static @Nullable BlockHitResult hit() {
        final Minecraft mc = Minecraft.getInstance();
        return mc.hitResult instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK ? b : null;
    }

    private static String describe(final @Nullable BlockHitResult hit) {
        return hit == null ? "nothing" : hit.getBlockPos().toShortString() + " " + hit.getDirection();
    }

    private static String anchors() {
        return ClientModeState.anchors().toString();
    }

    private static ClientLevel level() {
        return Objects.requireNonNull(Minecraft.getInstance().level, "no level");
    }

    private static LocalPlayer player() {
        return Objects.requireNonNull(Minecraft.getInstance().player, "no player");
    }

    private static void check(final boolean ok, final String what) {
        ModeHarness.check(ok, what);
    }

    private SableHarness() {}
}
