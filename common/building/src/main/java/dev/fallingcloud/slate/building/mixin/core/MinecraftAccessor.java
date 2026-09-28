package dev.fallingcloud.slate.building.mixin.core;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The private use entry point, for {@code AccuratePlacement}. (It counts its own wait instead of reading vanilla's
 * right-click cooldown: other placement mods set that one too.)
 */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {

    @Invoker("startUseItem")
    void slateBuilding$startUseItem();
}
