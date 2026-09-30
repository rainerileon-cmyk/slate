package dev.fallingcloud.slate.menu.client.overhaul.create;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.stage.Stage;
import dev.fallingcloud.slate.core.stage.StageFinish;
import dev.fallingcloud.slate.core.stage.StageWidget;
import dev.fallingcloud.slate.core.stage.anim.IdleMotion;
import dev.fallingcloud.slate.core.stage.node.FxNode;
import dev.fallingcloud.slate.core.stage.node.MotesNode;
import dev.fallingcloud.slate.core.stage.node.TerrainNode;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateDropdown;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateSegmented;
import dev.fallingcloud.slate.core.widget.SlateTabStrip;
import dev.fallingcloud.slate.core.widget.SlateTextField;
import dev.fallingcloud.slate.core.widget.SlateToggle;
import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.menu.mixin.CreateWorldScreenAccess;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditGameRulesScreen;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import org.jetbrains.annotations.Nullable;

/**
 * Slate's world creation screen. It is another face of vanilla's: the vanilla screen is kept behind it, holding
 * the state and doing the creating, so data packs, experiments, world types other mods add and every check
 * vanilla makes on the way work as they always did.
 *
 * <p>The Overhaul layout (docs/LAYOUTS.md, 7) opens with a view of the world that is about to be made: the land
 * around its origin, worked out from the chosen world type and seed ({@link WorldPreview}), seen from the air in
 * evening light. A button folds the view away. Under it runs a line with the three tabs hanging from it (Game,
 * World, More), and under those the chosen tab's settings, as tiles in two columns. The Custom layout is the same
 * screen without the view, its tabs a plain strip.</p>
 *
 * <p>Vanilla's own flows come back to the vanilla screen (from the game rules, the data packs, a warning). Every
 * time they do, the screen swap makes a new one of these around it; what should outlive that (the tab, the land
 * already worked out) is kept per vanilla screen.</p>
 */
public final class OverhaulCreateWorldScreen extends SlateScreen {

    /** What is kept for as long as the vanilla screen behind lives. */
    private static final class Session {
        int tab;
        final WorldPreview preview = new WorldPreview();
    }

    private static final Map<CreateWorldScreen, Session> SESSIONS = new WeakHashMap<>();
    private static final Component[] TABS = {
        Component.translatable("createWorld.tab.game.title"), Component.translatable("createWorld.tab.world.title"),
        Component.translatable("createWorld.tab.more.title")};
    /** Blocks of the world a unit of the scene stands for is cells × this: the model is sixteen units wide. */
    private static final float SCALE = 16f / WorldPreview.WIDTH;
    private static final int FOOTER_H = 30, BAR_H = 20;

    private final CreateWorldScreen vanilla;
    private final WorldCreationUiState state;
    private final Session session;
    private final boolean withView;
    @Nullable private Stage stage;
    @Nullable private TerrainNode terrain;
    private boolean closed;
    private long changedAt;
    private Rect viewRect = new Rect(0, 0, 0, 0);
    private final Anim viewIn = new Anim(0, 500, Ease.OUT_CUBIC);
    private final List<Runnable> refreshers = new ArrayList<>();
    @Nullable private WorldCreationUiState.WorldTypeEntry builtFor;
    private boolean builtEditor;
    @Nullable private SlateTextField seedField;

    /** @param withView the Overhaul layout's view of the world; without it this is the Custom layout */
    public OverhaulCreateWorldScreen(final CreateWorldScreen vanilla, final boolean withView) {
        super(Component.translatable("selectWorld.create"), ((CreateWorldScreenAccess) vanilla).slate$lastScreen());
        this.vanilla = vanilla;
        this.state = vanilla.getUiState();
        this.withView = withView;
        synchronized (SESSIONS) {
            this.session = SESSIONS.computeIfAbsent(vanilla, v -> new Session());
        }
        state.addListener(s -> { if (!closed) changedAt = Clock.nowMs(); });
    }

    @Override
    public Component getTitle() {
        return Component.literal(super.getTitle().getString() + " – " + TABS[Mth.clamp(session.tab, 0, TABS.length - 1)].getString());
    }

