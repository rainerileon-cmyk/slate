package dev.fallingcloud.slate.multiplayer.client;

import dev.fallingcloud.slate.core.layout.action.ActionType;
import dev.fallingcloud.slate.core.layout.action.ActionType.Arg;
import dev.fallingcloud.slate.multiplayer.client.stream.ScreenShare;
import dev.fallingcloud.slate.multiplayer.client.ui.FriendsHubScreen;
import dev.fallingcloud.slate.multiplayer.voice.VoiceStatus;
import java.util.List;
import net.minecraft.network.chat.Component;

/** The dev-mode actions this module contributes (buttons in custom layouts can run them). */
final class MultiplayerActions {

    static List<ActionType> all() {
        return List.of(
            new ActionType("slate_multiplayer:open_friends", Component.translatable("slate_multiplayer.action.open_friends"),
                List.of(Arg.choice("page", Component.translatable("slate_multiplayer.action.arg.page"), "friends", FriendsHubScreen.PAGE_IDS)),
                a -> FriendsHubScreen.open(a.getOrDefault("page", "friends"))),
            new ActionType("slate_multiplayer:open_messages", Component.translatable("slate_multiplayer.action.open_messages"),
                List.of(Arg.text("thread", Component.translatable("slate_multiplayer.action.arg.thread"), "")),
                a -> {
                    final String t = a.getOrDefault("thread", "");
                    if (t.isBlank()) FriendsHubScreen.open("messages"); else FriendsHubScreen.openThread(t);
                }),
            new ActionType("slate_multiplayer:share_screen", Component.translatable("slate_multiplayer.action.share_screen"),
                List.of(Arg.choice("mode", Component.translatable("slate_multiplayer.action.arg.mode"), "TOGGLE", List.of("TOGGLE", "START", "STOP"))),
                a -> {
                    switch (a.getOrDefault("mode", "TOGGLE")) {
                        case "START" -> ScreenShare.start("");
                        case "STOP" -> ScreenShare.stop();
                        default -> ScreenShare.toggle();
                    }
                }),
            new ActionType("slate_multiplayer:toggle_mute", Component.translatable("slate_multiplayer.action.toggle_mute"), List.of(),
                a -> { if (VoiceStatus.available()) VoiceStatus.toggleMuted(); else Notifications.plain("Voice chat not installed", null); }),
            new ActionType("slate_multiplayer:toggle_deafen", Component.translatable("slate_multiplayer.action.toggle_deafen"), List.of(),
                a -> { if (VoiceStatus.available()) VoiceStatus.toggleDeafened(); else Notifications.plain("Voice chat not installed", null); }));
    }

    private MultiplayerActions() {}
}
