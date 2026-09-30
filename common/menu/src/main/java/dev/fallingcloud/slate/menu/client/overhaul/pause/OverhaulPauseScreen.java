package dev.fallingcloud.slate.menu.client.overhaul.pause;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.module.Features;
import dev.fallingcloud.slate.core.module.KnownModules;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.screen.slot.CoreSlots;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.core.stage.Stage;
import dev.fallingcloud.slate.core.stage.StageFinish;
import dev.fallingcloud.slate.core.stage.StageWidget;
import dev.fallingcloud.slate.core.stage.anim.IdleMotion;
import dev.fallingcloud.slate.core.stage.node.FxNode;
import dev.fallingcloud.slate.core.stage.node.MotesNode;
import dev.fallingcloud.slate.core.stage.node.PivotNode;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import dev.fallingcloud.slate.menu.client.pause.PauseActions;
import dev.fallingcloud.slate.menu.client.pause.PauseVitals;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The Overhaul layout's escape menu. The world stays where it is, behind everything, out of focus. On the left, in
 * the words of the Overhaul loading screens, what is paused (the world's name, large, the mode, the dimension, the
 * day and hour under it) and the list of what can be done, in the heading font and with no plate under it: Back to
 * game with the accent's bar already beside it, the others taking it as the pointer comes to them, leaving last and
 * red. On the right, the player: the figure they are at this moment (their armour, what they hold), standing on
 * the piece of the world they stand on, cut out and turning a little ({@link Diorama}); under it how they are doing
 * and where they are. A click on the figure opens their profile.
 *
 * <p>A window too small for both keeps the list.</p>
 */
public final class OverhaulPauseScreen extends SlateScreen {

    private static final float FOV = 26f;
    /** How much wider and higher than the piece the view is: the air round it, and room for it to turn in. */
    private static final float AIR = 1.08f;
    /** How far round to the right of the piece the camera stands, and how far over it, in degrees. */
    private static final float ROUND = 25f, OVER = 20f;
    private static final int LEAVE_GAP = 8;

    private final Anim open = new Anim(0, 320, Ease.OUT_CUBIC);
    private final Anim pop = new Anim(0, 620, Ease.OUT_BACK);
    @Nullable private Stage stage;
    private Rect stageRect = new Rect(0, 0, 0, 0);
    /** What the scene holds, as a box round the figure's column: half its width, its back and front, its underside and top. */
    private float boxSide = 0.8f, boxBack = -0.8f, boxFront = 0.8f, boxBottom, boxTop = 1.95f;
    private int margin, colW, headTop, listBottom;
    private boolean big;

    public OverhaulPauseScreen() {
        super(Component.translatable("menu.game"), null);
        this.showHeader = false;
        this.showBack = false;
    }

    // ------------------------------------------------------------------ build

    @Override
    protected void build() {
        if (open.target() == 0f) open.set(1f);
        final List<PauseActions.Action> actions = PauseActions.actions(this, true);
        margin = Mth.clamp(width / 22, 12, 40);
        final boolean scene = width >= 380 && height >= 170;
        colW = scene ? Mth.clamp(Math.round(width * 0.34f), 150, 250) : Math.min(width - margin * 2, 250);
        big = height >= 300;
        final int headH = 14 + Math.round(9 * (big ? 2f : 1.5f)) + 5 + 12 + (big ? 14 : 6);
        final int rowH = Mth.clamp((height - headH - margin - LEAVE_GAP) / Math.max(1, actions.size()), 15, 24);
        headTop = Math.max(6, (height - headH - actions.size() * rowH - LEAVE_GAP) / 2);

        // The scene is drawn first and asked last: a row over it takes its own clicks.
        StageWidget view = null;
        if (scene) {
            // The scene has the screen beside the list, down to the two lines that stand under the figure.
            final int sx = margin + colW + 8;
            stageRect = new Rect(sx, 0, width - sx, Math.max(60, height - Math.max(10, margin / 2) - 32));
            if (stage == null || stage.isClosed()) createScene();
            frame(stage);
            view = new StageWidget(stageRect.x(), stageRect.y(), stageRect.w(), stageRect.h(), stage, getTitle());
            addRenderableOnly(view);
        } else {
            stageRect = new Rect(0, 0, 0, 0);
            if (stage != null) { stage.close(); stage = null; }
        }

        int y = headTop + headH;
        for (final PauseActions.Action a : actions) {
            if (a.kind() == PauseActions.Kind.DANGER) y += LEAVE_GAP;
            // The row's icon stands where the heading's first letter does; its bar is out in the margin.
            add(new Row(margin - 9, y, colW + 9, rowH, a));
            y += rowH;
        }
        listBottom = y;
        if (view != null) addWidget(view);
    }

