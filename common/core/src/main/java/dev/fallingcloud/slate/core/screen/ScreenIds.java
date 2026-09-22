package dev.fallingcloud.slate.core.screen;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.jetbrains.annotations.Nullable;

/**
 * Stable ids for screens so layouts, swaps and the editor can refer to them. Vanilla screens map by
 * class to {@code minecraft:<name>}; Slate screens register themselves; anything else becomes
 * {@code <guessed modid>:<ClassSimpleName>} where the modid is guessed from the package
 * (e.g. {@code me.shedaniel.clothconfig2...} -> {@code clothconfig2}).
 */
public final class ScreenIds {

    private static final Map<String, String> BY_CLASS = new ConcurrentHashMap<>();
    private static final Map<String, String> DISPLAY = new ConcurrentHashMap<>();

    static {
        vanilla("TitleScreen", "title", "Title screen");
        vanilla("worldselection.SelectWorldScreen", "select_world", "Select world");
        vanilla("worldselection.CreateWorldScreen", "create_world", "Create world");
        vanilla("worldselection.EditWorldScreen", "edit_world", "Edit world");
        vanilla("worldselection.OptimizeWorldScreen", "optimize_world", "Optimize world");
        vanilla("multiplayer.JoinMultiplayerScreen", "multiplayer", "Multiplayer");
        vanilla("multiplayer.EditServerScreen", "edit_server", "Edit server");
        vanilla("multiplayer.DirectJoinServerScreen", "direct_connect", "Direct connect");
        vanilla("multiplayer.ServerSelectionList", "server_list", "Server list");
        vanilla("ConnectScreen", "connect", "Connecting");
        vanilla("DisconnectedScreen", "disconnected", "Disconnected");
        vanilla("PauseScreen", "pause", "Pause menu");
        vanilla("DeathScreen", "death", "Death screen");
        vanilla("ChatScreen", "chat", "Chat");
        vanilla("options.OptionsScreen", "options", "Options");
        vanilla("options.VideoSettingsScreen", "video_settings", "Video settings");
        vanilla("options.SoundOptionsScreen", "sound_options", "Music & sounds");
        vanilla("options.controls.ControlsScreen", "controls", "Controls");
        vanilla("options.controls.KeyBindsScreen", "key_binds", "Key binds");
        vanilla("options.MouseSettingsScreen", "mouse_settings", "Mouse settings");
        vanilla("options.ChatOptionsScreen", "chat_options", "Chat settings");
        vanilla("options.SkinCustomizationScreen", "skin_customization", "Skin customization");
        vanilla("options.LanguageSelectScreen", "language", "Language");
        vanilla("options.AccessibilityOptionsScreen", "accessibility", "Accessibility");
        vanilla("options.OnlineOptionsScreen", "online_options", "Online options");
        vanilla("options.TelemetryInfoScreen", "telemetry", "Telemetry");
        vanilla("options.FontOptionsScreen", "font_options", "Font settings");
        vanilla("options.CreditsAndAttributionScreen", "credits", "Credits");
        vanilla("packs.PackSelectionScreen", "packs", "Resource packs");
        vanilla("ShareToLanScreen", "share_to_lan", "Open to LAN");
        vanilla("achievement.StatsScreen", "stats", "Statistics");
        vanilla("advancements.AdvancementsScreen", "advancements", "Advancements");
        vanilla("LevelLoadingScreen", "level_loading", "Loading world");
        vanilla("ReceivingLevelScreen", "receiving_level", "Loading terrain");
        vanilla("GenericMessageScreen", "generic_message", "Message");
        vanilla("ConfirmScreen", "confirm", "Confirm");
        vanilla("AlertScreen", "alert", "Alert");
        vanilla("ConfirmLinkScreen", "confirm_link", "Open link");
        vanilla("BackupConfirmScreen", "backup_confirm", "Backup");
        vanilla("ProgressScreen", "progress", "Progress");
        vanilla("OutOfMemoryScreen", "out_of_memory", "Out of memory");
        vanilla("social.SocialInteractionsScreen", "social_interactions", "Social interactions");
        vanilla("reporting.ReportPlayerScreen", "report_player", "Report player");
        vanilla("WinScreen", "win", "End credits");
        vanilla("InBedChatScreen", "in_bed_chat", "Sleeping");
        vanilla("DemoIntroScreen", "demo_intro", "Demo");
        vanilla("AccessibilityOnboardingScreen", "accessibility_onboarding", "Accessibility onboarding");
        vanilla("Realms32bitWarningScreen", "realms_warning", "Realms warning");
        BY_CLASS.put("com.mojang.realmsclient.RealmsMainScreen", "minecraft:realms");
        DISPLAY.put("minecraft:realms", "Realms");
    }

    private static void vanilla(final String cls, final String id, final String display) {
        BY_CLASS.put("net.minecraft.client.gui.screens." + cls, "minecraft:" + id);
        DISPLAY.put("minecraft:" + id, display);
    }

    /** Slate modules register their screens so ids are readable ({@code slate_menu:title}). */
    public static void register(final Class<? extends Screen> cls, final String id, final String display) {
        BY_CLASS.put(cls.getName(), id);
        DISPLAY.put(id, display);
    }

    public static String of(@Nullable final Screen screen) {
        if (screen == null) return "none";
        return of(screen.getClass());
    }

    public static String of(final Class<?> cls) {
        final String known = BY_CLASS.get(cls.getName());
        if (known != null) return known;
        return guessNamespace(cls.getName()) + ":" + cls.getSimpleName();
    }

    /** Human label for an id (falls back to the id's path). */
    public static String display(final String id) {
        final String d = DISPLAY.get(id);
        if (d != null) return d;
        final int i = id.indexOf(':');
        return i < 0 ? id : id.substring(i + 1);
    }

    public static boolean isVanilla(final Screen screen) {
        final String n = screen.getClass().getName();
        return n.startsWith("net.minecraft.") || n.startsWith("com.mojang.");
    }

    public static boolean isSlate(final Screen screen) {
        return screen.getClass().getName().startsWith("dev.fallingcloud.slate.");
    }

    public static boolean isContainer(@Nullable final Screen screen) {
        return screen instanceof AbstractContainerScreen<?>;
    }

    /** A readable modid-ish namespace out of a class name. */
    public static String guessNamespace(final String className) {
        final String[] parts = className.split("\\.");
        if (parts.length < 2) return "unknown";
        // Skip the reverse-domain part (com/net/dev/me/io/org + one segment), take the next.
        int i = 0;
        if (parts[0].matches("com|net|dev|me|io|org|de|fr|uk|eu|xyz|cc|tv|gg") && parts.length > 2) i = 2;
        else i = 1;
        final String ns = parts[Math.min(i, parts.length - 2)].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return ns.isEmpty() ? "unknown" : ns;
    }

    /** All known ids, for the editor's pickers. */
    public static java.util.List<String> known() {
        return DISPLAY.keySet().stream().sorted().toList();
    }

    private ScreenIds() {}
}
