package dev.fallingcloud.slate.menu.client.loading.scene;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads block models as the game has them (JSON: boxes with a texture on each face, turned about an axis, a parent
 * whose boxes and textures they take over) and their textures out of a mod's assets, and makes of them what the
 * scene draws: faces of four corners, their textures on the scene's {@link Sheet}. The game's own model baker does
 * not exist yet where the scene first runs, so this is the part of it the scene needs, written again.
 */
final class Kit {

    /** One face of a model: four corners counter-clockwise seen from outside, in blocks, and where on its texture they are. */
    static final class Quad {
        final float[] p = new float[12];
        final float[] n = new float[3];
        /** 0 to 16 across the texture, whatever its size in pixels. */
        final float[] uv = new float[8];
        String texture;
        Sheet.Region region;
    }

    static final class Model {
        final List<Quad> quads = new ArrayList<>();
    }

    /**
     * Gives a face another texture, or another part of it, than its model names: the belt that runs, the wall that
     * joins the wall beside it.
     */
    @FunctionalInterface
    interface Skin {
        /**
         * @param uv the face's coordinates, 0 to 16, to be changed in place
         * @return the texture to take them from, or null for the face's own
         */
        Sheet.Region skin(Quad quad, float[] uv);
    }

    /** What a {@link Skin} gives back for a face that is not to be drawn. */
    static final Sheet.Region SKIP = new Sheet.Region(0, 0, 0, 0, false);

    private static final String[] FACES = {"down", "up", "north", "south", "west", "east"};
    /** The corners of each face as the game orders them: for each corner, whether it takes the greater x, y and z. */
    private static final int[][][] CORNERS = {
        {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
        {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
        {{1, 1, 0}, {1, 0, 0}, {0, 0, 0}, {0, 1, 0}},
        {{0, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 1, 1}},
        {{0, 1, 0}, {0, 0, 0}, {0, 0, 1}, {0, 1, 1}},
        {{1, 1, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 0}},
    };

    private final Assets assets;
    private final Sheet sheet;
    private final Map<String, Model> models = new HashMap<>();
    private final Map<String, Sheet.Region> textures = new HashMap<>();
    private final Map<String, int[]> pictures = new HashMap<>();
    private final float[] scratch = new float[8];
    /** Textures that were named and not there, and had a plain colour put in their place. */
    int stoodIn;

    Kit(final Assets assets, final Sheet sheet) {
        this.assets = assets;
        this.sheet = sheet;
    }

    // ------------------------------------------------------------------ textures

    /** A texture by the name models call it ({@code create:block/belt}); a plain colour if there is no such file. */
    Sheet.Region texture(final String id) {
        final Sheet.Region known = textures.get(id);
        if (known != null) return known;
        final String[] name = split(id);
        int[] argb = null;
        int w = 16, h = 16;
        final Picture picture = Picture.read(assets.read(name[0], "textures/" + name[1] + ".png"));
        if (picture != null) {
            argb = picture.argb();
            w = picture.w();
            h = picture.h();
            // A texture that moves is its frames one under the other: the first one.
            if (h > w && h % w == 0 && assets.read(name[0], "textures/" + name[1] + ".png.mcmeta") != null) {
                h = w;
                argb = java.util.Arrays.copyOf(argb, w * h);
            }
        }
        if (argb == null) {
            stoodIn++;
            w = h = 16;
            argb = standIn(name[1]);
        }
        final Sheet.Region region = sheet.add(argb, w, h);
        if (region == null) throw new IllegalStateException("no room on the sheet for " + id);
        textures.put(id, region);
        pictures.put(id, argb);
        return region;
    }

    /**
     * A copy of a texture with a window cut into it: nothing where the window is, so what is behind shows. The
     * window is given in the texture's own pixels.
     */
    Sheet.Region windowed(final String id, final int x0, final int y0, final int x1, final int y1) {
        final String key = id + " window " + x0 + " " + y0 + " " + x1 + " " + y1;
        final Sheet.Region known = textures.get(key);
        if (known != null) return known;
        final Sheet.Region whole = texture(id);
        final int[] px = pictures.get(id).clone();
        for (int y = Math.max(0, y0); y < Math.min(whole.h(), y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(whole.w(), x1); x++) px[y * whole.w() + x] = 0;
        }
        final Sheet.Region region = sheet.add(px, whole.w(), whole.h());
        if (region == null) throw new IllegalStateException("no room on the sheet for " + key);
        textures.put(key, region);
        return region;
    }

    /** The colour of a texture taken all in all: the mean of what is not clear in it. */
    int tone(final String id) {
        texture(id);
        long r = 0, g = 0, b = 0, n = 0;
        for (final int c : pictures.get(id)) {
            if ((c >>> 24) < 128) continue;
            r += (c >>> 16) & 0xFF;
            g += (c >>> 8) & 0xFF;
            b += c & 0xFF;
            n++;
        }
        if (n == 0) return 0xFFB0B0B0;
        return 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
    }

    /** Whether the file of this texture is there. */
    boolean has(final String id) {
        final String[] name = split(id);
        return assets.read(name[0], "textures/" + name[1] + ".png") != null;
    }

    /** A plain texture in a colour its name suggests, with a little grain: for a file that is not there. */
    private static int[] standIn(final String path) {
        final int base = path.contains("dark_oak") ? 0xFF3C2A16 : path.contains("spruce") ? 0xFF6B4F2F
            : path.contains("log") || path.contains("planks") ? 0xFF8A6A42 : 0xFF808080;
        final int[] px = new int[256];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) px[y * 16 + x] = Atlas.shade(base, 0.9f + Atlas.hash(x / 2, y, 41) * 0.2f);
        }
        return px;
    }

