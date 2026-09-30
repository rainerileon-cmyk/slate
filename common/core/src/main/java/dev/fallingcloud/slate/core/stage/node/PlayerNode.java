package dev.fallingcloud.slate.core.stage.node;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.stage.StageRenderContext;
import dev.fallingcloud.slate.core.stage.StageSoft;
import dev.fallingcloud.slate.core.theme.Theme;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A player model with a real skin. The skin comes from the {@code SkinManager} for a game profile or UUID (the
 * default Steve/Alex shows until the download lands, or for good when offline) or from a local PNG (64×64, or the
 * legacy 64×32 layout which is converted like vanilla does). Slim and classic arms follow the skin's model.
 *
 * <p>No entity is involved: the {@code PlayerModel} is posed directly, which is what lets this work on the title
 * screen where no {@code ClientLevel} exists. Poses: {@link Pose#STAND}, {@link Pose#SIT}, {@link Pose#WAVE}, each
 * with idle breathing and an optional head that follows the cursor.</p>
 *
 * <p>A subclass can hang things on the player ({@link #drawExtras}: a hat that follows the head, a pack on the
 * back), and a node can show a part of the player only ({@link #show}: just an arm, to show a sleeve).</p>
 */
public class PlayerNode extends StageNode {

    public enum Pose { STAND, SIT, WAVE }

    /** The parts a player is made of, for {@link #show}. */
    public enum Part { HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG }

    private static final Quaternionf FLIP = Axis.YP.rotationDegrees(180f);
    private static final Vector3f HEAD = new Vector3f(0f, 1.62f, 0f);
    private static int localSkins;

    private final Supplier<PlayerSkin> skin;
    @Nullable private PlayerModel<LivingEntity> wideModel;
    @Nullable private PlayerModel<LivingEntity> slimModel;
    private Pose pose = Pose.STAND;
    private boolean lookAtCursor = true;
    private boolean breathe = true;
    private java.util.Set<Part> shown = java.util.EnumSet.allOf(Part.class);
    private float sitDrop = 0.5f;
    private float lookYaw, lookPitch;
    private final Vector3f head = new Vector3f();
    private final Vector3f point = new Vector3f();

    public PlayerNode(final GameProfile profile) {
        Supplier<PlayerSkin> s;
        try {
            s = Minecraft.getInstance().getSkinManager().lookupInsecure(profile);
        } catch (final Exception e) {
            final PlayerSkin fallback = DefaultPlayerSkin.get(profile.getId());
            s = () -> fallback;
        }
        this.skin = s;
        bounds(-0.4f, 0f, -0.4f, 0.4f, 1.9f, 0.4f);
        hoverFeel(0.03f, 1.03f);
        named(profile.getName() == null ? "player" : profile.getName());
    }

    public PlayerNode(final UUID uuid, @Nullable final String name) {
        this(new GameProfile(uuid, name == null || name.isEmpty() ? "?" : name));
    }

    /** A fixed skin: {@code texture} must already be registered with the texture manager. */
    public PlayerNode(final ResourceLocation texture, final boolean slim) {
        final PlayerSkin fixed = new PlayerSkin(texture, null, null, null, slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE, true);
        this.skin = () -> fixed;
        bounds(-0.4f, 0f, -0.4f, 0.4f, 1.9f, 0.4f);
        hoverFeel(0.03f, 1.03f);
        named("player");
    }

    /** A skin that may change while the node lives: asked for every frame. */
    public PlayerNode(final Supplier<PlayerSkin> skin) {
        this.skin = skin;
        bounds(-0.4f, 0f, -0.4f, 0.4f, 1.9f, 0.4f);
        hoverFeel(0.03f, 1.03f);
        named("player");
    }

    @Nullable private static Supplier<? extends PlayerNode> localFactory;

    /**
     * Hands over the making of {@link #local()}: a module that dresses the player (Slate Profile) gives the player
     * as dressed, so every scene that shows "you" shows what you wear. Null puts the plain player back.
     */
    public static void localFactory(@Nullable final Supplier<? extends PlayerNode> factory) { localFactory = factory; }

    /** The user playing this client, as they look: in their look when they wear one, else in their account's skin. */
    public static PlayerNode local() {
        final Supplier<? extends PlayerNode> factory = localFactory;
        if (factory != null) {
            try {
                final PlayerNode made = factory.get();
                if (made != null) return made;
            } catch (final Exception e) {
                Slate.LOGGER.warn("[Slate] stage: the player's look could not be made: {}", e.toString());
            }
        }
        final User u = Minecraft.getInstance().getUser();
        return new PlayerNode(u.getProfileId(), u.getName());
    }

    /**
     * A skin from a PNG on disk (64×64, or legacy 64×32 which is upgraded like vanilla's skin loader). Falls back to
     * the default skin when the file cannot be read.
     */
    public static PlayerNode fromPng(final Path png, final boolean slim) {
        final ResourceLocation tex = loadLocalSkin(png);
        if (tex == null) return new PlayerNode(UUID.nameUUIDFromBytes(png.toString().getBytes()), png.getFileName().toString());
        return new PlayerNode(tex, slim);
    }

    @Nullable
    public static ResourceLocation loadLocalSkin(final Path png) {
        try (InputStream in = Files.newInputStream(png)) {
            NativeImage image = NativeImage.read(in);
            if (image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
                image.close();
                Slate.LOGGER.warn("[Slate] stage: skin {} is not 64x64 or 64x32", png);
                return null;
            }
            if (image.getHeight() == 32) image = upgradeLegacySkin(image);
            final ResourceLocation id = Slate.id("stage/skin/" + (localSkins++));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
            return id;
        } catch (final Exception e) {
            Slate.LOGGER.warn("[Slate] stage: cannot load skin {}: {}", png, e.toString());
            return null;
        }
    }

    /** The 64×32 → 64×64 conversion vanilla applies to old skins (left limbs mirrored from the right ones). */
    public static NativeImage upgradeLegacySkin(final NativeImage legacy) {
        final NativeImage image = new NativeImage(64, 64, true);
        image.copyFrom(legacy);
        legacy.close();
        image.fillRect(0, 32, 64, 32, 0);
        image.copyRect(4, 16, 16, 32, 4, 4, true, false);
        image.copyRect(8, 16, 16, 32, 4, 4, true, false);
        image.copyRect(0, 20, 24, 32, 4, 12, true, false);
        image.copyRect(4, 20, 16, 32, 4, 12, true, false);
        image.copyRect(8, 20, 8, 32, 4, 12, true, false);
        image.copyRect(12, 20, 16, 32, 4, 12, true, false);
        image.copyRect(44, 16, -8, 32, 4, 4, true, false);
        image.copyRect(48, 16, -8, 32, 4, 4, true, false);
        image.copyRect(40, 20, 0, 32, 4, 12, true, false);
        image.copyRect(44, 20, -8, 32, 4, 12, true, false);
        image.copyRect(48, 20, -16, 32, 4, 12, true, false);
        image.copyRect(52, 20, -8, 32, 4, 12, true, false);
        return image;
    }

    // ------------------------------------------------------------------ configuration

    public PlayerNode pose(final Pose p) {
        this.pose = p;
        boundsMax.y = p == Pose.SIT ? 1.35f : 1.9f;
        return this;
    }

    public Pose pose() { return pose; }

    public PlayerNode lookAtCursor(final boolean on) { this.lookAtCursor = on; return this; }

    public PlayerNode breathe(final boolean on) { this.breathe = on; return this; }

    /** Draws these parts only (with what they wear); the rest of the player is left out. */
    public PlayerNode show(final java.util.Set<Part> parts) {
        this.shown = parts.isEmpty() ? java.util.EnumSet.allOf(Part.class) : java.util.EnumSet.copyOf(parts);
        return this;
    }

    public boolean shows(final Part part) { return shown.contains(part); }

    /**
     * Called after the player was drawn, with the pose stack in the model's own space (the one the model's parts
     * are posed in) and the model as it was posed: a subclass draws here what the player carries. To follow a part,
     * {@code model.head.translateAndRotate(ctx.pose)} and draw in pixels (1/16 of a block).
     */
    protected void drawExtras(final StageRenderContext ctx, final PlayerModel<LivingEntity> model) {}

    /** How far a sitting player drops (blocks); 0.5 sits on the ground, ~0.3 on a slab or stair. */
    public PlayerNode sitDrop(final float blocks) { this.sitDrop = blocks; return this; }

    public PlayerSkin skin() { return skin.get(); }

    // ------------------------------------------------------------------ per frame

    @Override
    public void update(final StageRenderContext ctx) {
        float targetYaw = 0f, targetPitch = 0f;
        if (lookAtCursor && ctx.hasMouse) {
            // The closest point on the mouse ray to the head, in local space, is what the head turns towards.
            head.set(HEAD);
            model.transformPosition(head);
            point.set(head).sub(ctx.rayOrigin);
            final float t = Math.max(0f, point.dot(ctx.rayDir));
            point.set(ctx.rayDir).mul(t).add(ctx.rayOrigin);
            modelInverse.transformPosition(point);
            final float dx = point.x - HEAD.x, dy = point.y - HEAD.y, dz = point.z - HEAD.z;
            final float horiz = (float) Math.sqrt(dx * dx + dz * dz);
            if (horiz > 1e-4f || Math.abs(dy) > 1e-4f) {
                targetYaw = Mth.clamp((float) Math.toDegrees(Math.atan2(-dx, dz)), -70f, 70f);
                targetPitch = Mth.clamp((float) Math.toDegrees(-Math.atan2(dy, horiz)), -45f, 45f);
            }
        }
        final float k = Theme.current().motion() <= 0f ? 1f : 1f - (float) Math.exp(-ctx.deltaMs / 140f);
        lookYaw += (targetYaw - lookYaw) * k;
        lookPitch += (targetPitch - lookPitch) * k;
    }

    private PlayerModel<LivingEntity> model(final boolean slim) {
        if (slim) {
            if (slimModel == null) slimModel = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_SLIM), true);
            return slimModel;
        }
        if (wideModel == null) wideModel = new PlayerModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER), false);
        return wideModel;
    }

    @Override
    protected void draw(final StageRenderContext ctx) {
        final PlayerSkin s = skin();
        final ResourceLocation texture = s.texture();
        final PlayerModel<LivingEntity> m = model(s.model() == PlayerSkin.Model.SLIM);
        setupPose(m, ctx);
        ctx.pose.pushPose();
        if (pose == Pose.SIT) ctx.pose.translate(0f, -sitDrop, 0f);
        ctx.pose.mulPose(FLIP);
        ctx.pose.scale(-1f, -1f, 1f);
        ctx.pose.translate(0f, -1.501f, 0f);
        if (ctx.soft) {
            final com.mojang.blaze3d.vertex.BufferBuilder bb = StageSoft.begin(StageSoft.ENTITY);
            m.renderToBuffer(ctx.pose, bb, ctx.light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            StageSoft.end(bb, StageSoft.ENTITY, texture, alpha(), false);
        } else {
            final VertexConsumer vc = ctx.buffers.getBuffer(RenderType.entityTranslucent(texture));
            final int color = (Math.round(alpha() * 255f) << 24) | 0xFFFFFF;
            m.renderToBuffer(ctx.pose, vc, ctx.light, OverlayTexture.NO_OVERLAY, color);
        }
        drawExtras(ctx, m);
        ctx.pose.popPose();
    }

    private void setupPose(final PlayerModel<LivingEntity> m, final StageRenderContext ctx) {
        final float t = ctx.seconds();
        final boolean anim = Theme.current().motion() > 0f;
        m.young = false;
        m.riding = false;
        m.crouching = false;
        m.swimAmount = 0f;
        m.attackTime = 0f;
        m.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        m.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        m.setAllVisible(true);
        m.head.resetPose();
        m.body.resetPose();
        m.rightArm.resetPose();
        m.leftArm.resetPose();
        m.rightLeg.resetPose();
        m.leftLeg.resetPose();
        final float breath = breathe && anim ? Mth.sin(t * 1.6f) : 0f;
        final float sway = anim ? Mth.sin(t * 1.3f) * 0.04f : 0f;
        m.head.yRot = (float) Math.toRadians(lookYaw);
        m.head.xRot = (float) Math.toRadians(lookPitch) + breath * 0.02f;
        m.body.y += breath * 0.15f;
        m.head.y += breath * 0.15f;
        m.rightArm.y += breath * 0.15f;
        m.leftArm.y += breath * 0.15f;
        m.rightArm.xRot = sway;
        m.leftArm.xRot = -sway;
        m.rightArm.zRot = 0.04f + breath * 0.02f;
        m.leftArm.zRot = -0.04f - breath * 0.02f;
        switch (pose) {
            case SIT -> {
                m.rightLeg.xRot = -1.4137167f;
                m.rightLeg.yRot = 0.31415927f;
                m.rightLeg.zRot = 0.07853982f;
                m.leftLeg.xRot = -1.4137167f;
                m.leftLeg.yRot = -0.31415927f;
                m.leftLeg.zRot = -0.07853982f;
                m.rightArm.xRot -= 0.62831855f;
                m.leftArm.xRot -= 0.62831855f;
            }
            case WAVE -> {
                final float w = anim ? Mth.sin(t * 9f) : 0f;
                m.rightArm.xRot = -2.9f + w * 0.12f;
                m.rightArm.zRot = -0.5f - w * 0.35f;
                m.rightArm.yRot = 0f;
            }
            default -> {}
        }
        m.hat.copyFrom(m.head);
        m.jacket.copyFrom(m.body);
        m.leftSleeve.copyFrom(m.leftArm);
        m.rightSleeve.copyFrom(m.rightArm);
        m.leftPants.copyFrom(m.leftLeg);
        m.rightPants.copyFrom(m.rightLeg);
        if (shown.size() < Part.values().length) {
            m.head.visible = m.hat.visible = shown.contains(Part.HEAD);
            m.body.visible = m.jacket.visible = shown.contains(Part.BODY);
            m.rightArm.visible = m.rightSleeve.visible = shown.contains(Part.RIGHT_ARM);
            m.leftArm.visible = m.leftSleeve.visible = shown.contains(Part.LEFT_ARM);
            m.rightLeg.visible = m.rightPants.visible = shown.contains(Part.RIGHT_LEG);
            m.leftLeg.visible = m.leftPants.visible = shown.contains(Part.LEFT_LEG);
        }
    }

    @Override
    public StageNode named(final Component c) {
        return super.named(c);
    }
}
