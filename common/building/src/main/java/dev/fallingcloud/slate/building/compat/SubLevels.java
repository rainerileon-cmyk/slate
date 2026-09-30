package dev.fallingcloud.slate.building.compat;

import dev.fallingcloud.slate.building.SlateBuilding;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Sable's sub-levels (ships, contraptions with physics), as far as building on them needs to know about them.
 *
 * <p>A sub-level's blocks are ordinary blocks of the level, kept far away from everything else in a plot of their
 * own, and drawn, collided with and clicked where the sub-level is: moved, turned, perhaps scaled. Sable makes the
 * game's own crosshair, reach and placement work with that: a click on a ship gives a block position in its plot, and
 * {@code Player.canInteractWithBlock} measures to where the ship is. What Sable cannot do is what only this mod knows
 * about: where a ghost or a selection box is to be drawn, which way the player looks as the ship sees it, and how far
 * something is that was measured by hand. That is what this class is for.</p>
 *
 * <p>It talks to Sable through its companion library ({@code dev.ryanhcode.sable.companion.SableCompanion}, which
 * Sable carries with it) and by reflection, so nothing of Sable is compiled against. Without Sable every position is
 * in the world and every method here says so at the cost of one field read. A failure (the library changed) turns
 * the bridge off with one log line, and building goes on as it does without Sable.</p>
 */
public final class SubLevels {

    private static final String COMPANION = "dev.ryanhcode.sable.companion.SableCompanion";

    /**
     * How a sub-level lies in the world at one moment: a point of its plot is moved to the sub-level's rotation
     * point, scaled, turned, and put where the sub-level is.
     */
    public static final class Pose {
        private final UUID id;
        private final Vector3d position, rotationPoint, scale;
        private final Quaterniond orientation;

        private Pose(final UUID id, final Vector3dc position, final Quaterniondc orientation, final Vector3dc rotationPoint, final Vector3dc scale) {
            this.id = id;
            this.position = new Vector3d(position);
            this.orientation = new Quaterniond(orientation);
            this.rotationPoint = new Vector3d(rotationPoint);
            this.scale = new Vector3d(scale);
        }

        /** Which sub-level this is. */
        public UUID id() {
            return id;
        }

        /** A point of the plot, where it is in the world. */
        public Vec3 toWorld(final Vec3 local) {
            final Vector3d v = toWorld(local.x, local.y, local.z, new Vector3d());
            return new Vec3(v.x, v.y, v.z);
        }

        private Vector3d toWorld(final double x, final double y, final double z, final Vector3d out) {
            out.set(x, y, z).sub(rotationPoint).mul(scale);
            return orientation.transform(out).add(position);
        }

        /** A point of the world, where it is in the plot. */
        public Vec3 toLocal(final Vec3 world) {
            final Vector3d v = new Vector3d(world.x, world.y, world.z).sub(position);
            orientation.transformInverse(v).div(scale).add(rotationPoint);
            return new Vec3(v.x, v.y, v.z);
        }

        /** A direction of the world, as the sub-level has it: turned, never stretched. */
        public Vec3 dirToLocal(final Vec3 dir) {
            final Vector3d v = orientation.transformInverse(new Vector3d(dir.x, dir.y, dir.z));
            return new Vec3(v.x, v.y, v.z);
        }

        /** A direction of the sub-level, as the world has it. */
        public Vec3 dirToWorld(final Vec3 dir) {
            final Vector3d v = orientation.transform(new Vector3d(dir.x, dir.y, dir.z));
            return new Vec3(v.x, v.y, v.z);
        }

        /** How many blocks of the world one block of the sub-level is: 1, unless something has scaled it. */
        public double size() {
            return Math.max(1.0E-6, Math.max(scale.x, Math.max(scale.y, scale.z)));
        }

        /** How the sub-level is turned. */
        public Quaternionf rotation() {
            return new Quaternionf((float) orientation.x, (float) orientation.y, (float) orientation.z, (float) orientation.w);
        }