    /** Back is vanilla's cancel: it also clears away the data packs picked for a world that is not made after all. */
    @Override
    public void back() {
        dev.fallingcloud.slate.core.widget.popup.Popups.closeAll();
        vanilla.popScreen();
    }

    private boolean minimised() { return SlateMenu.config().createPreviewMinimised; }

    // ------------------------------------------------------------------ build

    @Override
    protected void build() {
        final Minecraft mc = Minecraft.getInstance();
        // The vanilla screen is never shown, but its own flows need it to know the game and the window.
        vanilla.init(mc, width, height);
        closed = false;
        refreshers.clear();
        seedField = null;
        final int x = PAD, w = width - PAD * 2;
        int y = HEADER_H + 6;

        if (withView) {
            if (minimised()) {
                viewRect = new Rect(x, y, w, BAR_H);
                add(new SlateButton(x, y, w, BAR_H, Component.translatable("slate_menu.create.preview"), this::toggleView)
                    .variant(SlateButton.Variant.SECONDARY).icon(Icon.MAP).leftAligned()
                    .tip(Component.translatable("slate_menu.create.preview.expand")));
                if (stage != null) { stage.close(); stage = null; terrain = null; }
                session.preview.show(null);
                y += BAR_H + 6;
            } else {
                // A wide band of the world: a third of the screen where there is room, less on a low one.
                final int h = Mth.clamp(Math.round(height * (height < 300 ? 0.29f : 0.36f)), 56, Math.max(56, Math.round(w * 0.42f)));
                viewRect = new Rect(x, y, w, h);
                if (stage == null || stage.isClosed()) createScene();
                aim(viewRect);
                addRenderableOnly(new StageWidget(x + 1, y + 1, w - 2, h - 2, stage, Component.translatable("slate_menu.create.preview")));
                // A plate under the two buttons, so they read over whatever land lies behind them.
                final int px = x + w - 49, py = y + 3;
                addRenderableOnly((g, mx, my, pt) -> {
                    final Theme t = Theme.current();
                    SlateDraw.pixelRound(g, px, py, 46, 22, t.isVanilla() ? 0xC0000000 : Colors.withAlpha(t.bg(), 0xD8), t.isVanilla() ? 0 : t.radius());
                });
                add(new SlateIconButton(x + w - 24, y + 5, 18, Icon.CHEVRON_UP, Component.translatable("slate_menu.create.preview.minimise"), this::toggleView));
                add(new SlateIconButton(x + w - 46, y + 5, 18, Icon.REFRESH, Component.translatable("slate_menu.create.reroll"), this::reroll));
                y += h + 7;
            }
            add(new HangingTabs(x, y, w, Arrays.asList(TABS), session.tab, this::selectTab));
            y += HangingTabs.HEIGHT + 8;
        } else {
            final List<SlateTabStrip.Tab> tabs = new ArrayList<>();
            for (final Component c : TABS) tabs.add(new SlateTabStrip.Tab(c));
            final SlateTabStrip strip = add(new SlateTabStrip(x, y - 2, w, tabs, session.tab, this::selectTab));
            y += strip.getHeight() + 4;
        }

        final int footerTop = height - FOOTER_H;
        final SlateScrollPanel panel = new SlateScrollPanel(x, y, w, Math.max(24, footerTop - y - 2));
        fill(panel, session.tab);
        add(panel);

        // The foot: where the world goes on the left, the two ways out on the right.
        final int by = height - PAD + 2 - 20;
        final int createW = Math.min(150, Math.max(96, w / 3)), cancelW = Math.min(90, Math.max(56, w / 5));
        add(new SlateButton(x + w - createW, by, createW, Component.translatable("selectWorld.create"), this::create)
            .variant(SlateButton.Variant.PRIMARY).icon(Icon.NEW_WORLD));
        add(new SlateButton(x + w - createW - 6 - cancelW, by, cancelW, CommonComponents.GUI_CANCEL, this::back));

        builtFor = state.getWorldType();
        builtEditor = state.getPresetEditor() != null;
        if (withView && !minimised()) session.preview.update(state.getSettings());
    }

