package dev.fallingcloud.slate.core.stage;

import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.screen.ScreenIds;
import dev.fallingcloud.slate.core.stage.test.StageTestScreen;

/**
 * Client bootstrap of the stage toolkit: the test screen ids, the scene-capture action and command, and the hook
 * that closes stages when their screen goes away. Called once from {@code SlateClient.init()}.
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
    }

    private StageBootstrap() {}
}
