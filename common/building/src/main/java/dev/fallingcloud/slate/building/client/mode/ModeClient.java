package dev.fallingcloud.slate.building.client.mode;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.client.input.BuildInput;
import dev.fallingcloud.slate.core.client.render.SlateRenderEvents;
import dev.fallingcloud.slate.core.event.SlateEvents;

/**
 * Client init of building modes (called from {@code BuildingClient.init()}): the selection state machine
 * ({@link ModeController}) as a {@link BuildInput} handler at priority 50, its tick (mode / undo / redo / confirm /
 * cancel / exit keybinds, robustness checks), its reactions to {@link ClientModeState} changes (symmetry sync,
 * results), the per-frame preview + overlay submission, and the dev harness scenarios {@code selection},
 * {@code mirror}, {@code paste} and {@code dimension} ({@link ModeHarness}) and {@code sable} ({@link SableHarness}).
 *
 * <p>The per-frame work runs as the FIRST listener of {@code SlateRenderEvents.AFTER_TRANSLUCENT}, so the ghosts and
 * overlays it submits are drawn by the renderers' own listener in the same frame (one raycast per frame; planning is
 * cached and throttled by {@link ModePreview}).
 */
public final class ModeClient {

    private static boolean initialised;

    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        final ModeController controller = ModeController.INSTANCE;
        BuildInput.register(controller);
        SlateEvents.CLIENT_TICK_END.register(controller::tick);
        ClientModeState.addListener(controller::onStateChange);
        final SlateRenderEvents.WorldRender frame = ctx -> controller.frame(ctx.partialTick());
        try {
            SlateRenderEvents.AFTER_TRANSLUCENT.listeners().add(0, frame);
        } catch (final UnsupportedOperationException e) {
            // A read-only listener list: still correct, the submissions are then drawn one frame later.
            SlateRenderEvents.AFTER_TRANSLUCENT.register(frame);
        }
        ModeHarness.register();
        SableHarness.register();
        SlateBuilding.LOGGER.debug("[Slate Building] building modes ready");
    }

    private ModeClient() {}
}
