package dev.fallingcloud.slate.profile.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.fallingcloud.slate.profile.SlateProfile;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * What every player is drawn with right now: for the player at this client the look that is worn, for the others
 * what the server passed on. The game's own drawing asks here (see the mixins): a player who is in here wears the
 * skin and the things noted here, everybody else is drawn as the game would.
 */
public final class AppliedLooks {

    /** A look ready to draw: its skin as a texture of the game, its model, what rides on the player in 3D. */
    public static final class Applied {
        private final ResourceLocation texture;
        private final PlayerSkin.Model model;
        private final List<Cosmetic> models;
        @Nullable private PlayerSkin last, made;

        private Applied(final ResourceLocation texture, final PlayerSkin.Model model, final List<Cosmetic> models) {
            this.texture = texture;
            this.model = model;
            this.models = List.copyOf(models);
        }

        public ResourceLocation texture() { return texture; }

        public PlayerSkin.Model model() { return model; }

        public List<Cosmetic> models() { return models; }

        /** The skin the game is handed instead of {@code original}: the look's picture and model, the account's cape. */
        public PlayerSkin over(final PlayerSkin original) {
            if (made == null || last != original) {
                last = original;
                made = new PlayerSkin(texture, original.textureUrl(), original.capeTexture(), original.elytraTexture(), model, original.secure());
            }
            return made;
        }
    }

    private static final Map<UUID, Applied> LOOKS = new ConcurrentHashMap<>();
    private static final AtomicInteger COUNTER = new AtomicInteger();

    @Nullable
    public static Applied of(@Nullable final UUID player) {
        return player == null || LOOKS.isEmpty() ? null : LOOKS.get(player);
    }

    /** Dresses a player. The picture is taken over (and closed when the look goes). Render thread. */
    public static void put(final UUID player, final NativeImage skin, final boolean slim, final List<Cosmetic> models) {
        final ResourceLocation id = SlateProfile.id("look/" + COUNTER.incrementAndGet());
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(skin));
        final Applied old = LOOKS.put(player, new Applied(id, slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE, models));
        if (old != null) Minecraft.getInstance().getTextureManager().release(old.texture);
    }

    /** The player is drawn as the game would again. Render thread. */
    public static void remove(final UUID player) {
        final Applied old = LOOKS.remove(player);
        if (old != null) Minecraft.getInstance().getTextureManager().release(old.texture);
    }

    /** Forgets everybody but {@code keep} (the player at this client): the server was left. */
    public static void removeAllBut(@Nullable final UUID keep) {
        for (final UUID id : List.copyOf(LOOKS.keySet())) if (!id.equals(keep)) remove(id);
    }

    public static int count() { return LOOKS.size(); }

    private AppliedLooks() {}
}
