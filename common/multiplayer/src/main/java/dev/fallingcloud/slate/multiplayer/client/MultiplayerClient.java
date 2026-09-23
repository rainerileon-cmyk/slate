package dev.fallingcloud.slate.multiplayer.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.event.SlateKeys;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.Placeholders;
import dev.fallingcloud.slate.core.layout.action.ActionType;
import dev.fallingcloud.slate.core.module.SlateModule;
import dev.fallingcloud.slate.multiplayer.MultiplayerConfigs;
import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;
import dev.fallingcloud.slate.multiplayer.client.stream.ScreenShare;
import dev.fallingcloud.slate.multiplayer.client.stream.StreamViewer;
import dev.fallingcloud.slate.multiplayer.client.ui.FriendsHubScreen;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client bootstrap of the Multiplayer module: the social client and its links, the stream viewer,
 * layout element types, the hub keybind, placeholders, the Menu bridge, hub entries and actions.
 * Only ever loaded on the physical client (reached from {@code SlateMultiplayer.ClientSide}).
 */
public final class MultiplayerClient {

    public static final KeyMapping FRIENDS_KEY = new KeyMapping("key.slate_multiplayer.friends", InputConstants.Type.KEYSYM, InputConstants.KEY_F8, SlateKeys.CATEGORY);

    private static boolean initialised;

    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        MultiplayerConfigs.clientFile();
        SocialClient.get().init();
        StreamViewer.init();
        MultiplayerElements.registerAll();
        SlateKeys.register(FRIENDS_KEY);
        Placeholders.register("friends_online", () -> Integer.toString(SocialClient.get().onlineFriends()));
        Placeholders.register("friends_total", () -> Integer.toString(SocialClient.get().friends().size()));
        Placeholders.register("unread_messages", () -> Integer.toString(SocialClient.get().unreadTotal()));
        CoreActions.SCREEN_FACTORIES.put("slate_multiplayer:hub", FriendsHubScreen::new);
        SlateEvents.CLIENT_TICK_END.register(MultiplayerClient::tick);
        MenuBridge.install();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { SocialClient.get().shutdown(); } catch (final Throwable ignored) {}
        }, "slate-multiplayer-shutdown"));
        SlateMultiplayer.LOGGER.info("[Slate Multiplayer] client ready (home hub: {})",
            MultiplayerConfigs.client().hubHost() == null ? "none" : MultiplayerConfigs.client().homeHub);
    }

    private static void tick() {
        while (FRIENDS_KEY.consumeClick()) {
            final Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof FriendsHubScreen s) s.back(); else FriendsHubScreen.open(null);
        }
    }

    /** Raw key on a screen (from the KeyboardHandler mixin): the friends key toggles the hub. */
    public static boolean onScreenKey(final int key, final int scancode) {
        if (!initialised || FRIENDS_KEY.isUnbound() || !FRIENDS_KEY.matches(key, scancode)) return false;
        final Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof FriendsHubScreen s) s.back(); else FriendsHubScreen.open(null);
        return true;
    }

    /** The {@code slate:social} payload handler (client side). */
    public static void onPayload(final SocialMessage m) {
        SocialClient.onPayload(m);
    }

    public static List<SlateModule.HubEntry> hubEntries() {
        return List.of(
            new SlateModule.HubEntry(Component.translatable("slate_multiplayer.hub.friends"), Icon.FRIENDS, () -> FriendsHubScreen.open("friends")),
            new SlateModule.HubEntry(Component.translatable("slate_multiplayer.hub.messages"), Icon.CHAT, () -> FriendsHubScreen.open("messages")),
            new SlateModule.HubEntry(Component.translatable("slate_multiplayer.hub.share"), Icon.SCREEN_SHARE, ScreenShare::toggle),
            new SlateModule.HubEntry(Component.translatable("slate_multiplayer.hub.settings"), Icon.SETTINGS, () -> FriendsHubScreen.open("settings")));
    }

    public static List<ActionType> actions() {
        return MultiplayerActions.all();
    }

    private MultiplayerClient() {}
}
