package dev.fallingcloud.slate.building.client.wheel;

import dev.fallingcloud.slate.building.client.gfx.ArcRaster;
import dev.fallingcloud.slate.building.client.gfx.PixelCanvas;
import dev.fallingcloud.slate.building.client.gfx.RingRaster;
import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The radial menu itself, shared by the Alt overlay, the build menu and the wheel editor preview: a ring of slices
 * around a centre disc, rasterised on the GUI pixel grid ({@link RingRaster}), with the real variant stacks rendered
 * as 3D items. Not a widget: owners feed it geometry, content and the hovered slice each frame and call
 * {@link #render}. All motion honours {@code Theme.motion()}:
 * <ul>
 *   <li>content changes stagger in (each slice 15 ms after the previous: fade, slide out from the centre, item pop);</li>
 *   <li>the hovered slice pushes out 3 px and brightens (120 ms);</li>
 *   <li>an accent arc outside the ring slides to the hovered slice along the shorter way round (160 ms);</li>
 *   <li>per-slice emphasis (search): matches glow, misses dim (160 ms).</li>
 * </ul>
 * Dark skin: layered surfaces with a hard 2 px shadow, accent edges and a soft inner glow on hover. Vanilla skin:
 * opaque dark slices with a faint stone menu tile, black edges and a grey bevel that turns white on hover.
 */
public final class RadialWheel {

    public static final int NONE = -1;
    public static final int CENTER = RingRaster.CENTER;

    private static final int STAGGER_MS = 15;
    private static final int ENTER_MS = 170;
    private static final int PUSH_PX = 3;

    private int cx, cy, rOut = 60, rIn = 27, rCenter = 22;
    private int gap = 2;
    private List<WheelSlice> slices = List.of();
    private @Nullable WheelSlice center;
    private int hovered = NONE;
    private Anim[] hover = new Anim[0];
    private Anim[] dim = new Anim[0];
    private Anim[] glow = new Anim[0];
    private long[] enterAt = new long[0];
    private final Anim centerHover = new Anim(0, 120, Ease.OUT_CUBIC);
    private final Anim arcAngle = new Anim(0, 160, Ease.OUT_CUBIC);
    private final Anim arcAlpha = new Anim(0, 120, Ease.OUT_CUBIC);
    private final Anim pulse = new Anim(0, 260, Ease.OUT_CUBIC);
    private int pulseIndex = NONE;
    private boolean numbers;
    private boolean textured = true;

    // ------------------------------------------------------------------ setup

    /** Centre and outer radius; the inner radius, centre disc and gap follow from it. */
    public RadialWheel geometry(final int centerX, final int centerY, final int outerRadius) {
        this.cx = centerX;
        this.cy = centerY;
        this.rOut = Math.max(24, outerRadius);
        this.rIn = Math.max(12, Math.round(rOut * 0.46f));
        this.rCenter = Math.max(8, rIn - 4);
        this.gap = rOut >= 48 ? 2 : 1;
        return this;
    }

    public int centerX() { return cx; }

    public int centerY() { return cy; }

    public int outerRadius() { return rOut; }

    public int innerRadius() { return rIn; }

    public int centerRadius() { return rCenter; }

    /** Radius at which slice items sit. */
    public float itemRadius() { return (rIn + rOut) / 2f; }

    /** Draw 1..9 next to the first nine slices (number-key hints). */
    public RadialWheel numbers(final boolean on) {
        this.numbers = on;
        return this;
    }

    /** Vanilla skin: tile the slices with the stone menu texture (true) or flat dark fills (false). */
    public RadialWheel textured(final boolean on) {
        this.textured = on;
        return this;
    }

    /**
     * New content. {@code animateIn} replays the staggered entrance (open, page change); otherwise states carry over
     * when the slice count is unchanged (a refresh after the held stack changed).
     */
    public void content(final List<WheelSlice> newSlices, final @Nullable WheelSlice newCenter, final boolean animateIn) {
        final boolean sameSize = newSlices.size() == slices.size();
        this.slices = List.copyOf(newSlices);
        this.center = newCenter;
        final int n = slices.size();
        if (!sameSize || animateIn) {
            hover = new Anim[n];
            dim = new Anim[n];
            glow = new Anim[n];
            enterAt = new long[n];
            for (int i = 0; i < n; i++) {
                hover[i] = new Anim(0, 120, Ease.OUT_CUBIC);
                dim[i] = new Anim(0, 160, Ease.OUT_CUBIC);
                glow[i] = new Anim(0, 160, Ease.OUT_CUBIC);
            }
            if (hovered >= n) hovered = NONE;
        }
        if (animateIn) replayEntrance();
    }

    /** Staggers the slices in again from now. */
    public void replayEntrance() {
        final float motion = Theme.current().motion();
        final long now = Clock.nowMs();
        for (int i = 0; i < enterAt.length; i++) enterAt[i] = motion <= 0 ? 0 : now + Math.round(i * STAGGER_MS * motion);
    }

    public List<WheelSlice> slices() { return slices; }

    public @Nullable WheelSlice center() { return center; }

    /** The hovered slice index, {@link #CENTER} or {@link #NONE}. */
    public int hovered() { return hovered; }

    public void hover(final int index) {
        final int idx = index >= slices.size() ? NONE : index;
        if (idx == hovered) return;
        hovered = idx;
        if (idx >= 0) {
            final float target = RingRaster.sliceAngle(idx, slices.size());
            if (arcAlpha.get() < 0.05f) {
                arcAngle.snap(target);
            } else {
                final float cur = arcAngle.get();
                float d = target - cur;
                d = (float) Math.atan2(Math.sin(d), Math.cos(d));   // shorter way round
                arcAngle.set(cur + d);
            }
        }
    }

    /** Search emphasis: {@code matches[i]} true = glow, false = dim; null clears it. */
    public void emphasis(final boolean @Nullable [] matches) {
        for (int i = 0; i < slices.size(); i++) {
            final boolean on = matches != null && i < matches.length;
            glow[i].set(on && matches[i] ? 1f : 0f);
            dim[i].set(on && !matches[i] ? 1f : 0f);
        }
    }

    /** A short flash on a slice (it was applied). */
    public void pulse(final int index) {
        pulseIndex = index;
        pulse.snap(1f);
        pulse.set(0f);
    }

    /** Slice under (x, y), {@link #CENTER} for the disc, {@link #NONE} elsewhere. */
    public int hitTest(final double x, final double y) {
        final double dx = x - cx, dy = y - cy;
        final double d = Math.sqrt(dx * dx + dy * dy);
        if (center != null && d <= rCenter + 1) return CENTER;
        if (slices.isEmpty() || d < rIn - 2 || d > rOut + PUSH_PX + 1) return NONE;
        return RingRaster.sliceAtAngle(dx, dy, slices.size());
    }

    /** Screen position of slice {@code i}'s item. */
    public int[] itemPos(final int i) {
        final float a = RingRaster.sliceAngle(i, slices.size());
        final float r = itemRadius();
        return new int[] {cx + Math.round((float) Math.cos(a) * r), cy + Math.round((float) Math.sin(a) * r)};
    }

    private float enter(final int i) {
        if (i >= enterAt.length || enterAt[i] == 0) return 1f;
        final long t = Clock.nowMs() - enterAt[i];
        if (t <= 0) return 0f;
        final float motion = Math.max(0.01f, Theme.current().motion());
        final float f = t / (ENTER_MS * motion);
        if (f >= 1f) { enterAt[i] = 0; return 1f; }
        return f;
    }

    /** Whether any entrance or state animation is still running. */
    public boolean animating() {
        for (final long e : enterAt) if (e != 0) return true;
        return arcAngle.isAnimating();
    }

    // ------------------------------------------------------------------ render

    /**
     * Draws the wheel. {@code alpha} fades everything (items shrink instead, they cannot fade); {@code scale}
     * zooms around the centre (the open animation).
     */
    public void render(final GuiGraphics g, final float alpha, final float scale) {
        if (alpha <= 0.01f) return;
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean vanilla = t.isVanilla();
        final int n = slices.size();
        final RingRaster raster = RingRaster.of(rIn, rOut, n, gap, center != null ? rCenter : 0);

        for (int i = 0; i < n; i++) hover[i].set(i == hovered);
        centerHover.set(hovered == CENTER);
        arcAlpha.set(hovered >= 0);

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1f);
        g.pose().translate(-cx, -cy, 0);

        final int[] ox = new int[n], oy = new int[n];
        final float[] enter = new float[n];
        for (int i = 0; i < n; i++) {
            final float e = enter(i);
            enter[i] = Ease.OUT_CUBIC.apply(e);
            final float push = hover[i].get() * PUSH_PX - (1f - enter[i]) * 6f;
            final float a = RingRaster.sliceAngle(i, n);
            ox[i] = Math.round((float) Math.cos(a) * push);
            oy[i] = Math.round((float) Math.sin(a) * push);
        }

        // 1) hard 2 px shadow under everything (dark skin)
        PixelCanvas c = PixelCanvas.begin(g).alpha(alpha);
        if (!vanilla) {
            final int shadow = p.shadow();
            for (int i = 0; i < n; i++) raster.draw(c.alpha(alpha * enter[i]), i, cx + ox[i] + 2, cy + oy[i] + 2, shadow, 0);
            if (center != null) raster.draw(c.alpha(alpha), RingRaster.CENTER, cx + 2, cy + 2, shadow, 0);
        }

        // 2) flat bodies and edges
        final int[] rims = new int[n];
        for (int i = 0; i < n; i++) {
            final WheelSlice s = slices.get(i);
            final float h = hover[i].get(), gl = glow[i].get(), dm = dim[i].get();
            final float pl = i == pulseIndex ? pulse.get() : 0f;
            final int body, edge, rim;
            if (vanilla) {
                int b = Colors.lerp(0xF0262626, 0xF0404040, h);
                if (s.current()) b = Colors.mix(b, p.accent(), 0.12f);
                if (!s.available()) b = 0xC0181818;
                body = Colors.mix(b, 0xFF6A6A6A, 0.4f * pl);
                edge = 0xFF000000;
                int r = Colors.lerp(0xFF4A4A4A, 0xFFFFFFFF, h);
                if (s.current()) r = Colors.lerp(r, p.accent(), 0.85f * (1f - h));
                rim = Colors.lerp(r, 0xFFFFFF80, gl * (1f - h));
            } else {
                int b = Colors.lerp(Colors.withAlpha(p.surface(), 0xE8), Colors.withAlpha(p.surfaceHover(), 0xF4), h);
                if (s.current()) b = Colors.mix(b, p.accent(), 0.14f);
                b = Colors.mix(b, p.accent(), 0.08f * h + 0.10f * gl + 0.35f * pl);
                if (!s.available()) b = Colors.withAlpha(p.surface(), 0x90);
                body = b;
                int e = Colors.lerp(p.border(), p.borderStrong(), 0.35f);
                if (s.current()) e = Colors.lerp(e, p.accent(), 0.55f);
                edge = Colors.lerp(e, p.accent(), Math.max(h, gl));
                // A soft second line inside the accent edge while hovered or matched: reads as a glow.
                rim = Colors.withAlpha(p.accent(), Math.round(0x50 * Math.max(h, gl * 0.8f)));
            }
            rims[i] = rim;
            raster.draw(c.alpha(alpha * enter[i] * (1f - 0.45f * dm)), i, cx + ox[i], cy + oy[i], body, edge);
        }
        int centerRim = 0;
        if (center != null) {
            final float h = centerHover.get();
            final int body, edge;
            if (vanilla) {
                body = Colors.lerp(0xF41C1C1C, 0xF4343434, h);
                edge = 0xFF000000;
                centerRim = Colors.lerp(center.current() ? p.accent() : 0xFF4A4A4A, 0xFFFFFFFF, h);
            } else {
                body = Colors.lerp(Colors.withAlpha(p.bg2(), 0xF0), Colors.withAlpha(p.surfaceActive(), 0xF4), h);
                edge = Colors.lerp(center.current() ? Colors.lerp(p.borderStrong(), p.accent(), 0.55f) : p.borderStrong(), p.accent(), h);
                centerRim = Colors.withAlpha(p.accent(), Math.round(0x50 * h));
            }
            raster.draw(c.alpha(alpha), RingRaster.CENTER, cx, cy, body, edge);
        }
        c.end();

        // 3) vanilla: the stone menu tile over the flat bodies, faint, for a material feel
        if (vanilla && textured) {
            final PixelCanvas.Textured tex = PixelCanvas.Textured.begin(g, SlateDraw.MENU_BACKGROUND, 32, 32);
            for (int i = 0; i < n; i++) {
                raster.drawTextured(tex, i, cx + ox[i], cy + oy[i], Colors.scaleAlpha(0x38FFFFFF, alpha * enter[i]));
            }
            if (center != null) raster.drawTextured(tex, RingRaster.CENTER, cx, cy, Colors.scaleAlpha(0x28FFFFFF, alpha));
            tex.end();
        }

        // 4) rims (bevel / glow) and the selection arc outside the ring
        c = PixelCanvas.begin(g);
        for (int i = 0; i < n; i++) {
            if (Colors.alpha(rims[i]) > 0) raster.drawRim(c.alpha(alpha * enter[i] * (1f - 0.45f * dim[i].get())), i, cx + ox[i], cy + oy[i], rims[i]);
        }
        if (center != null && Colors.alpha(centerRim) > 0) raster.drawRim(c.alpha(alpha), RingRaster.CENTER, cx, cy, centerRim);
        final float arcA = arcAlpha.get();
        if (arcA > 0.01f && n > 0) {
            final float step = (float) (Math.PI * 2 / n);
            final float mid = arcAngle.get();
            final float half = step / 2f - (n == 1 ? 0f : Math.min(step * 0.18f, 0.12f));
            final int ho = hovered >= 0 ? hovered : 0;
            final int push = Math.round(hover.length > ho ? hover[ho].get() * PUSH_PX : 0);
            final int r0 = rOut + 3 + push;
            final int accent = vanilla ? 0xFFFFFFFF : p.accent();
            if (n == 1) ArcRaster.of(r0, r0 + 1).drawFull(c.alpha(alpha * arcA), cx, cy, accent);
            else ArcRaster.of(r0, r0 + 1).draw(c.alpha(alpha * arcA), cx, cy, mid - half, mid + half, accent);
        }
        c.end();

        // 4) items (they cannot fade: they pop in with their slice and shrink when the wheel fades out)
        final float itemFade = 0.6f + 0.4f * alpha;
        final float r = itemRadius();
        for (int i = 0; i < n; i++) {
            final WheelSlice s = slices.get(i);
            final float a = RingRaster.sliceAngle(i, n);
            final float ix = cx + ox[i] + (float) Math.cos(a) * r, iy = cy + oy[i] + (float) Math.sin(a) * r;
            final float pop = Ease.OUT_BACK.apply(Mth.clamp(enter[i], 0f, 1f));
            final float sc = Math.max(0.01f, pop * itemFade * (1f + 0.18f * hover[i].get()) * itemScale());
            drawSliceItem(g, s, ix, iy, sc, alpha * enter[i], p, vanilla);
        }
        if (center != null) {
            final float sc = itemFade * (1.2f + 0.12f * centerHover.get()) * itemScale();
            drawSliceItem(g, center, cx, cy, sc, alpha, p, vanilla);
        }

        // 5) on top of the items: search dimming, locks, number hints
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        c = PixelCanvas.begin(g);
        for (int i = 0; i < n; i++) {
            final float dm = dim[i].get();
            if (dm > 0.01f) raster.draw(c.alpha(alpha * enter[i] * dm), i, cx + ox[i], cy + oy[i], vanilla ? 0xA0000000 : Colors.withAlpha(p.bg(), 0xB0), 0);
        }
        c.end();
        for (int i = 0; i < n; i++) {
            final WheelSlice s = slices.get(i);
            final float a = RingRaster.sliceAngle(i, n);
            final float ea = alpha * enter[i];
            if (s.lock() != null && s.available()) {
                final int lx = Math.round(cx + ox[i] + (float) Math.cos(a) * r) + 3;
                final int ly = Math.round(cy + oy[i] + (float) Math.sin(a) * r) + 2;
                Icons.draw(g, Icon.LOCK, lx, ly, 8, Colors.scaleAlpha(vanilla ? 0xFFFFFF55 : p.warning(), ea));
            }
            if (numbers && i < 9 && ea > 0.05f) {
                final float nr = rOut - 5.5f;
                final int nx = Math.round(cx + ox[i] + (float) Math.cos(a) * nr);
                final int ny = Math.round(cy + oy[i] + (float) Math.sin(a) * nr);
                final String digit = Integer.toString(i + 1);
                final int col = Colors.scaleAlpha(i == hovered ? (vanilla ? 0xFFFFFFFF : p.text()) : (vanilla ? 0xFFA0A0A0 : p.textDim()), ea);
                g.drawString(SlateDraw.font(), digit, nx - SlateDraw.width(digit) / 2 + 1, ny - 3, col, vanilla);
            }
        }
        g.pose().popPose();

        g.pose().popPose();
    }

    /** Items grow a little with big wheels so they keep filling their slice. */
    private float itemScale() {
        final float ring = rOut - rIn;
        return Mth.clamp(ring / 30f, 0.75f, 1.6f);
    }

    private static void drawSliceItem(final GuiGraphics g, final WheelSlice s, final float x, final float y, final float scale,
                                      final float alpha, final Palette p, final boolean vanilla) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        if (!s.stack().isEmpty()) {
            g.renderItem(s.stack(), -8, -8);
        } else {
            final int col = s.available() ? (vanilla ? 0xFFE0E0E0 : p.textMuted()) : (vanilla ? 0xFF707070 : p.textDim());
            Icons.draw(g, s.icon(), -8, -8, 16, Colors.scaleAlpha(col, alpha));
        }
        g.pose().popPose();
    }
}
