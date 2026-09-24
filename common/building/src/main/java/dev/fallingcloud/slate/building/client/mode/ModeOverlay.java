package dev.fallingcloud.slate.building.client.mode;

import dev.fallingcloud.slate.building.client.render.OverlayRenderer;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.ops.ModeKind;
import dev.fallingcloud.slate.building.ops.ModeParams;
import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The in-world drawing of building modes, submitted once per frame to {@link OverlayRenderer}: the selection box
 * (smoothly following the crosshair, colour-coded: accent = fine, warning = not enough materials, danger = cannot be
 * applied), corner markers, the size label, the line / radius guides of line, sphere and cylinder, the destination
 * of a move, the paste footprint, the aim cursor (also on air), the mirror planes and the radial spokes of TOGGLE
 * modes, and short flashes when a selection is applied (success) or cancelled (danger). Animations honour
 * {@code Theme.motion()} (0 = none).
 */
final class ModeOverlay {

    /** What the controller knows this frame. */
    record Frame(BuildMode mode, ClientModeState.Pending pending, List<BlockPos> anchors, @Nullable ModeTarget target,
                 ModeGeometry.Shape shape, ClientModeState.Stats stats, int symmetryRadius, Direction face) {}

    private static final int DRAWN_SYMMETRY_RADIUS = 32;
    private static final long APPLY_FLASH_MS = 650;
    private static final long CANCEL_FLASH_MS = 260;

    private static @Nullable AABB shown;           // smoothed selection box
    private static long lastFrameNanos;
    private static final Anim BOX_IN = new Anim(0F, 160, Ease.OUT_CUBIC);
    private static final Anim TOGGLE_IN = new Anim(0F, 320, Ease.OUT_CUBIC);
    private static @Nullable AABB flashBox;
    private static long flashAtMs;
    private static boolean flashSuccess;

    /** The selection was sent: flash its box in the success colour. */
    static void flashApplied(final @Nullable AABB box) {
        flash(box, true);
    }

    /** The selection was cleared: a quick danger-coloured fade of its box. */
    static void flashCancelled(final @Nullable AABB box) {
        flash(box, false);
    }

    /** The box as drawn this frame (smoothed), else {@code fallback}. */
    static @Nullable AABB currentBoxOr(final @Nullable AABB fallback) {
        return shown != null ? shown : fallback;
    }

    /** Forget the smoothed box (mode left / new selection), so the next box starts from where it is. */
    static void reset() {
        shown = null;
        BOX_IN.snap(0F);
        TOGGLE_IN.snap(0F);
    }

    /** Draws only the running flash (used when no mode is active any more). */
    static void drawFlashOnly() {
        drawFlash(Theme.current().palette());
    }