    private static String[] split(final String id) {
        final int colon = id.indexOf(':');
        return colon < 0 ? new String[] {"minecraft", id} : new String[] {id.substring(0, colon), id.substring(colon + 1)};
    }

    // ------------------------------------------------------------------ models

    /** A model by its name ({@code create:block/belt/start}); throws when it is not there or cannot be read. */
    Model model(final String id) {
        final Model known = models.get(id);
        if (known != null) return known;
        final Model model = new Model();
        // The model and its parents, the model first: the first that has boxes gives them, the textures add up.
        final Map<String, String> names = new LinkedHashMap<>();
        List<Object> elements = null;
        String at = id;
        for (int depth = 0; at != null && depth < 8; depth++) {
            final String[] name = split(at);
            final byte[] file = assets.read(name[0], "models/" + name[1] + ".json");
            if (file == null) {
                if (depth == 0) throw new IllegalStateException("no model " + id);
                break;
            }
            final Map<String, Object> json = Json.object(Json.parse(new String(file, StandardCharsets.UTF_8)));
            for (final Map.Entry<String, Object> e : Json.object(json.get("textures")).entrySet()) {
                if (e.getValue() instanceof String s) names.putIfAbsent(e.getKey(), s);
            }
            if (elements == null && json.get("elements") instanceof List) elements = Json.list(json.get("elements"));
            at = Json.string(json.get("parent"), null);
        }
        if (elements != null) {
            for (final Object element : elements) box(model, Json.object(element), names);
        }
        models.put(id, model);
        return model;
    }

