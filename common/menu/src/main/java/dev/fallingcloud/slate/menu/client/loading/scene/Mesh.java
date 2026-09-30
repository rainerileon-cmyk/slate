package dev.fallingcloud.slate.menu.client.loading.scene;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.lwjgl.system.MemoryUtil;

/**
 * The triangles of one pass of a frame, built on the CPU: boxes as the game's block models have them (a face takes
 * the part of its texture its size covers, 16 texels to the unit, and starts a new tile every unit), cylinders, and
 * single quads, all moved by a matrix stack into the world before they are stored. The scene is small, so it is built
 * anew every frame and nothing has to be kept in step.
 *
 * <p>A vertex is 32 bytes: position, normal (signed bytes), texture coordinates, colour, and four bytes of material:
 * how much it shines by itself, what kind of surface it is ({@link #LIT} and the others), how glossy it is.</p>
 */
final class Mesh {

    static final int STRIDE = 32;
    /** Kinds of surface, the second material byte. */
    static final int LIT = 0, LAVA = 1, GLOW = 2, FLOOR = 3, FLAT = 4;
    /** Faces of a box, for {@link #faces}. */
    static final int TOP = 1, BOTTOM = 2, FRONT = 4, BACK = 8, RIGHT = 16, LEFT = 32, ALL = 63, SIDES = FRONT | BACK | RIGHT | LEFT;

    private ByteBuffer buffer;
    private int vertices;
    /** Where in the frame's one buffer this mesh starts, in vertices: set when the frame is loaded. */
    int first;

    private final float[] m = Mat4.identity();
    private final float[][] stack = new float[24][16];
    private int depth;
    private final float[] p = new float[4];

    private Tile side = Tile.WHITE, top = Tile.WHITE, bottom = Tile.WHITE;
    private int colour = 0xFFFFFFFF;
    private int emissive, kind, gloss;
    private int faces = ALL;
    /** The light the lower edge of an upright face keeps; 1 is none taken. */
    private float ground = 1f;
    /** Where on its tile a face starts, in texels. */
    private float texelU, texelV;

    Mesh(final int capacity) {
        buffer = MemoryUtil.memAlloc(capacity * STRIDE).order(ByteOrder.nativeOrder());
    }

    void free() {
        MemoryUtil.memFree(buffer);
        buffer = null;
    }

    void clear() {
        buffer.clear();
        vertices = 0;
        depth = 0;
        Mat4.set(m, IDENTITY);
        reset();
    }

    /** The material back to plain: white, lit, all faces, no shading of its own. */
    void reset() {
        side = top = bottom = Tile.WHITE;
        colour = 0xFFFFFFFF;
        emissive = 0;
        kind = LIT;
        gloss = 0;
        faces = ALL;
        ground = 1f;
        texelU = texelV = 0;
    }

    private static final float[] IDENTITY = Mat4.identity();

    int vertices() {
        return vertices;
    }

    /** All of another mesh after what is here, as it is: it has been moved into the world already. */
    void add(final Mesh other) {
        final ByteBuffer from = other.data();
        while (buffer.remaining() < from.remaining()) grow();
        buffer.put(from);
        vertices += other.vertices;
    }

    /** The bytes written so far, ready to be uploaded. */
    ByteBuffer data() {
        final ByteBuffer view = buffer.duplicate().order(ByteOrder.nativeOrder());
        view.flip();
        return view;
    }

    // ------------------------------------------------------------------ the matrix stack

    void push() {
        Mat4.set(stack[depth++], m);
    }

    void pop() {
        Mat4.set(m, stack[--depth]);
    }

    /** The matrix another mesh is moving its things by, for things of this one that belong to them. */
    void copyMatrix(final Mesh from) {
        Mat4.set(m, from.m);
    }

    void translate(final float x, final float y, final float z) {
        Mat4.translate(m, x, y, z);
    }

    void rotateX(final float degrees) {
        Mat4.rotate(m, degrees, 1, 0, 0);
    }

    void rotateY(final float degrees) {
        Mat4.rotate(m, degrees, 0, 1, 0);
    }

    void rotateZ(final float degrees) {
        Mat4.rotate(m, degrees, 0, 0, 1);
    }

