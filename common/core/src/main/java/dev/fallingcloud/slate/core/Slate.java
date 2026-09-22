package dev.fallingcloud.slate.core;

import dev.fallingcloud.slate.core.config.CoreConfig;
import dev.fallingcloud.slate.core.config.JsonConfig;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Slate core bootstrap. Loader entry points call {@link #init()} (both sides) and, on the client,
 * {@link dev.fallingcloud.slate.core.client.SlateClient#init()}; every module calls {@link #init()} first
 * thing in its own init so the order in which the loader constructs mods does not matter.
 */
public final class Slate {

    public static final String MOD_ID = "slate";
    public static final Logger LOGGER = LoggerFactory.getLogger("Slate");

    private static volatile boolean initialised;
    private static JsonConfig<CoreConfig> config;

    /** Idempotent. Loads the core config and platform services. Safe to call from any module's init. */
    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        config = JsonConfig.of("core", CoreConfig.class, CoreConfig::new);
        dev.fallingcloud.slate.core.net.blob.BlobChannel.register();
        LOGGER.info("[Slate] core {} on {}", version(), SlatePlatform.get().loader());
    }

    public static CoreConfig config() {
        init();
        return config.get();
    }

    /** The live config wrapper, for saving after edits ({@code Slate.configFile().save()}). */
    public static JsonConfig<CoreConfig> configFile() {
        init();
        return config;
    }

    public static String version() {
        return SlatePlatform.get().modInfo(MOD_ID).map(i -> i.version()).orElse("dev");
    }

    public static ResourceLocation id(final String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private Slate() {}
}
