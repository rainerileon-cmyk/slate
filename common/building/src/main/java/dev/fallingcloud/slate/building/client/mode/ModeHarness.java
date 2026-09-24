package dev.fallingcloud.slate.building.client.mode;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.client.BuildingHarness;
import dev.fallingcloud.slate.building.client.input.BuildInput;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.ops.Clipboard;
import dev.fallingcloud.slate.building.ops.ModeParams;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Dev harness scenarios for the building-mode client (run with {@code -PbuildingHarness=selection,mirror,paste,move};
 * {@code dimension} separately). Each one drives the real state machine (clicks go through {@link ModeController},
 * left-click through {@link BuildInput}) against the real ops server, logs {@code [BuildingHarness] CHECK ok|FAILED ...}
 * lines for the state it expects, and takes screenshots of the preview in both skins.
 */
final class ModeHarness {

    private static final BlockPos A = new BlockPos(-2, -60, 2);
    private static final BlockPos B = new BlockPos(2, -58, 6);

    static void register() {
        BuildingHarness.register("selection", ModeHarness::selection);
        BuildingHarness.register("mirror", ModeHarness::mirror);
        BuildingHarness.register("paste", ModeHarness::paste);
        BuildingHarness.register("move", ModeHarness::move);
        BuildingHarness.register("dimension", ModeHarness::dimension);
    }

    /**
     * Move, end to end with the server: a stone + gold pillar is selected, the destination is a click on the floor's top
     * face (the ops server's anchor convention: the clicked block + its face), the preview must land where the server
     * puts it, and after the apply the pillar stands there and its old place is empty; then undo brings it back.
     */
    private static void move(final BuildingHarness.Script s) {
        final BlockPos base = new BlockPos(3, -60, 6);
        final BlockPos floor = new BlockPos(-3, -61, 6);
        stage(s);
        s.log("move: pillar")
            .command("setblock 3 -60 6 minecraft:stone")
            .command("setblock 3 -59 6 minecraft:gold_block")
            .wait(10)                                                   // the client sees the pillar before planning
            .run(() -> check(ClientModeState.activate(BuildModes.MOVE), "move activates"))
            .run(() -> ModeController.INSTANCE.debugClick(base, Direction.UP))
            .run(() -> ModeController.INSTANCE.debugClick(base.above(), Direction.UP))
            .run(() -> check(ClientModeState.pending() == ClientModeState.Pending.DESTINATION, "two corners, then the destination follows"))
            .run(() -> ModeController.INSTANCE.debugClick(floor, Direction.UP))
            .run(() -> check(ClientModeState.pending() == ClientModeState.Pending.SELECTED
                && ClientModeState.anchors().equals(List.of(base, base.above(), floor)) && ClientModeState.face() == Direction.UP,
                "the destination is the clicked block and face"))
            .wait(25)
            .run(() -> {
                state("move preview");
                final net.minecraft.world.phys.AABB placed = ModePreview.placedBounds();
                check(placed != null && placed.equals(new net.minecraft.world.phys.AABB(-3, -60, 6, -2, -58, 7)),
                    "the preview lands on top of the clicked block (" + placed + ")");
            })
            .screenshot("move-preview")
            .run(() -> ModeController.INSTANCE.confirm())
            .waitUntil(() -> ClientModeState.pending() != ClientModeState.Pending.APPLYING, 240)
            .wait(30)
            .run(() -> {
                final Minecraft mc = Minecraft.getInstance();
                check(mc.level != null && mc.level.getBlockState(floor.above()).is(Blocks.STONE)
                    && mc.level.getBlockState(floor.above(2)).is(Blocks.GOLD_BLOCK), "the pillar moved");
                check(mc.level != null && mc.level.getBlockState(base).isAir() && mc.level.getBlockState(base.above()).isAir(),
                    "its old place is empty");
            })
            .screenshot("move-applied")
            .run(() -> ModeController.INSTANCE.undo())
            .wait(40)
            .run(() -> {
                final Minecraft mc = Minecraft.getInstance();
                check(mc.level != null && mc.level.getBlockState(base).is(Blocks.STONE)
                    && mc.level.getBlockState(floor.above()).isAir(), "undo moves it back");
            })
            .run(ClientModeState::deactivate);
    }

