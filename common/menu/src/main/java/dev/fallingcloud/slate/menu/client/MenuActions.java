package dev.fallingcloud.slate.menu.client;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.action.ActionType;
import dev.fallingcloud.slate.core.layout.action.ActionType.Arg;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import dev.fallingcloud.slate.menu.client.screenshots.SlateScreenshotsScreen;
import dev.fallingcloud.slate.menu.client.play.model.WorldActions;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

/**
 * Dev-mode actions this module contributes. The "open" actions go through the vanilla screen classes
 * so the per-screen swap toggles keep working for layouts too.
 */
public final class MenuActions {

    public static List<ActionType> all() {
        return List.of(
            new ActionType("slate_menu:open_screenshots", Component.translatable("slate_menu.action.open_screenshots"), List.of(),
                a -> { final Minecraft mc = Minecraft.getInstance(); mc.setScreen(new SlateScreenshotsScreen(mc.screen)); }),
            new ActionType("slate_menu:open_worlds", Component.translatable("slate_menu.action.open_worlds"), List.of(),
                a -> { final Minecraft mc = Minecraft.getInstance(); mc.setScreen(new SelectWorldScreen(mc.screen)); }),
            new ActionType("slate_menu:open_servers", Component.translatable("slate_menu.action.open_servers"), List.of(),
                a -> { final Minecraft mc = Minecraft.getInstance(); mc.setScreen(new JoinMultiplayerScreen(mc.screen)); }),
            new ActionType("slate_menu:open_options", Component.translatable("slate_menu.action.open_options"), List.of(),
                a -> { final Minecraft mc = Minecraft.getInstance(); mc.setScreen(new OptionsScreen(mc.screen, mc.options)); }),
            new ActionType("slate_menu:play_last", Component.translatable("slate_menu.action.play_last"), List.of(),
                a -> LastPlayed.quick().ifPresentOrElse(t -> LastPlayed.play(t, Minecraft.getInstance().screen),
                    () -> SlateToasts.show(Component.translatable("slate_menu.title.nothing_yet"), Component.translatable("slate_menu.title.nothing_yet_hint"), Icon.INFO))),
            new ActionType("slate_menu:open_world", Component.translatable("slate_menu.action.open_world"),
                List.of(Arg.text("folder", Component.translatable("slate_menu.action.arg.folder"), "New World")),
                a -> WorldActions.play(a.getOrDefault("folder", ""), Minecraft.getInstance().screen)));
    }

    private MenuActions() {}
}
