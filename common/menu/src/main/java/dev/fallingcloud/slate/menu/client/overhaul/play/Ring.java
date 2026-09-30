package dev.fallingcloud.slate.menu.client.overhaul.play;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.stage.Stage;
import dev.fallingcloud.slate.core.stage.StageRenderContext;
import dev.fallingcloud.slate.core.stage.node.CubePlanetNode;
import dev.fallingcloud.slate.core.stage.node.HitNode;
import dev.fallingcloud.slate.core.stage.node.ItemNode;
import dev.fallingcloud.slate.core.stage.node.PivotNode;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * A ring of planets: the worlds, or the servers. The selected planet stands at the front, nearest the viewer and
 * largest; the others follow round the ring, smaller and closer together the further back. Picking another turns
 * the ring until that one is in front. A ring holds any number of entries: {@link #capacity} of them show at once.
 * The rest are in the whirl of light at the back of the ring ({@link RiftNode}, there only while there is a rest):
 * turning the ring draws the planet that leaves into it, small, and brings the next one out of it.
 *
 * <p>Two rings share the stage. One is {@link #active}: full size, its planets there to be picked. The other hangs
 * small in the middle of it, turning slowly, and is one button: clicking it swaps the two, the big one shrinking
 * into the middle while the small one grows out to take its place.</p>
 */
final class Ring {

    static final float RADIUS = 3.3f, MINI_RADIUS = 0.7f, PLANET = 0.86f, MINI_PLANET = 0.25f;
    /** How high the small ring hangs over the floor of the large one: just clear of it, in the middle. */
    static final float MINI_LIFT = 0.14f;
    /** A planet at the back, against one at the side; and a step at the back, against one at the front. */
    private static final float BACK_SIZE = 0.44f, BACK_STEP = 0.42f;
    private static final float RIFT = 0.62f;
    /** How far a planet's top is tipped towards the viewer, who already looks down on the ring. */
    private static final float TILT = 4f;
    private static final float STIFFNESS = 64f, DAMPING = 16f;
    /** Planets made per frame: a planet is a mesh to build, and a ring that opens must not stall on a dozen. */
    private static final int GROW_PER_FRAME = 2;

    final boolean servers;
    final PivotNode pivot = new PivotNode();
    final HitNode miniHit = new HitNode();
    private final OrbitNode orbit;
    private final RiftNode rift;
    private float riftShown;
    private final Stage stage;
    private final Anim active;
    private final Anim miniHover = new Anim(0, 180, Ease.OUT_CUBIC);
    private List<RingEntry> shown = List.of();
    private int selected;
    private float position, target, velocity;
    private float idle;
    private int capacity = 11;
    @Nullable private Consumer<RingEntry> onPick;

    Ring(final Stage stage, final boolean servers, final boolean startsActive, final Runnable onMini, final Component miniTip) {
        this.stage = stage;
        this.servers = servers;
        this.active = new Anim(startsActive ? 1f : 0f, 680, Ease.IN_OUT_CUBIC);
        stage.add(pivot);
        final Theme theme = Theme.current();
        orbit = stage.add(new OrbitNode(1f, 0.012f, Colors.withAlpha(Colors.lerp(0xFFFFE9C8, theme.accent(), theme.isVanilla() ? 0f : 0.4f), 0x70)));
        orbit.attachTo(pivot);
        final int warm = Colors.lerp(0xFFFFE9C8, theme.accent(), theme.isVanilla() ? 0f : 0.25f);
        rift = stage.add(new RiftNode(RIFT, warm, theme.isVanilla() ? 0xFFFFFFFF : theme.accent(), servers ? 41L : 17L));
        rift.attachTo(pivot);
        stage.add(miniHit);
        miniHit.bounds(-MINI_RADIUS - 0.3f, -0.3f, -MINI_RADIUS - 0.3f, MINI_RADIUS + 0.3f, 0.35f, MINI_RADIUS + 0.3f);
        miniHit.named(miniTip).tooltip(miniTip).onClick(onMini).onHover(miniHover::set);
        miniHit.attachTo(pivot);
        pivot.onFrame(this::frame);
    }

    /** Told which planet was clicked (the ring has not turned yet). */
    void onPick(final Consumer<RingEntry> listener) { this.onPick = listener; }

    /** How many planets show at once (4..24). */
    void capacity(final int n) { this.capacity = Mth.clamp(n, 4, 24); }

    List<RingEntry> shown() { return shown; }

    int selectedIndex() { return selected; }

    @Nullable RingEntry selected() { return shown.isEmpty() ? null : shown.get(Mth.clamp(selected, 0, shown.size() - 1)); }

    boolean isActive() { return active.target() > 0.5f; }

    /** 0 = hanging small in the middle, 1 = the ring in use; in between while the two swap. */
    float activeAmount() { return active.get(); }

    void active(final boolean on) { active.set(on); }

    /** The ring in its state at once, without the swap: for a screen that opens on it. */
    void activeNow(final boolean on) { active.snap(on ? 1f : 0f); }

    /**
     * Shows these entries, in this order. The planet with id {@code keep} stays selected if it is among them, else the
     * first is. The ring jumps to its new state: a changed list has no "before" to turn from.
     */
    void show(final List<RingEntry> entries, @Nullable final String keep) {
        for (final RingEntry e : shown) if (!entries.contains(e)) release(e);
        shown = new ArrayList<>(entries);
        selected = 0;
        if (keep != null) {
            for (int i = 0; i < shown.size(); i++) if (shown.get(i).id().equals(keep)) { selected = i; break; }
        }
        position = target = selected;
        velocity = 0f;
    }

    /** Turns the ring, the short way round, until the planet at {@code index} is in front. */
    void select(final int index) {
        final int m = shown.size();
        if (m == 0) return;
        selected = Math.floorMod(index, m);
        target = position + wrap(selected - position, m);
    }

    void select(final RingEntry entry) {
        final int i = shown.indexOf(entry);
        if (i >= 0) select(i);
    }

    void step(final int by) {
        if (!shown.isEmpty()) select(selected + by);
    }

    /**
     * How far round the ring a planet stands that is {@code u} steps from the front, in radians: the steps shorten
     * towards the back, and {@code half} steps reach it.
     */
    private static float round(final float u, final float half) {
        final float x = Math.min(u, half);
        return Mth.PI * (x - (1f - BACK_STEP) * x * x / (2f * half)) / (half * (1f + BACK_STEP) / 2f);
    }

    /** The signed way from 0 to {@code a} round a ring of {@code m}: never more than half the ring. */
    private static float wrap(final float a, final int m) {
        if (m <= 0) return 0f;
        float r = a % m;
        if (r < 0) r += m;
        if (r > m / 2f) r -= m;
        return r;
    }

    private void frame(final StageRenderContext ctx) {
        final int m = shown.size();
        final float motion = Theme.current().motion();
        if (motion <= 0f) {
            position = target;
            velocity = 0f;
        } else {
            // A damped spring, in small steps so a slow frame cannot throw it.
            float left = Math.min(0.1f, ctx.deltaMs / 1000f) / motion;
            while (left > 0f) {
                final float dt = Math.min(left, 1f / 120f);
                velocity += (STIFFNESS * (target - position) - DAMPING * velocity) * dt;
                position += velocity * dt;
                left -= dt;
            }
            idle += ctx.deltaMs / 1000f * 0.22f;
        }
        final float act = active.get();
        pivot.at(0f, Mth.lerp(act, MINI_LIFT, 0f), 0f);
        miniHit.pickable(act < 0.04f);
        final float miniGrow = 1f + 0.14f * miniHover.get() * (1f - act);
        final float radius = Mth.lerp(act, MINI_RADIUS * miniGrow, RADIUS);
        orbit.scale(radius);
        orbit.alpha(m == 0 ? 0.45f * act : Mth.lerp(act, 0.5f + 0.5f * miniHover.get(), 1f));

        final int window = Math.min(m, capacity);
        // Steps from the front of the ring to its back, where the whirl is: a ring that shows all it has keeps the
        // back open all the same, and a ring of two or three keeps the spacing of a fuller one.
        final float half = Math.max(window, 6) / 2f + 0.5f;
        final float even = window <= 1 ? 0f : Mth.TWO_PI / Math.max(window, 6);
        final boolean more = m > capacity;
        // The whirl: there while there is more than the ring shows, and only on the ring in use.
        final float want = more ? act : 0f;
        riftShown = motion <= 0f ? want : riftShown + (want - riftShown) * Math.min(1f, ctx.deltaMs / 260f);
        rift.at(0f, 0.2f, -radius);
        rift.strength(riftShown);
        rift.visible(riftShown > 0.01f);
        int grown = 0;
        for (int i = 0; i < m; i++) {
            final RingEntry e = shown.get(i);
            final float d = wrap(i - position, m);
            final float u = Math.abs(d);
            final float edge = more ? Mth.clamp(half - u, 0f, 1f) : 1f;
            if (edge <= 0.001f) {
                // In the whirl: off the stage once it is well inside, so a long list stays cheap.
                if (e.holder != null) {
                    if (u > half + 1.5f) release(e);
                    else e.holder.visible(false);
                }
                continue;
            }
            if (e.holder == null) {
                if (grown >= GROW_PER_FRAME && u > 0.5f) continue;
                grow(e);
                grown++;
            }
            final PivotNode holder = e.holder;
            final CubePlanetNode planet = e.planet;
            if (holder == null || planet == null) continue;

            // Coming onto the stage a planet grows from nothing, so one that was just made does not pop in.
            e.shown = motion <= 0f ? 1f : Math.min(1f, e.shown + ctx.deltaMs / (260f * motion));
            final float born = Ease.OUT_CUBIC.apply(e.shown);
            // The small ring is an even one; the large one draws together towards its back.
            final float turn = Math.signum(d) * Mth.lerp(act, u * even, round(u, half));
            final float angle = turn + idle * (1f - act);
            final float depth = (1f + Mth.cos(turn)) / 2f;                             // 1 in front, 0 at the back
            final float front = Math.max(0f, 1f - u);                                  // 1 for the planet in front
            final float full = PLANET * Mth.lerp(depth, BACK_SIZE, 1f) * (1f + 0.36f * front) * (1f + 0.06f * planet.hover());
            final float bob = motion > 0f ? Mth.sin(ctx.seconds() * 0.9f + i * 1.9f) * 0.045f * act : 0f;
            holder.visible(true);
            holder.at(Mth.sin(angle) * radius, bob + 0.16f * front * act + 0.2f * (1f - edge), Mth.cos(angle) * radius);
            // What goes into the whirl grows small on its way, and what comes out of it grows.
            holder.scale(Math.max(0.001f, Mth.lerp(act, MINI_PLANET * miniGrow, full) * born * (0.2f + 0.8f * edge)));
            holder.alpha(edge);
            planet.spin(5f + 11f * front);
            // Clouds are for planets large enough to have weather: on the small ring they would be so much dust.
            planet.clouds(act > 0.45f);
            planet.pickable(act > 0.96f && edge > 0.5f);
            planet.tooltip(front > 0.5f ? null : Component.literal(e.name()));
            if (e instanceof RingEntry.Server s) planet.ring(s.statusColor());
            if (e.star != null) e.star.visible(e.favorite() && act > 0.45f);
        }
    }

    private void grow(final RingEntry e) {
        final PivotNode holder = stage.add(new PivotNode());
        holder.rotate(0f, TILT, 0f).attachTo(pivot);
        final CubePlanetNode planet = stage.add(e.grow());
        planet.size(1f);
        planet.yaw((float) (Math.floorMod(CubePlanetNode.seedOf(e.id()), 360L)));
        planet.named(e.name()).attachTo(holder);
        planet.onClick(() -> { if (onPick != null) onPick.accept(e); });
        // The mark of a favourite: a star over the planet, turning.
        final ItemNode star = stage.add(new ItemNode(new ItemStack(Items.NETHER_STAR)));
        star.context(ItemDisplayContext.GROUND).size(0.62f).spin(50f).bob(0.03f);
        star.pickable(false).at(0f, 0.86f, 0f).attachTo(holder);
        star.visible(e.favorite());
        e.holder = holder;
        e.planet = planet;
        e.star = star;
        e.shown = 0f;
    }

    private void release(final RingEntry e) {
        if (e.planet != null) stage.remove(e.planet);
        if (e.star != null) stage.remove(e.star);
        if (e.holder != null) stage.remove(e.holder);
        e.planet = null;
        e.star = null;
        e.holder = null;
    }

    /** Takes every planet off the stage (the ring itself stays). */
    void clear() {
        for (final RingEntry e : shown) release(e);
        shown = List.of();
        selected = 0;
        position = target = velocity = 0f;
    }
}
