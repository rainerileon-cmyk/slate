package dev.fallingcloud.slate.profile.client;

import dev.fallingcloud.slate.profile.SlateProfile;
import dev.fallingcloud.slate.profile.Slot;
import java.util.function.Supplier;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * One thing a look can have in a slot. It comes in two kinds. A {@link Kind#LAYER layer} is painted: a picture in
 * the layout of a player skin that is laid over the skin (a shirt, trousers, an arm of iron). A {@link Kind#MODEL
 * model} is built: boxes in 3D that ride on a part of the player (a hat on the head, a pack on the back, a pet on
 * the shoulder).
 */
public final class Cosmetic {

    public enum Kind { LAYER, MODEL }

    /** What a model rides on. {@link #BESIDE} hovers beside the head and keeps its own way up. */
    public enum Anchor { HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG, BESIDE }

    /** How a model moves by itself. */
    public enum Motion { STILL, BOB, FLAP, BOUNCE, HOVER }

    private final String id;
    private final Slot slot;
    private final Kind kind;
    private final ResourceLocation texture;
    private final Anchor anchor;
    private final Motion motion;
    private final boolean translucent;
    @Nullable private final Supplier<ModelPart> builder;
    @Nullable private ModelPart built;
    /** Where the middle of the thing is, in pixels of its own model, and how far it reaches: for showing it alone. */
    private final float midX, midY, midZ, reach;

    private Cosmetic(final String id, final Slot slot, final Kind kind, final Anchor anchor, final Motion motion, final boolean translucent,
                     @Nullable final Supplier<ModelPart> builder, final float midX, final float midY, final float midZ, final float reach) {
        this.id = id;
        this.slot = slot;
        this.kind = kind;
        this.texture = SlateProfile.id("textures/cosmetic/" + id + ".png");
        this.anchor = anchor;
        this.motion = motion;
        this.translucent = translucent;
        this.builder = builder;
        this.midX = midX;
        this.midY = midY;
        this.midZ = midZ;
        this.reach = reach;
    }

    static Cosmetic layer(final String id, final Slot slot) {
        return new Cosmetic(id, slot, Kind.LAYER, Anchor.BODY, Motion.STILL, false, null, 0f, 0f, 0f, 8f);
    }

    /**
     * @param mid   the middle of the model, in its own pixels (x right, y down, z back, as Minecraft's models have it)
     * @param reach how far it reaches from its middle, in pixels
     */
    static Cosmetic model(final String id, final Slot slot, final Anchor anchor, final Motion motion, final boolean translucent,
                          final Supplier<ModelPart> builder, final float midX, final float midY, final float midZ, final float reach) {
        return new Cosmetic(id, slot, Kind.MODEL, anchor, motion, translucent, builder, midX, midY, midZ, reach);
    }

    public String id() { return id; }

    public Slot slot() { return slot; }

    public Kind kind() { return kind; }

    public boolean isModel() { return kind == Kind.MODEL; }

    public ResourceLocation texture() { return texture; }

    public Anchor anchor() { return anchor; }

    public Motion motion() { return motion; }

    /** Drawn with see-through pixels (a slime's skin, a lens). */
    public boolean translucent() { return translucent; }

    public Component name() { return Component.translatable("slate_profile.cosmetic." + id); }

    public float midX() { return midX; }

    public float midY() { return midY; }

    public float midZ() { return midZ; }

    public float reach() { return reach; }

    /** The model, built when first asked for. Render thread. */
    public ModelPart part() {
        if (built == null) {
            if (builder == null) throw new IllegalStateException(id + " is a layer, it has no model");
            built = builder.get();
        }
        return built;
    }
}
