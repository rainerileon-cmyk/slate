package dev.fallingcloud.slate.profile.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.fallingcloud.slate.core.stage.node.PlayerNode;
import dev.fallingcloud.slate.profile.SlateProfile;
import dev.fallingcloud.slate.profile.Slot;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;

/**
 * Makes the one skin picture a look is drawn with: the look's skin, and over it everything the look wears that
 * is painted (shirt, trousers, a limb that was swapped). What comes out is an ordinary player skin of 64 × 64, so
 * everything that can draw a player can draw the look, and it can be sent to other players as it is. Render thread.
 */
public final class SkinComposer {

    /** The order the painted things go on in: limbs first, then what is worn over them. */
    private static final List<Slot> ORDER = List.of(Slot.RIGHT_ARM, Slot.LEFT_ARM, Slot.RIGHT_LEG, Slot.LEFT_LEG, Slot.PANTS, Slot.SHIRT, Slot.FACE, Slot.HAT, Slot.BACK, Slot.PET);

    /** The picture of a look, with everything painted on. The caller owns it (and closes it). */
    public static NativeImage compose(final ProfileStore.Look look) {
        final NativeImage skin = base(look);
        for (final Slot slot : ORDER) {
            final Cosmetic c = look.in(slot);
            if (c == null || c.isModel()) continue;
            try (NativeImage layer = resource(c.texture())) {
                if (layer != null) lay(skin, layer);
            }
        }
        return skin;
    }

    /** One painted thing on a plain figure, to show the thing by itself. The caller owns the picture. */
    public static NativeImage sample(final Cosmetic layer, final boolean slim) {
        final NativeImage skin = mannequin();
        try (NativeImage over = resource(layer.texture())) {
            if (over != null) lay(skin, over);
        }
        return skin;
    }

    /** A figure with no face and no clothes, in a quiet grey: what a thing is shown on when it is shown alone. */
    public static NativeImage mannequin() {
        final NativeImage image = new NativeImage(64, 64, true);
        image.fillRect(0, 0, 64, 64, 0);
        // The inner layer of every part; the outer layers stay empty.
        fill(image, 0, 0, 32, 16, 0xFF9A948C);       // head
        fill(image, 16, 16, 24, 16, 0xFF8C867E);     // body
        fill(image, 40, 16, 16, 16, 0xFF938D85);     // right arm
        fill(image, 0, 16, 16, 16, 0xFF857F78);      // right leg
        fill(image, 16, 48, 16, 16, 0xFF857F78);     // left leg
        fill(image, 32, 48, 16, 16, 0xFF938D85);     // left arm
        return image;
    }

