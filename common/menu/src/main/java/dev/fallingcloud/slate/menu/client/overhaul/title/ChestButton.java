package dev.fallingcloud.slate.menu.client.overhaul.title;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.module.Features;
import dev.fallingcloud.slate.core.module.KnownModules;
import dev.fallingcloud.slate.core.stage.Stage;
import dev.fallingcloud.slate.core.stage.StageRenderContext;
import dev.fallingcloud.slate.core.stage.node.BurstNode;
import dev.fallingcloud.slate.core.stage.node.ChestNode;
import dev.fallingcloud.slate.core.stage.node.FxNode;
import dev.fallingcloud.slate.core.stage.node.HitNode;
import dev.fallingcloud.slate.core.stage.node.PivotNode;
import dev.fallingcloud.slate.core.stage.node.SignBoardNode;
import dev.fallingcloud.slate.core.stage.node.StageNode;
import dev.fallingcloud.slate.core.stage.node.TextNode;
import dev.fallingcloud.slate.core.stage.scene.Anchor;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * One chest of the Overhaul main menu, which is two buttons. The open lid is one: in the recessed panel of its
 * inside sit a thing in 3D (a planet, the player, a cogwheel) and its name under it (Play, Profile, Options). The
 * sign on the chest's front is the other (Continue, Friends, Quit game). Everything rides on one pivot standing on
 * the chest's anchor, so the whole thing can be placed and turned as one.
 *
 * <p>The chest opens at {@link #openAtMs} with a puff of sparks; as the lid comes to rest the button appears on it.
 * A button whose module is not installed stays on screen, faded, and says what to install.</p>
 */
final class ChestButton {

    /** How far past upright the lid opens: its panel then looks at a camera that looks a little down. */
    private static final float LID_TILT = 7f;
    /**
     * The middle of the open lid's panel, in the chest's own space: the hinge (top of the base's back edge) plus the
     * panel's middle (4 pixels into the lid, 7 along it) turned by the lid's angle.
     */
    private static final float PANEL_Y, PANEL_Z;

    static {
        final double a = Math.toRadians(-(90.0 + LID_TILT));
        PANEL_Y = (float) (9.0 / 16.0 + (4.0 * Math.cos(a) - 7.0 * Math.sin(a)) / 16.0);
        PANEL_Z = (float) (-7.0 / 16.0 + (4.0 * Math.sin(a) + 7.0 * Math.cos(a)) / 16.0);
    }

    // In the panel's own space: X to the right, Y up the panel, Z out of it towards the viewer. The panel is 12 pixels
    // square (0.75 of a block) and lies 4 pixels deep in the frame the lid's walls make.
    // The thing and its name under it are one button, and all of the button is inside the panel: nothing of it over
    // the lid's walls, whichever chest it is and wherever the pointer. The thing may be 0.47 across, its middle a
    // little over the panel's; the name as far under the middle as the thing is over it.
    private static final float THING_Y = 0.07f, THING_Z = 0.2f, THING_GROWS = 0.06f;
    private static final float LABEL_Y = -0.275f, LABEL_SIZE = 0.0128f, LABEL_ROOM = 0.62f;
    /** How large what is shown on a lid may be, across and up: for whoever makes one. */
    static final float THING_SIZE = 0.47f;
    /**
     * The sign: in the middle of the chest's front, which is 14 pixels wide and 9 high, as much wood over it as
     * under it and the same at its sides; its back just off the wood.
     */
    private static final float SIGN_SCALE = 0.75f, SIGN_Y = (9f / 16f - 0.5f * SIGN_SCALE) / 2f, SIGN_Z = 0.4475f;
    private static final int SIGN_INK = 0xFF2A1B0C, SIGN_INK_HOVER = 0xFFFFFFFF, SIGN_INK_LOCKED = 0xFF9C8458;
    private static final int GLOW = 0xFFFFE2B8;

    final PivotNode root = new PivotNode();
    final ChestNode chest;
    final PivotNode signPivot = new PivotNode();
    final SignBoardNode sign;
    final TextNode signText;
    /** The plane of the open lid's panel. */
    final PivotNode face = new PivotNode();
    final PivotNode floater = new PivotNode();
    final TextNode label;
    final FxNode lidGlow;
    final BurstNode sparks;
    final HitNode lidHit = new HitNode();
    final HitNode signHit = new HitNode();

    private final float openAtMs;
    private final boolean sounds;
    private final boolean lidLocked, signLocked;
    private boolean opened;
    private boolean signDisabled;
    private final Anim pop = new Anim(0, 520, Ease.OUT_BACK);
    private final Anim labelIn = new Anim(0, 300, Ease.OUT_CUBIC);
    private final Anim lidHover = new Anim(0, 200, Ease.OUT_CUBIC);
    private final Anim signHover = new Anim(0, 170, Ease.OUT_CUBIC);
    private final float bobPhase;
    /** Where the camera comes to rest, in the scene; null until it is known. */
    @Nullable private org.joml.Vector3f eye;
    private final org.joml.Vector3f seen = new org.joml.Vector3f();
    @Nullable private HoverListener lidListener;

    /** Told how far the pointer is "on" the lid button (0..1) every frame, to drive whatever floats there. */
    interface HoverListener {
        void hover(float amount, boolean entered);
    }

    /**
     * @param lidModule  the module the lid button needs, or null when it is always there
     * @param signModule the same for the sign
     */
    ChestButton(final Stage stage, final Anchor anchor, final int index, final float openAtMs, final boolean sounds,
                final Component lidLabel, final Runnable lidAction, @Nullable final String lidModule,
                final Component signLabel, final Runnable signAction, @Nullable final String signModule) {
        this.openAtMs = openAtMs;
        this.sounds = sounds;
        this.bobPhase = index * 1.7f;
        this.lidLocked = lidModule != null && !Features.present(lidModule);
        this.signLocked = signModule != null && !Features.present(signModule);

        anchor.place(stage.add(root));

        chest = stage.add(new ChestNode());
        chest.lidAngle(90f + LID_TILT).pickable(false).attachTo(root);

        stage.add(signPivot).at(0f, SIGN_Y, SIGN_Z).scale(SIGN_SCALE).attachTo(root);
        sign = stage.add(new SignBoardNode());
        sign.pickable(false).attachTo(signPivot);
        signText = stage.add(new TextNode(signLabel));
        // The board is one block wide: the text takes what it needs of it, and never more than the board has.
        final int signW = Math.max(1, Minecraft.getInstance().font.width(signLabel));
        signText.billboard(false).background(0).size(Math.min(0.0205f, 0.84f / signW)).color(signLocked ? SIGN_INK_LOCKED : SIGN_INK);
        signText.at(0f, SignBoardNode.FACE_MID_Y, SignBoardNode.FACE_Z + 0.004f).attachTo(signPivot);

        // The button on the lid: a soft light on the panel, the thing floating in its frame, its name under it.
        stage.add(face).at(0f, PANEL_Y, PANEL_Z).rotate(0f, -LID_TILT, 0f).attachTo(root);
        // Wider than the panel and cut to it: what shows is light on the wood, with no rim of its own.
        lidGlow = stage.add(FxNode.glow(0.66f, Colors.withAlpha(GLOW, 0xFF)).clip(0.375f, 0.375f));
        lidGlow.rotate(0f, 90f, 0f).at(0f, 0f, 0.008f).attachTo(face);
        lidGlow.alpha(0f);
        stage.add(floater).at(0f, THING_Y, THING_Z).attachTo(face);
        final Component name = Fonts.heading(lidLabel);
        label = stage.add(new TextNode(name));
        final int nameW = Math.max(1, Minecraft.getInstance().font.width(name));
        label.billboard(false).background(0).shadow(true).size(Math.min(LABEL_SIZE, LABEL_ROOM / nameW)).color(lidLocked ? 0xFF9A9890 : 0xFFFFFFFF);
        label.at(0f, LABEL_Y, 0.014f).attachTo(face);
        label.alpha(0f);

        sparks = stage.add(new BurstNode(26, 0.62f, 0.05f, Colors.withAlpha(Colors.lerp(GLOW, Theme.current().accent(), 0.35f), 0xE0), 11L + index));
        sparks.at(0f, 0.6f, 0f).attachTo(root);

        // The two buttons: the open lid with what is on it, and the chest's body with its sign.
        stage.add(lidHit).bounds(-0.4375f, -0.44f, -0.06f, 0.4375f, 0.44f, 0.64f).named(lidLabel).attachTo(face);
        stage.add(signHit).bounds(-0.5f, 0f, -0.44f, 0.5f, 0.56f, 0.56f).named(signLabel).attachTo(root);
        lidHit.onHover(h -> { lidHover.set(h); if (lidListener != null && h) lidListener.hover(lidHover.get(), true); });
        signHit.onHover(signHover::set);
        // A button whose module is missing stays, says what to install when pointed at, and again when clicked.
        if (lidLocked) lidHit.tooltip(Features.lockedTooltip(lidModule)).onClick(() -> locked(lidModule));
        else lidHit.onClick(lidAction);
        if (signLocked) signHit.tooltip(Features.lockedTooltip(signModule)).onClick(() -> locked(signModule));
        else signHit.onClick(signAction);
        lidHit.pickable(false);                 // nothing to click until the button is on the lid

        root.onFrame(this::frame);
    }

    /** The thing on the lid: rides on {@link #floater}, which does the appearing, the bobbing and the zoom. */
    <T extends StageNode> T show(final Stage stage, final T node) {
        stage.add(node);
        node.pickable(false).hoverFeel(0f, 1f).attachTo(floater);
        if (lidLocked) node.muted(1f);
        return node;
    }

    /**
     * Where the camera comes to rest. The thing floats before the panel, and what floats before something is only seen
     * in its middle from straight ahead: so it floats on the line from the panel's middle to the camera, and is seen
     * in the middle of the lid from where the player sees it, whichever chest it is.
     */
    ChestButton seenFrom(final org.joml.Vector3f camera) {
        this.eye = new org.joml.Vector3f(camera);
        return this;
    }

    ChestButton onLidHover(final HoverListener listener) {
        this.lidListener = listener;
        return this;
    }

    boolean lidLocked() { return lidLocked; }

    boolean signLocked() { return signLocked; }

    /** A tooltip for the sign while it is not locked (what Continue would open). */
    void signTip(@Nullable final Component tip) {
        if (!signLocked) signHit.tooltip(tip);
    }

    /** The sign has nothing to do (nothing to continue yet): it stays, greyed out, and says why. */
    void disableSign(final Component why) {
        if (signLocked) return;
        signHit.onClick(null);
        signHit.pickable(true);
        signHit.tooltip(why);
        signText.color(SIGN_INK_LOCKED);
        signDisabled = true;
    }

    /** Undoes {@link #disableSign}. */
    void enableSign(final Runnable action) {
        if (signLocked) return;
        signDisabled = false;
        signHit.onClick(action);
    }

    private static void locked(final String module) {
        SlateToasts.show(KnownModules.name(module), Features.lockedTooltip(module), Icon.LOCK);
    }

    /** Everything in its end state, now: for animations off and for skipping the intro. */
    void finish() {
        if (!opened) opened = true;
        chest.openNow(true);
        pop.snap(1f);
        labelIn.snap(1f);
    }

    boolean settled() { return opened && pop.get() >= 0.999f && chest.openness() >= 0.999f && chest.openness() <= 1.001f; }

    private void frame(final StageRenderContext ctx) {
        final float motion = Theme.current().motion();
        if (motion <= 0f) {
            finish();
        } else {
            final float t = ctx.timeMs / motion;
            if (!opened && t >= openAtMs) {
                opened = true;
                chest.open(true);
                sparks.fire();
                if (sounds && Theme.current().uiSounds()) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHEST_OPEN, 0.95f + 0.06f * bobPhase, 0.32f));
                }
            }
            if (opened && t >= openAtMs + 380f) pop.set(1f);
            if (opened && t >= openAtMs + 560f) labelIn.set(1f);
        }

        // The thing on the lid: grows into place with a little overshoot, then bobs; a pointer on it brings it closer.
        final float shown = Mth.clamp(pop.get(), 0f, 1.2f);
        final float hover = lidLocked ? 0f : lidHover.get();
        final float bob = motion > 0f ? Mth.sin(ctx.seconds() * 1.5f + bobPhase) * 0.012f * Math.min(1f, shown) : 0f;
        floater.visible(shown > 0.01f);
        floater.scale(Math.max(0.001f, shown) * (1f + THING_GROWS * hover));
        final float out = THING_Z + 0.03f * hover, up = THING_Y + bob * 0.6f;
        float aside = 0f, lift = 0f;
        if (eye != null) {
            // The camera as the panel sees it: x to its right, y up it, z out of it.
            face.modelInverse.transformPosition(eye, seen);
            if (seen.z > 1f) {
                aside = seen.x / seen.z * out;
                lift = (seen.y - up) / seen.z * out;
            }
        }
        floater.at(aside, up + lift, out);
        if (lidListener != null) lidListener.hover(hover, false);
        lidHit.pickable(shown > 0.6f);

        final float in = labelIn.get();
        label.alpha(in);
        label.visible(in > 0.01f);
        if (!lidLocked) label.color(Colors.lerp(0xFFFFFFFF, Colors.lerp(0xFFFFFFFF, Theme.current().accent(), 0.85f), hover));
        // The panel lights up behind its button, and more under the pointer.
        final float lit = Math.min(1f, shown) * (lidLocked ? 0.04f : 0.07f + 0.2f * hover);
        lidGlow.alpha(lit);
        lidGlow.visible(lit > 0.01f);

        // The sign leans out a little under the pointer and its writing lights up.
        final float sh = signLocked || signDisabled ? signHover.get() * 0.25f : signHover.get();
        signPivot.scale(SIGN_SCALE * (1f + 0.06f * sh));
        // It grows about its own middle, so it stays in the middle of the wood.
        signPivot.at(0f, SIGN_Y - 0.25f * SIGN_SCALE * 0.06f * sh, SIGN_Z + 0.04f * sh);
        if (!signLocked && !signDisabled) signText.color(Colors.lerp(SIGN_INK, SIGN_INK_HOVER, sh));
    }

    /** A pool of light on the floor under the chest, and the shade right under it that makes it stand there. */
    static FxNode pool(final Stage stage, final Anchor anchor, final int argb) {
        final FxNode glow = stage.add(FxNode.glow(2.3f, argb));
        glow.at(anchor.x(), anchor.y() + 0.004f, anchor.z() + 0.3f);
        stage.add(FxNode.glow(0.95f, 0xB0000000)).at(anchor.x(), anchor.y() + 0.008f, anchor.z() + 0.05f);
        return glow;
    }
}