    private void selectTab(final int tab) {
        if (tab == session.tab) return;
        session.tab = tab;
        rebuildWidgets();
    }

    private void toggleView() {
        SlateMenu.configFile().update(c -> c.createPreviewMinimised = !c.createPreviewMinimised);
        viewIn.snap(0f);
        rebuildWidgets();
    }

    /** A new seed, written into the seed's box: the world shown is always the world that will be made. */
    private void reroll() {
        final String seed = Long.toString(new Random().nextLong());
        if (seedField != null) seedField.setValue(seed);
        else state.setSeed(seed);
    }

    private void create() {
        ((CreateWorldScreenAccess) vanilla).slate$create();
    }

    // ------------------------------------------------------------------ the tabs

    private void fill(final SlateScrollPanel panel, final int tab) {
        final int inner = panel.innerWidth();
        final boolean two = inner >= 260;
        final int gap = 4, colW = two ? (inner - gap) / 2 : inner;
        final List<Tile> tiles = switch (tab) {
            case 1 -> worldTab(colW);
            case 2 -> moreTab(colW);
            default -> gameTab(colW);
        };
        int i = 0, y = 0;
        for (final Tile t : tiles) {
            final int col = two ? i % 2 : 0;
            panel.add(t, col * (colW + gap), y);
            t.playEntrance(Math.min(STAGGER_MAX_MS, i * STAGGER_MS * 2));
            if (!two || col == 1) y += Tile.HEIGHT + gap;
            i++;
        }
        if (two && i % 2 == 1) y += Tile.HEIGHT + gap;
        panel.setContentHeight(y);
    }

    private static Component onOff(final boolean on) {
        return on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
    }

    /** A switch that says what it is set to, kept in step with the state (which other settings may change). */
    private SlateToggle toggle(final java.util.function.BooleanSupplier get, final java.util.function.Consumer<Boolean> set) {
        final SlateToggle t = new SlateToggle(0, 0, 70, onOff(get.getAsBoolean()), get.getAsBoolean(), set::accept).switchFirst();
        refreshers.add(() -> {
            final boolean on = get.getAsBoolean();
            if (t.value() != on) t.setValue(on);
            t.setMessage(onOff(on));
        });
        return t;
    }

    private List<Tile> gameTab(final int w) {
        final List<Tile> out = new ArrayList<>();
        final SlateTextField name = new SlateTextField(0, 0, w, Component.translatable("selectWorld.enterName"));
        name.text(state.getName()).maxLength(64);
        name.onChange(state::setName);
        out.add(new Tile(w, Component.translatable("selectWorld.enterName"), name)
            .tip(() -> Component.translatable("selectWorld.targetFolder", Component.literal(state.getTargetFolder()).withStyle(ChatFormatting.ITALIC))));

        final List<WorldCreationUiState.SelectedGameMode> modes = new ArrayList<>(List.of(WorldCreationUiState.SelectedGameMode.SURVIVAL,
            WorldCreationUiState.SelectedGameMode.HARDCORE, WorldCreationUiState.SelectedGameMode.CREATIVE));
        if (state.isDebug()) modes.add(WorldCreationUiState.SelectedGameMode.DEBUG);
        final SlateSegmented<WorldCreationUiState.SelectedGameMode> mode = new SlateSegmented<>(0, 0, w, modes, state.getGameMode(), m -> m.displayName, state::setGameMode);
        mode.optionTip(WorldCreationUiState.SelectedGameMode::getInfo);
        refreshers.add(() -> { if (mode.value() != state.getGameMode()) mode.setValue(state.getGameMode()); });
        out.add(new Tile(w, Component.translatable("selectWorld.gameMode"), mode).tip(() -> state.getGameMode().getInfo()).enabled(() -> !state.isDebug()));

        final SlateSegmented<Difficulty> difficulty = new SlateSegmented<>(0, 0, w, List.of(Difficulty.values()), state.getDifficulty(),
            Difficulty::getDisplayName, state::setDifficulty);
        refreshers.add(() -> { if (difficulty.value() != state.getDifficulty()) difficulty.setValue(state.getDifficulty()); });
        out.add(new Tile(w, Component.translatable("options.difficulty"), difficulty).tip(() -> state.getDifficulty().getInfo()).enabled(() -> !state.isHardcore()));

        out.add(new Tile(w, Component.translatable("selectWorld.allowCommands.new"), toggle(state::isAllowCommands, state::setAllowCommands))
            .tip(Component.translatable("selectWorld.allowCommands.info")).enabled(() -> !state.isDebug() && !state.isHardcore()));
        return out;
    }

