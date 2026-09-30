package dev.fallingcloud.slate.core.stage;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.layout.action.ActionType;
import dev.fallingcloud.slate.core.layout.action.ActionType.Arg;
import dev.fallingcloud.slate.core.layout.action.Actions;
import dev.fallingcloud.slate.core.screen.ScreenIds;
import dev.fallingcloud.slate.core.stage.platform.StageCommands;
import dev.fallingcloud.slate.core.stage.scene.SceneCommandHandler;
import dev.fallingcloud.slate.core.stage.test.StageTestScreen;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * Client bootstrap of the stage toolkit: the test screen ids, the scene-capture dev-mode action and client command,
 * and the hook that closes stages when their screen goes away. Called once from {@code SlateClient.init()}.
 */
public final class StageBootstrap {

    private static boolean initialised;

    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        ScreenIds.register(StageTestScreen.class, "slate:stage_test", "Stage test");
        CoreActions.SCREEN_FACTORIES.put("slate:stage_test", p -> new StageTestScreen(p, StageTestScreen.Variant.DEFAULT));
        CoreActions.SCREEN_FACTORIES.put("slate:stage_test/open", p -> new StageTestScreen(p, StageTestScreen.Variant.OPEN));
        CoreActions.SCREEN_FACTORIES.put("slate:stage_test/motion0", p -> new StageTestScreen(p, StageTestScreen.Variant.MOTION0));
        SlateEvents.SCREEN_OPENED.register(Stage::onScreenOpened);

        final SceneCommandHandler handler = new SceneCommandHandler();
        Actions.register(new ActionType("slate:scene_capture", Component.translatable("slate.action.scene_capture"),
            List.of(Arg.text("template", Component.translatable("slate.action.arg.template"), "title"),
                    Arg.text("scene", Component.translatable("slate.action.arg.scene"), "slate_menu:title"),
                    Arg.text("origin", Component.translatable("slate.action.arg.origin"), "")),
            a -> {
                BlockPos origin = null;
                final String o = a.getOrDefault("origin", "").trim();
                if (!o.isEmpty()) {
                    final String[] parts = o.split("[ ,]+");
                    if (parts.length == 3) {
                        try {
                            origin = new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
                        } catch (final NumberFormatException ignored) {}
                    }
                }
                final Component result = handler.capture(a.getOrDefault("template", ""), a.getOrDefault("scene", ""), origin);
                SlateToasts.show(Component.translatable("slate.stage.capture.title"), result, dev.fallingcloud.slate.core.gfx.Icon.INFO);
                if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(result, false);
            }));
        try {
            StageCommands.get().register(handler);
        } catch (final Exception e) {
            Slate.LOGGER.warn("[Slate] stage: client command not registered: {}", e.toString());
        }
    }

    private StageBootstrap() {}
}
