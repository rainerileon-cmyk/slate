package dev.fallingcloud.slate.building.client.mode;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.client.input.BuildInput;
import dev.fallingcloud.slate.building.client.input.BuildKeys;
import dev.fallingcloud.slate.building.compat.SubLevels;
import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.config.HudSettings;
import dev.fallingcloud.slate.building.config.ModeSettings;
import dev.fallingcloud.slate.building.net.ApplyOp;
import dev.fallingcloud.slate.building.net.CancelOp;
import dev.fallingcloud.slate.building.net.Redo;
import dev.fallingcloud.slate.building.net.SetReach;
import dev.fallingcloud.slate.building.net.SetSymmetry;
import dev.fallingcloud.slate.building.net.Undo;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.ops.ModeKind;
import dev.fallingcloud.slate.building.ops.ModeParam;
import dev.fallingcloud.slate.building.ops.ModeParams;
import dev.fallingcloud.slate.building.toolbox.ToolboxItem;
import dev.fallingcloud.slate.core.net.SlateNetwork;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The building-mode selection state machine (design §7 client flow), registered with {@link BuildInput} at priority
 * 50 (below the wheel overlay, above pick-block swap).
 *
 * <ul>
 *   <li><b>AREA</b> (and MEASURE): right-click = corner A, the box then follows the crosshair (on air it stays on the
 *       plane through A that faces the camera), right-click = corner B and the plan shows as ghosts, a third
 *       right-click (or the confirm key) applies. Measure only measures; its third click starts a new measurement.</li>
 *   <li><b>POINT</b>: the preview follows the crosshair (small plans), a click fixes it, the next click applies.</li>
 *   <li><b>MOVE</b>: corner A, corner B, then the destination follows the crosshair; a click fixes it and the next
 *       one applies.</li>
 *   <li><b>TOGGLE</b>: activating centres the symmetry on the targeted block (the confirm key re-centres, arrows move
 *       it); normal placing and breaking stay untouched on the client, the server mirrors them.</li>
 * </ul>
 * Left-click drives the same selection in its BREAKING form on the area modes (the plan breaks what it would have
 * placed). The other button cancels a pending selection and does nothing else (right-click a breaking one, left-click a
 * placing one), as does the cancel key (Q); the mode stays on. Esc is never taken: it only opens the pause menu, and a
 * pending selection stays as it is. Ctrl+scroll pushes / pulls the looked-at
 * face (radius / height on round modes), Shift+scroll steps the main parameter (thickness, count, depth, slices;
 * rotation for paste and move, axis for mirror; the radius of spheres and cylinders), arrow keys (Shift / Page Up /
 * Page Down for vertical) nudge; a plain scroll is the hotbar's (the corner distance in the air is a slider in the
 * build menu). Normal right-click use is suppressed while a selection-driven mode is active and the held item is
 * a block, a building tool or nothing; food, bows and other items with a use animation, containers (with nothing
 * selected) and the toolbox keep their own right-click.
 *
 * <p>Robustness: the mode ends on death, dimension change, disconnect and in spectator (measure excepted); nothing
 * is sent to a server without Slate Building (activation is refused with an explanation instead).
 */
final class ModeController implements BuildInput.Handler {

    static final ModeController INSTANCE = new ModeController();
    private static final int PRIORITY = 50;
    /** Ticks without progress or result after which an apply stops waiting (the server may have refused silently). */
    private static final int APPLY_TIMEOUT_TICKS = 200;

    /** Extended reach requests without a reply yet, and whether the last one asked for on. */
    private int reachInFlight;
    private boolean reachOnSent;

    private enum ScrollAction { RESIZE, PARAM, RADIUS, NUDGE }

    private boolean useLatched;
    private boolean swallowAttack;
    private double scrollAcc;
    private @Nullable ScrollAction lastScroll;
    private @Nullable ModeTarget target;
    /** Harness only: replaces the raycast of the next click. */
    private @Nullable ModeTarget forcedTarget;
    private @Nullable BuildMode previousMode;
    private @Nullable Level lastLevel;
    /** The dimension of the last tick with a level (kept through a loading gap, unlike {@link #lastLevel}). */
    private @Nullable net.minecraft.resources.ResourceKey<Level> lastDimension;
    private int applyingTicks;
    private int tickCount;
    private int inventoryStamp = -1;
    private boolean historyKnown;
    // Symmetry sync with the server.
    private int symmetryInFlight;
    private boolean symmetryOnSent;
    private int symmetryResendIn = -1;

    private ModeController() {}

    @Override
    public int priority() { return PRIORITY; }

    // ======================================================================== input

    @Override
    public boolean onUse() {
        final BuildMode mode = ClientModeState.current();
        final LocalPlayer player = Minecraft.getInstance().player;
        if (mode == null || player == null || mode.kind() == ModeKind.TOGGLE || mode.kind() == ModeKind.REACH) return false;
        // Right-click on a breaking (left-click) selection cancels it and does nothing else. The press stays latched,
        // so holding the button on does not start a placing selection right after.
        if (ClientModeState.selectionPending() && ClientModeState.destructive()) {
            if (!useLatched) cancelSelection();
            useLatched = true;
            return true;
        }
        if (!ClientModeState.selectionPending() && !claimsUse(player)) return false;
        if (useLatched) return true;                    // held: keep vanilla's repeat quiet, act once per press
        useLatched = true;
        click(player, mode, false, false);
        return true;
    }

