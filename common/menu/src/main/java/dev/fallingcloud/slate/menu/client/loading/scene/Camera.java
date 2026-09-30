package dev.fallingcloud.slate.menu.client.loading.scene;

/** A camera that has been moved back until what it looks at fits the part of the screen it is given. */
final class Camera {

    final float[] view = new float[16], viewProj = new float[16], eye = new float[3];
    final float[] right = new float[3], up = new float[3];
    private final float[] clip = new float[4];

    /**
     * @param target  what it looks at
     * @param yaw     degrees it stands to the left of straight in front
     * @param pitch   degrees it stands above level
     * @param corners x, y, z of every point that has to be in the picture
     * @param box     the part of the screen to fill, in units of half a screen from its middle: left, right,
     *                bottom, top
     * @param settle  where in the box the picture sits when it does not fill its height: 0 on its bottom, 0.5
     *                in its middle
     */
    void fit(final float[] target, final float yaw, final float pitch, final float fov, final float aspect,
             final float[] corners, final float[] box, final float settle) {
        final double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        final float dx = (float) (-Math.sin(y) * Math.cos(p)), dy = (float) Math.sin(p), dz = (float) (Math.cos(y) * Math.cos(p));
        float near = 1f, far = 400f;
        final float[] extent = new float[4];
        for (int i = 0; i < 40; i++) {
            final float d = (near + far) / 2f;
            measure(target, dx, dy, dz, d, fov, aspect, corners, extent);
            final boolean fits = extent[1] - extent[0] <= box[1] - box[0] && extent[3] - extent[2] <= box[3] - box[2];
            if (fits) far = d;
            else near = d;
        }
        final float d = far;
        measure(target, dx, dy, dz, d, fov, aspect, corners, extent);
        final float shiftX = (box[0] + box[1]) / 2f - (extent[0] + extent[1]) / 2f;
        final float room = (box[3] - box[2]) - (extent[3] - extent[2]);
        final float shiftY = box[2] + room * settle - extent[2];
        eye[0] = target[0] + dx * d;
        eye[1] = target[1] + dy * d;
        eye[2] = target[2] + dz * d;
        Mat4.set(view, Mat4.lookAt(eye[0], eye[1], eye[2], target[0], target[1], target[2], 0, 1, 0));
        final float[] proj = Mat4.perspective(fov, aspect, Math.max(0.5f, d - 40f), d + 80f, shiftX, shiftY);
        Mat4.mul(viewProj, proj, view);
        right[0] = view[0];
        right[1] = view[4];
        right[2] = view[8];
        up[0] = view[1];
        up[1] = view[5];
        up[2] = view[9];
    }

    private void measure(final float[] target, final float dx, final float dy, final float dz, final float d, final float fov,
                         final float aspect, final float[] corners, final float[] extent) {
        final float[] v = Mat4.lookAt(target[0] + dx * d, target[1] + dy * d, target[2] + dz * d, target[0], target[1], target[2], 0, 1, 0);
        final float[] vp = new float[16];
        Mat4.mul(vp, Mat4.perspective(fov, aspect, 0.5f, d + 80f, 0, 0), v);
        extent[0] = extent[2] = Float.MAX_VALUE;
        extent[1] = extent[3] = -Float.MAX_VALUE;
        for (int i = 0; i + 2 < corners.length; i += 3) {
            Mat4.transform(vp, corners[i], corners[i + 1], corners[i + 2], clip);
            if (clip[3] <= 0.01f) {
                extent[0] = extent[2] = -1e6f;
                extent[1] = extent[3] = 1e6f;
                return;
            }
            final float x = clip[0] / clip[3], yy = clip[1] / clip[3];
            extent[0] = Math.min(extent[0], x);
            extent[1] = Math.max(extent[1], x);
            extent[2] = Math.min(extent[2], yy);
            extent[3] = Math.max(extent[3], yy);
        }
    }

    /** Where a point of the world is on a screen of {@code w} x {@code h} GUI units: {@code out} takes x and y. */
    void project(final float x, final float y, final float z, final float w, final float h, final float[] out) {
        Mat4.transform(viewProj, x, y, z, clip);
        out[0] = (clip[0] / clip[3] * 0.5f + 0.5f) * w;
        out[1] = (1f - (clip[1] / clip[3] * 0.5f + 0.5f)) * h;
    }

    /** The corners of a box, for {@link #fit}. */
    static float[] box(final float x0, final float y0, final float z0, final float x1, final float y1, final float z1) {
        return new float[] {x0, y0, z0, x1, y0, z0, x0, y1, z0, x1, y1, z0, x0, y0, z1, x1, y0, z1, x0, y1, z1, x1, y1, z1};
    }
}
