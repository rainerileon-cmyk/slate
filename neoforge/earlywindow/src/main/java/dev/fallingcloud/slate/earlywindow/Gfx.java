package dev.fallingcloud.slate.earlywindow;

import static org.lwjgl.opengl.GL32C.*;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

/**
 * A small 2D renderer for the start-up window, in FML's GL context (its render thread early on, the game's main
 * thread once the game has the window): coloured and gradient quads, a vignette and alpha-atlas text, batched per mode
 * into one stream buffer. Positions are GUI units, scaled to canvas pixels here. Everything it binds is restored in
 * {@link #end}, since FML (and later the game's GlStateManager, which caches bindings) keeps drawing afterwards.
 */
final class Gfx {

    static final int SOLID = 0, TEXT = 1, VIGNETTE = 2;

    // y = 0 at the bottom of GL's target, as FML's own shader: FML flips its canvas when it shows it (the window blit
    // and the game's loading overlay both), so this puts y = 0 at the top of the screen.
    private static final String VERTEX = """
        #version 150
        in vec2 aPos;
        in vec2 aUv;
        in vec4 aColor;
        uniform vec2 uScreen;
        out vec2 vUv;
        out vec4 vColor;
        void main() {
            gl_Position = vec4(aPos / uScreen * 2.0 - 1.0, 0.0, 1.0);
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
            } else {
                fragColor = vColor;
            }
        }
        """;
    /** x, y, u, v as floats, then RGBA bytes. */
    private static final int STRIDE = 4 * 4 + 4;
    private static final int CAPACITY = 6 * 2048;

    private int program, vao, vbo, uScreen, uMode, uTex;
    private final ByteBuffer buffer = MemoryUtil.memAlloc(CAPACITY * STRIDE);
    private int vertices;
    private int mode = -1, texture;
    private float scale = 1f;
    /** Every colour's alpha is multiplied by this (the fade-in). */
    private float alpha = 1f;

    // GL state saved in begin, restored in end.
    private int savedProgram, savedVao, savedBuffer, savedActive, savedTexture, savedSrcRgb, savedDstRgb, savedSrcA, savedDstA, savedUnpack;
    private boolean savedBlend;

    private void init() {
        final int vs = shader(GL_VERTEX_SHADER, VERTEX), fs = shader(GL_FRAGMENT_SHADER, FRAGMENT);
        program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glBindAttribLocation(program, 0, "aPos");
        glBindAttribLocation(program, 1, "aUv");
        glBindAttribLocation(program, 2, "aColor");
        glBindFragDataLocation(program, 0, "fragColor");
        glLinkProgram(program);
        glDeleteShader(vs);
        glDeleteShader(fs);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) throw new IllegalStateException("link: " + glGetProgramInfoLog(program));
        uScreen = glGetUniformLocation(program, "uScreen");
        uMode = glGetUniformLocation(program, "uMode");
        uTex = glGetUniformLocation(program, "uTex");
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

    private static int shader(final int type, final String source) {
        final int s = glCreateShader(type);
        glShaderSource(s, source);
        glCompileShader(s);
        if (glGetShaderi(s, GL_COMPILE_STATUS) == GL_FALSE) throw new IllegalStateException("compile: " + glGetShaderInfoLog(s));
        return s;
    }

    /** Starts a frame on a {@code pixelW} x {@code pixelH} target with {@code scale} pixels per GUI unit. */
    void begin(final int pixelW, final int pixelH, final float scale) {
        savedProgram = glGetInteger(GL_CURRENT_PROGRAM);
        savedVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        savedBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        savedActive = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        savedTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        savedBlend = glIsEnabled(GL_BLEND);
        savedSrcRgb = glGetInteger(GL_BLEND_SRC_RGB);
        savedDstRgb = glGetInteger(GL_BLEND_DST_RGB);
        savedSrcA = glGetInteger(GL_BLEND_SRC_ALPHA);
        savedDstA = glGetInteger(GL_BLEND_DST_ALPHA);
        savedUnpack = glGetInteger(GL_UNPACK_ALIGNMENT);
        if (program == 0) init();
        this.scale = scale;
        glUseProgram(program);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glUniform2f(uScreen, pixelW, pixelH);
        glUniform1i(uTex, 0);
        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        mode = -1;
        vertices = 0;
        alpha = 1f;
    }

    void end() {
        flush();
        glUseProgram(savedProgram);
        glBindVertexArray(savedVao);
        glBindBuffer(GL_ARRAY_BUFFER, savedBuffer);
        glBindTexture(GL_TEXTURE_2D, savedTexture);
        glActiveTexture(savedActive);
        if (!savedBlend) glDisable(GL_BLEND);
        glBlendFuncSeparate(savedSrcRgb, savedDstRgb, savedSrcA, savedDstA);
        glPixelStorei(GL_UNPACK_ALIGNMENT, savedUnpack);
    }

    /** Frees the GL objects; call with the context current. */
    void delete() {
        if (program != 0) {
            glDeleteProgram(program);
            glDeleteVertexArrays(vao);
            glDeleteBuffers(vbo);
            program = 0;
        }
        MemoryUtil.memFree(buffer);
    }

    void alpha(final float a) {
        this.alpha = Math.max(0f, Math.min(1f, a));
    }

    // ------------------------------------------------------------------ shapes (GUI units)

    void rect(final float x, final float y, final float w, final float h, final int argb) {
        if (w <= 0 || h <= 0) return;
        quad(SOLID, 0, x, y, x + w, y + h, 0, 0, 1, 1, argb, argb, argb, argb);
    }

    /** Left to right from {@code left} to {@code right}. */
    void hgradient(final float x, final float y, final float w, final float h, final int left, final int right) {
        if (w <= 0 || h <= 0) return;
        quad(SOLID, 0, x, y, x + w, y + h, 0, 0, 1, 1, left, right, right, left);
    }

    /** A rectangle with stepped pixel corners of {@code radius}, as Slate's panels ({@code SlateDraw.pixelRound}). */
    void pixelRound(final float x, final float y, final float w, final float h, final int argb, final int radius) {
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
    void outline(final float x, final float y, final float w, final float h, final int argb, final int radius) {
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
    void shadow(final float x, final float y, final float w, final float h, final int argb, final int radius) {
        pixelRound(x + 2, y + h - radius - 2, w, radius + 4, argb, radius);
        rect(x + w, y + 2, 2, h - radius - 4, argb);
    }

    /** Edges darkened towards the corners, over the whole target. */
    void vignette(final float w, final float h, final int argb) {
        quad(VIGNETTE, 0, 0, 0, w, h, 0, 0, 1, 1, argb, argb, argb, argb);
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
        if (m != mode || m == TEXT && tex != texture) {
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
        if (mode == TEXT) glBindTexture(GL_TEXTURE_2D, texture);
        glDrawArrays(GL_TRIANGLES, 0, vertices);
        buffer.clear();
        vertices = 0;
    }
}
