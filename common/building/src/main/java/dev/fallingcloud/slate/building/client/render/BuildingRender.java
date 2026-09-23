package dev.fallingcloud.slate.building.client.render;

/**
 * Client init of rendering: ghost + overlay renderers on {@code SlateRenderEvents.AFTER_TRANSLUCENT}, the hand preview, the quad baker cache. Loader-specific model/colour/shader wiring goes in the loader render glue. Called from {@code BuildingClient.init()}.
 *
 * <p>Owner: B (render). Skeleton stub (empty); called in a fixed order by the skeleton, so owners never edit the caller.
 */
public final class BuildingRender {

    public static void init() {
    }

    private BuildingRender() {}
}
