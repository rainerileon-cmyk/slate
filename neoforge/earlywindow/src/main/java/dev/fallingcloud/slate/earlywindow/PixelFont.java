package dev.fallingcloud.slate.earlywindow;

import static org.lwjgl.opengl.GL32C.*;

import java.nio.ByteBuffer;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * One of Slate's pixel fonts baked by STB into an alpha atlas at one size. The game's fonts do not exist yet this
 * early, so all of the start-up window's text uses the heading font. Sizes are the em in GUI units, as the game's TTF
 * fonts ({@code size} in {@code font/heading_*.json}): Pixeloid Sans and Monocraft put 9 font pixels on the em, so at
 * size 9 one font pixel is one GUI pixel, and the atlas is baked at the canvas' scale so that stays one screen pixel
 * per GUI pixel times the scale. Text is placed by the top of its capitals, as the game's {@code drawString}.
 */
final class PixelFont {

    /** Latin-1, for mod names and log lines. */
    static final int LATIN_1 = 224;
    /** Printable ASCII, for the large wordmark. */
    static final int ASCII = 95;
    private static final int FIRST = 32;

    private final STBTTBakedChar.Buffer chars;
    private final int count, atlas, texture;
    /** Pixels per GUI unit, and the capital height in pixels. */
    private final float scale, capPx;

    private PixelFont(final STBTTBakedChar.Buffer chars, final int count, final int atlas, final int texture,
                      final float scale, final float capPx) {
        this.chars = chars;
        this.count = count;
        this.atlas = atlas;
        this.texture = texture;
        this.scale = scale;
        this.capPx = capPx;
    }

    /**
     * Bakes {@code count} characters from space of {@code ttf} with an em of {@code size} GUI units at {@code scale}
     * pixels per unit, in the smallest square atlas they fit; {@code crisp} samples it nearest-neighbour (for true pixel
     * fonts). Call with the GL context current (inside {@link Gfx#begin}, which restores the texture binding and unpack
     * alignment this changes).
     */
    static PixelFont bake(final ByteBuffer ttf, final float size, final float scale, final int count, final boolean crisp) {
        final float em = size * scale;
        final float pixelHeight, capPx;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final STBTTFontinfo info = STBTTFontinfo.malloc(stack);
            if (!STBTruetype.stbtt_InitFont(info, ttf)) throw new IllegalStateException("not a font");
            final var asc = stack.mallocInt(1);
            final var desc = stack.mallocInt(1);
            STBTruetype.stbtt_GetFontVMetrics(info, asc, desc, null);
            final float perUnit = STBTruetype.stbtt_ScaleForMappingEmToPixels(info, em);
            // STB's baking scales by ascent-to-descent; the height that gives this em.
            pixelHeight = (asc.get(0) - desc.get(0)) * perUnit;
            final var x0 = stack.mallocInt(1);
            final var y0 = stack.mallocInt(1);
            final var x1 = stack.mallocInt(1);
            final var y1 = stack.mallocInt(1);
            STBTruetype.stbtt_GetCodepointBox(info, 'H', x0, y0, x1, y1);
            capPx = Math.round(y1.get(0) * perUnit);
        }
        final STBTTBakedChar.Buffer chars = STBTTBakedChar.malloc(count);
        int side = 256;
        ByteBuffer bitmap = MemoryUtil.memAlloc(side * side);
        try {
            while (STBTruetype.stbtt_BakeFontBitmap(ttf, pixelHeight, bitmap, side, side, FIRST, chars) <= 0 && side < 4096) {
                MemoryUtil.memFree(bitmap);
                side *= 2;
                bitmap = MemoryUtil.memAlloc(side * side);
            }
            final int filter = crisp ? GL_NEAREST : GL_LINEAR;
            final int tex = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, tex);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, side, side, 0, GL_RED, GL_UNSIGNED_BYTE, bitmap);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            return new PixelFont(chars, count, side, tex, scale, capPx);
        } finally {
            MemoryUtil.memFree(bitmap);
        }
    }

    void delete() {
        glDeleteTextures(texture);
        chars.free();
    }

    /** Height of the capitals, GUI units. */
    float capHeight() {
        return capPx / scale;
    }

    float width(final String s) {
        float w = 0;
        for (int i = 0; i < s.length(); i++) {
            final int c = s.charAt(i) - FIRST;
            if (c >= 0 && c < count) w += chars.get(c).xadvance();
        }
        return w / scale;
    }

    /** {@code s} cut to {@code maxWidth} GUI units, with an ellipsis when cut. */
    String fit(final String s, final float maxWidth) {
        if (width(s) <= maxWidth) return s;
        final float dots = width("...");
        int end = s.length();
        while (end > 0 && width(s.substring(0, end)) + dots > maxWidth) end--;
        return s.substring(0, end).stripTrailing() + "...";
    }

    /** Draws {@code s} with the top of its capitals at {@code y} (GUI units). */
    void draw(final Gfx g, final String s, final float x, final float y, final int argb) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final STBTTAlignedQuad q = STBTTAlignedQuad.malloc(stack);
            final var px = stack.floats(Math.round(x * scale));
            final var py = stack.floats(Math.round(y * scale) + capPx);
            for (int i = 0; i < s.length(); i++) {
                final int c = s.charAt(i) - FIRST;
                if (c < 0 || c >= count) continue;
                STBTruetype.stbtt_GetBakedQuad(chars, atlas, atlas, c, px, py, q, true);
                g.glyph(texture, q.x0(), q.y0(), q.x1(), q.y1(), q.s0(), q.t0(), q.s1(), q.t1(), argb);
            }
        }
    }
}
