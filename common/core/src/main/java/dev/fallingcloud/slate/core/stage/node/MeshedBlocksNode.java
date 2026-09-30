package dev.fallingcloud.slate.core.stage.node;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.stage.StageLevel;
import dev.fallingcloud.slate.core.stage.StageRenderContext;
import dev.fallingcloud.slate.core.stage.StageResources;
import dev.fallingcloud.slate.core.stage.mesh.BlockMesh;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Base of the nodes that own a box of blocks in the stage level and draw it as one cached {@link BlockMesh}:
 * {@link StructureNode} (from an .nbt template) and {@link VoxelNode} (generated). The mesh is built lazily on the
 * render thread and rebuilt when the lighting or the resources change; block entities inside the box (chests,
 * signs, campfires...) are drawn live by their renderers and ticked at 20 Hz. The node's origin is the box's min
 * corner unless {@link #centered()} moves it to the middle of the floor.
 */
public abstract class MeshedBlocksNode extends StageNode {

    protected final StageLevel level;
    protected final BlockPos origin;
    protected Vec3i size = Vec3i.ZERO;
    @Nullable private BlockMesh mesh;
    private int meshLighting = -1, meshResources = -1;
    private boolean dirty = true;
    private final List<BlockEntity> blockEntities = new ArrayList<>();
    private final List<BlockEntityTicker<BlockEntity>> tickers = new ArrayList<>();
    private final List<BlockEntity> scratch = new ArrayList<>();
    private boolean centered;
    private float offsetX, offsetY, offsetZ;
    private boolean customOffset;

    protected MeshedBlocksNode(final StageLevel level) {
        this.level = level;
        this.origin = level.allocateRegion();
    }

    /** Puts the node's origin at the centre of the box floor instead of its min corner. */
    public MeshedBlocksNode centered() {
        this.centered = true;
        this.customOffset = false;
        applyBounds();
        return this;
    }

    /** Draws the box shifted by this offset from the node origin (a planet uses {@code -r,-r,-r} to sit on its centre). */
    public MeshedBlocksNode meshOffset(final float x, final float y, final float z) {
        this.customOffset = true;
        this.offsetX = x;
        this.offsetY = y;
        this.offsetZ = z;
        bounds(x, y, z, x + size.getX(), y + size.getY(), z + size.getZ());
        return this;
    }

    public Vec3i size() { return size; }

    public BlockPos origin() { return origin; }

    /** Places {@code state} at box-relative {@code (x, y, z)} (with optional block-entity NBT). */
    protected void set(final int x, final int y, final int z, final BlockState state, @Nullable final net.minecraft.nbt.CompoundTag nbt) {
        level.place(new BlockPos(origin.getX() + x, origin.getY() + y, origin.getZ() + z), state, nbt);
    }

    protected BlockState get(final int x, final int y, final int z) {
        return level.getBlockState(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
    }

    /** Call after filling: fixes the size, bounds and block-entity list, and schedules a mesh build. */
    @SuppressWarnings("unchecked")
    protected void finishFill(final Vec3i size) {
        this.size = size;
        applyBounds();
        blockEntities.clear();
        tickers.clear();
        final BlockPos max = origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        for (final BlockEntity be : level.blockEntitiesIn(origin, max, scratch)) {
            blockEntities.add(be);
            BlockEntityTicker<BlockEntity> ticker = null;
            final BlockState s = be.getBlockState();
            if (s.getBlock() instanceof EntityBlock eb) {
                try {
                    ticker = eb.getTicker(level, s, (BlockEntityType<BlockEntity>) be.getType());
                } catch (final Exception ignored) {}
            }
            tickers.add(ticker);
        }
        dirty = true;
    }

    private void applyBounds() {
        if (customOffset) {
            bounds(offsetX, offsetY, offsetZ, offsetX + size.getX(), offsetY + size.getY(), offsetZ + size.getZ());
            return;
        }
        offsetX = centered ? -size.getX() / 2f : 0f;
        offsetY = 0f;
        offsetZ = centered ? -size.getZ() / 2f : 0f;
        bounds(offsetX, 0f, offsetZ, offsetX + size.getX(), size.getY(), offsetZ + size.getZ());
    }

    /** Forces a rebuild of the mesh on the next frame (after changing blocks). */
    public void invalidateMesh() { dirty = true; }

    public List<BlockEntity> blockEntities() { return blockEntities; }

    private void ensureMesh(final StageRenderContext ctx) {
        final int lg = ctx.lighting.generation(), rg = StageResources.generation();
        if (mesh != null && !dirty && lg == meshLighting && rg == meshResources) return;
        if (mesh != null) mesh.close();
        try {
            final BlockPos max = origin.offset(Math.max(0, size.getX() - 1), Math.max(0, size.getY() - 1), Math.max(0, size.getZ() - 1));
            mesh = BlockMesh.build(level, origin, max, origin);
        } catch (final Exception e) {
            Slate.LOGGER.error("[Slate] stage: meshing failed", e);
            mesh = null;
        }
        meshLighting = lg;
        meshResources = rg;
        dirty = false;
    }

    @Override
    public void tick(final StageRenderContext ctx) {
        for (int i = 0; i < blockEntities.size(); i++) {
            final BlockEntityTicker<BlockEntity> t = tickers.get(i);
            if (t == null) continue;
            final BlockEntity be = blockEntities.get(i);
            try {
                t.tick(level, be.getBlockPos(), be.getBlockState(), be);
            } catch (final Exception e) {
                tickers.set(i, null);
                Slate.LOGGER.warn("[Slate] stage: ticker of {} failed, stopped: {}", be.getBlockState(), e.toString());
            }
        }
    }

    @Override
    protected void draw(final StageRenderContext ctx) {
        ensureMesh(ctx);
        ctx.pose.pushPose();
        ctx.pose.translate(offsetX, offsetY, offsetZ);
        if (mesh != null) {
            // Block meshes go straight to the GPU; the batched buffer source is untouched.
            mesh.drawOpaque(ctx.pose.last().pose(), ctx.camera.projection());
        }
        if (!blockEntities.isEmpty()) {
            final var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
            for (final BlockEntity be : blockEntities) {
                final BlockEntityRenderer<BlockEntity> r = dispatcher.getRenderer(be);
                if (r == null) continue;
                final BlockPos p = be.getBlockPos();
                ctx.pose.pushPose();
                ctx.pose.translate(p.getX() - origin.getX(), p.getY() - origin.getY(), p.getZ() - origin.getZ());
                try {
                    r.render(be, ctx.partial, ctx.pose, ctx.buffers, ctx.light, OverlayTexture.NO_OVERLAY);
                } catch (final Exception e) {
                    Slate.LOGGER.warn("[Slate] stage: block entity {} failed to render: {}", be.getBlockState(), e.toString());
                }
                ctx.pose.popPose();
            }
        }
        ctx.pose.popPose();
    }

    @Override
    public boolean hasTranslucentPass() { return true; }

    @Override
    protected void drawTranslucent(final StageRenderContext ctx) {
        if (mesh == null) return;
        ctx.pose.pushPose();
        ctx.pose.translate(offsetX, offsetY, offsetZ);
        mesh.drawTranslucent(ctx.pose.last().pose(), ctx.camera.projection());
        ctx.pose.popPose();
    }

    @Override
    public void dispose() {
        if (mesh != null) { mesh.close(); mesh = null; }
        level.clear(origin, origin.offset(Math.max(0, size.getX() - 1), Math.max(0, size.getY() - 1), Math.max(0, size.getZ() - 1)));
        blockEntities.clear();
        tickers.clear();
    }
}
