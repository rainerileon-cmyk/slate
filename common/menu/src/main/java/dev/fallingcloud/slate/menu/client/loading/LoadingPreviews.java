package dev.fallingcloud.slate.menu.client.loading;

import dev.fallingcloud.slate.core.client.CoreActions;
import dev.fallingcloud.slate.menu.mixin.ConnectScreenAccessor;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Dev harness only ({@code slate.autoScreens}, never in a normal launch): the loading screens with made-up progress,
 * so a screenshot run can capture them ({@code slate_menu:loading_world}, {@code _terrain}, {@code _message},
 * {@code _progress}, {@code _connect}).
 */
public final class LoadingPreviews {

    public static void register() {
        CoreActions.SCREEN_FACTORIES.put("slate_menu:loading_world", p -> new LevelLoadingScreen(halfGenerated()));
        CoreActions.SCREEN_FACTORIES.put("slate_menu:loading_terrain", p -> new ReceivingLevelScreen(() -> false, ReceivingLevelScreen.Reason.OTHER));
        CoreActions.SCREEN_FACTORIES.put("slate_menu:loading_message", p -> new GenericMessageScreen(Component.translatable("menu.savingLevel")));
        CoreActions.SCREEN_FACTORIES.put("slate_menu:loading_progress", p -> {
            final ProgressScreen s = new ProgressScreen(false);
            s.progressStartNoAbort(Component.translatable("menu.generatingLevel"));
            s.progressStage(Component.translatable("menu.generatingTerrain"));
            s.progressStagePercentage(42);
            return s;
        });
        CoreActions.SCREEN_FACTORIES.put("slate_menu:loading_connect", p -> ConnectScreenAccessor.slate$create(p, Component.translatable("connect.failed")));
    }

    /** A spawn area part way through: full chunks at the centre, earlier stages further out. */
    private static StoringChunkProgressListener halfGenerated() {
        final StoringChunkProgressListener l = StoringChunkProgressListener.createFromGameruleRadius(3);
        l.updateSpawnPos(ChunkPos.ZERO);
        l.start();
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                final int d = Math.max(Math.abs(x), Math.abs(z));
                final ChunkStatus s = d <= 2 ? ChunkStatus.FULL : d <= 4 ? ((x + z) % 2 == 0 ? ChunkStatus.FULL : ChunkStatus.FEATURES)
                    : d <= 6 ? ChunkStatus.NOISE : d <= 9 ? ChunkStatus.BIOMES : ChunkStatus.EMPTY;
                l.onStatusChange(new ChunkPos(x, z), s);
            }
        }
        return l;
    }

    private LoadingPreviews() {}
}
