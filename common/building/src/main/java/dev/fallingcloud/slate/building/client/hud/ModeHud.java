package dev.fallingcloud.slate.building.client.hud;

import dev.fallingcloud.slate.building.client.gfx.UiDraw;
import dev.fallingcloud.slate.building.client.input.BuildKeys;
import dev.fallingcloud.slate.building.client.mode.ClientModeState;
import dev.fallingcloud.slate.building.client.wheel.WheelConfig;
import dev.fallingcloud.slate.building.config.HudSettings;
import dev.fallingcloud.slate.building.config.ModeSettings;
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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
 * {@link ClientModeState#notice()}): the same plan the ghosts show. {@link #statsProvider} can override the stats.
 */
public final class ModeHud {

    /** What the chip shows about the pending selection. */
    public record Stats(@Nullable Vec3i size, int blocks, @Nullable Component note) {}

    private static final int RESULT_MS = 4000;
    private static final int PAD = 5;

    private static @Nullable Supplier<Stats> statsProvider;
    private static final Anim shown = new Anim(0, 200, Ease.OUT_CUBIC);
    private static final Anim width = new Anim(0, 160, Ease.OUT_CUBIC);
    private static final Anim progressAnim = new Anim(0, 200, Ease.OUT_CUBIC);
    private static final Anim progressShown = new Anim(0, 160, Ease.OUT_CUBIC);
    private static @Nullable BuildMode lastMode;
    private static boolean initialised;

    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        SlateEvents.HUD_RENDER.register(ModeHud::render);
    }

    /**
     * Lets the mode controller supply exact stats for the pending selection (planned change count, notes); null
     * restores the built-in estimate from the anchors.
     */
    public static void statsProvider(final @Nullable Supplier<Stats> provider) {
        statsProvider = provider;
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
        if (a > 0.01f && lastMode != null) renderChip(g, mc, lastMode, a, hs);
        if (hs.actionBar) renderResult(g, mc);
    }

    private static void renderChip(final GuiGraphics g, final Minecraft mc, final BuildMode mode, final float a, final HudSettings hs) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean vanilla = t.isVanilla();
        final float scale = WheelConfig.hudScale();
        final int sw = Math.round(g.guiWidth() / scale), sh = Math.round(g.guiHeight() / scale);

        final Component title = mode.name();
        final List<Component> stats = stats(mc, mode);
        final List<Hint> allHints = hints(mc, mode);
        final ClientModeState.Progress progress = ClientModeState.progress();

        int line1 = 16 + SlateDraw.width(title);
        for (final Component s : stats) line1 += 9 + SlateDraw.width(s);
        // The chip stays compact: hints are listed most important first, so the ones that do not fit are dropped.
        final int maxInner = Math.max(line1, Math.min(sw - 16, Math.max(280, sw * 11 / 20)) - PAD * 2);
        final List<Hint> hints = new ArrayList<>(allHints.size());
        int line2 = 0;
        for (final Hint h : allHints) {
            final int next = line2 + (line2 > 0 ? 8 : 0) + UiDraw.hintWidth(h.key, h.label);
            if (next > maxInner && !hints.isEmpty()) break;
            hints.add(h);
            line2 = next;
        }
        final int target = Math.max(line1, line2) + PAD * 2;
        if (width.get() < 1f) width.snap(target);
        width.set(target);
        final int w = Math.round(width.get());
        final int h = hints.isEmpty() ? 18 : 31;

        final Anchor anchor = Anchor.parse(hs.anchor, Anchor.TOP);
        final int margin = 6;
        final int slide = Math.round((1f - a) * -8f * (anchor.fy <= 0.5f ? 1 : -1));
        final int x = Mth.clamp(anchor.x(sw, 0, w) + (anchor.fx == 0 ? margin : anchor.fx == 1 ? -margin : 0), 2, Math.max(2, sw - w - 2));
        final int y = Mth.clamp(anchor.y(sh, 0, h) + (anchor.fy == 0 ? margin : anchor.fy == 1 ? -margin - 40 : 0), 2, Math.max(2, sh - h - 2)) + slide;

        g.pose().pushPose();
        g.pose().scale(scale, scale, 1f);
        UiDraw.pill(g, x, y, w, h, a);
        SlateDraw.scissor(g, Math.round(x * scale), Math.round(y * scale), Math.round(w * scale), Math.round(h * scale));
        // Line 1: accent icon, name, stats separated by dots.
        int cx = x + PAD;
        final int ty = y + 5;
        Icons.draw(g, mode.icon(), cx, ty - 1, 12, Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.accent(), a));
        cx += 16;
        g.drawString(SlateDraw.font(), title, cx, ty, Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.text(), a), vanilla);
        cx += SlateDraw.width(title);
        for (final Component s : stats) {
            SlateDraw.rect(g, cx + 3, ty + 3, 2, 2, Colors.scaleAlpha(vanilla ? 0xFF808080 : p.textDim(), a));
            cx += 9;
            g.drawString(SlateDraw.font(), s, cx, ty, Colors.scaleAlpha(vanilla ? 0xFFD0D0D0 : p.textMuted(), a), vanilla);
            cx += SlateDraw.width(s);
        }
        // Line 2: key hints.
        int hx = x + PAD;
        for (final Hint hint : hints) {
            hx += UiDraw.hint(g, hint.key, hint.label, hx, y + 17, a) + 8;
        }
        // Progress: a thin accent bar along the chip's bottom edge.
        progressShown.set(progress != null);
        if (progress != null) progressAnim.set(progress.fraction());
        final float ps = progressShown.get();
        if (ps > 0.01f) {
            final int bw = w - 4;
            SlateDraw.rect(g, x + 2, y + h - 3, bw, 2, Colors.scaleAlpha(vanilla ? 0xFF303030 : p.surfaceActive(), a * ps));
            SlateDraw.rect(g, x + 2, y + h - 3, Math.round(bw * Mth.clamp(progressAnim.get(), 0f, 1f)), 2,
                Colors.scaleAlpha(vanilla ? 0xFF55FF55 : p.accent(), a * ps));
        }
        SlateDraw.unscissor(g);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ stats

    private static List<Component> stats(final Minecraft mc, final BuildMode mode) {
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
        final Supplier<Stats> provider = statsProvider;
        if (provider != null) {
            final Stats s;
            try {
                s = provider.get();
            } catch (final RuntimeException e) {
                return out;
            }
            if (s == null) return out;
            if (s.size() != null) out.add(Component.translatable("slate_building.ui.hud.size", s.size().getX(), s.size().getY(), s.size().getZ()));
            if (s.blocks() > 0) out.add(Component.translatable("slate_building.ui.hud.blocks", s.blocks()));
            if (s.note() != null) out.add(s.note());
            return out;
        }
        // The mode controller's live numbers: the same plan the ghosts show, counted against what is carried.
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

    private static List<Hint> hints(final Minecraft mc, final BuildMode mode) {
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

    private static void renderResult(final GuiGraphics g, final Minecraft mc) {
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
            final boolean error = OpMessages.isError(r);
            final boolean ok = !error && (r.placed() + r.broken() > 0 || r.skipped() == 0);
            line.append(OpMessages.describe(r));
            if (!error && r.skipped() > 0) {
                line.append(Component.literal("  ")).append(Component.translatable("slate_building.ui.result.skipped", r.skipped())
                    .withColor((vanilla ? 0xFFAA00 : p.warning()) & 0xFFFFFF));
            }
            final String undo = UiDraw.keyName(BuildKeys.UNDO);
            final BuildMode mode = BuildModes.byId(r.mode());
            final boolean undoable = ok && mode != null && mode.changesWorld() && ClientModeState.undoCount() > 0;
            // "[U] Undo" with a bound undo key, else "[R] Undo in menu" (undo is one click in the build menu).
            hintKey = undo.isEmpty() ? UiDraw.keyName(BuildKeys.BUILD_MENU) : undo;
            undoHint = !undoable ? null
                : Component.translatable(undo.isEmpty() ? "slate_building.ui.result.undo_menu" : "slate_building.ui.result.undo_key");
            icon = error ? Icon.ERROR : ok ? Icon.CHECK : Icon.WARNING;
            ic = error ? (vanilla ? 0xFFFF5555 : p.danger()) : ok ? (vanilla ? 0xFF55FF55 : p.success()) : (vanilla ? 0xFFFFAA00 : p.warning());
        }

        final int textW = SlateDraw.width(line);
        final int hintW = undoHint == null ? 0 : 10 + (hintKey.isEmpty() ? 0 : UiDraw.keycapWidth(hintKey) + 3) + SlateDraw.width(undoHint);
        final int w = 18 + textW + hintW + 8;
        final int h = 16;
        final int x = g.guiWidth() / 2 - w / 2;
        final int y = g.guiHeight() - 76 + Math.round((1f - in) * 4f);
        UiDraw.pill(g, x, y, w, h, a);
        Icons.draw(g, icon, x + 5, y + 4, 8, Colors.scaleAlpha(ic, a));
        g.drawString(SlateDraw.font(), line, x + 17, y + 4, Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.text(), a), vanilla);
        if (undoHint != null) {
            int hx = x + 17 + textW + 10;
            if (!hintKey.isEmpty()) hx += UiDraw.keycap(g, hintKey, hx, y + 3, a) + 3;
            g.drawString(SlateDraw.font(), undoHint, hx, y + 4, Colors.scaleAlpha(vanilla ? 0xFFA0A0A0 : p.textMuted(), a), vanilla);
        }
    }

    /** Whether notices go to this HUD's result line (else the mode controller uses the vanilla action bar). */
    public static boolean showsNotices() {
        final HudSettings hs = WheelConfig.hud();
        return initialised && hs.actionBar;
    }

    private ModeHud() {}
}
