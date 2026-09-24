package dev.fallingcloud.slate.building.client.hud;

import dev.fallingcloud.slate.building.client.gfx.UiDraw;
import dev.fallingcloud.slate.building.client.input.BuildKeys;
import dev.fallingcloud.slate.building.client.mode.ClientModeState;
import dev.fallingcloud.slate.building.client.wheel.WheelConfig;
import dev.fallingcloud.slate.building.client.wheel.WheelSources;
import dev.fallingcloud.slate.building.config.HudSettings;
import dev.fallingcloud.slate.building.config.ModeSettings;
import dev.fallingcloud.slate.building.net.OpResult;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.ops.ModeKind;
import dev.fallingcloud.slate.building.ops.ModeParam;
import dev.fallingcloud.slate.building.ops.ModeParams;
import dev.fallingcloud.slate.building.variant.Variant;
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
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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
 * Stats come from {@link #statsProvider} when the mode controller installs one (exact plan counts), else they are
 * derived from the anchors and the crosshair.
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
        final List<Hint> hints = hints(mc, mode);
        final ClientModeState.Progress progress = ClientModeState.progress();

        int line1 = 16 + SlateDraw.width(title);
        for (final Component s : stats) line1 += 9 + SlateDraw.width(s);
        int line2 = 0;
        for (final Hint h : hints) line2 += (line2 > 0 ? 8 : 0) + UiDraw.hintWidth(h.key, h.label);
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
        final Stats s = currentStats(mc);
        if (s == null) return out;
        if (s.size() != null) {
            out.add(Component.translatable("slate_building.ui.hud.size", s.size().getX(), s.size().getY(), s.size().getZ()));
            if (mode.kind() == ModeKind.MEASURE) {
                final double d = Math.sqrt(sq(s.size().getX() - 1) + sq(s.size().getY() - 1) + sq(s.size().getZ() - 1));
                out.add(Component.translatable("slate_building.ui.hud.distance", String.format(java.util.Locale.ROOT, "%.1f", d)));
            }
        }
        if (s.blocks() > 0 && mode.kind() != ModeKind.MEASURE) out.add(Component.translatable("slate_building.ui.hud.blocks", s.blocks()));
        if (s.blocks() > 0 && mode.places() && mc.player != null && !mc.player.getAbilities().instabuild) {
            final Component mat = materials(mc.player, s.blocks());
            if (mat != null) out.add(mat);
        }
        if (s.note() != null) out.add(s.note());
        return out;
    }

    private static int sq(final int v) { return v * v; }

    private static @Nullable Stats currentStats(final Minecraft mc) {
        final Supplier<Stats> provider = statsProvider;
        if (provider != null) {
            try {
                return provider.get();
            } catch (final RuntimeException e) {
                return null;
            }
        }
        final List<BlockPos> anchors = ClientModeState.anchors();
        if (anchors.isEmpty()) return null;
        final BlockPos a = anchors.get(0);
        BlockPos b = anchors.size() > 1 ? anchors.get(1) : null;
        if (b == null && ClientModeState.pending() == ClientModeState.Pending.FIRST_ANCHOR
            && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            b = hit.getBlockPos();
        }
        if (b == null) return null;
        final Vec3i size = new Vec3i(Math.abs(b.getX() - a.getX()) + 1, Math.abs(b.getY() - a.getY()) + 1, Math.abs(b.getZ() - a.getZ()) + 1);
        final long vol = (long) size.getX() * size.getY() * size.getZ();
        return new Stats(size, (int) Math.min(Integer.MAX_VALUE, vol), null);
    }

    /** "384/512 Oak Planks" for the held material, warning-coloured when short. */
    private static @Nullable Component materials(final LocalPlayer player, final int need) {
        final ItemStack held = player.getMainHandItem();
        final Optional<Variant> v = WheelSources.get().identify(held);
        if (v.isEmpty()) return null;
        int have = 0;
        final Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            final ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            final Optional<Variant> sv = WheelSources.get().identify(s);
            if (sv.isPresent() && sv.get().material() == v.get().material()) have += s.getCount();
        }
        final MutableComponent c = Component.translatable("slate_building.ui.hud.materials", have, need, v.get().material().getName());
        if (have < need) c.withColor(Theme.current().palette().warning() & 0xFFFFFF);
        return c;
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
        final String use = UiDraw.keyName(mc.options.keyUse);
        final String attack = UiDraw.keyName(mc.options.keyAttack);
        final ModeSettings ms = dev.fallingcloud.slate.building.SlateBuilding.config().modes;
        final String confirm = ms != null && !ms.confirmWithRightClick ? UiDraw.keyName(BuildKeys.CONFIRM) : use;
        final List<Hint> out = new ArrayList<>();
        final ClientModeState.Pending pending = ClientModeState.pending();
        if (ClientModeState.progress() != null || pending == ClientModeState.Pending.APPLYING) {
            final String cancel = UiDraw.keyName(BuildKeys.CANCEL);
            if (!cancel.isEmpty()) out.add(new Hint(cancel, Component.translatable("slate_building.ui.hint.stop")));
            return out;
        }
        switch (mode.kind()) {
            case AREA, MOVE, MEASURE -> {
                switch (pending) {
                    case NONE -> out.add(new Hint(use, Component.translatable("slate_building.ui.hint.corner_a")));
                    case FIRST_ANCHOR -> {
                        out.add(new Hint(use, Component.translatable("slate_building.ui.hint.corner_b")));
                        out.add(new Hint(attack, Component.translatable("slate_building.ui.hint.cancel")));
                    }
                    case SELECTED -> {
                        if (mode.kind() == ModeKind.MEASURE) {
                            out.add(new Hint(attack, Component.translatable("slate_building.ui.hint.clear")));
                        } else {
                            out.add(new Hint(confirm, Component.translatable(mode.kind() == ModeKind.MOVE
                                ? "slate_building.ui.hint.pick_destination" : "slate_building.ui.hint.apply")));
                            out.add(new Hint(attack, Component.translatable("slate_building.ui.hint.cancel")));
                            out.add(new Hint(Component.translatable("slate_building.ui.key.ctrl_scroll").getString(),
                                Component.translatable("slate_building.ui.hint.resize")));
                        }
                    }
                    case DESTINATION, PREVIEW -> {
                        out.add(new Hint(confirm, Component.translatable("slate_building.ui.hint.place")));
                        out.add(new Hint(attack, Component.translatable("slate_building.ui.hint.cancel")));
                    }
                    default -> {}
                }
            }
            case POINT -> {
                if (pending == ClientModeState.Pending.PREVIEW) {
                    out.add(new Hint(confirm, Component.translatable("slate_building.ui.hint.apply")));
                    out.add(new Hint(attack, Component.translatable("slate_building.ui.hint.cancel")));
                } else {
                    out.add(new Hint(use, Component.translatable("slate_building.ui.hint.preview")));
                }
            }
            case TOGGLE -> {
                final String recentre = UiDraw.keyName(BuildKeys.CONFIRM);
                if (!recentre.isEmpty()) out.add(new Hint(recentre, Component.translatable("slate_building.ui.hint.recentre")));
                out.add(new Hint(UiDraw.keyName(mc.options.keyUse), Component.translatable("slate_building.ui.hint.build_mirrored")));
            }
        }
        final String exit = UiDraw.keyName(BuildKeys.EXIT_MODE);
        if (!exit.isEmpty()) out.add(new Hint(exit, Component.translatable("slate_building.ui.hint.exit")));
        else if (pending == ClientModeState.Pending.NONE) {
            final String menu = UiDraw.keyName(BuildKeys.BUILD_MENU);
            if (!menu.isEmpty()) out.add(new Hint(menu, Component.translatable("slate_building.ui.hint.menu")));
        }
        return out;
    }

    // ------------------------------------------------------------------ result line

    private static void renderResult(final GuiGraphics g, final Minecraft mc) {
        final OpResult r = ClientModeState.lastResult();
        if (r == null) return;
        final long age = Util.getMillis() - ClientModeState.lastResultAtMs();
        if (age > RESULT_MS) return;
        final float in = Mth.clamp(age / 150f, 0f, 1f);
        final float out = Mth.clamp((RESULT_MS - age) / 600f, 0f, 1f);
        final float a = Theme.current().motion() <= 0 ? 1f : Math.min(in, out);
        if (a <= 0.01f) return;
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean vanilla = t.isVanilla();

        final MutableComponent msg = Component.translatable(r.messageKey(), r.args().toArray());
        final boolean ok = r.placed() + r.broken() > 0 || r.skipped() == 0;
        final MutableComponent line = Component.empty().append(msg);
        if (r.skipped() > 0) {
            line.append(Component.literal("  ")).append(Component.translatable("slate_building.ui.result.skipped", r.skipped())
                .withColor((vanilla ? 0xFFAA00 : p.warning()) & 0xFFFFFF));
        }
        final String undo = UiDraw.keyName(BuildKeys.UNDO);
        final BuildMode mode = BuildModes.byId(r.mode());
        final boolean undoable = ok && mode != null && mode.changesWorld() && ClientModeState.undoCount() > 0;
        // "[U] Undo" with a bound undo key, else "[R] Undo in menu" (undo is one click in the build menu).
        final String hintKey = undo.isEmpty() ? UiDraw.keyName(BuildKeys.BUILD_MENU) : undo;
        final Component undoHint = !undoable ? null
            : Component.translatable(undo.isEmpty() ? "slate_building.ui.result.undo_menu" : "slate_building.ui.result.undo_key");

        final int textW = SlateDraw.width(line);
        final int hintW = undoHint == null ? 0 : 10 + (hintKey.isEmpty() ? 0 : UiDraw.keycapWidth(hintKey) + 3) + SlateDraw.width(undoHint);
        final int w = 18 + textW + hintW + 8;
        final int h = 16;
        final int x = g.guiWidth() / 2 - w / 2;
        final int y = g.guiHeight() - 76 + Math.round((1f - in) * 4f);
        UiDraw.pill(g, x, y, w, h, a);
        final Icon icon = ok ? Icon.CHECK : Icon.WARNING;
        final int ic = ok ? (vanilla ? 0xFF55FF55 : p.success()) : (vanilla ? 0xFFFFAA00 : p.warning());
        Icons.draw(g, icon, x + 5, y + 4, 8, Colors.scaleAlpha(ic, a));
        g.drawString(SlateDraw.font(), line, x + 17, y + 4, Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.text(), a), vanilla);
        if (undoHint != null) {
            int hx = x + 17 + textW + 10;
            if (!hintKey.isEmpty()) hx += UiDraw.keycap(g, hintKey, hx, y + 3, a) + 3;
            g.drawString(SlateDraw.font(), undoHint, hx, y + 4, Colors.scaleAlpha(vanilla ? 0xFFA0A0A0 : p.textMuted(), a), vanilla);
        }
    }

    private ModeHud() {}
}
