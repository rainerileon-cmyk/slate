package dev.fallingcloud.slate.chat;

import dev.fallingcloud.slate.chat.net.ChatPayloads;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.action.ActionType;
import dev.fallingcloud.slate.core.module.SlateModule;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Slate Chat module: Chatterbox rebuilt on Core + Multiplayer. Both sides register the typing payload
 * and the relay limits; the client side (mixins on {@code ChatComponent} / {@code ChatScreen}, the media
 * buttons, emotes, history, channels) is wired from {@link #initClient()} and lives under
 * {@code dev.fallingcloud.slate.chat.client}, which a dedicated server never loads.
 */
public final class SlateChat implements SlateModule {

    public static final String MOD_ID = "slate_chat";
    public static final Logger LOGGER = LoggerFactory.getLogger("Slate Chat");
    public static final SlateChat MODULE = new SlateChat();

    private SlateChat() {}

    public static ResourceLocation id(final String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override public String id() { return MOD_ID; }

    @Override public Component displayName() { return Component.translatable("slate_chat.name"); }

    @Override public Icon icon() { return Icon.CHAT; }

    @Override
    public void init() {
        Slate.init();
        ChatPayloads.register();
        SlateEvents.SERVER_STARTED.register(server -> ChatServerConfig.apply());
        LOGGER.info("[Slate Chat] init");
    }

    @Override
    public void initClient() {
        ClientHolder.init();
    }

    @Override
    public List<HubEntry> hubEntries() {
        if (!SlatePlatform.get().isClient()) return List.of();
        return ClientHolder.hubEntries();
    }

    @Override
    public List<ActionType> actions() {
        if (!SlatePlatform.get().isClient()) return List.of();
        return ClientHolder.actions();
    }

    /** Client-only references live here so a dedicated server never resolves the client package. */
    private static final class ClientHolder {
        static void init() { dev.fallingcloud.slate.chat.client.ChatClient.init(); }
        static List<HubEntry> hubEntries() { return dev.fallingcloud.slate.chat.client.ChatClient.hubEntries(); }
        static List<ActionType> actions() { return dev.fallingcloud.slate.chat.client.ChatClient.actions(); }
    }
}