    /**
     * Where the camera stands: looking at the middle of what the scene holds, from so far off that all of it is in
     * the view, however narrow the view is and whatever the piece is (a slab of ground, a room, the player alone).
     */
    private void frame(final Stage s) {
        final float aspect = stageRect.w() / (float) Math.max(1, stageRect.h());
        final float round = (float) Math.toRadians(ROUND), over = (float) Math.toRadians(OVER);
        // From the middle of the box towards the camera, and the two directions across the view.
        final float ex = Mth.cos(over) * Mth.sin(round), ey = Mth.sin(over), ez = Mth.cos(over) * Mth.cos(round);
        final float rx = Mth.cos(round), rz = -Mth.sin(round);
        final float ux = -ey * Mth.sin(round), uy = Mth.cos(over), uz = -ey * Mth.cos(round);
        final float cx = 0f, cy = (boxBottom + boxTop) / 2f, cz = (boxBack + boxFront) / 2f;
        float across = 0f, up = 0f, near = 0f;
        for (int i = 0; i < 8; i++) {
            final float x = ((i & 1) == 0 ? -boxSide : boxSide) - cx, y = ((i & 2) == 0 ? boxBottom : boxTop) - cy,
                z = ((i & 4) == 0 ? boxBack : boxFront) - cz;
            across = Math.max(across, Math.abs(x * rx + z * rz));
            up = Math.max(up, Math.abs(x * ux + y * uy + z * uz));
            near = Math.max(near, x * ex + y * ey + z * ez);
        }
        final float tan = (float) Math.tan(Math.toRadians(FOV / 2f));
        // The corner nearest the camera is larger in the view than the middle is: half its lead is allowed for, the
        // rest is what the air round the piece is for.
        final float dist = Math.max(up / tan, across / (tan * Math.max(0.2f, aspect))) * AIR + near * 0.5f;
        s.camera().at(cx + dist * ex, cy + dist * ey, cz + dist * ez).lookAt(cx, cy, cz).fov(FOV).clip(0.1f, dist + 60f);
    }

    private void createScene() {
        final Minecraft mc = Minecraft.getInstance();
        final Theme theme = Theme.current();
        final Stage s = new Stage().bind(this);
        stage = s;
        // The piece floats over the world itself: no background of its own.
        s.background(0);
        s.soft(true);
        s.finish(StageFinish.glow());
        s.resolutionScale(mc.getWindow().getWidth() <= 2048 ? 2f : 1.5f);
        s.camera().idle(IdleMotion.sway(26000f, 5f, 0.4f, 0.03f));
        s.lighting().sun(0.55f, 0.85f, 0.45f).ambient(0.62f).key(6f, 8f, 9f).keyColor(0.74f, 0.66f, 0.54f).ambientColor(0.42f, 0.42f, 0.46f)
            .fill(-1f, 0.3f, 0.3f, 0.14f, 0.18f, 0.26f).rim(1f, 0.9f, 0.72f, 0.2f);

        final PivotNode root = s.add(new PivotNode());
        final int warm = Colors.lerp(0xFFFFE9C8, theme.accent(), theme.isVanilla() ? 0f : 0.3f);
        final Diorama.Piece piece = mc.player == null || mc.level == null ? null : Diorama.cut(s, mc.player, mc.level, ROUND);
        float feet = 0f;
        if (piece != null) {
            s.add(piece.land()).at(-(Diorama.SIDE + 0.5f), -Diorama.DEEP, -(Diorama.BACK + 0.5f)).attachTo(root);
            feet = piece.feet();
            boxSide = Diorama.SIDE + 0.5f;
            boxBack = -(Diorama.BACK + 0.5f);
            boxFront = Diorama.FRONT + 0.5f;
            boxBottom = piece.bottom();
            boxTop = Math.max(piece.top(), feet + 1.95f);
            // A light under the piece, as under everything that floats in this layout.
            s.add(FxNode.glow(5.6f, Colors.withAlpha(warm, 0x30)).fade(1.9f)).at(0f, -Diorama.DEEP - 0.6f, -1f).attachTo(root);
        } else {
            // Nothing under the feet (in the air, in the water): the player alone, on a pool of light.
            boxSide = 1.3f;
            boxBack = -1.3f;
            boxFront = 1.3f;
            boxBottom = -0.2f;
            boxTop = 2.1f;
            s.add(FxNode.glow(1.5f, Colors.withAlpha(warm, 0x66)).fade(1.6f)).at(0f, 0.01f, 0f).attachTo(root);
        }
        // What the figure stands on takes its shadow.
        s.add(FxNode.glow(0.62f, 0x78000000).fade(1.5f)).at(0f, feet + 0.02f, 0f).attachTo(root);
        final SelfNode self = s.add(new SelfNode());
        self.at(0f, feet, 0f).attachTo(root);
        if (Features.present(KnownModules.PROFILE)) {
            final Component profile = Component.translatable("slate_menu.overhaul.profile");
            self.named(profile).tooltip(profile).onClick(() -> MenuSlots.open(CoreSlots.PROFILE, this));
        } else {
            self.tooltip(Features.lockedTooltip(KnownModules.PROFILE))
                .onClick(() -> SlateToasts.show(KnownModules.name(KnownModules.PROFILE), Features.lockedTooltip(KnownModules.PROFILE), Icon.LOCK));
        }
        s.add(new MotesNode(24, 7f, 5f, 7f, 0.03f, 0x48FFE7C2, 12L)).at(0f, 2.2f, -1f).attachTo(root);

        // The piece grows into place, then turns a little from side to side.
        pop.snap(0f);
        pop.set(1f);
        root.onFrame(ctx -> {
            final boolean moving = Theme.current().motion() > 0f;
            root.scale(moving ? Math.max(0.001f, pop.get()) : 1f);
            root.rotate(moving ? 7f * Mth.sin(ctx.seconds() * 0.35f) : 0f, 0f, 0f);
        });
    }

