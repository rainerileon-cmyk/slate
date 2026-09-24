package dev.fallingcloud.slate.building.client.menu;

import dev.fallingcloud.slate.building.client.gfx.UiDraw;
import dev.fallingcloud.slate.building.client.input.BuildKeys;
import dev.fallingcloud.slate.building.client.mode.ClientModeState;
import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.config.ServerOps;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Rows of the build menu's mode table (a {@link SlateList} with plain rows, drawn here): icon, name, one-line
 * description, keybind cap, required tool + tier chip, lock icon + reason when locked, and the active mode's accent
 * bar and tint. Rows stagger in when the menu opens, locked rows shake when clicked, the active row's highlight
 * fades between rows.
 */
final class ModeRows implements SlateList.RowRenderer<BuildMode> {

    static final int ROW_H = 24;
    private static final int STAGGER_MS = 16, ENTER_MS = 200, SHAKE_MS = 320;

    /** Why each mode is locked right now (null = usable); refreshed once per tick by the screen. */
    private final Map<String, Component> locks = new HashMap<>();
    private final Map<String, Anim> activeAnims = new HashMap<>();
    private final Map<String, Long> shakes = new HashMap<>();
    private final BiConsumer<BuildMode, Integer> onClick;
    private long openedAt = Clock.nowMs();

    /** {@code onClick(mode, button)}. */
    ModeRows(final BiConsumer<BuildMode, Integer> onClick) {
        this.onClick = onClick;
    }

    void replayEntrance() {
        openedAt = Clock.nowMs();
    }

    void skipEntrance() {
        openedAt = 0;
    }

    /** Recomputes lock reasons (toolbox, server rules). */
    void refreshLocks(final List<BuildMode> modes) {
        locks.clear();
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        final ToolboxAccess.Capabilities caps = ToolboxAccess.of(mc.player);
        final ServerOps ops = BuildingServerSettings.effective(mc.player).ops();
        for (final BuildMode m : modes) {
            final Component reason;
            if (!ops.enabled) reason = Component.translatable("slate_building.ui.menu.ops_disabled");
            else if (ops.disabledModes != null && ops.disabledModes.contains(m.id())) reason = Component.translatable("slate_building.ui.menu.mode_disabled");
            else reason = caps.lockReason(m);
            if (reason != null) locks.put(m.id(), reason);
        }
    }

    @Nullable Component lock(final BuildMode m) {
        return locks.get(m.id());
    }

    void shake(final BuildMode m) {
        shakes.put(m.id(), Clock.nowMs());
    }

    @Override
    public boolean click(final BuildMode item, final int index, final int x, final int y, final int w, final int h,
                         final double mouseX, final double mouseY, final int button) {
        if (button != 0) return false;             // right click: the list selects (options only)
        onClick.accept(item, button);
        return true;
    }

    @Override
    public void render(final GuiGraphics g, final BuildMode mode, final int index, final int x0, final int y0, final int w, final int h,
                       final boolean hovered, final boolean selected, final int mouseX, final int mouseY) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean vanilla = t.isVanilla();

        // Entrance: rows fade and slide in from the right one after another.
        float enter = 1f;
        if (openedAt > 0 && t.motion() > 0) {
            final float el = (Clock.nowMs() - openedAt - index * STAGGER_MS * t.motion()) / (ENTER_MS * t.motion());
            enter = Ease.OUT_CUBIC.apply(Mth.clamp(el, 0f, 1f));
        }
        if (enter <= 0.01f) return;
        int x = x0 + Math.round((1f - enter) * 10f);
        final Long shakeAt = shakes.get(mode.id());
        if (shakeAt != null) {
            final long age = Clock.nowMs() - shakeAt;
            if (age > SHAKE_MS || t.motion() <= 0) shakes.remove(mode.id());
            else x += Math.round((float) Math.sin(age / 22.0) * 3f * (1f - age / (float) SHAKE_MS));
        }
        final float a = enter;

        final Component lock = locks.get(mode.id());
        final boolean locked = lock != null;
        final boolean active = ClientModeState.current() == mode;
        final Anim act = activeAnims.computeIfAbsent(mode.id(), k -> new Anim(active ? 1f : 0f, 180, Ease.OUT_CUBIC));
        act.set(active);
        final float on = act.get();

        // Row card.
        if (vanilla) {
            if (hovered || selected) g.fill(x, y0, x + w, y0 + h, Colors.scaleAlpha(selected ? 0x50000000 : 0x28FFFFFF, a));
            if (on > 0.01f) g.fill(x, y0, x + w, y0 + h, Colors.scaleAlpha(Colors.withAlpha(p.accent(), 0x38), a * on));
            if (selected) SlateDraw.outline(g, x, y0, w, h, Colors.scaleAlpha(0xFFA0A0A0, a), 0);
            if (on > 0.01f) SlateDraw.rect(g, x, y0, 2, h, Colors.scaleAlpha(0xFFFFFFFF, a * on));
        } else {
            int fill = selected ? p.surfaceActive() : hovered ? p.surfaceHover() : 0;
            if (fill != 0) SlateDraw.pixelRound(g, x, y0, w, h, Colors.scaleAlpha(fill, a), t.radius());
            if (on > 0.01f) {
                SlateDraw.pixelRound(g, x, y0, w, h, Colors.scaleAlpha(Colors.withAlpha(p.accent(), 0x26), a * on), t.radius());
                SlateDraw.rect(g, x, y0 + 3, 2, h - 6, Colors.scaleAlpha(p.accent(), a * on));
            }
            if (selected) SlateDraw.outline(g, x, y0, w, h, Colors.scaleAlpha(Colors.withAlpha(p.borderStrong(), 0xC0), a), t.radius());
        }

