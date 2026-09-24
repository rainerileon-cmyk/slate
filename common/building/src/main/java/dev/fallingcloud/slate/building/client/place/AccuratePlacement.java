package dev.fallingcloud.slate.building.client.place;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.client.input.BuildKeys;
import dev.fallingcloud.slate.building.client.mode.ClientModeState;
import dev.fallingcloud.slate.building.client.wheel.WheelConfig;
import dev.fallingcloud.slate.building.config.PlacementSettings;
import dev.fallingcloud.slate.building.mixin.core.KeyMappingAccessor;
import dev.fallingcloud.slate.building.mixin.core.MinecraftAccessor;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.ModeKind;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ScaffoldingBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Accurate Block Placement (Clayborn's mod, the NeoForge 1.21.1 port by Binaris), rebuilt inside Slate Building with
 * the same rules, so the mod itself is not needed (it is deferred to when installed: {@link #yields()}).
 *
 * <p>What it does: with a block in hand, holding use places a block <b>every time the crosshair reaches a spot that
 * can take one</b>, instead of vanilla's one placement per 4 ticks, so sweeping along a row or a wall never skips a
 * block. The rules, in the order the mod applies them after every crosshair update ({@code GameRenderer.pick}, every
 * frame and once per tick):
 * <ul>
 *   <li>A fresh press of the use key resets everything and remembers where the mouse was; then each queued click is
 *       placed at once (vanilla's own use call is suppressed while this takes over).</li>
 *   <li>Only for a {@link BlockItem} in either hand (main hand first) whose class does not override {@code Item.use}
 *       (lily pads keep vanilla), with the crosshair on a block; not while the other hand is using an item that
 *       overrides {@code use} (a bow, a shield).</li>
 *   <li>Vanilla keeps interactive blocks: a target block that overrides {@code useWithoutItem} / {@code useItemOn}
 *       (chest, door, lever, ...) unless it is a stair or the player sneaks; a block entity block within 0.6 of the
 *       last placement; scaffolding on scaffolding; a compostable item on a composter.</li>
 *   <li>While the key is held: a block goes down when the crosshair is on a new block that is not the one just placed,
 *       or on the one just placed after the player moved at least 0.99 along the face's axis away from it (bridging
 *       backwards). The first 4 ticks after a fresh press wait for vanilla's cooldown unless the mouse moved at least
 *       a tenth of the screen; spots passed while waiting are back-filled when the wait ends.</li>
 *   <li>Each placement is vanilla's own {@code startUseItem} (so every mod hook, the swing and the item animation
 *       still happen); it counts as placed when the block at the placement position changed.</li>
 * </ul>
 * Fast breaking (off by default) removes vanilla's 5-tick pause between blocks while attack is held
 * ({@code FastBreakGameModeMixin}). Both have an unbound toggle key ({@link BuildKeys}) with a chat line.
 *
 * <p>Slate's own building modes come first: while a selection-driven mode is active (anything but Mirror, Radial and
 * Extended) this stays out of the way, since their right-click is the mode's.
 */
public final class AccuratePlacement {

    private static final String ORIGINAL_MOD = "accurateblockplacement";
    /** Vanilla's right-click cooldown after a placement, the wait a fresh press honours. */
    private static final double MOUSE_MOVE_FRACTION = 0.1;
    private static final double BACKSTEP_BLOCKS = 0.99;
    private static final double BLOCK_ENTITY_DISTANCE = 0.6;

    private static @Nullable BlockPos lastSeenBlockPos;
    private static @Nullable BlockPos lastPlacedBlockPos;
    private static @Nullable Vec3 lastPlayerPlacedBlockPos;
    private static boolean autoRepeatWaitingOnCooldown = true;
    private static @Nullable Vec3 lastFreshPressMouseRatio;
    private static final List<HitResult> BACK_FILL = new ArrayList<>();
    private static InteractionHand handOfCurrentItemInUse = InteractionHand.MAIN_HAND;
    /** While true, vanilla's own {@code startUseItem} is cancelled: this class places instead ({@code BuildMinecraftMixin}). */
    private static boolean suppressVanillaUse;

    private static @Nullable Boolean yields;

    private AccuratePlacement() {}

    public static void init() {
        SlateEvents.CLIENT_TICK_END.register(AccuratePlacement::pollKeys);
    }

    // ------------------------------------------------------------------ settings

    private static PlacementSettings settings() {
        if (SlateBuilding.config().placement == null) SlateBuilding.config().placement = new PlacementSettings();
        return SlateBuilding.config().placement;
    }

    /** The original mod is installed: it does this already, so nothing here runs. */
    public static boolean yields() {
        if (yields == null) yields = SlatePlatform.get().isModLoaded(ORIGINAL_MOD);
        return yields;
    }

    public static boolean enabled() {
        return settings().accurate && !yields();
    }

    public static boolean fastBreaking() {
        return settings().fastBreaking && !yields();
    }

    /** {@code Minecraft.startUseItem} HEAD: whether vanilla's own call is cancelled right now. */
    public static boolean suppressVanillaUse() {
        return suppressVanillaUse;
    }

    private static void pollKeys() {
        final Minecraft mc = Minecraft.getInstance();
        while (BuildKeys.TOGGLE_ACCURATE_PLACEMENT.consumeClick()) {
            settings().accurate = !settings().accurate;
            WheelConfig.saveAndNotify();
            if (mc.player != null && settings().toggleMessage) {
                mc.player.sendSystemMessage(Component.translatable(settings().accurate
                    ? "slate_building.placement.accurate.on" : "slate_building.placement.accurate.off"));
            }
        }
        while (BuildKeys.TOGGLE_FAST_BREAKING.consumeClick()) {
            settings().fastBreaking = !settings().fastBreaking;
            WheelConfig.saveAndNotify();
            if (mc.player != null && settings().toggleMessage) {
                mc.player.sendSystemMessage(Component.translatable(settings().fastBreaking
                    ? "slate_building.placement.fast_breaking.on" : "slate_building.placement.fast_breaking.off"));
            }
        }
    }

    // ------------------------------------------------------------------ the mod's logic, after every crosshair update

    private static void reset() {
        lastSeenBlockPos = null;
        lastPlacedBlockPos = null;
        lastPlayerPlacedBlockPos = null;
        autoRepeatWaitingOnCooldown = true;
        BACK_FILL.clear();
        lastFreshPressMouseRatio = null;
    }

    /** The block item in hand and the hand holding it (main hand first), else null. */
    private static @Nullable Item itemInUse(final Minecraft mc) {
        for (final InteractionHand hand : InteractionHand.values()) {
            final ItemStack stack = mc.player.getItemInHand(hand);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) continue;
            handOfCurrentItemInUse = hand;
            return stack.getItem();
        }
        return null;
    }

    public static void afterPick() {
        if (!enabled()) {
            suppressVanillaUse = false;
            reset();
            return;
        }
        final Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || mc.options.keyUse == null || mc.hitResult == null || mc.player == null || mc.level == null
            || mc.mouseHandler == null || mc.getWindow() == null) {
            return;
        }
        suppressVanillaUse = false;
        // Slate's selection-driven modes own the right-click; the toggles (mirror, radial, extended) build normally.
        final BuildMode mode = ClientModeState.current();
        if (mode != null && mode.kind() != ModeKind.TOGGLE && mode.kind() != ModeKind.REACH) {
            reset();
            return;
        }

        final boolean freshKeyPress = ((KeyMappingAccessor) mc.options.keyUse).slateBuilding$clickCount() > 0;
        final Item item = itemInUse(mc);
        if (freshKeyPress) {
            reset();
            lastFreshPressMouseRatio = mouseRatio(mc);
        }
        if (item == null || !(item instanceof BlockItem)) return;
        if (overridesItemUse(item)) return;
        if (mc.hitResult.getType() != HitResult.Type.BLOCK) return;
        final InteractionHand otherHand = handOfCurrentItemInUse == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        final ItemStack other = mc.player.getItemInHand(otherHand);
        if (!other.isEmpty() && overridesItemUse(other.getItem()) && mc.player.isUsingItem()) return;

        final BlockHitResult hit = (BlockHitResult) mc.hitResult;
        final BlockPos hitPos = hit.getBlockPos();
        final Block hitBlock = mc.level.getBlockState(hitPos).getBlock();
        if (overridesBlockUse(hitBlock) && !(hitBlock instanceof StairBlock) && !mc.player.isShiftKeyDown()) {
            // An interactive block: vanilla opens / toggles it, and the run starts over afterwards.
            lastSeenBlockPos = null;
            lastPlacedBlockPos = null;
            lastPlayerPlacedBlockPos = null;
            BACK_FILL.clear();
            return;
        }
        if (hitBlock instanceof BaseEntityBlock && lastPlayerPlacedBlockPos != null
            && lastPlayerPlacedBlockPos.distanceTo(mc.player.position()) <= BLOCK_ENTITY_DISTANCE) {
            return;
        }
        if (item instanceof ScaffoldingBlockItem && hitBlock instanceof ScaffoldingBlock) return;
        if (hitBlock instanceof ComposterBlock && ComposterBlock.COMPOSTABLES.containsKey(item)) return;
        if (!freshKeyPress && !mc.options.keyUse.isDown()) return;

        suppressVanillaUse = true;
        final BlockPlaceContext ctx = new BlockPlaceContext(new UseOnContext(mc.player, handOfCurrentItemInUse, hit));
        final Block atTarget = mc.level.getBlockState(ctx.getClickedPos()).getBlock();

        // Along the clicked face's axis: where the player is, where they were at the last placement, and the last
        // placed block's near edge (the far edge for the west / north faces).
        double playerAxis = 0, lastPlayerAxis = 0, lastPlacedAxis = 0;
        if (lastPlacedBlockPos != null && lastPlayerPlacedBlockPos != null) {
            final Direction.Axis axis = ctx.getClickedFace().getAxis();
            playerAxis = mc.player.position().get(axis);
            lastPlayerAxis = lastPlayerPlacedBlockPos.get(axis);
            lastPlacedAxis = new Vec3(lastPlacedBlockPos.getX(), lastPlacedBlockPos.getY(), lastPlacedBlockPos.getZ()).get(axis);
            if (ctx.getClickedFace() == Direction.WEST || ctx.getClickedFace() == Direction.NORTH) lastPlacedAxis += 1;
        }
        final MinecraftAccessor acc = (MinecraftAccessor) mc;
        final Vec3 mouse = mouseRatio(mc);

        // A new spot: a block that is neither the one seen last time nor the one just placed, or the one just placed
        // once the player has backed off from it by a block along the face's axis.
        final boolean newSpot;
        final boolean seenChanged = lastSeenBlockPos == null || !lastSeenBlockPos.equals(hitPos);
        if (seenChanged && (lastPlacedBlockPos == null || !lastPlacedBlockPos.equals(hitPos))) {
            newSpot = true;
        } else {
            newSpot = lastPlacedBlockPos != null && lastPlayerPlacedBlockPos != null && lastPlacedBlockPos.equals(hitPos)
                && Math.abs(lastPlayerAxis - playerAxis) >= BACKSTEP_BLOCKS
                && Math.abs(lastPlayerAxis - lastPlacedAxis) < Math.abs(playerAxis - lastPlacedAxis);
        }
        final boolean mouseMoved = mouse != null && lastFreshPressMouseRatio != null
            && lastFreshPressMouseRatio.distanceTo(mouse) >= MOUSE_MOVE_FRACTION;
        final boolean waiting = autoRepeatWaitingOnCooldown && acc.slateBuilding$rightClickDelay() > 0 && !mouseMoved;

        if (freshKeyPress || (newSpot && !waiting)) {
            if (autoRepeatWaitingOnCooldown && !freshKeyPress) {
                // The wait is over: put down what the crosshair passed meanwhile, then carry on with this spot.
                autoRepeatWaitingOnCooldown = false;
                final HitResult current = mc.hitResult;
                for (final HitResult h : BACK_FILL) {
                    mc.hitResult = h;
                    placeNow(acc);
                }
                BACK_FILL.clear();
                mc.hitResult = current;
            }
            // Held: one placement now. Fresh: one per queued click.
            boolean run = !freshKeyPress;
            while (run || mc.options.keyUse.consumeClick()) {
                placeNow(acc);
                if (!atTarget.equals(mc.level.getBlockState(ctx.getClickedPos()).getBlock())) {
                    lastPlacedBlockPos = ctx.getClickedPos();
                    if (lastPlayerPlacedBlockPos == null) {
                        lastPlayerPlacedBlockPos = mc.player.position();
                    } else {
                        final Direction face = ctx.getClickedFace();
                        final Vec3 shifted = lastPlayerPlacedBlockPos.add(face.getStepX(), face.getStepY(), face.getStepZ());
                        final Vec3 p = mc.player.position();
                        lastPlayerPlacedBlockPos = switch (face.getAxis()) {
                            case X -> new Vec3(shifted.x, p.y, p.z);
                            case Y -> new Vec3(p.x, shifted.y, p.z);
                            case Z -> new Vec3(p.x, p.y, shifted.z);
                        };
                    }
                }
                run = false;
            }
        } else if (newSpot) {
            BACK_FILL.add(mc.hitResult);
        }
        lastSeenBlockPos = hit.getBlockPos();
    }

    /** Vanilla's use, with the suppression lifted for the call. */
    private static void placeNow(final MinecraftAccessor acc) {
        final boolean saved = suppressVanillaUse;
        suppressVanillaUse = false;
        acc.slateBuilding$startUseItem();
        suppressVanillaUse = saved;
    }

    private static @Nullable Vec3 mouseRatio(final Minecraft mc) {
        final int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
        if (w <= 0 || h <= 0) return null;
        return new Vec3(mc.mouseHandler.xpos() / w, mc.mouseHandler.ypos() / h, 0);
    }

    // ------------------------------------------------------------------ "does it override use?" (by signature, so it works under any mappings)

    private static final Map<Class<?>, Boolean> BLOCK_OVERRIDES = new HashMap<>();
    private static final Map<Class<?>, Boolean> ITEM_OVERRIDES = new HashMap<>();
    private static @Nullable String itemUseName;
    private static @Nullable String blockUseWithoutItemName, blockUseItemOnName;
    private static boolean namesResolved;

    private static void resolveNames() {
        if (namesResolved) return;
        namesResolved = true;
        for (final Method m : Item.class.getDeclaredMethods()) {
            if (m.getReturnType() == InteractionResultHolder.class && matches(m, Level.class, Player.class, InteractionHand.class)) itemUseName = m.getName();
        }
        for (final Method m : BlockBehaviour.class.getDeclaredMethods()) {
            if (m.getReturnType() == InteractionResult.class && matches(m, BlockState.class, Level.class, BlockPos.class, Player.class, BlockHitResult.class)) {
                blockUseWithoutItemName = m.getName();
            } else if (m.getReturnType() == ItemInteractionResult.class
                && matches(m, ItemStack.class, BlockState.class, Level.class, BlockPos.class, Player.class, InteractionHand.class, BlockHitResult.class)) {
                blockUseItemOnName = m.getName();
            }
        }
    }

    private static boolean matches(final Method m, final Class<?>... params) {
        final Class<?>[] p = m.getParameterTypes();
        if (p.length != params.length) return false;
        for (int i = 0; i < p.length; i++) if (p[i] != params[i]) return false;
        return true;
    }

    /** Whether the item's own class declares {@code Item.use(Level, Player, InteractionHand)} (lily pads do). */
    private static boolean overridesItemUse(final Item item) {
        resolveNames();
        if (itemUseName == null) return false;
        return ITEM_OVERRIDES.computeIfAbsent(item.getClass(), c -> {
            for (final Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(itemUseName) && matches(m, Level.class, Player.class, InteractionHand.class)) return true;
            }
            return false;
        });
    }

    /** Whether any class of the block's hierarchy below {@code BlockBehaviour} declares {@code useWithoutItem} or {@code useItemOn}. */
    private static boolean overridesBlockUse(final Block block) {
        resolveNames();
        return BLOCK_OVERRIDES.computeIfAbsent(block.getClass(), c -> {
            for (Class<?> k = c; k != null && k != Object.class && k != BlockBehaviour.class; k = k.getSuperclass()) {
                for (final Method m : k.getDeclaredMethods()) {
                    if (m.getName().equals(blockUseWithoutItemName) && m.getParameterCount() == 5) return true;
                    if (m.getName().equals(blockUseItemOnName) && m.getParameterCount() == 7) return true;
                }
            }
            return false;
        });
    }
}
