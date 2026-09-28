package dev.fallingcloud.slate.building.client.wheel;

import dev.fallingcloud.slate.building.client.BuildingIcons;
import dev.fallingcloud.slate.building.client.gfx.ArcRaster;
import dev.fallingcloud.slate.building.client.gfx.PixelCanvas;
import dev.fallingcloud.slate.building.client.gfx.UiDraw;
import dev.fallingcloud.slate.building.client.input.BuildInput;
import dev.fallingcloud.slate.building.client.input.BuildKeys;
import dev.fallingcloud.slate.building.client.input.ExclusiveKeys;
import dev.fallingcloud.slate.building.client.input.KeyClaims;
import dev.fallingcloud.slate.building.client.render.OverlayRenderer;
import dev.fallingcloud.slate.building.config.WheelSettings;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The Alt quick-swap wheel (design §5): a HUD overlay, not a screen, so the player keeps walking while it is open.
 * Hold the swap key with a variant in hand (or, with a hammer in the toolbox, the Builder's Toolbox / a building tool in
 * hand while looking at a variant block) and it opens around a virtual cursor fed by mouse movement (the camera stays still).
 * <ul>
 *   <li>Release the key: apply the hovered slice ({@code SwapHeld} / {@code ChiselHeld}, or {@code ReshapeTarget} /
 *       {@code ChiselTarget} in the world). Tapping without moving does nothing; any nudge of the cursor arms the
 *       centre, so a nudge and release (or coming back to the middle from a slice) picks the full block.</li>
 *   <li>LMB / RMB: previous / next page. Scroll: step along the ring. Hotbar keys 1-9: pick a slice. Esc: closes it unapplied and opens the pause menu as usual.</li>
 *   <li>{@code releaseToSelect = false}: LMB applies (and the wheel stays open), release just closes.</li>
 * </ul>
 * The swap key is claimed through {@link ExclusiveKeys} exactly when this would open, so the six other Alt mods of
 * the DF pack keep their key otherwise. Input comes from {@link BuildInput} at priority 100.
 */
public final class WheelOverlay implements BuildInput.Handler {

    public static final WheelOverlay INSTANCE = new WheelOverlay();

    private static final int BASE_RADIUS = 64;
    /** Cursor travel (GUI px) after which the centre can be picked: a nudge, not a trip to the ring. */
    private static final float ARM_DISTANCE = 6f;

    private final RadialWheel wheel = new RadialWheel();
    private final Anim openAnim = new Anim(0, 180, Ease.OUT_BACK);
    private final Anim fade = new Anim(0, 140, Ease.OUT_CUBIC);
    private final Anim pointer = new Anim(0, 120, Ease.OUT_CUBIC);
    private final Anim labelSwap = new Anim(1, 120, Ease.OUT_CUBIC);

    private boolean open;
    private boolean closing;
    private boolean keyWasDown;
    private @Nullable WheelTarget target;
    private @Nullable WheelPages.WheelSet set;
    private String targetKey = "";
    private int page;
    private float cursorX, cursorY;
    private boolean armed;
    private int lastHover = RadialWheel.NONE;
    /** Preview mode (dev harness): opened programmatically, stays open without the key. */
    private boolean pinned;

    private WheelOverlay() {}

    public static void init() {
        BuildInput.register(INSTANCE);
        // The swap key takes Alt for itself exactly when the wheel would open (and the user wants exclusivity).
        ExclusiveKeys.claim(BuildKeys.SWAP, KeyClaims::swapWanted);
        SlateEvents.CLIENT_TICK_END.register(INSTANCE::tick);
        SlateEvents.HUD_RENDER.register(INSTANCE::render);
        SlateEvents.CLIENT_LEFT_SERVER.register(() -> INSTANCE.close(false));
        WheelConfig.onChange(INSTANCE::rebuild);
    }

    public boolean isOpen() { return open; }

    /** Whether the vanilla crosshair should be hidden (the wheel's centre sits on it). */
    public boolean hidesCrosshair() { return open || (closing && fade.get() > 0.2f); }

    // ------------------------------------------------------------------ lifecycle

