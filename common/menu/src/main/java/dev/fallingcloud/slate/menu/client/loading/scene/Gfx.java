package dev.fallingcloud.slate.menu.client.loading.scene;

import static org.lwjgl.opengl.GL32C.*;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

/**
 * A small 2D renderer in plain OpenGL 3.2, for pictures drawn where the game's own drawing is not there yet or not
 * usable: NeoForge's start-up window (FML's GL context, its render thread early on and the game's main thread once the
 * game has the window) and the loading overlay. Coloured and gradient quads, a vignette, alpha-atlas text and pictures,
 * batched per mode into one stream buffer. Positions are GUI units, scaled to canvas pixels here. Everything it
 * touches is put back in {@link #end} ({@link GlSave}).
 */
public final class Gfx {

    static final int SOLID = 0, TEXT = 1, VIGNETTE = 2, IMAGE = 3;

    // uFlip 1: y = 0 at the bottom of GL's target, as FML's own shader has it (FML flips its canvas when it shows it,
    // in its window blit and in the game's loading overlay both), which puts y = 0 at the top of the screen.
    // uFlip -1: a target shown as it is, y = 0 at its top.
    private static final String VERTEX = """
        #version 150
        in vec2 aPos;
        in vec2 aUv;
        in vec4 aColor;
        uniform vec2 uScreen;
        uniform float uFlip;
        out vec2 vUv;
        out vec4 vColor;
        void main() {
            vec2 p = aPos / uScreen * 2.0 - 1.0;
            gl_Position = vec4(p.x, p.y * uFlip, 0.0, 1.0);
            vUv = aUv;
            vColor = aColor;
        }
        """;
    private static final String FRAGMENT = """
        #version 150
        in vec2 vUv;
        in vec4 vColor;
        uniform sampler2D uTex;
        uniform int uMode;
        out vec4 fragColor;
        void main() {
            if (uMode == 1) {
                fragColor = vec4(vColor.rgb, vColor.a * texture(uTex, vUv).r);
            } else if (uMode == 2) {
                vec2 d = (vUv - 0.5) * 2.0;
                fragColor = vec4(vColor.rgb, vColor.a * clamp(dot(d, d) * 0.5, 0.0, 1.0));
            } else if (uMode == 3) {
                fragColor = texture(uTex, vUv) * vColor;
            } else {
                fragColor = vColor;
            }
        }
        """;
    /** x, y, u, v as floats, then RGBA bytes. */
    private static final int STRIDE = 4 * 4 + 4;
    private static final int CAPACITY = 6 * 2048;

    private int program, vao, vbo, uScreen, uMode, uTex, uFlip;
    private final ByteBuffer buffer = MemoryUtil.memAlloc(CAPACITY * STRIDE);
    private int vertices;
    private int mode = -1, texture;
    private float scale = 1f;
    /** Every colour's alpha is multiplied by this (the fade-in). */
    private float alpha = 1f;
    private final GlSave saved = new GlSave();
    /** Whether this frame saved the state itself and has to put it back. */
    private boolean own;