    private void box(final Model model, final Map<String, Object> element, final Map<String, String> names) {
        final float[] from = three(element.get("from"), 0f), to = three(element.get("to"), 16f);
        final Map<String, Object> rotation = Json.object(element.get("rotation"));
        final float angle = Json.number(rotation.get("angle"), 0f);
        final String axis = Json.string(rotation.get("axis"), "y");
        final float[] origin = three(rotation.get("origin"), 8f);
        final boolean rescale = Boolean.TRUE.equals(rotation.get("rescale"));
        final float[] turn = Mat4.identity();
        if (angle != 0f) Mat4.rotate(turn, angle, axis.equals("x") ? 1 : 0, axis.equals("y") ? 1 : 0, axis.equals("z") ? 1 : 0);
        final float stretch = rescale && angle != 0f ? (float) (1.0 / Math.cos(Math.toRadians(Math.abs(angle)))) : 1f;
        final float[] out = new float[4];

        final Map<String, Object> faces = Json.object(element.get("faces"));
        for (int f = 0; f < FACES.length; f++) {
            if (!(faces.get(FACES[f]) instanceof Map)) continue;
            final Map<String, Object> face = Json.object(faces.get(FACES[f]));
            final String texture = resolve(Json.string(face.get("texture"), ""), names);
            if (texture == null) continue;
            final Quad q = new Quad();
            q.texture = texture;
            q.region = texture(texture);
            for (int c = 0; c < 4; c++) {
                final int[] corner = CORNERS[f][c];
                float x = corner[0] == 1 ? to[0] : from[0], y = corner[1] == 1 ? to[1] : from[1], z = corner[2] == 1 ? to[2] : from[2];
                if (angle != 0f) {
                    Mat4.transform(turn, x - origin[0], y - origin[1], z - origin[2], out);
                    x = out[0] * (axis.equals("x") ? 1f : stretch) + origin[0];
                    y = out[1] * (axis.equals("y") ? 1f : stretch) + origin[1];
                    z = out[2] * (axis.equals("z") ? 1f : stretch) + origin[2];
                }
                q.p[c * 3] = x / 16f;
                q.p[c * 3 + 1] = y / 16f;
                q.p[c * 3 + 2] = z / 16f;
            }
            // Where the face looks, from its corners: a box given back to front has its faces looking inward, and a
            // box without thickness has faces without size, which are left out.
            final float e1x = q.p[3] - q.p[0], e1y = q.p[4] - q.p[1], e1z = q.p[5] - q.p[2];
            final float e2x = q.p[9] - q.p[0], e2y = q.p[10] - q.p[1], e2z = q.p[11] - q.p[2];
            final float nx = e1y * e2z - e1z * e2y, ny = e1z * e2x - e1x * e2z, nz = e1x * e2y - e1y * e2x;
            final float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (length < 1e-7f) continue;
            q.n[0] = nx / length;
            q.n[1] = ny / length;
            q.n[2] = nz / length;

            final float[] uv = face.get("uv") instanceof List ? four(face.get("uv")) : ownUv(f, from, to);
            final int quarter = Math.round(Json.number(face.get("rotation"), 0f) / 90f) & 3;
            for (int c = 0; c < 4; c++) {
                final int j = (c + quarter) & 3;
                q.uv[c * 2] = j == 0 || j == 1 ? uv[0] : uv[2];
                q.uv[c * 2 + 1] = j == 0 || j == 3 ? uv[1] : uv[3];
            }
            model.quads.add(q);
        }
    }

    /** What a face shows of its texture when its model does not say: what its place in the block covers. */
    private static float[] ownUv(final int face, final float[] from, final float[] to) {
        return switch (face) {
            case 0 -> new float[] {from[0], 16f - to[2], to[0], 16f - from[2]};
            case 1 -> new float[] {from[0], from[2], to[0], to[2]};
            case 2 -> new float[] {16f - to[0], 16f - to[1], 16f - from[0], 16f - from[1]};
            case 3 -> new float[] {from[0], 16f - to[1], to[0], 16f - from[1]};
            case 4 -> new float[] {from[2], 16f - to[1], to[2], 16f - from[1]};
            default -> new float[] {16f - to[2], 16f - to[1], 16f - from[2], 16f - from[1]};
        };
    }

    /** A texture as a face names it: a name of the model's ({@code #side}), which may stand for another. */
    private static String resolve(final String name, final Map<String, String> names) {
        String at = name;
        for (int i = 0; i < 8 && at != null && at.startsWith("#"); i++) at = names.get(at.substring(1));
        return at == null || at.isEmpty() || at.startsWith("#") ? null : at;
    }

    private static float[] three(final Object value, final float fallback) {
        final List<Object> list = Json.list(value);
        final float[] v = {fallback, fallback, fallback};
        for (int i = 0; i < 3 && i < list.size(); i++) v[i] = Json.number(list.get(i), fallback);
        return v;
    }

    private static float[] four(final Object value) {
        final List<Object> list = Json.list(value);
        final float[] v = {0, 0, 16, 16};
        for (int i = 0; i < 4 && i < list.size(); i++) v[i] = Json.number(list.get(i), v[i]);
        return v;
    }

