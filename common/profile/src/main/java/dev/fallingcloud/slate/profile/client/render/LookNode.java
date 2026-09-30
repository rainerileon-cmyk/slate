package dev.fallingcloud.slate.profile.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.BufferBuilder;
import dev.fallingcloud.slate.core.stage.StageRenderContext;
import dev.fallingcloud.slate.core.stage.StageSoft;
import dev.fallingcloud.slate.core.stage.node.PlayerNode;
import dev.fallingcloud.slate.profile.SlateProfile;
import dev.fallingcloud.slate.profile.client.Cosmetic;
import dev.fallingcloud.slate.profile.client.ProfileStore;
import dev.fallingcloud.slate.profile.client.SkinComposer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * A player on a stage, dressed: a look as it will be seen in the game, the painted things on its skin and the
 * built things riding on it. The node follows its look: when something about the look changes, the skin is made
 * again. It can also stand for one painted thing on a plain figure ({@link #sample}), to show that thing alone.
 */
public final class LookNode extends PlayerNode {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Nullable private ProfileStore.Look look;
    @Nullable private Cosmetic sample;
    private boolean sampleSlim;
    private String made = "";
    @Nullable private ResourceLocation texture;
    private PlayerSkin skin = DefaultPlayerSkin.get(new java.util.UUID(0L, 0L));
    private List<Cosmetic> models = List.of();
    /** Something not in the look, worn for the moment: what the pointer is on in the list of things. */
    @Nullable private Cosmetic tryOn;
    private boolean waiting;
    private long retryAt;

    private LookNode() {
        super(() -> DefaultPlayerSkin.get(new java.util.UUID(0L, 0L)));
    }

    public LookNode(final ProfileStore.Look look) {
        this();
        this.look = look;
        named(look.name());
    }

    /** One painted thing on a plain figure. */
    public static LookNode sample(final Cosmetic layer, final boolean slim) {
        final LookNode n = new LookNode();
        n.sample = layer;
        n.sampleSlim = slim;
        n.named(layer.name());
        return n;
    }

    /** A plain figure wearing a skin out of the resource packs (one of the game's own skins, to choose from). */
    public static LookNode ofTexture(final ResourceLocation skin, final boolean slim) {
        final LookNode n = new LookNode();
        n.skin = new PlayerSkin(skin, null, null, null, slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE, true);
        n.made = "fixed";
        return n;
    }

    @Nullable public ProfileStore.Look look() { return look; }

    /** Wears {@code cosmetic} for now without it being in the look (null: only what the look has). */
    public LookNode tryOn(@Nullable final Cosmetic cosmetic) {
        if (this.tryOn != cosmetic) {
            this.tryOn = cosmetic;
            this.made = "";
        }
        return this;
    }

    @Override
    public PlayerSkin skin() { return skin; }

    private void ensure() {
        if (look == null && sample == null) return;
        final String now = look != null ? look.signature() + "|" + (tryOn == null ? "" : tryOn.id()) : "sample:" + sample.id();
        if (now.equals(made) && !(waiting && System.currentTimeMillis() >= retryAt)) return;
        made = now;
        final NativeImage image;
        final boolean slim;
        final List<Cosmetic> built = new ArrayList<>();
        if (look != null) {
            if (tryOn != null && !tryOn.isModel()) {
                // A painted thing tried on: the look is made as if it had it.
                final Cosmetic before = look.in(tryOn.slot());
                look.hold(tryOn.slot(), tryOn);
                image = SkinComposer.compose(look);
                look.hold(tryOn.slot(), before);
            } else {
                image = SkinComposer.compose(look);
            }
            slim = look.slim();
            for (final Cosmetic c : look.items()) if (c.isModel() && (tryOn == null || tryOn.slot() != c.slot())) built.add(c);
            if (tryOn != null && tryOn.isModel()) built.add(tryOn);
            // The account's own skin may still be on its way from Mojang: look again in a moment.
            waiting = "account".equals(look.skin()) && System.currentTimeMillis() < firstMade + 12_000L;
            retryAt = System.currentTimeMillis() + 1500L;
        } else {
            image = SkinComposer.sample(sample, sampleSlim);
            slim = sampleSlim;
        }
        final ResourceLocation id = SlateProfile.id("stage_look/" + COUNTER.incrementAndGet());
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        if (texture != null) Minecraft.getInstance().getTextureManager().release(texture);
        texture = id;
        skin = new PlayerSkin(id, null, null, null, slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE, true);
        models = built;
    }

    private final long firstMade = System.currentTimeMillis();

    @Override
    public void update(final StageRenderContext ctx) {
        ensure();
        super.update(ctx);
    }

    @Override
    protected void drawExtras(final StageRenderContext ctx, final PlayerModel<LivingEntity> model) {
        if (models.isEmpty()) return;
        final float age = ctx.timeMs / 50f;
        for (final Cosmetic c : models) {
            if (!rides(c)) continue;
            if (ctx.soft) {
                final BufferBuilder bb = StageSoft.begin(StageSoft.ENTITY);
                CosmeticRenderer.draw(ctx.pose, model, c, age, ctx.light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, cosmetic -> bb);
                StageSoft.end(bb, StageSoft.ENTITY, c.texture(), c.translucent() ? Math.min(0.999f, alpha()) : alpha(), false);
            } else {
                final int color = (Math.round(alpha() * 255f) << 24) | 0xFFFFFF;
                CosmeticRenderer.draw(ctx.pose, model, c, age, ctx.light, OverlayTexture.NO_OVERLAY, color,
                    cosmetic -> ctx.buffers.getBuffer(RenderType.entityTranslucent(cosmetic.texture())));
            }
        }
    }

    /** A thing is drawn when the part it rides on is. */
    private boolean rides(final Cosmetic c) {
        return switch (c.anchor()) {
            case HEAD, BESIDE -> shows(Part.HEAD);
            case RIGHT_ARM -> shows(Part.RIGHT_ARM);
            case LEFT_ARM -> shows(Part.LEFT_ARM);
            case RIGHT_LEG -> shows(Part.RIGHT_LEG);
            case LEFT_LEG -> shows(Part.LEFT_LEG);
            default -> shows(Part.BODY);
        };
    }

    @Override
    public void dispose() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(texture);
            texture = null;
        }
        super.dispose();
    }
}