    static void draw(final Frame f) {
        final Palette pal = Theme.current().palette();
        final float dt = frameSeconds();
        drawFlash(pal);
        if (f.mode().kind() == ModeKind.TOGGLE) {
            TOGGLE_IN.set(1F);
            drawSymmetry(f, pal);
            return;
        }
        final ClientModeState.Pending pending = f.pending();
        final ModeTarget target = f.target();
        final boolean selecting = pending == ClientModeState.Pending.FIRST_ANCHOR || pending == ClientModeState.Pending.SELECTED
            || pending == ClientModeState.Pending.PREVIEW || pending == ClientModeState.Pending.DESTINATION;

        // Aim cursor: where the next click lands.
        if (target != null && pending != ClientModeState.Pending.SELECTED && pending != ClientModeState.Pending.PREVIEW
            && (!target.air() || ModeController.airAllowed(f.mode()))) {
            final int cursor = target.air() ? Colors.withAlpha(pal.text(), pulse(0x70, 0xC0)) : Colors.withAlpha(pal.accent(), 0xA0);
            OverlayRenderer.marker(target.pos(), cursor);
        }
        if (!selecting && !(pending == ClientModeState.Pending.NONE && f.shape().box() != null && f.mode().kind() == ModeKind.POINT)) {
            BOX_IN.set(0F);
            shown = null;
            return;
        }

        final AABB box = targetBox(f);
        if (box == null) return;
        BOX_IN.set(1F);
        shown = smooth(shown, box, dt);
        final float in = BOX_IN.get();
        final int colour = boxColour(f, pal);
        final boolean live = pending == ClientModeState.Pending.FIRST_ANCHOR || pending == ClientModeState.Pending.DESTINATION
            || pending == ClientModeState.Pending.NONE;
        final int alpha = (int) ((live ? 0xB8 : 0xFF) * in);

        final boolean moveTarget = f.mode() == BuildModes.MOVE && f.anchors().size() >= 2 && pending != ClientModeState.Pending.FIRST_ANCHOR;
        if (moveTarget) {
            // The source stays put and dim; the (smoothed) destination follows the crosshair, joined by a guide line.
            final AABB src = ModeGeometry.boxOf(f.anchors().get(0), f.anchors().get(1));
            OverlayRenderer.box(src, Colors.withAlpha(colour, (int) (0x68 * in)), false);
            OverlayRenderer.box(shown, Colors.withAlpha(colour, alpha), true);
            OverlayRenderer.line(src.getCenter(), shown.getCenter(), Colors.withAlpha(colour, (int) (0x90 * in)));
        } else {
            OverlayRenderer.box(shown, Colors.withAlpha(colour, alpha), true);
        }

        // Guides of round modes and lines.
        final List<BlockPos> anchors = f.anchors();
        if (anchors.size() >= 2 && (f.mode() == BuildModes.LINE || f.mode() == BuildModes.SPHERE || f.mode() == BuildModes.CYLINDER
            || f.mode().kind() == ModeKind.MEASURE)) {
            final Vec3 a = Vec3.atCenterOf(anchors.get(0));
            final Vec3 b = f.mode() == BuildModes.CYLINDER
                ? new Vec3(anchors.get(1).getX() + 0.5, a.y, anchors.get(1).getZ() + 0.5)
                : Vec3.atCenterOf(anchors.get(1));
            OverlayRenderer.line(a, b, Colors.withAlpha(colour, (int) (0xE0 * in)));
            if (f.mode().kind() == ModeKind.MEASURE && ClientModeState.settings().labels) {
                OverlayRenderer.label(a.add(b).scale(0.5).add(0, 0.45, 0),
                    Component.translatable("slate_building.stats.distance", String.format(Locale.ROOT, "%.1f", f.shape().distance())),
                    Colors.withAlpha(pal.text(), (int) (0xFF * in)));
            }
        }

        // Corner markers.
        if (!anchors.isEmpty() && !moveTarget && f.mode().kind() != ModeKind.POINT) {
            OverlayRenderer.marker(anchors.get(0), Colors.withAlpha(pal.accent(), (int) (0xFF * in)));
            if (anchors.size() >= 2) {
                final int second = live ? Colors.withAlpha(pal.text(), (int) (0xD0 * in)) : Colors.withAlpha(pal.accent(), (int) (0xFF * in));
                OverlayRenderer.marker(anchors.get(1), second);
            }
        } else if (f.mode().kind() == ModeKind.POINT && pending == ClientModeState.Pending.PREVIEW && !anchors.isEmpty()) {
            OverlayRenderer.marker(anchors.get(0), Colors.withAlpha(pal.accent(), (int) (0xFF * in)));
        }

        if (ClientModeState.settings().labels) {
            final Component label = label(f);
            if (label != null) {
                final int labelColour = f.stats().error() != null && !live ? pal.danger() : pal.text();
                OverlayRenderer.label(ModeGeometry.labelPoint(shown), label, Colors.withAlpha(labelColour, (int) (0xFF * in)));
            }
        }
    }

    // ---- pieces ----

    private static @Nullable AABB targetBox(final Frame f) {
        if (f.mode() == BuildModes.MOVE && f.pending() == ClientModeState.Pending.DESTINATION || f.mode() == BuildModes.MOVE && f.pending() == ClientModeState.Pending.SELECTED) {
            return moveDestination(f);
        }
        if (f.mode().kind() == ModeKind.POINT) {
            final AABB placed = ModePreview.placedBounds();
            if (placed != null) return placed;
        }
        return f.shape().box();
    }

    private static @Nullable AABB moveDestination(final Frame f) {
        final AABB planned = ModePreview.placedBounds();
        if (planned != null) return planned;
        return ModeGeometry.moveDestination(ClientModeState.params(f.mode()), f.anchors(), f.face());
    }

