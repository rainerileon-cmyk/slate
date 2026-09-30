import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Paints the textures of Slate Profile's sample cosmetics: one thing at least for every slot, to test with. Two
 * kinds. A <em>layer</em> is a 64×64 picture in the layout of a player skin, empty but for what the thing covers; it
 * is laid over the skin. A <em>model</em> texture is the unwrapped boxes of a thing that is built in 3D (a hat, a
 * pack, a pet), in the layout Minecraft's own models use.
 *
 * <p>Run from the repository root: {@code java tools/overhaul/ProfileTextures.java [--preview DIR]}. Writes to
 * {@code common/profile/src/main/resources/assets/slate_profile/textures/cosmetic/}. The boxes painted here are
 * the boxes {@code profile.client.Cosmetics} builds: same offsets, same sizes.</p>
 */
public final class ProfileTextures {

    /** The six sides of a box, in the order its unwrapping lists them. */
    enum Face { TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK }

    @FunctionalInterface
    interface Paint {
        /** The colour of pixel {@code (x, y)} of a side {@code w × h} pixels large; 0 leaves it empty. */
        int at(Face face, int x, int y, int w, int h);
    }

    /**
     * In a layer: "take away what the skin has here". A limb that is replaced must not keep the sleeve the skin wore
     * over it. Nearly empty (alpha 1), which no painted pixel ever is.
     */
    static final int ERASE = 0x01000000;

    private static BufferedImage img;
    private static File out, preview;

