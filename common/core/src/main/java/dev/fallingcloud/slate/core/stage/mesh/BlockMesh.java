package dev.fallingcloud.slate.core.stage.mesh;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fallingcloud.slate.core.stage.StageLevel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.joml.Matrix4f;

/**
 * A box of stage-level blocks meshed once into static vertex buffers, one per chunk render layer (solid, cutout,
 * cutout-mipped, translucent), exactly like a chunk section: through {@code BlockRenderDispatcher.renderBatched}
 * (real models, ambient occlusion, biome tints, the stage's shade) and {@code renderLiquid} for water and lava.
 * Drawing is one {@code drawWithShader} per layer with the layer's own render state (atlas, lightmap, blend).
 * Coordinates are relative to {@code origin}; the mesh is disposable GPU memory ({@link #close}).
 */
public final class BlockMesh implements AutoCloseable {

    private record Layer(RenderType type, VertexBuffer buffer, boolean translucent) {}

    private final List<Layer> layers = new ArrayList<>();
    private int quads;

    private BlockMesh() {}

    public static BlockMesh build(final StageLevel level, final BlockPos min, final BlockPos max, final BlockPos origin) {
        final BlockMesh mesh = new BlockMesh();
        final Map<RenderType, BufferBuilder> builders = new LinkedHashMap<>();
        final List<ByteBufferBuilder> memory = new ArrayList<>();
        final BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        final PoseStack pose = new PoseStack();
        final RandomSource random = RandomSource.create();
        final OffsetConsumer offset = new OffsetConsumer();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    final BlockState state = level.getBlockState(x, y, z);
                    if (state.isAir()) continue;
                    pos.set(x, y, z);
                    final FluidState fluid = state.getFluidState();
                    if (!fluid.isEmpty()) {
                        final BufferBuilder bb = builder(builders, memory, ItemBlockRenderTypes.getRenderLayer(fluid));
                        // The liquid renderer writes section-local coordinates (pos & 15): shift them to mesh space.
                        offset.wrap(bb, (x & ~15) - origin.getX(), (y & ~15) - origin.getY(), (z & ~15) - origin.getZ());
                        dispatcher.renderLiquid(pos, level, offset, state, fluid);
                    }
                    if (state.getRenderShape() == RenderShape.MODEL) {
                        final BufferBuilder bb = builder(builders, memory, ItemBlockRenderTypes.getChunkRenderType(state));
                        pose.pushPose();
                        pose.translate(x - origin.getX(), y - origin.getY(), z - origin.getZ());
                        dispatcher.renderBatched(state, pos, level, pose, bb, true, random);
                        pose.popPose();
                    }
                }
            }
        }
        for (final Map.Entry<RenderType, BufferBuilder> e : builders.entrySet()) {
            final MeshData data = e.getValue().build();
            if (data == null) continue;
            mesh.quads += data.drawState().vertexCount() / 4;
            final VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vbo.bind();
            vbo.upload(data);
            VertexBuffer.unbind();
            mesh.layers.add(new Layer(e.getKey(), vbo, e.getKey() == RenderType.translucent() || e.getKey() == RenderType.tripwire()));
        }
        for (final ByteBufferBuilder b : memory) b.close();
        mesh.layers.sort((a, b) -> Integer.compare(order(a.type), order(b.type)));
        return mesh;
    }

    private static int order(final RenderType t) {
        if (t == RenderType.solid()) return 0;
        if (t == RenderType.cutoutMipped()) return 1;
        if (t == RenderType.cutout()) return 2;
        if (t == RenderType.translucent()) return 4;
        return 3;
    }

    private static BufferBuilder builder(final Map<RenderType, BufferBuilder> builders, final List<ByteBufferBuilder> memory, final RenderType type) {
        BufferBuilder bb = builders.get(type);
        if (bb == null) {
            final ByteBufferBuilder mem = new ByteBufferBuilder(type.bufferSize());
            memory.add(mem);
            bb = new BufferBuilder(mem, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            builders.put(type, bb);
        }
        return bb;
    }

    /** Draws the opaque and cutout layers. {@code modelView} is view × model. */
    public void drawOpaque(final Matrix4f modelView, final Matrix4f projection) {
        for (final Layer l : layers) if (!l.translucent) draw(l, modelView, projection);
    }

    /** Draws the translucent layers (after everything opaque). */
    public void drawTranslucent(final Matrix4f modelView, final Matrix4f projection) {
        for (final Layer l : layers) if (l.translucent) draw(l, modelView, projection);
    }

    private static void draw(final Layer l, final Matrix4f modelView, final Matrix4f projection) {
        l.type.setupRenderState();
        l.buffer.bind();
        l.buffer.drawWithShader(modelView, projection, RenderSystem.getShader());
        VertexBuffer.unbind();
        l.type.clearRenderState();
    }

    public int quadCount() { return quads; }

    public boolean isEmpty() { return layers.isEmpty(); }

    @Override
    public void close() {
        for (final Layer l : layers) l.buffer.close();
        layers.clear();
    }

    /** Adds a constant offset to every vertex position; everything else passes through. */
    private static final class OffsetConsumer implements VertexConsumer {
        private VertexConsumer delegate;
        private float ox, oy, oz;

        void wrap(final VertexConsumer delegate, final float ox, final float oy, final float oz) {
            this.delegate = delegate;
            this.ox = ox;
            this.oy = oy;
            this.oz = oz;
        }

        @Override public VertexConsumer addVertex(final float x, final float y, final float z) { delegate.addVertex(x + ox, y + oy, z + oz); return this; }
        @Override public VertexConsumer setColor(final int r, final int g, final int b, final int a) { delegate.setColor(r, g, b, a); return this; }
        @Override public VertexConsumer setUv(final float u, final float v) { delegate.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(final int u, final int v) { delegate.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(final int u, final int v) { delegate.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(final float x, final float y, final float z) { delegate.setNormal(x, y, z); return this; }
    }
}
