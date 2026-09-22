package dev.fallingcloud.slate.core.client;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.layout.action.ActionType;
import dev.fallingcloud.slate.core.layout.action.ActionType.Arg;
import dev.fallingcloud.slate.core.layout.action.Actions;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.core.screen.ScreenIds;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateSounds;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.network.chat.Component;

/**
 * The built-in dev-mode actions. Screen ids resolve through {@link #SCREEN_FACTORIES}, which modules
 * extend ({@code slate_menu} adds its screens; {@code custom:<id>} opens a custom screen).
 */
public final class CoreActions {

    /** screen id -> factory(parent). Modules register theirs; the editor lists the keys. */
    public static final Map<String, Function<Screen, Screen>> SCREEN_FACTORIES = new java.util.LinkedHashMap<>();

    static {
        SCREEN_FACTORIES.put("minecraft:title", p -> new TitleScreen());
        SCREEN_FACTORIES.put("minecraft:select_world", SelectWorldScreen::new);
        SCREEN_FACTORIES.put("minecraft:multiplayer", JoinMultiplayerScreen::new);
        SCREEN_FACTORIES.put("minecraft:options", p -> new OptionsScreen(p, Minecraft.getInstance().options));
        SCREEN_FACTORIES.put("slate:hub", SlateHubScreen::new);
        SCREEN_FACTORIES.put("slate:settings", CoreSettingsScreen::new);
    }

    public static void openScreen(final String id) {
        final Minecraft mc = Minecraft.getInstance();
        final Function<Screen, Screen> f = SCREEN_FACTORIES.get(id);
        if (f != null) { mc.setScreen(f.apply(mc.screen)); return; }
        if (id.startsWith("custom:")) { mc.setScreen(new CustomScreen(id.substring(7), mc.screen)); return; }
        Slate.LOGGER.warn("[Slate] no screen factory for {}", id);
        SlateToasts.show(Component.literal("Unknown screen"), Component.literal(id), dev.fallingcloud.slate.core.gfx.Icon.WARNING);
    }