    private void init() {
        program = link(VERTEX, FRAGMENT, "aPos", "aUv", "aColor");
        uScreen = glGetUniformLocation(program, "uScreen");
        uMode = glGetUniformLocation(program, "uMode");
        uTex = glGetUniformLocation(program, "uTex");
        uFlip = glGetUniformLocation(program, "uFlip");
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) CAPACITY * STRIDE, GL_STREAM_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, STRIDE, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, STRIDE, 8);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 4, GL_UNSIGNED_BYTE, true, STRIDE, 16);
    }

    /** A program of the two sources, its attributes bound to 0, 1, ... in the order given. */
    static int link(final String vertex, final String fragment, final String... attributes) {
        final int vs = shader(GL_VERTEX_SHADER, vertex), fs = shader(GL_FRAGMENT_SHADER, fragment);
        final int p = glCreateProgram();
        glAttachShader(p, vs);
        glAttachShader(p, fs);
        for (int i = 0; i < attributes.length; i++) glBindAttribLocation(p, i, attributes[i]);
        glBindFragDataLocation(p, 0, "fragColor");
        glLinkProgram(p);
        glDeleteShader(vs);
        glDeleteShader(fs);
        if (glGetProgrami(p, GL_LINK_STATUS) == GL_FALSE) {
            final String log = glGetProgramInfoLog(p);
            glDeleteProgram(p);
            throw new IllegalStateException("link: " + log);
        }
        return p;
    }

    private static int shader(final int type, final String source) {
        final int s = glCreateShader(type);
        glShaderSource(s, source);
        glCompileShader(s);
        if (glGetShaderi(s, GL_COMPILE_STATUS) == GL_FALSE) {
            final String log = glGetShaderInfoLog(s);
            glDeleteShader(s);
            throw new IllegalStateException("compile: " + log);
        }
        return s;
    }

    /** Starts a frame on FML's canvas of {@code pixelW} x {@code pixelH} with {@code scale} pixels per GUI unit. */
    public void begin(final int pixelW, final int pixelH, final float scale) {
        begin(pixelW, pixelH, scale, true);
    }

    /**
     * Starts a frame on a {@code pixelW} x {@code pixelH} target with {@code scale} pixels per GUI unit.
     *
     * @param flipped whether the target is shown upside down (FML's canvas), so that y = 0 has to be drawn at GL's
     *                bottom to be seen at the top
     */
    public void begin(final int pixelW, final int pixelH, final float scale, final boolean flipped) {
        begin(pixelW, pixelH, scale, flipped, true);
    }

    /** As {@link #begin(int, int, float, boolean)}; with {@code save} off the caller keeps OpenGL's state itself. */
    void begin(final int pixelW, final int pixelH, final float scale, final boolean flipped, final boolean save) {
        own = save;
        if (save) saved.save();
        if (program == 0) init();
        this.scale = scale;
        glUseProgram(program);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glUniform2f(uScreen, pixelW, pixelH);
        glUniform1f(uFlip, flipped ? 1f : -1f);
        glUniform1i(uTex, 0);
        glActiveTexture(GL_TEXTURE0);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_STENCIL_TEST);
        glDepthMask(false);
        glColorMask(true, true, true, true);
        glEnable(GL_BLEND);
        glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        mode = -1;
        vertices = 0;
        alpha = 1f;
    }

    public void end() {
        flush();
        if (own) saved.restore();
    }

    /** Frees the GL objects; call with the context current. */
    public void delete() {
        if (program != 0) {
            glDeleteProgram(program);
            glDeleteVertexArrays(vao);
            glDeleteBuffers(vbo);
            program = 0;
        }
        MemoryUtil.memFree(buffer);
    }

    public void alpha(final float a) {
        this.alpha = Math.max(0f, Math.min(1f, a));
    }

    /** Pixels per GUI unit of the frame being drawn. */
    public float scale() {
        return scale;
    }

    // ------------------------------------------------------------------ shapes (GUI units)

    public void rect(final float x, final float y, final float w, final float h, final int argb) {
        if (w <= 0 || h <= 0) return;
        quad(SOLID, 0, x, y, x + w, y + h, 0, 0, 1, 1, argb, argb, argb, argb);
    }

    /** Left to right from {@code left} to {@code right}. */
    public void hgradient(final float x, final float y, final float w, final float h, final int left, final int right) {
        if (w <= 0 || h <= 0) return;
        quad(SOLID, 0, x, y, x + w, y + h, 0, 0, 1, 1, left, right, right, left);
    }

    /** Top to bottom from {@code top} to {@code bottom}. */
    public void vgradient(final float x, final float y, final float w, final float h, final int top, final int bottom) {
        if (w <= 0 || h <= 0) return;
        quad(SOLID, 0, x, y, x + w, y + h, 0, 0, 1, 1, top, top, bottom, bottom);
    }

    /** A rectangle with stepped pixel corners of {@code radius}, as Slate's panels ({@code SlateDraw.pixelRound}). */
    public void pixelRound(final float x, final float y, final float w, final float h, final int argb, final int radius) {
        final int r = (int) Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) {
            rect(x, y, w, h, argb);
            return;
        }
        rect(x, y + r, w, h - 2 * r, argb);
        for (int i = 0; i < r; i++) {
            final int inset = r - i;
            rect(x + inset, y + i, w - 2 * inset, 1, argb);
            rect(x + inset, y + h - 1 - i, w - 2 * inset, 1, argb);
        }
    }

    /** A one-unit outline matching {@link #pixelRound} ({@code SlateDraw.outline}). */
    public void outline(final float x, final float y, final float w, final float h, final int argb, final int radius) {
        final int r = (int) Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        rect(x + r, y, w - 2 * r, 1, argb);
        rect(x + r, y + h - 1, w - 2 * r, 1, argb);
        rect(x, y + r, 1, h - 2 * r, argb);
        rect(x + w - 1, y + r, 1, h - 2 * r, argb);
        for (int i = 1; i <= r; i++) {
            final int px = r - i, py = i - 1;
            rect(x + px, y + py + 1, 1, 1, argb);
            rect(x + w - 1 - px, y + py + 1, 1, 1, argb);
            rect(x + px, y + h - 2 - py, 1, 1, argb);
            rect(x + w - 1 - px, y + h - 2 - py, 1, 1, argb);
        }
    }

    /** Slate's hard panel shadow: only its visible L (bottom and right bands), so it never doubles under the panel. */
    public void shadow(final float x, final float y, final float w, final float h, final int argb, final int radius) {
        pixelRound(x + 2, y + h - radius - 2, w, radius + 4, argb, radius);
        rect(x + w, y + 2, 2, h - radius - 4, argb);
    }

    /** Edges darkened towards the corners, over the whole target. */
    public void vignette(final float w, final float h, final int argb) {
        quad(VIGNETTE, 0, 0, 0, w, h, 0, 0, 1, 1, argb, argb, argb, argb);
    }

    /**
     * A picture from a colour texture, tinted by {@code argb}. {@code v0} is the texture row shown at the top of the
     * rectangle: a texture rendered by OpenGL has its first row at the bottom, so it is drawn from 1 to 0.
     */
    public void image(final int tex, final float x, final float y, final float w, final float h,
                      final float u0, final float v0, final float u1, final float v1, final int argb) {
        if (w <= 0 || h <= 0) return;
        quad(IMAGE, tex, x, y, x + w, y + h, u0, v0, u1, v1, argb, argb, argb, argb);
    }

    /** A textured quad in pixel space from a glyph atlas (positions already in pixels). */
    void glyph(final int atlas, final float x0, final float y0, final float x1, final float y1,
               final float u0, final float v0, final float u1, final float v1, final int argb) {
        quadPx(TEXT, atlas, x0, y0, x1, y1, u0, v0, u1, v1, argb, argb, argb, argb);
    }

    // ------------------------------------------------------------------ batching

    private void quad(final int m, final int tex, final float x0, final float y0, final float x1, final float y1,
                      final float u0, final float v0, final float u1, final float v1, final int c0, final int c1, final int c2, final int c3) {
        quadPx(m, tex, x0 * scale, y0 * scale, x1 * scale, y1 * scale, u0, v0, u1, v1, c0, c1, c2, c3);
    }

    /** Corners clockwise from the top left: {@code c0} top left, {@code c1} top right, {@code c2} bottom right, {@code c3} bottom left. */
    private void quadPx(final int m, final int tex, final float x0, final float y0, final float x1, final float y1,
                        final float u0, final float v0, final float u1, final float v1, final int c0, final int c1, final int c2, final int c3) {
        final boolean textured = m == TEXT || m == IMAGE;
        if (m != mode || textured && tex != texture) {
            flush();
            mode = m;
            texture = tex;
        }
        if (vertices + 6 > CAPACITY) flush();
        vertex(x0, y0, u0, v0, c0);
        vertex(x1, y0, u1, v0, c1);
        vertex(x1, y1, u1, v1, c2);
        vertex(x0, y0, u0, v0, c0);
        vertex(x1, y1, u1, v1, c2);
        vertex(x0, y1, u0, v1, c3);
    }

    private void vertex(final float x, final float y, final float u, final float v, final int argb) {
        final int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
        buffer.putFloat(x).putFloat(y).putFloat(u).putFloat(v)
            .put((byte) (argb >>> 16)).put((byte) (argb >>> 8)).put((byte) argb).put((byte) a);
        vertices++;
    }

    private void flush() {
        if (vertices == 0) return;
        buffer.flip();
        glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);
        glUniform1i(uMode, mode);
        if (mode == TEXT || mode == IMAGE) glBindTexture(GL_TEXTURE_2D, texture);
        glDrawArrays(GL_TRIANGLES, 0, vertices);
        buffer.clear();
        vertices = 0;
    }
}
