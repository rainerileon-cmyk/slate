package dev.fallingcloud.slate.building.client.render;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.jetbrains.annotations.Nullable;

/**
 * Soft integrations of the renderers, all reflective and all failing closed (a missing or changed mod only turns the
 * integration off, logged once):
 * <ul>
 *   <li><b>Iris</b> ({@code net.irisshaders.iris.api.v0.IrisApi}): while a shader pack is active our core shader
 *       would bypass the pack's pipeline, so ghosts fall back to the vanilla translucent entity path (alpha only, no
 *       desaturation); nothing is drawn during the shadow pass.</li>
 *   <li><b>BridgingMod</b> ({@code me.cg360.mod.bridging.raytrace.BridgingStateTracker}): while its bridge assist
 *       targets a block (it only does when the crosshair misses), it draws its own preview, so ours stays out.</li>
 * </ul>
 */
public final class RenderCompat {

    private static @Nullable MethodHandle irisInstance;
    private static @Nullable MethodHandle irisPackInUse;
    private static @Nullable MethodHandle irisShadowPass;
    private static @Nullable MethodHandle bridgingTarget;
    private static boolean resolved;

    /** Whether an Iris shader pack is rendering this frame. */
    public static boolean shaderPackInUse() {
        resolve();
        if (irisInstance == null || irisPackInUse == null) return false;
        try {
            return (boolean) irisPackInUse.invoke(irisInstance.invoke());
        } catch (final Throwable t) {
            irisInstance = null;
            SlateBuilding.LOGGER.warn("[Slate Building] Iris API call failed; treating shader packs as off", t);
            return false;
        }
    }

    /** Whether Iris is rendering its shadow pass right now (nothing of ours belongs in the shadow map). */
    public static boolean shadowPass() {
        resolve();
        if (irisInstance == null || irisShadowPass == null) return false;
        try {
            return (boolean) irisShadowPass.invoke(irisInstance.invoke());
        } catch (final Throwable t) {
            irisShadowPass = null;
            return false;
        }
    }

    /** Whether BridgingMod's bridge assist is showing its own placement target. */
    public static boolean bridgingAssistActive() {
        resolve();
        if (bridgingTarget == null) return false;
        try {
            return bridgingTarget.invoke() != null;
        } catch (final Throwable t) {
            bridgingTarget = null;
            return false;
        }
    }

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        if (loaded("iris") || loaded("oculus")) {
            try {
                final Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisInstance = lookup.findStatic(api, "getInstance", MethodType.methodType(api));
                irisPackInUse = lookup.findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class));
                irisShadowPass = lookup.findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class));
            } catch (final ReflectiveOperationException | LinkageError e) {
                irisInstance = null;
                SlateBuilding.LOGGER.warn("[Slate Building] Iris is installed but its API was not found; ghosts will not adapt to shader packs");
            }
        }
        if (loaded("bridgingmod")) {
            try {
                final Class<?> tracker = Class.forName("me.cg360.mod.bridging.raytrace.BridgingStateTracker");
                bridgingTarget = lookup.findStatic(tracker, "getLastTickTarget", MethodType.methodType(net.minecraft.util.Tuple.class));
            } catch (final ReflectiveOperationException | LinkageError e) {
                SlateBuilding.LOGGER.info("[Slate Building] BridgingMod found without its state tracker; the placement ghost ignores it");
            }
        }
    }

    private static boolean loaded(final String modId) {
        try {
            return SlatePlatform.get().isModLoaded(modId);
        } catch (final RuntimeException e) {
            return false;
        }
    }

    private RenderCompat() {}
}