    /** The same in every direction: normals stay what they are. */
    void scale(final float s) {
        Mat4.scale(m, s, s, s);
    }

    /** Each axis by its own: for faces that look along the axes, whose normals this leaves where they point. */
    void scale(final float x, final float y, final float z) {
        Mat4.scale(m, x, y, z);
    }

    // ------------------------------------------------------------------ the material

    Mesh tile(final Tile all) {
        side = top = bottom = all;
        return this;
    }

    Mesh tiles(final Tile side, final Tile top, final Tile bottom) {
        this.side = side;
        this.top = top;
        this.bottom = bottom;
        return this;
    }

    Mesh colour(final int argb) {
        this.colour = argb;
        return this;
    }

    /** 0 to 1: how much of its own colour the surface gives off whatever the light. */
    Mesh emissive(final float amount) {
        this.emissive = Math.round(clamp(amount) * 255f);
        return this;
    }

    Mesh kind(final int kind) {
        this.kind = kind;
        return this;
    }

    /** 0 to 1: how much of a highlight the key light leaves on the surface. */
    Mesh gloss(final float amount) {
        this.gloss = Math.round(clamp(amount) * 255f);
        return this;
    }

    Mesh faces(final int mask) {
        this.faces = mask;
        return this;
    }

    /** Upright faces darken towards their lower edge down to {@code light}, as where a thing stands on another. */
    Mesh ground(final float light) {
        this.ground = light;
        return this;
    }

    /** Faces start this many texels into their tile instead of at its corner. */
    Mesh texel(final float u, final float v) {
        this.texelU = u;
        this.texelV = v;
        return this;
    }