    /**
     * Left-click drives the BREAKING selection of the area modes: the same corners, and the mode then breaks what it
     * would have placed (Fill clears the box, Walls tears them down, Replace removes the matches). On a placing
     * (right-click) selection of any mode, a left-click cancels it and does nothing else. Point and move modes have no
     * breaking variant: with nothing pending they mine as usual.
     */
    @Override
    public boolean onAttack() {
        final BuildMode mode = ClientModeState.current();
        final LocalPlayer player = Minecraft.getInstance().player;
        if (mode == null || player == null || mode.kind() == ModeKind.TOGGLE || mode.kind() == ModeKind.REACH) return false;
        if (ClientModeState.selectionPending() && !ClientModeState.destructive()) {
            cancelSelection();
            swallowAttack = true;                       // no mining with the press that cancelled
            return true;
        }
        if (mode.kind() != ModeKind.AREA && mode.kind() != ModeKind.MEASURE) return false;
        if (!ClientModeState.selectionPending() && !claimsAttack(player)) return false;
        click(player, mode, false, true);
        swallowAttack = true;                           // do not mine with the same press
        return true;
    }

    @Override
    public boolean suppressContinueAttack() {
        return swallowAttack || ClientModeState.selectionPending();
    }

    @Override
    public boolean onScroll(final double dx, final double dy) {
        final BuildMode mode = ClientModeState.current();
        final LocalPlayer player = Minecraft.getInstance().player;
        if (mode == null || player == null || dy == 0) return false;
        final ScrollAction action = scrollAction(mode);
        if (action == null) {
            scrollAcc = 0;
            return false;
        }
        if (action != lastScroll) scrollAcc = 0;
        lastScroll = action;
        scrollAcc += dy;
        final int steps = (int) scrollAcc;
        scrollAcc -= steps;
        if (steps != 0) scroll(player, mode, action, steps);
        return true;
    }

    @Override
    public boolean onKey(final int key, final int scancode, final int action, final int mods) {
        final BuildMode mode = ClientModeState.current();
        final LocalPlayer player = Minecraft.getInstance().player;
        // Esc is never taken here: it only opens the pause menu (a pending selection stays as it is).
        if (mode == null || player == null || action == GLFW.GLFW_RELEASE) return false;
        if (!ClientModeState.settings().arrowNudge) return false;
        final boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0;
        final List<BlockPos> held = ClientModeState.anchors();
        final Direction facing = ModeTarget.horizontalFacing(player, held.isEmpty() ? null : held.get(0));
        final Direction dir = switch (key) {
            case GLFW.GLFW_KEY_UP -> shift ? Direction.UP : facing;
            case GLFW.GLFW_KEY_DOWN -> shift ? Direction.DOWN : facing.getOpposite();
            case GLFW.GLFW_KEY_LEFT -> facing.getCounterClockWise();
            case GLFW.GLFW_KEY_RIGHT -> facing.getClockWise();
            case GLFW.GLFW_KEY_PAGE_UP -> Direction.UP;
            case GLFW.GLFW_KEY_PAGE_DOWN -> Direction.DOWN;
            default -> null;
        };
        return dir != null && nudge(mode, dir);
    }

