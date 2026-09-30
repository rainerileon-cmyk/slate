package dev.fallingcloud.slate.profile.client;

import dev.fallingcloud.slate.profile.Slot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import org.jetbrains.annotations.Nullable;

/**
 * Everything there is to put in a slot. For now that is the module's own sample set: something for every slot, to
 * test with and to start from. The textures are painted by {@code tools/overhaul/ProfileTextures.java}; the boxes
 * built here are the boxes painted there, same offsets, same sizes.
 *
 * <p>Models are built in the space of the part they ride on, in pixels, as Minecraft's own models are: x to the
 * player's left, y down, z to the back, the part's pivot at the origin. A head reaches from -8 to 0 in y and from
 * -4 to 4 in x and z; a body from 0 to 12 in y, from -4 to 4 in x and from -2 to 2 in z.</p>
 */
public final class Cosmetics {

    private static final Map<String, Cosmetic> ALL = new LinkedHashMap<>();

    static {
        // ---- worn
        add(Cosmetic.model("hat_top", Slot.HAT, Cosmetic.Anchor.HEAD, Cosmetic.Motion.STILL, false, Cosmetics::topHat, 0f, -12f, 0f, 8f));
        add(Cosmetic.model("hat_crown", Slot.HAT, Cosmetic.Anchor.HEAD, Cosmetic.Motion.STILL, false, Cosmetics::crown, 0f, -10f, 0f, 7f));
        add(Cosmetic.model("hat_straw", Slot.HAT, Cosmetic.Anchor.HEAD, Cosmetic.Motion.STILL, false, Cosmetics::strawHat, 0f, -9.5f, 0f, 10f));
        add(Cosmetic.model("face_glasses", Slot.FACE, Cosmetic.Anchor.HEAD, Cosmetic.Motion.STILL, true, Cosmetics::glasses, 0f, -4f, -3f, 6f));
        add(Cosmetic.layer("face_bandana", Slot.FACE));
        add(Cosmetic.layer("shirt_hoodie", Slot.SHIRT));
        add(Cosmetic.layer("shirt_striped", Slot.SHIRT));
        add(Cosmetic.layer("pants_jeans", Slot.PANTS));
        add(Cosmetic.layer("pants_shorts", Slot.PANTS));
        add(Cosmetic.model("back_pack", Slot.BACK, Cosmetic.Anchor.BODY, Cosmetic.Motion.STILL, false, Cosmetics::pack, 0f, 4.5f, 3.5f, 7f));
        add(Cosmetic.model("back_wings", Slot.BACK, Cosmetic.Anchor.BODY, Cosmetic.Motion.FLAP, false, Cosmetics::wings, 0f, 5f, 3f, 12f));
        // ---- of the body
        add(Cosmetic.layer("arm_iron_right", Slot.RIGHT_ARM));
        add(Cosmetic.layer("arm_bandage_right", Slot.RIGHT_ARM));
        add(Cosmetic.layer("arm_iron_left", Slot.LEFT_ARM));
        add(Cosmetic.layer("arm_bandage_left", Slot.LEFT_ARM));
        add(Cosmetic.layer("leg_wood_right", Slot.RIGHT_LEG));
        add(Cosmetic.layer("leg_stocking_right", Slot.RIGHT_LEG));
        add(Cosmetic.layer("leg_wood_left", Slot.LEFT_LEG));
        add(Cosmetic.layer("leg_stocking_left", Slot.LEFT_LEG));
        add(Cosmetic.model("pet_slime", Slot.PET, Cosmetic.Anchor.BODY, Cosmetic.Motion.BOUNCE, true, Cosmetics::slime, -6.5f, -3f, 0f, 4.5f));
        add(Cosmetic.model("pet_bee", Slot.PET, Cosmetic.Anchor.BESIDE, Cosmetic.Motion.HOVER, true, Cosmetics::bee, 0f, 0f, 0f, 4f));
    }

    private static void add(final Cosmetic c) { ALL.put(c.id(), c); }

    @Nullable
    public static Cosmetic get(@Nullable final String id) { return id == null ? null : ALL.get(id); }

    public static List<Cosmetic> all() { return new ArrayList<>(ALL.values()); }

    public static List<Cosmetic> of(final Slot slot) {
        final List<Cosmetic> out = new ArrayList<>();
        for (final Cosmetic c : ALL.values()) if (c.slot() == slot) out.add(c);
        return out;
    }

    // ------------------------------------------------------------------ the models

    private static ModelPart bake(final MeshDefinition mesh, final int width, final int height) {
        return LayerDefinition.create(mesh, width, height).bakeRoot();
    }