    /** Fill: corner A, box following the crosshair, corner B, preview, resize, apply; then cancel, walls, measure. */
    private static void selection(final BuildingHarness.Script s) {
        stage(s);
        s.log("selection: fill")
            .run(() -> check(ClientModeState.activate(BuildModes.FILL), "fill activates"))
            .run(() -> check(ClientModeState.pending() == ClientModeState.Pending.NONE, "fill starts with nothing selected"))
            .wait(10)
            .run(() -> ModeController.INSTANCE.debugClick(A, Direction.UP))
            .run(() -> check(ClientModeState.pending() == ClientModeState.Pending.FIRST_ANCHOR && ClientModeState.anchors().equals(List.of(A)),
                "first click sets corner A"))
            .run(() -> aim(new Vec3(2.5, -57.5, 6.5)))
            .wait(30)
            .run(() -> state("following"))
            .run(() -> check(ClientModeState.stats().hasBox(), "the box follows the crosshair"))
            .screenshot("selection-following")
            .run(() -> ModeController.INSTANCE.debugClick(B, Direction.UP))
            .run(() -> check(ClientModeState.pending() == ClientModeState.Pending.SELECTED
                && ClientModeState.anchors().equals(List.of(A, B)), "second click completes the selection"))
            .wait(30)
            .run(() -> state("selected"))
            .run(() -> checkSize(5, 3, 5))
            .screenshot("selection-preview")
            .skin("VANILLA").wait(15).screenshot("selection-preview-vanilla").skin("DARK")
            .run(() -> check(ModeController.INSTANCE.debugResize(Direction.EAST, 2), "Ctrl+scroll pushes the east face"))
            .wait(20)
            .run(() -> checkSize(7, 3, 5))
            .screenshot("selection-resized")
            .run(() -> check(ModeController.INSTANCE.debugResize(Direction.EAST, -2), "Ctrl+scroll pulls it back"))
            .run(() -> check(!ModeController.INSTANCE.debugResize(Direction.UP, -5), "a face cannot be pulled through the box"))
            .wait(5)
            .run(() -> ModeController.INSTANCE.confirm())
            .run(() -> state("after confirm"))
            .waitUntil(() -> ClientModeState.pending() != ClientModeState.Pending.APPLYING, 240)
            .wait(40)
            .run(() -> {
                state("applied");
                world("filled corner A", A);
                world("filled corner B", B);
            })
            .screenshot("selection-applied")
            .log("selection: left-click cancels")
            .run(() -> {
                // Without the ops server's planners the apply is refused and the selection stays: clear it the user's way.
                if (ClientModeState.selectionPending()) check(BuildInput.fireAttack(), "left-click clears a refused selection");
            })
            .run(() -> ModeController.INSTANCE.debugClick(new BlockPos(-4, -60, 9), Direction.UP))
            .run(() -> check(ClientModeState.pending() == ClientModeState.Pending.FIRST_ANCHOR, "a new click starts a new selection"))
            .run(() -> check(BuildInput.fireAttack(), "left-click on a pending selection is consumed"))
            .run(() -> check(ClientModeState.pending() == ClientModeState.Pending.NONE && ClientModeState.anchors().isEmpty(),
                "left-click clears the selection"))
            .run(() -> check(!BuildInput.fireAttack(), "left-click with nothing selected mines normally"))
            .log("selection: walls + Shift+scroll")
            .run(() -> check(ClientModeState.activate(BuildModes.WALLS), "walls activates"))
            .run(() -> ModeController.INSTANCE.debugClick(new BlockPos(-6, -60, 8), Direction.UP))
            .run(() -> ModeController.INSTANCE.debugClick(new BlockPos(0, -57, 14), Direction.UP))
            .run(() -> ModeController.INSTANCE.debugStepParam(1))
            .run(() -> check(ClientModeState.params(BuildModes.WALLS).getInt("thickness") == 2, "Shift+scroll raises the thickness"))
            .run(() -> ModeController.INSTANCE.debugStepParam(-1))
            .wait(25)
            .run(() -> state("walls"))
            .screenshot("selection-walls")
            .log("selection: measure")
            .run(() -> check(ClientModeState.activate(BuildModes.MEASURE), "measure activates"))
            .run(() -> ModeController.INSTANCE.debugClick(new BlockPos(-8, -61, -2), Direction.UP))
            .run(() -> ModeController.INSTANCE.debugClick(new BlockPos(7, -59, 13), Direction.UP))
            .wait(25)
            .run(() -> {
                state("measure");
                checkSize(16, 3, 16);
                check(Math.abs(ClientModeState.stats().distance() - Math.sqrt(15 * 15 + 2 * 2 + 15 * 15)) < 0.01, "measure reports the distance");
            })
            .screenshot("selection-measure")
            .run(ClientModeState::deactivate)
            .run(() -> check(!ClientModeState.isActive() && ClientModeState.anchors().isEmpty(), "modes turn off"))
            .wait(5)
            .run(() -> check(ClientModeState.hints().isEmpty(), "no hints without a mode"));
    }

