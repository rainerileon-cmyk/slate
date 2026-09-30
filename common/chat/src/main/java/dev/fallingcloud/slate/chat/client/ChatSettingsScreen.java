package dev.fallingcloud.slate.chat.client;

import dev.fallingcloud.slate.chat.ChatConfig;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.media.MediaCache;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateLabel;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateSegmented;
import dev.fallingcloud.slate.core.widget.SlateSeparator;
import dev.fallingcloud.slate.core.widget.SlateSlider;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import dev.fallingcloud.slate.core.widget.SlateToggle;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Slate Chat's own settings page ({@code chat.json}), reachable from the hub. The Config module offers the
 * same options in its Chat page; this one keeps Chat usable on its own. Every change saves and re-wraps
 * the chat immediately.
 */
public final class ChatSettingsScreen extends SlateScreen {

    public ChatSettingsScreen(@Nullable final Screen parent) {
        super(Component.translatable("slate_chat.settings.title"), parent);
        this.maxContentWidth = 420;
    }

    @Override
    protected void build() {
        final ChatConfig cfg = ChatConfig.get();
        final Rect c = contentRect();
        final SlateScrollPanel panel = add(new SlateScrollPanel(c.x(), c.y() + 4, c.w(), c.h() - 4));
        final int w = c.w() - 10;
        final int half = w / 2 - 4;
        int y = 0;

        y = section(panel, y, w, "slate_chat.settings.section.look");
        // The menu style stays reachable from every module's own settings (R6), Slate UI or not.
        panel.add(dev.fallingcloud.slate.core.client.settings.SettingRow.of(w, dev.fallingcloud.slate.core.client.settings.LayoutStyleRows.styleLabel(), null,
            dev.fallingcloud.slate.core.client.settings.LayoutStyleRows.styleSegmented(0, 0, dev.fallingcloud.slate.core.client.settings.SettingRow.controlWidth(w), null)), 0, y);
        y += dev.fallingcloud.slate.core.client.settings.SettingRow.HEIGHT + 6;
        panel.add(new SlateSegmented<>(0, y, w, List.of("COZY", "COMPACT"), cfg.isCompact() ? "COMPACT" : "COZY",
            v -> Component.translatable("slate_chat.density." + v.toLowerCase(java.util.Locale.ROOT)), v -> { cfg.density = v; ChatClient.saveAndApply(); }), 0, y);
        y += 26;
        y = toggles(panel, y, half,
            new Opt("slate_chat.settings.grouping", () -> cfg.grouping, v -> cfg.grouping = v),
            new Opt("slate_chat.settings.heads", () -> cfg.heads, v -> cfg.heads = v),
            new Opt("slate_chat.settings.timestamps", () -> cfg.timestamps, v -> cfg.timestamps = v),
            new Opt("slate_chat.settings.fade", () -> cfg.fadeUnfocused, v -> cfg.fadeUnfocused = v));
        panel.add(new SlateSlider(0, y, w, Component.translatable("slate_chat.settings.opacity"), -0.1, 1.0, 0.05, cfg.opacity < 0 ? -0.1 : cfg.opacity,
            v -> v < 0 ? Component.translatable("slate_chat.settings.vanilla").getString() : "%d%%".formatted(Math.round(v * 100)),
            v -> { cfg.opacity = v < 0 ? -1 : v; ChatClient.saveAndApply(); }).compact(true), 0, y);
        y += 24;
        panel.add(new SlateSlider(0, y, half, Component.translatable("slate_chat.settings.width"), 0, 640, 10, cfg.width,
            v -> v <= 0 ? Component.translatable("slate_chat.settings.vanilla").getString() : "%d px".formatted((int) v),
            v -> { cfg.width = (int) v; ChatClient.saveAndApply(); }).compact(true), 0, y);
        panel.add(new SlateSlider(half + 8, y, half, Component.translatable("slate_chat.settings.height"), 0, 400, 10, cfg.height,
            v -> v <= 0 ? Component.translatable("slate_chat.settings.vanilla").getString() : "%d px".formatted((int) v),
            v -> { cfg.height = (int) v; ChatClient.saveAndApply(); }).compact(true), half + 8, y);
        y += 24;
        panel.add(new SlateSlider(0, y, half, Component.translatable("slate_chat.settings.group_window"), 1, 30, 1, cfg.groupWindowMinutes,
            v -> "%d min".formatted((int) v), v -> { cfg.groupWindowMinutes = (int) v; ChatClient.saveAndApply(); }).compact(true), 0, y);
        y += 30;

        y = section(panel, y, w, "slate_chat.settings.section.attention");
        y = toggles(panel, y, half,
            new Opt("slate_chat.settings.mentions", () -> cfg.mentions, v -> cfg.mentions = v),
            new Opt("slate_chat.settings.mention_on_name", () -> cfg.mentionOnName, v -> cfg.mentionOnName = v),
            new Opt("slate_chat.settings.ping_sound", () -> cfg.pingSound, v -> cfg.pingSound = v),
            new Opt("slate_chat.settings.unread_badge", () -> cfg.unreadBadge, v -> cfg.unreadBadge = v));
        panel.add(new SlateSlider(0, y, half, Component.translatable("slate_chat.settings.ping_volume"), 0, 1, 0.05, cfg.pingVolume,
            v -> "%d%%".formatted(Math.round(v * 100)), v -> { cfg.pingVolume = v; ChatConfig.file().save(); }).compact(true), 0, y);
        panel.add(new SlateButton(half + 8, y, half, 20, Component.translatable("slate_chat.settings.test_ping"), ChatClient::ping).icon(Icon.BELL), half + 8, y);
        y += 30;

        y = section(panel, y, w, "slate_chat.settings.section.motion");
        y = toggles(panel, y, half,
            new Opt("slate_chat.settings.animation", () -> cfg.animation, v -> cfg.animation = v),
            new Opt("slate_chat.settings.hover_actions", () -> cfg.hoverActions, v -> cfg.hoverActions = v));
        panel.add(new SlateSlider(0, y, half, Component.translatable("slate_chat.settings.animation_ms"), 50, 500, 10, cfg.animationMs,
            v -> "%d ms".formatted((int) v), v -> { cfg.animationMs = (int) v; ChatConfig.file().save(); }).compact(true), 0, y);
        y += 30;

        y = section(panel, y, w, "slate_chat.settings.section.media");
        y = toggles(panel, y, half,
            new Opt("slate_chat.settings.embed_links", () -> cfg.embedLinks, v -> cfg.embedLinks = v),
            new Opt("slate_chat.settings.emotes", () -> cfg.emotes, v -> cfg.emotes = v));
        final SlateLabel note = new SlateLabel(0, y, w, Component.translatable("slate_chat.settings.embed_links.note")).style(SlateLabel.Style.CAPTION).wrap(true);
        panel.add(note, 0, y);
        y += note.getHeight() + 8;
        panel.add(new SlateSlider(0, y, half, Component.translatable("slate_chat.settings.max_download"), 64, 32768, 64, cfg.maxDownloadKb,
            v -> v >= 1024 ? "%.1f MB".formatted(v / 1024) : "%d KB".formatted((int) v), v -> { cfg.maxDownloadKb = (int) v; ChatClient.saveAndApply(); }).compact(true), 0, y);
        panel.add(new SlateSlider(half + 8, y, half, Component.translatable("slate_chat.settings.thumb_rows"), 3, 12, 1, cfg.thumbRows,
            v -> "%d".formatted((int) v), v -> { cfg.thumbRows = (int) v; ChatClient.saveAndApply(); }).compact(true), half + 8, y);
        y += 24;
        panel.add(new SlateSlider(0, y, half, Component.translatable("slate_chat.settings.max_record"), 5, 120, 5, cfg.maxRecordSeconds,
            v -> "%d s".formatted((int) v), v -> { cfg.maxRecordSeconds = (int) v; ChatConfig.file().save(); }).compact(true), 0, y);
        panel.add(new SlateButton(half + 8, y, half, 20, Component.translatable("slate_chat.settings.clear_media_cache"), () -> {
            MediaCache.clearDisk();
            SlateToasts.show(Component.translatable("slate_chat.settings.cleared"), null, Icon.TRASH);
        }).icon(Icon.TRASH), half + 8, y);
        y += 24;
        // A recorded GIF: its length, and its width (a wider one is a larger file, and is sent smaller if it must be).
        panel.add(new SlateSlider(0, y, half, Component.translatable("slate_chat.settings.gif_seconds"), 2, 15, 1, cfg.gifSeconds,
            v -> "%d s".formatted((int) v), v -> { cfg.gifSeconds = (int) v; ChatConfig.file().save(); }).compact(true), 0, y);
        panel.add(new SlateSlider(half + 8, y, half, Component.translatable("slate_chat.settings.gif_width"), 240, 960, 40, cfg.gifWidth,
            v -> "%d px".formatted((int) v), v -> { cfg.gifWidth = (int) v; ChatConfig.file().save(); }).compact(true), half + 8, y);
        y += 30;

        y = section(panel, y, w, "slate_chat.settings.section.history");
        y = toggles(panel, y, half,
            new Opt("slate_chat.settings.restore_history", () -> cfg.restoreHistory, v -> cfg.restoreHistory = v),
            new Opt("slate_chat.settings.typing", () -> cfg.typingIndicator, v -> cfg.typingIndicator = v),
            new Opt("slate_chat.settings.system_tab", () -> cfg.systemTab, v -> cfg.systemTab = v));
        panel.add(new SlateSlider(0, y, half, Component.translatable("slate_chat.settings.history_size"), 0, 1000, 10, cfg.historySize,
            v -> v <= 0 ? Component.translatable("slate_chat.settings.off").getString() : "%d".formatted((int) v),
            v -> { cfg.historySize = (int) v; ChatClient.saveAndApply(); }).compact(true), 0, y);
        panel.add(new SlateButton(half + 8, y, half, 20, Component.translatable("slate_chat.settings.clear_history"), () ->
            SlateModal.confirmDanger(Component.translatable("slate_chat.settings.clear_history"), Component.translatable("slate_chat.settings.clear_history.body"),
                Component.translatable("slate_chat.settings.clear_history"), () -> {
                    ChatHistory.clearCurrent();
                    SlateToasts.show(Component.translatable("slate_chat.settings.cleared"), null, Icon.TRASH);
                })).variant(SlateButton.Variant.DANGER).icon(Icon.TRASH), half + 8, y);
        y += 30;
        panel.setContentHeight(y + 8);
    }

    private record Opt(String key, BooleanSupplier get, Consumer<Boolean> set) {}

    private int section(final SlateScrollPanel panel, final int y, final int w, final String key) {
        panel.add(new SlateSeparator(0, y, w, Component.translatable(key)), 0, y);
        return y + 16;
    }

    /** Two toggles per row. */
    private int toggles(final SlateScrollPanel panel, int y, final int half, final Opt... opts) {
        for (int i = 0; i < opts.length; i++) {
            final Opt o = opts[i];
            final int x = (i % 2) * (half + 8);
            panel.add(new SlateToggle(x, y, half, Component.translatable(o.key()), o.get().getAsBoolean(), v -> { o.set().accept(v); ChatClient.saveAndApply(); }), x, y);
            if (i % 2 == 1 || i == opts.length - 1) y += 22;
        }
        return y + 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
