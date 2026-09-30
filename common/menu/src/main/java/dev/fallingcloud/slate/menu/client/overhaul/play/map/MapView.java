package dev.fallingcloud.slate.menu.client.overhaul.play.map;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.gfx.Textures;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateSpinner;
import dev.fallingcloud.slate.core.widget.SlateTooltips;
import dev.fallingcloud.slate.core.widget.SlateWidget;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The map in the Play screen's details: the selected world seen from above, to drag about and zoom. Its tiles come
 * from {@link MapTiles}; on top go the world's spawn, where the player was last, and the waypoints a map mod has
 * saved ({@link Waypoints}). Drag pans, the wheel zooms about the pointer, a double click (or the target button)
 * goes back to where the map opened.
 *
 * <p>Without tiles (a server nothing on this computer has mapped) the box shows a picture and a line of text
 * instead: {@link #placeholder}.</p>
 */
public final class MapView extends SlateWidget {

    private static final float MIN_ZOOM = 0.125f, MAX_ZOOM = 6f;

    @Nullable private MapTiles tiles;
    private List<Waypoints.Waypoint> waypoints = List.of();
    private double centreX, centreZ, homeX, homeZ;
    private final Anim zoom = new Anim(0.5f, 160, Ease.OUT_CUBIC);
    private boolean hasSpawn, hasPlayer;
    private int spawnX, spawnZ;
    private double playerX, playerZ;
    private boolean dragging;
    private long lastClickMs;
    private Component placeholder = Component.empty();
    @Nullable private Textures.Loaded picture;
    private final Anim appear = new Anim(0, 260, Ease.OUT_CUBIC);

    public MapView(final int x, final int y, final int width, final int height) {
        super(x, y, width, height, Component.translatable("slate_menu.play.map"));
        silent();
    }

    /** Shows a world's map, opened on {@code (x, z)}. The view does not own the tiles: the screen closes them. */
    public MapView show(@Nullable final MapTiles tiles, final double x, final double z) {
        this.tiles = tiles;
        this.centreX = this.homeX = x;
        this.centreZ = this.homeZ = z;
        zoom.snap(0.5f);
        appear.snap(0f);
        appear.set(1f);
        return this;
    }

    public MapView spawn(final int x, final int z) { hasSpawn = true; spawnX = x; spawnZ = z; return this; }

    public MapView player(final double x, final double z) { hasPlayer = true; playerX = x; playerZ = z; return this; }

    public MapView clearMarks() { hasSpawn = false; hasPlayer = false; waypoints = List.of(); return this; }

    public MapView waypoints(final List<Waypoints.Waypoint> list) { this.waypoints = list == null ? List.of() : list; return this; }

    /** What the box shows while there is no map: a picture (a server's icon, a world's) and a line under it. */
    public MapView placeholder(final Component text, @Nullable final Textures.Loaded picture) {
        this.placeholder = text;
        this.picture = picture;
        return this;
    }

    public boolean hasMap() { return tiles != null; }

    // ------------------------------------------------------------------ input

    @Override
    public void onClick(final double mouseX, final double mouseY) {
        super.onClick(mouseX, mouseY);
        if (tiles == null) return;
        if (overHome(mouseX, mouseY)) { home(); return; }
        final long now = Clock.nowMs();
        if (now - lastClickMs < 350) home();
        lastClickMs = now;
        dragging = true;
    }

    @Override
    public void onRelease(final double mouseX, final double mouseY) {
        super.onRelease(mouseX, mouseY);
        dragging = false;
    }

    @Override
    protected void onDrag(final double mouseX, final double mouseY, final double dragX, final double dragY) {
        if (!dragging || tiles == null) return;
        final float z = zoom.get();
        centreX -= dragX / z;
        centreZ -= dragY / z;
    }

    @Override
    public boolean mouseScrolled(final double mouseX, final double mouseY, final double scrollX, final double scrollY) {
        if (tiles == null || !isMouseOver(mouseX, mouseY) || scrollY == 0) return false;
        final float from = zoom.target();
        final float to = Mth.clamp(from * (scrollY > 0 ? 1.35f : 1f / 1.35f), MIN_ZOOM, MAX_ZOOM);
        if (to == from) return true;
        // What is under the pointer stays under the pointer.
        final double px = mouseX - (getX() + getWidth() / 2.0), pz = mouseY - (getY() + getHeight() / 2.0);
        final float now = zoom.get();
        final double worldX = centreX + px / now, worldZ = centreZ + pz / now;
        centreX = worldX - px / to;
        centreZ = worldZ - pz / to;
        zoom.snap(to);
        return true;
    }

    private void home() {
        centreX = homeX;
        centreZ = homeZ;
        zoom.set(0.5f);
    }

    private boolean overHome(final double mx, final double my) {
        final int bx = getX() + getWidth() - 19, by = getY() + getHeight() - 19;
        return mx >= bx && mx < bx + 15 && my >= by && my < by + 15;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, mouseX, mouseY, false);
    }

    @Override
    protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        draw(g, mouseX, mouseY, true);
    }

    private void draw(final GuiGraphics g, final int mouseX, final int mouseY, final boolean vanilla) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final float a = effectiveAlpha();
        if (a <= 0.004f) return;
        final int x = getX(), y = getY() + enterOffset(), w = getWidth(), h = getHeight();
        final int r = vanilla ? 0 : t.radius();
        SlateDraw.shadow(g, x, y, w, h, 0.5f * a);
        SlateDraw.pixelRound(g, x, y, w, h, Colors.scaleAlpha(vanilla ? 0xFF101010 : 0xFF0E0F11, a), r);

        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        if (tiles != null) tiles(g, x, y, w, h, a * appear.get());
        else placeholder(g, x, y, w, h, a, vanilla, p);
        g.disableScissor();

        if (tiles != null) {
            marks(g, x, y, w, h, mouseX, mouseY, a, vanilla, p);
            // Shade along the edges, so the map sits in its frame instead of being cut by it.
            SlateDraw.vgradient(g, x + 1, y + 1, w - 2, 10, Colors.scaleAlpha(0x70000000, a), 0);
            SlateDraw.vgradient(g, x + 1, y + h - 11, w - 2, 10, 0, Colors.scaleAlpha(0x70000000, a));
            if (isHovered()) {
                final float z = zoom.get();
                final int bx = (int) Math.floor(centreX + (mouseX - (x + w / 2.0)) / z), bz = (int) Math.floor(centreZ + (mouseY - (y + h / 2.0)) / z);
                final String at = bx + ", " + bz;
                g.drawString(SlateDraw.font(), at, x + 5, y + h - 11, Colors.scaleAlpha(0xFFE8E6E0, a * hover()), true);
            }
            final boolean overHome = overHome(mouseX, mouseY);
            final int bx = x + w - 19, by = y + h - 19;
            SlateDraw.pixelRound(g, bx, by, 15, 15, Colors.scaleAlpha(overHome ? 0xE0202022 : 0xA0101012, a), vanilla ? 0 : 2);
            Icons.draw(g, Icon.COMPASS, bx + 3, by + 3, 9, Colors.scaleAlpha(overHome ? 0xFFFFFFFF : 0xFFB8B6B0, a));
            if (overHome) SlateTooltips.request(Component.translatable("slate_menu.play.map.home"), this);
        }
        SlateDraw.outline(g, x, y, w, h, Colors.scaleAlpha(vanilla ? 0xFF000000 : Colors.lerp(p.border(), p.borderStrong(), hover()), a), r);
        SlateDraw.focusRing(g, x, y, w, h, focus() * a);
    }

    private void tiles(final GuiGraphics g, final int x, final int y, final int w, final int h, final float a) {
        final MapTiles map = tiles;
        if (map == null) return;
        final float z = zoom.get();
        final double left = centreX - w / 2.0 / z, top = centreZ - h / 2.0 / z;
        final int rx0 = Math.floorDiv((int) Math.floor(left), RegionMap.SIZE), rz0 = Math.floorDiv((int) Math.floor(top), RegionMap.SIZE);
        final int rx1 = Math.floorDiv((int) Math.floor(left + w / z), RegionMap.SIZE), rz1 = Math.floorDiv((int) Math.floor(top + h / z), RegionMap.SIZE);
        // Zoomed far out, the view would want a great many regions at once: those stay blank until zoomed in.
        if ((long) (rx1 - rx0 + 1) * (rz1 - rz0 + 1) > 12) return;
        boolean waiting = false;
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, a);
        for (int rz = rz0; rz <= rz1; rz++) {
            for (int rx = rx0; rx <= rx1; rx++) {
                final Textures.Loaded tex = map.tile(rx, rz);
                if (tex == null) {
                    waiting |= map.loading(rx, rz);
                    continue;
                }
                // Both edges are rounded on their own, so neighbouring tiles share them and no seam opens.
                final int sx0 = x + (int) Math.round((rx * (double) RegionMap.SIZE - left) * z), sx1 = x + (int) Math.round(((rx + 1) * (double) RegionMap.SIZE - left) * z);
                final int sy0 = y + (int) Math.round((rz * (double) RegionMap.SIZE - top) * z), sy1 = y + (int) Math.round(((rz + 1) * (double) RegionMap.SIZE - top) * z);
                final var texture = Minecraft.getInstance().getTextureManager().getTexture(tex.id());
                texture.setFilter(z < 1f, false);
                g.blit(tex.id(), sx0, sy0, sx1 - sx0, sy1 - sy0, 0f, 0f, tex.width(), tex.height(), tex.width(), tex.height());
            }
        }
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        if (waiting) SlateSpinner.draw(g, x + w - 18, y + 6, 12, Colors.scaleAlpha(Theme.current().accent(), a));
    }

    private void marks(final GuiGraphics g, final int x, final int y, final int w, final int h, final int mouseX, final int mouseY,
                       final float a, final boolean vanilla, final Palette p) {
        final float z = zoom.get();
        final double left = centreX - w / 2.0 / z, top = centreZ - h / 2.0 / z;
        g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        Component tip = null;
        for (final Waypoints.Waypoint wp : waypoints) {
            final int sx = x + (int) Math.round((wp.x() + 0.5 - left) * z), sy = y + (int) Math.round((wp.z() + 0.5 - top) * z);
            if (sx < x - 6 || sy < y - 6 || sx > x + w + 6 || sy > y + h + 6) continue;
            SlateDraw.pixelCircle(g, sx, sy, 3, Colors.scaleAlpha(0xFF101010, a));
            SlateDraw.pixelCircle(g, sx, sy, 2, Colors.scaleAlpha(wp.argb(), a));
            if (Math.abs(mouseX - sx) <= 4 && Math.abs(mouseY - sy) <= 4) tip = Component.literal(wp.name() + "  " + wp.x() + ", " + wp.y() + ", " + wp.z());
        }
        if (hasSpawn) {
            final int sx = x + (int) Math.round((spawnX + 0.5 - left) * z), sy = y + (int) Math.round((spawnZ + 0.5 - top) * z);
            SlateDraw.pixelCircle(g, sx, sy, 4, Colors.scaleAlpha(0xFF101010, a));
            SlateDraw.pixelRing(g, sx, sy, 3, Colors.scaleAlpha(0xFFFFFFFF, a));
            SlateDraw.pixelCircle(g, sx, sy, 1, Colors.scaleAlpha(0xFFE5484D, a));
            if (Math.abs(mouseX - sx) <= 5 && Math.abs(mouseY - sy) <= 5) tip = Component.translatable("slate_menu.play.map.spawn", spawnX, spawnZ);
        }
        if (hasPlayer) {
            final int sx = x + (int) Math.round((playerX - left) * z), sy = y + (int) Math.round((playerZ - top) * z);
            // A slow ring of light round where the player stands.
            final float pulse = Theme.current().motion() > 0f ? (Clock.nowMs() % 1800L) / 1800f : 0.4f;
            SlateDraw.pixelRing(g, sx, sy, 4 + Math.round(pulse * 5f), Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.accent(), a * (1f - pulse) * 0.9f));
            SlateDraw.pixelCircle(g, sx, sy, 3, Colors.scaleAlpha(0xFF101010, a));
            SlateDraw.pixelCircle(g, sx, sy, 2, Colors.scaleAlpha(vanilla ? 0xFFFFFFFF : p.accent(), a));
            if (Math.abs(mouseX - sx) <= 5 && Math.abs(mouseY - sy) <= 5) {
                tip = Component.translatable("slate_menu.play.map.player", (int) Math.floor(playerX), (int) Math.floor(playerZ));
            }
        }
        g.disableScissor();
        if (tip != null && isHovered()) SlateTooltips.request(tip, this);
    }

    private void placeholder(final GuiGraphics g, final int x, final int y, final int w, final int h, final float a, final boolean vanilla, final Palette p) {
        final Textures.Loaded pic = picture;
        final int muted = Colors.scaleAlpha(vanilla ? 0xFFC0C0C0 : p.textMuted(), a);
        int textY = y + h / 2 - 4;
        if (pic != null) {
            // The picture, large and soft behind, small and sharp in the middle.
            SlateDraw.blitFit(g, pic.id(), x, y, w, h, pic.width(), pic.height(), true, a * 0.22f);
            final int s = Math.min(48, h - 30);
            if (s >= 16) {
                SlateDraw.blitScaled(g, pic.id(), x + (w - s) / 2, y + (h - s) / 2 - 8, s, s, pic.width(), pic.height(), a);
                textY = y + (h - s) / 2 - 8 + s + 5;
            }
        } else {
            Icons.draw(g, Icon.MAP, x + w / 2 - 8, y + h / 2 - 18, 16, muted);
            textY = y + h / 2 + 3;
        }
        for (final var line : SlateDraw.font().split(placeholder, w - 16)) {
            g.drawString(SlateDraw.font(), line, x + (w - SlateDraw.font().width(line)) / 2, textY, muted, vanilla);
            textY += 10;
        }
    }
}