    public static void main(final String[] args) throws IOException {
        out = new File("common/profile/src/main/resources/assets/slate_profile/textures/cosmetic");
        if (!out.isDirectory() && !out.mkdirs()) throw new IOException("cannot create " + out);
        for (int i = 0; i + 1 < args.length; i++) if (args[i].equals("--preview")) preview = new File(args[i + 1]);

        // ---- layers
        layer("shirt_hoodie", () -> {
            final Paint cloth = shaded(0xFF4E6FA8, 0.16f);
            box(16, 32, 8, 12, 4, (f, x, y, w, h) -> {
                int c = cloth.at(f, x, y, w, h);
                if (f == Face.FRONT) {
                    if (y >= 7 && y <= 9 && x >= 1 && x <= 6) c = mul(c, y == 7 ? 0.8f : 0.9f);      // the pocket
                    if ((x == 3 || x == 4) && y <= 4) c = y % 2 == 0 ? 0xFFE8E4DA : mul(c, 0.85f);  // the cords
                }
                if (y == h - 1 && f != Face.TOP && f != Face.BOTTOM) c = mul(c, 0.78f);              // the hem
                return f == Face.BOTTOM ? 0 : c;
            });
            final Paint sleeve = (f, x, y, w, h) -> f == Face.BOTTOM ? 0 : y >= h - 2 && f != Face.TOP ? mul(cloth.at(f, x, y, w, h), 0.78f) : cloth.at(f, x, y, w, h);
            box(40, 32, 4, 12, 4, sleeve);
            box(48, 48, 4, 12, 4, sleeve);
            // The hood, lying on the shoulders: a band round the neck on the head's outer layer.
            box(32, 0, 8, 8, 8, (f, x, y, w, h) -> f != Face.TOP && f != Face.BOTTOM && y >= 6 && (f != Face.FRONT || x < 2 || x > 5) ? mul(0xFF4E6FA8, y == 6 ? 1.1f : 0.9f) : 0);
        });
        layer("shirt_striped", () -> {
            final Paint stripes = (f, x, y, w, h) -> f == Face.BOTTOM ? 0 : mul(y % 4 < 2 ? 0xFFEDE6D6 : 0xFFC9483F, faceLight(f) * (1f - 0.1f * y / h));
            box(16, 32, 8, 12, 4, stripes);
            final Paint shortSleeve = (f, x, y, w, h) -> f == Face.BOTTOM || y > 4 ? 0 : stripes.at(f, x, y, w, h);
            box(40, 32, 4, 12, 4, shortSleeve);
            box(48, 48, 4, 12, 4, shortSleeve);
        });
        layer("pants_jeans", () -> {
            final Paint denim = (f, x, y, w, h) -> {
                if (f == Face.TOP) return 0;
                int c = mul(0xFF3C5A8C, faceLight(f) * (1f - 0.18f * y / h));
                if ((x + y * 3) % 5 == 0) c = mul(c, 1.08f);                                     // the weave
                if (f == Face.FRONT && x == w - 1) c = mul(c, 0.84f);                                // the seam
                if (y == h - 1) c = mul(c, 1.18f);                                                   // the turn-up
                if (y == 0) c = 0xFF6B4A2B;                                                          // the belt
                return c;
            };
            box(0, 32, 4, 12, 4, denim);
            box(0, 48, 4, 12, 4, denim);
            // The belt goes round the body too.
            box(16, 32, 8, 12, 4, (f, x, y, w, h) -> f != Face.TOP && f != Face.BOTTOM && y == h - 1 ? (f == Face.FRONT && (x == 3 || x == 4) ? 0xFFD9B44A : 0xFF6B4A2B) : 0);
        });
        layer("pants_shorts", () -> {
            final Paint shorts = (f, x, y, w, h) -> f == Face.TOP || y > 5 ? 0 : mul(y == 5 ? 0xFFB9A273 : 0xFFD2BC8A, faceLight(f));
            box(0, 32, 4, 12, 4, shorts);
            box(0, 48, 4, 12, 4, shorts);
        });
        for (final boolean left : new boolean[] {false, true}) {
            final String side = left ? "left" : "right";
            final int armU = left ? 32 : 40, armV = left ? 48 : 16, sleeveU = left ? 48 : 40, sleeveV = left ? 48 : 32;
            final int legU = left ? 16 : 0, legV = left ? 48 : 16, pantsU = 0, pantsV = left ? 48 : 32;
            layer("arm_iron_" + side, () -> {
                // An arm of iron plates: the limb itself is replaced, rivets and a joint at the elbow.
                box(armU, armV, 4, 12, 4, (f, x, y, w, h) -> {
                    int c = mul(0xFFB8BEC6, faceLight(f) * (1f - 0.12f * y / h));
                    if (f != Face.TOP && f != Face.BOTTOM) {
                        if (y == 5 || y == 6) c = mul(0xFF5C636B, faceLight(f));
                        if ((y == 1 || y == 9) && (x == 0 || x == w - 1)) c = 0xFFE6EAEE;
                        if (y == h - 1) c = mul(c, 0.7f);
                    }
                    return c;
                });
                box(sleeveU, sleeveV, 4, 12, 4, (f, x, y, w, h) -> f != Face.TOP && f != Face.BOTTOM && y <= 1 ? mul(0xFF8A929B, faceLight(f)) : ERASE);
            });
            layer("arm_bandage_" + side, () ->
                box(sleeveU, sleeveV, 4, 12, 4, (f, x, y, w, h) -> {
                    if (f == Face.TOP || f == Face.BOTTOM || y < 3 || y > 9) return 0;
                    final boolean gap = (y + (f.ordinal() % 2)) % 3 == 0 && x % 3 == 1;
                    return gap ? 0 : mul((x + y) % 2 == 0 ? 0xFFF0EADB : 0xFFE0D8C4, faceLight(f));
                }));
            layer("leg_wood_" + side, () -> {
                // A leg of turned wood under the knee.
                box(legU, legV, 4, 12, 4, (f, x, y, w, h) -> {
                    if (f == Face.TOP || y < 5) return 0;
                    if (f == Face.BOTTOM) return 0xFF6B4A2B;
                    int c = mul(y % 3 == 0 ? 0xFF8A5F35 : 0xFFA5743F, faceLight(f));
                    if (y == 5) c = mul(0xFF8C9299, faceLight(f));                                   // the iron band
                    if (y >= h - 2) c = mul(c, 0.78f);
                    return c;
                });
                box(pantsU, pantsV, 4, 12, 4, (f, x, y, w, h) -> f == Face.TOP || y < 5 ? 0 : ERASE);
            });
            layer("leg_stocking_" + side, () ->
                box(pantsU, pantsV, 4, 12, 4, (f, x, y, w, h) -> f == Face.TOP || y < 4 ? 0 : f == Face.BOTTOM ? 0xFF2E2E36
                    : mul(y % 2 == 0 ? 0xFFE7E2D6 : 0xFF3E8F6B, faceLight(f))));
        }
        layer("face_bandana", () ->
            box(32, 0, 8, 8, 8, (f, x, y, w, h) -> {
                if (f == Face.TOP || f == Face.BOTTOM || y < 5) return 0;
                int c = mul(0xFFB5392F, faceLight(f) * (y == 5 ? 1.12f : 1f));
                if ((x + y) % 4 == 0) c = 0xFFF2E9D8;                                                // the pattern
                return c;
            }));

        // ---- models
        model("hat_top", 64, 32, () -> {
            box(0, 0, 10, 1, 10, shaded(0xFF2B2B33, 0.1f));                                          // the brim
            box(0, 11, 6, 7, 6, (f, x, y, w, h) -> {                                                 // the crown, with its band
                final int c = mul(0xFF2B2B33, faceLight(f) * (1f - 0.1f * y / Math.max(1, h)));
                return f != Face.TOP && f != Face.BOTTOM && y >= h - 2 ? mul(0xFFB5392F, faceLight(f)) : c;
            });
        });
        model("hat_crown", 64, 32, () -> {
            final Paint gold = (f, x, y, w, h) -> mul((x + y) % 3 == 0 ? 0xFFFFE27A : 0xFFE8B93A, faceLight(f));
            box(0, 0, 9, 2, 9, (f, x, y, w, h) -> {                                                  // the band, a gem in front
                if (f == Face.TOP || f == Face.BOTTOM) return x > 0 && x < w - 1 && y > 0 && y < h - 1 ? 0 : gold.at(f, x, y, w, h);
                return f == Face.FRONT && y == 0 && x == 4 ? 0xFFD8434A : gold.at(f, x, y, w, h);
            });
            box(0, 11, 1, 2, 1, gold);                                                               // a point
            box(8, 11, 1, 3, 1, (f, x, y, w, h) -> f != Face.BOTTOM && y == 0 ? 0xFF5FD3E8 : gold.at(f, x, y, w, h));   // the tall point, tipped
        });
        model("hat_straw", 64, 32, () -> {
            final Paint straw = (f, x, y, w, h) -> mul((x * 3 + y) % 4 == 0 ? 0xFFC9A654 : 0xFFE3C474, faceLight(f));
            box(0, 0, 14, 1, 14, straw);
            box(0, 15, 8, 3, 8, (f, x, y, w, h) -> f != Face.TOP && f != Face.BOTTOM && y == h - 1 ? mul(0xFF6F8F4A, faceLight(f)) : straw.at(f, x, y, w, h));
        });
        model("face_glasses", 32, 16, () -> {
            box(0, 0, 3, 3, 1, (f, x, y, w, h) -> f == Face.FRONT && x == 1 && y == 1 ? 0xC09ED8F0 : 0xFF23232A);   // a lens in its frame
            box(8, 0, 2, 1, 1, shaded(0xFF23232A, 0f));                                              // the bridge
            box(0, 4, 1, 1, 5, shaded(0xFF23232A, 0f));                                              // a temple
        });
        model("back_pack", 64, 32, () -> {
            box(0, 0, 6, 8, 3, (f, x, y, w, h) -> {
                int c = mul(0xFF7A5230, faceLight(f) * (1f - 0.12f * y / Math.max(1, h)));
                if (f == Face.BACK && y >= 4 && y <= 6 && x >= 1 && x <= 4) c = mul(0xFF93663C, y == 4 ? 1.1f : 1f);   // the pocket
                if (f == Face.BACK && y == 5 && (x == 2 || x == 3)) c = 0xFFD9B44A;                   // its buckle
                return c;
            });
            box(18, 0, 6, 3, 1, shaded(0xFF5E3D22, 0.1f));                                           // the flap
            box(0, 11, 4, 2, 4, (f, x, y, w, h) -> mul(y % 2 == 0 ? 0xFFC9483F : 0xFFA93A33, faceLight(f)));   // the bedroll on top
        });
        model("back_wings", 64, 32, () ->
            box(0, 0, 9, 11, 1, (f, x, y, w, h) -> {
                if (f != Face.FRONT && f != Face.BACK) return 0xFFE9EDF2;
                // A wing: feathers in rows, longer towards the tip, nothing where the wing has ended.
                final int reach = Math.min(w, 3 + y * 2 / 3 + (y > 7 ? 7 - y : 0) * 2 + 4);
                if (x >= reach || (y == h - 1 && x % 2 == 0)) return 0;
                int c = y % 3 == 2 ? 0xFFC9D3DF : 0xFFF4F7FA;
                if (x == reach - 1) c = 0xFFB3C0CF;
                return c;
            }));
        model("pet_slime", 32, 32, () -> {
            box(0, 0, 5, 5, 5, (f, x, y, w, h) -> {                                                  // the skin: see-through
                final boolean rim = x == 0 || y == 0 || x == w - 1 || y == h - 1;
                return rim ? 0xB070C860 : 0x7088E070;
            });
            box(0, 10, 3, 3, 3, (f, x, y, w, h) -> {                                                 // the core, with a face
                if (f == Face.FRONT && y == 0 && (x == 0 || x == 2)) return 0xFF20301C;
                if (f == Face.FRONT && y == 2 && x == 1) return 0xFF20301C;
                return mul(0xFF5FAE4F, faceLight(f));
            });
        });
        model("pet_bee", 32, 32, () -> {
            box(0, 0, 3, 3, 4, (f, x, y, w, h) -> {
                if (f == Face.FRONT) return (y == 1 && (x == 0 || x == 2)) ? 0xFF1E1A14 : 0xFFF2C53D;
                if (f == Face.BACK) return 0xFF2A241B;
                final int along = f == Face.TOP || f == Face.BOTTOM ? y : x;
                return mul(along % 2 == 1 ? 0xFF2A241B : 0xFFF2C53D, faceLight(f));
            });
            box(0, 8, 3, 1, 2, (f, x, y, w, h) -> (x + y) % 2 == 0 ? 0xC0FFFFFF : 0x90DDEBF5);        // a wing
        });
    }