    private static float clamp(final float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    // ------------------------------------------------------------------ shapes

    /** A box from corner to corner, textured as a block model. */
    void box(final float x0, final float y0, final float z0, final float x1, final float y1, final float z1) {
        if ((faces & TOP) != 0) face(top, x0, y1, z0, 1, 0, 0, 0, 0, 1, x1 - x0, z1 - z0, 0, 1, 0, 1f, 1f);
        if ((faces & BOTTOM) != 0) face(bottom, x0, y0, z1, 1, 0, 0, 0, 0, -1, x1 - x0, z1 - z0, 0, -1, 0, ground, ground);
        if ((faces & FRONT) != 0) face(side, x0, y1, z1, 1, 0, 0, 0, -1, 0, x1 - x0, y1 - y0, 0, 0, 1, 1f, ground);
        if ((faces & BACK) != 0) face(side, x1, y1, z0, -1, 0, 0, 0, -1, 0, x1 - x0, y1 - y0, 0, 0, -1, 1f, ground);
        if ((faces & RIGHT) != 0) face(side, x1, y1, z1, 0, 0, -1, 0, -1, 0, z1 - z0, y1 - y0, 1, 0, 0, 1f, ground);
        if ((faces & LEFT) != 0) face(side, x0, y1, z0, 0, 0, 1, 0, -1, 0, z1 - z0, y1 - y0, -1, 0, 0, 1f, ground);
    }

    /**
     * One face from its corner at texture (0, 0): {@code w} along the u direction, {@code h} along the v direction,
     * cut into tiles of a unit. {@code lightTop} and {@code lightBottom} are the light it keeps at v = 0 and v = h.
     */
    private void face(final Tile tile, final float ox, final float oy, final float oz,
                      final float ux, final float uy, final float uz, final float vx, final float vy, final float vz,
                      final float w, final float h, final float nx, final float ny, final float nz,
                      final float lightTop, final float lightBottom) {
        if (w <= 0 || h <= 0) return;
        final float startU = texelU / 16f, startV = texelV / 16f;
        float a = 0;
        while (a < w - 1e-5f) {
            final float tu = into(a + startU);
            final float spanA = Math.min(w - a, 1f - tu);
            float b = 0;
            while (b < h - 1e-5f) {
                final float tv = into(b + startV);
                final float spanB = Math.min(h - b, 1f - tv);
                final float a1 = a + spanA, b1 = b + spanB;
                final float l0 = lightTop + (lightBottom - lightTop) * (b / h), l1 = lightTop + (lightBottom - lightTop) * (b1 / h);
                final float u0 = tile.u(tu), u1 = tile.u(tu + spanA), v0 = tile.v(tv), v1 = tile.v(tv + spanB);
                // Counter-clockwise seen from the side the normal points to.
                vertex(ox + ux * a + vx * b, oy + uy * a + vy * b, oz + uz * a + vz * b, nx, ny, nz, u0, v0, l0);
                vertex(ox + ux * a + vx * b1, oy + uy * a + vy * b1, oz + uz * a + vz * b1, nx, ny, nz, u0, v1, l1);
                vertex(ox + ux * a1 + vx * b1, oy + uy * a1 + vy * b1, oz + uz * a1 + vz * b1, nx, ny, nz, u1, v1, l1);
                vertex(ox + ux * a + vx * b, oy + uy * a + vy * b, oz + uz * a + vz * b, nx, ny, nz, u0, v0, l0);
                vertex(ox + ux * a1 + vx * b1, oy + uy * a1 + vy * b1, oz + uz * a1 + vz * b1, nx, ny, nz, u1, v1, l1);
                vertex(ox + ux * a1 + vx * b, oy + uy * a1 + vy * b, oz + uz * a1 + vz * b, nx, ny, nz, u1, v0, l0);
                b = b1;
            }
            a += spanA;
        }
    }

    /** How far into its tile a face is at {@code at}; what is a hair before the next tile is at its start. */
    private static float into(final float at) {
        final float t = at - (float) Math.floor(at);
        return t > 1f - 1e-4f ? 0f : t;
    }

    /**
     * A single quad, its corners counter-clockwise seen from its front, with the current side tile laid over it whole
     * (or the part of it {@code u0..v1} names, 0 to 1 within the tile).
     */
    void quad(final float ax, final float ay, final float az, final float bx, final float by, final float bz,
              final float cx, final float cy, final float cz, final float dx, final float dy, final float dz,
              final float u0, final float v0, final float u1, final float v1) {
        // The normal from the corners.
        final float e1x = bx - ax, e1y = by - ay, e1z = bz - az, e2x = dx - ax, e2y = dy - ay, e2z = dz - az;
        float nx = e1y * e2z - e1z * e2y, ny = e1z * e2x - e1x * e2z, nz = e1x * e2y - e1y * e2x;
        final float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l > 0) {
            nx /= l;
            ny /= l;
            nz /= l;
        }
        final float tu0 = side.u(u0), tu1 = side.u(u1), tv0 = side.v(v0), tv1 = side.v(v1);
        vertex(ax, ay, az, nx, ny, nz, tu0, tv0, 1f);
        vertex(bx, by, bz, nx, ny, nz, tu0, tv1, 1f);
        vertex(cx, cy, cz, nx, ny, nz, tu1, tv1, 1f);
        vertex(ax, ay, az, nx, ny, nz, tu0, tv0, 1f);
        vertex(cx, cy, cz, nx, ny, nz, tu1, tv1, 1f);
        vertex(dx, dy, dz, nx, ny, nz, tu1, tv0, 1f);
    }

    /**
     * A quad whose texture coordinates are not from the atlas but what the surface's kind makes of them: texels for
     * {@link #LAVA}, 0 to 1 across for {@link #GLOW}, anything for {@link #FLOOR}.
     */
    void rawQuad(final float ax, final float ay, final float az, final float bx, final float by, final float bz,
                 final float cx, final float cy, final float cz, final float dx, final float dy, final float dz,
                 final float nx, final float ny, final float nz,
                 final float au, final float av, final float bu, final float bv, final float cu, final float cv, final float du, final float dv) {
        vertex(ax, ay, az, nx, ny, nz, au, av, 1f);
        vertex(bx, by, bz, nx, ny, nz, bu, bv, 1f);
        vertex(cx, cy, cz, nx, ny, nz, cu, cv, 1f);
        vertex(ax, ay, az, nx, ny, nz, au, av, 1f);
        vertex(cx, cy, cz, nx, ny, nz, cu, cv, 1f);
        vertex(dx, dy, dz, nx, ny, nz, du, dv, 1f);
    }

