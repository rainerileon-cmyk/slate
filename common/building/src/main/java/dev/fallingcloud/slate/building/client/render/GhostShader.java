package dev.fallingcloud.slate.building.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.fallingcloud.slate.building.SlateBuilding;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code slate_building:ghost} core shader (terrain vertex format): desaturation toward luma, opacity, the
 * replace / remove tints (remove adds drifting hatching) and a soft rim toward grazing angles. Registered by the
 * loader glue (NeoForge {@code RegisterShadersEvent}, Fabric {@code CoreShaderRegistrationCallback}), which hands
 * the loaded instance to {@link #set} on every resource reload.
 *
 * <p>Uniforms besides vanilla's defaults: {@code GhostOffset} (vec3, draw origin minus camera, like the chunk
 * renderer's {@code ChunkOffset}), {@code GhostParams} (opacity, saturation, time in seconds, rim strength),
 * {@code GhostAccent}, {@code GhostReplace}, {@code GhostRemove} (rgb + tint amount).
 */
public final class GhostShader {

    /** Shader id; the program lives in {@code assets/slate_building/shaders/core/ghost.{json,vsh,fsh}}. */
    public static final ResourceLocation ID = SlateBuilding.id("ghost");
    /** The vertex format the program is compiled for. */
    public static final VertexFormat FORMAT = DefaultVertexFormat.BLOCK;

    private static @Nullable ShaderInstance instance;
    private static boolean warned;

    /** Loader glue: the freshly (re)loaded program. */
    public static void set(final ShaderInstance shader) {
        instance = shader;
        warned = false;
    }

    /** The program, or null when it failed to load (ghosts then use the vanilla fallback path). */
    static @Nullable ShaderInstance get() {
        if (instance == null && !warned) {
            warned = true;
            SlateBuilding.LOGGER.warn("[Slate Building] ghost shader not loaded; ghosts use the vanilla translucent fallback");
        }
        return instance;
    }

    private GhostShader() {}
}
