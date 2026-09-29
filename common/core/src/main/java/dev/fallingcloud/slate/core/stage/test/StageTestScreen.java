package dev.fallingcloud.slate.core.stage.test;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.stage.Stage;
import dev.fallingcloud.slate.core.stage.StageWidget;
import dev.fallingcloud.slate.core.stage.node.BlockNode;
import dev.fallingcloud.slate.core.stage.node.EntityNode;
import dev.fallingcloud.slate.core.stage.node.FxNode;
import dev.fallingcloud.slate.core.stage.node.ItemNode;
import dev.fallingcloud.slate.core.stage.node.ModelNode;
import dev.fallingcloud.slate.core.stage.node.PlayerNode;
import dev.fallingcloud.slate.core.stage.node.TextNode;
import dev.fallingcloud.slate.core.stage.node.VoxelNode;
import dev.fallingcloud.slate.core.stage.scene.Anchor;
import dev.fallingcloud.slate.core.stage.scene.Scene;
import dev.fallingcloud.slate.core.stage.scene.SceneLoader;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * {@code slate:stage_test}: the {@code slate:stage_test} scene (a hand-made .nbt diorama with a sign, a lit campfire,
 * a pond and a tree) plus every node type placed on its anchors: a chest that opens on click, two players with real
 * skins (waving and sitting, with nameplates), a spinning item with sparkles, the Slate cogwheel model, a wolf, a
 * voxel planet with clouds and a floating heightfield island. The frame cost is printed in the corner.
 *
 * <p>Variants for the screenshot harness: {@link Variant#OPEN} opens the chest and gives it keyboard focus (the
 * outline shows), {@link Variant#MOTION0} forces {@code motion = 0} so the intro must snap to its end state.</p>
 */
public final class StageTestScreen extends SlateScreen {

    public enum Variant { DEFAULT, OPEN, MOTION0 }

    /** Notch's UUID: a fixed public profile whose skin downloads when online, default skin when not. */
    public static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final ItemStack[] ITEMS = {
        new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE), new ItemStack(Items.COMPASS), new ItemStack(Items.OAK_SAPLING)};

    private final Variant variant;
    @Nullable private Stage stage;
    @Nullable private BlockNode chest;
    @Nullable private StageWidget widget;
    private int itemIndex;
    private boolean signGlow;
    private double savedMotion = Double.NaN;

    public StageTestScreen(@Nullable final Screen parent, final Variant variant) {
        super(Component.translatable("slate.stage.test.title"), parent);
        this.variant = variant;
    }

    @Override
    protected void build() {
        if (stage == null) createScene();
        final Rect r = contentRect();
        widget = add(new StageWidget(r.x(), r.y(), r.w(), r.h(), stage, Component.translatable("slate.stage.test.title")));
        if (variant == Variant.OPEN) setInitialFocus(widget);
    }

    private void createScene() {
        if (variant == Variant.MOTION0 && Double.isNaN(savedMotion)) {
            savedMotion = Slate.config().motion;
            Slate.config().motion = 0;
            Theme.reload();
        }
        stage = new Stage().bind(this);
        final Scene scene = SceneLoader.load(Slate.id("stage_test"));
        scene.apply(stage);

        // The chest: a real ChestBlockEntity, click swings the lid.
        final Anchor chestAt = scene.anchorOr("chest", 0f, 1f, 1.5f, 0f);
        chest = stage.add(chestAt.place(new BlockNode(stage.level(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH))));
        chest.tooltip(Component.translatable("slate.stage.test.chest_tip")).named(Component.translatable("slate.stage.test.chest"));
        chest.onClick(() -> chest.toggle());

        // A sign with text; click makes the text glow.
        final Anchor signAt = scene.anchorOr("sign", 0f, 1f, 3.3f, 0f);
        final BlockNode sign = stage.add(signAt.place(new BlockNode(stage.level(), Blocks.OAK_SIGN.defaultBlockState().setValue(BlockStateProperties.ROTATION_16, 0))));
        sign.signText(Component.translatable("slate.stage.test.sign1"), Component.translatable("slate.stage.test.sign2"));
        sign.bounds(-0.4f, 0f, -0.15f, 0.4f, 1.1f, 0.15f).hoverFeel(0.08f, 1.06f).named(Component.translatable("slate.stage.test.sign"));
        sign.onClick(() -> {
            signGlow = !signGlow;
            sign.signText(signGlow ? net.minecraft.world.item.DyeColor.ORANGE : net.minecraft.world.item.DyeColor.BLACK, signGlow,
                Component.translatable("slate.stage.test.sign1"), Component.translatable("slate.stage.test.sign2"));
        });

        // Players with real skins: the local user waving, Notch sitting; nameplates above.
        final Anchor a = scene.anchorOr("player_a", -2.4f, 1f, 1.2f, 18f);
        final PlayerNode me = stage.add(a.place(PlayerNode.local()));
        me.pose(PlayerNode.Pose.WAVE).pickable(true).tooltip(Component.translatable("slate.stage.test.player_tip"));
        stage.add(a.offset(0f, 2.25f, 0f).place(new TextNode(Component.literal(me.name().getString())).seeThrough(true)));
        final Anchor bAt = scene.anchorOr("player_b", 2.4f, 1f, 1.2f, -18f);
        final PlayerNode notch = stage.add(bAt.place(new PlayerNode(NOTCH, "Notch")));
        notch.pose(PlayerNode.Pose.SIT).pickable(true).tooltip(Component.translatable("slate.stage.test.player_tip"));
        stage.add(bAt.offset(0f, 1.75f, 0f).place(new TextNode(Component.literal("Notch")).seeThrough(true)));

        // A spinning item with sparkles; click cycles it.
        final Anchor itemAt = scene.anchorOr("item", -1.6f, 1.15f, -1.2f, 0f);
        final ItemNode item = stage.add(itemAt.place(new ItemNode(ITEMS[0])));
        item.size(1.5f).spin(40f).bob(0.05f).pickable(true).tooltip(Component.translatable("slate.stage.test.item_tip"));
        item.onClick(() -> item.stack(ITEMS[(++itemIndex) % ITEMS.length]));
        stage.add(itemAt.offset(0f, 0.5f, 0f).place(FxNode.sparkles(18, 1.4f, 1.4f, 1.4f, 0.12f, Theme.current().accent(), 3L)));

        // The Slate cogwheel (a JSON block model); hover spins it faster for a moment.
        final Anchor cogAt = scene.anchorOr("cog", 1.6f, 1.55f, -1.2f, 0f);
        final ModelNode cog = stage.add(cogAt.place(new ModelNode(Slate.id("stage/cog"))));
        cog.spin(18f).pickable(true).tooltip(Component.translatable("slate.stage.test.cog_tip")).named(Component.translatable("slate.stage.test.cog"));
        cog.onHover(h -> { if (h) cog.boostSpin(6f); });
        cog.onClick(() -> SlateToasts.show(Component.translatable("slate.stage.test.cog"), Component.translatable("slate.stage.test.cog_clicked"), dev.fallingcloud.slate.core.gfx.Icon.INFO));

        // A pet.
        final Anchor wolfAt = scene.anchorOr("wolf", 3f, 1f, -2.6f, -30f);
        final EntityNode wolf = stage.add(wolfAt.place(new EntityNode(stage.level(), EntityType.WOLF)));
        wolf.tamed(true).sitting(true).pickable(true).tooltip(Component.translatable("slate.stage.test.wolf_tip"));

        // A voxel planet with clouds, and a floating island (heightfield).
        final Anchor planetAt = scene.anchorOr("planet", -3.6f, 4.2f, -3.2f, 0f);
        final VoxelNode planet = stage.add(planetAt.place(VoxelNode.planet(stage.level(), 4, 20260929L)));
        planet.scale(0.42f).spin(6f).pickable(true).tooltip(Component.translatable("slate.stage.test.planet_tip"));
        stage.add(planetAt.place(FxNode.clouds(5, 7f, 7f, 0.9f, 0.9f, 11L, 0.12f)).scale(0.42f));
        final Anchor islandAt = scene.anchorOr("island", 4.3f, 3.2f, -4f, 25f);
        final VoxelNode island = stage.add(islandAt.place(VoxelNode.heightfield(stage.level(), 8, 8, 4242L)));
        island.centered();
        island.scale(0.4f).spin(-4f).pickable(true).tooltip(Component.translatable("slate.stage.test.island_tip"));

        if (variant == Variant.OPEN) {
            chest.open(true);
            stage.focus(chest);
        }
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        if (stage == null) return;
        final Rect r = contentRect();
        final String info = String.format("stage %.2f ms (last %.2f)  fbo %dx%d  nodes %d  motion %.1f  intro %d%%",
            stage.frameMs(), stage.lastFrameMs(), stage.targetWidth(), stage.targetHeight(), stage.nodes().size(), Theme.current().motion(),
            Math.round(stage.camera().timeline().progress() * 100f));
        // A translucent plate keeps the diagnostics readable over the scene.
        final int plateW = SlateDraw.width(info) + 8;
        g.fill(r.x() + 2, r.y() + r.h() - 15, r.x() + 2 + plateW, r.y() + r.h() - 2, 0xA0000000);
        SlateDraw.text(g, info, r.x() + 6, r.y() + r.h() - 12, palette().textMuted());
        final Component hint = Component.translatable("slate.stage.test.hint");
        final int hintW = SlateDraw.width(hint) + 8;
        g.fill(r.x() + r.w() - 2 - hintW, r.y() + 2, r.x() + r.w() - 2, r.y() + 15, 0xA0000000);
        SlateDraw.textRight(g, hint, r.x() + r.w() - 6, r.y() + 5, palette().textMuted());
        final var hovered = stage.hovered();
        if (hovered != null) SlateDraw.text(g, hovered.name(), r.x() + 6, r.y() + 4, palette().text());
    }

    @Override
    public void removed() {
        super.removed();
        if (stage != null) { stage.close(); stage = null; }
        if (!Double.isNaN(savedMotion)) {
            Slate.config().motion = savedMotion;
            savedMotion = Double.NaN;
            Theme.reload();
        }
    }
}