    /**
     * A thing as the game makes one of a flat picture: the picture front and back, a texel thick, its rim closed texel
     * by texel. It stands in a block like a picture on a wall: x and y from 0 to 1, z round the middle.
     */
    Model sprite(final String id) {
        final String key = "sprite " + id;
        final Model known = models.get(key);
        if (known != null) return known;
        final Sheet.Region region = texture(id);
        final int[] px = pictures.get(id);
        final int w = region.w(), h = region.h();
        final Model model = new Model();
        final float z0 = 7.5f / 16f, z1 = 8.5f / 16f;
        flat(model, id, region, 0, 0, z1, 1, 1, z1, 0, 0, 1, 0, 0, 16, 16);
        flat(model, id, region, 1, 0, z0, 0, 1, z0, 0, 0, -1, 16, 0, 0, 16);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (clear(px, w, h, x, y)) continue;
                final float x0 = x / (float) w, x1 = (x + 1) / (float) w, y1 = 1f - y / (float) h, y0 = 1f - (y + 1) / (float) h;
                final float u = (x + 0.5f) * 16f / w, v = (y + 0.5f) * 16f / h;
                if (clear(px, w, h, x - 1, y)) side(model, id, region, x0, y0, z0, x0, y1, z1, -1, 0, 0, u, v);
                if (clear(px, w, h, x + 1, y)) side(model, id, region, x1, y0, z1, x1, y1, z0, 1, 0, 0, u, v);
                if (clear(px, w, h, x, y - 1)) side(model, id, region, x0, y1, z1, x1, y1, z0, 0, 1, 0, u, v);
                if (clear(px, w, h, x, y + 1)) side(model, id, region, x0, y0, z0, x1, y0, z1, 0, -1, 0, u, v);
            }
        }
        models.put(key, model);
        return model;
    }

    private static boolean clear(final int[] px, final int w, final int h, final int x, final int y) {
        return x < 0 || y < 0 || x >= w || y >= h || (px[y * w + x] >>> 24) < 26;
    }

    /** The picture itself: from its lower corner (x0, y0) to its upper (x1, y1), facing along z. */
    private static void flat(final Model model, final String id, final Sheet.Region region, final float x0, final float y0, final float z,
                             final float x1, final float y1, final float zz, final float nx, final float ny, final float nz,
                             final float u0, final float v0, final float u1, final float v1) {
        final Quad q = new Quad();
        q.texture = id;
        q.region = region;
        set(q, 0, x0, y1, z, u0, v0);
        set(q, 1, x0, y0, z, u0, v1);
        set(q, 2, x1, y0, z, u1, v1);
        set(q, 3, x1, y1, z, u1, v0);
        q.n[0] = nx;
        q.n[1] = ny;
        q.n[2] = nz;
        model.quads.add(q);
    }

    /** One texel of the rim: a strip from front to back, all of it in that texel's colour. */
    private static void side(final Model model, final String id, final Sheet.Region region, final float ax, final float ay, final float az,
                             final float bx, final float by, final float bz, final float nx, final float ny, final float nz,
                             final float u, final float v) {
        final Quad q = new Quad();
        q.texture = id;
        q.region = region;
        if (nx != 0) {
            // A strip upright: from (ax, ay, az) up to by and across to bz.
            set(q, 0, ax, by, az, u, v);
            set(q, 1, ax, ay, az, u, v);
            set(q, 2, ax, ay, bz, u, v);
            set(q, 3, ax, by, bz, u, v);
        } else {
            // A strip lying: from (ax, ay, az) along to bx and across to bz.
            set(q, 0, ax, ay, bz, u, v);
            set(q, 1, ax, ay, az, u, v);
            set(q, 2, bx, ay, az, u, v);
            set(q, 3, bx, ay, bz, u, v);
        }
        q.n[0] = nx;
        q.n[1] = ny;
        q.n[2] = nz;
        // Whichever way round the corners came: they have to go counter-clockwise seen from where the face looks.
        final float e1x = q.p[3] - q.p[0], e1y = q.p[4] - q.p[1], e1z = q.p[5] - q.p[2];
        final float e2x = q.p[9] - q.p[0], e2y = q.p[10] - q.p[1], e2z = q.p[11] - q.p[2];
        final float cx = e1y * e2z - e1z * e2y, cy = e1z * e2x - e1x * e2z, cz = e1x * e2y - e1y * e2x;
        if (cx * nx + cy * ny + cz * nz < 0) {
            for (int i = 0; i < 3; i++) {
                final float t = q.p[3 + i];
                q.p[3 + i] = q.p[9 + i];
                q.p[9 + i] = t;
            }
        }
        model.quads.add(q);
    }

    private static void set(final Quad q, final int corner, final float x, final float y, final float z, final float u, final float v) {
        q.p[corner * 3] = x;
        q.p[corner * 3 + 1] = y;
        q.p[corner * 3 + 2] = z;
        q.uv[corner * 2] = u;
        q.uv[corner * 2 + 1] = v;
    }

    // ------------------------------------------------------------------ drawing

    /**
     * A model into the frame, moved by the matrix on top of {@code solid}'s stack, in the material set on the meshes.
     * Faces whose texture lets light through go to {@code sheer} (moved by the same matrix), all others to
     * {@code solid}.
     */
    void emit(final Mesh solid, final Mesh sheer, final Model model, final Skin skin) {
        final float[] uv = scratch;
        for (final Quad q : model.quads) {
            System.arraycopy(q.uv, 0, uv, 0, 8);
            Sheet.Region region = skin == null ? null : skin.skin(q, uv);
            if (region == SKIP) continue;
            if (region == null) region = q.region;
            // Pulled a hair towards the middle of the face, off the rim of the texture.
            float mu = 0, mv = 0;
            for (int c = 0; c < 4; c++) {
                mu += uv[c * 2] / 4f;
                mv += uv[c * 2 + 1] / 4f;
            }
            final Mesh into = region.sheer() && sheer != null ? sheer : solid;
            if (into != solid) into.copyMatrix(solid);
            final float[] p = q.p;
            into.rawQuad(p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7], p[8], p[9], p[10], p[11], q.n[0], q.n[1], q.n[2],
                region.u(near(uv[0], mu)), region.v(near(uv[1], mv)), region.u(near(uv[2], mu)), region.v(near(uv[3], mv)),
                region.u(near(uv[4], mu)), region.v(near(uv[5], mv)), region.u(near(uv[6], mu)), region.v(near(uv[7], mv)));
        }
    }

    private static float near(final float value, final float middle) {
        return value + Math.signum(middle - value) * 0.004f;
    }

    /**
     * Which of the sixteen pictures of a wall that joins its neighbours a block takes: a sheet of four by four, its
     * column by what is left and right of the block, its row by what is over and under it.
     */
    static void joined(final float[] uv, final boolean left, final boolean right, final boolean up, final boolean down) {
        final int column = left && right ? 2 : left ? 3 : right ? 1 : 0;
        final int row = up && down ? 1 : up ? 2 : down ? 0 : 3;
        for (int c = 0; c < 4; c++) {
            uv[c * 2] = (column * 16f + uv[c * 2]) / 4f;
            uv[c * 2 + 1] = (row * 16f + uv[c * 2 + 1]) / 4f;
        }
    }

    /**
     * For a face of a block that is one of many making a bigger thing ({@code size} blocks, this one at {@code at}):
     * takes the picture that joins it to its neighbours, from the way the face's own texture lies on it.
     */
    static void joined(final Quad q, final float[] uv, final int[] at, final int[] size) {
        // Along which edge of the face u grows, and along which v.
        final float[] p = q.p;
        final float e1x = p[3] - p[0], e1y = p[4] - p[1], e1z = p[5] - p[2];
        final float e2x = p[9] - p[0], e2y = p[10] - p[1], e2z = p[11] - p[2];
        final float du1 = uv[2] - uv[0], dv1 = uv[3] - uv[1], du2 = uv[6] - uv[0], dv2 = uv[7] - uv[1];
        final int[] right, down;
        if (Math.abs(du1) > Math.abs(dv1)) {
            right = way(e1x, e1y, e1z, du1);
            down = way(e2x, e2y, e2z, dv2);
        } else {
            right = way(e2x, e2y, e2z, du2);
            down = way(e1x, e1y, e1z, dv1);
        }
        joined(uv, in(at, size, right, -1), in(at, size, right, 1), in(at, size, down, -1), in(at, size, down, 1));
    }

    /** The direction of an edge as a step of one block along an axis, turned round when what grows along it shrinks. */
    private static int[] way(final float x, final float y, final float z, final float grows) {
        final float ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        final int s = grows < 0 ? -1 : 1;
        if (ax >= ay && ax >= az) return new int[] {x < 0 ? -s : s, 0, 0};
        if (ay >= az) return new int[] {0, y < 0 ? -s : s, 0};
        return new int[] {0, 0, z < 0 ? -s : s};
    }

    private static boolean in(final int[] at, final int[] size, final int[] way, final int step) {
        for (int i = 0; i < 3; i++) {
            final int v = at[i] + way[i] * step;
            if (v < 0 || v >= size[i]) return false;
        }
        return true;
    }
}
