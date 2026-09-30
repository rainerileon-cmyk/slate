package dev.fallingcloud.slate.core.stage;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.stage.mesh.StageModels;
import dev.fallingcloud.slate.core.stage.scene.SceneLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Resource-reload awareness for the stage toolkit. Registered lazily on the client's resource manager (a plain
 * vanilla API, so no loader glue); on reload it bumps a generation counter that block meshes check (atlas UVs may
 * have moved), and drops the baked-model and scene caches.
 */
public final class StageResources implements ResourceManagerReloadListener {

    private static volatile int generation;
    private static boolean registered;

    /** Idempotent; call on the render thread once a stage exists. */
    public static synchronized void ensureRegistered() {
        if (registered) return;
        final ResourceManager rm = Minecraft.getInstance().getResourceManager();
        if (rm instanceof ReloadableResourceManager reloadable) {
            reloadable.registerReloadListener(new StageResources());
            registered = true;
        } else {
            Slate.LOGGER.warn("[Slate] stage: resource manager is not reloadable; stage caches will not follow resource reloads");
            registered = true;
        }
    }

    /** Bumped on every resource reload; caches compare against it. */
    public static int generation() { return generation; }

    @Override
    public void onResourceManagerReload(final ResourceManager manager) {
        generation++;
        StageModels.clear();
        SceneLoader.clear();
        StagePost.reload();
        StageSoft.reload();
        Slate.LOGGER.debug("[Slate] stage: resources reloaded (generation {})", generation);
    }
}
