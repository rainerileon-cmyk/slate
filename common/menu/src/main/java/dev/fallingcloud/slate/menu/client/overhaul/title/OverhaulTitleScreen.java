package dev.fallingcloud.slate.menu.client.overhaul.title;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.client.DevHarness;
import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.module.KnownModules;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.screen.slot.CoreSlots;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.core.stage.Stage;
import dev.fallingcloud.slate.core.stage.StageWidget;
import dev.fallingcloud.slate.core.stage.StageFinish;
import dev.fallingcloud.slate.core.stage.node.CubePlanetNode;
import dev.fallingcloud.slate.core.stage.node.FxNode;
import dev.fallingcloud.slate.core.stage.node.ModelNode;
import dev.fallingcloud.slate.core.stage.node.MotesNode;
import dev.fallingcloud.slate.core.stage.node.PivotNode;
import dev.fallingcloud.slate.core.stage.node.PlayerNode;
import dev.fallingcloud.slate.core.stage.scene.Scene;
import dev.fallingcloud.slate.core.stage.scene.SceneLoader;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.menu.MenuConfig;
import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.menu.client.Fmt;
import dev.fallingcloud.slate.menu.client.LastPlayed;
import dev.fallingcloud.slate.menu.client.ModsScreenOpener;
import dev.fallingcloud.slate.menu.mixin.SplashRendererAccessor;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The Overhaul main menu: a Minecraft scene with three chests under a big, centred logo. The camera flies in, the
 * chests open one by one, and out of each lid rises a button: a cube of world circled by clouds (Play), the
 * player's own head (Profile), a cogwheel (Options). The sign on the front of each chest is a button too:
 * Continue, Friends, Quit game.
 *
 * <p>The scene is {@code slate_menu:title} ({@code assets/slate_menu/slate/scenes/title.json}): for now three chests
 * on a dark floor. It names the anchors {@code chest_play}, {@code chest_profile} and {@code chest_options}, so a
 * scene built in the game and captured with {@code /slate scene capture} drops in without touching this class.</p>
 *
 * <p>The fly-in plays once per game session; coming back from another screen the chests just pop open. A click or a
 * key skips whatever is still moving.</p>
 */
public final class OverhaulTitleScreen extends SlateScreen {

    public static final ResourceLocation SCENE = SlateMenu.id("title");
    /** Create's own large cogwheel when that mod is installed, else Slate's stand-in made of vanilla textures. */
    private static final ResourceLocation CREATE_COG = ResourceLocation.fromNamespaceAndPath("create", "block/large_cogwheel");
    private static final ResourceLocation SLATE_COG = SlateMenu.id("stage/large_cogwheel");
    /** The planet on the Play button: always the same one. */
    private static final long PLAY_SEED = 20260929L;
    /** The head on the Profile lid: how far it turns to either side by itself, and how far it nods to a pointer, in degrees. */
    private static final float HEAD_TURN = 18f, HEAD_NOD = 7f;

    /** The cinematic runs once per session. */
    private static boolean introPlayed;

    private final LogoRenderer logo = new LogoRenderer(false);
    private final List<ChestButton> chests = new ArrayList<>();
    private final List<SlateIconButton> corner = new ArrayList<>();
    private final Anim logoIn = new Anim(0, 700, Ease.OUT_CUBIC);
    private final Anim chromeIn = new Anim(0, 500, Ease.OUT_CUBIC);
    @Nullable private Stage stage;
    @Nullable private ChestButton play, profile, options;
    @Nullable private String splash;
    @Nullable private LastPlayed.Target continueTarget;
    private boolean cinematic;
    private boolean skipped;
    private long sceneStartMs;
    private float planetSpin = 16f;

    public OverhaulTitleScreen() {
        super(Component.translatable("narrator.screen.title"), null);
        this.showHeader = false;
        this.showBack = false;
    }

    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override public boolean isPauseScreen() { return false; }

    // ------------------------------------------------------------------ build

