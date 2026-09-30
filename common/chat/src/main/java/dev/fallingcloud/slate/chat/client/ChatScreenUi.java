package dev.fallingcloud.slate.chat.client;

import dev.fallingcloud.slate.chat.ChatConfig;
import dev.fallingcloud.slate.core.client.media.GifLibraryScreen;
import dev.fallingcloud.slate.core.client.media.ImagePickerScreen;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.media.FilePicker;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateSearchField;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Everything Slate adds to vanilla's {@link ChatScreen}: the styled input bar around vanilla's own
 * {@link EditBox} (kept at its vanilla position so {@code CommandSuggestions}, which anchors to the screen
 * bottom, keeps working untouched), the attach / GIF / emote / mic buttons, the channel tabs, the search
 * field (Ctrl+F) and the typing line. Widgets are owned and routed here, NOT registered with the screen:
 * vanilla's focus handling would steal keyboard focus from the input the moment a button is clicked.
 *
 * <p>Layout (GUI px from the bottom): typing line at -40..-31, tabs at -31..-17, bar at -16..-1.</p>
 */
public final class ChatScreenUi {

    public static final int BAR_H = 15;

    @Nullable private static ChatScreenUi current;
    private static boolean searchOpen;
    private static String searchQuery = "";

    private final ChatScreen screen;
    private final EditBox input;
    private final int width, height;
    private final List<SlateWidget> buttons = new ArrayList<>();
    private final ChannelTabs tabs;
    private final SlateSearchField search;
    private final SlateIconButton attach, gif, mic, record;
    @Nullable private final SlateIconButton emote;
    private boolean micHeld;
    private boolean draggingBar;
    private double dragStartY;
    private int dragStartScroll;
    private long lastThreadPoll;
    private int lastThreadCount = -1;

    private ChatScreenUi(final ChatScreen screen, final EditBox input, final int width, final int height) {
        this.screen = screen;
        this.input = input;
        this.width = width;
        this.height = height;
        final ChatConfig cfg = ChatConfig.get();
        final boolean emotesOn = cfg.emotes;
        final int n = emotesOn ? 5 : 4;
        final int buttonsW = n * 16;
        input.setX(8);
        input.setWidth(Math.max(40, width - 8 - 6 - buttonsW - 6));
        int bx = width - 4 - 15;
        final int by = height - 16;
        mic = new SlateIconButton(bx, by, 14, Icon.MIC, Component.translatable(MultiplayerBridge.voiceAvailable() ? "slate_chat.button.voice" : "slate_chat.voice.unavailable"), () -> {});
        buttons.add(mic);
        bx -= 16;
        if (emotesOn) {
            emote = new SlateIconButton(bx, by, 14, Icon.EMOTE, Component.translatable("slate_chat.button.emote"), this::openEmotes);
            buttons.add(emote);
            bx -= 16;
        } else {
            emote = null;
        }
        gif = new SlateIconButton(bx, by, 14, Icon.GIF, Component.translatable("slate_chat.button.gif"), this::openGifs);
        buttons.add(gif);
        bx -= 16;
        attach = new SlateIconButton(bx, by, 14, Icon.ATTACH, Component.translatable("slate_chat.button.attach"), this::openAttach);
        buttons.add(attach);
        bx -= 16;
        record = new SlateIconButton(bx, by, 14, Icon.CAMERA,
            Component.translatable("slate_chat.button.record_gif", GifRecorder.KEY.getTranslatedKeyMessage(), ChatConfig.get().gifSeconds), GifRecorder::startFromChat);
        buttons.add(record);
        for (final SlateWidget b : buttons) b.silent();
        tabs = new ChannelTabs(4, height - 31, width - 8);
        search = new SlateSearchField(4, height - 34, Math.min(240, width - 8), q -> { searchQuery = q; ChatChannels.setSearch(q); });
        search.placeholder(Component.translatable("slate_chat.search.placeholder"));
        search.setValue(searchQuery);
        search.setFocused(searchOpen);
        search.onEscape(this::closeSearch);
        search.onEnter(this::closeSearch);
    }

    /** {@code ChatScreen.init} tail: (re)build for the current size. */
    public static ChatScreenUi build(final ChatScreen screen, final EditBox input, final int width, final int height) {
        current = new ChatScreenUi(screen, input, width, height);
        return current;
    }

