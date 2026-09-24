package dev.fallingcloud.slate.building.client.hud;

import dev.fallingcloud.slate.building.client.gfx.UiDraw;
import dev.fallingcloud.slate.building.client.input.BuildKeys;
import dev.fallingcloud.slate.building.client.mode.ClientModeState;
import dev.fallingcloud.slate.building.client.wheel.WheelConfig;
import dev.fallingcloud.slate.building.config.HudSettings;
import dev.fallingcloud.slate.building.net.OpResult;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.ops.ModeKind;
import dev.fallingcloud.slate.building.ops.ModeParam;
import dev.fallingcloud.slate.building.ops.ModeParams;
import dev.fallingcloud.slate.building.ops.OpMessages;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Anchor;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The building-mode HUD (design §5), drawn in {@code HUD_RENDER} from {@link ClientModeState}:
 * <ul>
 *   <li>a compact chip at {@code hud.anchor} with the mode icon and name, live stats ("12 × 4 × 8 · 384 blocks ·
 *       384/512 Oak Planks") and key hints for the current selection step;</li>
 *   <li>a progress bar inside the chip while the server executes an operation;</li>
 *   <li>an action-bar style result line ("Filled 384 — press U to undo") that fades after a few seconds.</li>
 * </ul>
 * The chip slides in when a mode activates, resizes smoothly as its text changes and fades out when the mode ends.
 * Stats, key hints and notices come from the mode controller ({@link ClientModeState#stats()}, {@link ClientModeState#hints()},
 * {@link ClientModeState#notice()}): the same plan the ghosts show.
 *
 * <p>Layout: a top-anchored chip never reaches under the toasts at the top right (vanilla's and Slate's): a centred
 * chip stays centred and its width is clamped to the room left of them, so on a narrow GUI its stats wrap onto more
 * lines and its hints onto a second row. Text is only ever drawn whole (or ellipsised), never clipped mid-word, also
 * while the chip is animating to a new size.</p>
 */
public final class ModeHud {

    private static final int RESULT_MS = 4000;
    private static final int PAD = 5;
    /** Header row: 12 px icon, then the name. */
    private static final int ICON_W = 16;
    /** A stat after another one on the same row: a dot, then the text. */
    private static final int DOT_W = 9;
    private static final int HINT_GAP = 8;
    private static final int STAT_ROW_H = 10, HINT_ROW_H = 13, MAX_HINT_ROWS = 2;
    /** Room the toasts take at the top right, in GUI pixels (Slate's toasts: 160 wide, 8 from the edge) plus a gap. */
    private static final int TOAST_RESERVE = SlateToasts.WIDTH + 8 + 6;
    /** A centred chip narrower than this is not worth keeping centred: it moves left of the toasts instead. */
    private static final int MIN_CENTRED_W = 120;
    private static final int MARGIN = 6;

    private static final Anim shown = new Anim(0, 200, Ease.OUT_CUBIC);
    private static final Anim width = new Anim(0, 160, Ease.OUT_CUBIC);
    private static final Anim height = new Anim(0, 160, Ease.OUT_CUBIC);
    private static final Anim progressAnim = new Anim(0, 200, Ease.OUT_CUBIC);
    private static final Anim progressShown = new Anim(0, 160, Ease.OUT_CUBIC);
    private static @Nullable BuildMode lastMode;
    private static boolean initialised;

    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        SlateEvents.HUD_RENDER.register(ModeHud::render);
    }

    // ------------------------------------------------------------------ render

    private static void render(final GuiGraphics g, final float partialTick) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || mc.options.hideGui) return;
        final HudSettings hs = WheelConfig.hud();
        final BuildMode mode = ClientModeState.current();
        // The swap wheel's title sits where a top chip would; the chip steps aside while the wheel is open.
        shown.set(hs.enabled && mode != null && !dev.fallingcloud.slate.building.client.wheel.WheelOverlay.INSTANCE.isOpen());
        if (mode != null) lastMode = mode;
        final float a = shown.get();
        if (a > 0.01f && lastMode != null) renderChip(g, lastMode, a, hs);
        if (hs.actionBar) renderResult(g);
    }

    /** One piece of text placed in the chip, relative to the content origin. */
    private record Placed(Component source, FormattedCharSequence text, int x, int y, int w) {}

    /** One key hint placed in the chip, relative to the chip's top left. */
    private record PlacedHint(Hint hint, int x, int y, int w) {}

    /**
     * Where the chip may go on a {@code sw}-wide screen (chip coordinates, i.e. already divided by the HUD scale).
     *
     * @param maxW       widest the chip may get
     * @param rightLimit the chip's right edge stays left of this (the toast column for a top chip)
     * @param centred    centred on the screen (else placed by its anchor)
     */
    private record Frame(int maxW, int rightLimit, boolean centred) {

        static Frame of(final int sw, final float scale, final Anchor anchor) {
            int maxW = Math.min(sw - MARGIN * 2, Math.max(280, sw * 11 / 20));
            int rightLimit = sw - 2;
            boolean centred = anchor.fx == 0.5f;
            if (anchor.fy <= 0f) {
                rightLimit = sw - (int) Math.ceil(TOAST_RESERVE / scale);
                if (centred) {
                    final int symmetric = (rightLimit - 2 - sw / 2) * 2;    // x() keeps 2 px off the limit
                    if (symmetric >= MIN_CENTRED_W) maxW = Math.min(maxW, symmetric);
                    else centred = false;                            // too narrow to centre: sit left of the toasts
                }
                if (!centred) maxW = Math.min(maxW, rightLimit - MARGIN);
            }
            return new Frame(Math.max(60, maxW), rightLimit, centred);
        }

        int x(final int sw, final Anchor anchor, final int w) {
            final int x = centred ? sw / 2 - w / 2 : anchor.x(sw, 0, w) + (anchor.fx == 0 ? MARGIN : anchor.fx == 1 ? -MARGIN : 0);
            return Mth.clamp(x, 2, Math.max(2, rightLimit - w - 2));
        }
    }

    private static void renderChip(final GuiGraphics g, final BuildMode mode, final float a, final HudSettings hs) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean vanilla = t.isVanilla();
        final float scale = WheelConfig.hudScale();
        final int sw = Math.round(g.guiWidth() / scale), sh = Math.round(g.guiHeight() / scale);
        final Anchor anchor = Anchor.parse(hs.anchor, Anchor.TOP);
        final Frame frame = Frame.of(sw, scale, anchor);
        final int inner = frame.maxW() - PAD * 2;

        // ---- line 1 (+ wrapped stat rows): icon, name, stats separated by dots
        final List<Placed> texts = new ArrayList<>();
        final Component title = mode.name();
        final int titleRoom = Math.max(8, inner - ICON_W);
        final FormattedCharSequence titleText = SlateDraw.truncate(title, titleRoom);
        final int titleW = Math.min(SlateDraw.width(title), titleRoom);
        int cx = ICON_W + titleW;
        int row = 0;
        int contentW = cx;
        final List<int[]> dots = new ArrayList<>();                 // {x, row} of each separator dot
        for (final Component s : stats(mode)) {
            final int sWidth = SlateDraw.width(s);
            final boolean rowEmpty = row > 0 && cx == ICON_W;
            final int need = (rowEmpty ? 0 : DOT_W) + sWidth;
            if (!rowEmpty && cx + need > inner) {                   // wrap under the name
                row++;
                cx = ICON_W;
            }
            final boolean first = row > 0 && cx == ICON_W;
            if (!first) {
                dots.add(new int[] {cx, row});
                cx += DOT_W;
            }
            final int room = inner - cx;
            final int w = Math.min(sWidth, room);
            texts.add(new Placed(s, SlateDraw.truncate(s, room), cx, row * STAT_ROW_H, w));
            cx += w;
            contentW = Math.max(contentW, cx);
        }
        final int statRows = row + 1;

        // ---- key hints: most important first, whole hints only, up to two rows
        final List<PlacedHint> hints = new ArrayList<>();
        final int hintsTop = 5 + statRows * STAT_ROW_H + 2;
        int hx = 0, hintRow = 0;
        for (final Hint h : hints(mode)) {
            final int w = UiDraw.hintWidth(h.key, h.label);
            if (w > inner) continue;                                 // never cut a hint: leave it out
            if (hx > 0 && hx + HINT_GAP + w > inner) {
                if (hintRow + 1 >= MAX_HINT_ROWS) break;
                hintRow++;
                hx = 0;
            }
            if (hx > 0) hx += HINT_GAP;
            hints.add(new PlacedHint(h, hx, hintsTop + hintRow * HINT_ROW_H, w));
            hx += w;
            contentW = Math.max(contentW, hx);
        }
        final int targetW = contentW + PAD * 2;
        final int targetH = hints.isEmpty() ? 5 + statRows * STAT_ROW_H + 3 : hintsTop + (hintRow + 1) * HINT_ROW_H + 1;
        if (width.get() < 1f) { width.snap(targetW); height.snap(targetH); }
        width.set(targetW);
        height.set(targetH);
        final int w = Math.round(width.get());
        final int h = Math.round(height.get());

        // ---- place the chip
        final int slide = Math.round((1f - a) * -8f * (anchor.fy <= 0.5f ? 1 : -1));
        final int x = frame.x(sw, anchor, w);
        final int y = Mth.clamp(anchor.y(sh, 0, h) + (anchor.fy == 0 ? MARGIN : anchor.fy == 1 ? -MARGIN - 40 : 0), 2, Math.max(2, sh - h - 2)) + slide;

        g.pose().pushPose();
        g.pose().scale(scale, scale, 1f);
        UiDraw.pill(g, x, y, w, h, a);
        final int ox = x + PAD, oy = y + 5;
        final int right = x + w - PAD + 1;                          // content must end left of this (animated size)
        final int bottom = y + h;
        Icons.draw(g, mode.icon(), ox, oy - 1, 12, Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.accent(), a));
        drawFitting(g, titleText, title, ox + ICON_W, oy, titleW, right, Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.text(), a), vanilla);
        final int dotCol = Colors.scaleAlpha(vanilla ? 0xFF808080 : p.textDim(), a);
        for (final int[] d : dots) {
            final int dy = oy + d[1] * STAT_ROW_H;
            if (ox + d[0] + DOT_W <= right && dy + 8 <= bottom) SlateDraw.rect(g, ox + d[0] + 3, dy + 3, 2, 2, dotCol);
        }
        final int statCol = Colors.scaleAlpha(vanilla ? 0xFFD0D0D0 : p.textMuted(), a);
        for (final Placed s : texts) {
            if (oy + s.y + 8 > bottom) continue;                     // its row is not open yet
            drawFitting(g, s.text, s.source, ox + s.x, oy + s.y, s.w, right, statCol, vanilla);
        }
        for (final PlacedHint ph : hints) {
            final int hy = y + ph.y;
            // Whole hints only: one that does not fit the (still resizing) chip yet waits until it does.
            if (ox + ph.x + ph.w > right || hy + UiDraw.KEYCAP_H > bottom) continue;
            UiDraw.hint(g, ph.hint.key, ph.hint.label, ox + ph.x, hy, a);
        }
        // Progress: a thin accent bar along the chip's bottom edge.
        final ClientModeState.Progress progress = ClientModeState.progress();
        progressShown.set(progress != null);
        if (progress != null) progressAnim.set(progress.fraction());
        final float ps = progressShown.get();
        if (ps > 0.01f) {
            final int bw = w - 4;
            SlateDraw.rect(g, x + 2, y + h - 3, bw, 2, Colors.scaleAlpha(vanilla ? 0xFF303030 : p.surfaceActive(), a * ps));
            SlateDraw.rect(g, x + 2, y + h - 3, Math.round(bw * Mth.clamp(progressAnim.get(), 0f, 1f)), 2,
                Colors.scaleAlpha(vanilla ? 0xFF55FF55 : p.accent(), a * ps));
        }
        g.pose().popPose();
    }

    /**
     * Draws {@code text} (laid out {@code w} wide at {@code x}) when it fits left of {@code right}; while the chip is
     * narrower than its content (animating), the text is shortened with an ellipsis instead of being cut.
     */
    private static void drawFitting(final GuiGraphics g, final FormattedCharSequence text, final @Nullable Component source,
                                    final int x, final int y, final int w, final int right, final int color, final boolean shadow) {
        if (x + w <= right) {
            g.drawString(SlateDraw.font(), text, x, y, color, shadow);
            return;
        }
        final int room = right - x;
        if (room < 12 || source == null) return;
        g.drawString(SlateDraw.font(), SlateDraw.truncate(source, room), x, y, color, shadow);
    }

    // ------------------------------------------------------------------ stats

    /**
     * The chip's stats, one entry per dot-separated item: the controller's live numbers for the pending selection
     * (the same plan the ghosts show, counted against what is carried), the progress of a running operation, or a
     * toggle mode's state.
     */
    private static List<Component> stats(final BuildMode mode) {
        final List<Component> out = new ArrayList<>();
        final ClientModeState.Progress progress = ClientModeState.progress();
        if (progress != null) {
            out.add(Component.translatable("slate_building.ui.hud.progress", progress.done(), progress.total()));
            return out;
        }
        if (mode.kind() == ModeKind.TOGGLE) {
            final ModeParams params = ClientModeState.params(mode);
            final ModeParam first = mode.params().isEmpty() ? null : mode.params().get(0);
            if (first != null) out.add(valueText(first, params.get(first.id())));
            if (ClientModeState.symmetry() == null) out.add(Component.translatable("slate_building.ui.hud.symmetry_off"));
            return out;
        }
        final ClientModeState.Stats s = ClientModeState.stats();
        final Palette p = Theme.current().palette();
        final boolean vanilla = Theme.current().isVanilla();
        if (s.hasBox()) out.add(s.sizeText());
        if (s.radius() > 0) {
            out.add(s.height() > 0
                ? Component.translatable("slate_building.stats.radius_height", s.radius(), s.height())
                : Component.translatable("slate_building.stats.radius", s.radius()));
        }
        if (mode.kind() == ModeKind.MEASURE) {
            if (s.hasBox() && s.volume() > 1) out.add(Component.translatable("slate_building.stats.volume", s.volume()));
        } else if (s.planned()) {
            out.add(Component.translatable("slate_building.stats.blocks", s.blocks()));
            if (!s.creative() && !s.materials().isEmpty()) {
                final ClientModeState.MaterialNeed top = s.materials().get(0);
                final MutableComponent m = Component.translatable("slate_building.stats.material",
                    Math.min(top.available(), top.needed()), top.needed(), top.name());
                if (!top.enough() && !s.supplyLink()) m.withColor((vanilla ? 0xFFAA00 : p.warning()) & 0xFFFFFF);
                if (s.materials().size() > 1) m.append(Component.translatable("slate_building.stats.more_materials", s.materials().size() - 1));
                out.add(m);
            }
        } else if (s.hasBox() && s.volume() > 1) {
            out.add(Component.translatable("slate_building.stats.volume", s.volume()));
        }
        if (s.distance() > 0) {
            out.add(Component.translatable("slate_building.stats.distance", String.format(java.util.Locale.ROOT, "%.1f", s.distance())));
        }
        if (s.error() != null && ClientModeState.pending() != ClientModeState.Pending.FIRST_ANCHOR) {
            out.add(s.error().copy().withColor((vanilla ? 0xFF5555 : p.danger()) & 0xFFFFFF));
        }
        return out;
    }

    private static Component valueText(final ModeParam p, final Object value) {
        return switch (p.type()) {
            case BOOL -> Component.empty().append(p.displayName()).append(": ")
                .append(Component.translatable(Boolean.TRUE.equals(value) ? "options.on" : "options.off"));
            case INT -> Component.empty().append(p.displayName()).append(" " + value);
            case CHOICE -> p.optionName(String.valueOf(value));
        };
    }

    // ------------------------------------------------------------------ hints

    private record Hint(String key, Component label) {}

    private static List<Hint> hints(final BuildMode mode) {
        final List<Hint> out = new ArrayList<>();
        final ClientModeState.Pending pending = ClientModeState.pending();
        if (ClientModeState.progress() != null || pending == ClientModeState.Pending.APPLYING) {
            final String cancel = UiDraw.keyName(BuildKeys.CANCEL);
            if (!cancel.isEmpty()) out.add(new Hint(cancel, Component.translatable("slate_building.ui.hint.stop")));
            return out;
        }
        // The mode controller's hints follow its state machine exactly (and the confirm / nudge settings).
        for (final ClientModeState.Hint h : ClientModeState.hints()) out.add(new Hint(h.key().getString(), h.action()));
        final String exit = UiDraw.keyName(BuildKeys.EXIT_MODE);
        if (mode.kind() != ModeKind.TOGGLE && !exit.isEmpty()) {
            out.add(new Hint(exit, Component.translatable("slate_building.ui.hint.exit")));
        } else if (exit.isEmpty() && pending == ClientModeState.Pending.NONE) {
            final String menu = UiDraw.keyName(BuildKeys.BUILD_MENU);
            if (!menu.isEmpty()) out.add(new Hint(menu, Component.translatable("slate_building.ui.hint.menu")));
        }
        return out;
    }

    // ------------------------------------------------------------------ result line

    private static void renderResult(final GuiGraphics g) {
        final OpResult r = ClientModeState.lastResult();
        final ClientModeState.Notice notice = ClientModeState.notice();
        // The newer of the last result and the last notice (a refusal, "nothing to undo", ...) owns the line.
        final boolean showNotice = notice != null && (r == null || notice.atMs() > ClientModeState.lastResultAtMs());
        if (r == null && !showNotice) return;
        final long age = Util.getMillis() - (showNotice ? notice.atMs() : ClientModeState.lastResultAtMs());
        if (age > RESULT_MS) return;
        final float in = Mth.clamp(age / 150f, 0f, 1f);
        final float out = Mth.clamp((RESULT_MS - age) / 600f, 0f, 1f);
        final float a = Theme.current().motion() <= 0 ? 1f : Math.min(in, out);
        if (a <= 0.01f) return;
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean vanilla = t.isVanilla();

        final MutableComponent line = Component.empty();
        final Icon icon;
        final int ic;
        Component undoHint = null;
        String hintKey = "";
        if (showNotice) {
            line.append(notice.text());
            switch (notice.severity()) {
                case ERROR -> { icon = Icon.ERROR; ic = vanilla ? 0xFFFF5555 : p.danger(); }
                case WARNING -> { icon = Icon.WARNING; ic = vanilla ? 0xFFFFAA00 : p.warning(); }
                default -> { icon = Icon.INFO; ic = vanilla ? 0xFFFFFFFF : p.accent(); }
            }
        } else {
            // describe() translates the server's "lang:" arguments (block and tool names); refusals are errors.
            final boolean error = OpMessages.isError(r);
            final boolean changed = r.placed() + r.broken() > 0;
            final boolean ok = !error && (changed || r.skipped() == 0);
            line.append(OpMessages.describe(r));
            final int skipped = error ? 0 : unmentionedSkips(r);
            if (skipped > 0) {
                line.append(Component.literal("  ")).append(Component.translatable("slate_building.ui.result.skipped", skipped)
                    .withColor((vanilla ? 0xFFAA00 : p.warning()) & 0xFFFFFF));
            }
            final String undo = UiDraw.keyName(BuildKeys.UNDO);
            final BuildMode mode = BuildModes.byId(r.mode());
            // Only a result that changed something offers undo (else U would revert the operation before it).
            final boolean undoable = ok && changed && mode != null && mode.changesWorld() && ClientModeState.undoCount() > 0;
            // "[U] Undo" with a bound undo key, else "[R] Undo in menu" (undo is one click in the build menu).
            hintKey = undo.isEmpty() ? UiDraw.keyName(BuildKeys.BUILD_MENU) : undo;
            undoHint = !undoable ? null
                : Component.translatable(undo.isEmpty() ? "slate_building.ui.result.undo_menu" : "slate_building.ui.result.undo_key");
            icon = error ? Icon.ERROR : ok ? Icon.CHECK : Icon.WARNING;
            ic = error ? (vanilla ? 0xFFFF5555 : p.danger()) : ok ? (vanilla ? 0xFF55FF55 : p.success()) : (vanilla ? 0xFFFFAA00 : p.warning());
        }

        final int hintW = undoHint == null ? 0 : 10 + (hintKey.isEmpty() ? 0 : UiDraw.keycapWidth(hintKey) + 3) + SlateDraw.width(undoHint);
        // A long refusal on a narrow GUI is shortened with an ellipsis rather than running off the screen.
        final int textW = Math.min(SlateDraw.width(line), Math.max(40, g.guiWidth() - 8 - 18 - 8 - hintW));
        final int w = 18 + textW + hintW + 8;
        final int h = 16;
        final int x = g.guiWidth() / 2 - w / 2;
        final int y = g.guiHeight() - 76 + Math.round((1f - in) * 4f);
        UiDraw.pill(g, x, y, w, h, a);
        Icons.draw(g, icon, x + 5, y + 4, 8, Colors.scaleAlpha(ic, a));
        g.drawString(SlateDraw.font(), SlateDraw.truncate(line, textW), x + 17, y + 4, Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.text(), a), vanilla);
        if (undoHint != null) {
            int hx = x + 17 + textW + 10;
            if (!hintKey.isEmpty()) hx += UiDraw.keycap(g, hintKey, hx, y + 3, a) + 3;
            g.drawString(SlateDraw.font(), undoHint, hx, y + 4, Colors.scaleAlpha(vanilla ? 0xFFA0A0A0 : p.textMuted(), a), vanilla);
        }
    }

    /**
     * Skipped positions the result's own message does not already state: {@code .skipped} results say "N left alone"
     * and {@code .short} ones say how many lacked materials (args: changed, missing, skipped), so the suffix only adds
     * what is left.
     */
    private static int unmentionedSkips(final OpResult r) {
        final String key = r.messageKey();
        if (key.endsWith(".skipped")) return 0;
        if (key.endsWith(".short")) {
            if (r.args().size() < 3) return 0;
            try {
                return Math.max(0, Integer.parseInt(r.args().get(2)));
            } catch (final NumberFormatException e) {
                return 0;
            }
        }
        return Math.max(0, r.skipped());
    }

    /** Whether notices go to this HUD's result line (else the mode controller uses the vanilla action bar). */
    public static boolean showsNotices() {
        final HudSettings hs = WheelConfig.hud();
        return initialised && hs.actionBar;
    }

    // ------------------------------------------------------------------ dev harness

    /**
     * Dev harness: how a chip whose content wants {@code contentWidth} is placed on a {@code guiWidth}-wide GUI at
     * {@code hud.scale} 1 with the default TOP anchor: {x, width, left edge of the toast column}.
     */
    public static int[] debugTopLayout(final int guiWidth, final int contentWidth) {
        final Frame f = Frame.of(guiWidth, 1f, Anchor.TOP);
        final int w = Math.min(f.maxW(), contentWidth);
        return new int[] {f.x(guiWidth, Anchor.TOP, w), w, guiWidth - TOAST_RESERVE};
    }

    private ModeHud() {}
}