    @Override
    protected void build() {
        final Minecraft mc = Minecraft.getInstance();
        final MenuConfig cfg = SlateMenu.config();
        if (stage == null || stage.isClosed()) createScene();
        if (splash == null && cfg.splash) {
            final SplashRenderer s = mc.getSplashManager().getSplash();
            splash = s == null ? null : ((SplashRendererAccessor) s).slate$splash();
        }
        add(new StageWidget(0, 0, width, height, stage, getTitle()));
        corner.clear();

        // The small things vanilla keeps on its title screen, in the corner opposite the version: they must stay
        // reachable, not loud, and the logo has the top of the screen to itself.
        int x = width - PAD - 20;
        final int y = height - 24;
        if (cfg.showModsButton) {
            final var mods = ModsScreenOpener.factory();
            if (mods.isPresent()) {
                corner(new SlateIconButton(x, y, 20, Icon.MODS, Component.translatable("slate_menu.title.mods"), () -> mc.setScreen(mods.get().apply(this))));
                x -= 22;
            }
        }
        corner(new SlateIconButton(x, y, 20, Icon.CAMERA, Component.translatable("slate_menu.screenshots.title"), () -> MenuSlots.open(CoreSlots.SCREENSHOTS, this)));
        x -= 22;
        if (mc.allowsRealms()) {
            corner(new SlateIconButton(x, y, 20, Icon.REALMS, Component.translatable("menu.online"), () -> mc.setScreen(new com.mojang.realmsclient.RealmsMainScreen(this))));
            x -= 22;
        }
        corner(new SlateIconButton(x, y, 20, Icon.ACCESSIBILITY, Component.translatable("options.accessibility.title"),
            () -> mc.setScreen(new AccessibilityOptionsScreen(this, mc.options))));
        x -= 22;
        corner(new SlateIconButton(x, y, 20, Icon.LANGUAGE, Component.translatable("options.language"),
            () -> mc.setScreen(new LanguageSelectScreen(this, mc.options, mc.getLanguageManager()))));
    }

    private static dev.fallingcloud.slate.core.stage.scene.Anchor profileAt(final Scene scene) {
        return scene.anchorOr("chest_profile", 0f, 0f, 0f, 0f);
    }

    private void corner(final SlateIconButton button) {
        corner.add(add(button));
    }

    /** The scene's own field of view, which is for a wide window. */
    private float restFov = 24.1f;

    /**
     * The field of view for this window: the scene's own, or as much more as it takes for the three chests to stay
     * in a window that is narrow.
     */
    private float fovFor(final float aspect) {
        return Math.max(restFov, (float) Math.toDegrees(2.0 * Math.atan(0.355 / Math.max(0.5f, aspect))));
    }

