package dev.fallingcloud.slate.building.mixin.render;

import dev.fallingcloud.slate.building.client.render.WorldChanges;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reports client world changes to {@link WorldChanges}, so the building-mode preview re-plans when the blocks around
 * its selection change (and only then). Every client block change that needs a re-render passes through
 * {@code sendBlockUpdated} (server updates, the player's own predicted placements, a shape block's material
 * arriving); whole chunks arrive through {@code onChunkLoaded} and leave through {@code unload}.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelChangesMixin {

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void slateBuilding$blockChanged(final BlockPos pos, final BlockState oldState, final BlockState newState, final int flags,
                                            final CallbackInfo ci) {
        WorldChanges.blockChanged(pos);
    }

    @Inject(method = "onChunkLoaded", at = @At("HEAD"))
    private void slateBuilding$chunkLoaded(final ChunkPos pos, final CallbackInfo ci) {
        WorldChanges.chunkChanged(pos.x, pos.z);
    }

    @Inject(method = "unload(Lnet/minecraft/world/level/chunk/LevelChunk;)V", at = @At("HEAD"))
    private void slateBuilding$chunkUnloaded(final LevelChunk chunk, final CallbackInfo ci) {
        WorldChanges.chunkChanged(chunk.getPos().x, chunk.getPos().z);
    }
}