        /**
         * Puts on {@code matrix} what draws a mesh that was built round {@code origin} (a block of the plot) where
         * the sub-level shows it: moved to where that block is seen from {@code cam}, turned and scaled as the
         * sub-level is. The large numbers of the plot are taken off in doubles, so the matrix holds small ones.
         */
        public Matrix4f place(final Matrix4f matrix, final Vec3i origin, final Vec3 cam) {
            final Vector3d w = toWorld(origin.getX(), origin.getY(), origin.getZ(), new Vector3d());
            return matrix.translate((float) (w.x - cam.x), (float) (w.y - cam.y), (float) (w.z - cam.z))
                .rotate(rotation())
                .scale((float) scale.x, (float) scale.y, (float) scale.z);
        }
    }

    private static volatile boolean resolved;
    private static @Nullable Object companion;
    private static @Nullable MethodHandle containing, containingClient, logicalPose, renderPose, uniqueId, posePosition, poseOrientation,
        poseRotationPoint, poseScale, eyeInterpolated;

    /** Whether Sable (or anything else that speaks for sub-levels through its companion library) is there. */
    public static boolean present() {
        resolve();
        return companion != null;
    }

    /** The pose of the sub-level {@code pos} lies in, as the game's logic has it this tick; null in the world itself. */
    public static @Nullable Pose at(final @Nullable Level level, final Vec3i pos) {
        resolve();
        final Object c = companion;
        if (c == null || level == null) return null;
        try {
            final Object sub = (Object) containing.invokeExact(c, level, pos);
            return sub == null ? null : pose(sub, (Object) logicalPose.invokeExact(sub));
        } catch (final Throwable t) {
            disable("getContaining", t);
            return null;
        }
    }

    /**
     * The pose the sub-level {@code pos} lies in is drawn with in this frame; null in the world itself. Client only,
     * render thread.
     */
    public static @Nullable Pose renderAt(final Vec3i pos) {
        resolve();
        final Object c = companion;
        if (c == null) return null;
        try {
            final Object sub = (Object) containingClient.invokeExact(c, pos);
            return sub == null ? null : pose(sub, (Object) renderPose.invokeExact(sub));
        } catch (final Throwable t) {
            disable("getContainingClient", t);
            return null;
        }
    }

    /** Which sub-level {@code pos} lies in, as the client has it; null in the world itself. Client only. */
    public static @Nullable UUID renderIdAt(final Vec3i pos) {
        resolve();
        final Object c = companion;
        if (c == null) return null;
        try {
            final Object sub = (Object) containingClient.invokeExact(c, pos);
            return sub == null ? null : (UUID) uniqueId.invokeExact(sub);
        } catch (final Throwable t) {
            disable("getContainingClient", t);
            return null;
        }
    }

    /** Which sub-level {@code pos} lies in; null in the world itself. */
    public static @Nullable UUID idAt(final @Nullable Level level, final Vec3i pos) {
        resolve();
        final Object c = companion;
        if (c == null || level == null) return null;
        try {
            final Object sub = (Object) containing.invokeExact(c, level, pos);
            return sub == null ? null : (UUID) uniqueId.invokeExact(sub);
        } catch (final Throwable t) {
            disable("getContaining", t);
            return null;
        }
    }

    /**
     * Where {@code entity}'s eyes are in this frame, as the crosshair has them. With Sable an entity a sub-level
     * carries is moved with it between ticks, in step with the pose the sub-level is drawn with; the game's own
     * interpolation knows nothing of that. Client only.
     */
    public static Vec3 eye(final Entity entity, final float partialTick) {
        resolve();
        final Object c = companion;
        final MethodHandle eyes = eyeInterpolated;
        if (c == null || eyes == null) return entity.getEyePosition(partialTick);
        try {
            return (Vec3) eyes.invokeExact(c, entity, partialTick);
        } catch (final Throwable t) {
            disable("getEyePositionInterpolated", t);
            return entity.getEyePosition(partialTick);
        }
    }

    /** Whether two positions are in the same space: both in the world, or both in one sub-level. */
    public static boolean same(final @Nullable Level level, final Vec3i a, final Vec3i b) {
        if (!present()) return true;
        return Objects.equals(idAt(level, a), idAt(level, b));
    }

    /** A point of the world as the space of {@code pos} has it: itself, unless {@code pos} lies in a sub-level. */
    public static Vec3 toSpaceOf(final @Nullable Level level, final Vec3i pos, final Vec3 world) {
        final Pose pose = at(level, pos);
        return pose == null ? world : pose.toLocal(world);
    }