    private void createScene() {
        final Minecraft mc = Minecraft.getInstance();
        final Theme theme = Theme.current();
        chests.clear();
        skipped = false;
        cinematic = (!introPlayed || DevHarness.replayIntros()) && theme.motion() > 0f;
        introPlayed = true;
        sceneStartMs = Clock.nowMs();

        final Stage s = new Stage().bind(this);
        stage = s;
        final Scene scene = SceneLoader.load(SCENE);
        scene.apply(s);
        // On the vanilla style the panorama stays behind the scene; on Slate's the scene brings its own dark.
        if (theme.isVanilla() && SlateMenu.config().panorama) s.background(0);
        s.focusOutline(true);
        // The look of a trailer shot: glow on what is bright, fuller colour, corners falling into shade; and the
        // scene rendered at twice the size it is shown at, which is what takes the stair-steps off small voxels.
        s.finish(StageFinish.cinematic());
        // Everything in the scene under one soft light: a warm lamp up front to the right, a cool breath from the left.
        s.soft(true);
        s.lighting().key(profileAt(scene).x() + 2.4f, profileAt(scene).y() + 4.6f, profileAt(scene).z() + 5.8f);
        s.resolutionScale(mc.getWindow().getWidth() <= 2048 ? 2f : 1.5f);
        if (!cinematic) s.camera().timeline().skip(s.camera().pose());

        final var playAt = scene.anchorOr("chest_play", -1f, 0f, 0f, 0f);
        final var profileAt = scene.anchorOr("chest_profile", 0f, 0f, 0f, 0f);
        final var optionsAt = scene.anchorOr("chest_options", 1f, 0f, 0f, 0f);

        // A dark floor that fades into the background, a pool of light under every chest, dust in the light.
        s.add(FxNode.glow(16f, 0xFF2B2A27).fade(2.6f)).at(profileAt.x(), profileAt.y(), profileAt.z() + 0.5f);
        final int pool = Colors.withAlpha(Colors.lerp(0xFFFFE9C8, theme.accent(), 0.25f), 0x2E);
        ChestButton.pool(s, playAt, pool);
        ChestButton.pool(s, profileAt, pool);
        ChestButton.pool(s, optionsAt, pool);
        s.add(new MotesNode(54, 5.2f, 2.6f, 2.6f, 0.02f, 0x66FFE7C2, 4L)).at(profileAt.x(), profileAt.y() + 0.1f, profileAt.z() + 0.4f);

        final float first = cinematic ? 2350f : 80f, step = cinematic ? 390f : 110f;
        play = new ChestButton(s, playAt, 0, first, cinematic,
            Component.translatable("slate_menu.overhaul.play"), () -> mc.setScreen(new SelectWorldScreen(this)), null,
            Component.translatable("slate_menu.overhaul.continue"), this::continuePlaying, null);
        profile = new ChestButton(s, profileAt, 1, first + step, cinematic,
            Component.translatable("slate_menu.overhaul.profile"), () -> MenuSlots.open(CoreSlots.PROFILE, this), KnownModules.PROFILE,
            Component.translatable("slate_menu.overhaul.friends"), () -> MenuSlots.open(CoreSlots.FRIENDS, this), KnownModules.MULTIPLAYER);
        options = new ChestButton(s, optionsAt, 2, first + step * 2f, cinematic,
            Component.translatable("slate_menu.overhaul.options"), () -> mc.setScreen(new OptionsScreen(this, mc.options)), null,
            Component.translatable("slate_menu.overhaul.quit"), () -> quit(mc), null);
        chests.add(play);
        chests.add(profile);
        chests.add(options);
        for (final ChestButton chest : chests) chest.seenFrom(scene.camera().rest().position);
        restFov = scene.camera().rest().fov;

        // Play: a cube planet, tipped towards the viewer and turning, its clouds circling the other way. A pointer
        // on it speeds both up.
        final PivotNode tilt = play.show(s, new PivotNode());
        tilt.rotate(0f, 24f, 0f);
        final CubePlanetNode planet = s.add(CubePlanetNode.create(PLAY_SEED, CubePlanetNode.Kind.TEMPERATE));
        // A cube that turns is, seen from its corner, half as wide again as its side, and its clouds a little more.
        planet.size(ChestButton.THING_SIZE / 1.62f).pickable(false).attachTo(tilt);
        planetSpin = 16f;
        planet.spin(planetSpin);
        planet.yaw(35f);
        play.onLidHover((amount, entered) -> {
            final float dt = Clock.frameDelta() / 1000f;
            planetSpin = Mth.lerp(Math.min(1f, dt * 6f), planetSpin, 16f + 84f * amount);
            planet.spin(planetSpin);
            planet.cloudSpeed(-7f - 38f * amount);
        });

        // Profile: the player's head, as they look, in the middle of the lid. It turns a little from side to side by
        // itself, and to the viewer with a nod when pointed at; the pointer is nothing it follows about.
        final PivotNode turn = profile.show(s, new PivotNode());
        final PlayerNode me = s.add(PlayerNode.local());
        me.show(EnumSet.of(PlayerNode.Part.HEAD)).lookAtCursor(false).breathe(false);
        // A head is half a block, nine sixteenths with what is worn on it, and a quarter wider again when it is turned
        // furthest: that is when it is as wide as the button may be. Its middle is a block and three quarters over
        // the feet, and that is what is in the middle of the lid.
        final float head = ChestButton.THING_SIZE / 0.71f;
        me.scale(head).at(0f, -1.75f * head, 0f).pickable(false).hoverFeel(0f, 1f).attachTo(turn);
        if (profile.lidLocked()) me.muted(1f);
        final long began = Clock.nowMs();
        profile.onLidHover((amount, entered) -> {
            final float sway = Theme.current().motion() > 0f ? Mth.sin((Clock.nowMs() - began) / 1000f * 0.9f) : -0.8f;
            turn.rotate(HEAD_TURN * sway * (1f - amount), HEAD_NOD * amount, 0f);
        });

        // Options: a large cogwheel, seen at an angle so that it is a wheel and not a disc, turning slowly about its
        // own axis; pointing at it spins it up for a moment. It is two blocks across.
        final boolean create = SlatePlatform.get().isModLoaded("create");
        final PivotNode lean = options.show(s, new PivotNode());
        lean.rotate(-24f, 58f, 0f).scale(ChestButton.THING_SIZE / 2.06f);
        final ModelNode cog = s.add(new ModelNode(create ? CREATE_COG : SLATE_COG).fallback(SLATE_COG));
        cog.pickable(false).hoverFeel(0f, 1f).attachTo(lean);
        if (options.lidLocked()) cog.muted(1f);
        cog.spin(22f);
        cog.at(0f, -0.5f, 0f);
        options.onLidHover((amount, entered) -> { if (entered) cog.boostSpin(7f); });

        logoIn.snap(0f);
        chromeIn.snap(0f);
        resolveContinue();
    }

