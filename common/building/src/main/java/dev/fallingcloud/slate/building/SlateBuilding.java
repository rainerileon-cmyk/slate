package dev.fallingcloud.slate.building;

import dev.fallingcloud.slate.building.config.BuildingConfig;
import dev.fallingcloud.slate.building.config.BuildingServerConfig;
import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.net.BuildingNetwork;
import dev.fallingcloud.slate.building.registry.BuildingRegistry;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.config.JsonConfig;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.module.SlateModule;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Slate Building module: unified block variants, the quick-swap wheel, the placement ghost, the build menu with
 * area modes and undo, and the toolbox. Both sides, and unlike the other Slate modules it registers content, so a
 * client and server must both have it.
 *
 * <p>Boot order (both loaders): the loader entry calls {@code Modules.register(MODULE)}, which runs {@link #init()}
 * and then, on a physical client, {@link #initClient()}. {@link #init()} only QUEUES registry entries
 * ({@link BuildingRegistry}); each loader entry flushes them later (NeoForge in {@code RegisterEvent}, Fabric right
 * after registration), so nothing may call {@code RegistryRef.get()} during init/initClient. Each area's own init
 * class is called from here in a fixed order so owners never touch this file.
 */
public final class SlateBuilding implements SlateModule {

    public static final String MOD_ID = "slate_building";
    public static final Logger LOGGER = LoggerFactory.getLogger("Slate Building");
    public static final SlateBuilding MODULE = new SlateBuilding();

    private static JsonConfig<BuildingConfig> clientConfig;
    private static JsonConfig<BuildingServerConfig> serverConfig;

    private SlateBuilding() {}

    /** {@code slate_building:<path>} (content ids; payload ids use {@code Slate.id("building/...")} instead). */
    public static ResourceLocation id(final String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    /** {@code config/slate/building.json}: this player's client preferences. Only meaningful on a physical client. */
    public static synchronized JsonConfig<BuildingConfig> configFile() {
        if (clientConfig == null) clientConfig = JsonConfig.of("building", BuildingConfig.class, BuildingConfig::new);
        return clientConfig;
    }

    public static BuildingConfig config() { return configFile().get(); }

    /**
     * {@code config/slate/building-server.json}: the rules of the server running in THIS process (a dedicated server
     * or the integrated one). Code that must honour the rules of whatever server the player is on (client side
     * included) reads {@link BuildingServerSettings#effective} instead.
     */
    public static synchronized JsonConfig<BuildingServerConfig> serverConfigFile() {
        if (serverConfig == null) serverConfig = JsonConfig.of("building-server", BuildingServerConfig.class, BuildingServerConfig::new);
        return serverConfig;
    }

    public static BuildingServerConfig serverConfig() { return serverConfigFile().get(); }

    @Override public String id() { return MOD_ID; }

    @Override public Component displayName() { return Component.translatable("slate_building.name"); }

    @Override public Icon icon() { return Icon.BLOCK; }

    @Override
    public void init() {
        Slate.init();
        serverConfigFile();
        BuildingRegistry.bootstrap();
        BuildingNetwork.register();
        SlateEvents.PLAYER_JOINED.register(BuildingServerSettings::sendTo);
        dev.fallingcloud.slate.building.variant.VariantSystem.init();
        dev.fallingcloud.slate.building.chisel.ChiselSystem.init();
        dev.fallingcloud.slate.building.ops.OpsSystem.init();
        dev.fallingcloud.slate.building.toolbox.ToolboxSystem.init();
        LOGGER.info("[Slate Building] init ({} registry entries queued)", BuildingRegistry.entries().size());
    }

    @Override
    public void initClient() {
        ClientSide.init();
    }

    @Override
    public List<HubEntry> hubEntries() {
        return SlatePlatform.get().isClient() ? ClientSide.hubEntries() : List.of();
    }

    /** Client-only references live here so a dedicated server never resolves them. */
    private static final class ClientSide {
        static void init() { dev.fallingcloud.slate.building.client.BuildingClient.init(); }
        static List<HubEntry> hubEntries() { return dev.fallingcloud.slate.building.client.BuildingClient.hubEntries(); }
    }
}
