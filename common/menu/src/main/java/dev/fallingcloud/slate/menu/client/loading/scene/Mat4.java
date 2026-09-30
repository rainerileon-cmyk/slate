package dev.fallingcloud.slate.menu.client.loading.scene;

/**
 * The little matrix arithmetic the loading scene needs, on {@code float[16]} in OpenGL's column order. The scene runs
 * before the game's libraries are certain to be there, so it brings its own.
 */
final class Mat4 {

    static float[] identity() {
        final float[] m = new float[16];
        m[0] = m[5] = m[10] = m[15] = 1f;
        return m;
    }

    static void set(final float[] to, final float[] from) {
        System.arraycopy(from, 0, to, 0, 16);
    }

    /** {@code out = a * b}; {@code out} may be {@code a} or {@code b}. */
    static void mul(final float[] out, final float[] a, final float[] b) {
        final float[] r = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int row = 0; row < 4; row++) {
                r[c * 4 + row] = a[row] * b[c * 4] + a[4 + row] * b[c * 4 + 1] + a[8 + row] * b[c * 4 + 2] + a[12 + row] * b[c * 4 + 3];
            }
        }
        System.arraycopy(r, 0, out, 0, 16);
    }

    /**
     * A perspective of {@code fovY} degrees, moved on the screen by {@code shiftX}, {@code shiftY} (in units of half a
     * screen) the way a shift lens does it: the picture slides, its lines stay as they are.
     */
    static float[] perspective(final float fovY, final float aspect, final float near, final float far, final float shiftX, final float shiftY) {
        final float f = (float) (1.0 / Math.tan(Math.toRadians(fovY) / 2.0));
        final float[] m = new float[16];
        m[0] = f / aspect;
        m[5] = f;
        m[8] = -shiftX;
        m[9] = -shiftY;
        m[10] = (far + near) / (near - far);
        m[11] = -1f;
        m[14] = 2f * far * near / (near - far);
        return m;
    }

    static float[] ortho(final float l, final float r, final float b, final float t, final float n, final float f) {
        final float[] m = new float[16];
        m[0] = 2f / (r - l);
        m[5] = 2f / (t - b);
        m[10] = -2f / (f - n);
        m[12] = -(r + l) / (r - l);
        m[13] = -(t + b) / (t - b);
        m[14] = -(f + n) / (f - n);
        m[15] = 1f;
        return m;
    }

    static float[] lookAt(final float ex, final float ey, final float ez, final float tx, final float ty, final float tz,
                          final float ux, final float uy, final float uz) {
        float fx = tx - ex, fy = ty - ey, fz = tz - ez;
        final float fl = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl;
        fy /= fl;
        fz /= fl;
        float sx = fy * uz - fz * uy, sy = fz * ux - fx * uz, sz = fx * uy - fy * ux;
        final float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        sx /= sl;
        sy /= sl;
        sz /= sl;
        final float vx = sy * fz - sz * fy, vy = sz * fx - sx * fz, vz = sx * fy - sy * fx;
        final float[] m = new float[16];
        m[0] = sx;
        m[4] = sy;
        m[8] = sz;
        m[1] = vx;
        m[5] = vy;
        m[9] = vz;
        m[2] = -fx;
        m[6] = -fy;
        m[10] = -fz;
        m[12] = -(sx * ex + sy * ey + sz * ez);
        m[13] = -(vx * ex + vy * ey + vz * ez);
        m[14] = fx * ex + fy * ey + fz * ez;
        m[15] = 1f;
        return m;
    }

    /** {@code m = m * translation}. */
    static void translate(final float[] m, final float x, final float y, final float z) {
        m[12] += m[0] * x + m[4] * y + m[8] * z;
        m[13] += m[1] * x + m[5] * y + m[9] * z;
        m[14] += m[2] * x + m[6] * y + m[10] * z;
        m[15] += m[3] * x + m[7] * y + m[11] * z;
    }

    /** {@code m = m * scale}. */
    static void scale(final float[] m, final float x, final float y, final float z) {
        for (int i = 0; i < 4; i++) {
            m[i] *= x;
            m[4 + i] *= y;
            m[8 + i] *= z;
        }
    }

    /** {@code m = m * rotation} of {@code degrees} round the axis ({@code ax}, {@code ay}, {@code az}), a unit vector. */
    static void rotate(final float[] m, final float degrees, final float ax, final float ay, final float az) {
        final double rad = Math.toRadians(degrees);
        final float c = (float) Math.cos(rad), s = (float) Math.sin(rad), t = 1f - c;
        final float[] r = identity();
        r[0] = t * ax * ax + c;
        r[1] = t * ax * ay + s * az;
        r[2] = t * ax * az - s * ay;
        r[4] = t * ax * ay - s * az;
        r[5] = t * ay * ay + c;
        r[6] = t * ay * az + s * ax;
        r[8] = t * ax * az + s * ay;
        r[9] = t * ay * az - s * ax;
        r[10] = t * az * az + c;
        mul(m, m, r);
    }

    /** The point as the matrix sees it, with its {@code w}: {@code out} takes four values. */
    static void transform(final float[] m, final float x, final float y, final float z, final float[] out) {
        out[0] = m[0] * x + m[4] * y + m[8] * z + m[12];
        out[1] = m[1] * x + m[5] * y + m[9] * z + m[13];
        out[2] = m[2] * x + m[6] * y + m[10] * z + m[14];
        out[3] = m[3] * x + m[7] * y + m[11] * z + m[15];
    }

    private Mat4() {}
}
