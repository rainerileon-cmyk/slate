package dev.fallingcloud.slate.multiplayer.client.ui;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/**
 * A small multi-line text input for the chat: Enter sends, Shift+Enter breaks the line, Ctrl+V pastes
 * text (an image on the clipboard goes to {@link #onPasteImage}), Ctrl+A selects all, arrows/Home/End move
 * the cursor. Grows from one to four lines with the text. Both skins.
 */
public final class ChatInput extends SlateWidget {

    public static final int LINE = 10, PAD_X = 6, PAD_Y = 5, MAX_LINES = 4;

    private final StringBuilder text = new StringBuilder();
    private int cursor;
    private boolean allSelected;
    private final Anim focusAnim = new Anim(0, 160, Ease.OUT_CUBIC);
    @Nullable private Runnable onSend;
    @Nullable private Runnable onPasteImage;
    @Nullable private Consumer<String> onChange;
    private Component placeholder = Component.empty();
    private int maxLength = 4000;
    private final int baseY;

    public ChatInput(final int x, final int y, final int width) {
        super(x, y, width, LINE + PAD_Y * 2, Component.translatable("slate_multiplayer.chat.input"));
        this.baseY = y;
    }

    public ChatInput onSend(final Runnable r) { this.onSend = r; return this; }

    public ChatInput onPasteImage(final Runnable r) { this.onPasteImage = r; return this; }

    public ChatInput onChange(final Consumer<String> c) { this.onChange = c; return this; }

    public ChatInput placeholder(final Component c) { this.placeholder = c; return this; }

    public ChatInput maxLength(final int n) { this.maxLength = n; return this; }

    public String value() { return text.toString(); }

    public void setValue(final String v) {
        text.setLength(0);
        text.append(v == null ? "" : v);
        cursor = text.length();
        allSelected = false;
        changed();
        relayout();
    }

    public void clear() { setValue(""); }

    /** Bottom edge stays anchored at the original y; the box grows upward. */
    private void relayout() {
        final int lines = Math.max(1, Math.min(MAX_LINES, lines().size()));
        final int h = lines * LINE + PAD_Y * 2;
        setHeight(h);
        setY(baseY - (h - (LINE + PAD_Y * 2)));
    }

    public int bottom() { return baseY + LINE + PAD_Y * 2; }

    private Font font() { return SlateDraw.font(); }

    private List<FormattedCharSequence> lines() {
        final List<FormattedCharSequence> out = new ArrayList<>();
        for (final String para : text.toString().split("\n", -1)) {
            final List<FormattedCharSequence> wrapped = font().split(Component.literal(para), Math.max(10, getWidth() - PAD_X * 2));
            if (wrapped.isEmpty()) out.add(FormattedCharSequence.EMPTY); else out.addAll(wrapped);
        }
        return out;
    }

    private void changed() {
        if (onChange != null) onChange.accept(text.toString());
    }

    private void insert(final String s) {
        if (s == null || s.isEmpty()) return;
        if (allSelected) { text.setLength(0); cursor = 0; allSelected = false; }
        final String clean = s.replace("\r", "");
        final int room = maxLength - text.length();
        if (room <= 0) return;
        final String ins = clean.length() > room ? clean.substring(0, room) : clean;
        text.insert(cursor, ins);
        cursor += ins.length();
        changed();
        relayout();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean charTyped(final char c, final int modifiers) {
        if (!isFocused() || !this.active || !this.visible) return false;
        if (c < 32) return false;
        insert(String.valueOf(c));
        return true;
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!isFocused() || !this.active || !this.visible) return false;
        final boolean ctrl = Screen.hasControlDown();
        switch (keyCode) {
            case 257, 335 -> {                                                     // enter
                if (Screen.hasShiftDown()) insert("\n");
                else if (onSend != null) onSend.run();
                return true;
            }
            case 259 -> {                                                          // backspace
                if (allSelected) { setValue(""); return true; }
                if (cursor > 0) { text.deleteCharAt(cursor - 1); cursor--; changed(); relayout(); }
                return true;
            }
            case 261 -> {                                                          // delete
                if (allSelected) { setValue(""); return true; }
                if (cursor < text.length()) { text.deleteCharAt(cursor); changed(); relayout(); }
                return true;
            }
            case 263 -> { allSelected = false; cursor = Math.max(0, cursor - 1); return true; }      // left
            case 262 -> { allSelected = false; cursor = Math.min(text.length(), cursor + 1); return true; }   // right
            case 268 -> { allSelected = false; cursor = ctrl ? 0 : lineStart(cursor); return true; }   // home
            case 269 -> { allSelected = false; cursor = ctrl ? text.length() : lineEnd(cursor); return true; }   // end
            case 265 -> { allSelected = false; cursor = lineStart(cursor) > 0 ? lineStart(lineStart(cursor) - 1) : 0; return true; }   // up
            case 264 -> { allSelected = false; final int e = lineEnd(cursor); cursor = e < text.length() ? Math.min(text.length(), e + 1) : e; return true; }   // down
            default -> {}
        }
        if (ctrl) {
            if (keyCode == 65) { allSelected = !text.isEmpty(); return true; }                                   // ctrl+a
            if (keyCode == 86) {                                                                             // ctrl+v
                if (onPasteImage != null && dev.fallingcloud.slate.multiplayer.client.ClipboardImages.hasImage()) { onPasteImage.run(); return true; }
                insert(Minecraft.getInstance().keyboardHandler.getClipboard());
                return true;
            }
            if (keyCode == 67 && allSelected) { Minecraft.getInstance().keyboardHandler.setClipboard(text.toString()); return true; }   // ctrl+c
            if (keyCode == 88 && allSelected) { Minecraft.getInstance().keyboardHandler.setClipboard(text.toString()); setValue(""); return true; }   // ctrl+x
        }
        return false;
    }

