package dev.fallingcloud.slate.profile;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.module.SlateModule;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.profile.net.ProfileNet;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Slate Profile module, the suite's player customization: looks (a skin and what is worn with it) to switch
 * between, a profile picture and a bio, kept on the PC for every instance and every version of the game. Both
 * sides: a server that has the module passes every player's look on to the others, so those who have the module
 * too see it. The client half is {@code client.ProfileClient}; its classes are only ever referenced from
 * client-side entry points, so a dedicated server never resolves them.
 */
public final class SlateProfile implements SlateModule {

    public static final String MOD_ID = "slate_profile";
    public static final Logger LOGGER = LoggerFactory.getLogger("Slate Profile");
    public static final SlateProfile MODULE = new SlateProfile();

    private SlateProfile() {}

    public static ResourceLocation id(final String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override public String id() { return MOD_ID; }

    @Override public Component displayName() { return Component.translatable("slate_profile.name"); }

    @Override public Icon icon() { return Icon.USER; }

    @Override
    public void init() {
        Slate.init();
        ProfileNet.register();
        LOGGER.info("[Slate Profile] init");
    }

    @Override
    public void initClient() {
        ClientSide.init();
    }

    @Override
    public List<HubEntry> hubEntries() {
        return SlatePlatform.get().isClient() ? ClientSide.hubEntries() : List.of();
    }

    /** Client-only references live here so a dedicated server never resolves them. */
    private static final class ClientSide {
        static void init() { dev.fallingcloud.slate.profile.client.ProfileClient.init(); }
        static List<HubEntry> hubEntries() { return dev.fallingcloud.slate.profile.client.ProfileClient.hubEntries(); }
    }
}