    /** Mirror (plane through the targeted block, axis change, a mirrored placement) and radial. */
    private static void mirror(final BuildingHarness.Script s) {
        stage(s);
        final BlockPos centre = new BlockPos(0, -61, 4);
        s.log("mirror: activate on the targeted block")
            .command("item replace entity @s hotbar.0 with minecraft:stone_bricks 64")
            .command("tp @s 0.5 -58 0.5")                              // within placing reach of the test block
            .wait(5)
            .run(() -> aim(topOf(centre)))
            .wait(5)
            .run(() -> check(ClientModeState.activate(BuildModes.MIRROR_MODE), "mirror activates"))
            .run(() -> check(ClientModeState.anchors().equals(List.of(centre)), "the centre is the targeted block"))
            .wait(30)
            .run(() -> state("mirror"))
            .screenshot("mirror-plane")
            .run(() -> ClientModeState.setParam(BuildModes.MIRROR_MODE, "axis", "XZ"))
            .wait(20)
            .screenshot("mirror-xz")
            .run(() -> place(new BlockPos(2, -61, 3), Direction.UP))
            .wait(30)
            .run(() -> {
                // Centre (0, -61, 4): x' = -x, z' = 8 - z.
                world("placed", new BlockPos(2, -60, 3));
                world("mirrored across X", new BlockPos(-2, -60, 3));
                world("mirrored across Z", new BlockPos(2, -60, 5));
                world("mirrored across both", new BlockPos(-2, -60, 5));
                state("server symmetry " + (ClientModeState.symmetry() == null ? "none" : ClientModeState.symmetry().mode().id()));
            })
            .screenshot("mirror-placed")
            .run(() -> check(ClientModeState.activate(BuildModes.RADIAL), "radial activates"))
            .wait(30)
            .screenshot("mirror-radial")
            .skin("VANILLA").wait(15).screenshot("mirror-radial-vanilla").skin("DARK")
            .run(ClientModeState::deactivate)
            .run(() -> check(!ClientModeState.isActive() && ClientModeState.anchors().isEmpty(), "radial turns off"));
    }

    /**
     * A dimension change ends the active mode. Separate from {@link #mirror} because a quick nether round trip can
     * leave nether chunks generating when the harness quits, and vanilla's shutdown then waits for them (seen as a
     * long "Saving worlds"); the generous waits here let them settle first.
     */
    private static void dimension(final BuildingHarness.Script s) {
        stage(s);
        s.log("dimension: a dimension change ends the mode")
            .run(() -> check(ClientModeState.activate(BuildModes.RADIAL), "radial activates"))
            .command("execute in minecraft:the_nether run tp @s 0.5 110 0.5")
            .waitUntil(() -> dimension() == Level.NETHER && Minecraft.getInstance().levelRenderer.hasRenderedAllSections(), 900)
            .wait(40)
            .run(() -> check(!ClientModeState.isActive(), "leaving the dimension turns the mode off"))
            .run(() -> check(ClientModeState.notice() != null && ClientModeState.notice().text().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                && "slate_building.notice.left_dimension".equals(tc.getKey()), "the player is told why"))
            .wait(160)
            .command("execute in minecraft:overworld run tp @s 0.5 -57 -5.5 0 30")
            .waitUntil(() -> dimension() == Level.OVERWORLD && Minecraft.getInstance().levelRenderer.hasRenderedAllSections(), 900)
            .wait(200);
    }

    /** Paste with a synthetic clipboard (structure-template format): hover footprint, fixed preview, rotation. */
    private static void paste(final BuildingHarness.Script s) {
        stage(s);
        s.log("paste: synthetic clipboard")
            .run(() -> ClientModeState.setClipboard(sampleClipboard()))
            .run(() -> check(ClientModeState.activate(BuildModes.PASTE), "paste activates"))
            .run(() -> aim(topOf(new BlockPos(0, -61, 6))))
            .wait(30)
            .run(() -> state("paste hover"))
            .run(() -> check(ClientModeState.stats().hasBox(), "the clipboard footprint follows the crosshair"))
            .screenshot("paste-hover")
            .run(() -> ModeController.INSTANCE.debugClick(new BlockPos(0, -61, 6), Direction.UP))
            .run(() -> check(ClientModeState.pending() == ClientModeState.Pending.PREVIEW, "a click fixes the paste preview"))
            .run(() -> ModeController.INSTANCE.debugStepParam(1))
            .run(() -> check("90".equals(ClientModeState.params(BuildModes.PASTE).getChoice("rotation")), "Shift+scroll rotates the paste"))
            .wait(25)
            .run(() -> {
                state("paste preview");
                checkSize(3, 2, 4);
            })
            .screenshot("paste-preview")
            .run(ClientModeState::deactivate);
    }

    // ---- helpers ----

