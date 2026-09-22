package dev.fallingcloud.slate.multiplayer;

import dev.fallingcloud.slate.core.config.JsonConfig;
import dev.fallingcloud.slate.core.platform.SlatePlatform;

/** Lazily loaded config files. The client config is only ever touched on the physical client. */
public final class MultiplayerConfigs {

    private static JsonConfig<MultiplayerConfig> client;
    private static JsonConfig<MultiplayerServerConfig> server;

    public static synchronized JsonConfig<MultiplayerConfig> clientFile() {
        if (client == null) client = JsonConfig.of("multiplayer", MultiplayerConfig.class, MultiplayerConfig::new);
        return client;
    }

    public static MultiplayerConfig client() { return clientFile().get(); }

    public static synchronized JsonConfig<MultiplayerServerConfig> serverFile() {
        if (server == null) server = JsonConfig.of("multiplayer-server", MultiplayerServerConfig.class, MultiplayerServerConfig::new);
        return server;
    }

    public static MultiplayerServerConfig server() { return serverFile().get(); }

    /** The client's config dir for caches and media: {@code config/slate/multiplayer/}. */
    public static java.nio.file.Path clientDataDir() {
        return JsonConfig.dir().resolve("multiplayer");
    }

    public static boolean isClient() { return SlatePlatform.get().isClient(); }

    private MultiplayerConfigs() {}
}
