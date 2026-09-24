package dev.fallingcloud.slate.building.client.render;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.client.mode.ClientModeState;
import dev.fallingcloud.slate.building.client.model.ShapeModels;
import dev.fallingcloud.slate.building.config.PreviewSettings;
import dev.fallingcloud.slate.building.mixin.render.BlockItemInvoker;
import dev.fallingcloud.slate.building.variant.ShapeBlock;
import dev.fallingcloud.slate.building.variant.Variant;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The placement ghost (design §6 "Hand preview"): while the player holds a variant (or any block item with
 * {@code preview.allBlocks}) and looks at a block, show exactly what a right-click would place.
 *
 * <p>The state comes from the item's own placement logic on a {@link BlockPlaceContext} built from the crosshair,
 * after {@code updatePlacementContext} (so slab merging, scaffolding and replaceable plants behave as in vanilla);
 * our shape items go through {@link VariantRegistry#placementState} and carry their material. A placement the item
 * would refuse (an entity in the way, no support) shows as an INVALID outline. Doors and tall plants get their upper
 * half, beds their head. Nothing shows when the click would do something else instead: opening a chest, flipping a
 * lever or door (unless sneaking), while a building mode has a selection pending, in spectator / adventure, or while
 * BridgingMod's assist draws its own target. With symmetry active ({@code preview.showMirrored}) the mirrored copies
 * the server will place appear too, a little fainter.
 *
 * <p>Motion: a new target settles in quickly from 60% (from nothing when there was no ghost), and the ghost fades
 * out when the target is lost.
 */
final class HandPreview {

    private record Target(List<GhostRenderer.Ghost> ghosts, float[] weights) {}

    private static final Anim FADE = new Anim(0F, 110, Ease.OUT_CUBIC);
    private static @Nullable Target shown;
    private static @Nullable Object shownKey;
    private static final Set<String> FAILED = new HashSet<>();

    static void init() {
        BuildingRender.FRAME.register(HandPreview::frame);
    }

    /** Leaving the world. */
    static void reset() {
        shown = null;
        shownKey = null;
        FADE.snap(0F);
    }

    private static void frame() {
        Target now;
        try {
            now = compute();
        } catch (final RuntimeException e) {
            // A modded block whose placement logic throws on a client-side context: no preview, logged once per item.
            if (FAILED.add(Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getMainHandItem().getItem().toString())) {
                SlateBuilding.LOGGER.warn("[Slate Building] placement preview failed", e);
            }
            now = null;
        }
        if (now != null) {
            final Object key = keyOf(now);
            if (!key.equals(shownKey)) {
                // A new spot or state: a short settle from 60% (from 0 when nothing was shown), so sweeping the
                // crosshair over blocks feels responsive without blinking.
                FADE.snap(shown == null ? 0F : Math.min(FADE.get(), 0.6F));
                FADE.set(1F);
                shownKey = key;
            } else if (FADE.target() < 1F) {
                FADE.set(1F);
            }
            shown = now;
        } else if (shown != null) {
            FADE.set(0F);
            shownKey = null;
        }
        if (shown == null) return;
        final float fade = FADE.get();
        if (fade <= 0.01F && FADE.target() == 0F) {
            shown = null;
            return;
        }
        for (int i = 0; i < shown.ghosts().size(); i++) GhostRenderer.submit(shown.ghosts().get(i), fade * shown.weights()[i]);
    }

    private static Object keyOf(final Target t) {
        final List<Object> key = new ArrayList<>(t.ghosts().size() * 3);
        for (final GhostRenderer.Ghost g : t.ghosts()) {
            key.add(g.pos());
            key.add(g.state());
            key.add(g.style());
        }
        return key;
    }

    private static @Nullable Target compute() {
        final Minecraft mc = Minecraft.getInstance();
        final PreviewSettings settings = PreviewSettings.current();
        final LocalPlayer player = mc.player;
        final Level level = mc.level;
        if (!settings.enabled || player == null || level == null || mc.screen != null || mc.gameMode == null) return null;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        if (player.isSpectator() || !player.mayBuild()) return null;
        if (ClientModeState.isActive() && ClientModeState.pending() != ClientModeState.Pending.NONE) return null;
        if (RenderCompat.bridgingAssistActive()) return null;

        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof BlockItem) && stack.isEmpty()) {
            hand = InteractionHand.OFF_HAND;
            stack = player.getOffhandItem();
        }
        if (!(stack.getItem() instanceof BlockItem item)) return null;
        final boolean variant = isVariant(stack, item);
        if (!variant && !settings.allBlocks) return null;

        final BlockPos clicked = hit.getBlockPos();
        if (!player.isSecondaryUseActive() && interactive(level.getBlockState(clicked), level, clicked)) return null;
        if (!player.mayUseItemAt(clicked.relative(hit.getDirection()), hit.getDirection(), stack)) return null;

        final BlockPlaceContext base = new BlockPlaceContext(player, hand, stack, hit);
        if (!base.canPlace()) return null;
        final BlockPlaceContext ctx = item.updatePlacementContext(base);
        if (ctx == null) return null;

        BlockState material = null;
        BlockState state;
        if (item.getBlock() instanceof ShapeBlock shape) {
            final Block materialBlock = ShapeModels.materialOf(stack);
            if (materialBlock == null) return null;   // a shape item without a material places nothing
            material = materialBlock.defaultBlockState();
            state = VariantRegistry.get().placementState(new Variant(materialBlock, shape.shape()), ctx);
            if (state == null) state = ((BlockItemInvoker) item).slateBuilding$getPlacementState(ctx);
        } else {
            state = ((BlockItemInvoker) item).slateBuilding$getPlacementState(ctx);
        }
        GhostRenderer.Style style = GhostRenderer.Style.PLACE;
        if (state == null) {
            // What it would place if nothing were in the way: shown as "cannot place here".
            state = item.getBlock().getStateForPlacement(ctx);
            if (state == null) return null;
            style = GhostRenderer.Style.INVALID;
        }

        final BlockPos pos = ctx.getClickedPos();
        final List<GhostRenderer.Ghost> ghosts = new ArrayList<>(4);
        final List<Float> weights = new ArrayList<>(4);
        addWithPartner(ghosts, weights, pos, state, material, style, 1F);

        final ClientModeState.Symmetry symmetry = ClientModeState.symmetry();
        if (settings.showMirrored && symmetry != null && style == GhostRenderer.Style.PLACE) {
            for (final SymmetryPreview.Image image : SymmetryPreview.images(symmetry, pos, state)) {
                if (!level.isLoaded(image.pos()) || !level.getBlockState(image.pos()).canBeReplaced()) continue;
                addWithPartner(ghosts, weights, image.pos(), image.state(), material, GhostRenderer.Style.PLACE, 0.7F);
            }
        }
        final float[] w = new float[weights.size()];
        for (int i = 0; i < w.length; i++) w[i] = weights.get(i);
        return new Target(List.copyOf(ghosts), w);
    }

    /** Adds the ghost and, for two-block placements, the second half (door / tall plant top, bed head). */
    private static void addWithPartner(final List<GhostRenderer.Ghost> ghosts, final List<Float> weights, final BlockPos pos, final BlockState state,
                                       final @Nullable BlockState material, final GhostRenderer.Style style, final float weight) {
        ghosts.add(new GhostRenderer.Ghost(pos, state, material, style));
        weights.add(weight);
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            ghosts.add(new GhostRenderer.Ghost(pos.above(), state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), material, style));
            weights.add(weight);
        } else if (state.getBlock() instanceof BedBlock && state.hasProperty(BedBlock.PART) && state.getValue(BedBlock.PART) == BedPart.FOOT) {
            final Direction facing = state.getValue(BedBlock.FACING);
            ghosts.add(new GhostRenderer.Ghost(pos.relative(facing), state.setValue(BedBlock.PART, BedPart.HEAD), material, style));
            weights.add(weight);
        }
    }

    /**
     * Whether the held item is a variant (a shape of some material other than the full block): the variant registry
     * decides; items it does not know fall back to the classic shape classes (stairs, slabs, walls, fences, gates).
     */
    private static boolean isVariant(final ItemStack stack, final BlockItem item) {
        final Optional<Variant> v = VariantRegistry.get().identify(stack);
        if (v.isPresent()) return !v.get().isFull();
        final Block b = item.getBlock();
        return b instanceof ShapeBlock || b instanceof StairBlock || b instanceof SlabBlock || b instanceof WallBlock
            || b instanceof FenceBlock || b instanceof FenceGateBlock;
    }

    /** Whether right-clicking {@code state} does something itself (opens a menu, flips a door / lever / ...). */
    private static boolean interactive(final BlockState state, final Level level, final BlockPos pos) {
        if (state.isAir()) return false;
        try {
            if (state.getMenuProvider(level, pos) != null) return true;
        } catch (final RuntimeException ignored) {
            // A broken block entity must not take the preview down; treat it as not interactive.
        }
        final Block b = state.getBlock();
        return state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.BUTTONS)
            || b instanceof LeverBlock || b instanceof RepeaterBlock || b instanceof ComparatorBlock || b instanceof NoteBlock
            || b instanceof DaylightDetectorBlock || Objects.equals(b, net.minecraft.world.level.block.Blocks.CAKE);
    }

    private HandPreview() {}
}
