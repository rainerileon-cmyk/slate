package dev.fallingcloud.slate.profile.client.ui;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.stage.Stage;
import dev.fallingcloud.slate.core.stage.StageFinish;
import dev.fallingcloud.slate.core.stage.StageRenderContext;
import dev.fallingcloud.slate.core.stage.anim.IdleMotion;
import dev.fallingcloud.slate.core.stage.node.FxNode;
import dev.fallingcloud.slate.core.stage.node.MotesNode;
import dev.fallingcloud.slate.core.stage.node.PivotNode;
import dev.fallingcloud.slate.core.stage.node.PlayerNode;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.profile.Slot;
import dev.fallingcloud.slate.profile.client.ProfileStore;
import dev.fallingcloud.slate.profile.client.SkinComposer;
import dev.fallingcloud.slate.profile.client.render.LookNode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The stage the looks stand on. They stand side by side in a shallow bow; the one in view in the middle, two more
 * to either side, smaller and further back the further out. A lamp hangs over the one in view; over the one that
 * is worn hangs a brighter one, which shines whether that look is in view or not. Turning to another look slides
 * the row along.
 *
 * <p>For editing, the other looks leave and the view comes closer; with a slot chosen it goes right up to the part
 * of the body the slot is about (for the back the player turns round). With {@code theatre} off the stage is plain:
 * one look, no lamps, the Custom layout's preview.</p>
 */
final class LookStage {

    /** One place in the row. {@code look} is null for the empty place at the end, where a new look is made. */
    static final class Stand {
        @Nullable final ProfileStore.Look look;
        final PivotNode root = new PivotNode();
        final LookNode node;
        final FxNode beam, pool;
        private final Anim lit = new Anim(0, 420, Ease.OUT_CUBIC);

        Stand(@Nullable final ProfileStore.Look look, final LookNode node, final FxNode beam, final FxNode pool) {
            this.look = look;
            this.node = node;
            this.beam = beam;
            this.pool = pool;
        }
    }

    private static final float STIFFNESS = 58f, DAMPING = 15f;
    /** How far apart the looks stand: set for the shape of the view, so five of them fill it. */
    private float pace = 1.62f;
    /** Where the row is turning to, counted on without end: the look in view is this, wrapped to the row. */
    private float target;

    final Stage stage;
    private final boolean theatre;
    private final List<Stand> stands = new ArrayList<>();
    private final PivotNode driver = new PivotNode();
    private int viewed;
    private float position, velocity;
    private String worn = "";
    private final Anim edit = new Anim(0, 560, Ease.IN_OUT_CUBIC);
    private final Anim zoom = new Anim(0, 460, Ease.IN_OUT_CUBIC);
    private final Anim turn = new Anim(0, 520, Ease.IN_OUT_CUBIC);
    @Nullable private Slot focus;
    private final Vector3f focusAt = new Vector3f(0f, 1f, 0f), eye = new Vector3f(), aim = new Vector3f();
    private float focusDistance = 3f;
    private float aspect = 2f;
    @Nullable private IntConsumer onPick;

    LookStage(final Screen owner, final boolean theatre) {
        this.theatre = theatre;
        final Stage s = new Stage().bind(owner);
        this.stage = s;
        s.background(0);
        s.soft(true);
        s.finish(theatre ? StageFinish.cinematic().bloom(0.5f).vignette(0.3f) : StageFinish.glow());
        s.resolutionScale(Minecraft.getInstance().getWindow().getWidth() <= 2048 ? 2f : 1.5f);
        s.focusOutline(false);
        s.camera().idle(IdleMotion.sway(24000f, 1.6f, 0.3f, 0.01f));
        if (theatre) {
            // A dark hall: what light there is comes from the lamps.
            s.lighting().key(1.4f, 4.6f, 4.2f).keyColor(0.7f, 0.64f, 0.56f).ambientColor(0.26f, 0.26f, 0.3f)
                .fill(-1f, 0.4f, 0.4f, 0.1f, 0.13f, 0.2f).rim(1f, 0.9f, 0.76f, 0.3f).wrap(0.5f).sun(0.3f, 1f, 0.6f);
            s.add(FxNode.glow(9f, 0xD01C1B1A).fade(2.2f)).at(0f, -0.005f, -0.5f);
            s.add(new MotesNode(46, 7f, 3.2f, 3f, 0.022f, 0x60FFE7C2, 12L)).at(0f, 1.7f, 0.2f);
        } else {
            s.lighting().key(2f, 4f, 5f).sun(0.3f, 1f, 0.6f);
            s.add(FxNode.glow(3f, 0xC01C1B1A).fade(2f)).at(0f, -0.005f, 0f);
        }
        s.add(driver);
        driver.onFrame(this::frame);
    }