    @Override
    public void removed() {
        super.removed();
        if (stage != null) { stage.close(); stage = null; }
    }

    // ------------------------------------------------------------------ render

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        if (Theme.current().isVanilla()) return;
        // Dark from the left, where the words are, and out at the corners: the world is still there, further off.
        final float a = open.get();
        SlateDraw.hgradient(g, 0, 0, Math.round(width * 0.62f), height, Colors.withAlpha(0xFF000000, Math.round(0x96 * a)), 0x00000000);
        SlateDraw.vignette(g, 0, 0, width, height, 0.4f * a);
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        final float a = open.get();
        if (a <= 0.01f) return;
        final int slide = Math.round((1f - a) * 6f);
        final int accent = van ? 0xFFFFFFFF : p.accent();
        final int muted = van ? 0xFFC8C8C8 : p.textMuted();
        final int room = stageRect.w() > 0 ? colW + stageRect.w() / 4 : colW;

        // What is paused: the screen's name small, the world's name large, the state of the world under it.
        int y = headTop + slide;
        final String kicker = getTitle().getString().toUpperCase(Locale.ROOT);
        if (!van) SlateDraw.rect(g, margin, y + 3, 10, 2, Colors.scaleAlpha(accent, a));
        g.drawString(font, kicker, margin + (van ? 0 : 15), y, Colors.scaleAlpha(van ? 0xFFE0E0E0 : accent, a), true);
        y += 14;
        final float scale = big ? 2f : 1.5f;
        final FormattedCharSequence name = SlateDraw.truncate(Fonts.heading(PauseActions.worldName()), Math.max(40, Math.round(room / scale)));
        g.pose().pushPose();
        g.pose().translate(margin, y, 0f);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, name, 0, 0, Colors.scaleAlpha(van ? 0xFFFFFFFF : p.text(), a), true);
        g.pose().popPose();
        y += Math.round(9 * scale) + 5;
        final MutableComponent state = Component.empty();
        for (final Component part : new Component[] {PauseActions.mode(), PauseActions.dimension(), PauseActions.dayTime()}) {
            if (part == null) continue;
            if (!state.getSiblings().isEmpty()) state.append(" · ");
            state.append(part);
        }
        g.drawString(font, SlateDraw.truncate(state, room), margin, y, Colors.scaleAlpha(muted, a), true);

        // How long they have been at it: at the foot of the column, where there is room for it.
        final Component session = PauseActions.session();
        final int footY = height - Math.max(10, margin / 2) - 9;
        if (session != null && footY >= listBottom + 8) g.drawString(font, session, margin, footY, Colors.scaleAlpha(van ? 0xFFA0A0A0 : p.textDim(), a), true);

        // Under the figure: how they are doing, and where they are.
        if (stageRect.w() > 0) {
            final int cx = stageRect.x() + stageRect.w() / 2;
            final Component position = PauseActions.position(), biome = PauseActions.biome();
            Component where = position;
            if (position != null && biome != null) where = Component.empty().append(position).append(" · ").append(biome);
            else if (biome != null) where = biome;
            int line = footY;
            if (where != null) {
                g.drawString(font, where, cx - font.width(where) / 2, line + slide, Colors.scaleAlpha(muted, a), true);
                line -= 14;
            }
            final int vitals = PauseVitals.width();
            if (vitals > 0) PauseVitals.draw(g, cx - vitals / 2, line + slide, a, van ? 0xFFFFFFFF : p.text());
        }
    }

    // ------------------------------------------------------------------ a row of the list

    /**
     * One thing the menu does: its icon and its name in the heading font, on nothing. Under the pointer (or the
     * keyboard's focus) it moves a little to the right and the accent's bar comes out beside it, its light running
     * off to the right; the row that goes back to the game has the bar from the start.
     */
    private static final class Row extends SlateButton {

        private final PauseActions.Kind kind;
        private final Component heading;

        Row(final int x, final int y, final int w, final int h, final PauseActions.Action a) {
            super(x, y, w, h, a.label(), a.run());
            this.icon = a.icon();
            this.kind = a.kind();
            this.heading = Fonts.heading(a.label());
            if (!a.enabled()) enabled(false);
            if (a.locked()) locked(a.lockedModule());
            else if (a.tip() != null) tip(a.tip());
        }

        @Override
        protected void renderDark(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            final Palette p = Theme.current().palette();
            final boolean danger = kind == PauseActions.Kind.DANGER;
            draw(g, danger ? p.danger() : p.accent(), p.text(), p.textMuted(), Colors.withAlpha(p.textDim(), 0xB0), danger ? p.danger() : p.text(), 0x46);
        }

        @Override
        protected void renderVanilla(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
            final boolean danger = kind == PauseActions.Kind.DANGER;
            draw(g, danger ? 0xFFFF6A6A : 0xFFFFFFFF, 0xFFFFFFFF, 0xFFC8C8C8, 0xFF909090, danger ? 0xFFFF6A6A : 0xFFFFFFFF, 0x38);
        }

        /**
         * @param accent the bar's colour
         * @param lit    the text of the row that goes back to the game
         * @param rest   the text of a row at rest
         * @param off    the text of a row that cannot be used
         * @param over   the text of a row under the pointer
         * @param glow   how strong the bar's light is at its strongest (alpha, 0 to 255)
         */
        private void draw(final GuiGraphics g, final int accent, final int lit, final int rest, final int off, final int over, final int glow) {
            final float a = effectiveAlpha();
            if (a <= 0.004f) return;
            final boolean primary = kind == PauseActions.Kind.PRIMARY;
            final boolean usable = this.active && !isLocked();
            final float lift = Math.max(hover(), focus());
            final int x = getX(), y = getY() + enterOffset() + Math.round(press()), w = getWidth(), h = getHeight();
            final float light = !usable ? 0f : primary ? 0.6f + 0.4f * lift : lift;
            if (light > 0.01f) {
                SlateDraw.hgradient(g, x, y + 1, w, h - 2, Colors.withAlpha(accent, Math.round(glow * light * a)), Colors.withAlpha(accent, 0));
                SlateDraw.rect(g, x, y + 2, 2, h - 4, Colors.scaleAlpha(accent, light * a));
            }
            final int shift = usable ? Math.round(lift * 4f) : 0;
            final int fg = !usable ? off : primary ? lit : Colors.lerp(rest, over, lift);
            final int ic = !usable ? off : Colors.lerp(primary ? accent : rest, accent, lift);
            Icons.draw(g, icon, x + 9 + shift, y + (h - 12) / 2, 12, Colors.scaleAlpha(ic, a));
            final FormattedCharSequence text = SlateDraw.truncate(heading, w - 27 - (isLocked() ? 14 : 2));
            final int tx = x + 27 + shift, ty = y + (h - 9) / 2 + 1;
            g.drawString(SlateDraw.font(), text, tx, ty, Colors.scaleAlpha(fg, a), true);
            if (isLocked()) Icons.draw(g, Icon.LOCK, tx + SlateDraw.width(text) + 5, y + (h - 8) / 2, 8, Colors.scaleAlpha(fg, a));
        }
    }
}
