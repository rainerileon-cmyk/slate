package dev.fallingcloud.slate.core.stage.node;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.stage.StageLevel;
import dev.fallingcloud.slate.core.stage.StageRenderContext;
import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Any entity type, as a real entity living in the stage level and drawn by its own renderer (so wolves wag, cats
 * lie down, armour and collars show). The entity is ticked at 20 Hz for its idle animations; if a type's tick throws
 * without a real world, ticking stops for that node and it keeps rendering in its rest pose. Living entities can
 * turn their head towards the cursor.
 */
public class EntityNode extends StageNode {

    private final StageLevel level;
    @Nullable private final Entity entity;
    private boolean tickEntity = true;
    private boolean tickBroken;
    private boolean lookAtCursor = true;
    private float lookYaw, lookPitch;
    private final Vector3f head = new Vector3f();
    private final Vector3f point = new Vector3f();

    public EntityNode(final StageLevel level, final EntityType<?> type) {
        this.level = level;
        this.entity = level.spawn(type, 0.0, 0.0, 0.0, 0f);
        if (entity != null) {
            final EntityDimensions d = entity.getDimensions(entity.getPose());
            final float w = Math.max(0.3f, d.width()), h = Math.max(0.3f, d.height());
            bounds(-w / 2f, 0f, -w / 2f, w / 2f, h, w / 2f);
            hoverFeel(0.04f, 1.04f);
        }
        named(type.getDescription());
    }

    @Nullable public Entity entity() { return entity; }

    public EntityNode tickEntity(final boolean on) { this.tickEntity = on; return this; }

    public EntityNode lookAtCursor(final boolean on) { this.lookAtCursor = on; return this; }

    /** Pets: sit down (wolves, cats, parrots). */
    public EntityNode sitting(final boolean sit) {
        if (entity instanceof TamableAnimal t) {
            t.setInSittingPose(sit);
            t.setOrderedToSit(sit);
        }
        return this;
    }

    /** Pets: tamed look (collar, tame texture). */
    public EntityNode tamed(final boolean tame) {
        if (entity instanceof TamableAnimal t) t.setTame(tame, false);
        return this;
    }

    @Override
    public void tick(final StageRenderContext ctx) {
        if (entity == null || !tickEntity || tickBroken) return;
        try {
            entity.setOldPosAndRot();
            if (entity instanceof LivingEntity le) {
                le.yBodyRotO = le.yBodyRot;
                le.yHeadRotO = le.yHeadRot;
            }
            entity.tickCount++;
            entity.tick();
            // Keep it where it was put: nothing in a stage should drift or fall.
            entity.setPos(0.0, 0.0, 0.0);
            entity.setDeltaMovement(0.0, 0.0, 0.0);
        } catch (final Exception e) {
            tickBroken = true;
            Slate.LOGGER.warn("[Slate] stage: {} cannot tick without a world, left in its rest pose: {}", EntityType.getKey(entity.getType()), e.toString());
        }
    }

    @Override
    public void update(final StageRenderContext ctx) {
        if (!(entity instanceof LivingEntity le)) return;
        float targetYaw = 0f, targetPitch = 0f;
        if (lookAtCursor && ctx.hasMouse) {
            head.set(0f, le.getEyeHeight(), 0f);
            model.transformPosition(head);
            point.set(head).sub(ctx.rayOrigin);
            final float t = Math.max(0f, point.dot(ctx.rayDir));
            point.set(ctx.rayDir).mul(t).add(ctx.rayOrigin);
            modelInverse.transformPosition(point);
            final float dx = point.x, dy = point.y - le.getEyeHeight(), dz = point.z;
            final float horiz = (float) Math.sqrt(dx * dx + dz * dz);
            if (horiz > 1e-4f || Math.abs(dy) > 1e-4f) {
                targetYaw = Mth.clamp((float) Math.toDegrees(Math.atan2(-dx, dz)), -60f, 60f);
                targetPitch = Mth.clamp((float) Math.toDegrees(-Math.atan2(dy, horiz)), -40f, 40f);
            }
        }
        final float k = Theme.current().motion() <= 0f ? 1f : 1f - (float) Math.exp(-ctx.deltaMs / 140f);
        lookYaw += (targetYaw - lookYaw) * k;
        lookPitch += (targetPitch - lookPitch) * k;
        le.yHeadRot = lookYaw;
        le.yHeadRotO = lookYaw;
        le.setXRot(lookPitch);
        le.xRotO = lookPitch;
        le.yBodyRot = 0f;
        le.yBodyRotO = 0f;
    }

    @Override
    protected void draw(final StageRenderContext ctx) {
        if (entity == null) return;
        final EntityRenderer<? super Entity> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        if (renderer == null) return;
        try {
            renderer.render(entity, 0f, ctx.partial, ctx.pose, ctx.buffers, ctx.light);
        } catch (final Exception e) {
            visible = false;
            Slate.LOGGER.warn("[Slate] stage: {} cannot render on a stage, hidden: {}", EntityType.getKey(entity.getType()), e.toString());
        }
    }

    @Override
    public void dispose() {
        if (entity != null) level.discard(entity);
    }
}
