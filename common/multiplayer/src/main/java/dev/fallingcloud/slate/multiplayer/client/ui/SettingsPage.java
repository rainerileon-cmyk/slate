package dev.fallingcloud.slate.multiplayer.client.ui;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.ui.Flow;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SidebarScreen;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateLabel;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateSeparator;
import dev.fallingcloud.slate.core.widget.SlateSlider;
import dev.fallingcloud.slate.core.widget.SlateTextField;
import dev.fallingcloud.slate.core.widget.SlateToggle;
import dev.fallingcloud.slate.multiplayer.MultiplayerConfig;
import dev.fallingcloud.slate.multiplayer.MultiplayerConfigs;
import dev.fallingcloud.slate.multiplayer.client.LinkState;
import dev.fallingcloud.slate.multiplayer.client.SocialClient;
import dev.fallingcloud.slate.multiplayer.voice.VoiceStatus;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;

/**
 * Home hub, presence privacy, notifications, screen share caps, voice and panel options. The two text
 * fields (hub address, status) live in a fixed block above the scrolling options, because a text field's
 * frame is bound to its construction position.
 */
final class SettingsPage extends FriendsHubScreen.HubPage {

    private SlateLabel hubStatus;
    private SlateTextField hubField;

    SettingsPage(final FriendsHubScreen screen) {
        super(screen, "settings", UiUtil.t("page.settings"), Icon.SETTINGS);
    }

    private static void save(final Consumer<MultiplayerConfig> edit) {
        MultiplayerConfigs.clientFile().update(edit);
    }

    private static void savePresence(final Consumer<MultiplayerConfig> edit) {
        save(edit);
        SocialClient.get().presenceChanged();
    }

