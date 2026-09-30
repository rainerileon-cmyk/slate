package dev.fallingcloud.slate.menu.client.pause;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.module.Features;
import dev.fallingcloud.slate.core.module.KnownModules;
import dev.fallingcloud.slate.core.screen.slot.CoreSlots;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.menu.client.Fmt;
import dev.fallingcloud.slate.menu.client.LastPlayed;
import dev.fallingcloud.slate.menu.client.ModsScreenOpener;
import dev.fallingcloud.slate.menu.client.screenshots.SlateScreenshotsScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerLinksScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerLinks;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;

/**
 * What an escape menu does and knows, whichever layout draws it: the list of its actions (vanilla's, less the
 * feedback and bug-report links, plus Screenshots, Friends and Mods), the leaving of a world in vanilla's exact
 * sequence, and the few facts a pause menu shows about the world and the player.
 */
public final class PauseActions {

    /** How an action reads: the one that goes back to the game, the ordinary ones, the one that leaves. */
    public enum Kind { PRIMARY, NORMAL, DANGER }

    /**
     * One entry of the menu.
     *
     * @param enabled      false: shown, but cannot be used (LAN already open); {@code tip} says why
     * @param lockedModule the module it needs when that module is missing (the entry then only explains itself)
     */
    public record Action(Icon icon, Component label, Runnable run, Kind kind, boolean enabled, @Nullable Component tip, @Nullable String lockedModule) {
        public boolean locked() { return lockedModule != null; }
    }

    /**
     * The entries, top to bottom.
     *
     * @param self       the pause menu, for the screens that come back to it
     * @param keepLocked whether an entry whose module is missing stays, locked (the Overhaul layout), or is left out
     *                   (the Custom one)
     */
    public static List<Action> actions(final Screen self, final boolean keepLocked) {
        final Minecraft mc = Minecraft.getInstance();
        final List<Action> out = new ArrayList<>();
        final boolean sp = mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null;
        out.add(new Action(Icon.PLAY, Component.translatable("menu.returnToGame"), () -> mc.setScreen(null), Kind.PRIMARY, true, null, null));
        out.add(plain(Icon.TROPHY, Component.translatable("gui.advancements"),
            () -> { if (mc.player != null) mc.setScreen(new AdvancementsScreen(mc.player.connection.getAdvancements(), self)); }));
        out.add(plain(Icon.HISTORY, Component.translatable("gui.stats"),
            () -> { if (mc.player != null) mc.setScreen(new StatsScreen(self, mc.player.getStats())); }));
        out.add(plain(Icon.SETTINGS, Component.translatable("menu.options"), () -> mc.setScreen(new OptionsScreen(self, mc.options))));
        final ServerLinks links = mc.player != null ? mc.player.connection.serverLinks() : ServerLinks.EMPTY;
        if (sp) {
            final boolean open = mc.getSingleplayerServer().isPublished();
            out.add(new Action(Icon.LAN, Component.translatable("menu.shareToLan"), () -> mc.setScreen(new ShareToLanScreen(self)), Kind.NORMAL,
                !open, open ? Component.translatable("slate_menu.pause.lan_open") : null, null));
        } else if (!links.isEmpty()) {
            out.add(plain(Icon.LINK, Component.translatable("menu.server_links"), () -> mc.setScreen(new ServerLinksScreen(self, links))));
        } else {
            out.add(plain(Icon.SHIELD, Component.translatable("menu.playerReporting"),
                () -> mc.setScreen(new net.minecraft.client.gui.screens.social.SocialInteractionsScreen(self))));
        }
        out.add(plain(Icon.CAMERA, Component.translatable("slate_menu.screenshots.title"), () -> mc.setScreen(new SlateScreenshotsScreen(self))));
        if (Features.present(KnownModules.MULTIPLAYER)) {
            out.add(plain(Icon.FRIENDS, Component.translatable("slate_menu.title.friends"), () -> MenuSlots.open(CoreSlots.FRIENDS, self)));
        } else if (keepLocked) {
            out.add(new Action(Icon.FRIENDS, Component.translatable("slate_menu.title.friends"), () -> {}, Kind.NORMAL, true,
                Features.lockedTooltip(KnownModules.MULTIPLAYER), KnownModules.MULTIPLAYER));
        }
        if (SlateMenu.config().showModsButton) {
            final Optional<Function<Screen, Screen>> mods = ModsScreenOpener.factory();
            mods.ifPresent(f -> out.add(plain(Icon.MODS, Component.translatable("slate_menu.title.mods"), () -> mc.setScreen(f.apply(self)))));
        }
        out.add(new Action(Icon.EXIT, Component.translatable(sp ? "menu.returnToMenu" : "menu.disconnect"), PauseActions::confirmDisconnect,
            Kind.DANGER, true, null, null));
        return out;
    }