    private void tick() {
        final Minecraft mc = Minecraft.getInstance();
        final boolean down = BuildKeys.SWAP.isDown();
        // Vanilla's held-item name would sit on top of the wheel's label; it comes back when the stack changes.
        if (open && mc.gui != null) ((dev.fallingcloud.slate.building.mixin.ui.GuiAccessor) mc.gui).slateBuilding$setToolHighlightTimer(0);
        while (BuildKeys.SWAP.consumeClick()) { /* the wheel works on the held state, not clicks */ }
        if (!open) {
            if (down && !keyWasDown && mc.screen == null && mc.player != null) {
                final WheelTarget t = WheelTarget.resolve();
                if (t != null) openFor(t, false);
            }
        } else if (mc.screen != null || mc.player == null) {
            close(false);
        } else if (!down && !pinned) {
            release();
        } else {
            refresh();
        }
        keyWasDown = down;
    }

    /** Opens the wheel for {@code t}. {@code pin} keeps it open without the key (dev harness previews). */
    public void openFor(final WheelTarget t, final boolean pin) {
        this.target = t;
        this.pinned = pin;
        this.set = WheelPages.overlay(t);
        this.targetKey = keyOf(t);
        this.open = true;
        this.closing = false;
        this.page = 0;
        this.cursorX = this.cursorY = 0;
        this.armed = false;
        this.lastHover = RadialWheel.NONE;
        openAnim.snap(0.001f);
        openAnim.set(1f);
        fade.snap(0f);
        fade.set(1f, 150);
        pointer.snap(0f);
        wheel.hover(RadialWheel.NONE);
        showPage(0, true);
    }

    /** Closes the wheel; {@code apply} applies the hovered slice first. */
    public void close(final boolean apply) {
        if (!open) return;
        if (apply) applyHovered();
        open = false;
        pinned = false;
        closing = true;
        fade.set(0f, 110);
    }

    private void release() {
        close(WheelConfig.wheel().releaseToSelect);
    }

    /** Rebuilds the pages (settings changed); keeps the page when it still exists. */
    private void rebuild() {
        if (!open || target == null) return;
        final String pageKey = currentPage() != null ? Objects.requireNonNull(currentPage()).key() : "";
        set = WheelPages.overlay(target);
        int idx = 0;
        for (int i = 0; i < set.pages().size(); i++) if (set.pages().get(i).key().equals(pageKey)) idx = i;
        showPage(idx, false);
    }

    /** Follows the held stack / looked-at block while open (count changes, material changes, target lost). */
    private void refresh() {
        if (pinned || target == null) return;
        final WheelTarget now = target.held() ? WheelTarget.heldTarget() : WheelTarget.worldTarget();
        if (now == null) { close(false); return; }
        final String key = keyOf(now);
        if (key.equals(targetKey)) return;
        final boolean sameMaterial = now.material() == target.material();
        target = now;
        targetKey = key;
        if (sameMaterial) rebuild();
        else { set = WheelPages.overlay(now); showPage(0, true); }
    }

    private static String keyOf(final WheelTarget t) {
        return t.source() + "|" + t.material() + "|" + t.shape() + "|" + t.count() + "|" + t.slot() + "|" + t.pos();
    }

    private @Nullable WheelPage currentPage() {
        final WheelPages.WheelSet s = set;
        if (s == null || s.pages().isEmpty()) return null;
        return s.pages().get(Mth.clamp(page, 0, s.pages().size() - 1));
    }

    private void showPage(final int index, final boolean animate) {
        final WheelPages.WheelSet s = set;
        if (s == null) return;
        page = s.pages().isEmpty() ? 0 : Math.floorMod(index, s.pages().size());
        final WheelPage p = currentPage();
        final WheelTarget t = target;
        final WheelSlice centre = p != null && p.kind() == WheelSlice.Kind.CHISEL && t != null ? WheelPages.currentSlice(t) : s.full();
        wheel.content(p == null ? List.of() : p.slices(), centre, animate);
        wheel.numbers(WheelConfig.wheel().numberKeys);
        updateHover();
    }

    private void switchPage(final int dir) {
        final WheelPages.WheelSet s = set;
        if (s == null || s.pages().size() < 2) return;
        showPage(page + dir, true);
        labelSwap.snap(0f);
        labelSwap.set(1f);
        WheelActions.tick(dir > 0 ? 1.35f : 1.15f);
    }

    // ------------------------------------------------------------------ selection

    private int deadZone() {
        return Math.round(wheel.innerRadius() * 0.72f);
    }

