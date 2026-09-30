package dev.fallingcloud.slate.profile.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.client.DevHarness;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.module.SlateModule;
import dev.fallingcloud.slate.core.net.SlateNetwork;
import dev.fallingcloud.slate.core.screen.ScreenIds;
import dev.fallingcloud.slate.core.screen.slot.CoreSlots;
import dev.fallingcloud.slate.core.screen.slot.Layout;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.profile.SlateProfile;
import dev.fallingcloud.slate.profile.Slot;
import dev.fallingcloud.slate.profile.client.ui.ProfileScreen;
import dev.fallingcloud.slate.profile.net.ProfileNet;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The client half of Slate Profile: knows who is playing, dresses them in the look they wear, tells the server,
 * dresses the others in what the server tells, and opens the profile screen.
 */
public final class ProfileClient {

    private static boolean initialised;
    private static boolean dirty = true;
    private static boolean announce;
    private static String wornSignature = "";
    private static long retryUntil;

    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        ScreenIds.register(ProfileScreen.class, "slate_profile:profile", "Profile");
        MenuSlots.provide(CoreSlots.PROFILE, Layout.CUSTOM, p -> new ProfileScreen(p, false));
        MenuSlots.provide(CoreSlots.PROFILE, Layout.OVERHAUL, p -> new ProfileScreen(p, true));
        CoreActions.SCREEN_FACTORIES.put("slate_profile:profile", p -> new ProfileScreen(p, MenuSlots.effective(CoreSlots.PROFILE) == Layout.OVERHAUL));
        CoreActions.SCREEN_FACTORIES.put("slate_profile:profile/new", p -> new ProfileScreen(p, MenuSlots.effective(CoreSlots.PROFILE) == Layout.OVERHAUL).startBlank());
        for (final Slot s : Slot.values()) {
            CoreActions.SCREEN_FACTORIES.put("slate_profile:profile/" + s.key(), p -> new ProfileScreen(p, MenuSlots.effective(CoreSlots.PROFILE) == Layout.OVERHAUL).startEditing(s));
        }
        // Every scene of the suite that shows the player shows the look the player wears.
        dev.fallingcloud.slate.core.stage.node.PlayerNode.localFactory(() -> {
            final ProfileStore.Look worn = account().worn();
            return worn == null ? null : new dev.fallingcloud.slate.profile.client.render.LookNode(worn);
        });
        SlateEvents.CLIENT_TICK_END.register(ProfileClient::tick);
        SlateEvents.CLIENT_JOINED_SERVER.register(() -> announce = true);
        SlateEvents.CLIENT_LEFT_SERVER.register(() -> AppliedLooks.removeAllBut(self()));
        final User user = Minecraft.getInstance().getUser();
        try {
            ProfileStore.seen(user.getProfileId(), user.getName(), user.getType() == User.Type.MSA);
            if (DevHarness.sampleData()) SampleLooks.ensure(account());
        } catch (final Exception e) {
            SlateProfile.LOGGER.warn("[Slate Profile] cannot read the profile folder {}: {}", ProfileStore.root(), e.toString());
        }
        SlateProfile.LOGGER.info("[Slate Profile] client ready ({} look(s) for {}, kept in {})", account().data().looks.size(), user.getName(), ProfileStore.root());
    }

    public static UUID self() {
        return Minecraft.getInstance().getUser().getProfileId();
    }

    /** The profile of whoever is playing. */
    public static ProfileStore.Account account() {
        final User user = Minecraft.getInstance().getUser();
        return ProfileStore.account(user.getProfileId(), user.getName());
    }

    /** Something about the look that is worn changed (or another is worn now): it is made again, and told. */
    public static void changed() {
        dirty = true;
    }

    /** Wears a look (null: none, the player looks as the account says). */
    public static void wear(@Nullable final ProfileStore.Look look) {
        account().wear(look);
        changed();
    }

    private static void tick() {
        final Minecraft mc = Minecraft.getInstance();
        final ProfileStore.Look worn = account().worn();
        final String signature = worn == null ? "" : worn.signature();
        if (!signature.equals(wornSignature)) dirty = true;
        // The account's own skin comes from Mojang when it comes: a look built on it is made again until it is there.
        final boolean waiting = worn != null && "account".equals(worn.skin()) && System.currentTimeMillis() < retryUntil;
        if (!dirty && !(waiting && mc.level != null && mc.level.getGameTime() % 40 == 0)) {
            if (announce && mc.getConnection() != null) { announce = false; tell(); }
            return;
        }
        if (dirty) retryUntil = System.currentTimeMillis() + 15_000L;
        dirty = false;
        wornSignature = signature;
        try {
            if (worn == null) {
                AppliedLooks.remove(self());
            } else {
                final List<Cosmetic> models = new ArrayList<>();
                for (final Cosmetic c : worn.items()) if (c.isModel()) models.add(c);
                AppliedLooks.put(self(), SkinComposer.compose(worn), worn.slim(), models);
            }
        } catch (final Exception e) {
            SlateProfile.LOGGER.error("[Slate Profile] cannot dress the player", e);
        }
        if (mc.getConnection() != null) tell();
    }

    /** Tells the server what the player looks like now. Nothing happens on a server without the module. */
    private static void tell() {
        final SlateNetwork net = SlateNetwork.get();
        if (!net.serverHasChannel(ProfileNet.LookUpdate.TYPE)) return;
        final ProfileStore.Look worn = account().worn();
        if (worn == null) return;
        try (NativeImage skin = SkinComposer.compose(worn)) {
            final List<String> models = new ArrayList<>();
            for (final Cosmetic c : worn.items()) if (c.isModel()) models.add(c.id());
            final ProfileNet.LookData data = new ProfileNet.LookData(SkinComposer.png(skin), worn.slim(), models);
            if (data.valid()) net.sendToServer(new ProfileNet.LookUpdate(data));
        } catch (final Exception e) {
            SlateProfile.LOGGER.warn("[Slate Profile] cannot send the look: {}", e.toString());
        }
    }

    /** The server says what another player looks like (or that they have no look any more). */
    public static void lookOf(final ProfileNet.LookOf message) {
        final UUID who = message.player();
        if (who.equals(self())) return;
        if (message.look().isEmpty()) {
            AppliedLooks.remove(who);
            return;
        }
        final ProfileNet.LookData data = message.look().get();
        if (!data.valid()) return;
        try {
            final NativeImage image = NativeImage.read(new ByteArrayInputStream(data.skin()));
            if (image.getWidth() != 64 || image.getHeight() != 64) { image.close(); return; }
            final List<Cosmetic> models = new ArrayList<>();
            for (final String id : data.models()) {
                final Cosmetic c = Cosmetics.get(id);
                if (c != null && c.isModel()) models.add(c);      // a thing this client does not know is left out
            }
            AppliedLooks.put(who, image, data.slim(), models);
        } catch (final Exception e) {
            SlateProfile.LOGGER.warn("[Slate Profile] the look of {} cannot be read: {}", who, e.toString());
        }
    }

    public static List<SlateModule.HubEntry> hubEntries() {
        return List.of(new SlateModule.HubEntry(Component.translatable("slate_profile.name.short"), Icon.USER,
            () -> MenuSlots.open(CoreSlots.PROFILE, Minecraft.getInstance().screen)));
    }

    private ProfileClient() {}
}