    /** A point of the space {@code pos} lies in, where it is in the world: for distances, sounds, particles. */
    public static Vec3 toWorld(final @Nullable Level level, final Vec3 point) {
        final Pose pose = at(level, BlockPos.containing(point));
        return pose == null ? point : pose.toWorld(point);
    }

    private static Pose pose(final Object sub, final Object pose) throws Throwable {
        return new Pose((UUID) uniqueId.invokeExact(sub), (Vector3dc) posePosition.invokeExact(pose), (Quaterniondc) poseOrientation.invokeExact(pose),
            (Vector3dc) poseRotationPoint.invokeExact(pose), (Vector3dc) poseScale.invokeExact(pose));
    }

    private static void resolve() {
        if (resolved) return;
        synchronized (SubLevels.class) {
            if (resolved) return;
            try {
                final Class<?> api = Class.forName(COMPANION);
                final Class<?> access = Class.forName("dev.ryanhcode.sable.companion.SubLevelAccess");
                final Class<?> clientAccess = Class.forName("dev.ryanhcode.sable.companion.ClientSubLevelAccess");
                final Class<?> pose = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
                final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                final Object instance = lookup.findStaticGetter(api, "INSTANCE", api).invoke();
                // Every handle takes and gives plain objects: the call sites know no type of Sable's.
                containing = lookup.findVirtual(api, "getContaining", MethodType.methodType(access, Level.class, Vec3i.class))
                    .asType(MethodType.methodType(Object.class, Object.class, Level.class, Vec3i.class));
                containingClient = lookup.findVirtual(api, "getContainingClient", MethodType.methodType(clientAccess, Vec3i.class))
                    .asType(MethodType.methodType(Object.class, Object.class, Vec3i.class));
                logicalPose = lookup.findVirtual(access, "logicalPose", MethodType.methodType(pose))
                    .asType(MethodType.methodType(Object.class, Object.class));
                renderPose = lookup.findVirtual(clientAccess, "renderPose", MethodType.methodType(pose))
                    .asType(MethodType.methodType(Object.class, Object.class));
                uniqueId = lookup.findVirtual(access, "getUniqueId", MethodType.methodType(UUID.class))
                    .asType(MethodType.methodType(UUID.class, Object.class));
                posePosition = lookup.findVirtual(pose, "position", MethodType.methodType(Vector3dc.class))
                    .asType(MethodType.methodType(Vector3dc.class, Object.class));
                poseOrientation = lookup.findVirtual(pose, "orientation", MethodType.methodType(Quaterniondc.class))
                    .asType(MethodType.methodType(Quaterniondc.class, Object.class));
                poseRotationPoint = lookup.findVirtual(pose, "rotationPoint", MethodType.methodType(Vector3dc.class))
                    .asType(MethodType.methodType(Vector3dc.class, Object.class));
                poseScale = lookup.findVirtual(pose, "scale", MethodType.methodType(Vector3dc.class))
                    .asType(MethodType.methodType(Vector3dc.class, Object.class));
                try {
                    eyeInterpolated = lookup.findVirtual(api, "getEyePositionInterpolated", MethodType.methodType(Vec3.class, Entity.class, float.class))
                        .asType(MethodType.methodType(Vec3.class, Object.class, Entity.class, float.class));
                } catch (final ReflectiveOperationException e) {
                    eyeInterpolated = null;   // a companion without it: the game's own interpolation does
                }
                companion = instance;
                SlateBuilding.LOGGER.info("[Slate Building] Sable is here: building follows its sub-levels");
            } catch (final ClassNotFoundException e) {
                // No Sable: every position is in the world.
            } catch (final Throwable t) {
                SlateBuilding.LOGGER.warn("[Slate Building] Sable's companion library is not what this was written for; sub-levels are ignored: {}", t.toString());
            }
            resolved = true;
        }
    }

    private static void disable(final String what, final Throwable t) {
        if (companion == null) return;
        companion = null;
        SlateBuilding.LOGGER.warn("[Slate Building] Sable's {} failed; sub-levels are ignored from here on: {}", what, t.toString());
    }

    private SubLevels() {}
}