    /** Finds what Continue would open; until something was played the sign stays greyed out. */
    private void resolveContinue() {
        final ChestButton sign = play;
        if (sign == null) return;
        continueTarget = LastPlayed.quick().orElse(null);
        applyContinue(sign);
        LastPlayed.resolve(found -> {
            if (sign != play) return;               // the scene was rebuilt meanwhile
            continueTarget = found.orElse(null);
            applyContinue(sign);
        });
    }

    private void applyContinue(final ChestButton sign) {
        final LastPlayed.Target t = continueTarget;
        if (t == null) {
            sign.disableSign(Component.translatable("slate_menu.title.nothing_yet_hint"));
            return;
        }
        sign.enableSign(this::continuePlaying);
        final String name = t.name() == null || t.name().isBlank() ? t.id() : t.name();
        sign.signTip(Component.translatable(t.kind() == LastPlayed.Kind.WORLD ? "slate_menu.overhaul.continue.world" : "slate_menu.overhaul.continue.server",
            name, Fmt.ago(t.at())));
    }

    private void continuePlaying() {
        if (continueTarget != null) LastPlayed.play(continueTarget, this);
    }

    /** The danger dialog has no PRIMARY button, so a stray Enter cannot confirm it. */
    private static void quit(final Minecraft mc) {
        if (!SlateMenu.config().confirmQuitGame) { mc.stop(); return; }
        SlateModal.confirmDanger(Component.translatable("menu.quit"), Component.translatable("slate_menu.title.confirm_quit"),
            Component.translatable("menu.quit"), mc::stop);
    }

    // ------------------------------------------------------------------ intro

    /** Milliseconds of intro so far, in the intro's own time (animation speed taken out). */
    private float introMs() {
        final float motion = Theme.current().motion();
        return motion <= 0f ? Float.MAX_VALUE : (Clock.nowMs() - sceneStartMs) / motion;
    }

    private boolean introRunning() {
        if (skipped || stage == null) return false;
        if (stage.camera().timeline().playing()) return true;
        for (final ChestButton c : chests) if (!c.settled()) return true;
        return false;
    }

