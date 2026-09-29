package dev.fallingcloud.slate.core.stage.node;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.stage.StageLevel;
import dev.fallingcloud.slate.core.stage.StageRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * One block state, drawn through the real block model (with the stage level's tint, shade and light) and, when the
 * block has a block entity, through its block-entity renderer too. Chests keep a real {@code ChestBlockEntity}, so
 * {@link #open} swings the lid with vanilla's own lid controller (10 ticks); signs get their text with
 * {@link #signText}. The block lives at a private spot in the stage level so its ticker and renderer see a normal
 * world around it (air), and it is drawn centred: -0.5..0.5 on X/Z, 0..1 on Y.
 */
public class BlockNode extends StageNode {

    private final StageLevel level;
    private final BlockPos levelPos;
    private BlockState state;
    @Nullable private BlockEntity blockEntity;
    @Nullable private BlockEntityTicker<BlockEntity> ticker;
    private boolean tickerBroken;
    private boolean open;
    private final RandomSource random = RandomSource.create(42L);

    public BlockNode(final StageLevel level, final BlockState state) {
        this.level = level;
        this.levelPos = level.allocateRegion();
        setState(state);
        named(state.getBlock().getName());
    }

    public BlockNode state(final BlockState s) { setState(s); return this; }

    public BlockState state() { return state; }

    @Nullable public BlockEntity blockEntity() { return blockEntity; }

    @SuppressWarnings("unchecked")
    private void setState(final BlockState s) {
        state = s;
        level.place(levelPos, s, null);
        blockEntity = level.getBlockEntity(levelPos);
        ticker = null;
        tickerBroken = false;
        if (blockEntity != null && s.getBlock() instanceof EntityBlock eb) {
            try {
                ticker = eb.getTicker(level, s, (BlockEntityType<BlockEntity>) blockEntity.getType());
            } catch (final Exception e) {
                ticker = null;
            }
        }
    }

    /** Chests: swing the lid open or shut with the real lid animation. */
    public BlockNode open(final boolean open) {
        this.open = open;
        if (blockEntity instanceof ChestBlockEntity chest) chest.triggerEvent(1, open ? 1 : 0);
        return this;
    }

    public boolean isOpen() { return open; }

    public BlockNode toggle() { return open(!open); }

    /** Signs: the front text (up to four lines). */
    public BlockNode signText(final DyeColor color, final boolean glowing, final Component... lines) {
        if (blockEntity instanceof SignBlockEntity sign) {
            SignText text = new SignText().setColor(color).setHasGlowingText(glowing);
            for (int i = 0; i < Math.min(4, lines.length); i++) text = text.setMessage(i, lines[i]);
            sign.setText(text, true);
        }
        return this;
    }

    public BlockNode signText(final Component... lines) {
        return signText(DyeColor.BLACK, false, lines);
    }

    @Override
    public void tick(final StageRenderContext ctx) {
        if (ticker == null || tickerBroken || blockEntity == null) return;
        try {
            ticker.tick(level, levelPos, state, blockEntity);
        } catch (final Exception e) {
            tickerBroken = true;
            Slate.LOGGER.warn("[Slate] stage: ticker of {} failed, stopped: {}", state, e.toString());
        }
    }

    @Override
    protected void draw(final StageRenderContext ctx) {
        final Minecraft mc = Minecraft.getInstance();
        ctx.pose.pushPose();
        ctx.pose.translate(-0.5f, 0f, -0.5f);
        if (state.getRenderShape() == RenderShape.MODEL) {
            final RenderType type = alpha < 1f ? RenderType.translucentMovingBlock() : ItemBlockRenderTypes.getMovingBlockRenderType(state);
            final VertexConsumer vc = ctx.buffers.getBuffer(type);
            mc.getBlockRenderer().renderBatched(state, levelPos, level, ctx.pose, vc, true, random);
        }
        if (blockEntity != null) {
            final BlockEntityRenderer<BlockEntity> renderer = mc.getBlockEntityRenderDispatcher().getRenderer(blockEntity);
            if (renderer != null) renderer.render(blockEntity, ctx.partial, ctx.pose, ctx.buffers, ctx.light, OverlayTexture.NO_OVERLAY);
        }
        ctx.pose.popPose();
    }

    @Override
    public void dispose() {
        level.clear(levelPos, levelPos);
        blockEntity = null;
        ticker = null;
    }
}
