package dev.fallingcloud.slate.core.layout.editor;

import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A small multi-line text box for MULTILINE / ACTION_LIST properties: typing, Enter for a new line,
 * arrows/Home/End, Backspace/Delete, Ctrl+V paste, Ctrl+C copies everything, click places the cursor,
 * wheel scrolls. No selection (Slate's text elements are short); long lines scroll horizontally per line.
 */
final class MultiLineField extends SlateWidget {

    private static final int PAD = 4, LINE = 10;

    private String text = "";
    private int cursor;
    private int scrollLine;
    @Nullable private Consumer<String> onChange;
    @Nullable private Component placeholder;

    MultiLineField(final int x, final int y, final int w, final int h, final Component narration) {
        super(x, y, w, h, narration);
        silent();
    }

    MultiLineField text(final String v) {
        text = v == null ? "" : v.replace("\r\n", "\n").replace('\r', '\n');
        cursor = Math.min(cursor, text.length());
        return this;
    }

    MultiLineField onChange(final Consumer<String> c) { this.onChange = c; return this; }

    MultiLineField placeholder(final Component c) { this.placeholder = c; return this; }

    String value() { return text; }

    private List<String> lines() { return Arrays.asList(text.split("\n", -1)); }

    private int[] lineCol(final int pos) {
        int line = 0, col = 0;
        for (int i = 0; i < pos && i < text.length(); i++) {
            if (text.charAt(i) == '\n') { line++; col = 0; } else col++;
        }
        return new int[] { line, col };
    }

    private int posOf(final int line, final int col) {
        final List<String> ls = lines();
        int pos = 0;
        for (int i = 0; i < line && i < ls.size(); i++) pos += ls.get(i).length() + 1;
        final int l = Math.max(0, Math.min(line, ls.size() - 1));
        return pos + Math.max(0, Math.min(col, ls.get(l).length()));
    }

    private int visibleLines() { return Math.max(1, (getHeight() - PAD * 2) / LINE); }

    private void changed() {
        if (onChange != null) onChange.accept(text);
    }

    private void insert(final String s) {
        if (s == null || s.isEmpty()) return;
        final String clean = s.replace("\r\n", "\n").replace('\r', '\n');
        text = text.substring(0, cursor) + clean + text.substring(cursor);
        cursor += clean.length();
        changed();
    }

    private void ensureCursorVisible() {
        final int line = lineCol(cursor)[0];
        if (line < scrollLine) scrollLine = line;
        else if (line >= scrollLine + visibleLines()) scrollLine = line - visibleLines() + 1;
        scrollLine = Math.max(0, Math.min(scrollLine, Math.max(0, lines().size() - 1)));
    }

    // ------------------------------------------------------------------ input

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        final List<String> ls = lines();
        final int line = Math.max(0, Math.min(ls.size() - 1, scrollLine + (int) ((mouseY - getY() - PAD) / LINE)));
        final String l = ls.get(line);
        final int col = SlateDraw.font().plainSubstrByWidth(l, Math.max(0, (int) (mouseX - getX() - PAD))).length();
        cursor = posOf(line, col);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (!isFocused() || !this.active || !this.visible) return false;
        final boolean ctrl = EditorKeys.ctrl();
        switch (keyCode) {
            case EditorKeys.LEFT -> { if (cursor > 0) cursor--; }
            case EditorKeys.RIGHT -> { if (cursor < text.length()) cursor++; }
            case EditorKeys.UP -> { final int[] lc = lineCol(cursor); if (lc[0] > 0) cursor = posOf(lc[0] - 1, lc[1]); else cursor = 0; }
            case EditorKeys.DOWN -> { final int[] lc = lineCol(cursor); if (lc[0] < lines().size() - 1) cursor = posOf(lc[0] + 1, lc[1]); else cursor = text.length(); }
            case EditorKeys.HOME -> { final int[] lc = lineCol(cursor); cursor = posOf(lc[0], 0); }
            case EditorKeys.END -> { final int[] lc = lineCol(cursor); cursor = posOf(lc[0], Integer.MAX_VALUE / 2); }
            case EditorKeys.BACKSPACE -> {
                if (cursor > 0) { text = text.substring(0, cursor - 1) + text.substring(cursor); cursor--; changed(); }
            }
            case EditorKeys.DELETE -> {
                if (cursor < text.length()) { text = text.substring(0, cursor) + text.substring(cursor + 1); changed(); }
            }
            case EditorKeys.ENTER, EditorKeys.KP_ENTER -> insert("\n");
            case EditorKeys.V -> { if (!ctrl) return false; insert(Minecraft.getInstance().keyboardHandler.getClipboard()); }
            case EditorKeys.C -> { if (!ctrl) return false; Minecraft.getInstance().keyboardHandler.setClipboard(text); }
            case EditorKeys.A -> { if (!ctrl) return false; cursor = text.length(); }
            default -> { return false; }
        }
        ensureCursorVisible();
        return true;
    }

    @Override
    public boolean charTyped(final char c, final int modifiers) {
        if (!isFocused() || !this.active || !this.visible || c < 32) return false;
        insert(String.valueOf(c));
        ensureCursorVisible();
        return true;
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (!contains(mouseX, mouseY)) return false;
        final int max = Math.max(0, lines().size() - visibleLines());
        if (max == 0) return false;
        scrollLine = Math.max(0, Math.min(max, scrollLine - (int) Math.signum(scrollY)));
        return true;
    }

    // ------------------------------------------------------------------ render

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, false);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, true);
    }

    private void draw(final GuiGraphics g, final boolean vanilla) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        final float foc = focus() + (isFocused() ? 1 : 0), hov = hover();
        if (vanilla) {
            SlateDraw.vanillaTextField(g, x, y, w, h, isFocused());
        } else {
            final int fill = Colors.lerp(Colors.lerp(p.bg2(), p.surface(), hov), p.surface(), Math.min(1, foc));
            final int border = Colors.lerp(Colors.lerp(p.border(), p.borderStrong(), hov), p.accent(), Math.min(1, foc));
            SlateDraw.pixelRound(g, x, y, w, h, fill, t.radius());
            SlateDraw.outline(g, x, y, w, h, border, t.radius());
        }
        final int fg = vanilla ? 0xFFE0E0E0 : p.text();
        SlateDraw.scissor(g, x + 1, y + 1, w - 2, h - 2);
        final List<String> ls = lines();
        final int[] lc = lineCol(cursor);
        final int innerW = w - PAD * 2 - 2;
        int ly = y + PAD;
        if (text.isEmpty() && placeholder != null && !isFocused()) {
            g.drawString(SlateDraw.font(), SlateDraw.truncate(placeholder, innerW), x + PAD, ly, p.textDim(), false);
        }
        for (int i = scrollLine; i < ls.size() && ly < y + h - PAD; i++) {
            final String line = ls.get(i);
            // Horizontal scroll per line so the cursor stays in view.
            int shift = 0;
            if (i == lc[0]) {
                final int cw = SlateDraw.width(line.substring(0, Math.min(lc[1], line.length())));
                if (cw > innerW) shift = cw - innerW;
            }
            final String shown = shift > 0 ? line.substring(SlateDraw.font().plainSubstrByWidth(line, shift).length()) : line;
            g.drawString(SlateDraw.font(), SlateDraw.font().plainSubstrByWidth(shown, innerW), x + PAD, ly, fg, vanilla);
            if (isFocused() && i == lc[0] && (Clock.nowMs() / 500) % 2 == 0) {
                final int cx = x + PAD + SlateDraw.width(line.substring(0, Math.min(lc[1], line.length()))) - shift;
                g.fill(cx, ly - 1, cx + 1, ly + 9, fg);
            }
            ly += LINE;
        }
        SlateDraw.unscissor(g);
        final int max = Math.max(0, ls.size() - visibleLines());
        if (max > 0) {
            final int trackH = h - 4, barH = Math.max(8, trackH * visibleLines() / ls.size());
            final int by = y + 2 + (trackH - barH) * scrollLine / max;
            g.fill(x + w - 3, by, x + w - 2, by + barH, Colors.withAlpha(p.textDim(), 0xA0));
        }
    }
}