    private static ModelPart topHat() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("brim", CubeListBuilder.create().texOffs(0, 0).addBox(-5f, -9f, -5f, 10f, 1f, 10f), PartPose.ZERO);
        root.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(0, 11).addBox(-3f, -16f, -3f, 6f, 7f, 6f), PartPose.ZERO);
        // Worn a little askew.
        final ModelPart part = bake(mesh, 64, 32);
        part.zRot = 0.06f;
        return part;
    }

    private static ModelPart crown() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("band", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5f, -10f, -4.5f, 9f, 2f, 9f), PartPose.ZERO);
        // Points round the band: short ones at the corners, tall ones in the middle of every side.
        final float[][] corners = {{-4.5f, -4.5f}, {3.5f, -4.5f}, {-4.5f, 3.5f}, {3.5f, 3.5f}};
        for (int i = 0; i < corners.length; i++) {
            root.addOrReplaceChild("corner" + i, CubeListBuilder.create().texOffs(0, 11).addBox(corners[i][0], -12f, corners[i][1], 1f, 2f, 1f), PartPose.ZERO);
        }
        final float[][] middles = {{-0.5f, -4.5f}, {-0.5f, 3.5f}, {-4.5f, -0.5f}, {3.5f, -0.5f}};
        for (int i = 0; i < middles.length; i++) {
            root.addOrReplaceChild("middle" + i, CubeListBuilder.create().texOffs(8, 11).addBox(middles[i][0], -13f, middles[i][1], 1f, 3f, 1f), PartPose.ZERO);
        }
        return bake(mesh, 64, 32);
    }

    private static ModelPart strawHat() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("brim", CubeListBuilder.create().texOffs(0, 0).addBox(-7f, -9f, -7f, 14f, 1f, 14f), PartPose.ZERO);
        root.addOrReplaceChild("crown", CubeListBuilder.create().texOffs(0, 15).addBox(-4f, -12f, -4f, 8f, 3f, 8f), PartPose.ZERO);
        return bake(mesh, 64, 32);
    }

    private static ModelPart glasses() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("right", CubeListBuilder.create().texOffs(0, 0).addBox(-4f, -5.5f, -5f, 3f, 3f, 1f), PartPose.ZERO);
        root.addOrReplaceChild("left", CubeListBuilder.create().texOffs(0, 0).mirror().addBox(1f, -5.5f, -5f, 3f, 3f, 1f), PartPose.ZERO);
        root.addOrReplaceChild("bridge", CubeListBuilder.create().texOffs(8, 0).addBox(-1f, -4.5f, -5f, 2f, 1f, 1f), PartPose.ZERO);
        root.addOrReplaceChild("temple_right", CubeListBuilder.create().texOffs(0, 4).addBox(-5f, -4.5f, -5f, 1f, 1f, 5f), PartPose.ZERO);
        root.addOrReplaceChild("temple_left", CubeListBuilder.create().texOffs(0, 4).mirror().addBox(4f, -4.5f, -5f, 1f, 1f, 5f), PartPose.ZERO);
        return bake(mesh, 32, 16);
    }

    private static ModelPart pack() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("sack", CubeListBuilder.create().texOffs(0, 0).addBox(-3f, 1f, 2f, 6f, 8f, 3f), PartPose.ZERO);
        root.addOrReplaceChild("flap", CubeListBuilder.create().texOffs(18, 0).addBox(-3f, 1f, 5f, 6f, 3f, 1f), PartPose.ZERO);
        root.addOrReplaceChild("roll", CubeListBuilder.create().texOffs(0, 11).addBox(-2f, -1f, 1.5f, 4f, 2f, 4f), PartPose.ZERO);
        return bake(mesh, 64, 32);
    }

    private static ModelPart wings() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        // Each wing hangs on its own hinge by the spine, so it can beat.
        root.addOrReplaceChild("left", CubeListBuilder.create().texOffs(0, 0).addBox(0f, -1f, 0f, 9f, 11f, 1f), PartPose.offsetAndRotation(1f, 1f, 2.2f, 0f, -0.5f, 0f));
        root.addOrReplaceChild("right", CubeListBuilder.create().texOffs(0, 0).mirror().addBox(-9f, -1f, 0f, 9f, 11f, 1f), PartPose.offsetAndRotation(-1f, 1f, 2.2f, 0f, 0.5f, 0f));
        return bake(mesh, 64, 32);
    }

    private static ModelPart slime() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        // On the right shoulder: the core first, so the skin round it is drawn over it.
        root.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 10).addBox(-8f, -4f, -1.5f, 3f, 3f, 3f), PartPose.ZERO);
        root.addOrReplaceChild("skin", CubeListBuilder.create().texOffs(0, 0).addBox(-9f, -5.2f, -2.5f, 5f, 5f, 5f), PartPose.ZERO);
        return bake(mesh, 32, 32);
    }

    private static ModelPart bee() {
        final MeshDefinition mesh = new MeshDefinition();
        final PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5f, -1.5f, -2f, 3f, 3f, 4f), PartPose.ZERO);
        root.addOrReplaceChild("left", CubeListBuilder.create().texOffs(0, 8).addBox(0f, 0f, -1f, 3f, 1f, 2f), PartPose.offsetAndRotation(0.5f, -2f, 0f, 0f, 0f, -0.4f));
        root.addOrReplaceChild("right", CubeListBuilder.create().texOffs(0, 8).mirror().addBox(-3f, 0f, -1f, 3f, 1f, 2f), PartPose.offsetAndRotation(-0.5f, -2f, 0f, 0f, 0f, 0.4f));
        return bake(mesh, 32, 32);
    }

    private Cosmetics() {}
}