    void onPick(final IntConsumer listener) { this.onPick = listener; }

    List<Stand> stands() { return stands; }

    int viewed() { return viewed; }

    @Nullable
    Stand viewedStand() { return stands.isEmpty() ? null : stands.get(Mth.clamp(viewed, 0, stands.size() - 1)); }

    /** 0 = the row of looks, 1 = one look being edited. */
    float editing() { return edit.get(); }

    float zoomed() { return zoom.get(); }

    /**
     * Sets the row: these looks, and an empty place after them when {@code blank}. The look in view stays in view if
     * it is among them.
     */
    void show(final List<ProfileStore.Look> looks, final boolean blank, @Nullable final String keep) {
        show(looks, blank, keep, -1);
    }

    /** As above; {@code at} (when not negative) is the place in view afterwards, whatever stands there. */
    void show(final List<ProfileStore.Look> looks, final boolean blank, @Nullable final String keep, final int at0) {
        for (final Stand st : stands) {
            stage.remove(st.node);
            stage.remove(st.beam);
            stage.remove(st.pool);
            stage.remove(st.root);
        }
        stands.clear();
        final Theme t = Theme.current();
        final int warm = Colors.lerp(0xFFFFE9C8, t.accent(), t.isVanilla() ? 0f : 0.22f);
        int i = 0;
        final List<ProfileStore.Look> all = new ArrayList<>(looks);
        if (blank || looks.isEmpty()) all.add(null);
        for (final ProfileStore.Look look : all) {
            final LookNode node = look != null ? new LookNode(look)
                : LookNode.ofTexture(SkinComposer.defaultSkin(i % 2 == 0 ? "steve" : "alex", i % 2 != 0), i % 2 != 0);
            if (look == null) node.muted(0.85f);
            final FxNode beam = FxNode.cone(0.16f, 1.05f, 4.4f, Colors.withAlpha(warm, 0x3C));
            final FxNode pool = FxNode.glow(1.25f, Colors.withAlpha(warm, 0x70)).fade(1.7f);
            final Stand st = new Stand(look, node, beam, pool);
            stage.add(st.root);
            stage.add(pool).at(0f, 0.01f, 0f).attachTo(st.root);
            stage.add(node).attachTo(st.root);
            stage.add(beam).attachTo(st.root);
            node.hoverFeel(0.02f, 1.03f);
            final int index = i;
            node.onClick(() -> { if (onPick != null) onPick.accept(index); });
            beam.visible(theatre);
            pool.visible(theatre);
            stands.add(st);
            i++;
        }
        int at = 0;
        if (keep != null) for (int k = 0; k < stands.size(); k++) if (stands.get(k).look != null && stands.get(k).look.id().equals(keep)) at = k;
        if (at0 >= 0) at = Math.min(at0, Math.max(0, stands.size() - 1));
        viewed = at;
        position = at;
        target = at;
        velocity = 0f;
    }

    /** The signed way from 0 to {@code a} round a row of {@code n}: never more than half of it. */
    private static float wrap(final float a, final int n) {
        if (n <= 0) return 0f;
        float r = a % n;
        if (r < 0) r += n;
        if (r > n / 2f) r -= n;
        return r;
    }

    /** Turns the row until the look at {@code index} is in view. */
    void view(final int index) {
        if (stands.isEmpty()) return;
        viewed = Math.floorMod(index, stands.size());
        // The short way round.
        target = position + wrap(viewed - position, stands.size());
    }

    void worn(final String id) { this.worn = id == null ? "" : id; }

    void edit(final boolean on) {
        edit.set(on);
        if (!on) focus(null);
    }

    /** Goes up to what {@code slot} is about; null steps back to the whole player. */
    void focus(@Nullable final Slot slot) {
        this.focus = slot;
        if (slot == null || slot == Slot.SKIN) {
            zoom.set(0f);
            turn.set(0f);
            return;
        }
        turn.set(slot == Slot.BACK ? 1f : 0f);
        switch (slot) {
            case HAT -> { focusAt.set(0f, 1.86f, 0f); focusDistance = 2.5f; }
            case FACE -> { focusAt.set(0f, 1.64f, 0f); focusDistance = 1.9f; }
            case SHIRT -> { focusAt.set(0f, 1.14f, 0f); focusDistance = 2.7f; }
            case PANTS -> { focusAt.set(0f, 0.42f, 0f); focusDistance = 2.7f; }
            case BACK -> { focusAt.set(0f, 1.2f, 0f); focusDistance = 3f; }
            case RIGHT_ARM -> { focusAt.set(-0.36f, 1.1f, 0f); focusDistance = 2.3f; }
            case LEFT_ARM -> { focusAt.set(0.36f, 1.1f, 0f); focusDistance = 2.3f; }
            case RIGHT_LEG -> { focusAt.set(-0.13f, 0.4f, 0f); focusDistance = 2.3f; }
            case LEFT_LEG -> { focusAt.set(0.13f, 0.4f, 0f); focusDistance = 2.3f; }
            default -> { focusAt.set(-0.2f, 1.55f, 0f); focusDistance = 2.7f; }
        }
        zoom.set(1f);
    }

