package dev.fallingcloud.slate.multiplayer.client.ui;

import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateAvatar;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateContextMenu;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.core.widget.SlateSlider;
import dev.fallingcloud.slate.core.widget.popup.MenuPopup;
import dev.fallingcloud.slate.multiplayer.MultiplayerConfigs;
import dev.fallingcloud.slate.multiplayer.client.Friend;
import dev.fallingcloud.slate.multiplayer.client.SocialClient;
import dev.fallingcloud.slate.multiplayer.social.GroupInfo;
import dev.fallingcloud.slate.multiplayer.social.Presence;
import dev.fallingcloud.slate.multiplayer.voice.VoiceStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Shared drawing/formatting helpers and the friend context menu used by every social surface. */
public final class UiUtil {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM");
    private static final DateTimeFormatter DAY_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy");

    public static Component t(final String key, final Object... args) {
        return Component.translatable("slate_multiplayer." + key, args);
    }

    public static SlateAvatar.Status avatarStatus(@Nullable final Presence p) {
        if (p == null) return SlateAvatar.Status.OFFLINE;
        return switch (p.state()) {
            case OFFLINE -> SlateAvatar.Status.OFFLINE;
            case AWAY -> SlateAvatar.Status.AWAY;
            default -> SlateAvatar.Status.ONLINE;
        };
    }

    public static int statusColor(@Nullable final Presence p) {
        final var pal = Theme.current().palette();
        if (p == null) return pal.textDim();
        return switch (p.state()) {
            case OFFLINE -> pal.textDim();
            case AWAY -> pal.warning();
            default -> pal.success();
        };
    }

    /** A player head with status dot and, when Simple Voice Chat says so, a speaking ring. */
    public static void drawHead(final GuiGraphics g, final UUID uuid, final String name, final int x, final int y, final int size, @Nullable final Presence presence, final float alpha) {
        if (MultiplayerConfigs.client().voiceSpeakingRings && VoiceStatus.isSpeaking(uuid)) {
            final int c = Colors.scaleAlpha(VoiceStatus.isWhispering(uuid) ? Theme.current().palette().warning() : Theme.current().palette().success(), alpha);
            SlateDraw.outline(g, x - 2, y - 2, size + 4, size + 4, c, 1);
        }
        SlateAvatar.draw(g, SlateAvatar.skinFor(uuid, name), x, y, size, presence == null ? SlateAvatar.Status.NONE : avatarStatus(presence), alpha);
    }

    public static String relativeTime(final long ms) {
        if (ms <= 0) return "";
        final long d = System.currentTimeMillis() - ms;
        if (d < 60_000) return "just now";
        if (d < 3_600_000) return (d / 60_000) + " min ago";
        if (d < 86_400_000) return (d / 3_600_000) + " h ago";
        if (d < 7 * 86_400_000L) return (d / 86_400_000) + " d ago";
        return DAY_YEAR.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()));
    }

    public static String clock(final long ms) {
        return CLOCK.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()));
    }

    public static String dayLabel(final long ms) {
        final LocalDate d = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()).toLocalDate();
        final LocalDate today = LocalDate.now();
        if (d.equals(today)) return "Today";
        if (d.equals(today.minusDays(1))) return "Yesterday";
        return d.getYear() == today.getYear() ? DAY.format(d) : DAY_YEAR.format(d);
    }

    public static boolean sameDay(final long a, final long b) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(a), ZoneId.systemDefault()).toLocalDate()
            .equals(LocalDateTime.ofInstant(Instant.ofEpochMilli(b), ZoneId.systemDefault()).toLocalDate());
    }

    /** "Last seen 5 min ago" / presence line for lists. */
    public static String presenceLine(final Friend f) {
        if (f.online()) return dev.fallingcloud.slate.multiplayer.client.Notifications.presenceLine(f.presence);
        final long seen = f.presence == null ? 0 : f.presence.sinceMs();
        return seen > 0 ? "Last seen " + relativeTime(seen) : "Offline";
    }

    public static List<GroupInfo> mutualGroups(final UUID other) {
        final List<GroupInfo> out = new ArrayList<>();
        for (final GroupInfo g : SocialClient.get().groups()) if (g.isMember(other)) out.add(g);
        return out;
    }

    // ------------------------------------------------------------------ friend menu

    public static List<MenuPopup.Item> friendMenu(final Friend f) {
        final SocialClient sc = SocialClient.get();
        final List<MenuPopup.Item> items = new ArrayList<>();
        items.add(MenuPopup.Item.of(t("menu.message"), Icon.CHAT, () -> FriendsHubScreen.openThread(sc.dmThread(f.uuid).key)));
        if (f.joinable()) items.add(MenuPopup.Item.of(t("menu.join"), Icon.SERVER, () -> CoreActions.joinServer(f.presence.server(), f.display())));
        final Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null && mc.level != null && f.online()) {
            items.add(MenuPopup.Item.of(t("menu.invite_server"), Icon.INVITE, () -> {
                if (sc.inviteToMyServer(f.uuid)) dev.fallingcloud.slate.multiplayer.client.Notifications.plain("Invite sent", f.display());
            }));
        }
        if (VoiceStatus.available() && VoiceStatus.hasVoice(f.uuid)) items.add(MenuPopup.Item.of(t("menu.voice_volume"), Icon.VOLUME, () -> voiceVolumeDialog(f)));
        items.add(MenuPopup.Item.sep());
        items.add(MenuPopup.Item.of(t("menu.nickname"), Icon.EDIT, () -> PromptPopup.open(t("menu.nickname"), t("nickname.body", f.name), f.nick, 32, v -> sc.setNickname(f.uuid, v, f.note))));
        items.add(MenuPopup.Item.of(t("menu.note"), Icon.TEXT, () -> PromptPopup.open(t("menu.note"), t("note.body", f.name), f.note, 200, v -> sc.setNickname(f.uuid, f.nick, v))));
        items.add(MenuPopup.Item.sep());
        items.add(MenuPopup.Item.danger(t("menu.remove"), Icon.MINUS, () -> SlateModal.confirmDanger(t("menu.remove"), t("remove.body", f.display()), t("menu.remove"), () -> sc.removeFriend(f.uuid))));
        items.add(MenuPopup.Item.danger(t("menu.block"), Icon.BLOCKED, () -> SlateModal.confirmDanger(t("menu.block"), t("block.body", f.display()), t("menu.block"), () -> sc.block(f.uuid, f.name))));
        return items;
    }

    public static void openFriendMenu(final Friend f, final double mx, final double my) {
        SlateContextMenu.open(mx, my, friendMenu(f));
    }

    public static void voiceVolumeDialog(final Friend f) {
        final SlateSlider slider = new SlateSlider(0, 0, SlateModal.WIDTH - 24, t("voice.volume"), 0, 200, 5, VoiceStatus.volume(f.uuid) * 100,
            v -> Math.round(v) + "%", v -> VoiceStatus.setVolume(f.uuid, v / 100.0)).compact(true);
        new SlateModal(t("menu.voice_volume"), Component.literal(f.display()), Icon.VOLUME).extra(slider)
            .button(Component.translatable("gui.done"), SlateButton.Variant.PRIMARY, null).show();
    }

    private UiUtil() {}
}
