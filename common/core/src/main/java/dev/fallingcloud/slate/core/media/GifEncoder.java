package dev.fallingcloud.slate.core.media;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Writes an animated GIF from frames of the game. Every frame gets a palette of its own (median cut over what is in
 * it, so a sunset keeps its reds and a cave its greys), and only what has changed since the frame before is written:
 * the rest of the frame is left see-through over what is already there, which is what keeps a recording of a menu or
 * of a player standing still small. Frames in which nothing changed are not written at all; the frame before them
 * is shown for longer.
 *
 * <p>Plain Java, no image library: the game's runtime is not certain to have one that writes GIFs well.</p>
 */
public final class GifEncoder {

    /** A palette has 256 places; the last is what stands for "as it was". */
    private static final int COLOURS = 255, SAME = 255;
    /** Red and blue to 5 bits, green to 6: what the eye tells apart best gets the most. */
    private static final int BINS = 1 << 16;
    /** How far a colour may be from what is shown for the pixel to count as unchanged, per channel. */
    private static final int TOLERANCE = 5;

    /**
     * @param frames  the frames, each {@code w * h} pixels as ARGB (alpha is ignored), row by row from the top
     * @param delays  how long each frame is shown, in milliseconds
     * @return the file's bytes
     */
    public static byte[] encode(final List<int[]> frames, final int[] delays, final int w, final int h) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(frames.size() * w * h / 3 + 1024);
        ascii(out, "GIF89a");
        word(out, w);
        word(out, h);
        out.write(0x70);                                   // no palette for all frames; 8 bits a channel
        out.write(0);
        out.write(0);
        out.write(0x21);                                   // plays over and over
        out.write(0xFF);
        out.write(11);
        ascii(out, "NETSCAPE2.0");
        out.write(3);
        out.write(1);
        word(out, 0);
        out.write(0);