    private static Action plain(final Icon icon, final Component label, final Runnable run) {
        return new Action(icon, label, run, Kind.NORMAL, true, null, null);
    }

    // ------------------------------------------------------------------ leaving (vanilla's exact sequence)

    private static boolean disconnecting;

    /** Leaves the world, after asking when the player wants to be asked. */
    public static void confirmDisconnect() {
        final boolean sp = Minecraft.getInstance().hasSingleplayerServer();
        if (!SlateMenu.config().confirmQuit) { disconnect(); return; }
        SlateModal.confirm(Component.translatable(sp ? "menu.returnToMenu" : "menu.disconnect"),
            Component.translatable(sp ? "slate_menu.pause.confirm_quit_sp" : "slate_menu.pause.confirm_quit_mp"),
            Component.translatable(sp ? "menu.returnToMenu" : "menu.disconnect"), PauseActions::disconnect);
    }

    public static void disconnect() {
        if (disconnecting) return;
        disconnecting = true;
        try {
            final Minecraft mc = Minecraft.getInstance();
            final boolean local = mc.isLocalServer();
            final ServerData server = mc.getCurrentServer();
            if (mc.level != null) mc.level.disconnect();
            if (local) mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
            else mc.disconnect();
            final TitleScreen title = new TitleScreen();
            if (local) mc.setScreen(title);
            else if (server != null && server.isRealm()) mc.setScreen(new com.mojang.realmsclient.RealmsMainScreen(title));
            else mc.setScreen(new JoinMultiplayerScreen(title));
        } finally {
            disconnecting = false;
        }
    }

    // ------------------------------------------------------------------ what is shown

    /** The world's name, or the server's; vanilla's "Game Menu" when neither has one. */
    public static String worldName() {
        final Minecraft mc = Minecraft.getInstance();
        String name = null;
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) name = mc.getSingleplayerServer().getWorldData().getLevelName();
        else if (mc.getCurrentServer() != null) name = mc.getCurrentServer().name;
        return name == null || name.isBlank() ? Component.translatable("menu.game").getString() : name;
    }

    public static boolean singleplayer() {
        final Minecraft mc = Minecraft.getInstance();
        return mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null;
    }

    /** The game mode in a word, or null before the player is there. */
    @Nullable
    public static Component mode() {
        final Minecraft mc = Minecraft.getInstance();
        return mc.gameMode == null ? null : mc.gameMode.getPlayerMode().getShortDisplayName();
    }

    /** Whether health, hunger and armour mean anything right now. */
    public static boolean survives() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null) return false;
        final GameType type = mc.gameMode.getPlayerMode();
        return type == GameType.SURVIVAL || type == GameType.ADVENTURE;
    }

    /** {@code the_nether} as {@code The Nether}; null without a level. */
    @Nullable
    public static Component dimension() {
        final Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? null : Component.literal(pretty(mc.level.dimension().location().getPath()));
    }

    /** "Day 3 · 14:20" by the world's clock; null without a level. */
    @Nullable
    public static Component dayTime() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        final long time = mc.level.getDayTime();
        final long day = time / 24000L + 1;
        final long tod = Math.floorMod(time, 24000L);
        final int hour = (int) ((tod / 1000L + 6L) % 24L), minute = (int) ((tod % 1000L) * 60L / 1000L);
        return Component.translatable("slate_menu.pause.day_time", day, String.format(Locale.ROOT, "%02d:%02d", hour, minute));
    }

    /** "Playing for 12m", or null when the session is not being timed. */
    @Nullable
    public static Component session() {
        if (!SlateMenu.config().showSessionTime || LastPlayed.sessionMs() <= 0) return null;
        return Component.translatable("slate_menu.pause.session", Fmt.duration(LastPlayed.sessionMs()));
    }

    /** Where the player stands, as the debug screen would say it; null where the server asked for reduced debug info. */
    @Nullable
    public static Component position() {
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer p = mc.player;
        if (p == null || mc.showOnlyReducedInfo()) return null;
        return Component.literal(Mth.floor(p.getX()) + "  " + Mth.floor(p.getY()) + "  " + Mth.floor(p.getZ()));
    }

    /** The biome the player stands in; null under reduced debug info. */
    @Nullable
    public static Component biome() {
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.showOnlyReducedInfo()) return null;
        return mc.level.getBiome(p.blockPosition()).unwrapKey()
            .map(key -> (Component) Component.translatable("biome." + key.location().getNamespace() + "." + key.location().getPath()))
            .orElse(null);
    }

    private static String pretty(final String path) {
        final StringBuilder sb = new StringBuilder(path.length());
        boolean up = true;
        for (final char c : path.toCharArray()) {
            if (c == '_' || c == '/' || c == ':') { sb.append(' '); up = true; continue; }
            sb.append(up ? Character.toUpperCase(c) : c);
            up = false;
        }
        return sb.toString();
    }

    private PauseActions() {}
}
