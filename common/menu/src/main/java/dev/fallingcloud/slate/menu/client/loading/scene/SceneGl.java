package dev.fallingcloud.slate.menu.client.loading.scene;

import static org.lwjgl.opengl.GL32C.*;

import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;

/**
 * The loading scene's own small 3D renderer in plain OpenGL 3.2: a shadow map from the key light, then the scene into
 * a multisampled target of the canvas' size with a soft, stylized light (a warm key light that wraps round the form,
 * a cool fill, a line of light on edges that turn away, lamps that fall off with distance, soft shadows), resolved
 * into a texture the 2D layer puts on the canvas. The same light as the stage engine's soft shader in the game, so
 * the loading scene and the menus that follow it look like one thing.
 *
 * <p>Nothing here restores OpenGL's state: the frame it is part of does ({@link GlSave}).</p>
 */
final class SceneGl {

    /** How the triangles of a pass meet what is already there. */
    static final int OPAQUE = 0, BLEND = 1, ADD = 2, MIRRORED = 3;
    private static final int SHADOW_SIDE = 2048;

    private static final String VERTEX = """
        #version 150
        in vec3 aPos;
        in vec4 aNormal;
        in vec2 aUv;
        in vec4 aColor;
        in vec4 aExtra;
        uniform mat4 uViewProj;
        uniform mat4 uLightViewProj;
        out vec3 vWorld;
        out vec3 vNormal;
        centroid out vec2 vUv;
        out vec4 vColor;
        flat out vec3 vExtra;
        out vec4 vShadow;
        void main() {
            vWorld = aPos;
            vNormal = aNormal.xyz;
            vUv = aUv;
            vColor = aColor;
            vExtra = aExtra.xyz;
            vShadow = uLightViewProj * vec4(aPos + aNormal.xyz * 0.035, 1.0);
            gl_Position = uViewProj * vec4(aPos, 1.0);
        }
        """;