    private static int boxColour(final Frame f, final Palette pal) {
        final ClientModeState.Stats s = f.stats();
        if (s.error() != null && f.pending() != ClientModeState.Pending.FIRST_ANCHOR) return pal.danger();
        if (s.error() != null) return Colors.lerp(pal.accent(), pal.danger(), 0.6F);
        if (s.missing() > 0 || s.blocked() > 0) return pal.warning();
        if (f.mode().kind() == ModeKind.MEASURE) return pal.text();
        if (!f.mode().places() && f.mode().breaks()) return Colors.lerp(pal.accent(), pal.danger(), 0.35F);
        return pal.accent();
    }

    private static @Nullable Component label(final Frame f) {
        final ClientModeState.Stats s = f.stats();
        if (!s.hasBox()) return null;
        MutableComponent out = s.sizeText().copy();
        if (s.radius() > 0) {
            out = Component.empty().append(s.height() > 0
                ? Component.translatable("slate_building.stats.radius_height", s.radius(), s.height())
                : Component.translatable("slate_building.stats.radius", s.radius()))
                .append(Component.translatable("slate_building.stats.separator")).append(out);
        }
        if (f.mode().kind() == ModeKind.MEASURE) {
            return out.append(Component.translatable("slate_building.stats.separator"))
                .append(Component.translatable("slate_building.stats.volume", s.volume()));
        }
        if (s.error() != null && f.pending() != ClientModeState.Pending.FIRST_ANCHOR) {
            return out.append(Component.translatable("slate_building.stats.separator")).append(s.error());
        }
        if (s.planned() && s.blocks() > 0) {
            out.append(Component.translatable("slate_building.stats.separator")).append(Component.translatable("slate_building.stats.blocks", s.blocks()));
        }
        return out;
    }

    private static void drawSymmetry(final Frame f, final Palette pal) {
        if (f.anchors().isEmpty()) return;
        final BlockPos c = f.anchors().get(0);
        final float in = TOGGLE_IN.get();
        // The symmetry reaches symmetryRadius blocks; the guides stop at a readable size (the label says the rest).
        final int r = Math.max(4, Math.min(f.symmetryRadius(), DRAWN_SYMMETRY_RADIUS));
        final ModeParams params = ClientModeState.params(f.mode());
        final double cx = c.getX() + 0.5;
        final double cy = c.getY() + 0.5;
        final double cz = c.getZ() + 0.5;
        final int plane = Colors.withAlpha(pal.accent(), (int) (0xA0 * in));
        OverlayRenderer.marker(c, Colors.withAlpha(pal.accent(), (int) (0xFF * in)));
        final AABB bounds = new AABB(cx - r, cy - Math.min(r, 16), cz - r, cx + r, cy + Math.min(r, 32), cz + r);
        Component label;
        if (f.mode() == BuildModes.MIRROR_MODE) {
            final String axis = params.getChoice("axis");
            if (axis.contains("X")) OverlayRenderer.plane(Direction.Axis.X, cx, bounds, plane);
            if (axis.contains("Z")) OverlayRenderer.plane(Direction.Axis.Z, cz, bounds, plane);
            label = Component.empty().append(f.mode().name()).append(Component.translatable("slate_building.stats.separator"))
                .append(BuildModes.MIRROR_MODE.param("axis").optionName(axis));
        } else {
            final int slices = Math.max(2, params.getInt("slices"));
            final int spoke = Colors.withAlpha(pal.accent(), (int) (0xC0 * in));
            final double reach = r * Math.min(1.0, 0.25 + 0.75 * in);
            for (int k = 0; k < slices; k++) {
                final double ang = Math.PI * 2 * k / slices;
                OverlayRenderer.line(new Vec3(cx, cy, cz), new Vec3(cx + Math.sin(ang) * reach, cy, cz + Math.cos(ang) * reach), spoke);
            }
            final int ring = Colors.withAlpha(pal.accent(), (int) (0x70 * in));
            final int segments = Math.max(24, Math.min(96, r * 3));
            Vec3 prev = null;
            for (int k = 0; k <= segments; k++) {
                final double ang = Math.PI * 2 * k / segments;
                final Vec3 p = new Vec3(cx + Math.sin(ang) * reach, cy, cz + Math.cos(ang) * reach);
                if (prev != null) OverlayRenderer.line(prev, p, ring);
                prev = p;
            }
            OverlayRenderer.line(new Vec3(cx, cy - 3, cz), new Vec3(cx, cy + 12, cz), Colors.withAlpha(pal.accent(), (int) (0x90 * in)));
            label = Component.empty().append(f.mode().name()).append(Component.translatable("slate_building.stats.separator"))
                .append(Component.translatable("slate_building.stats.slices", slices));
        }
        if (ClientModeState.settings().labels) {
            final Component full = Component.empty().append(label).append(Component.translatable("slate_building.stats.separator"))
                .append(Component.translatable("slate_building.stats.radius", f.symmetryRadius()));
            OverlayRenderer.label(new Vec3(cx, cy + 1.1, cz), full, Colors.withAlpha(pal.text(), (int) (0xFF * in)));
        }
    }