    @Override
    public void build(final SidebarScreen s, final Rect area) {
        final MultiplayerConfig cfg = MultiplayerConfigs.client();
        final int w = area.w() - 10;
        int y = area.y();

        // ---- fixed block: hub address + connect, status line, status text
        s.addPageWidget(new SlateLabel(area.x(), y, w, UiUtil.t("settings.section.hub")).style(SlateLabel.Style.TITLE));
        y += 14;
        hubField = new SlateTextField(area.x(), y, w - 90, UiUtil.t("settings.home_hub"));
        hubField.placeholder(UiUtil.t("settings.home_hub.placeholder")).icon(Icon.SERVER).maxLength(120).setValue(cfg.homeHub);
        hubField.onEnter(this::applyHub);
        s.addPageWidget(hubField);
        s.addPageWidget(new SlateButton(area.x() + w - 84, y, 84, UiUtil.t("settings.connect"), this::applyHub).variant(SlateButton.Variant.PRIMARY).icon(Icon.LINK));
        y += 24;
        hubStatus = s.addPageWidget(new SlateLabel(area.x(), y, w, Component.empty()).style(SlateLabel.Style.MUTED));
        refreshStatus();
        y += 14;
        final SlateTextField status = new SlateTextField(area.x(), y, w, UiUtil.t("settings.status"));
        status.placeholder(UiUtil.t("settings.status.placeholder")).icon(Icon.EDIT).maxLength(100).setValue(cfg.statusText);
        status.onChange(v -> savePresence(c -> c.statusText = v));
        s.addPageWidget(status);
        y += 26;

        // ---- scrolling options
        final SlateScrollPanel panel = s.addPageWidget(new SlateScrollPanel(area.x(), y, area.w(), area.bottom() - y));
        final Flow f = Flow.column(0, 0, 6);
        panel.add(f.place(new SlateLabel(0, 0, w, UiUtil.t("settings.home_hub.help")).style(SlateLabel.Style.CAPTION).wrap(true)));
        panel.add(f.place(new SlateToggle(0, 0, w, UiUtil.t("settings.hub_auth"), cfg.hubAuth, v -> save(c -> c.hubAuth = v))));

        panel.add(f.place(new SlateSeparator(0, 0, w, UiUtil.t("settings.section.presence"))));
        panel.add(f.place(new SlateToggle(0, 0, w, UiUtil.t("settings.share_server"), cfg.shareServer, v -> savePresence(c -> c.shareServer = v))));
        panel.add(f.place(new SlateToggle(0, 0, w, UiUtil.t("settings.share_dimension"), cfg.shareDimension, v -> savePresence(c -> c.shareDimension = v))));
        panel.add(f.place(new SlateToggle(0, 0, w, UiUtil.t("settings.away"), cfg.away, v -> savePresence(c -> c.away = v))));
        panel.add(f.place(new SlateToggle(0, 0, w, UiUtil.t("settings.invisible"), cfg.invisible, v -> savePresence(c -> c.invisible = v))));

        panel.add(f.place(new SlateSeparator(0, 0, w, UiUtil.t("settings.section.notifications"))));
        panel.add(f.place(new SlateToggle(0, 0, w, UiUtil.t("settings.notify.online"), cfg.notifyFriendOnline, v -> save(c -> c.notifyFriendOnline = v))));
        panel.add(f.place(new SlateToggle(0, 0, w, UiUtil.t("settings.notify.requests"), cfg.notifyRequests, v -> save(c -> c.notifyRequests = v))));
        panel.add(f.place(new SlateToggle(0, 0, w, UiUtil.t("settings.notify.messages"), cfg.notifyMessages, v -> save(c -> c.notifyMessages = v))));
        panel.add(f.place(new SlateToggle(0, 0, w, UiUtil.t("settings.notify.invites"), cfg.notifyInvites, v -> save(c -> c.notifyInvites = v))));
        panel.add(f.place(new SlateToggle(0, 0, w, UiUtil.t("settings.notify.streams"), cfg.notifyStreams, v -> save(c -> c.notifyStreams = v))));

        panel.add(f.place(new SlateSeparator(0, 0, w, UiUtil.t("settings.section.stream"))));
        panel.add(f.place(new SlateSlider(0, 0, w, UiUtil.t("settings.stream.width"), 320, 1280, 32, cfg.streamMaxWidth, v -> (int) v + " px", v -> save(c -> c.streamMaxWidth = (int) v)).compact(true)));
        panel.add(f.place(new SlateSlider(0, 0, w, UiUtil.t("settings.stream.quality"), 0.2, 0.9, 0.05, cfg.streamQuality, v -> Math.round(v * 100) + "%", v -> save(c -> c.streamQuality = v)).compact(true)));
        panel.add(f.place(new SlateSlider(0, 0, w, UiUtil.t("settings.stream.min_fps"), 1, 15, 1, cfg.streamMinFps, v -> (int) v + " fps", v -> save(c -> c.streamMinFps = (int) v)).compact(true)));
        panel.add(f.place(new SlateSlider(0, 0, w, UiUtil.t("settings.stream.max_fps"), 4, 20, 1, cfg.streamMaxFps, v -> (int) v + " fps", v -> save(c -> c.streamMaxFps = (int) v)).compact(true)));
        panel.add(f.place(new SlateSlider(0, 0, w, UiUtil.t("settings.stream.pip_width"), 96, 320, 8, cfg.pipWidth, v -> (int) v + " px", v -> save(c -> c.pipWidth = (int) v)).compact(true)));
        panel.add(f.place(new SlateButton(0, 0, 140, UiUtil.t("settings.stream.reset_pip"), () -> save(c -> { c.pipX = -1; c.pipY = -1; })).variant(SlateButton.Variant.GHOST).icon(Icon.PIP)));

        panel.add(f.place(new SlateSeparator(0, 0, w, UiUtil.t("settings.section.voice"))));
        if (VoiceStatus.available()) {
            panel.add(f.place(new SlateToggle(0, 0, w, UiUtil.t("voice.mute"), VoiceStatus.isMuted(), VoiceStatus::setMuted)));
            panel.add(f.place(new SlateToggle(0, 0, w, UiUtil.t("voice.deafen"), VoiceStatus.isDeafened(), VoiceStatus::setDeafened)));
        } else {
            panel.add(f.place(new SlateLabel(0, 0, w, UiUtil.t("settings.voice.missing")).style(SlateLabel.Style.MUTED).wrap(true)));
        }
        panel.add(f.place(new SlateToggle(0, 0, w, UiUtil.t("settings.voice.rings"), cfg.voiceSpeakingRings, v -> save(c -> c.voiceSpeakingRings = v))));
        panel.add(f.place(new SlateSlider(0, 0, w, UiUtil.t("settings.voice.clip_seconds"), 5, 120, 5, cfg.voiceClipMaxSeconds, v -> (int) v + " s", v -> save(c -> c.voiceClipMaxSeconds = (int) v)).compact(true)));

        panel.add(f.place(new SlateSeparator(0, 0, w, UiUtil.t("settings.section.panel"))));
        panel.add(f.place(new SlateToggle(0, 0, w, UiUtil.t("settings.panel.offline"), cfg.panelShowOffline, v -> save(c -> c.panelShowOffline = v))));
        panel.add(f.place(new SlateSlider(0, 0, w, UiUtil.t("settings.panel.rows"), 3, 20, 1, cfg.panelMaxRows, v -> Integer.toString((int) v), v -> save(c -> c.panelMaxRows = (int) v)).compact(true)));
        panel.add(f.place(new SlateSlider(0, 0, w, UiUtil.t("settings.cache_messages"), 20, 500, 10, cfg.cacheMessagesPerThread, v -> Integer.toString((int) v), v -> save(c -> c.cacheMessagesPerThread = (int) v)).compact(true)));
        panel.add(f.place(new SlateButton(0, 0, 160, Component.translatable("slate.settings.open_config_folder"), () ->
            net.minecraft.Util.getPlatform().openPath(MultiplayerConfigs.clientDataDir())).variant(SlateButton.Variant.GHOST).icon(Icon.FOLDER)));
        panel.setContentHeight(f.maxY() + 8);
    }

    private void applyHub() {
        final String v = hubField.getValue().trim();
        save(c -> c.homeHub = v);
        SocialClient.get().reconnect();
        refreshStatus();
    }

    @Override
    void onModelChanged() { refreshStatus(); }

    private void refreshStatus() {
        if (hubStatus == null) return;
        final SocialClient sc = SocialClient.get();
        final String text = switch (sc.linkState()) {
            case CONNECTED -> "Connected to " + (sc.hubName().isEmpty() ? sc.linkDescription() : sc.hubName()) + (sc.usingHub() ? "" : " (this server)")
                + (sc.hubOnlineMode() ? "  ·  Mojang-verified" : "  ·  offline mode") + (sc.latencyMs() >= 0 ? "  ·  " + sc.latencyMs() + " ms" : "");
            case CONNECTING -> "Connecting to " + sc.linkDescription() + "...";
            case AUTHENTICATING -> "Verifying your session with " + sc.linkDescription() + "...";
            case FAILED -> "Cannot connect: " + sc.stateDetail() + " (retrying)";
            default -> MultiplayerConfigs.client().hubHost() == null ? "No home hub set - friends work on servers that run Slate Multiplayer" : "Not connected";
        };
        hubStatus.text(Component.literal(text));
        hubStatus.color(sc.linkState() == LinkState.FAILED ? Theme.current().palette().danger() : 0);
    }

    @Override
    public void tick() {
        if ((System.currentTimeMillis() / 1000) % 2 == 0) refreshStatus();
    }
}
