package dev.fallingcloud.slate.core.stage.scene;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.core.stage.anim.CameraPath;
import dev.fallingcloud.slate.core.stage.anim.CameraPose;
import dev.fallingcloud.slate.core.stage.anim.HoldPath;
import dev.fallingcloud.slate.core.stage.anim.IdleMotion;
import dev.fallingcloud.slate.core.stage.anim.LinePath;
import dev.fallingcloud.slate.core.stage.anim.OrbitPath;
import dev.fallingcloud.slate.core.stage.anim.SplinePath;
import dev.fallingcloud.slate.core.stage.node.StructureNode;
import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Loads {@link Scene}s. A scene {@code ns:path} lives at {@code assets/<ns>/slate/scenes/<path>.json} with an optional
 * {@code <path>.nbt} next to it; {@code config/slate/scenes/<ns>.<path>.json} (and {@code .nbt}) override both, for
 * modpacks and for scenes captured in-game. Scenes are cached until the next resource reload or {@link #clear()}.
 *
 * <h2>Format</h2>
 * <pre>
 * {
 *   "background": "#161615" | "transparent" | { "top": "#0B0E14", "bottom": "#1C2230" },
 *   "structure":  { "file": "stage_test", "offset": [0, 0, 0], "centered": true,
 *                   "hideAnchorBlocks": true, "hide": [[x, y, z], ...] },     // file: name of the .nbt beside the json
 *   "camera":     { "fov": 45, "near": 0.05, "far": 256, "pose": POSE, "intro": [ SEGMENT, ... ], "idle": IDLE },
 *   "light":      { "sun": [0.35, 1, 0.55], "ambient": 0.45, "blockLight": 15, "skyLight": 15, "brightness": 1,
 *                   "fog": { "start": 12, "end": 40, "color": "#161615" } },
 *   "resolution": 1.0,
 *   "anchors":    { "chest_play": { "pos": [1.5, 1, 2.5], "rot": [180, 0, 0] } }   // rot = [yaw, pitch, roll] degrees
 * }
 * </pre>
 * {@code POSE} is {@code {"position": [x,y,z], "target": [x,y,z], "fov": 45, "roll": 0}}; {@code SEGMENT} and
 * {@code IDLE} are documented on {@link CameraPath} and {@link IdleMotion}. Every key is optional.
 */
public final class SceneLoader {

    private static final Map<ResourceLocation, Scene> CACHE = new HashMap<>();

    public static synchronized Scene load(final ResourceLocation id) {
        Scene s = CACHE.get(id);
        if (s == null) {
            s = read(id);
            CACHE.put(id, s);
        }
        return s;
    }

    public static synchronized void clear() {
        CACHE.clear();
    }

    /** {@code config/slate/scenes}. */
    public static Path configDir() {
        return SlatePlatform.get().configDir().resolve("slate").resolve("scenes");
    }

    /** The config override file name for a scene id: {@code <ns>.<path with / as .>.<ext>}. */
    public static String configName(final ResourceLocation id, final String ext) {
        return id.getNamespace() + "." + id.getPath().replace('/', '.') + "." + ext;
    }

    private static Scene read(final ResourceLocation id) {
        JsonObject json = null;
        final Path override = configDir().resolve(configName(id, "json"));
        if (Files.isRegularFile(override)) {
            try (BufferedReader r = Files.newBufferedReader(override)) {
                json = JsonParser.parseReader(r).getAsJsonObject();
                Slate.LOGGER.info("[Slate] scene {} from {}", id, override);
            } catch (final Exception e) {
                Slate.LOGGER.warn("[Slate] scene override {} unreadable: {}", override, e.toString());
            }
        }
        if (json == null) {
            final ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "slate/scenes/" + id.getPath() + ".json");
            try {
                final Resource res = Minecraft.getInstance().getResourceManager().getResource(file).orElse(null);
                if (res != null) {
                    try (BufferedReader r = res.openAsReader()) {
                        json = JsonParser.parseReader(r).getAsJsonObject();
                    }
                }
            } catch (final Exception e) {
                Slate.LOGGER.warn("[Slate] scene {} unreadable: {}", file, e.toString());
            }
        }
        if (json == null) {
            Slate.LOGGER.warn("[Slate] scene {} not found; using an empty scene", id);
            json = new JsonObject();
        }
        return parse(id, json);
    }

    // ------------------------------------------------------------------ parsing

    static Scene parse(final ResourceLocation id, final JsonObject json) {
        final Scene.Background background = parseBackground(json.get("background"));
        final Scene.StructureRef structure = parseStructure(id, json.has("structure") && json.get("structure").isJsonObject() ? json.getAsJsonObject("structure") : null);
        final Scene.CameraConfig camera = parseCamera(json.has("camera") && json.get("camera").isJsonObject() ? json.getAsJsonObject("camera") : new JsonObject());
        final Scene.Light light = parseLight(json.has("light") && json.get("light").isJsonObject() ? json.getAsJsonObject("light") : new JsonObject());
        final Map<String, Anchor> anchors = new HashMap<>();
        if (json.has("anchors") && json.get("anchors").isJsonObject()) {
            for (final Map.Entry<String, JsonElement> e : json.getAsJsonObject("anchors").entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                final JsonObject a = e.getValue().getAsJsonObject();
                final Vector3f pos = vec3(a.get("pos"), new Vector3f());
                float yaw = GsonHelper.getAsFloat(a, "yaw", 0f), pitch = 0f, roll = 0f;
                if (a.has("rot") && a.get("rot").isJsonArray()) {
                    final JsonArray r = a.getAsJsonArray("rot");
                    if (r.size() > 0) yaw = r.get(0).getAsFloat();
                    if (r.size() > 1) pitch = r.get(1).getAsFloat();
                    if (r.size() > 2) roll = r.get(2).getAsFloat();
                }
                anchors.put(e.getKey(), new Anchor(e.getKey(), pos, yaw, pitch, roll));
            }
        }
        final float resolution = GsonHelper.getAsFloat(json, "resolution", 1f);
        return new Scene(id, background, structure, camera, light, anchors, resolution);
    }

    private static Scene.Background parseBackground(@Nullable final JsonElement el) {
        if (el == null || el.isJsonNull()) return new Scene.Background(0xFF161615, 0xFF161615, false);
        if (el.isJsonPrimitive()) {
            if (el.getAsJsonPrimitive().isString() && "transparent".equalsIgnoreCase(el.getAsString())) return new Scene.Background(0, 0, true);
            final int c = color(el, 0xFF161615);
            return new Scene.Background(c, c, false);
        }
        final JsonObject o = el.getAsJsonObject();
        final int top = color(o.get("top"), 0xFF10141C), bottom = color(o.get("bottom"), 0xFF232833);
        return new Scene.Background(top, bottom, false);
    }

    @Nullable
    private static Scene.StructureRef parseStructure(final ResourceLocation id, @Nullable final JsonObject o) {
        if (o == null) return null;
        final String file = GsonHelper.getAsString(o, "file", id.getPath());
        CompoundTag nbt = StructureNode.readFile(configDir().resolve(id.getNamespace() + "." + file.replace('/', '.') + ".nbt"));
        if (nbt == null) nbt = StructureNode.readResource(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "slate/scenes/" + file + ".nbt"));
        if (nbt == null) Slate.LOGGER.warn("[Slate] scene {}: structure '{}' not found", id, file);
        final Set<BlockPos> hidden = new HashSet<>();
        if (o.has("hide") && o.get("hide").isJsonArray()) {
            for (final JsonElement e : o.getAsJsonArray("hide")) {
                if (!e.isJsonArray() || e.getAsJsonArray().size() < 3) continue;
                final JsonArray a = e.getAsJsonArray();
                hidden.add(new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()));
            }
        }
        return new Scene.StructureRef(nbt, vec3(o.get("offset"), new Vector3f()), GsonHelper.getAsBoolean(o, "centered", false),
            GsonHelper.getAsBoolean(o, "hideAnchorBlocks", true), hidden);
    }

    private static Scene.CameraConfig parseCamera(final JsonObject o) {
        final float fov = GsonHelper.getAsFloat(o, "fov", 45f);
        final CameraPose rest = new CameraPose(0f, 2f, 6f, 0f, 1f, 0f, fov, 0f);
        if (o.has("pose") && o.get("pose").isJsonObject()) pose(o.getAsJsonObject("pose"), rest, rest);
        final List<CameraPath> intro = new ArrayList<>();
        if (o.has("intro") && o.get("intro").isJsonArray()) {
            for (final JsonElement e : o.getAsJsonArray("intro")) {
                if (!e.isJsonObject()) continue;
                final CameraPath p = parsePath(e.getAsJsonObject(), rest);
                if (p != null) intro.add(p);
            }
        }
        IdleMotion idle = IdleMotion.NONE;
        if (o.has("idle") && o.get("idle").isJsonObject()) {
            final JsonObject i = o.getAsJsonObject("idle");
            final String type = GsonHelper.getAsString(i, "type", "sway").toLowerCase(Locale.ROOT);
            final float period = GsonHelper.getAsFloat(i, "period", 12000f), bob = GsonHelper.getAsFloat(i, "bob", 0f);
            idle = switch (type) {
                case "orbit" -> IdleMotion.orbit(period, bob);
                case "none" -> IdleMotion.NONE;
                default -> IdleMotion.sway(period, GsonHelper.getAsFloat(i, "yaw", 3f), GsonHelper.getAsFloat(i, "pitch", 1f), bob);
            };
        }
        return new Scene.CameraConfig(rest, intro, idle, GsonHelper.getAsFloat(o, "near", 0.05f), GsonHelper.getAsFloat(o, "far", 256f));
    }

    /** One intro segment; {@code defaults} supplies fov/roll for poses that omit them. See {@link CameraPath}. */
    @Nullable
    public static CameraPath parsePath(final JsonObject o, final CameraPose defaults) {
        final String type = GsonHelper.getAsString(o, "type", "line").toLowerCase(Locale.ROOT);
        final float duration = GsonHelper.getAsFloat(o, "duration", 1000f);
        final Ease ease = CameraPath.parseEase(GsonHelper.getAsString(o, "ease", "outCubic"));
        switch (type) {
            case "hold" -> {
                return new HoldPath(o.has("pose") && o.get("pose").isJsonObject() ? pose(o.getAsJsonObject("pose"), defaults, new CameraPose()) : null, duration);
            }
            case "line" -> {
                if (!o.has("to") || !o.get("to").isJsonObject()) return null;
                final CameraPose to = pose(o.getAsJsonObject("to"), defaults, new CameraPose());
                final CameraPose from = o.has("from") && o.get("from").isJsonObject() ? pose(o.getAsJsonObject("from"), defaults, new CameraPose()) : null;
                return new LinePath(from, to, duration, ease);
            }
            case "spline" -> {
                final List<CameraPose> points = new ArrayList<>();
                if (o.has("points") && o.get("points").isJsonArray()) {
                    for (final JsonElement e : o.getAsJsonArray("points")) if (e.isJsonObject()) points.add(pose(e.getAsJsonObject(), defaults, new CameraPose()));
                }
                if (points.isEmpty()) return null;
                return new SplinePath(points, duration, ease);
            }
            case "orbit" -> {
                return new OrbitPath(vec3(o.get("center"), new Vector3f()), GsonHelper.getAsFloat(o, "radius", 6f), GsonHelper.getAsFloat(o, "height", 2f),
                    GsonHelper.getAsFloat(o, "from", 0f), GsonHelper.getAsFloat(o, "to", 90f), GsonHelper.getAsFloat(o, "fov", defaults.fov),
                    GsonHelper.getAsFloat(o, "targetHeight", 1f), duration, ease);
            }
            default -> {
                Slate.LOGGER.warn("[Slate] scene: unknown camera path type '{}'", type);
                return null;
            }
        }
    }

    private static Scene.Light parseLight(final JsonObject o) {
        final Vector3f sun = vec3(o.get("sun"), new Vector3f(0.35f, 1f, 0.55f));
        boolean fog = false;
        float fogStart = 12f, fogEnd = 40f;
        int fogColor = 0xFF161615;
        if (o.has("fog") && o.get("fog").isJsonObject()) {
            final JsonObject f = o.getAsJsonObject("fog");
            fog = GsonHelper.getAsBoolean(f, "enabled", true);
            fogStart = GsonHelper.getAsFloat(f, "start", fogStart);
            fogEnd = GsonHelper.getAsFloat(f, "end", fogEnd);
            fogColor = color(f.get("color"), fogColor);
        }
        return new Scene.Light(sun, GsonHelper.getAsFloat(o, "ambient", 0.45f), GsonHelper.getAsInt(o, "blockLight", 15),
            GsonHelper.getAsInt(o, "skyLight", 15), GsonHelper.getAsFloat(o, "brightness", 1f), fog, fogStart, fogEnd, fogColor);
    }

    // ------------------------------------------------------------------ primitives

    static CameraPose pose(final JsonObject o, final CameraPose defaults, final CameraPose out) {
        final Vector3f p = vec3(o.get("position"), new Vector3f(defaults.position));
        final Vector3f t = vec3(o.get("target"), new Vector3f(defaults.target));
        out.set(p.x, p.y, p.z, t.x, t.y, t.z, GsonHelper.getAsFloat(o, "fov", defaults.fov), GsonHelper.getAsFloat(o, "roll", defaults.roll));
        return out;
    }

    static Vector3f vec3(@Nullable final JsonElement el, final Vector3f fallback) {
        if (el == null || !el.isJsonArray() || el.getAsJsonArray().size() < 3) return fallback;
        final JsonArray a = el.getAsJsonArray();
        return fallback.set(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    /** {@code "#RRGGBB"}, {@code "#AARRGGBB"} or a number; ARGB out. */
    static int color(@Nullable final JsonElement el, final int fallback) {
        if (el == null || el.isJsonNull()) return fallback;
        try {
            if (el.getAsJsonPrimitive().isNumber()) return el.getAsInt();
            String s = el.getAsString().trim();
            if (s.startsWith("#")) s = s.substring(1);
            if (s.length() == 6) return 0xFF000000 | (int) Long.parseLong(s, 16);
            if (s.length() == 8) return (int) Long.parseLong(s, 16);
        } catch (final Exception ignored) {}
        return fallback;
    }

    private SceneLoader() {}
}