        // What has to be written: a frame for every change, shown until the next one.
        final List<Part> parts = new ArrayList<>();
        final int[] shown = new int[w * h];
        for (int f = 0; f < frames.size(); f++) {
            final int[] frame = frames.get(f);
            final int delay = Math.max(20, f < delays.length ? delays[f] : 80);
            final Part part = f == 0 ? whole(frame, w, h) : changed(frame, shown, w, h);
            if (part == null) {
                if (!parts.isEmpty()) parts.get(parts.size() - 1).delay += delay;
                continue;
            }
            part.delay = delay;
            quantize(part, frame, shown, w);
            parts.add(part);
        }
        for (final Part part : parts) write(out, part);
        out.write(0x3B);
        return out.toByteArray();
    }

    /**
     * A frame at another size, every pixel the mean of what it covers: for a smaller file, and for a frame captured
     * larger than it is wanted.
     */
    public static int[] scale(final int[] src, final int w, final int h, final int nw, final int nh) {
        if (nw == w && nh == h) return src;
        final int[] out = new int[nw * nh];
        for (int y = 0; y < nh; y++) {
            final int y0 = y * h / nh, y1 = Math.max(y0 + 1, (y + 1) * h / nh);
            for (int x = 0; x < nw; x++) {
                final int x0 = x * w / nw, x1 = Math.max(x0 + 1, (x + 1) * w / nw);
                long r = 0, g = 0, b = 0;
                for (int sy = y0; sy < y1; sy++) {
                    for (int sx = x0; sx < x1; sx++) {
                        final int c = src[sy * w + sx];
                        r += (c >>> 16) & 0xFF;
                        g += (c >>> 8) & 0xFF;
                        b += c & 0xFF;
                    }
                }
                final int n = (y1 - y0) * (x1 - x0);
                out[y * nw + x] = 0xFF000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ what changed

    /** One image of the file: a rectangle of a frame, its palette, a palette index for each of its pixels. */
    private static final class Part {
        int x, y, w, h, delay;
        boolean sameUsed;
        byte[] palette;
        byte[] pixels;
        /** Per pixel of the rectangle: whether it is as it was and is left out. */
        boolean[] same;
    }

    private static Part whole(final int[] frame, final int w, final int h) {
        final Part p = new Part();
        p.w = w;
        p.h = h;
        p.same = new boolean[w * h];
        return p;
    }

    /** The rectangle round everything that is not what is shown already, or null when nothing is. */
    private static Part changed(final int[] frame, final int[] shown, final int w, final int h) {
        int x0 = w, y0 = h, x1 = -1, y1 = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (near(frame[y * w + x], shown[y * w + x])) continue;
                if (x < x0) x0 = x;
                if (x > x1) x1 = x;
                if (y < y0) y0 = y;
                if (y > y1) y1 = y;
            }
        }
        if (x1 < 0) return null;
        final Part p = new Part();
        p.x = x0;
        p.y = y0;
        p.w = x1 - x0 + 1;
        p.h = y1 - y0 + 1;
        p.same = new boolean[p.w * p.h];
        for (int y = 0; y < p.h; y++) {
            for (int x = 0; x < p.w; x++) {
                final int i = (y0 + y) * w + x0 + x;
                if (near(frame[i], shown[i])) {
                    p.same[y * p.w + x] = true;
                    p.sameUsed = true;
                }
            }
        }
        return p;
    }

    private static boolean near(final int a, final int b) {
        return Math.abs(((a >>> 16) & 0xFF) - ((b >>> 16) & 0xFF)) <= TOLERANCE
            && Math.abs(((a >>> 8) & 0xFF) - ((b >>> 8) & 0xFF)) <= TOLERANCE
            && Math.abs((a & 0xFF) - (b & 0xFF)) <= TOLERANCE;
    }

    // ------------------------------------------------------------------ the palette

    private static int bin(final int argb) {
        return ((argb >>> 19) & 0x1F) << 11 | ((argb >>> 10) & 0x3F) << 5 | ((argb >>> 3) & 0x1F);
    }

    /** A box of colours in the median cut: a stretch of the list of colours that are there, and what it spans. */
    private static final class Box {
        int from, to;
        int r0, r1, g0, g1, b0, b1;
        long count;

        int longest() {
            // Green is counted at half its steps: it has twice as many.
            return Math.max(Math.max(r1 - r0, (g1 - g0) / 2), b1 - b0);
        }
    }

    /**
     * Gives the part its palette and its pixels, and notes what is shown from now on. The colours that are in the
     * part are cut into boxes of as nearly the same number of pixels as can be, a box to a place in the palette.
     */
    private static void quantize(final Part p, final int[] frame, final int[] shown, final int w) {
        final int[] count = new int[BINS];
        int colours = 0;
        for (int y = 0; y < p.h; y++) {
            for (int x = 0; x < p.w; x++) {
                if (p.same[y * p.w + x]) continue;
                if (count[bin(frame[(p.y + y) * w + p.x + x])]++ == 0) colours++;
            }
        }
        final int[] list = new int[colours];
        int n = 0;
        for (int i = 0; i < BINS; i++) if (count[i] > 0) list[n++] = i;

        final List<Box> boxes = new ArrayList<>();
        if (colours > 0) boxes.add(span(list, count, 0, colours));
        while (boxes.size() < COLOURS) {
            Box widest = null;
            long most = 0;
            for (final Box b : boxes) {
                if (b.to - b.from < 2) continue;
                final long score = b.count * (b.longest() + 1);
                if (score > most) {
                    most = score;
                    widest = b;
                }
            }
            if (widest == null) break;
            final Box box = widest;
            final int axis = box.r1 - box.r0 >= (box.g1 - box.g0) / 2 && box.r1 - box.r0 >= box.b1 - box.b0 ? 0
                : (box.g1 - box.g0) / 2 >= box.b1 - box.b0 ? 1 : 2;
            sort(list, box.from, box.to, axis);
            long half = 0;
            int cut = box.from;
            while (cut < box.to - 1 && half + count[list[cut]] <= box.count / 2) half += count[list[cut++]];
            if (cut == box.from) cut++;
            boxes.remove(box);
            boxes.add(span(list, count, box.from, cut));
            boxes.add(span(list, count, cut, box.to));
        }

        p.palette = new byte[256 * 3];
        final int[] placeOf = new int[BINS];
        final int[] colourOf = new int[256];
        for (int i = 0; i < boxes.size(); i++) {
            final Box b = boxes.get(i);
            long r = 0, g = 0, bl = 0;
            for (int k = b.from; k < b.to; k++) {
                final int c = list[k], weight = count[c];
                final int r5 = (c >>> 11) & 0x1F, g6 = (c >>> 5) & 0x3F, b5 = c & 0x1F;
                r += (long) (r5 << 3 | r5 >>> 2) * weight;
                g += (long) (g6 << 2 | g6 >>> 4) * weight;
                bl += (long) (b5 << 3 | b5 >>> 2) * weight;
                placeOf[c] = i;
            }
            final int cr = (int) (r / b.count), cg = (int) (g / b.count), cb = (int) (bl / b.count);
            p.palette[i * 3] = (byte) cr;
            p.palette[i * 3 + 1] = (byte) cg;
            p.palette[i * 3 + 2] = (byte) cb;
            colourOf[i] = 0xFF000000 | cr << 16 | cg << 8 | cb;
        }

        p.pixels = new byte[p.w * p.h];
        for (int y = 0; y < p.h; y++) {
            for (int x = 0; x < p.w; x++) {
                final int i = y * p.w + x, at = (p.y + y) * w + p.x + x;
                if (p.same[i]) {
                    p.pixels[i] = (byte) SAME;
                } else {
                    final int place = placeOf[bin(frame[at])];
                    p.pixels[i] = (byte) place;
                    shown[at] = colourOf[place];
                }
            }
        }
    }

    private static Box span(final int[] list, final int[] count, final int from, final int to) {
        final Box b = new Box();
        b.from = from;
        b.to = to;
        b.r0 = b.g0 = b.b0 = Integer.MAX_VALUE;
        b.r1 = b.g1 = b.b1 = -1;
        for (int k = from; k < to; k++) {
            final int c = list[k];
            final int r = (c >>> 11) & 0x1F, g = (c >>> 5) & 0x3F, bl = c & 0x1F;
            b.r0 = Math.min(b.r0, r);
            b.r1 = Math.max(b.r1, r);
            b.g0 = Math.min(b.g0, g);
            b.g1 = Math.max(b.g1, g);
            b.b0 = Math.min(b.b0, bl);
            b.b1 = Math.max(b.b1, bl);
            b.count += count[c];
        }
        return b;
    }

    /** Sorts a stretch of the list of colours by one channel: by moving that channel to the front and back again. */
    private static void sort(final int[] list, final int from, final int to, final int axis) {
        final int shift = axis == 0 ? 11 : axis == 1 ? 5 : 0, mask = axis == 1 ? 0x3F : 0x1F;
        final long[] keyed = new long[to - from];
        for (int k = from; k < to; k++) keyed[k - from] = (long) ((list[k] >>> shift) & mask) << 32 | list[k];
        Arrays.sort(keyed);
        for (int k = from; k < to; k++) list[k] = (int) keyed[k - from];
    }

    // ------------------------------------------------------------------ the file

    private static void write(final ByteArrayOutputStream out, final Part p) {
        out.write(0x21);                                   // how the image is shown
        out.write(0xF9);
        out.write(4);
        out.write(0x04 | (p.sameUsed ? 1 : 0));            // stays when the next comes; has a see-through colour
        word(out, Math.max(2, Math.round(p.delay / 10f)));
        out.write(p.sameUsed ? SAME : 0);
        out.write(0);
        out.write(0x2C);                                   // where it is
        word(out, p.x);
        word(out, p.y);
        word(out, p.w);
        word(out, p.h);
        out.write(0x87);                                   // a palette of its own, 256 places
        out.write(p.palette, 0, p.palette.length);
        pack(out, p.pixels);
    }

    /** The pixels packed as GIF packs them (LZW with codes of 9 to 12 bits), in blocks of up to 255 bytes. */
    private static void pack(final ByteArrayOutputStream out, final byte[] pixels) {
        final int table = 5003, clear = 256, end = 257;
        final int[] key = new int[table], code = new int[table];
        Arrays.fill(key, -1);
        out.write(8);
        final Bits bits = new Bits(out);
        int width = 9, next = 258, limit = 1 << width;
        bits.put(clear, width);
        if (pixels.length == 0) {
            bits.put(end, width);
            bits.close();
            return;
        }
        int run = pixels[0] & 0xFF;
        outer:
        for (int i = 1; i < pixels.length; i++) {
            final int c = pixels[i] & 0xFF;
            final int k = c << 12 | run;
            int at = (c << 4 ^ run) % table;
            while (key[at] >= 0) {
                if (key[at] == k) {
                    run = code[at];
                    continue outer;
                }
                if (++at == table) at = 0;
            }
            bits.put(run, width);
            run = c;
            if (next < 4096) {
                key[at] = k;
                code[at] = next;
                // The reader makes its table one code behind: the codes grow a bit wider when it has caught up.
                if (next == limit && width < 12) {
                    width++;
                    limit = 1 << width;
                }
                next++;
            } else {
                bits.put(clear, width);
                Arrays.fill(key, -1);
                width = 9;
                limit = 1 << width;
                next = 258;
            }
        }
        bits.put(run, width);
        if (next == limit && width < 12) width++;
        bits.put(end, width);
        bits.close();
    }

    /** Codes of any width, written low bit first into blocks that say how long they are. */
    private static final class Bits {
        private final ByteArrayOutputStream out;
        private final byte[] block = new byte[255];
        private int length, held, bits;

        Bits(final ByteArrayOutputStream out) {
            this.out = out;
        }

        void put(final int code, final int width) {
            held |= code << bits;
            bits += width;
            while (bits >= 8) {
                add(held & 0xFF);
                held >>>= 8;
                bits -= 8;
            }
        }

        private void add(final int b) {
            block[length++] = (byte) b;
            if (length == 255) flush();
        }

        private void flush() {
            if (length == 0) return;
            out.write(length);
            out.write(block, 0, length);
            length = 0;
        }

        void close() {
            if (bits > 0) add(held & 0xFF);
            flush();
            out.write(0);
        }
    }

    private static void word(final ByteArrayOutputStream out, final int v) {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
    }

    private static void ascii(final ByteArrayOutputStream out, final String s) {
        for (int i = 0; i < s.length(); i++) out.write(s.charAt(i));
    }

    private GifEncoder() {}
}