    /** Whether a left-click with nothing selected starts a breaking selection (instead of mining): same items as {@link #claimsUse}. */
    private boolean claimsAttack(final LocalPlayer player) {
        final ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof ToolboxItem) return false;
        return main.isEmpty() || ClientPalette.placeable(player, main) || main.getUseAnimation() == UseAnim.NONE;
    }

    /** Whether a right-click with nothing selected starts a selection (instead of the held item's own use). */
    private boolean claimsUse(final LocalPlayer player) {
        final ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof ToolboxItem) return false;               // the toolbox opens as usual
        if (!main.isEmpty() && !ClientPalette.placeable(player, main) && main.getUseAnimation() != UseAnim.NONE) return false;
        final ModeSettings s = ClientModeState.settings();
        if (s.openContainers && !player.isSecondaryUseActive() && target != null && target.clicked() != null) {
            final Level level = player.level();
            if (level.getBlockState(target.clicked()).getMenuProvider(level, target.clicked()) != null) return false;
        }
        return true;
    }

    // ======================================================================== the flow

    /**
     * A click ({@code confirm = false}) or the confirm key ({@code confirm = true}); {@code destructive} says which
     * kind a NEW selection is (left-click: breaking), a pending one keeps its own.
     */
    private void click(final LocalPlayer player, final BuildMode mode, final boolean confirm, final boolean destructive) {
        final ClientModeState.Pending pending = ClientModeState.pending();
        final List<BlockPos> anchors = ClientModeState.anchors();
        if (pending == ClientModeState.Pending.FIRST_ANCHOR && anchors.isEmpty()
            || pending == ClientModeState.Pending.DESTINATION && anchors.size() < 2) {
            startSelection(player, mode, destructive);   // inconsistent state (set from outside): start over
            return;
        }
        switch (mode.kind()) {
            case AREA, MEASURE -> {
                switch (pending) {
                    case FIRST_ANCHOR -> {
                        final ModeTarget t = targetFor(player, mode, ModeTarget.Role.CORNER, anchors.get(0));
                        ClientModeState.setSelection(List.of(anchors.get(0), t.pos()), ClientModeState.face(), ClientModeState.Pending.SELECTED);
                        ModePreview.invalidate();
                        ModeSounds.selected();
                    }
                    case SELECTED -> {
                        if (mode.kind() == ModeKind.MEASURE) startSelection(player, mode, destructive);
                        else applyOrHint(player, mode, confirm);
                    }
                    default -> startSelection(player, mode, destructive);
                }
            }
            case POINT -> {
                if (pending == ClientModeState.Pending.PREVIEW) {
                    applyOrHint(player, mode, confirm);
                } else {
                    final ModeTarget t = targetFor(player, mode, ModeTarget.Role.POINT, null);
                    if (t.air() && !airAllowed(mode)) {
                        refuse(Component.translatable("slate_building.notice.aim_at_block"));
                        return;
                    }
                    ClientModeState.setSelection(List.of(t.pos()), t.face(), ClientModeState.Pending.PREVIEW);
                    ModePreview.invalidate();
                    ModeSounds.selected();
                }
            }
            case MOVE -> {
                switch (pending) {
                    case FIRST_ANCHOR -> {
                        final ModeTarget t = targetFor(player, mode, ModeTarget.Role.CORNER, anchors.get(0));
                        ClientModeState.setSelection(List.of(anchors.get(0), t.pos()), ClientModeState.face(), ClientModeState.Pending.DESTINATION);
                        ModeSounds.selected();
                    }
                    case DESTINATION -> {
                        // The ops server's convention: [A, B, clicked block] + the destination's face (PlanContext).
                        final ModeTarget d = targetFor(player, mode, ModeTarget.Role.DESTINATION, null);
                        ClientModeState.setSelection(List.of(anchors.get(0), anchors.get(1), d.pos()), d.face(), ClientModeState.Pending.SELECTED);
                        ModePreview.invalidate();
                        ModeSounds.selected();
                    }
                    case SELECTED -> applyOrHint(player, mode, confirm);
                    default -> startSelection(player, mode, false);
                }
            }
            case TOGGLE -> recentre(player, mode);
            case REACH -> { /* vanilla's own click, farther away */ }
        }
    }

    private void startSelection(final LocalPlayer player, final BuildMode mode, final boolean destructive) {
        final ModeTarget t = targetFor(player, mode, ModeTarget.Role.CORNER, null);
        ModeOverlay.reset();
        ClientModeState.setDestructive(destructive);
        ClientModeState.setSelection(List.of(t.pos()), t.face(), ClientModeState.Pending.FIRST_ANCHOR);
        ModeSounds.anchor();
    }

    private void applyOrHint(final LocalPlayer player, final BuildMode mode, final boolean confirm) {
        if (confirm || confirmsWithRightClick()) {
            apply(player, mode);
            return;
        }
        ClientModeState.notice(Component.translatable("slate_building.notice.press_confirm", BuildKeys.CONFIRM.getTranslatedKeyMessage()),
            ClientModeState.Severity.INFO);
    }

    /** Whether a click on air makes sense for {@code mode} (extend needs a block face to grow from). */
    static boolean airAllowed(final BuildMode mode) {
        return ClientModeState.airAllowed(mode);
    }

    private static boolean confirmsWithRightClick() {
        return ClientModeState.settings().confirmWithRightClick || BuildKeys.CONFIRM.isUnbound();
    }

    /** Sends the pending selection to the server after re-planning it on the spot. */
    void apply(final LocalPlayer player, final BuildMode mode) {
        final List<BlockPos> anchors = ClientModeState.anchors();
        final Direction face = ClientModeState.face() != null ? ClientModeState.face() : Direction.UP;
        if (anchors.isEmpty()) return;
        if (!ModeRules.serverHasModule(mode)) {
            refuse(Component.translatable("slate_building.notice.no_server"));
            return;
        }
        ModePreview.invalidate();
        final ClientModeState.Stats stats = ModePreview.update(player, mode, anchors, face, false);
        ClientModeState.setStats(stats);
        if (stats.error() != null) {
            refuse(stats.error());
            return;
        }
        if (!ModePreview.applicable(mode)) {
            refuse(Component.translatable("slate_building.notice.nothing"));
            return;
        }
        if (!stats.creative() && stats.missing() > 0) {
            final boolean partial = BuildingServerSettings.effective(player).ops().placeWhatYouCan;
            final int affordable = stats.blocks() - stats.missing() - stats.blocked();
            final ClientModeState.MaterialNeed short_ = firstShort(stats);
            if ((!partial || affordable <= 0) && !stats.supplyLink()) {
                refuse(short_ == null ? Component.translatable("slate_building.notice.nothing")
                    : Component.translatable("slate_building.notice.not_enough", short_.name(), short_.available(), short_.needed()));
                return;
            }
            if (!stats.supplyLink()) {
                ClientModeState.notice(Component.translatable("slate_building.notice.partial", affordable, stats.blocks()), ClientModeState.Severity.WARNING);
            }
        }
        final ModeParams params = ClientModeState.params(mode);
        SlateNetwork.get().sendToServer(new ApplyOp(mode.id(), params.toTag(), anchors, face, ClientPalette.paySlot(player), ClientModeState.destructive()));
        final AABB flash = mode == BuildModes.MOVE || mode.kind() == ModeKind.POINT ? ModePreview.placedBounds() : ModePreview.shape().box();
        ModeOverlay.flashApplied(flash != null ? flash : ModePreview.shape().box());
        ModeSounds.apply();
        ModePreview.clear();
        applyingTicks = 0;
        applied = new Applied(mode, anchors, face, ClientModeState.pending());
        ClientModeState.setSelection(anchors, face, ClientModeState.Pending.APPLYING);
    }

    /** What was last sent, so a selection the server refused outright can be handed back for adjusting. */
    private record Applied(BuildMode mode, List<BlockPos> anchors, Direction face, ClientModeState.Pending pending) {}

    private @Nullable Applied applied;

    /** The server answered the last apply: done → back to an empty selection; nothing happened → the selection returns. */
    private void onApplyResult() {
        final Applied last = applied;
        applied = null;
        if (ClientModeState.pending() != ClientModeState.Pending.APPLYING) return;
        final net.minecraft.world.level.Level level = Minecraft.getInstance().level;
        final dev.fallingcloud.slate.building.net.OpResult r = ClientModeState.lastResult();
        final boolean nothing = r != null && r.placed() + r.broken() == 0;
        if (last != null && nothing && last.mode() == ClientModeState.current() && last.mode().changesWorld() && level != null) {
            ClientModeState.setSelection(last.anchors(), last.face(), last.pending());
            ModePreview.invalidate();
        } else {
            ClientModeState.clearSelection();
        }
    }

    private static @Nullable ClientModeState.MaterialNeed firstShort(final ClientModeState.Stats stats) {
        for (final ClientModeState.MaterialNeed m : stats.materials()) if (!m.enough()) return m;
        return null;
    }

    /** The other mouse button, or the cancel key, on a pending selection. */
    void cancelSelection() {
        if (!ClientModeState.selectionPending()) return;
        ModeOverlay.flashCancelled(ModeOverlay.currentBoxOr(ModePreview.shape().box()));
        ModePreview.clear();
        ClientModeState.clearSelection();
        ModeSounds.cancel();
    }

    /** The cancel key: a pending selection first, else the running operation on the server. */
    void cancel() {
        if (ClientModeState.selectionPending()) {
            cancelSelection();
            return;
        }
        if (ClientModeState.pending() == ClientModeState.Pending.APPLYING || ClientModeState.progress() != null) {
            if (!ModeRules.serverHasOps()) return;
            SlateNetwork.get().sendToServer(CancelOp.INSTANCE);
            ClientModeState.notice(Component.translatable("slate_building.notice.stopping"), ClientModeState.Severity.INFO);
            ModeSounds.cancel();
        }
    }

    /** The confirm key. */
    void confirm() {
        final BuildMode mode = ClientModeState.current();
        final LocalPlayer player = Minecraft.getInstance().player;
        if (mode == null || player == null) return;
        click(player, mode, true, ClientModeState.destructive());
    }

    void undo() { history(true); }

    void redo() { history(false); }

    private void history(final boolean undo) {
        if (Minecraft.getInstance().player == null) return;
        if (!ModeRules.serverHasOps()) {
            refuse(Component.translatable("slate_building.notice.no_server"));
            return;
        }
        final int count = undo ? ClientModeState.undoCount() : ClientModeState.redoCount();
        if (historyKnown && count <= 0) {
            refuse(Component.translatable(undo ? "slate_building.notice.nothing_to_undo" : "slate_building.notice.nothing_to_redo"));
            return;
        }
        SlateNetwork.get().sendToServer(undo ? new Undo() : new Redo());
        ModeSounds.history();
    }

    // ======================================================================== scroll, nudge

    private @Nullable ScrollAction scrollAction(final BuildMode mode) {
        final ClientModeState.Pending pending = ClientModeState.pending();
        final boolean ctrl = Screen.hasControlDown();
        final boolean shift = Screen.hasShiftDown();
        if (ctrl && !shift) {
            if (pending == ClientModeState.Pending.SELECTED && (mode.kind() == ModeKind.AREA || mode.kind() == ModeKind.MEASURE)) return ScrollAction.RESIZE;
            if (pending == ClientModeState.Pending.PREVIEW || pending == ClientModeState.Pending.SELECTED && mode.kind() == ModeKind.MOVE) return ScrollAction.NUDGE;
            return null;
        }
        if (shift && !ctrl) {
            if (mainParam(mode) != null) return ScrollAction.PARAM;
            if (ModeGeometry.kind(mode) == ModeGeometry.Kind.SPHERE || ModeGeometry.kind(mode) == ModeGeometry.Kind.CYLINDER) {
                return pending == ClientModeState.Pending.SELECTED ? ScrollAction.RADIUS : null;
            }
            return null;
        }
        // A plain scroll is the hotbar's. (The corner distance in the air used to ride on it, which read as the hotbar
        // "sometimes" refusing to scroll; it is a slider in the build menu now.)
        return null;
    }

    private void scroll(final LocalPlayer player, final BuildMode mode, final ScrollAction action, final int steps) {
        switch (action) {
            case PARAM -> stepParam(mode, steps);
            case RADIUS, RESIZE -> {
                final List<BlockPos> anchors = ClientModeState.anchors();
                final Direction face;
                if (action == ScrollAction.RADIUS) {
                    face = Direction.EAST;       // any side face: the radius
                } else {
                    final AABB box = ModePreview.shape().box();
                    if (box == null) return;
                    final float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                    face = ModeTarget.lookedAtFace(box, SubLevels.eye(player, pt), player.getViewVector(pt));
                }
                List<BlockPos> next = anchors;
                for (int i = 0; i < Math.abs(steps) && next != null; i++) next = ModeGeometry.pushFace(mode, next, face, Integer.signum(steps));
                if (next == null || next.equals(anchors)) { ModeSounds.refused(); return; }
                ClientModeState.setAnchors(next);
                ModeSounds.step(steps > 0 ? 1.3F : 1.0F);
            }
            case NUDGE -> {
                final float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                // Towards where the player looks, as the space of the selection has it (a ship's own axes).
                final List<BlockPos> held = ClientModeState.anchors();
                final SubLevels.Pose space = held.isEmpty() || !SubLevels.present() ? null : SubLevels.renderAt(held.get(0));
                final Vec3 look = space == null ? player.getViewVector(pt) : space.dirToLocal(player.getViewVector(pt));
                final Direction dir = Direction.getNearest(look.x, look.y, look.z);
                for (int i = 0; i < Math.abs(steps); i++) nudge(mode, steps > 0 ? dir : dir.getOpposite());
            }
        }
    }

    /** The parameter Shift+scroll steps: the first INT parameter, else rotation (paste / move) or axis (mirror). */
    static @Nullable ModeParam mainParam(final BuildMode mode) {
        for (final ModeParam p : mode.params()) if (p.type() == ModeParam.Type.INT) return p;
        final ModeParam rotation = mode.param("rotation");
        if (rotation != null) return rotation;
        return mode.param("axis");
    }

    private void stepParam(final BuildMode mode, final int steps) {
        final ModeParam p = mainParam(mode);
        if (p == null) return;
        final ModeParams params = ClientModeState.params(mode);
        final Object before = params.get(p.id());
        final Object next = switch (p.type()) {
            case INT -> params.getInt(p.id()) + steps;
            case CHOICE -> {
                final int n = p.options().size();
                final int i = p.options().indexOf(params.getChoice(p.id()));
                yield p.options().get(Math.floorMod(i + steps, n));
            }
            case BOOL -> !params.getBool(p.id());
        };
        ClientModeState.setParam(mode, p.id(), next);
        final Object after = ClientModeState.params(mode).get(p.id());
        if (after.equals(before)) { ModeSounds.refused(); return; }
        ModeSounds.step(steps > 0 ? 1.3F : 1.0F);
        final Component value = p.type() == ModeParam.Type.CHOICE ? p.optionName((String) after) : Component.literal(String.valueOf(after));
        ClientModeState.notice(Component.translatable("slate_building.notice.param", p.displayName(), value), ClientModeState.Severity.INFO);
    }

    /** Moves whatever the player is placing one block towards {@code dir}; false when there is nothing to move. */
    private boolean nudge(final BuildMode mode, final Direction dir) {
        final ClientModeState.Pending pending = ClientModeState.pending();
        final List<BlockPos> anchors = ClientModeState.anchors();
        if (anchors.isEmpty()) return false;
        final List<BlockPos> next = new ArrayList<>(anchors);
        if (mode.kind() == ModeKind.TOGGLE) {
            next.set(0, anchors.get(0).relative(dir));
            symmetryResendIn = 2;
        } else if (pending == ClientModeState.Pending.FIRST_ANCHOR) {
            next.set(0, anchors.get(0).relative(dir));
        } else if (mode.kind() == ModeKind.MOVE && pending == ClientModeState.Pending.SELECTED && anchors.size() >= 3) {
            next.set(2, anchors.get(2).relative(dir));
        } else if (pending == ClientModeState.Pending.SELECTED || pending == ClientModeState.Pending.PREVIEW) {
            for (int i = 0; i < next.size(); i++) next.set(i, next.get(i).relative(dir));
        } else {
            return false;
        }
        ClientModeState.setAnchors(next);
        ModeSounds.step(1.15F);
        return true;
    }

    // ======================================================================== targets

    private ModeTarget targetFor(final LocalPlayer player, final BuildMode mode, final ModeTarget.Role role, final @Nullable BlockPos planeAnchor) {
        if (forcedTarget != null) return forcedTarget;
        final float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        return ModeTarget.compute(player, mode, ClientModeState.params(mode), role, planeAnchor, pt);
    }

    /** The role the next click has, for the live cursor. */
    private static ModeTarget.Role roleFor(final BuildMode mode, final ClientModeState.Pending pending) {
        if (mode.kind() == ModeKind.TOGGLE) return ModeTarget.Role.CENTRE;
        if (mode.kind() == ModeKind.POINT) return ModeTarget.Role.POINT;
        if (mode.kind() == ModeKind.MOVE && pending == ClientModeState.Pending.DESTINATION) return ModeTarget.Role.DESTINATION;
        return ModeTarget.Role.CORNER;
    }

    /** Re-centres a TOGGLE mode's symmetry on the targeted block. */
    private void recentre(final LocalPlayer player, final BuildMode mode) {
        final BlockPos centre = centreTarget(player, mode);
        ClientModeState.setSelection(List.of(centre), Direction.UP, ClientModeState.Pending.NONE);
        sendSymmetry(true);
        ModeSounds.anchor();
    }

    private BlockPos centreTarget(final LocalPlayer player, final BuildMode mode) {
        final ModeTarget t = targetFor(player, mode, ModeTarget.Role.CENTRE, null);
        return t.air() ? player.blockPosition() : t.pos();
    }

    // ======================================================================== per frame

    /** Called once per rendered frame before the ghost/overlay renderers draw. */
    void frame(final float partialTick) {
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        final BuildMode mode = ClientModeState.current();
        if (player == null || mc.level == null || mode == null) {
            target = null;
            ModeOverlay.drawFlashOnly();
            if (mode == null && !ClientModeState.hints().isEmpty()) ClientModeState.setHints(List.of());
            return;
        }
        final ClientModeState.Pending pending = ClientModeState.pending();
        final List<BlockPos> anchors = ClientModeState.anchors();
        final BlockPos planeAnchor = pending == ClientModeState.Pending.FIRST_ANCHOR && !anchors.isEmpty() ? anchors.get(0) : null;
        if (mode.kind() == ModeKind.REACH) {
            // Nothing to target or draw: vanilla's own crosshair, now with a longer arm. The chip shows the bonus.
            target = null;
            ClientModeState.setStats(ClientModeState.Stats.EMPTY);
            ModeOverlay.drawFlashOnly();
            updateHints(mode, pending);
            return;
        }
        target = ModeTarget.compute(player, mode, ClientModeState.params(mode), roleFor(mode, pending), planeAnchor, partialTick);

        if (mode.kind() == ModeKind.TOGGLE) {
            ClientModeState.setStats(ClientModeState.Stats.EMPTY);
            ModeOverlay.draw(new ModeOverlay.Frame(mode, pending, anchors, target, ModeGeometry.Shape.NONE, ClientModeState.Stats.EMPTY,
                ModeRules.symmetryRadius(player), Direction.UP));
            updateHints(mode, pending);
            return;
        }

        final List<BlockPos> live = liveAnchors(mode, pending, anchors, target);
        final boolean following = pending == ClientModeState.Pending.NONE || pending == ClientModeState.Pending.FIRST_ANCHOR
            || pending == ClientModeState.Pending.DESTINATION;
        // The destination / hover point faces the crosshair's face; a committed selection keeps its own.
        final Direction face = pending == ClientModeState.Pending.DESTINATION || pending == ClientModeState.Pending.NONE
            || ClientModeState.face() == null ? target.face() : ClientModeState.face();
        final ClientModeState.Stats stats = ModePreview.update(player, mode, live, face, following);
        ClientModeState.setStats(stats);
        ModePreview.submitGhosts();
        ModeOverlay.draw(new ModeOverlay.Frame(mode, pending, live, target, ModePreview.shape(), stats, 0, face));
        updateHints(mode, pending);
    }

    /** Committed anchors plus the one that follows the crosshair, as the preview plans them. */
    private static List<BlockPos> liveAnchors(final BuildMode mode, final ClientModeState.Pending pending, final List<BlockPos> anchors,
                                              final ModeTarget t) {
        return switch (pending) {
            case FIRST_ANCHOR -> anchors.isEmpty() ? List.of() : List.of(anchors.get(0), t.pos());
            case DESTINATION -> anchors.size() < 2 ? List.of() : List.of(anchors.get(0), anchors.get(1), t.pos());
            case SELECTED, PREVIEW -> anchors;
            case NONE -> mode.kind() == ModeKind.POINT && (!t.air() || mode == BuildModes.PASTE) ? List.of(t.pos()) : List.of();
            case APPLYING -> List.of();
        };
    }

    // ======================================================================== per tick

    void tick() {
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            lastLevel = null;
            return;
        }
        final BuildMode mode = ClientModeState.current();
        if (mode != null) {
            if ((lastLevel != null && lastLevel != mc.level) || (lastDimension != null && lastDimension != mc.level.dimension())) {
                ClientModeState.deactivate();
                ClientModeState.notice(Component.translatable("slate_building.notice.left_dimension"), ClientModeState.Severity.INFO);
            } else if (player.isDeadOrDying()) {
                ClientModeState.deactivate();
            } else if (player.isSpectator() && mode.kind() != ModeKind.MEASURE) {
                ClientModeState.deactivate();
                ClientModeState.notice(Component.translatable("slate_building.notice.spectator"), ClientModeState.Severity.INFO);
            }
        }
        lastLevel = mc.level;
        lastDimension = mc.level.dimension();

        if (!mc.options.keyUse.isDown()) useLatched = false;
        if (!mc.options.keyAttack.isDown()) swallowAttack = false;
        pollKeys();

        // An apply that never hears back (refused silently, or an older server) stops waiting.
        if (ClientModeState.pending() == ClientModeState.Pending.APPLYING) {
            if (ClientModeState.progress() != null) applyingTicks = 0;
            else if (++applyingTicks > APPLY_TIMEOUT_TICKS) {
                applied = null;
                ClientModeState.clearSelection();
            }
        }

        if (symmetryResendIn > 0 && --symmetryResendIn == 0) {
            symmetryResendIn = -1;
            final BuildMode m = ClientModeState.current();
            if (m != null && m.kind() == ModeKind.TOGGLE) sendSymmetry(true);
        }

        // Carried materials: re-check twice a second, and at once when the inventory changed.
        final int stamp = player.getInventory().getTimesChanged();
        if (++tickCount % 10 == 0 || stamp != inventoryStamp) {
            inventoryStamp = stamp;
            if (ClientModeState.current() != null) ModePreview.refreshAvailability(player, false);
        }
    }

    private void pollKeys() {
        for (final Map.Entry<String, KeyMapping> e : BuildKeys.modeKeys().entrySet()) {
            while (e.getValue().consumeClick()) {
                final BuildMode m = BuildModes.byId(e.getKey());
                if (m != null) ClientModeState.toggle(m);
            }
        }
        while (BuildKeys.UNDO.consumeClick()) undo();
        while (BuildKeys.REDO.consumeClick()) redo();
        while (BuildKeys.CONFIRM.consumeClick()) confirm();
        while (BuildKeys.CANCEL.consumeClick()) cancel();
        while (BuildKeys.EXIT_MODE.consumeClick()) ClientModeState.deactivate();
    }

    // ======================================================================== state events

    void onStateChange(final ClientModeState.Change change) {
        switch (change) {
            case MODE -> onModeChanged();
            case PARAMS -> {
                final BuildMode m = ClientModeState.current();
                if (m != null && m.kind() == ModeKind.TOGGLE && symmetryOnSent) symmetryResendIn = 3;
            }
            case RESULT -> {
                if (ClientModeState.isResetting()) return;
                onApplyResult();
                ModePreview.invalidate();
                final var r = ClientModeState.lastResult();
                if (r != null && r.placed() + r.broken() > 0) ModeSounds.done();
            }
            case PROGRESS -> applyingTicks = 0;
            case HISTORY -> historyKnown = true;
            case SYMMETRY -> onServerSymmetry();
            case REACH -> onServerReach();
            default -> {}
        }
    }

    private void onModeChanged() {
        final BuildMode now = ClientModeState.current();
        final BuildMode before = previousMode;
        previousMode = now;
        ModePreview.clear();
        ModeOverlay.reset();
        scrollAcc = 0;
        lastScroll = null;
        if (ClientModeState.isResetting()) {
            // Leaving the world: nothing to send, nothing to hear.
            symmetryOnSent = false;
            symmetryInFlight = 0;
            reachOnSent = false;
            reachInFlight = 0;
            symmetryResendIn = -1;
            historyKnown = false;
            applied = null;
            return;
        }
        // Mirror → radial just replaces the symmetry; anything else turns it off.
        if (before != null && before.kind() == ModeKind.TOGGLE && symmetryOnSent && (now == null || now.kind() != ModeKind.TOGGLE)) {
            sendSymmetry(false);
        }
        if (before != null && before.kind() == ModeKind.REACH && reachOnSent && (now == null || now.kind() != ModeKind.REACH)) {
            sendReach(false);
        }
        final LocalPlayer player = Minecraft.getInstance().player;
        if (now == null) {
            if (before != null) ModeSounds.modeOff();
            return;
        }
        ModeSounds.modeOn();
        if (now.kind() == ModeKind.TOGGLE && player != null) {
            ClientModeState.setSelection(List.of(centreTarget(player, now)), Direction.UP, ClientModeState.Pending.NONE);
            sendSymmetry(true);
        }
        if (now.kind() == ModeKind.REACH) sendReach(true);
        final HudSettings hud = SlateBuilding.config().hud;
        if (hud != null && !hud.enabled) {
            ClientModeState.notice(Component.translatable("slate_building.notice.mode_on", now.name()), ClientModeState.Severity.INFO);
        }
    }

    private void sendSymmetry(final boolean on) {
        final BuildMode mode = ClientModeState.current();
        final List<BlockPos> anchors = ClientModeState.anchors();
        if (Minecraft.getInstance().getConnection() == null || !SlateNetwork.get().serverHasChannel(SetSymmetry.TYPE)) return;
        final SetSymmetry payload = on && mode != null && mode.kind() == ModeKind.TOGGLE && !anchors.isEmpty()
            ? new SetSymmetry(mode.id(), ClientModeState.params(mode).toTag(), anchors.get(0))
            : new SetSymmetry("", new CompoundTag(), BlockPos.ZERO);
        SlateNetwork.get().sendToServer(payload);
        symmetryInFlight++;
        symmetryOnSent = !payload.mode().isEmpty();
    }

    /** The server reported its symmetry: reconcile with what the client wants. */
    private void onServerSymmetry() {
        if (symmetryInFlight > 0) symmetryInFlight--;
        if (symmetryInFlight > 0) return;              // a reply to an older request; the newest is on its way
        final BuildMode mode = ClientModeState.current();
        final boolean wantOn = mode != null && mode.kind() == ModeKind.TOGGLE;
        final ClientModeState.Symmetry s = ClientModeState.symmetry();
        if (wantOn && s == null && symmetryOnSent) {
            symmetryOnSent = false;
            ClientModeState.deactivate();
            // The server ends symmetry when the player changes dimension, and that reply can arrive before tick() sees the
            // new level: say why in the same words as tick() would.
            final Level level = Minecraft.getInstance().level;
            if (level != null && lastDimension != null && level.dimension() != lastDimension) {
                ClientModeState.notice(Component.translatable("slate_building.notice.left_dimension"), ClientModeState.Severity.INFO);
            } else {
                ClientModeState.notice(Component.translatable("slate_building.notice.symmetry_off"), ClientModeState.Severity.WARNING);
            }
        } else if (!wantOn && s != null) {
            sendSymmetry(false);
        }
    }

    // ---- extended reach (kind REACH): the server adds the toolbox reach bonus to the block reach while it is on

    private void sendReach(final boolean on) {
        if (Minecraft.getInstance().getConnection() == null || !SlateNetwork.get().serverHasChannel(SetReach.TYPE)) return;
        SlateNetwork.get().sendToServer(new SetReach(on));
        reachInFlight++;
        reachOnSent = on;
    }

    /** The server reported the reach it applies: reconcile with what the client wants (same dance as symmetry). */
    private void onServerReach() {
        if (reachInFlight > 0) reachInFlight--;
        if (reachInFlight > 0) return;              // a reply to an older request; the newest is on its way
        final BuildMode mode = ClientModeState.current();
        final boolean wantOn = mode != null && mode.kind() == ModeKind.REACH;
        final int bonus = ClientModeState.reachBonus();
        if (wantOn && bonus <= 0 && reachOnSent) {
            reachOnSent = false;
            ClientModeState.deactivate();
            ClientModeState.notice(Component.translatable("slate_building.notice.reach_off"), ClientModeState.Severity.WARNING);
        } else if (!wantOn && bonus > 0) {
            sendReach(false);
        }
    }

    // ======================================================================== hints

    private void updateHints(final BuildMode mode, final ClientModeState.Pending pending) {
        final List<ClientModeState.Hint> h = new ArrayList<>(5);
        final Component rmb = Component.translatable("slate_building.hint.key.rmb");
        final Component lmb = Component.translatable("slate_building.hint.key.lmb");
        // A breaking (left-click) selection is driven by the left button and cancelled with the right one; a placing
        // selection the other way round.
        final boolean breaking = ClientModeState.selectionPending() && ClientModeState.destructive();
        final Component clickKey = breaking ? lmb : rmb;
        final Component applyKey = confirmsWithRightClick() ? clickKey : BuildKeys.CONFIRM.getTranslatedKeyMessage();
        final Component cancelKey = breaking ? rmb : lmb;
        final String applyAction = breaking ? "break" : "apply";
        final boolean air = target != null && target.air();
        final ModeParam main = mainParam(mode);
        switch (mode.kind()) {
            case TOGGLE -> {
                if (!BuildKeys.CONFIRM.isUnbound()) h.add(hint(BuildKeys.CONFIRM.getTranslatedKeyMessage(), "recentre"));
                if (ClientModeState.settings().arrowNudge) h.add(hint(Component.translatable("slate_building.hint.key.arrows"), "move_centre"));
                if (main != null) h.add(new ClientModeState.Hint(Component.translatable("slate_building.hint.key.shift_scroll"), main.displayName()));
                if (!BuildKeys.EXIT_MODE.isUnbound()) h.add(hint(BuildKeys.EXIT_MODE.getTranslatedKeyMessage(), "turn_off"));
            }
            case REACH -> {
                if (!BuildKeys.EXIT_MODE.isUnbound()) h.add(hint(BuildKeys.EXIT_MODE.getTranslatedKeyMessage(), "turn_off"));
            }
            case POINT -> {
                if (pending == ClientModeState.Pending.PREVIEW) {
                    h.add(hint(applyKey, "apply"));
                    h.add(hint(cancelKey, "cancel"));
                    if (ClientModeState.settings().arrowNudge) h.add(hint(Component.translatable("slate_building.hint.key.arrows"), "move"));
                    if (main != null) h.add(new ClientModeState.Hint(Component.translatable("slate_building.hint.key.shift_scroll"), main.displayName()));
                } else {
                    h.add(hint(rmb, mode == BuildModes.PASTE ? "place_paste" : "preview"));
                    if (main != null) h.add(new ClientModeState.Hint(Component.translatable("slate_building.hint.key.shift_scroll"), main.displayName()));
                }
            }
            case MOVE -> {
                switch (pending) {
                    case FIRST_ANCHOR -> { h.add(hint(rmb, "corner_b")); h.add(hint(cancelKey, "cancel")); }
                    case DESTINATION -> { h.add(hint(rmb, "destination")); h.add(hint(cancelKey, "cancel")); }
                    case SELECTED -> {
                        h.add(hint(applyKey, "move_here"));
                        h.add(hint(cancelKey, "cancel"));
                        if (main != null) h.add(new ClientModeState.Hint(Component.translatable("slate_building.hint.key.shift_scroll"), main.displayName()));
                    }
                    default -> h.add(hint(rmb, "corner_a"));
                }
            }
            case AREA, MEASURE -> {
                switch (pending) {
                    case FIRST_ANCHOR -> { h.add(hint(clickKey, "corner_b")); h.add(hint(cancelKey, "cancel")); }
                    case SELECTED -> {
                        if (mode.kind() == ModeKind.MEASURE) {
                            h.add(hint(clickKey, "new_measure"));
                            h.add(hint(cancelKey, "clear"));
                        } else {
                            h.add(hint(applyKey, applyAction));
                            h.add(hint(cancelKey, "cancel"));
                        }
                        h.add(hint(Component.translatable("slate_building.hint.key.ctrl_scroll"), "resize"));
                        if (main != null) {
                            h.add(new ClientModeState.Hint(Component.translatable("slate_building.hint.key.shift_scroll"), main.displayName()));
                        } else if (ModeGeometry.kind(mode) == ModeGeometry.Kind.SPHERE || ModeGeometry.kind(mode) == ModeGeometry.Kind.CYLINDER) {
                            h.add(hint(Component.translatable("slate_building.hint.key.shift_scroll"), "radius"));
                        }
                        if (ClientModeState.settings().arrowNudge) h.add(hint(Component.translatable("slate_building.hint.key.arrows"), "move"));
                    }
                    default -> {
                        h.add(hint(rmb, "corner_a"));
                        if (mode.kind() == ModeKind.AREA) h.add(hint(lmb, "break_corner_a"));
                    }
                }
            }
        }
        ClientModeState.setHints(h);
    }

    private static ClientModeState.Hint hint(final Component key, final String action) {
        return new ClientModeState.Hint(key, Component.translatable("slate_building.hint." + action));
    }

    private static void refuse(final Component why) {
        ClientModeState.notice(why, ClientModeState.Severity.ERROR);
        ModeSounds.refused();
    }

    // ======================================================================== harness access

    /** Harness: acts as a right-click whose crosshair is exactly on {@code pos} / {@code face} (no raycast, no face rule). */
    void debugClick(final BlockPos pos, final Direction face) {
        final LocalPlayer player = Minecraft.getInstance().player;
        final BuildMode mode = ClientModeState.current();
        if (player == null || mode == null) return;
        forcedTarget = new ModeTarget(pos, face, false, pos);
        try {
            click(player, mode, false, false);
        } finally {
            forcedTarget = null;
        }
    }

    /** Harness: Ctrl+scroll on {@code face} of the selection, {@code steps} notches (negative pulls in). */
    boolean debugResize(final Direction face, final int steps) {
        final BuildMode mode = ClientModeState.current();
        if (mode == null) return false;
        List<BlockPos> next = ClientModeState.anchors();
        for (int i = 0; i < Math.abs(steps) && next != null; i++) next = ModeGeometry.pushFace(mode, next, face, Integer.signum(steps));
        if (next == null) return false;
        ClientModeState.setAnchors(next);
        return true;
    }

    /** Harness: Shift+scroll, {@code steps} notches. */
    void debugStepParam(final int steps) {
        final BuildMode mode = ClientModeState.current();
        if (mode != null) stepParam(mode, steps);
    }

    /** The target of the last frame (null before the first frame of a mode). */
    @Nullable ModeTarget target() { return target; }
}