    // ------------------------------------------------------------------ painting

    /** How bright a side is drawn: the texture carries a little light of its own, so a thing reads even when lit flat. */
    static float faceLight(final Face f) {
        return switch (f) {
            case TOP -> 1.1f;
            case FRONT -> 1f;
            case BOTTOM -> 0.72f;
            case BACK -> 0.84f;
            default -> 0.9f;
        };
    }

    /** One colour, each side in its own light, darker by {@code fall} towards the foot of the upright sides. */
    static Paint shaded(final int argb, final float fall) {
        return (f, x, y, w, h) -> mul(argb, faceLight(f) * (f == Face.TOP || f == Face.BOTTOM ? 1f : 1f - fall * y / Math.max(1, h)));
    }

    static int mul(final int argb, final float by) {
        final int a = argb >>> 24;
        final int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * by));
        final int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * by));
        final int b = Math.min(255, Math.round((argb & 0xFF) * by));
        return a << 24 | r << 16 | g << 8 | b;
    }

    /**
     * Paints the six sides of a box {@code w × h × d} pixels unwrapped at {@code (u, v)}, the way Minecraft's models
     * (and a player skin) lay a box out: top and bottom in a first row, then right, front, left and back side by side.
     */
    static void box(final int u, final int v, final int w, final int h, final int d, final Paint paint) {
        side(Face.TOP, u + d, v, w, d, paint);
        side(Face.BOTTOM, u + d + w, v, w, d, paint);
        side(Face.RIGHT, u, v + d, d, h, paint);
        side(Face.FRONT, u + d, v + d, w, h, paint);
        side(Face.LEFT, u + d + w, v + d, d, h, paint);
        side(Face.BACK, u + d + w + d, v + d, w, h, paint);
    }

    private static void side(final Face face, final int x0, final int y0, final int w, final int h, final Paint paint) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                final int px = x0 + x, py = y0 + y;
                if (px < 0 || py < 0 || px >= img.getWidth() || py >= img.getHeight()) continue;
                img.setRGB(px, py, paint.at(face, x, y, w, h));
            }
        }
    }

    private static void layer(final String name, final Runnable painter) throws IOException {
        model(name, 64, 64, painter);
    }

    private static void model(final String name, final int w, final int h, final Runnable painter) throws IOException {
        img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        painter.run();
        ImageIO.write(img, "png", new File(out, name + ".png"));
        if (preview != null) {
            if (!preview.isDirectory() && !preview.mkdirs()) throw new IOException("cannot create " + preview);
            final int s = 8;
            final BufferedImage big = new BufferedImage(w * s, h * s, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h * s; y++) {
                for (int x = 0; x < w * s; x++) {
                    final int c = img.getRGB(x / s, y / s);
                    // Empty pixels show as a checker, so what is painted and what is not can be told apart.
                    big.setRGB(x, y, (c >>> 24) == 0 ? (((x / s + y / s) % 2 == 0) ? 0xFF2A2A2E : 0xFF333338) : c | 0xFF000000);
                }
            }
            ImageIO.write(big, "png", new File(preview, name + ".png"));
        }
    }
}
