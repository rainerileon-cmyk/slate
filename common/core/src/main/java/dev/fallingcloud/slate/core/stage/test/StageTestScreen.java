package dev.fallingcloud.slate.core.stage.test;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.stage.Stage;
import dev.fallingcloud.slate.core.stage.StageWidget;
import dev.fallingcloud.slate.core.stage.node.BlockNode;
import dev.fallingcloud.slate.core.stage.node.PlayerNode;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import org.jetbrains.annotations.Nullable;

/**
 * {@code slate:stage_test}: every node type on one stage, with the frame cost in the corner. Variants (registered as
 * extra screen ids for the screenshot harness): {@link Variant#OPEN} opens the chest and puts keyboard focus on it,
 * {@link Variant#MOTION0} forces {@code motion = 0} so the intro must snap to its end state.
 */
public final class StageTestScreen extends SlateScreen {

    public enum Variant { DEFAULT, OPEN, MOTION0 }

    /** Notch's UUID: a fixed public profile whose skin downloads when online, default skin when not. */
    public static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    private final Variant variant;
    @Nullable private Stage stage;
    @Nullable private BlockNode chest;
    @Nullable private StageWidget widget;
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
        setInitialFocus(widget);
    }

    private void createScene() {
        if (variant == Variant.MOTION0 && Double.isNaN(savedMotion)) {
            savedMotion = Slate.config().motion;
            Slate.config().motion = 0;
            Theme.reload();
        }
        stage = new Stage().bind(this);
        stage.gradient(0xFF10141C, 0xFF232833);
        stage.camera().at(0f, 1.7f, 4.6f).lookAt(0f, 0.9f, 0f).fov(42f);

        chest = stage.add(new BlockNode(stage.level(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH)));
        chest.at(0f, 0f, 0f).pickable(true).tooltip(Component.translatable("slate.stage.test.chest_tip")).named("chest");
        chest.onClick(() -> chest.toggle());

        final PlayerNode me = stage.add(PlayerNode.local());
        me.pose(PlayerNode.Pose.WAVE).at(-1.7f, 0f, 0.3f).yaw(15f).pickable(true).named("me");

        final PlayerNode notch = stage.add(new PlayerNode(NOTCH, "Notch"));
        notch.pose(PlayerNode.Pose.SIT).at(1.7f, 0f, 0.3f).yaw(-15f).pickable(true).named("Notch");

        if (variant == Variant.OPEN) {
            chest.open(true);
            stage.focus(chest);
        }
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        if (stage == null) return;
        final Rect r = contentRect();
        final String info = String.format("stage %.2f ms (last %.2f)  fbo %dx%d  nodes %d  motion %.1f",
            stage.frameMs(), stage.lastFrameMs(), stage.targetWidth(), stage.targetHeight(), stage.nodes().size(), Theme.current().motion());
        SlateDraw.text(g, info, r.x() + 4, r.y() + r.h() - 12, palette().textMuted());
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
