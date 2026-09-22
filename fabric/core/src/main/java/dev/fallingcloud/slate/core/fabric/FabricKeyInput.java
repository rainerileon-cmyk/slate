package dev.fallingcloud.slate.core.fabric;

import dev.fallingcloud.slate.core.event.SlateEvents;
import net.minecraft.client.Minecraft;

/**
 * Fabric has no raw key-press event for the no-screen case, so KEY_PRESSED listeners are given a
 * chance from the client tick via a small GLFW poll: listeners that use KeyMappings work through
 * {@code consumeClick()} in their own tick handlers anyway; this only serves listeners that want raw
 * keys. Kept minimal: it reports the keys of registered Slate key mappings when they are pressed.
 */
final class FabricKeyInput {

    private static final java.util.Map<Integer, Boolean> LAST = new java.util.HashMap<>();

    static void tick(final Minecraft mc) {
        if (mc.screen != null || mc.getWindow() == null) return;
        final long window = mc.getWindow().getWindow();
        for (final int key : new int[] { 290, 291, 292, 293, 294, 295, 296, 297, 298, 299, 300, 301 }) { // F1..F12
            final boolean down = org.lwjgl.glfw.GLFW.glfwGetKey(window, key) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            final boolean was = LAST.getOrDefault(key, false);
            if (down && !was) {
                SlateEvents.KEY_PRESSED.invokeUntilConsumed(l -> l.onKey(key, org.lwjgl.glfw.GLFW.glfwGetKeyScancode(key), 0));
            }
            LAST.put(key, down);
        }
    }

    private FabricKeyInput() {}
}
