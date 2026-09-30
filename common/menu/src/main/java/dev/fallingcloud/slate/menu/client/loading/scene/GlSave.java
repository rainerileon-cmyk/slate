package dev.fallingcloud.slate.menu.client.loading.scene;

import static org.lwjgl.opengl.GL32C.*;

/**
 * Everything of OpenGL's state the loading scene touches, read before a frame and put back after it. The scene draws
 * in somebody else's context: FML's start-up renderer keeps drawing afterwards, and so does the game, whose state
 * manager remembers what it set and would not set it again. What is read from the driver here is what is restored,
 * so both find the context as they left it.
 */
final class GlSave {

    private int program, vao, arrayBuffer, active, texture0, texture1, drawFbo, readFbo, renderbuffer;
    private int srcRgb, dstRgb, srcA, dstA, equationRgb, equationA, depthFunc, cullMode, frontFace;
    private int unpackAlignment, unpackRowLength, unpackSkipRows, unpackSkipPixels, unpackBuffer;
    private boolean blend, depth, cull, scissor, stencil, polygonOffset, multisample, depthMask;
    private final int[] viewport = new int[4];
    private final float[] clear = new float[4];
    private final int[] colourMask = new int[4];
    private final float[] offset = new float[2];
    private final float[] one = new float[1];

    void save() {
        program = glGetInteger(GL_CURRENT_PROGRAM);
        vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        arrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        active = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE1);
        texture1 = glGetInteger(GL_TEXTURE_BINDING_2D);
        glActiveTexture(GL_TEXTURE0);
        texture0 = glGetInteger(GL_TEXTURE_BINDING_2D);
        drawFbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        readFbo = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        renderbuffer = glGetInteger(GL_RENDERBUFFER_BINDING);
        glGetIntegerv(GL_VIEWPORT, viewport);
        blend = glIsEnabled(GL_BLEND);
        srcRgb = glGetInteger(GL_BLEND_SRC_RGB);
        dstRgb = glGetInteger(GL_BLEND_DST_RGB);
        srcA = glGetInteger(GL_BLEND_SRC_ALPHA);
        dstA = glGetInteger(GL_BLEND_DST_ALPHA);
        equationRgb = glGetInteger(GL_BLEND_EQUATION_RGB);
        equationA = glGetInteger(GL_BLEND_EQUATION_ALPHA);
        depth = glIsEnabled(GL_DEPTH_TEST);
        depthFunc = glGetInteger(GL_DEPTH_FUNC);
        depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        cull = glIsEnabled(GL_CULL_FACE);
        cullMode = glGetInteger(GL_CULL_FACE_MODE);
        frontFace = glGetInteger(GL_FRONT_FACE);
        scissor = glIsEnabled(GL_SCISSOR_TEST);
        stencil = glIsEnabled(GL_STENCIL_TEST);
        polygonOffset = glIsEnabled(GL_POLYGON_OFFSET_FILL);
        glGetFloatv(GL_POLYGON_OFFSET_FACTOR, one);
        offset[0] = one[0];
        glGetFloatv(GL_POLYGON_OFFSET_UNITS, one);
        offset[1] = one[0];
        multisample = glIsEnabled(GL_MULTISAMPLE);
        glGetFloatv(GL_COLOR_CLEAR_VALUE, clear);
        glGetIntegerv(GL_COLOR_WRITEMASK, colourMask);
        unpackAlignment = glGetInteger(GL_UNPACK_ALIGNMENT);
        unpackRowLength = glGetInteger(GL_UNPACK_ROW_LENGTH);
        unpackSkipRows = glGetInteger(GL_UNPACK_SKIP_ROWS);
        unpackSkipPixels = glGetInteger(GL_UNPACK_SKIP_PIXELS);
        unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
    }

    /** The framebuffer that was bound for drawing when the state was read: the canvas the frame is for. */
    int canvas() {
        return drawFbo;
    }

    void restore() {
        glUseProgram(program);
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, texture1);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture0);
        glActiveTexture(active);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFbo);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readFbo);
        glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        toggle(GL_BLEND, blend);
        glBlendFuncSeparate(srcRgb, dstRgb, srcA, dstA);
        glBlendEquationSeparate(equationRgb, equationA);
        toggle(GL_DEPTH_TEST, depth);
        glDepthFunc(depthFunc);
        glDepthMask(depthMask);
        toggle(GL_CULL_FACE, cull);
        glCullFace(cullMode);
        glFrontFace(frontFace);
        toggle(GL_SCISSOR_TEST, scissor);
        toggle(GL_STENCIL_TEST, stencil);
        toggle(GL_POLYGON_OFFSET_FILL, polygonOffset);
        glPolygonOffset(offset[0], offset[1]);
        toggle(GL_MULTISAMPLE, multisample);
        glClearColor(clear[0], clear[1], clear[2], clear[3]);
        glColorMask(colourMask[0] != 0, colourMask[1] != 0, colourMask[2] != 0, colourMask[3] != 0);
        glPixelStorei(GL_UNPACK_ALIGNMENT, unpackAlignment);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, unpackRowLength);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, unpackSkipRows);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, unpackSkipPixels);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
    }

    /** Pixel unpacking as a plain upload of tightly packed rows expects it. */
    static void plainUnpack() {
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
    }

    private static void toggle(final int cap, final boolean on) {
        if (on) glEnable(cap);
        else glDisable(cap);
    }
}