    private List<Tile> worldTab(final int w) {
        final List<Tile> out = new ArrayList<>();
        // Every world type there is: the usual ones, then those vanilla keeps behind the Alt key.
        final List<WorldCreationUiState.WorldTypeEntry> types = new ArrayList<>(state.getNormalPresetList());
        for (final WorldCreationUiState.WorldTypeEntry e : state.getAltPresetList()) if (!types.contains(e)) types.add(e);
        if (!types.contains(state.getWorldType())) types.add(0, state.getWorldType());
        final SlateDropdown<WorldCreationUiState.WorldTypeEntry> type = new SlateDropdown<>(0, 0, w, types, state.getWorldType(),
            WorldCreationUiState.WorldTypeEntry::describePreset, state::setWorldType);
        final Tile typeTile = new Tile(w, Component.translatable("selectWorld.mapType"), type)
            .tip(() -> state.getWorldType().isAmplified() ? Component.translatable("generator.minecraft.amplified.info") : null);
        final PresetEditor editor = state.getPresetEditor();
        if (editor != null) {
            typeTile.extra(new SlateIconButton(0, 0, 20, Icon.SLIDERS, Component.translatable("selectWorld.customizeType"),
                () -> Minecraft.getInstance().setScreen(editor.createEditScreen(vanilla, state.getSettings()))));
        }
        out.add(typeTile);

        final SlateTextField seed = new SlateTextField(0, 0, w, Component.translatable("selectWorld.enterSeed"));
        seed.placeholder(Component.translatable("selectWorld.seedInfo")).text(state.getSeed()).maxLength(32);
        seed.onChange(s -> { if (!s.equals(state.getSeed())) state.setSeed(s); });
        seedField = seed;
        out.add(new Tile(w, Component.translatable("selectWorld.enterSeed"), seed)
            .extra(new SlateIconButton(0, 0, 20, Icon.REFRESH, Component.translatable("slate_menu.create.reroll"), this::reroll))
            .tip(Component.translatable("selectWorld.seedInfo")));

        out.add(new Tile(w, Component.translatable("selectWorld.mapFeatures"), toggle(state::isGenerateStructures, state::setGenerateStructures))
            .tip(Component.translatable("selectWorld.mapFeatures.info")).enabled(() -> !state.isDebug()));
        out.add(new Tile(w, Component.translatable("selectWorld.bonusItems"), toggle(state::isBonusChest, state::setBonusChest))
            .enabled(() -> !state.isHardcore() && !state.isDebug()));
        return out;
    }

    private List<Tile> moreTab(final int w) {
        final Minecraft mc = Minecraft.getInstance();
        final List<Tile> out = new ArrayList<>();
        out.add(new Tile(w, Component.translatable("selectWorld.gameRules"), new SlateButton(0, 0, w, Component.translatable("slate_menu.create.edit"), () ->
            mc.setScreen(new EditGameRulesScreen(state.getGameRules().copy(), changed -> {
                mc.setScreen(vanilla);
                changed.ifPresent(state::setGameRules);
            }))).icon(Icon.SLIDERS)).tip(Component.translatable("slate_menu.create.game_rules.info")));
        out.add(new Tile(w, Component.translatable("selectWorld.dataPacks"), new SlateButton(0, 0, w, Component.translatable("slate_menu.create.choose"), () ->
            ((CreateWorldScreenAccess) vanilla).slate$openDataPacks(state.getSettings().dataConfiguration())).icon(Icon.PACK))
            .tip(Component.translatable("slate_menu.create.data_packs.info")));
        out.add(new Tile(w, Component.translatable("selectWorld.experiments"), new SlateButton(0, 0, w, Component.translatable("slate_menu.create.choose"), () ->
            ((CreateWorldScreenAccess) vanilla).slate$openExperiments(state.getSettings().dataConfiguration())).icon(Icon.SPARKLE))
            .tip(Component.translatable("slate_menu.create.experiments.info")));
        return out;
    }