    @Nullable Slot focus() { return focus; }

    /** The shape of the view the stage is drawn into: the camera keeps the five looks inside whatever it is. */
    void shape(final float widthOverHeight) { this.aspect = Math.max(0.6f, widthOverHeight); }

    private void frame(final StageRenderContext ctx) {
        final float motion = Theme.current().motion();
        if (motion <= 0f) {
            position = target;
            velocity = 0f;
        } else {
            float left = Math.min(0.1f, ctx.deltaMs / 1000f) / motion;
            while (left > 0f) {
                final float dt = Math.min(left, 1f / 120f);
                velocity += (STIFFNESS * (target - position) - DAMPING * velocity) * dt;
                position += velocity * dt;
                left -= dt;
            }
        }
        final float e = edit.get();
        for (int i = 0; i < stands.size(); i++) {
            final Stand st = stands.get(i);
            // The row is a ring: after the last look comes the first again, so there is always someone to either side.
            final float d = stands.size() >= 5 ? wrap(i - position, stands.size()) : i - position, far = Math.abs(d);
            final float inView = Math.max(0f, 1f - far);
            // The others leave when one is edited: outwards, and out of sight.
            final float out = 1f + e * 1.4f * (1f - inView);
            final float x = d * pace * out;
            final float z = -(float) Math.pow(Math.min(far, 3.2f), 1.25) * 0.78f;
            final float size = 1f - 0.13f * Math.min(far, 2.4f);
            final float seen = Mth.clamp(2.7f - far, 0f, 1f) * (1f - e * (1f - inView));
            st.root.at(x, 0f, z);
            st.root.scale(size);
            st.root.visible(seen > 0.01f);
            st.root.alpha(seen);
            // Turned a little towards the middle; turned right round to show the back.
            st.node.yaw(-d * 13f * (1f - e) + 180f * turn.get() * inView);
            st.node.pickable(seen > 0.6f && e < 0.5f);
            st.node.lookAtCursor(false);
            final boolean isWorn = st.look != null && st.look.id().equals(worn);
            st.lit.set(isWorn ? 1f : inView > 0.5f ? 0.62f : 0f);
            final float lit = st.lit.get();
            st.beam.alpha(lit * (1f - 0.55f * zoom.get()));
            st.beam.visible(theatre && lit > 0.01f);
            st.pool.alpha(lit);
            st.pool.visible(theatre && lit > 0.01f);
        }
        camera(e);
    }

    private void camera(final float e) {
        // Far enough for five looks side by side, whatever the shape of the view; closer for the one that is edited.
        final float fov = 30f;
        final float tan = (float) Math.tan(Math.toRadians(fov / 2f));
        final float upright = 2.95f / (2f * tan);
        // As far apart as the view is wide: five looks fill four fifths of it, but they never crowd nor scatter.
        pace = Mth.clamp(2f * upright * tan * aspect * 0.8f / 4.6f, 1.45f, 2.3f);
        final float across = (pace * 4.6f) / (2f * aspect * tan * 0.86f);
        final float row = theatre ? Math.max(across, upright) : upright * 1.02f;
        final float one = Math.max(2.5f / (2f * tan), 1.45f / (2f * aspect * tan));
        final float dist = Mth.lerp(e, row, one);
        eye.set(0f, 1.18f + dist * 0.06f, dist);
        aim.set(0f, 1.04f, 0f);
        final float zm = zoom.get();
        if (zm > 0.001f) {
            final float side = turn.get() > 0.5f ? -1f : 1f;
            eye.lerp(new Vector3f(focusAt.x * side, focusAt.y + 0.12f, focusDistance), zm);
            aim.lerp(new Vector3f(focusAt.x * side, focusAt.y, 0f), zm);
        }
        stage.camera().at(eye.x, eye.y, eye.z).lookAt(aim.x, aim.y, aim.z).fov(fov).clip(0.05f, 80f);
        final Stand v = viewedStand();
        if (v != null) v.node.pose(PlayerNode.Pose.STAND);
    }

    void close() {
        stands.clear();
        stage.close();
    }
}