        final float dim = locked ? 0.5f : 1f;
        // Icon (lock badge on top when locked).
        final int iconCol = locked ? (vanilla ? 0xFF808080 : p.textDim())
            : Colors.lerp(vanilla ? 0xFFE0E0E0 : p.textMuted(), vanilla ? 0xFFFFFFFF : p.accent(), Math.max(on, hovered ? 0.6f : 0f));
        Icons.draw(g, mode.icon(), x + 6, y0 + 4, 16, Colors.scaleAlpha(iconCol, a));
        if (locked) {
            SlateDraw.rect(g, x + 15, y0 + 13, 10, 10, Colors.scaleAlpha(vanilla ? 0xFF202020 : p.bg2(), a));
            Icons.draw(g, Icon.LOCK, x + 16, y0 + 14, 8, Colors.scaleAlpha(vanilla ? 0xFFFFAA00 : p.warning(), a));
        }

        // Right side: [key] [tool chip].
        int right = x + w - 5;
        final int chipY = y0 + (h - 12) / 2;
        if (mode.tool() != null) {
            final int cw = 26;
            right -= cw;
            toolChip(g, mode, right, chipY, cw, a * (locked ? 0.9f : 1f), vanilla, p, locked);
            right -= 4;
        }
        final String key = UiDraw.keyName(BuildKeys.forMode(mode));
        if (!key.isEmpty()) {
            final int kw = UiDraw.keycapWidth(key);
            right -= kw;
            UiDraw.keycap(g, key, right, y0 + (h - UiDraw.KEYCAP_H) / 2, a * dim);
            right -= 4;
        }

        // Name + description (lock reason in place of the description when locked).
        final int tx = x + 28;
        final int tw = Math.max(10, right - tx - 2);
        final int nameCol = locked ? (vanilla ? 0xFF909090 : p.textMuted())
            : Colors.lerp(vanilla ? 0xFFFFFFFF : p.text(), vanilla ? 0xFFFFFF80 : p.accent(), on * 0.85f);
        g.drawString(SlateDraw.font(), SlateDraw.truncate(mode.name(), tw), tx, y0 + 3, Colors.scaleAlpha(nameCol, a), vanilla);
        final Component sub = locked ? lock : mode.description();
        final int subCol = locked ? (vanilla ? 0xFFFFAA00 : Colors.mix(p.warning(), p.textDim(), 0.25f)) : (vanilla ? 0xFFA0A0A0 : p.textDim());
        final FormattedCharSequence line = SlateDraw.truncate(sub, tw);
        g.drawString(SlateDraw.font(), line, tx, y0 + 13, Colors.scaleAlpha(subCol, a), vanilla);
    }

    /** Tool glyph tinted by the required tier + tier pips. */
    private static void toolChip(final GuiGraphics g, final BuildMode mode, final int x, final int y, final int w, final float a,
                                 final boolean vanilla, final Palette p, final boolean locked) {
        if (vanilla) {
            SlateDraw.rect(g, x, y, w, 12, Colors.scaleAlpha(0x70000000, a));
        } else {
            SlateDraw.pixelRound(g, x, y, w, 12, Colors.scaleAlpha(p.bg2(), a), 2);
        }
        final int tier = Math.max(1, mode.minTier());
        final int col = UiDraw.tierColor(tier);
        Icons.draw(g, mode.tool().icon(), x + 2, y + 2, 8, Colors.scaleAlpha(locked ? Colors.mix(col, 0xFF808080, 0.5f) : col, a));
        UiDraw.pips(g, x + 13, y + 4, tier, 4, col, a);
    }

    /** Tooltip lines for a row: description, tool requirement, keybind, lock. */
    List<Component> tooltip(final BuildMode m) {
        final List<Component> lines = new java.util.ArrayList<>();
        lines.add(m.name());
        lines.add(m.description().copy().withStyle(net.minecraft.ChatFormatting.GRAY));
        if (m.tool() != null) {
            lines.add(Component.translatable("slate_building.ui.menu.requires", Component.translatable("slate_building.tool_tiered",
                dev.fallingcloud.slate.building.toolbox.ToolTier.byLevel(m.minTier()).displayName(), m.tool().displayName()))
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
        final Component lock = locks.get(m.id());
        if (lock != null) lines.add(lock.copy().withStyle(net.minecraft.ChatFormatting.GOLD));
        return lines;
    }
}