    private void updateHover() {
        final float d = (float) Math.sqrt(cursorX * cursorX + cursorY * cursorY);
        // Any nudge arms the centre (a tap without moving still does nothing): the full block must be as quick to
        // get back to as any slice, without a trip out to the ring first.
        if (d >= ARM_DISTANCE) armed = true;
        int h;
        final int n = wheel.slices().size();
        if (d >= deadZone() && n > 0) h = dev.fallingcloud.slate.building.client.gfx.RingRaster.sliceAtAngle(cursorX, cursorY, n);
        else if (armed && wheel.center() != null) h = RadialWheel.CENTER;
        else h = RadialWheel.NONE;
        wheel.hover(h);
        pointer.set(d >= deadZone() && n > 0);
        if (h != lastHover) {
            if (h != RadialWheel.NONE && open) WheelActions.tick(1.6f + (h >= 0 ? h * 0.03f : 0f));
            lastHover = h;
            labelSwap.snap(0.35f);
            labelSwap.set(1f);
        }
    }

    private void step(final int dir) {
        final int n = wheel.slices().size();
        if (n == 0) return;
        int h = wheel.hovered();
        if (h < 0) {
            final WheelPage p = currentPage();
            h = p != null && p.currentIndex() >= 0 ? p.currentIndex() : (dir > 0 ? -1 : 0);
        }
        pointAt(Math.floorMod(h + dir, n));
    }

    private void pointAt(final int index) {
        final int n = wheel.slices().size();
        if (index < 0 || index >= n) return;
        final float a = dev.fallingcloud.slate.building.client.gfx.RingRaster.sliceAngle(index, n);
        final float r = wheel.itemRadius();
        cursorX = (float) Math.cos(a) * r;
        cursorY = (float) Math.sin(a) * r;
        armed = true;
        updateHover();
    }

    private @Nullable WheelSlice hoveredSlice() {
        final int h = wheel.hovered();
        if (h == RadialWheel.CENTER) return wheel.center();
        if (h >= 0 && h < wheel.slices().size()) return wheel.slices().get(h);
        return null;
    }

    private void applyHovered() {
        final WheelSlice s = hoveredSlice();
        final WheelTarget t = target;
        if (s == null || t == null) return;
        if (s.lock() != null || !s.available()) { WheelActions.deny(); return; }
        if (WheelActions.apply(t, s) && wheel.hovered() >= 0) wheel.pulse(wheel.hovered());
    }

    // ------------------------------------------------------------------ input (BuildInput, priority 100)

    @Override
    public int priority() { return 100; }

    @Override
    public boolean onMouseLook(final double dx, final double dy) {
        if (!open) return false;
        final Minecraft mc = Minecraft.getInstance();
        final double perPx = (double) mc.getWindow().getGuiScaledWidth() / Math.max(1, mc.getWindow().getScreenWidth());
        final double sens = 0.6 + mc.options.sensitivity().get() * 0.8;     // 0.6..1.4 around vanilla's default 0.5 → 1.0
        cursorX += (float) (dx * perPx * sens);
        cursorY += (float) (dy * perPx * sens);
        final float max = wheel.outerRadius() + 6f;
        final float d = (float) Math.sqrt(cursorX * cursorX + cursorY * cursorY);
        if (d > max) { cursorX *= max / d; cursorY *= max / d; }
        updateHover();
        return true;
    }

