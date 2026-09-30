package dev.fallingcloud.slate.chat.client;

import dev.fallingcloud.slate.chat.ChatConfig;
import dev.fallingcloud.slate.chat.SlateChat;
import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.client.media.GifLibraryScreen;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.event.SlateKeys;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.action.ActionType;
import dev.fallingcloud.slate.core.layout.action.ActionType.Arg;
import dev.fallingcloud.slate.core.media.Attachment;
import dev.fallingcloud.slate.core.media.GifStore;
import dev.fallingcloud.slate.core.media.MediaCache;
import dev.fallingcloud.slate.core.module.SlateModule;
import dev.fallingcloud.slate.core.screen.ScreenIds;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Client bootstrap of Slate Chat: config -> media policy, the record key, event wiring (join/leave for
 * history and state, tick for recording/typing, HUD for the recording overlay), hub entries and dev-mode
 * actions, and the per-message hook the ChatComponent mixin calls.
 */
public final class ChatClient {

    private static boolean initialised;

    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        ChatConfig.file();
        applyConfig();
        MediaCache.init();
        SlateKeys.register(VoiceRecorder.RECORD_KEY);
        SlateKeys.register(GifRecorder.KEY);
        ScreenIds.register(ChatSettingsScreen.class, "slate_chat:settings", "Chat settings");
        CoreActions.SCREEN_FACTORIES.put("slate_chat:settings", ChatSettingsScreen::new);
        SlateEvents.CLIENT_TICK_END.register(() -> {
            VoiceRecorder.tick();
            GifRecorder.tick();
            TypingIndicator.tick();
        });
        SlateEvents.HUD_RENDER.register((g, pt) -> VoiceRecorder.renderHud(g));
        SlateEvents.CLIENT_JOINED_SERVER.register(() -> {
            ChatRenderState.reset();
            ChatChannels.reset();
            ChatHistory.restore();
        });
        SlateEvents.CLIENT_LEFT_SERVER.register(() -> {
            ChatMeta.clear();
            TypingIndicator.clear();
            ChatChannels.reset();
            ChatHistory.onLeave();
            ChatRenderState.reset();
            ChatLayout.resetGrouping();
        });
        Theme.onChange(ChatClient::rescale);
        SlateChat.LOGGER.info("[Slate Chat] client ready");
    }

    /** Pushes the config's media policy into Core's statics. */
    public static void applyConfig() {
        final ChatConfig cfg = ChatConfig.get();
        Attachment.embedLinks = cfg.embedLinks;
        Attachment.thumbRows = Math.max(3, Math.min(12, cfg.thumbRows));
        MediaCache.maxDownloadBytes = Math.max(64, cfg.maxDownloadKb) * 1024;
    }

    public static void saveAndApply() {
        ChatConfig.file().save();
        applyConfig();
        rescale();
    }

    /** Re-wraps the chat (row geometry may have changed). */
    public static void rescale() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) mc.execute(() -> mc.gui.getChat().rescaleChat());
    }

    /** Line budget for vanilla's trimmed list (headers and cards eat lines). */
    public static int maxLines() {
        return Math.max(100, Math.min(3000, ChatConfig.get().historySize * 4));
    }

    // ------------------------------------------------------------------ per-message hook

    /** {@code ChatComponent.addMessage} HEAD: meta, animation, GIF recents, mention ping, unread, history. */
    public static void onIncoming(final Component content, final GuiMessageTag tag, final boolean chatFocused) {
        final ChatMeta.Meta meta = ChatMeta.prepare(content, tag);
        if (meta.history || meta.synthetic) return;
        final ChatConfig cfg = ChatConfig.get();
        ChatRenderState.onNewMessage();
        for (final Attachment a : meta.attachments) {
            if (a.kind() == Attachment.Kind.IMAGE_URL && a.isGif()) GifStore.noteRecent(a.url());
        }
        if (meta.mention && cfg.pingSound) ping();
        if (!chatFocused && meta.hasSender() && !meta.self) ChatRenderState.noteUnread();
        ChatHistory.append(meta, content);
    }

    public static void ping() {
        final ChatConfig cfg = ChatConfig.get();
        final float vol = (float) Math.max(0, Math.min(1, cfg.pingVolume));
        if (vol <= 0) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, vol));
    }

    // ------------------------------------------------------------------ hub + actions

    public static List<SlateModule.HubEntry> hubEntries() {
        final Minecraft mc = Minecraft.getInstance();
        final List<SlateModule.HubEntry> out = new ArrayList<>();
        out.add(new SlateModule.HubEntry(Component.translatable("slate_chat.hub.open_chat"), Icon.CHAT, () -> {
            if (mc.level != null) mc.setScreen(new ChatScreen(""));
        }));
        out.add(new SlateModule.HubEntry(Component.translatable("slate_chat.hub.settings"), Icon.SETTINGS, () -> mc.setScreen(new ChatSettingsScreen(mc.screen))));
        out.add(new SlateModule.HubEntry(Component.translatable("slate_chat.hub.gifs"), Icon.GIF, () -> mc.setScreen(new GifLibraryScreen(mc.screen, ChatSend::text))));
        return out;
    }

    public static List<ActionType> actions() {
        final Minecraft mc = Minecraft.getInstance();
        final List<ActionType> out = new ArrayList<>();
        out.add(new ActionType("slate_chat:open_chat", Component.translatable("slate_chat.action.open_chat"),
            List.of(Arg.text("text", Component.translatable("slate_chat.action.arg.text"), "")),
            a -> { if (mc.level != null) mc.setScreen(new ChatScreen(a.getOrDefault("text", ""))); }));
        out.add(new ActionType("slate_chat:toggle_timestamps", Component.translatable("slate_chat.action.toggle_timestamps"), List.of(),
            a -> { ChatConfig.get().timestamps = !ChatConfig.get().timestamps; saveAndApply(); }));
        out.add(new ActionType("slate_chat:open_gifs", Component.translatable("slate_chat.action.open_gifs"), List.of(),
            a -> mc.setScreen(new GifLibraryScreen(mc.screen, ChatSend::text))));
        out.add(new ActionType("slate_chat:open_settings", Component.translatable("slate_chat.action.open_settings"), List.of(),
            a -> mc.setScreen(new ChatSettingsScreen(mc.screen))));
        return out;
    }

    private ChatClient() {}
}