    @Nullable
    public static ChatScreenUi current() { return current; }

    /** {@code ChatScreen.removed}. */
    public static void clear() {
        current = null;
        searchOpen = false;
        searchQuery = "";
        ChatChannels.setSearch("");
    }

    public EditBox input() { return input; }

    // ------------------------------------------------------------------ render

    /** The screen body, in vanilla's order: chat panel, our chrome, the input. */
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick, final int guiTicks) {
        final Minecraft mc = Minecraft.getInstance();
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final ChatConfig cfg = ChatConfig.get();
        mc.gui.getChat().render(g, guiTicks, mouseX, mouseY, true);

        // Typing line, just under the panel.
        if (cfg.typingIndicator && TypingIndicator.any()) {
            final Component text = TypingIndicator.text();
            final int ty = height - 40 + 1;
            g.drawString(SlateDraw.font(), Component.empty().append(text).append(TypingIndicator.dots()), 6, ty,
                t.isVanilla() ? 0xFFC0C0C0 : p.textMuted(), t.isVanilla());
        }

        // Input bar
        final int by = height - 16, bh = BAR_H;
        if (t.isVanilla()) {
            g.fill(2, by, width - 2, by + bh, mc.options.getBackgroundColor(Integer.MIN_VALUE));
            SlateDraw.hline(g, 2, by, width - 4, 0x40FFFFFF);
        } else {
            SlateDraw.pixelRound(g, 2, by, width - 4, bh, Colors.withAlpha(p.surface(), 0xF2), t.radius());
            SlateDraw.outline(g, 2, by, width - 4, bh, input.getValue().isEmpty() ? p.border() : p.borderStrong(), t.radius());
        }
        if (input.getValue().isEmpty()) {
            final Component hint = Component.translatable("slate_chat.input.hint", ChatChannels.current().label());
            g.drawString(SlateDraw.font(), SlateDraw.truncate(hint, input.getWidth()), input.getX(), input.getY(), t.isVanilla() ? 0xFF707070 : p.textDim(), false);
        }
        input.render(g, mouseX, mouseY, partialTick);

        if (searchOpen) search.render(g, mouseX, mouseY, partialTick);
        else tabs.render(g, mouseX, mouseY, partialTick);
        mic.toggled(VoiceRecorder.isRecording());
        for (final SlateWidget b : buttons) b.render(g, mouseX, mouseY, partialTick);

        TypingIndicator.onLocalEdit(input.getValue());
        pollThread();
    }

    /** A DM tab shows a thread we only poll; refresh when its message count changed. */
    private void pollThread() {
        if (!ChatChannels.isThread() || ChatChannels.current().thread() == null) { lastThreadCount = -1; return; }
        final long now = System.currentTimeMillis();
        if (now - lastThreadPoll < 1000) return;
        lastThreadPoll = now;
        final int n = MultiplayerBridge.messages(ChatChannels.current().thread()).size();
        if (lastThreadCount >= 0 && n != lastThreadCount) ChatChannels.refresh();
        lastThreadCount = n;
    }

    // ------------------------------------------------------------------ actions

    private void openAttach() {
        Minecraft.getInstance().setScreen(new ImagePickerScreen(screen, ChatSend::imageFile));
    }

    private void openAnyFile() {
        if (!FilePicker.available()) { openAttach(); return; }
        FilePicker.pickFile("Send a file", List.of(), null, ChatSend::anyFile);
    }

    private void openGifs() {
        Minecraft.getInstance().setScreen(new GifLibraryScreen(screen, ChatSend::text));
    }

    private void openEmotes() {
        if (emote == null) return;
        if (Popups.any()) { Popups.closeAll(); return; }
        EmotePickerPopup.open(emote.getX() + 7, emote.getY(), name -> input.insertText(Emotes.token(name) + " "));
    }

    private void toggleSearch() {
        if (searchOpen) closeSearch();
        else {
            searchOpen = true;
            search.setFocused(true);
        }
    }

    private void closeSearch() {
        searchOpen = false;
        search.setFocused(false);
        // Keep the filter if the user typed one; Escape with a query first clears it (SlateSearchField).
        if (search.getValue().isEmpty()) ChatChannels.setSearch("");
    }

    public boolean isSearchOpen() { return searchOpen; }

    // ------------------------------------------------------------------ input routing (from the mixin)

    public boolean mouseClicked(final double mx, final double my, final int button) {
        if (button == 0) {
            final ChatRenderState.ActionRect ar = ChatRenderState.actionAt(mx, my);
            if (ar != null) {
                if (ar.action() == ChatRenderState.Action.SCROLL_BOTTOM) {
                    Minecraft.getInstance().gui.getChat().resetChatScroll();
                } else if (ar.message() != null) {
                    ChatActions.run(ar.action(), ar.message(), input);
                }
                return true;
            }
            final ChatRenderState.ClickRect cr = ChatRenderState.cardAt(mx, my);
            if (cr != null) {
                ChatActions.activate(cr.attachment(), cr.message());
                return true;
            }
            final ChatRenderState.Scrollbar sb = ChatRenderState.scrollbar;
            if (sb != null && sb.contains(mx, my)) {
                draggingBar = true;
                dragStartY = my;
                dragStartScroll = access() == null ? 0 : access().slate$scroll();
                if (my < sb.thumbY0() || my >= sb.thumbY1()) {
                    // Click on the track: jump so the thumb centres on the mouse.
                    final int thumbH = sb.thumbY1() - sb.thumbY0();
                    final double frac = 1 - Math.max(0, Math.min(1, (my - sb.y0() - thumbH / 2.0) / Math.max(1, sb.y1() - sb.y0() - thumbH)));
                    if (access() != null) access().slate$setScroll((int) Math.round(frac * sb.maxScroll()));
                    dragStartScroll = access() == null ? 0 : access().slate$scroll();
                }
                return true;
            }
            if (mic.visible && mic.contains(mx, my)) {
                micHeld = true;
                VoiceRecorder.beginHold();
                return true;
            }
        }
        if (button == 1 && attach.contains(mx, my)) {
            openAnyFile();
            return true;
        }
        for (final SlateWidget b : buttons) {
            if (b != mic && b.mouseClicked(mx, my, button)) return true;
        }
        if (searchOpen) {
            if (search.mouseClicked(mx, my, button)) return true;
        } else if (tabs.mouseClicked(mx, my, button)) {
            return true;
        }
        return false;
    }

    public boolean mouseReleased(final double mx, final double my, final int button) {
        boolean consumed = false;
        if (micHeld) { micHeld = false; VoiceRecorder.endHold(); consumed = true; }
        if (draggingBar) { draggingBar = false; consumed = true; }
        for (final SlateWidget b : buttons) b.mouseReleased(mx, my, button);
        return consumed;
    }

    public boolean mouseDragged(final double mx, final double my, final int button, final double dx, final double dy) {
        if (!draggingBar) return false;
        final ChatRenderState.Scrollbar sb = ChatRenderState.scrollbar;
        final ChatAccess a = access();
        if (sb == null || a == null) return true;
        final int thumbH = sb.thumbY1() - sb.thumbY0();
        final double track = Math.max(1, sb.y1() - sb.y0() - thumbH);
        final double rowsPerPx = sb.maxScroll() / track;
        final int target = (int) Math.round(dragStartScroll - (my - dragStartY) * rowsPerPx);
        a.slate$setScroll(Math.max(0, Math.min(sb.maxScroll(), target)));
        return true;
    }

    public boolean keyPressed(final int key, final int scan, final int mods) {
        if (searchOpen) {
            if (key == 256) { closeSearch(); return true; }
            if (search.keyPressed(key, scan, mods)) return true;
            return key != 258;                                   // swallow everything but Tab
        }
        if (key == 70 && Screen.hasControlDown()) { toggleSearch(); return true; }          // ctrl+F
        if (Screen.isPaste(key) && ChatSend.pasteImage()) return true;   // only when the clipboard holds an image
        if (key == 256 && Popups.any()) { Popups.closeAll(); return true; }
        return false;
    }

    public boolean charTyped(final char c, final int mods) {
        if (!searchOpen) return false;
        search.charTyped(c, mods);
        return true;
    }

    @Nullable
    private static ChatAccess access() {
        final Minecraft mc = Minecraft.getInstance();
        return mc.gui != null && mc.gui.getChat() instanceof ChatAccess a ? a : null;
    }
}
