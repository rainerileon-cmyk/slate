package dev.fallingcloud.slate.core.event;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The events Slate modules can hook, fed by the loader layer (NeoForge/Fabric events) and by Core's
 * own mixins. Generic type parameters keep this class free of client class references in bytecode, so
 * it loads on a dedicated server; only the client-side loader code ever touches the client events.
 */
public final class SlateEvents {

    // ---- client
    /** End of every client tick. */
    public static final Event<Runnable> CLIENT_TICK_END = new Event<>("CLIENT_TICK_END");
    /** A screen finished {@code init()} (its widgets exist). Fires again on resize. */
    public static final Event<Consumer<Screen>> SCREEN_INIT_POST = new Event<>("SCREEN_INIT_POST");
    /** A screen was set (before its first init). {@code null} means the screen was closed. */
    public static final Event<Consumer<Screen>> SCREEN_OPENED = new Event<>("SCREEN_OPENED");
    /** After a screen rendered; the place for overlays (tooltips, toasts, editor). */
    public static final Event<ScreenRender> SCREEN_RENDER_POST = new Event<>("SCREEN_RENDER_POST");
    /** After the in-game HUD rendered. */
    public static final Event<HudRender> HUD_RENDER = new Event<>("HUD_RENDER");
    /** The local player joined a world/server (play phase). */
    public static final Event<Runnable> CLIENT_JOINED_SERVER = new Event<>("CLIENT_JOINED_SERVER");
    /** The local player left / disconnected. */
    public static final Event<Runnable> CLIENT_LEFT_SERVER = new Event<>("CLIENT_LEFT_SERVER");
    /** Raw key press while no screen is open (return true to consume). */
    public static final Event<KeyPress> KEY_PRESSED = new Event<>("KEY_PRESSED");

    // ---- server (integrated or dedicated)
    public static final Event<Consumer<MinecraftServer>> SERVER_STARTED = new Event<>("SERVER_STARTED");
    public static final Event<Consumer<MinecraftServer>> SERVER_STOPPING = new Event<>("SERVER_STOPPING");
    public static final Event<Consumer<MinecraftServer>> SERVER_TICK_END = new Event<>("SERVER_TICK_END");
    public static final Event<Consumer<ServerPlayer>> PLAYER_JOINED = new Event<>("PLAYER_JOINED");
    public static final Event<Consumer<ServerPlayer>> PLAYER_LEFT = new Event<>("PLAYER_LEFT");

    @FunctionalInterface
    public interface ScreenRender {
        void render(Screen screen, GuiGraphics graphics, int mouseX, int mouseY, float partialTick);
    }

    @FunctionalInterface
    public interface HudRender {
        void render(GuiGraphics graphics, float partialTick);
    }

    @FunctionalInterface
    public interface KeyPress {
        boolean onKey(int key, int scancode, int modifiers);
    }

    private SlateEvents() {}
}