    private static final String FRAGMENT = """
        #version 150
        in vec3 vWorld;
        in vec3 vNormal;
        centroid in vec2 vUv;
        in vec4 vColor;
        flat in vec3 vExtra;
        in vec4 vShadow;

        uniform sampler2D uAtlas;
        uniform sampler2DShadow uShadowMap;
        uniform float uShadowOn;
        uniform float uShadowTexel;
        uniform vec3 uEye;
        uniform vec3 uKeyDir;
        uniform vec3 uKeyColor;
        uniform vec3 uFillDir;
        uniform vec3 uFillColor;
        uniform vec3 uAmbient;
        uniform vec3 uRimColor;
        uniform float uWrap;
        uniform vec4 uLamp[3];
        uniform vec3 uLampColor[3];
        uniform vec3 uBackground;
        uniform vec3 uFloorLit;
        uniform vec4 uPool;
        uniform vec4 uStage;
        uniform float uTime;

        out vec4 fragColor;

        float hash(vec2 p) {
            vec3 q = fract(vec3(p.xyx) * 0.1031);
            q += dot(q, q.yzx + 33.33);
            return fract((q.x + q.y) * q.z);
        }

        float noise(vec2 p) {
            vec2 i = floor(p), f = fract(p);
            f = f * f * (3.0 - 2.0 * f);
            return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
        }

        // How much of the key light gets here: 25 looks at the shadow map round the place, each one smoothed by the
        // driver, so the edge of a shadow is a soft ramp.
        float shadow() {
            if (uShadowOn < 0.5) return 1.0;
            vec3 p = vShadow.xyz / vShadow.w * 0.5 + 0.5;
            if (p.x <= 0.0 || p.x >= 1.0 || p.y <= 0.0 || p.y >= 1.0 || p.z >= 1.0) return 1.0;
            float s = 0.0;
            for (int x = -2; x <= 2; x++) {
                for (int y = -2; y <= 2; y++) {
                    s += texture(uShadowMap, vec3(p.xy + vec2(x, y) * uShadowTexel * 1.6, p.z - 0.0012));
                }
            }
            return s / 25.0;
        }

        vec3 lamps(vec3 n, float turned) {
            vec3 sum = vec3(0.0);
            for (int i = 0; i < 3; i++) {
                vec3 d = uLamp[i].xyz - vWorld;
                float far = length(d);
                float a = clamp(1.0 - far / uLamp[i].w, 0.0, 1.0);
                float facing = mix(1.0, clamp(dot(n, d / max(far, 0.001)) * 0.5 + 0.5, 0.0, 1.0), turned);
                sum += uLampColor[i] * a * a * facing;
            }
            return sum;
        }

        // Lava as the game paints it, texel by texel, but made here: two layers of noise that drift through each other,
        // cut into a few steps of colour.
        vec3 lava(vec2 texel) {
            vec2 c = floor(texel);
            float a = noise(c * 0.23 + vec2(0.0, uTime * 0.42));
            float b = noise(c * 0.51 + vec2(uTime * 0.21, -uTime * 0.17));
            float n = floor((a * 0.62 + b * 0.38) * 7.0) / 6.0;
            vec3 dark = vec3(0.66, 0.14, 0.02), mid = vec3(0.98, 0.43, 0.05), hot = vec3(1.0, 0.83, 0.33);
            return n < 0.5 ? mix(dark, mid, n * 2.0) : mix(mid, hot, (n - 0.5) * 2.0);
        }

        void main() {
            int kind = int(vExtra.y * 255.0 / 32.0 + 0.5);
            float grain = (hash(gl_FragCoord.xy) - 0.5) / 255.0;

            if (kind == 2) {
                // A glow: strongest in the middle of its quad, nothing at its edge. Added to what is there.
                float r = length(vUv * 2.0 - 1.0);
                float a = clamp(1.0 - r, 0.0, 1.0);
                fragColor = vec4(vColor.rgb * a * a * vColor.a, 1.0);
                return;
            }
            if (kind == 1) {
                vec3 c = lava(vUv) * vColor.rgb;
                fragColor = vec4(c + grain, 1.0);
                return;
            }

            vec3 n = normalize(vNormal);
            float lit = shadow();

            if (kind == 3) {
                // The floor: the colour of the background, lifted where the light pools on it. Slabs of a block with
                // a seam between them show in the light and are gone in the dark, so the floor has no edge.
                vec2 q = (vWorld.xz - uPool.xy) / uPool.zw;
                float pool = exp(-dot(q, q) * 1.5);
                vec2 cell = floor(vWorld.xz);
                vec2 within = fract(vWorld.xz);
                float tone = hash(cell) * 0.16 - 0.08 + hash(floor(vWorld.xz * 16.0)) * 0.06 - 0.03;
                float seam = (within.x < 0.0625 || within.y < 0.0625) ? -0.22 : 0.0;
                vec3 slab = uFloorLit * (1.0 + tone + seam);
                // What stands on the floor takes light from it all round its foot.
                float off = length(max(abs(vWorld.xz - uStage.xy) - uStage.zw, 0.0));
                float foot = 1.0 - 0.62 * exp(-off * 2.2);
                vec3 c = mix(uBackground, slab, pool * (0.22 + 0.78 * lit) * foot);
                c += slab * lamps(n, 0.0) * 1.6;
                fragColor = vec4(c + grain, 1.0);
                return;
            }

            vec4 tex = texture(uAtlas, vUv);
            if (tex.a < 0.02) discard;
            vec3 base = tex.rgb * vColor.rgb;
            if (kind == 4) {
                fragColor = vec4(base, tex.a * vColor.a);
                return;
            }

            float key = clamp((dot(n, -uKeyDir) + uWrap) / (1.0 + uWrap), 0.0, 1.0);
            key = key * key * (3.0 - 2.0 * key);
            key *= mix(0.16, 1.0, lit);
            float fill = clamp(dot(n, uFillDir) * 0.5 + 0.5, 0.0, 1.0);
            vec3 light = uAmbient + uKeyColor * key + uFillColor * fill + lamps(n, 1.0);

            vec3 toEye = normalize(uEye - vWorld);
            float edge = 1.0 - clamp(dot(n, toEye), 0.0, 1.0);
            float rim = edge * edge * edge;
            vec3 c = base * light + uRimColor * rim * (0.35 + 0.65 * key);

            vec3 halfway = normalize(toEye - uKeyDir);
            float shine = pow(max(dot(n, halfway), 0.0), 28.0) * vExtra.z * lit;
            c += uKeyColor * shine;

            c = mix(c, base * 1.12, vExtra.x);
            fragColor = vec4(c + grain, tex.a * vColor.a);
        }
        """;

