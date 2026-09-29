package dev.fallingcloud.slate.core.stage.anim;

import dev.fallingcloud.slate.core.gfx.Ease;
import java.util.Locale;

/**
 * One segment of a camera move: samples a {@link CameraPose} for an eased progress {@code t} in 0..1 and knows how
 * long it takes. Segments chain in a {@link Timeline}. Implementations: {@link LinePath} (straight lerp),
 * {@link SplinePath} (Catmull-Rom through key poses), {@link OrbitPath} (circle around a point), {@link HoldPath}
 * (stay put).
 *
 * <h2>JSON form (scene files, {@code "camera": {"intro": [...]}})</h2>
 * Each segment is an object with {@code "type"}, {@code "duration"} (ms) and {@code "ease"} (one of
 * {@code linear, outCubic, inOutCubic, outQuint, outBack, outExpo, spring}; default {@code outCubic}):
 * <pre>
 * { "type": "line",   "duration": 1800, "ease": "outCubic", "from": POSE, "to": POSE }     // "from" optional: current pose
 * { "type": "spline", "duration": 2600, "ease": "inOutCubic", "points": [POSE, POSE, POSE] }
 * { "type": "orbit",  "duration": 3000, "ease": "linear", "center": [x,y,z], "radius": 6, "height": 2.5,
 *                     "from": 0, "to": 90, "fov": 40, "targetHeight": 1 }                    // angles in degrees
 * { "type": "hold",   "duration": 400 }                                                      // optional "pose"
 * </pre>
 * where {@code POSE} is {@code {"position": [x,y,z], "target": [x,y,z], "fov": 45, "roll": 0}} ({@code fov} and
 * {@code roll} optional). Dolly's shot format could not be checked in this environment; the loader is
 * {@code scene.SceneLoader#parsePath}.
 */
public interface CameraPath {

    /** Writes the pose at eased progress {@code t} (0..1) into {@code out}. */
    void sample(float t, CameraPose out);

    float durationMs();

    Ease ease();

    /** Called once when the segment starts playing, with the camera's current pose (lets "from" default to it). */
    default void begin(final CameraPose current) {}

    /** Parses an ease name as used by scene JSON ({@code outCubic}, {@code linear}, ...). Unknown = OUT_CUBIC. */
    static Ease parseEase(final String name) {
        if (name == null) return Ease.OUT_CUBIC;
        return switch (name.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "")) {
            case "linear" -> Ease.LINEAR;
            case "inoutcubic" -> Ease.IN_OUT_CUBIC;
            case "outquint" -> Ease.OUT_QUINT;
            case "outback" -> Ease.OUT_BACK;
            case "outexpo" -> Ease.OUT_EXPO;
            case "spring" -> Ease.SPRING;
            default -> Ease.OUT_CUBIC;
        };
    }
}
