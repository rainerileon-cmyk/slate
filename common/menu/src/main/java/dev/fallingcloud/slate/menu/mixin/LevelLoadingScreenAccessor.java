package dev.fallingcloud.slate.menu.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla's colour per chunk generation stage, for Slate's own drawing of the spawn-area map. */
@Mixin(LevelLoadingScreen.class)
public interface LevelLoadingScreenAccessor {

    @Accessor("COLORS")
    static Object2IntMap<ChunkStatus> slate$colors() {
        throw new AssertionError();
    }
}