    // ------------------------------------------------------------------ the view

    private void createScene() {
        final Stage s = new Stage().bind(this);
        stage = s;
        // Evening: a deep blue overhead going over into gold at the horizon, which is also what the distance fades to.
        s.gradient(0xFF182747, 0xFFE9B384);
        s.fog(true, 15f, 27f, 0xFFDFAA84);
        s.finish(StageFinish.cinematic().vignette(0.26f));
        s.soft(true);
        s.resolutionScale(Minecraft.getInstance().getWindow().getWidth() <= 2048 ? 2f : 1.5f);
        s.lighting().key(9f, 9f, 5f).keyColor(0.78f, 0.66f, 0.5f).ambientColor(0.4f, 0.4f, 0.47f).fill(-1f, 0.3f, 0.2f, 0.12f, 0.16f, 0.26f)
            .rim(1f, 0.85f, 0.65f, 0.16f).sun(0.75f, 0.6f, 0.35f);

        final TerrainNode land = s.add(new TerrainNode(WorldPreview.WIDTH, WorldPreview.DEPTH));
        land.scale(SCALE);
        terrain = land;
        s.add(FxNode.clouds(9, 17f, 9f, 5.4f, 0.42f, 17L, 0.09f)).alpha(0.85f);
        s.add(new MotesNode(30, 12f, 3f, 6f, 0.035f, 0x50FFE2B0, 3L)).at(0f, 4f, 3f);
        viewIn.snap(0f);
        viewIn.set(1f);
        session.preview.show(land);
    }

    /** Sets the camera for the shape the view has: always the same sweep of land from side to side, however low the band. */
    private void aim(final Rect view) {
        if (stage == null) return;
        final float aspect = Math.max(0.5f, (view.w() - 2) / (float) Math.max(1, view.h() - 2));
        final float across = (float) Math.toRadians(50.0);
        final float fov = (float) Math.toDegrees(2.0 * Math.atan(Math.tan(across / 2.0) / aspect));
        // From the air, looking along the land: even a low band shows it from the near rim to the far one.
        stage.camera().at(0f, 7.6f, 13.6f).lookAt(0f, 1.7f, -0.5f).fov(Mth.clamp(fov, 6f, 46f)).clip(0.1f, 90f);
        stage.camera().idle(IdleMotion.sway(26000f, 13f, 0.6f, 0.04f));
    }

    // ------------------------------------------------------------------ living