    static void registerAll() {
        final Minecraft mc = Minecraft.getInstance();
        Actions.register(new ActionType("slate:open_screen", Component.translatable("slate.action.open_screen"),
            List.of(Arg.screen("screen", Component.translatable("slate.action.arg.screen"))),
            a -> openScreen(a.getOrDefault("screen", "minecraft:title"))));
        Actions.register(new ActionType("slate:back", Component.translatable("slate.action.back"), List.of(),
            a -> { if (mc.screen != null) mc.screen.onClose(); }));
        Actions.register(new ActionType("slate:close", Component.translatable("slate.action.close"), List.of(),
            a -> mc.setScreen(null)));
        Actions.register(new ActionType("slate:open_url", Component.translatable("slate.action.open_url"),
            List.of(Arg.text("url", Component.translatable("slate.action.arg.url"), "https://"),
                    Arg.bool("confirm", Component.translatable("slate.action.arg.confirm"), true)),
            a -> {
                final String url = a.getOrDefault("url", "");
                if (url.isBlank()) return;
                if (Boolean.parseBoolean(a.getOrDefault("confirm", "true"))) {
                    final Screen prev = mc.screen;
                    mc.setScreen(new ConfirmLinkScreen(ok -> { if (ok) SlatePlatform.get().openUri(URI.create(url)); mc.setScreen(prev); }, url, true));
                } else SlatePlatform.get().openUri(URI.create(url));
            }));
        Actions.register(new ActionType("slate:run_command", Component.translatable("slate.action.run_command"),
            List.of(Arg.text("command", Component.translatable("slate.action.arg.command"), "/help")),
            a -> {
                String cmd = a.getOrDefault("command", "").trim();
                if (cmd.isEmpty() || mc.player == null || mc.getConnection() == null) return;
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                mc.getConnection().sendCommand(cmd);
            }));
        Actions.register(new ActionType("slate:say", Component.translatable("slate.action.say"),
            List.of(Arg.text("message", Component.translatable("slate.action.arg.message"), "")),
            a -> { final String m = a.getOrDefault("message", ""); if (!m.isBlank() && mc.getConnection() != null) mc.getConnection().sendChat(m); }));
        Actions.register(new ActionType("slate:join_server", Component.translatable("slate.action.join_server"),
            List.of(Arg.text("address", Component.translatable("slate.action.arg.address"), "play.example.com"),
                    Arg.text("name", Component.translatable("slate.action.arg.name"), "Server")),
            a -> joinServer(a.getOrDefault("address", ""), a.getOrDefault("name", "Server"))));
        Actions.register(new ActionType("slate:quit", Component.translatable("slate.action.quit"), List.of(), a -> mc.stop()));
        Actions.register(new ActionType("slate:open_folder", Component.translatable("slate.action.open_folder"),
            List.of(Arg.text("path", Component.translatable("slate.action.arg.path"), "screenshots")),
            a -> {
                final java.nio.file.Path p = SlatePlatform.get().gameDir().resolve(a.getOrDefault("path", "."));
                net.minecraft.Util.getPlatform().openPath(p);
            }));
        Actions.register(new ActionType("slate:copy_text", Component.translatable("slate.action.copy_text"),
            List.of(Arg.text("text", Component.translatable("slate.action.arg.text"), "")),
            a -> { mc.keyboardHandler.setClipboard(a.getOrDefault("text", "")); SlateToasts.show(Component.translatable("slate.copied"), null, dev.fallingcloud.slate.core.gfx.Icon.COPY); }));
        Actions.register(new ActionType("slate:play_sound", Component.translatable("slate.action.play_sound"),
            List.of(Arg.text("sound", Component.translatable("slate.action.arg.sound"), "minecraft:ui.button.click"),
                    Arg.number("pitch", Component.translatable("slate.action.arg.pitch"), "1.0")),
            a -> {
                final net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(a.getOrDefault("sound", ""));
                if (rl == null) return;
                float pitch = 1f;
                try { pitch = Float.parseFloat(a.getOrDefault("pitch", "1")); } catch (final NumberFormatException ignored) {}
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(rl), pitch));
            }));
        Actions.register(new ActionType("slate:toggle_fullscreen", Component.translatable("slate.action.toggle_fullscreen"), List.of(),
            a -> { mc.getWindow().toggleFullScreen(); mc.options.fullscreen().set(mc.getWindow().isFullscreen()); mc.options.save(); }));
        Actions.register(new ActionType("slate:set_skin", Component.translatable("slate.action.set_skin"),
            List.of(Arg.choice("skin", Component.translatable("slate.action.arg.skin"), "TOGGLE", List.of("TOGGLE", "DARK", "VANILLA"))),
            a -> {
                final String s = a.getOrDefault("skin", "TOGGLE");
                Slate.configFile().update(c -> c.skin = "TOGGLE".equals(s) ? (c.isVanillaSkin() ? "DARK" : "VANILLA") : s);
                Theme.reload();
                SlateSounds.tick();
            }));
        Actions.register(new ActionType("slate:toast", Component.translatable("slate.action.toast"),
            List.of(Arg.text("title", Component.translatable("slate.action.arg.title"), "Hello"),
                    Arg.text("body", Component.translatable("slate.action.arg.body"), "")),
            a -> SlateToasts.show(Component.literal(a.getOrDefault("title", "")), a.getOrDefault("body", "").isEmpty() ? null : Component.literal(a.get("body")), dev.fallingcloud.slate.core.gfx.Icon.INFO)));
        Actions.register(new ActionType("slate:if_mod_loaded", Component.translatable("slate.action.if_mod_loaded"),
            List.of(Arg.text("mod", Component.translatable("slate.action.arg.mod"), "slate_menu"),
                    Arg.text("then", Component.translatable("slate.action.arg.then"), "slate:open_screen"),
                    Arg.text("then_arg", Component.translatable("slate.action.arg.then_arg"), "screen=slate:hub")),
            a -> {
                if (!SlatePlatform.get().isModLoaded(a.getOrDefault("mod", ""))) return;
                Actions.run(a.getOrDefault("then", ""), parseArgs(a.getOrDefault("then_arg", "")));
            }));
        Actions.register(new ActionType("slate:sequence", Component.translatable("slate.action.sequence"),
            List.of(Arg.text("actions", Component.translatable("slate.action.arg.sequence"), "slate:close; slate:open_screen screen=slate:hub")),
            a -> {
                for (final String step : a.getOrDefault("actions", "").split(";")) {
                    final String s = step.trim();
                    if (s.isEmpty()) continue;
                    final int sp = s.indexOf(' ');
                    Actions.run(sp < 0 ? s : s.substring(0, sp), sp < 0 ? Map.of() : parseArgs(s.substring(sp + 1)));
                }
            }));
    }

    /** {@code k=v k2=v2} into a map. */
    public static Map<String, String> parseArgs(final String s) {
        final Map<String, String> m = new java.util.LinkedHashMap<>();
        for (final String part : s.trim().split("\\s+")) {
            final int eq = part.indexOf('=');
            if (eq > 0) m.put(part.substring(0, eq), part.substring(eq + 1));
        }
        return m;
    }

    public static void joinServer(final String address, final String name) {
        if (address == null || address.isBlank()) return;
        final Minecraft mc = Minecraft.getInstance();
        final ServerData data = new ServerData(name == null || name.isBlank() ? address : name, address, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(mc.screen != null ? mc.screen : new TitleScreen(), mc, ServerAddress.parseString(address), data, false, null);
    }

    private CoreActions() {}
}
