package dev.fallingcloud.slate.core.stage.mesh;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fallingcloud.slate.core.stage.StageSoft;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Hand-built geometry on the block atlas: unit faces of voxels, each with a sprite (or a window of one) and a tint.
 * Unlike {@link BlockMesh}, which meshes real block states, this draws whatever the caller says, so a face can show
 * grass whichever way it points: what a cube planet needs.
 *
 * <p>Faces are kept in one vertex buffer per layer (opaque, translucent) and direction. The shade is not baked: the
 * owner passes it per direction when drawing, so a turning object is lit for the way each of its sides faces now.</p>
 */
public final class QuadMesh implements AutoCloseable {

    /** What a direction's faces are multiplied by this frame. */
    @FunctionalInterface
    public interface Shade {
        float of(Direction direction);
    }

    private static final Direction[] DIRECTIONS = Direction.values();

    private final VertexBuffer[] opaque = new VertexBuffer[6];
    private final VertexBuffer[] translucent = new VertexBuffer[6];
    private int quads;

    private QuadMesh() {}

    public static Builder builder() {
        return new Builder();
    }

    /** A sprite of the block atlas ({@code minecraft:block/stone}); the missing-texture sprite when it does not exist. */
    public static TextureAtlasSprite sprite(final ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id);
    }

    public static TextureAtlasSprite sprite(final String blockTexture) {
        return sprite(ResourceLocation.withDefaultNamespace("block/" + blockTexture));
    }

    public int quadCount() { return quads; }

    /**
     * Draws one layer. {@code modelView} is view × model; every direction's faces are multiplied by
     * {@code brightness × shade.of(direction)}, and by {@code alpha}. Leaves the shader colour as it found it.
     */
    public void draw(final boolean translucentLayer, final Matrix4f modelView, final Matrix4f projection, final Shade shade,
                     final float brightness, final float alpha) {
        final VertexBuffer[] buffers = translucentLayer ? translucent : opaque;
        final RenderType type = translucentLayer ? RenderType.translucent() : RenderType.cutoutMipped();
        final float[] was = RenderSystem.getShaderColor().clone();
        boolean any = false;
        for (int i = 0; i < 6; i++) {
            final VertexBuffer vbo = buffers[i];
            if (vbo == null) continue;
            if (!any) {
                type.setupRenderState();
                any = true;
            }
            final float s = brightness * shade.of(DIRECTIONS[i]);
            RenderSystem.setShaderColor(s, s, s, alpha);
            vbo.bind();
            vbo.drawWithShader(modelView, projection, RenderSystem.getShader());
        }
        if (any) {
            VertexBuffer.unbind();
            type.clearRenderState();
        }
        RenderSystem.setShaderColor(was[0], was[1], was[2], was[3]);
    }

    /** Draws one layer with the soft stage shader, which lights every face by its own normal. */
    public void drawSoft(final boolean translucentLayer, final Matrix4f modelView, final Matrix4f projection, final float alpha) {
        final VertexBuffer[] buffers = translucentLayer ? translucent : opaque;
        for (int i = 0; i < 6; i++) {
            if (buffers[i] != null) StageSoft.draw(buffers[i], modelView, projection, TextureAtlas.LOCATION_BLOCKS, alpha, translucentLayer);
        }
    }

    @Override
    public void close() {
        for (int i = 0; i < 6; i++) {
            if (opaque[i] != null) { opaque[i].close(); opaque[i] = null; }
            if (translucent[i] != null) { translucent[i].close(); translucent[i] = null; }
        }
    }

    /** Collects faces; {@link #build()} uploads them. Use on the render thread. */
    public static final class Builder {

        private final BufferBuilder[] opaque = new BufferBuilder[6];
        private final BufferBuilder[] translucent = new BufferBuilder[6];
        private final List<ByteBufferBuilder> memory = new ArrayList<>();
        private int quads;

        private Builder() {}

        private BufferBuilder buffer(final boolean translucentLayer, final Direction face) {
            final BufferBuilder[] set = translucentLayer ? translucent : opaque;
            BufferBuilder bb = set[face.ordinal()];
            if (bb == null) {
                final ByteBufferBuilder mem = new ByteBufferBuilder(1 << 14);
                memory.add(mem);
                bb = new BufferBuilder(mem, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
                set[face.ordinal()] = bb;
            }
            return bb;
        }

        /**
         * The {@code face} side of the unit cell at {@code (x, y, z)}. The sprite is laid over the cells in windows of
         * {@code texels} texels a cell (16 = the whole texture on every face, 4 = one texture across four cells), so
         * neighbouring faces continue each other's texture. {@code argb} tints it (white = as the texture is).
         */
        public Builder face(final boolean translucentLayer, final Direction face, final int x, final int y, final int z,
                            final TextureAtlasSprite sprite, final int texels, final int argb) {
            final BufferBuilder bb = buffer(translucentLayer, face);
            final int a, b;                       // the cell's place in the face's own plane
            switch (face.getAxis()) {
                case Y -> { a = x; b = z; }
                case Z -> { a = x; b = y; }
                default -> { a = z; b = y; }
            }
            final int t = Math.max(1, Math.min(16, texels));
            final int span = Math.max(1, 16 / t);
            final float u0 = Math.floorMod(a, span) * t / 16f, u1 = u0 + t / 16f;
            // On an upright face the texture runs downwards while the cells count upwards.
            final int row = face.getAxis() == Direction.Axis.Y ? Math.floorMod(b, span) : span - 1 - Math.floorMod(b, span);
            final float v0 = row * t / 16f, v1 = v0 + t / 16f;
            final float x1 = x + 1f, y1 = y + 1f, z1 = z + 1f;
            final float nx = face.getStepX(), ny = face.getStepY(), nz = face.getStepZ();
            // Corners counter-clockwise seen from outside; (ua, vb) per corner picks the low or high end of the window.
            switch (face) {
                case UP -> {
                    vertex(bb, sprite, x, y1, z, u0, v0, argb, nx, ny, nz);
                    vertex(bb, sprite, x, y1, z1, u0, v1, argb, nx, ny, nz);
                    vertex(bb, sprite, x1, y1, z1, u1, v1, argb, nx, ny, nz);
                    vertex(bb, sprite, x1, y1, z, u1, v0, argb, nx, ny, nz);
                }
                case DOWN -> {
                    vertex(bb, sprite, x, y, z1, u0, v1, argb, nx, ny, nz);
                    vertex(bb, sprite, x, y, z, u0, v0, argb, nx, ny, nz);
                    vertex(bb, sprite, x1, y, z, u1, v0, argb, nx, ny, nz);
                    vertex(bb, sprite, x1, y, z1, u1, v1, argb, nx, ny, nz);
                }
                case NORTH -> {
                    vertex(bb, sprite, x, y, z, u0, v1, argb, nx, ny, nz);
                    vertex(bb, sprite, x, y1, z, u0, v0, argb, nx, ny, nz);
                    vertex(bb, sprite, x1, y1, z, u1, v0, argb, nx, ny, nz);
                    vertex(bb, sprite, x1, y, z, u1, v1, argb, nx, ny, nz);
                }
                case SOUTH -> {
                    vertex(bb, sprite, x1, y, z1, u1, v1, argb, nx, ny, nz);
                    vertex(bb, sprite, x1, y1, z1, u1, v0, argb, nx, ny, nz);
                    vertex(bb, sprite, x, y1, z1, u0, v0, argb, nx, ny, nz);
                    vertex(bb, sprite, x, y, z1, u0, v1, argb, nx, ny, nz);
                }
                case WEST -> {
                    vertex(bb, sprite, x, y, z1, u1, v1, argb, nx, ny, nz);
                    vertex(bb, sprite, x, y1, z1, u1, v0, argb, nx, ny, nz);
                    vertex(bb, sprite, x, y1, z, u0, v0, argb, nx, ny, nz);
                    vertex(bb, sprite, x, y, z, u0, v1, argb, nx, ny, nz);
                }
                default -> {
                    vertex(bb, sprite, x1, y, z, u0, v1, argb, nx, ny, nz);
                    vertex(bb, sprite, x1, y1, z, u0, v0, argb, nx, ny, nz);
                    vertex(bb, sprite, x1, y1, z1, u1, v0, argb, nx, ny, nz);
                    vertex(bb, sprite, x1, y, z1, u1, v1, argb, nx, ny, nz);
                }
            }
            quads++;
            return this;
        }

        private static void vertex(final BufferBuilder bb, final TextureAtlasSprite sprite, final float x, final float y, final float z,
                                   final float u, final float v, final int argb, final float nx, final float ny, final float nz) {
            bb.addVertex(x, y, z).setColor(argb).setUv(sprite.getU(u), sprite.getV(v)).setLight(LightTexture.FULL_BRIGHT).setNormal(nx, ny, nz);
        }

        public QuadMesh build() {
            final QuadMesh mesh = new QuadMesh();
            mesh.quads = quads;
            for (int i = 0; i < 6; i++) {
                mesh.opaque[i] = upload(opaque[i]);
                mesh.translucent[i] = upload(translucent[i]);
            }
            for (final ByteBufferBuilder b : memory) b.close();
            memory.clear();
            return mesh;
        }

        @Nullable
        private static VertexBuffer upload(@Nullable final BufferBuilder bb) {
            if (bb == null) return null;
            final MeshData data = bb.build();
            if (data == null) return null;
            final VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vbo.bind();
            vbo.upload(data);
            VertexBuffer.unbind();
            return vbo;
        }
    }
}
