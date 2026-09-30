package dev.fallingcloud.slate.menu.client.loading.scene;

import static org.lwjgl.opengl.GL32C.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * A picture read from a PNG file's bytes.
 *
 * @param argb its pixels, row by row from the top
 */
record Picture(int w, int h, int[] argb) {

    /** The picture in the file, or null when there is no file or it is not a picture. */
    static Picture read(final byte[] file) {
        if (file == null || file.length == 0) return null;
        final ByteBuffer in = MemoryUtil.memAlloc(file.length).put(file).flip();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            final IntBuffer pw = stack.mallocInt(1), ph = stack.mallocInt(1), pc = stack.mallocInt(1);
            final ByteBuffer rgba = STBImage.stbi_load_from_memory(in, pw, ph, pc, 4);
            if (rgba == null) return null;
            try {
                final int w = pw.get(0), h = ph.get(0);
                final int[] argb = new int[w * h];
                for (int i = 0; i < argb.length; i++) {
                    final int r = rgba.get(i * 4) & 0xFF, g = rgba.get(i * 4 + 1) & 0xFF, b = rgba.get(i * 4 + 2) & 0xFF, a = rgba.get(i * 4 + 3) & 0xFF;
                    argb[i] = a << 24 | r << 16 | g << 8 | b;
                }
                return new Picture(w, h, argb);
            } finally {
                STBImage.stbi_image_free(rgba);
            }
        } finally {
            MemoryUtil.memFree(in);
        }
    }

    /**
     * The picture as a texture of its own, smoothed when it is drawn at another size than its own. Call with the
     * context current; the binding of the texture unit in use is what it is afterwards.
     */
    int texture() {
        final ByteBuffer bytes = MemoryUtil.memAlloc(argb.length * 4);
        try {
            for (final int c : argb) bytes.put((byte) (c >>> 16)).put((byte) (c >>> 8)).put((byte) c).put((byte) (c >>> 24));
            bytes.flip();
            final int texture = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, texture);
            GlSave.plainUnpack();
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            return texture;
        } finally {
            MemoryUtil.memFree(bytes);
        }
    }
}