    /**
     * A cylinder along z, round enough with {@code segments} sides and shaded as if it were round. Its mantle takes the
     * side tile once round, its ends the top tile.
     */
    void cylinderZ(final float cx, final float cy, final float z0, final float z1, final float radius, final int segments) {
        final float length = z1 - z0;
        for (int i = 0; i < segments; i++) {
            final double a0 = Math.PI * 2 * i / segments, a1 = Math.PI * 2 * (i + 1) / segments;
            final float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            final float x0 = cx + c0 * radius, y0 = cy + s0 * radius, x1 = cx + c1 * radius, y1 = cy + s1 * radius;
            if ((faces & SIDES) != 0) {
                final float v0 = side.v(i / (float) segments), v1 = side.v((i + 1) / (float) segments);
                final float uEnd = side.u(Math.min(1f, length));
                final float uStart = side.u(0);
                vertex(x0, y0, z1, c0, s0, 0, uStart, v0, 1f);
                vertex(x0, y0, z0, c0, s0, 0, uEnd, v0, 1f);
                vertex(x1, y1, z0, c1, s1, 0, uEnd, v1, 1f);
                vertex(x0, y0, z1, c0, s0, 0, uStart, v0, 1f);
                vertex(x1, y1, z0, c1, s1, 0, uEnd, v1, 1f);
                vertex(x1, y1, z1, c1, s1, 0, uStart, v1, 1f);
            }
            if ((faces & FRONT) != 0) {
                vertex(cx, cy, z1, 0, 0, 1, top.u(0.5f), top.v(0.5f), 1f);
                vertex(x0, y0, z1, 0, 0, 1, top.u(0.5f + c0 * 0.5f), top.v(0.5f - s0 * 0.5f), 1f);
                vertex(x1, y1, z1, 0, 0, 1, top.u(0.5f + c1 * 0.5f), top.v(0.5f - s1 * 0.5f), 1f);
            }
            if ((faces & BACK) != 0) {
                vertex(cx, cy, z0, 0, 0, -1, top.u(0.5f), top.v(0.5f), 1f);
                vertex(x1, y1, z0, 0, 0, -1, top.u(0.5f - c1 * 0.5f), top.v(0.5f - s1 * 0.5f), 1f);
                vertex(x0, y0, z0, 0, 0, -1, top.u(0.5f - c0 * 0.5f), top.v(0.5f - s0 * 0.5f), 1f);
            }
        }
    }

    /** One corner, moved into the world by the matrix on top of the stack. */
    void vertex(final float x, final float y, final float z, final float nx, final float ny, final float nz,
                final float u, final float v, final float light) {
        if (buffer.remaining() < STRIDE) grow();
        Mat4.transform(m, x, y, z, p);
        float wx = m[0] * nx + m[4] * ny + m[8] * nz, wy = m[1] * nx + m[5] * ny + m[9] * nz, wz = m[2] * nx + m[6] * ny + m[10] * nz;
        final float l = (float) Math.sqrt(wx * wx + wy * wy + wz * wz);
        if (l > 0) {
            wx /= l;
            wy /= l;
            wz /= l;
        }
        buffer.putFloat(p[0]).putFloat(p[1]).putFloat(p[2]);
        buffer.put((byte) Math.round(wx * 127f)).put((byte) Math.round(wy * 127f)).put((byte) Math.round(wz * 127f)).put((byte) 0);
        buffer.putFloat(u).putFloat(v);
        final int r = (colour >>> 16) & 0xFF, g = (colour >>> 8) & 0xFF, b = colour & 0xFF, a = colour >>> 24;
        buffer.put((byte) Math.round(r * light)).put((byte) Math.round(g * light)).put((byte) Math.round(b * light)).put((byte) a);
        buffer.put((byte) emissive).put((byte) (kind * 32)).put((byte) gloss).put((byte) 0);
        vertices++;
    }

    private void grow() {
        final ByteBuffer bigger = MemoryUtil.memAlloc(buffer.capacity() * 2).order(ByteOrder.nativeOrder());
        buffer.flip();
        bigger.put(buffer);
        MemoryUtil.memFree(buffer);
        buffer = bigger;
    }
}
