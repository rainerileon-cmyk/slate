package dev.fallingcloud.slate.multiplayer.client;

import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import dev.fallingcloud.slate.multiplayer.MultiplayerConfig;
import dev.fallingcloud.slate.multiplayer.MultiplayerConfigs;
import dev.fallingcloud.slate.multiplayer.client.stream.StreamViewer;
import dev.fallingcloud.slate.multiplayer.client.ui.FriendsHubScreen;
import dev.fallingcloud.slate.multiplayer.social.ChatMessage;
import dev.fallingcloud.slate.multiplayer.social.FriendInfo;
import dev.fallingcloud.slate.multiplayer.social.GroupInfo;
import dev.fallingcloud.slate.multiplayer.social.GroupInvite;
import dev.fallingcloud.slate.multiplayer.social.Presence;
import dev.fallingcloud.slate.multiplayer.social.RequestInfo;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage;
import dev.fallingcloud.slate.multiplayer.social.StreamInfo;
import dev.fallingcloud.slate.multiplayer.voice.VoiceStatus;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Toasts for social events (each gated by its config toggle); clicking one opens the relevant place. */
public final class Notifications {

    private static MultiplayerConfig cfg() { return MultiplayerConfigs.client(); }

    private static Component t(final String key, final Object... args) { return Component.translatable("slate_multiplayer.toast." + key, args); }

    public static void plain(final String title, @Nullable final String body) {
        SlateToasts.show(Component.literal(title), body == null ? null : Component.literal(body), Icon.FRIENDS);
    }

    static void friendRequest(final RequestInfo r) {
        if (!cfg().notifyRequests) return;
        SlateToasts.show(t("request"), t("request.body", r.ref().display()), Icon.INVITE, () -> FriendsHubScreen.open("requests"));
    }

    static void friendAdded(final FriendInfo f) {
        SlateToasts.show(t("friend_added"), Component.literal(f.display()), Icon.FRIENDS, () -> FriendsHubScreen.openPlayer(f.ref().uuid()));
    }

    static void friendOnline(final Friend f) {
        if (!cfg().notifyFriendOnline) return;
        SlateToasts.show(t("online", f.display()), Component.literal(presenceLine(f.presence)), Icon.ONLINE, () -> FriendsHubScreen.openPlayer(f.uuid));
    }

    static void message(final ThreadModel thread, final ChatMessage m) {
        if (!cfg().notifyMessages) return;
        final String title = thread.isGroup() ? SocialClient.get().threadTitle(thread) : m.from().display();
        final String body = m.text().isEmpty() ? (m.hasAttachment() ? "[" + m.attachKind() + "]" : "") : (thread.isGroup() ? m.from().display() + ": " : "") + m.text();
        SlateToasts.show(Component.literal(title), Component.literal(body.length() > 90 ? body.substring(0, 90) + "..." : body), Icon.CHAT,
            () -> FriendsHubScreen.openThread(thread.key));
    }

    static void groupInvite(final GroupInvite i) {
        if (!cfg().notifyInvites) return;
        SlateToasts.show(t("group_invite"), t("group_invite.body", i.from().display(), i.group().name()), Icon.GROUP, () -> FriendsHubScreen.open("groups"));
    }

    static void groupJoined(final GroupInfo g) {
        SlateToasts.show(t("group_joined"), Component.literal(g.name()), Icon.GROUP, () -> FriendsHubScreen.open("groups"));
    }

    static void groupCreated(final String id) {}

    static void invite(final SocialMessage.InviteIn i) {
        if (!cfg().notifyInvites) return;
        if ("voice".equals(i.kind())) {
            SlateToasts.show(t("voice_invite", i.from().display()), Component.literal(i.label()), Icon.HEADSET, () -> {
                try { VoiceStatus.joinGroup(UUID.fromString(i.address())); } catch (final IllegalArgumentException ignored) {}
            });
        } else {
            SlateToasts.show(t("server_invite", i.from().display()), t("server_invite.body", i.label().isEmpty() ? i.address() : i.label()), Icon.SERVER,
                () -> CoreActions.joinServer(i.address(), i.label()));
        }
    }

    static void streamStarted(final StreamInfo s) {
        if (!cfg().streams || !cfg().notifyStreams) return;
        SlateToasts.show(t("stream", s.owner().display()), Component.literal(s.title()), Icon.STREAM, () -> StreamViewer.watch(s.id(), true));
    }

    static void hubError(final SocialMessage.Error e) {
        SlateToasts.show(t("hub_error", e.context()), Component.literal(e.message()), Icon.WARNING);
    }

    /** "Playing on play.example.com · The Nether" style line for a presence. */
    public static String presenceLine(@Nullable final Presence p) {
        if (p == null || !p.online()) return "Offline";
        final StringBuilder sb = new StringBuilder();
        switch (p.state()) {
            case AWAY -> sb.append("Away");
            case IN_GAME -> sb.append(p.server().isEmpty() ? "In game" : Presence.SINGLEPLAYER.equals(p.server()) ? "Singleplayer" : "On " + p.server());
            default -> sb.append("In menus");
        }
        if (!p.dimension().isEmpty()) sb.append(" · ").append(dimensionName(p.dimension()));
        if (!p.status().isEmpty()) sb.append(" · ").append(p.status());
        return sb.toString();
    }

    public static String dimensionName(final String path) {
        return switch (path) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "The Nether";
            case "the_end" -> "The End";
            default -> {
                final String s = path.replace('_', ' ');
                yield s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
            }
        };
    }

    private Notifications() {}
}
