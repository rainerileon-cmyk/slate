package dev.fallingcloud.slate.core.stage.anim;

import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * The slow ambient movement a camera does once its intro has finished: a gentle sway around the target, or a
 * continuous orbit, with an optional vertical bob. Applied on top of the rest pose every frame; it never changes the
 * rest pose itself. Off entirely when {@code Theme.motion() == 0}.
 *
 * <p>JSON ({@code "camera": {"idle": ...}}): {@code {"type": "sway", "period": 12000, "yaw": 3, "pitch": 1, "bob": 0.05}}
 * or {@code {"type": "orbit", "period": 30000, "bob": 0}} or {@code {"type": "none"}}. Angles in degrees, period in ms.</p>
 */
public final class IdleMotion {

    public enum Kind { NONE, SWAY, ORBIT }

    public static final IdleMotion NONE = new IdleMotion(Kind.NONE, 0f, 0f, 0f, 0f);

    private final Kind kind;
    private final float periodMs, yawDeg, pitchDeg, bob;
    private final Vector3f d = new Vector3f();

    private IdleMotion(final Kind kind, final float periodMs, final float yawDeg, final float pitchDeg, final float bob) {
        this.kind = kind;
        this.periodMs = periodMs;
        this.yawDeg = yawDeg;
        this.pitchDeg = pitchDeg;
        this.bob = bob;
    }

    public static IdleMotion sway(final float periodMs, final float yawDeg, final float pitchDeg, final float bob) {
        return new IdleMotion(Kind.SWAY, periodMs, yawDeg, pitchDeg, bob);
    }

    public static IdleMotion orbit(final float periodMs, final float bob) {
        return new IdleMotion(Kind.ORBIT, periodMs, 0f, 0f, bob);
    }

    public Kind kind() { return kind; }

    /** Writes {@code base} plus the motion at {@code timeMs} into {@code out}. */
    public void apply(final CameraPose base, final float timeMs, final CameraPose out) {
        out.set(base);
        if (kind == Kind.NONE || periodMs <= 0f || Theme.current().motion() <= 0f) return;
        final float phase = timeMs / periodMs * Mth.TWO_PI;
        final float yaw = kind == Kind.ORBIT ? phase : (float) Math.toRadians(yawDeg) * Mth.sin(phase);
        final float pitch = kind == Kind.SWAY ? (float) Math.toRadians(pitchDeg) * Mth.sin(phase * 0.71f + 1.3f) : 0f;
        d.set(base.position).sub(base.target);
        final float r = d.length();
        if (r > 1e-4f) {
            float azimuth = (float) Math.atan2(d.x, d.z) + yaw;
            float elevation = (float) Math.asin(Mth.clamp(d.y / r, -1f, 1f)) + pitch;
            elevation = Mth.clamp(elevation, -1.5f, 1.5f);
            final float horiz = r * Mth.cos(elevation);
            out.position.set(base.target.x + Mth.sin(azimuth) * horiz, base.target.y + r * Mth.sin(elevation), base.target.z + Mth.cos(azimuth) * horiz);
        }
        if (bob != 0f) {
            final float b = bob * Mth.sin(phase * 1.37f);
            out.position.y += b;
            out.target.y += b * 0.5f;
        }
    }
}
