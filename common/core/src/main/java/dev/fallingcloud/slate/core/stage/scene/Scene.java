package dev.fallingcloud.slate.core.stage.scene;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.stage.Stage;
import dev.fallingcloud.slate.core.stage.StageCamera;
import dev.fallingcloud.slate.core.stage.anim.CameraPath;
import dev.fallingcloud.slate.core.stage.anim.CameraPose;
import dev.fallingcloud.slate.core.stage.anim.IdleMotion;
import dev.fallingcloud.slate.core.stage.node.StructureNode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A loaded scene: what {@code assets/<ns>/slate/scenes/<id>.json} (or its {@code config/slate/scenes} override)
 * describes, ready to apply to a {@link Stage}. Holds the background, the structure template, the camera (rest pose,
 * intro timeline, idle motion), the light and the named {@link Anchor}s. See {@link SceneLoader} for the file format.
 */
public final class Scene {

    /** Solid colour when {@code top == bottom}; {@code transparent} lets the GUI show through. */
    public record Background(int top, int bottom, boolean transparent) {}

    public record Light(Vector3f sun, float ambient, int blockLight, int skyLight, float brightness,
                        boolean fog, float fogStart, float fogEnd, int fogColor) {}

    public record CameraConfig(CameraPose rest, List<CameraPath> intro, IdleMotion idle, float near, float far) {}

    public record StructureRef(@Nullable CompoundTag template, Vector3f offset, boolean centered, boolean hideAnchorBlocks, Set<BlockPos> hidden) {}

    private final ResourceLocation id;
    private final Background background;
    @Nullable private final StructureRef structure;
    private final CameraConfig camera;
    private final Light light;
    private final Map<String, Anchor> anchors;
    private final float resolution;
    private final Set<String> warned = new HashSet<>();

    Scene(final ResourceLocation id, final Background background, @Nullable final StructureRef structure, final CameraConfig camera,
          final Light light, final Map<String, Anchor> anchors, final float resolution) {
        this.id = id;
        this.background = background;
        this.structure = structure;
        this.camera = camera;
        this.light = light;
        this.anchors = Map.copyOf(anchors);
        this.resolution = resolution;
    }

    public ResourceLocation id() { return id; }

    public Background background() { return background; }

    @Nullable public StructureRef structure() { return structure; }

    public CameraConfig camera() { return camera; }

    public Light light() { return light; }

    public Map<String, Anchor> anchors() { return anchors; }

    public float resolution() { return resolution; }

    public Optional<Anchor> anchor(final String name) {
        return Optional.ofNullable(anchors.get(name));
    }

    /** The named anchor, or a fallback at the given place (logged once per scene and name) so callers never block. */
    public Anchor anchorOr(final String name, final float x, final float y, final float z, final float yaw) {
        final Anchor a = anchors.get(name);
        if (a != null) return a;
        if (warned.add(name)) Slate.LOGGER.warn("[Slate] scene {} has no anchor '{}', using the fallback at {},{},{}", id, name, x, y, z);
        return new Anchor(name, x, y, z, yaw);
    }

    /**
     * Applies background, light, fog, resolution and camera (rest pose, intro started, idle motion) to the stage and
     * adds the structure node when the scene has one. Returns that node (null without a structure).
     */
    @Nullable
    public StructureNode apply(final Stage stage) {
        if (background.transparent) stage.background(0);
        else if (background.top == background.bottom) stage.background(background.top);
        else stage.gradient(background.top, background.bottom);
        stage.lighting().sun(light.sun.x, light.sun.y, light.sun.z).ambient(light.ambient).light(light.blockLight, light.skyLight).brightness(light.brightness);
        stage.fog(light.fog, light.fogStart, light.fogEnd, light.fogColor);
        stage.resolutionScale(resolution);
        final StageCamera cam = stage.camera();
        cam.pose().set(camera.rest);
        cam.clip(camera.near, camera.far);
        cam.idle(camera.idle);
        cam.timeline().clear();
        for (final CameraPath p : camera.intro) cam.timeline().add(p);
        if (!camera.intro.isEmpty()) cam.timeline().play();
        StructureNode node = null;
        if (structure != null && structure.template != null) {
            node = stage.add(new StructureNode(stage.level(), structure.template, structure.hideAnchorBlocks ? structure.hidden : null));
            if (structure.centered) node.centered();
            node.at(structure.offset.x, structure.offset.y, structure.offset.z);
            node.named("scene:" + id.getPath());
        }
        return node;
    }
}