    @Override
    public boolean onMouseButton(final int button, final int action, final int mods) {
        // Releases always reach vanilla. A button held from before the wheel opened (use / attack held down, or the
        // swap key bound to a mouse button) must see its release, or its KeyMapping stays down: blocks keep being
        // placed or mined after the wheel closes, or the wheel never closes. A press the wheel consumed never set its
        // mapping down, so vanilla's release of it is a no-op.
        if (!open || action != GLFW.GLFW_PRESS) return false;
        final WheelSettings ws = WheelConfig.wheel();
        if (!ws.releaseToSelect && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            applyHovered();
            return true;
        }
        if (ws.clickSwitchesWheel) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) switchPage(-1);
            else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) switchPage(1);
        }
        return true;
    }

    @Override
    public boolean onScroll(final double dx, final double dy) {
        if (!open) return false;
        if (WheelConfig.wheel().scrollSelects && dy != 0) step(dy < 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean onKey(final int key, final int scancode, final int action, final int mods) {
        // Like mouse buttons: key releases always reach vanilla, so nothing held from before the wheel opened sticks.
        if (!open || action == GLFW.GLFW_RELEASE) return false;
        final Minecraft mc = Minecraft.getInstance();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            // Esc only ever opens the pause menu: the wheel closes without applying and the key goes on to vanilla.
            if (action == GLFW.GLFW_PRESS) close(false);
            return false;
        }
        for (int i = 0; i < 9; i++) {
            if (!mc.options.keyHotbarSlots[i].matches(key, scancode)) continue;
            if (action == GLFW.GLFW_PRESS && WheelConfig.wheel().numberKeys && i < wheel.slices().size()) {
                pointAt(i);
                if (!WheelConfig.wheel().releaseToSelect) applyHovered();
            }
            return true;                       // never change the hotbar slot under an open wheel
        }
        return false;
    }

    @Override
    public boolean onUse() { return open; }

    @Override
    public boolean onAttack() { return open; }

    @Override
    public boolean suppressContinueAttack() { return open; }

    @Override
    public boolean onPickBlock() { return open; }

    // ------------------------------------------------------------------ render (HUD)

    private void render(final GuiGraphics g, final float partialTick) {
        final Minecraft mc = Minecraft.getInstance();
        if (!open && !closing) return;
        if (mc.screen != null || mc.player == null) { if (closing && fade.get() <= 0.01f) closing = false; return; }
        final float alpha = fade.get();
        if (closing && alpha <= 0.01f) { closing = false; return; }
        final WheelTarget t = target;
        final WheelPages.WheelSet s = set;
        if (t == null || s == null) return;

        final int w = g.guiWidth(), h = g.guiHeight();
        final int maxR = Math.max(28, Math.min(h / 2 - 40, w / 2 - 24));
        final int r = Math.min(maxR, Math.round(BASE_RADIUS * WheelConfig.wheelScale()));
        final int cx = w / 2, cy = h / 2;
        wheel.geometry(cx, cy, r);
        final Theme theme = Theme.current();
        final Palette p = theme.palette();
        final boolean vanilla = theme.isVanilla();

        // Keep the looked-at block outlined while reshaping it in the world.
        if (!t.held() && t.pos() != null) OverlayRenderer.box(new AABB(t.pos()).inflate(0.002), p.accent(), true);

        // Soft backdrop so the wheel reads over any world.
        final float bd = alpha * Math.min(1f, openAnim.get());
        g.fillGradient(0, 0, w, h, Colors.scaleAlpha(0x30000000, bd), Colors.scaleAlpha(0x40000000, bd));

        final float open01 = Mth.clamp(openAnim.get(), 0f, 1.2f);
        final float scale = closing ? 0.94f + 0.06f * alpha : 0.85f + 0.15f * open01;
        wheel.render(g, alpha, scale);

        // The pointer: a short accent arc in the gap between the centre disc and the ring, facing the cursor.
        final float pa = pointer.get() * alpha;
        if (pa > 0.02f && wheel.center() != null) {
            final float ang = (float) Math.atan2(cursorY, cursorX);
            final int pr = wheel.centerRadius() + 2;
            final PixelCanvas c = PixelCanvas.begin(g).alpha(pa);
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().scale(scale, scale, 1f);
            g.pose().translate(-cx, -cy, 0);
            ArcRaster.of(pr, pr).draw(c, cx, cy, ang - 0.32f, ang + 0.32f, vanilla ? 0xFFFFFFFF : p.accent());
            c.end();
            g.pose().popPose();
        } else if (wheel.center() == null && open) {
            // Chisel pages have no centre disc: show the virtual cursor as a small dot instead.
            final int dx = Math.round(cx + cursorX), dy = Math.round(cy + cursorY);
            SlateDraw.pixelCircle(g, dx, dy, 2, Colors.scaleAlpha(vanilla ? 0xFF000000 : p.shadow(), alpha));
            SlateDraw.pixelCircle(g, dx, dy, 1, Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.text(), alpha));
        }

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        renderTitle(g, s, cx, cy - Math.round(r * scale) - 26, alpha, vanilla, p);
        if (WheelConfig.wheel().labels) renderLabel(g, t, cx, cy + Math.round(r * scale) + 10, alpha, vanilla, p);
        g.pose().popPose();
    }

    private void renderTitle(final GuiGraphics g, final WheelPages.WheelSet s, final int cx, final int y, final float alpha,
                             final boolean vanilla, final Palette p) {
        final WheelPage pg = currentPage();
        final Component name;
        final Icon icon;
        if (pg == null) {
            name = Component.translatable("slate_building.ui.wheel.no_shapes");
            icon = BuildingIcons.WHEEL;
        } else {
            name = pg.name();
            icon = pg.kind() == WheelSlice.Kind.CHISEL ? BuildingIcons.CHISEL
                : (target != null && !target.held() ? BuildingIcons.TOOL_HAMMER : BuildingIcons.WHEEL);
        }
        final Component title = Fonts.heading(name);
        final int pages = s.pages().size();
        final int tw = SlateDraw.width(title) + 14;
        final int dotsW = pages > 1 ? pages * 6 - 2 : 0;
        final int w = Math.max(tw, dotsW) + 16 + (pages > 1 ? 20 : 0);
        final int h = pages > 1 ? 22 : UiDraw.PILL_H;
        final int x = cx - w / 2;
        final float la = alpha * (0.4f + 0.6f * labelSwap.get());
        UiDraw.pill(g, x, y, w, h, alpha);
        final int textX = cx - tw / 2;
        Icons.draw(g, icon, textX, y + 4, 8, Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.accent(), la));
        g.drawString(SlateDraw.font(), title, textX + 12, y + 4, Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.text(), la), vanilla);
        if (pages > 1) {
            final int dy = y + 15;
            int dx = cx - dotsW / 2;
            for (int i = 0; i < pages; i++) {
                final boolean on = i == page;
                final int col = on ? (vanilla ? 0xFFFFFFFF : p.accent()) : (vanilla ? 0xFF606060 : p.borderStrong());
                SlateDraw.rect(g, dx, dy, 4, 2, Colors.scaleAlpha(col, alpha));
                dx += 6;
            }
            // LMB / RMB page hints at the pill's ends
            final int hint = Colors.scaleAlpha(vanilla ? 0xFFA0A0A0 : p.textDim(), alpha);
            Icons.draw(g, Icon.CHEVRON_LEFT, x + 3, y + 7, 8, hint);
            Icons.draw(g, Icon.CHEVRON_RIGHT, x + w - 11, y + 7, 8, hint);
        }
    }

    private void renderLabel(final GuiGraphics g, final WheelTarget t, final int cx, final int y, final float alpha,
                             final boolean vanilla, final Palette p) {
        final WheelSlice s = hoveredSlice();
        final Component name;
        final boolean isCurrent;
        if (s != null) {
            name = s.label();
            isCurrent = s.current();
        } else {
            name = t.displayName();
            isCurrent = true;
        }
        final Component count = t.held() ? Component.literal("×" + t.count()) : Component.translatable("slate_building.ui.wheel.in_world");
        final Component lock = s != null ? (s.available() ? s.lock() : Component.translatable("slate_building.ui.wheel.unavailable")) : null;
        final int nameW = SlateDraw.width(name), countW = SlateDraw.width(count);
        int w = nameW + 6 + countW + 16;
        final int lockW = lock == null ? 0 : SlateDraw.width(lock) + 12;
        w = Math.max(w, lockW + 16);
        final int h = lock == null ? UiDraw.PILL_H : 27;
        final int x = cx - w / 2;
        final float la = alpha * (0.4f + 0.6f * labelSwap.get());
        UiDraw.pill(g, x, y, w, h, alpha);
        final int nx = cx - (nameW + 6 + countW) / 2;
        final int nameCol = isCurrent ? (vanilla ? 0xFFFFFF55 : p.accent()) : (vanilla ? 0xFFFFFFFF : p.text());
        g.drawString(SlateDraw.font(), name, nx, y + 4, Colors.scaleAlpha(nameCol, la), vanilla);
        g.drawString(SlateDraw.font(), count, nx + nameW + 6, y + 4, Colors.scaleAlpha(vanilla ? 0xFFA0A0A0 : p.textMuted(), la), vanilla);
        if (lock != null) {
            final int lx = cx - lockW / 2;
            final int col = Colors.scaleAlpha(vanilla ? 0xFFFFAA00 : p.warning(), la);
            Icons.draw(g, Icon.LOCK, lx, y + 15, 8, col);
            g.drawString(SlateDraw.font(), lock, lx + 12, y + 15, col, vanilla);
        }
    }

    // ------------------------------------------------------------------ dev harness

    /** Dev harness: moves the virtual cursor to slice {@code index} (or the centre with {@link RadialWheel#CENTER}). */
    public void debugPoint(final int index) {
        if (!open) return;
        if (index == RadialWheel.CENTER) {
            armed = true;
            cursorX = cursorY = 0;
            updateHover();
        } else {
            pointAt(index);
        }
    }

    /** Dev harness: shows page {@code index}. */
    public void debugPage(final int index) {
        if (open) showPage(index, true);
    }

    public int pageCount() {
        return set == null ? 0 : set.pages().size();
    }
}