    private int lineStart(final int at) {
        final int i = text.lastIndexOf("\n", Math.max(0, at - 1));
        return i < 0 ? 0 : i + 1;
    }

    private int lineEnd(final int at) {
        final int i = text.indexOf("\n", at);
        return i < 0 ? text.length() : i;
    }

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        setFocused(true);
        allSelected = false;
        cursor = text.length();
    }

    @Override
    public void setFocused(final boolean focused) {
        super.setFocused(focused);
        if (!focused) allSelected = false;
    }

    // ------------------------------------------------------------------ render

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        focusAnim.set(isFocused());
        final float foc = focusAnim.get();
        final int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        final int fill = Colors.lerp(Colors.lerp(p.bg2(), p.surface(), hover()), p.surface(), foc);
        final int border = Colors.lerp(Colors.lerp(p.border(), p.borderStrong(), hover()), p.accent(), foc);
        SlateDraw.pixelRound(g, x, y, w, h, fill, t.radius());
        SlateDraw.outline(g, x, y, w, h, border, t.radius());
        drawText(g, p.text(), p.textDim(), false);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        SlateDraw.vanillaTextField(g, getX(), getY(), getWidth(), getHeight(), isFocused());
        drawText(g, 0xFFE0E0E0, 0xFF808080, true);
    }

    private void drawText(final GuiGraphics g, final int fg, final int dim, final boolean shadow) {
        final int x = getX() + PAD_X, y = getY() + PAD_Y;
        if (text.isEmpty()) {
            g.drawString(font(), SlateDraw.truncate(placeholder, getWidth() - PAD_X * 2), x, y, dim, shadow);
            if (isFocused() && (Clock.nowMs() / 500) % 2 == 0) SlateDraw.vline(g, x, y, LINE - 1, fg);
            return;
        }
        final List<FormattedCharSequence> ls = lines();
        final int first = Math.max(0, ls.size() - MAX_LINES);
        if (allSelected) g.fill(x - 1, y - 1, x + getWidth() - PAD_X * 2 + 1, y + Math.min(MAX_LINES, ls.size()) * LINE, Colors.withAlpha(Theme.current().accent(), 0x50));
        int ly = y;
        for (int i = first; i < ls.size(); i++) { g.drawString(font(), ls.get(i), x, ly, fg, shadow); ly += LINE; }
        if (isFocused() && (Clock.nowMs() / 500) % 2 == 0) {
            // Cursor: measure the text of the paragraph up to the cursor, on its wrapped line.
            final int[] pos = cursorPos(ls, first);
            SlateDraw.vline(g, x + pos[0], y + pos[1] * LINE, LINE - 1, fg);
        }
    }

    /** {x offset, visible line index} of the cursor. */
    private int[] cursorPos(final List<FormattedCharSequence> ls, final int first) {
        // Re-wrap paragraph by paragraph to find which visual line holds the cursor.
        int consumed = 0, line = 0;
        for (final String para : text.toString().split("\n", -1)) {
            final List<FormattedCharSequence> wrapped = font().split(Component.literal(para), Math.max(10, getWidth() - PAD_X * 2));
            final int count = Math.max(1, wrapped.size());
            if (cursor <= consumed + para.length()) {
                final int inPara = cursor - consumed;
                int at = 0;
                for (int i = 0; i < count; i++) {
                    final String seg = i < wrapped.size() ? seqString(wrapped.get(i)) : "";
                    final int segLen = seg.length();
                    final boolean last = i == count - 1;
                    if (inPara <= at + segLen || last) {
                        final int xo = font().width(para.substring(at, Math.min(para.length(), Math.max(at, Math.min(inPara, at + segLen)))));
                        return new int[] { xo, Math.max(0, line + i - first) };
                    }
                    at += segLen;
                    if (at < para.length() && para.charAt(at) == ' ') at++;   // the splitter eats the break space
                }
            }
            consumed += para.length() + 1;
            line += count;
        }
        return new int[] { 0, Math.max(0, Math.min(MAX_LINES - 1, ls.size() - 1 - first)) };
    }

    private static String seqString(final FormattedCharSequence seq) {
        final StringBuilder sb = new StringBuilder();
        seq.accept((i, style, cp) -> { sb.appendCodePoint(cp); return true; });
        return sb.toString();
    }
}