    /** Puts everything in its end state. Returns whether there was anything left to skip. */
    private boolean skipIntro() {
        if (!introRunning() || stage == null) return false;
        skipped = true;
        stage.camera().timeline().skip(stage.camera().pose());
        for (final ChestButton c : chests) c.finish();
        logoIn.snap(1f);
        chromeIn.snap(1f);
        return true;
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        // A click during the fly-in only ends it: what it would have hit is not where it will be.
        if (cinematic && stage != null && stage.camera().timeline().playing() && skipIntro()) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (keyCode != 258 && skipIntro() && (keyCode == 256 || keyCode == 32 || keyCode == 257 || keyCode == 335)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        super.removed();
        if (stage != null) { stage.close(); stage = null; }
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        if (t.isVanilla() && SlateMenu.config().panorama) {
            PANORAMA.render(g, width, height, 1f, partialTick);
            g.fill(0, 0, width, height, 0x66000000);
            SlateDraw.vignette(g, 0, 0, width, height, 0.4f);
        } else {
            g.fill(0, 0, width, height, t.isVanilla() ? 0xFF101010 : t.bg());
        }
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final float ms = introMs();
        if (ms >= (cinematic ? 1500f : 0f)) logoIn.set(1f);
        if (ms >= (cinematic ? 3300f : 250f)) chromeIn.set(1f);
        // The corner buttons come in with the footer, once the scene has settled.
        final float ci = chromeIn.get();
        for (final SlateIconButton b : corner) {
            b.setAlpha(ci);
            b.active = ci > 0.5f;
        }
        // Once the camera has come to rest, a narrow window gets as wide a view as the three chests need.
        if (stage != null && !stage.camera().timeline().playing() && height > 0) stage.camera().fov(fovFor(width / (float) height));
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();

        // Soft shade behind the logo, so it reads over whatever scene sits there.
        final float li = logoIn.get();
        final float scale = logoScale();
        final int logoH = Math.round(51 * scale);
        final int logoY = logoTop(logoH);
        if (li > 0.004f) {
            g.pose().pushPose();
            // Grows into place from a touch larger, around its own middle.
            final float s = scale * (1f + 0.06f * (1f - li));
            g.pose().translate(width / 2f, logoY + logoH / 2f + (1f - li) * -6f, 0f);
            g.pose().scale(s, s, 1f);
            g.pose().translate(-width / 2f, -25.5f, 0f);
            if (SlateMenu.config().showLogo) {
                logo.renderLogo(g, width, li, 0);
            } else {
                final Palette p = t.palette();
                g.pose().pushPose();
                g.pose().translate(width / 2f, 8f, 0f);
                g.pose().scale(3f, 3f, 1f);
                final Component word = Fonts.heading(Component.literal("Slate"));
                g.drawString(font, word, -font.width(word) / 2, 0, Colors.scaleAlpha(p.text(), li), t.isVanilla());
                g.pose().popPose();
            }
            // The splash belongs to the Vanilla style: the Slate style leaves the logo alone.
            if (splash != null && !splash.isEmpty() && t.isVanilla()) renderSplash(g, li);
            g.pose().popPose();
            RenderSystem.enableBlend();
        }

        final float ci = chromeIn.get();
        if (ci > 0.004f && SlateMenu.config().showFooter) {
            final int fg = Colors.scaleAlpha(t.isVanilla() ? 0xFFFFFFFF : t.palette().textDim(), ci * (t.isVanilla() ? 0.85f : 1f));
            final Component version = Component.literal("Minecraft " + SharedConstants.getCurrentVersion().getName() + " · "
                + SlatePlatform.get().loader().displayName + " · " + SlatePlatform.get().allMods().size() + " mods · Slate " + Slate.version());
            final int y = height - 11;
            // The long version line when it fits, the short one when it does not.
            final Component brief = Component.literal(SharedConstants.getCurrentVersion().getName() + " · " + SlatePlatform.get().loader().displayName);
            final int room = width - PAD * 2;
            g.drawString(font, font.width(version) <= room ? version : brief, PAD, y, fg, t.isVanilla());
        }
    }

    /**
     * As big as the screen allows beside the chests: half of its width, a fifth of its height. The loading screen has
     * the logo in the same place at the same size ({@code LoadingScene.logo}): keep the two alike.
     */
    private float logoScale() {
        final float byWidth = width * 0.51f / 256f, byHeight = height * 0.22f / 51f;
        return Mth.clamp(Math.min(byWidth, byHeight), 0.7f, 4f);
    }

    /** The logo's middle sits a sixth down the screen: the chests' lids reach up to a little under a third. */
    private int logoTop(final int logoH) {
        return Math.max(4, Math.round(height * 0.16f - logoH / 2f));
    }

    /** Vanilla's splash, at the logo's lower right corner. Drawn inside the logo's scaled space. */
    private void renderSplash(final GuiGraphics g, final float alpha) {
        final Theme t = Theme.current();
        g.pose().pushPose();
        g.pose().translate(width / 2f + 123f, 39f, 0f);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-20f));
        float s = 1.8f - Mth.abs(Mth.sin((float) (Util.getMillis() % 1000L) / 1000f * ((float) Math.PI * 2f)) * 0.1f);
        s = s * 100f / (font.width(splash) + 32);
        g.pose().scale(s, s, s);
        g.drawCenteredString(font, splash, 0, -8, Colors.scaleAlpha(t.isVanilla() ? 0xFFFFFF00 : t.accent(), alpha));
        g.pose().popPose();
    }
}