    @Override
    public void tick() {
        super.tick();
        for (final Runnable r : refreshers) r.run();
        // Another world type may have an editor, or none: the World tab is laid out again for it.
        if (session.tab == 1 && (builtFor != state.getWorldType() || builtEditor != (state.getPresetEditor() != null))) {
            rebuildWidgets();
            return;
        }
        // A seed is typed letter by letter: the land is worked out for what stands there once the typing rests.
        if (changedAt != 0 && Clock.nowMs() - changedAt > 320) {
            changedAt = 0;
            if (withView && !minimised()) session.preview.update(state.getSettings());
        }
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == 257 || keyCode == 335) { create(); return true; }
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        closed = true;
        session.preview.show(null);
        if (stage != null) { stage.close(); stage = null; }
        terrain = null;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        final Theme t = Theme.current();
        if (t.isVanilla() || (minecraft != null && minecraft.level != null)) return;
        SlateDraw.vgradient(g, 0, HEADER_H, width, height - HEADER_H, 0xFF0B0C0F, 0xFF1A1816);
        SlateDraw.vignette(g, 0, HEADER_H, width, height - HEADER_H, 0.4f);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        if (withView && !minimised()) {
            // The plate the view lies in, under it; the view itself is one of the widgets.
            final Theme t = Theme.current();
            final Rect r = viewRect;
            renderBackground(g, mouseX, mouseY, partialTick);
            SlateDraw.shadow(g, r.x(), r.y(), r.w(), r.h(), 0.6f);
            SlateDraw.pixelRound(g, r.x(), r.y(), r.w(), r.h(), t.isVanilla() ? 0xFF000000 : 0xFF0B0C0F, t.isVanilla() ? 0 : t.radius());
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        final int by = height - PAD + 2 - 20;
        // Where the world will be saved, beside the buttons.
        final Component folder = Component.translatable("selectWorld.targetFolder", Component.literal(state.getTargetFolder()).withStyle(ChatFormatting.ITALIC));
        final int room = width - PAD * 2 - Math.min(150, Math.max(96, (width - PAD * 2) / 3)) - Math.min(90, Math.max(56, (width - PAD * 2) / 5)) - 18;
        if (room > 60) g.drawString(font, SlateDraw.truncate(folder, room), PAD, by + 6, van ? 0xFFA0A0A0 : p.textDim(), van);

        if (!withView || minimised()) return;
        final Rect r = viewRect;
        final float a = viewIn.get();
        SlateDraw.outline(g, r.x(), r.y(), r.w(), r.h(), van ? 0xFF000000 : p.borderStrong(), van ? 0 : t.radius());
        final WorldPreview preview = session.preview;
        final WorldPreview.State st = preview.state();
        // What is shown, and of which seed.
        final Component seed = Component.translatable("slate_menu.create.seed", Long.toString(preview.seed()));
        final int cw = Math.min(font.width(seed) + 22, r.w() - 60), ch = 15;
        final int cx = r.x() + 5, cy = r.y() + 5;
        SlateDraw.pixelRound(g, cx, cy, cw, ch, Colors.scaleAlpha(van ? 0xC0000000 : Colors.withAlpha(p.bg(), 0xD8), a), van ? 0 : t.radius());
        Icons.draw(g, Icon.MAP, cx + 4, cy + 3, 9, Colors.scaleAlpha(van ? 0xFFE0E0E0 : p.accent(), a));
        g.drawString(font, SlateDraw.truncate(seed, cw - 20), cx + 17, cy + 4, Colors.scaleAlpha(van ? 0xFFFFFFFF : p.text(), a), van);

        if (st == WorldPreview.State.FAILED) {
            final Component none = Component.translatable(state.isDebug() ? "slate_menu.create.preview.debug" : "slate_menu.create.preview.none");
            SlateDraw.textCentered(g, none, r.centerX(), r.centerY() - 4, 0xFFFFFFFF, true);
        } else {
            final Component span = Component.translatable("slate_menu.create.preview.span", WorldPreview.blocksWide(), WorldPreview.blocksDeep());
            final int sw = font.width(span) + 10;
            if (sw < r.w() - cw - 70) {
                SlateDraw.pixelRound(g, r.right() - 5 - sw, r.bottom() - 5 - ch, sw, ch, Colors.scaleAlpha(van ? 0xC0000000 : Colors.withAlpha(p.bg(), 0xC8), a), van ? 0 : t.radius());
                g.drawString(font, span, r.right() - sw, r.bottom() - ch - 1, Colors.scaleAlpha(van ? 0xFFE0E0E0 : p.textMuted(), a), van);
            }
        }
        if (st == WorldPreview.State.WORKING) {
            // A line along the foot of the view, filling as the land is worked out.
            final int lw = Math.round((r.w() - 4) * Mth.clamp(preview.progress(), 0f, 1f));
            SlateDraw.rect(g, r.x() + 2, r.bottom() - 3, r.w() - 4, 1, 0x50000000);
            SlateDraw.rect(g, r.x() + 2, r.bottom() - 3, lw, 1, van ? 0xFFFFFFFF : p.accent());
        }
    }

    /** For the harness and for other modules: whether the land shown is all there. */
    public boolean previewComplete() {
        return session.preview.state() == WorldPreview.State.DONE;
    }
}