    private static void drawFlash(final Palette pal) {
        if (flashBox == null) return;
        final long age = net.minecraft.Util.getMillis() - flashAtMs;
        final float motion = Theme.current().motion();
        final long life = (long) ((flashSuccess ? APPLY_FLASH_MS : CANCEL_FLASH_MS) * Math.max(0.5F, motion));
        if (motion <= 0 || age >= life) { flashBox = null; return; }
        final float t = age / (float) life;
        final float fade = 1F - Ease.OUT_CUBIC.apply(t);
        final double grow = flashSuccess ? 0.12 * Ease.OUT_CUBIC.apply(t) : -0.08 * t;
        OverlayRenderer.box(flashBox.inflate(grow), Colors.withAlpha(flashSuccess ? pal.success() : pal.danger(), (int) (0xFF * fade)), false);
    }

    private static void flash(final @Nullable AABB box, final boolean success) {
        if (box == null) return;
        flashBox = box;
        flashAtMs = net.minecraft.Util.getMillis();
        flashSuccess = success;
    }

    /** Eases the drawn box towards {@code target} (exponential, ~40 ms half-life at motion 1; snaps on big jumps). */
    private static AABB smooth(final @Nullable AABB from, final AABB target, final float dt) {
        final float motion = Theme.current().motion();
        if (from == null || motion <= 0 || far(from, target)) return target;
        final double k = 1.0 - Math.pow(0.5, dt / (0.04 * motion));
        final AABB next = new AABB(lerp(from.minX, target.minX, k), lerp(from.minY, target.minY, k), lerp(from.minZ, target.minZ, k),
            lerp(from.maxX, target.maxX, k), lerp(from.maxY, target.maxY, k), lerp(from.maxZ, target.maxZ, k));
        return close(next, target) ? target : next;
    }

    private static boolean far(final AABB a, final AABB b) {
        return Math.abs(a.minX - b.minX) + Math.abs(a.minY - b.minY) + Math.abs(a.minZ - b.minZ)
            + Math.abs(a.maxX - b.maxX) + Math.abs(a.maxY - b.maxY) + Math.abs(a.maxZ - b.maxZ) > 48;
    }

    private static boolean close(final AABB a, final AABB b) {
        return Math.abs(a.minX - b.minX) + Math.abs(a.minY - b.minY) + Math.abs(a.minZ - b.minZ)
            + Math.abs(a.maxX - b.maxX) + Math.abs(a.maxY - b.maxY) + Math.abs(a.maxZ - b.maxZ) < 0.003;
    }

    private static double lerp(final double a, final double b, final double t) {
        return a + (b - a) * t;
    }

    private static int pulse(final int lo, final int hi) {
        if (Theme.current().motion() <= 0) return hi;
        final double s = 0.5 + 0.5 * Math.sin(net.minecraft.Util.getMillis() / 180.0);
        return (int) (lo + (hi - lo) * s);
    }

    private static float frameSeconds() {
        final long now = System.nanoTime();
        final float dt = lastFrameNanos == 0 ? 0.016F : Math.min(0.1F, (now - lastFrameNanos) / 1.0E9F);
        lastFrameNanos = now;
        return dt;
    }

    private ModeOverlay() {}
}
