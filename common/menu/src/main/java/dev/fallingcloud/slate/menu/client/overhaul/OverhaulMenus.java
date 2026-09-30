package dev.fallingcloud.slate.menu.client.overhaul;

import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.screen.ScreenIds;
import dev.fallingcloud.slate.core.screen.slot.CoreSlots;
import dev.fallingcloud.slate.core.screen.slot.Layout;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.menu.client.overhaul.create.OverhaulCreateWorldScreen;
import dev.fallingcloud.slate.menu.client.overhaul.play.OverhaulPlayScreen;
import dev.fallingcloud.slate.menu.client.overhaul.title.OverhaulTitleScreen;
import dev.fallingcloud.slate.menu.mixin.JoinMultiplayerScreenAccessor;
import dev.fallingcloud.slate.menu.mixin.SelectWorldScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

/**
 * The UI module's Overhaul layouts (docs/LAYOUTS.md): the scene-based screens, provided to Core's menu slots next
 * to the Custom ones. Which layout a menu shows is Core's to decide, from the global layout and the per-menu choice.
 */
public final class OverhaulMenus {

    public static void register() {
        ScreenIds.register(OverhaulTitleScreen.class, "slate_menu:title_overhaul", "Main menu (Overhaul)");
        MenuSlots.provide(CoreSlots.TITLE, Layout.OVERHAUL, s -> Minecraft.getInstance().isDemo() ? null : new OverhaulTitleScreen());
        CoreActions.SCREEN_FACTORIES.put("slate_menu:title_overhaul", p -> new OverhaulTitleScreen());

        // Worlds and servers are one screen in this layout: two rings, one in use. Which one depends on the way in.
        ScreenIds.register(OverhaulPlayScreen.class, "slate_menu:play_overhaul", "Play (Overhaul)");
        MenuSlots.provide(CoreSlots.WORLDS, Layout.OVERHAUL, s -> new OverhaulPlayScreen(((SelectWorldScreenAccessor) s).slate$lastScreen(), false));
        MenuSlots.provide(CoreSlots.SERVERS, Layout.OVERHAUL, s -> new OverhaulPlayScreen(((JoinMultiplayerScreenAccessor) s).slate$lastScreen(), true));
        CoreActions.SCREEN_FACTORIES.put("slate_menu:play_overhaul", p -> new OverhaulPlayScreen(p, false));
        CoreActions.SCREEN_FACTORIES.put("slate_menu:play_overhaul_servers", p -> new OverhaulPlayScreen(p, true));

        // World creation: vanilla's screen stays behind Slate's and does the creating. The Overhaul layout shows the
        // world that is about to be made; the Custom layout is the same settings without the view.
        ScreenIds.register(OverhaulCreateWorldScreen.class, "slate_menu:create_world", "Create world");
        MenuSlots.provide(CoreSlots.CREATE_WORLD, Layout.OVERHAUL, s -> s instanceof CreateWorldScreen c ? new OverhaulCreateWorldScreen(c, true) : null);
        // Vanilla loads the world data and then shows its screen itself; what it shows is what is opened.
        CoreActions.SCREEN_FACTORIES.put("slate_menu:create_world", p -> {
            CreateWorldScreen.openFresh(Minecraft.getInstance(), p);
            return Minecraft.getInstance().screen;
        });
        MenuSlots.provide(CoreSlots.CREATE_WORLD, Layout.CUSTOM, s -> s instanceof CreateWorldScreen c ? new OverhaulCreateWorldScreen(c, false) : null);
    }

    private OverhaulMenus() {}
}
