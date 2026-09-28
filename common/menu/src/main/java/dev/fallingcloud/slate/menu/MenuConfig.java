package dev.fallingcloud.slate.menu;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code config/slate/menu.json}. Public fields with initialiser defaults (see Core's JsonConfig): a
 * missing key keeps its default, so old files never crash. The "last played" block at the bottom is
 * written by the module itself (continue card, {@code play_last} action, disconnected-screen reconnect).
 */
public final class MenuConfig {

    // ---- which vanilla screens are replaced (false = vanilla screen, Core still restyles it)
    public boolean titleScreen = true;
    public boolean worldsScreen = true;
    public boolean serversScreen = true;
    public boolean pauseScreen = true;
    public boolean optionsScreen = true;
    public boolean disconnectedScreen = true;
    /**
     * Slate's look for the loading screens (loading a world, joining a server, loading terrain, saving, progress). They
     * are drawn over, not replaced: the game drives them, so vanilla's screen and its logic stay. Also read, as text, by
     * the NeoForge start-up window jar (slate-earlywindow), which draws only while this is on.
     */
    public boolean loadingScreens = true;

    // ---- title screen
    public boolean panorama = true;
    public boolean splash = true;
    /** Minecraft logo (true) or a Slate wordmark (false). */
    public boolean showLogo = true;
    public boolean showContinueCard = true;
    public boolean showAccountCard = true;
    /** The Multiplayer module's friends panel next to the nav (only when that module is installed). Off by default. */
    public boolean showFriendsPanel = false;
    public boolean showFooter = true;
    /** Title-screen button for the loader's mod list (when one exists). */
    public boolean showModsButton = true;
    /** Ask before the Quit button closes the game. */
    public boolean confirmQuitGame = true;
    /** Skip vanilla's first-launch accessibility screen (narrator / text size prompt) and go straight to the title. */
    public boolean skipOnboarding = true;

    // ---- worlds
    /** {@code GRID} or {@code LIST}. */
    public String worldsView = "GRID";
    /** {@code LAST_PLAYED}, {@code NAME}, {@code SIZE}, {@code FAVORITES}. */
    public String worldsSort = "LAST_PLAYED";
    public boolean worldsShowDetails = true;

    // ---- servers
    public boolean serverAutoRefresh = true;
    public int serverRefreshSeconds = 30;
    /** {@code MANUAL}, {@code NAME}, {@code PING}, {@code PLAYERS}. */
    public String serversSort = "MANUAL";
    public boolean showLan = true;
    public boolean showCommunity = true;
    public boolean showRecent = true;
    public int recentServersMax = 8;

    // ---- screenshots
    /** {@code NEWEST}, {@code OLDEST}, {@code NAME}. */
    public String screenshotsSort = "NEWEST";
    /** Thumbnail columns; 0 = pick from the window width. */
    public int screenshotColumns = 0;

    // ---- pause menu
    public boolean confirmQuit = true;
    public boolean showSessionTime = true;

    // ---- written by the module (migrations, last played target, recent servers)
    /** One-time migration done: the friends panel went opt-in, so a file written while it defaulted on was switched off. */
    public boolean friendsPanelOptIn = false;
    /** {@code world} or {@code server}; empty = nothing recorded yet. */
    public String lastKind = "";
    public String lastWorld = "";
    public String lastWorldName = "";
    public String lastServerAddress = "";
    public String lastServerName = "";
    public long lastPlayedAt = 0;
    public List<RecentServer> recentServers = new ArrayList<>();

    public static final class RecentServer {
        public String name = "";
        public String address = "";
        public long lastJoinedAt;

        public RecentServer() {}

        public RecentServer(final String name, final String address, final long at) {
            this.name = name;
            this.address = address;
            this.lastJoinedAt = at;
        }
    }
}