    private static final String DEPTH_VERTEX = """
        #version 150
        in vec3 aPos;
        uniform mat4 uLightViewProj;
        void main() {
            gl_Position = uLightViewProj * vec4(aPos, 1.0);
        }
        """;

    private static final String DEPTH_FRAGMENT = """
        #version 150
        out vec4 fragColor;
        void main() {
            fragColor = vec4(1.0);
        }
        """;

    private int program, depthProgram, vao, vbo, atlas;
    private ByteBuffer frame = MemoryUtil.memAlloc(1 << 20);
    private int shadowFbo, shadowMap;
    private int manyFbo, manyColour, manyDepth, flatFbo, flatColour;
    private int width, height, samples;

    private int uViewProj, uLightViewProj, uAtlas, uShadowMap, uShadowOn, uShadowTexel, uEye, uKeyDir, uKeyColor, uFillDir, uFillColor,
        uAmbient, uRimColor, uWrap, uLamp, uLampColor, uBackground, uFloorLit, uPool, uStage, uTime, uDepthLight;

    /** The light of a frame. Directions are the way the light travels; colours are linear factors, not bytes. */
    static final class Light {
        final float[] keyDir = {0.64f, -0.72f, -0.16f};
        final float[] keyColour = {1.02f, 0.94f, 0.80f};
        final float[] fillDir = {0.35f, 0.25f, 0.9f};
        final float[] fillColour = {0.20f, 0.24f, 0.32f};
        final float[] ambient = {0.27f, 0.27f, 0.30f};
        final float[] rim = {0.50f, 0.56f, 0.66f};
        float wrap = 0.45f;
        /** Up to three lamps: x, y, z, reach. */
        final float[] lamp = new float[12];
        final float[] lampColour = new float[9];
        final float[] background = new float[3];
        final float[] floorLit = new float[3];
        /** Where the light pools on the floor: centre x and z, radius x and z. */
        final float[] pool = {0f, 0f, 9f, 4f};
        /** What stands on the floor, its middle (x, z) and half its size: the floor is darker round its foot. */
        final float[] stage = {0f, 0f, 0f, 0f};
        float time;
    }