    /** A clean smooth-stone floor, the player hovering (creative flight) a few blocks up and back, looking at it. */
    private static void stage(final BuildingHarness.Script s) {
        s.command("time set noon")
            .command("weather clear")
            .command("fill -12 -60 -10 12 -48 18 minecraft:air")
            .command("fill -12 -61 -10 12 -61 18 minecraft:smooth_stone")
            .command("tp @s 0.5 -57 -5.5 0 30")
            .command("item replace entity @s hotbar.0 with minecraft:oak_planks 64")
            .command("item replace entity @s hotbar.1 with minecraft:stone_bricks 64")
            .run(() -> {
                final Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.getInventory().selected = 0;
                    mc.player.getAbilities().flying = true;
                    mc.player.onUpdateAbilities();
                }
                mc.getToasts().clear();
                ClientModeState.deactivate();
                // Parameters persist in the run dir's building.json; every scenario starts from the defaults.
                for (final BuildMode m : BuildModes.all()) ClientModeState.setParams(ModeParams.defaults(m));
            })
            .wait(20);
    }

    /** A 4×2×3 (x, y, z) clipboard: a stone-brick floor with oak stairs on it, in the ops server's sync format. */
    private static CompoundTag sampleClipboard() {
        final List<Clipboard.Entry> entries = new java.util.ArrayList<>();
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 3; z++) {
                entries.add(new Clipboard.Entry(new BlockPos(x, 0, z), Blocks.STONE_BRICKS.defaultBlockState(), null));
                if (z == 1) entries.add(new Clipboard.Entry(new BlockPos(x, 1, z), Blocks.OAK_STAIRS.defaultBlockState(), null));
            }
        }
        return new Clipboard(new net.minecraft.core.Vec3i(4, 2, 3), entries).toTag();
    }

    /** A point just under the middle of {@code pos}'s top face: the view ray enters that block through its top. */
    private static Vec3 topOf(final BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.98, pos.getZ() + 0.5);
    }

    /** Turns the player to look at {@code at} (client rotation; the server follows with the next movement packet). */
    private static void aim(final Vec3 at) {
        final LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        final Vec3 d = at.subtract(p.getEyePosition());
        final float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
        final float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
        p.setYHeadRot(yaw);
    }

    /** A real right-click placement on {@code pos}'s {@code face} (goes through the server, so symmetry applies). */
    private static void place(final BlockPos pos, final Direction face) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return;
        aim(Vec3.atCenterOf(pos).relative(face, 0.5));
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false));
    }

    private static net.minecraft.resources.ResourceKey<Level> dimension() {
        final Minecraft mc = Minecraft.getInstance();
        return mc.level == null || mc.player == null ? null : mc.level.dimension();
    }

    private static void checkSize(final int x, final int y, final int z) {
        final ClientModeState.Stats st = ClientModeState.stats();
        check(st.sizeX() == x && st.sizeY() == y && st.sizeZ() == z,
            "size is " + x + "×" + y + "×" + z + " (got " + st.sizeX() + "×" + st.sizeY() + "×" + st.sizeZ() + ")");
    }

    private static void check(final boolean ok, final String what) {
        if (ok) SlateBuilding.LOGGER.info("[BuildingHarness] CHECK ok: {}", what);
        else SlateBuilding.LOGGER.warn("[BuildingHarness] CHECK FAILED: {}", what);
    }

    private static void world(final String what, final BlockPos pos) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        final BlockState state = mc.level.getBlockState(pos);
        SlateBuilding.LOGGER.info("[BuildingHarness] world {} {}: {}", what, pos.toShortString(), BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    private static void state(final String what) {
        final BuildMode m = ClientModeState.current();
        final ClientModeState.Stats st = ClientModeState.stats();
        final ModeTarget t = ModeController.INSTANCE.target();
        final ClientModeState.Notice n = ClientModeState.notice();
        SlateBuilding.LOGGER.info("[BuildingHarness] {}: mode={} pending={} anchors={} target={} stats='{}' planned={} blocks={} missing={} error={} hints=[{}] notice={}",
            what, m == null ? "-" : m.id(), ClientModeState.pending(),
            ClientModeState.anchors().stream().map(BlockPos::toShortString).collect(Collectors.joining(" / ")),
            t == null ? "-" : t.pos().toShortString() + (t.air() ? " (air)" : ""),
            st.summary().getString(), st.planned(), st.blocks(), st.missing(),
            st.error() == null ? "-" : st.error().getString(),
            ClientModeState.hints().stream().map(h -> h.key().getString() + ": " + h.action().getString()).collect(Collectors.joining(", ")),
            n == null ? "-" : n.text().getString());
    }

    private ModeHarness() {}
}
