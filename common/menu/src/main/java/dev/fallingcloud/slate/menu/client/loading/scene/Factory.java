package dev.fallingcloud.slate.menu.client.loading.scene;

/**
 * The factory that does the loading, as something to be built: where its parts stand (which the scene has to know to
 * move things through them and to put its words over them) and the building of a frame of it. There are two: the
 * scene's own, of textures painted here, and one of the blocks of the Create mod, if the player has it.
 */
abstract class Factory {

    /** The top of the plinth everything stands on. */
    static final float BASE = 0.375f;
    /** Blocks a second a belt runs. */
    static final float BELT_SPEED = 1.9f;

    // ------------------------------------------------------------------ the plan: set by the one that builds

    /** The top of the belts where they are level with the press. */
    float beltY;
    /** The first belt: where a thing comes onto it, and where it hands the thing over to the second. */
    float lumpX0, lumpX1;
    /** The second belt, from where the first hands over, and how far along it the press stands. */
    Path way;
    float pressAt;
    /** Where the press stands. */
    float pressX;
    /** The corners of all of it: what the camera has to see. */
    float[] bounds;
    /** What the camera looks at, and how many degrees over level it stands. */
    final float[] target = new float[3];
    float pitch = 15f;
    /** The plinth: its middle (x, z) and half its size. */
    final float[] stage = new float[4];
    /** Where the memory is written: the middle of the tank's top. */
    final float[] memoryAt = new float[3];
    /** Where the words about the task begin, and where they have to end. */
    final float[] taskFrom = new float[3], taskTo = new float[3];
    /** What rises over the first belt: the words about the task stay clear of these points. */
    float[][] over = new float[0][];
    /** Where how far it all is, is written: over the store. */
    final float[] totalAt = new float[3];

    // ------------------------------------------------------------------ the building

    /** The lamps of this frame: the lava, the store, the press. */
    abstract void lamps(Motion m, SceneGl.Light light, int accent);

    /** All of the factory into the frame. */
    abstract void build(Motion m, Frame frame, int accent);

    /** How long a thing takes from the end of the second belt until it is in the store, in seconds. */
    abstract float leaving(Motion m);

    void free() {}

    /**
     * The plan's numbers that follow from its others.
     *
     * @param tall what stands on the plinth and has to be in the picture, each as a box: x, y, z of its lower corner
     *             and of its upper
     */
    final void planned(final float x0, final float z0, final float x1, final float z1, final float[]... tall) {
        bounds = new float[24 * (tall.length + 1)];
        System.arraycopy(Camera.box(x0 - 0.05f, 0f, z0 - 0.05f, x1 + 0.05f, BASE, z1 + 0.05f), 0, bounds, 0, 24);
        for (int i = 0; i < tall.length; i++) {
            final float[] b = tall[i];
            System.arraycopy(Camera.box(b[0], b[1], b[2], b[3], b[4], b[5]), 0, bounds, 24 * (i + 1), 24);
        }
        stage[0] = (x0 + x1) / 2f;
        stage[1] = (z0 + z1) / 2f;
        stage[2] = (x1 - x0) / 2f;
        stage[3] = (z1 - z0) / 2f;
        target[0] = stage[0];
        target[1] = 1.7f;
        target[2] = 0f;
    }

    /**
     * Where a thing is that has come {@code along}: on the first belt while that is negative, on the second from 0.
     * {@code out} takes x, y and the slope under it in degrees.
     */
    final void place(final float along, final float[] out) {
        if (along >= 0f) {
            way.at(along, out);
            return;
        }
        out[0] = Math.max(lumpX0, lumpX1 + along);
        out[1] = beltY;
        out[2] = 0f;
    }

    static void set(final float[] point, final float x, final float y, final float z) {
        point[0] = x;
        point[1] = y;
        point[2] = z;
    }

    /** One lamp of the three. */
    static void lamp(final SceneGl.Light light, final int i, final float x, final float y, final float z, final float reach,
                     final float r, final float g, final float b) {
        light.lamp[i * 4] = x;
        light.lamp[i * 4 + 1] = y;
        light.lamp[i * 4 + 2] = z;
        light.lamp[i * 4 + 3] = reach;
        light.lampColour[i * 3] = r;
        light.lampColour[i * 3 + 1] = g;
        light.lampColour[i * 3 + 2] = b;
    }

    /** Dust in the light over the belts, and what flies when the press strikes. */
    static void air(final Motion m, final Frame frame, final float x0, final float x1, final float dim) {
        for (final Motion.Spark s : m.sparks) {
            final float t = s.life / s.span;
            if (s.smoke) frame.halo(s.x, s.y, s.z, s.size * (1f + 1.6f * t), s.colour, (1f - t) * 0.10f);
            else frame.halo(s.x, s.y, s.z, s.size * (1f - 0.5f * t) * 2.2f, s.colour, 0.9f * (1f - t * t));
        }
        for (int i = 0; i < 46; i++) {
            final float a = Atlas.hash(i, 1, 77), b = Atlas.hash(i, 2, 77), c = Atlas.hash(i, 3, 77), d = Atlas.hash(i, 4, 77);
            final float x = x0 + (x1 - x0) * a + (float) Math.sin(m.time * (0.11f + 0.1f * b) + i) * 0.5f;
            final float y = 0.6f + 4.2f * ((b + m.time * (0.012f + 0.02f * c)) % 1f);
            final float z = -1.6f + 3.4f * c + (float) Math.cos(m.time * (0.13f + 0.08f * d) + i * 2.1f) * 0.4f;
            final float twinkle = 0.5f + 0.5f * (float) Math.sin(m.time * (0.7f + d) + i * 1.7f);
            frame.halo(x, y, z, 0.035f + 0.03f * d, 0xFFFFF1D6, (0.10f + 0.25f * twinkle) * dim);
        }
    }
}