    private static void fill(final NativeImage image, final int x, final int y, final int w, final int h, final int argb) {
        // NativeImage keeps its pixels as ABGR.
        final int abgr = (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
        image.fillRect(x, y, w, h, abgr);
    }

    /** The skin a look starts from. Never null: what cannot be read falls back to the account's default skin. */
    public static NativeImage base(final ProfileStore.Look look) {
        final String source = look.skin();
        NativeImage image = null;
        if ("file".equals(source) && Files.isRegularFile(look.skinFile())) {
            try (InputStream in = Files.newInputStream(look.skinFile())) {
                image = normalised(NativeImage.read(in));
            } catch (final Exception e) {
                SlateProfile.LOGGER.warn("[Slate Profile] cannot read the skin of {}: {}", look.name(), e.toString());
            }
        } else if (source.startsWith("default:")) {
            image = resource(defaultSkin(source.substring(8), look.slim()));
        } else if ("account".equals(source)) {
            image = ofAccount(look.account().uuid(), look.account().name());
        }
        if (image == null) image = resource(DefaultPlayerSkin.get(look.account().uuid()).texture());
        if (image == null) image = mannequin();
        return image;
    }

    public static ResourceLocation defaultSkin(final String name, final boolean slim) {
        final String n = ProfileStore.Look.DEFAULTS.contains(name) ? name : "steve";
        return ResourceLocation.withDefaultNamespace("textures/entity/player/" + (slim ? "slim" : "wide") + "/" + n + ".png");
    }

    /** Whether the skin the account has is for the slim model. False while that is not known yet. */
    public static boolean accountSlim(final UUID uuid, final String name) {
        try {
            return Minecraft.getInstance().getSkinManager().getInsecureSkin(new GameProfile(uuid, name.isEmpty() ? "?" : name)).model() == PlayerSkin.Model.SLIM;
        } catch (final Exception e) {
            return DefaultPlayerSkin.get(uuid).model() == PlayerSkin.Model.SLIM;
        }
    }

    /** The skin the account has with Mojang, read back from the texture the game keeps of it. Null until it is loaded. */
    @Nullable
    private static NativeImage ofAccount(final UUID uuid, final String name) {
        try {
            final PlayerSkin skin = Minecraft.getInstance().getSkinManager().getInsecureSkin(new GameProfile(uuid, name.isEmpty() ? "?" : name));
            return texture(skin.texture());
        } catch (final Exception e) {
            return null;
        }
    }

    /** A picture out of the resource packs; null when there is none. */
    @Nullable
    public static NativeImage resource(final ResourceLocation id) {
        try {
            final var found = Minecraft.getInstance().getResourceManager().getResource(id);
            if (found.isEmpty()) return texture(id);
            try (InputStream in = found.get().open()) {
                return normalised(NativeImage.read(in));
            }
        } catch (final Exception e) {
            SlateProfile.LOGGER.warn("[Slate Profile] cannot read {}: {}", id, e.toString());
            return null;
        }
    }

    /** The pixels of a texture the game has on the graphics card (a skin it downloaded), read back. */
    @Nullable
    private static NativeImage texture(final ResourceLocation id) {
        try {
            final AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(id);
            RenderSystem.bindTexture(t.getId());
            final int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            final int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (w != 64 || (h != 64 && h != 32)) return null;
            final NativeImage image = new NativeImage(w, h, false);
            image.downloadTexture(0, false);
            return normalised(image);
        } catch (final Exception e) {
            return null;
        }
    }

    /** A skin of 64 × 64 whatever came in: the old 64 × 32 layout is brought up to date, anything else is refused. */
    @Nullable
    private static NativeImage normalised(@Nullable final NativeImage image) {
        if (image == null) return null;
        if (image.getWidth() == 64 && image.getHeight() == 64) return image;
        if (image.getWidth() == 64 && image.getHeight() == 32) return PlayerNode.upgradeLegacySkin(image);
        image.close();
        return null;
    }

    /** Lays a painted thing over a skin. A pixel that is next to empty (alpha 1) takes away what the skin has there. */
    private static void lay(final NativeImage skin, final NativeImage over) {
        final int w = Math.min(skin.getWidth(), over.getWidth()), h = Math.min(skin.getHeight(), over.getHeight());
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                final int top = over.getPixelRGBA(x, y);
                final int a = top >>> 24;
                if (a == 0) continue;
                if (a <= 2) { skin.setPixelRGBA(x, y, 0); continue; }
                if (a >= 250) { skin.setPixelRGBA(x, y, top | 0xFF000000); continue; }
                final int under = skin.getPixelRGBA(x, y);
                final int ua = under >>> 24;
                final float t = a / 255f;
                final int r = Math.round((top & 0xFF) * t + (under & 0xFF) * (1f - t));
                final int g = Math.round(((top >> 8) & 0xFF) * t + ((under >> 8) & 0xFF) * (1f - t));
                final int b = Math.round(((top >> 16) & 0xFF) * t + ((under >> 16) & 0xFF) * (1f - t));
                final int outA = ua == 0 ? a : Math.max(a, ua);
                skin.setPixelRGBA(x, y, outA << 24 | b << 16 | g << 8 | r);
            }
        }
    }

    /** The picture as a PNG, to keep or to send. */
    public static byte[] png(final NativeImage image) {
        try {
            return image.asByteArray();
        } catch (final Exception e) {
            SlateProfile.LOGGER.warn("[Slate Profile] cannot write a skin: {}", e.toString());
            return new byte[0];
        }
    }

    private SkinComposer() {}
}
