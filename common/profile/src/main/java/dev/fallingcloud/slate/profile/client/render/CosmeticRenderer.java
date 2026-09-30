package dev.fallingcloud.slate.profile.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.fallingcloud.slate.profile.client.Cosmetic;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * Draws what a look wears in 3D onto a posed player model: puts the thing where the part it rides on is, moves it
 * the way it moves by itself, and hands its boxes to whoever draws (the game's own buffers in the world, the
 * stage's soft light in the menus). The pose stack comes in as the model's own space: what the model's parts are
 * posed in.
 */
public final class CosmeticRenderer {

    /** Where the drawing goes: asked once per thing, with the thing's texture and whether it has see-through pixels. */
    @FunctionalInterface
    public interface Sink {
        VertexConsumer open(Cosmetic cosmetic);
    }

    /**
     * @param age   time in ticks, running on (what the thing's own movement follows)
     * @param light packed light; {@code overlay} packed overlay (the red of a hit)
     */
    public static void draw(final PoseStack pose, final HumanoidModel<?> model, final Cosmetic cosmetic, final float age,
                            final int light, final int overlay, final int argb, final Sink sink) {
        if (!cosmetic.isModel()) return;
        final ModelPart part = cosmetic.part();
        pose.pushPose();
        switch (cosmetic.anchor()) {
            case HEAD -> model.head.translateAndRotate(pose);
            case RIGHT_ARM -> model.rightArm.translateAndRotate(pose);
            case LEFT_ARM -> model.leftArm.translateAndRotate(pose);
            case RIGHT_LEG -> model.rightLeg.translateAndRotate(pose);
            case LEFT_LEG -> model.leftLeg.translateAndRotate(pose);
            case BESIDE -> {
                // Beside the head, and its own way up whatever the head does: it circles a little as it hovers.
                final float turn = age * 0.045f;
                pose.translate((9.5f + Mth.sin(turn) * 1.6f) / 16f, (-7f + Mth.sin(age * 0.11f) * 1.3f + model.head.y) / 16f, (Mth.cos(turn) * 2.4f) / 16f);
                pose.mulPose(Axis.YP.rotation(-0.5f + Mth.sin(turn) * 0.5f));
            }
            default -> model.body.translateAndRotate(pose);
        }
        move(part, cosmetic, age);
        if (cosmetic.motion() == Cosmetic.Motion.BOUNCE) {
            // Squash and stretch about the foot of the thing.
            final float s = Mth.sin(age * 0.16f);
            final float foot = cosmetic.midY() + cosmetic.reach() * 0.5f;
            pose.translate(cosmetic.midX() / 16f, foot / 16f, cosmetic.midZ() / 16f);
            pose.scale(1f - s * 0.07f, 1f + s * 0.1f, 1f - s * 0.07f);
            pose.translate(-cosmetic.midX() / 16f, -foot / 16f, -cosmetic.midZ() / 16f);
        }
        part.render(pose, sink.open(cosmetic), light, overlay, argb);
        pose.popPose();
    }

    /** Sets the thing's own joints for the moment {@code age}. */
    private static void move(final ModelPart part, final Cosmetic cosmetic, final float age) {
        switch (cosmetic.motion()) {
            case FLAP -> {
                final float beat = Mth.sin(age * 0.12f) * 0.22f + Mth.sin(age * 0.031f) * 0.06f;
                if (part.hasChild("left")) part.getChild("left").yRot = -0.5f - beat;
                if (part.hasChild("right")) part.getChild("right").yRot = 0.5f + beat;
            }
            case HOVER -> {
                final float beat = Mth.sin(age * 2.4f) * 0.5f;
                if (part.hasChild("left")) part.getChild("left").zRot = -0.4f + beat;
                if (part.hasChild("right")) part.getChild("right").zRot = 0.4f - beat;
            }
            default -> {}
        }
    }

    /**
     * The thing by itself, its middle at the origin, one block its full reach: for showing it on a shelf. The pose
     * stack comes in as block space, y up.
     */
    public static void drawAlone(final PoseStack pose, final Cosmetic cosmetic, final float age, final int light, final int argb, final Sink sink) {
        if (!cosmetic.isModel()) return;
        final ModelPart part = cosmetic.part();
        move(part, cosmetic, age);
        pose.pushPose();
        // Models are in pixels, y down and z back; a part draws itself in sixteenths of what it is given.
        final float s = 8f / Math.max(1f, cosmetic.reach());
        pose.scale(s, -s, -s);
        pose.translate(-cosmetic.midX() / 16f, -cosmetic.midY() / 16f, -cosmetic.midZ() / 16f);
        part.render(pose, sink.open(cosmetic), light, OverlayTexture.NO_OVERLAY, argb);
        pose.popPose();
    }

    private CosmeticRenderer() {}
}
