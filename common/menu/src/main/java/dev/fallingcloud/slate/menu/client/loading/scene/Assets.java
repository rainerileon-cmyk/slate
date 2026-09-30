package dev.fallingcloud.slate.menu.client.loading.scene;

/**
 * Where the loading scene reads the models and textures of a mod that is installed: the mod's own jar, opened where
 * it lies. Nothing of another mod is kept in Slate; what the scene shows of Create is what the player's Create brings.
 */
@FunctionalInterface
public interface Assets {

    /**
     * A file of a mod's (or the game's) assets.
     *
     * @param namespace {@code create}, {@code minecraft}
     * @param path      the path under {@code assets/<namespace>/}, as {@code textures/block/belt.png}
     * @return its bytes, or null when there is no such file (or no such mod)
     */
    byte[] read(String namespace, String path);
}