    private void init() {
        program = Gfx.link(VERTEX, FRAGMENT, "aPos", "aNormal", "aUv", "aColor", "aExtra");
        depthProgram = Gfx.link(DEPTH_VERTEX, DEPTH_FRAGMENT, "aPos");
        uViewProj = glGetUniformLocation(program, "uViewProj");
        uLightViewProj = glGetUniformLocation(program, "uLightViewProj");
        uAtlas = glGetUniformLocation(program, "uAtlas");
        uShadowMap = glGetUniformLocation(program, "uShadowMap");
        uShadowOn = glGetUniformLocation(program, "uShadowOn");
        uShadowTexel = glGetUniformLocation(program, "uShadowTexel");
        uEye = glGetUniformLocation(program, "uEye");
        uKeyDir = glGetUniformLocation(program, "uKeyDir");
        uKeyColor = glGetUniformLocation(program, "uKeyColor");
        uFillDir = glGetUniformLocation(program, "uFillDir");
        uFillColor = glGetUniformLocation(program, "uFillColor");
        uAmbient = glGetUniformLocation(program, "uAmbient");
        uRimColor = glGetUniformLocation(program, "uRimColor");
        uWrap = glGetUniformLocation(program, "uWrap");
        uLamp = glGetUniformLocation(program, "uLamp");
        uLampColor = glGetUniformLocation(program, "uLampColor");
        uBackground = glGetUniformLocation(program, "uBackground");
        uFloorLit = glGetUniformLocation(program, "uFloorLit");
        uPool = glGetUniformLocation(program, "uPool");
        uStage = glGetUniformLocation(program, "uStage");
        uTime = glGetUniformLocation(program, "uTime");
        uDepthLight = glGetUniformLocation(depthProgram, "uLightViewProj");

        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, Mesh.STRIDE, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 4, GL_BYTE, true, Mesh.STRIDE, 12);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 2, GL_FLOAT, false, Mesh.STRIDE, 16);
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(3, 4, GL_UNSIGNED_BYTE, true, Mesh.STRIDE, 24);
        glEnableVertexAttribArray(4);
        glVertexAttribPointer(4, 4, GL_UNSIGNED_BYTE, true, Mesh.STRIDE, 28);

        atlas = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, atlas);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        shadowMap = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, shadowMap);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, SHADOW_SIDE, SHADOW_SIDE, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
        shadowFbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, shadowFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, shadowMap, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        complete("shadow");
    }

    private static void complete(final String what) {
        final int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException(what + " target incomplete: 0x" + Integer.toHexString(status));
    }

    /** The targets at the canvas' size; made anew when it changes. */
    private void size(final int w, final int h) {
        if (w == width && h == height && manyFbo != 0) return;
        dropTargets();
        width = w;
        height = h;
        samples = Math.max(1, Math.min(4, glGetInteger(GL_MAX_SAMPLES)));
        manyColour = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, manyColour);
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_RGBA8, w, h);
        manyDepth = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, manyDepth);
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_DEPTH_COMPONENT24, w, h);
        manyFbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, manyFbo);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, manyColour);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, manyDepth);
        complete("scene");

        flatColour = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, flatColour);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        flatFbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, flatFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, flatColour, 0);
        complete("picture");
    }

    private void dropTargets() {
        if (manyFbo == 0) return;
        glDeleteFramebuffers(manyFbo);
        glDeleteFramebuffers(flatFbo);
        glDeleteRenderbuffers(manyColour);
        glDeleteRenderbuffers(manyDepth);
        glDeleteTextures(flatColour);
        manyFbo = 0;
    }

    /** Frees the GL objects; call with the context current. */
    void delete() {
        if (frame != null) MemoryUtil.memFree(frame);
        frame = null;
        if (program == 0) return;
        dropTargets();
        glDeleteProgram(program);
        glDeleteProgram(depthProgram);
        glDeleteVertexArrays(vao);
        glDeleteBuffers(vbo);
        glDeleteTextures(atlas);
        glDeleteTextures(shadowMap);
        glDeleteFramebuffers(shadowFbo);
        program = 0;
    }

    // ------------------------------------------------------------------ a frame

    /** The sheet as the texture everything is drawn from. */
    private void send(final Sheet sheet) {
        final int[] argb = sheet.pixels();
        final ByteBuffer bytes = MemoryUtil.memAlloc(argb.length * 4);
        try {
            for (final int c : argb) bytes.put((byte) (c >>> 16)).put((byte) (c >>> 8)).put((byte) c).put((byte) (c >>> 24));
            bytes.flip();
            glBindTexture(GL_TEXTURE_2D, atlas);
            GlSave.plainUnpack();
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, Sheet.SIDE, Sheet.SIDE, 0, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
        } finally {
            MemoryUtil.memFree(bytes);
        }
    }

    /** Readies the state all passes share and the targets for a canvas of {@code w} x {@code h}. */
    void begin(final int w, final int h, final Sheet sheet) {
        if (program == 0) init();
        if (sheet.takeChanged()) send(sheet);
        size(w, h);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_STENCIL_TEST);
        glEnable(GL_MULTISAMPLE);
        glColorMask(true, true, true, true);
        glFrontFace(GL_CCW);
        glCullFace(GL_BACK);
        glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
        glDepthFunc(GL_LEQUAL);
    }

    /** All the meshes of the frame into the one buffer they are drawn from. */
    void load(final Mesh... meshes) {
        int bytes = 0;
        for (final Mesh m : meshes) bytes += m.vertices() * Mesh.STRIDE;
        if (bytes > frame.capacity()) {
            MemoryUtil.memFree(frame);
            frame = MemoryUtil.memAlloc(Math.max(bytes, frame.capacity() * 2));
        }
        frame.clear();
        int first = 0;
        for (final Mesh m : meshes) {
            m.first = first;
            frame.put(m.data());
            first += m.vertices();
        }
        frame.flip();
        glBufferData(GL_ARRAY_BUFFER, frame, GL_STREAM_DRAW);
    }

    /** What throws a shadow, as the key light sees it. */
    void shadows(final Mesh casters, final float[] lightViewProj) {
        glBindFramebuffer(GL_FRAMEBUFFER, shadowFbo);
        glViewport(0, 0, SHADOW_SIDE, SHADOW_SIDE);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glClearDepth(1.0);
        glClear(GL_DEPTH_BUFFER_BIT);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(1.5f, 3f);
        glUseProgram(depthProgram);
        glUniformMatrix4fv(uDepthLight, false, lightViewProj);
        glDrawArrays(GL_TRIANGLES, casters.first, casters.vertices());
        glDisable(GL_POLYGON_OFFSET_FILL);
    }

    /** Starts the picture: the multisampled target, cleared to {@code background}. */
    void picture(final float[] background) {
        glBindFramebuffer(GL_FRAMEBUFFER, manyFbo);
        glViewport(0, 0, width, height);
        glDepthMask(true);
        glClearColor(background[0], background[1], background[2], 1f);
        glClearDepth(1.0);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glUseProgram(program);
        glUniform1i(uAtlas, 0);
        glUniform1i(uShadowMap, 1);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, shadowMap);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, atlas);
    }

    /** Forgets how deep everything drawn so far is: what comes next is a picture of its own, in front. */
    void inFront() {
        glDepthMask(true);
        glClear(GL_DEPTH_BUFFER_BIT);
    }

    /** The camera and the light for the passes that follow. */
    void view(final float[] viewProj, final float[] eye, final Light light, final float[] lightViewProj, final boolean shadowed) {
        glUniformMatrix4fv(uViewProj, false, viewProj);
        glUniformMatrix4fv(uLightViewProj, false, lightViewProj);
        glUniform1f(uShadowOn, shadowed ? 1f : 0f);
        glUniform1f(uShadowTexel, 1f / SHADOW_SIDE);
        glUniform3f(uEye, eye[0], eye[1], eye[2]);
        glUniform3f(uKeyDir, light.keyDir[0], light.keyDir[1], light.keyDir[2]);
        glUniform3f(uKeyColor, light.keyColour[0], light.keyColour[1], light.keyColour[2]);
        glUniform3f(uFillDir, light.fillDir[0], light.fillDir[1], light.fillDir[2]);
        glUniform3f(uFillColor, light.fillColour[0], light.fillColour[1], light.fillColour[2]);
        glUniform3f(uAmbient, light.ambient[0], light.ambient[1], light.ambient[2]);
        glUniform3f(uRimColor, light.rim[0], light.rim[1], light.rim[2]);
        glUniform1f(uWrap, light.wrap);
        glUniform4fv(uLamp, light.lamp);
        glUniform3fv(uLampColor, light.lampColour);
        glUniform3f(uBackground, light.background[0], light.background[1], light.background[2]);
        glUniform3f(uFloorLit, light.floorLit[0], light.floorLit[1], light.floorLit[2]);
        glUniform4f(uPool, light.pool[0], light.pool[1], light.pool[2], light.pool[3]);
        glUniform4f(uStage, light.stage[0], light.stage[1], light.stage[2], light.stage[3]);
        glUniform1f(uTime, light.time);
    }

    /** Only the camera anew, the light as it is: for the same things seen another way. */
    void camera(final float[] viewProj) {
        glUniformMatrix4fv(uViewProj, false, viewProj);
    }

    void draw(final Mesh mesh, final int how) {
        if (mesh.vertices() == 0) return;
        glEnable(GL_DEPTH_TEST);
        switch (how) {
            case MIRRORED -> {
                // A mirror turns every triangle round: both of its sides are drawn.
                glDepthMask(true);
                glDisable(GL_BLEND);
                glDisable(GL_CULL_FACE);
            }
            case OPAQUE -> {
                glDepthMask(true);
                glDisable(GL_BLEND);
                glEnable(GL_CULL_FACE);
            }
            case BLEND -> {
                glDepthMask(false);
                glEnable(GL_BLEND);
                glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
                glDisable(GL_CULL_FACE);
            }
            default -> {
                glDepthMask(false);
                glEnable(GL_BLEND);
                glBlendFuncSeparate(GL_ONE, GL_ONE, GL_ZERO, GL_ONE);
                glDisable(GL_CULL_FACE);
            }
        }
        glDrawArrays(GL_TRIANGLES, mesh.first, mesh.vertices());
    }

    /** The finished picture, its samples merged, as a texture of the canvas' size (its first row at the bottom). */
    int finish() {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, manyFbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, flatFbo);
        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
        return flatColour;
    }

    /** The atlas as a texture, for a look at it. */
    int atlas() {
        return atlas;
    }
}
