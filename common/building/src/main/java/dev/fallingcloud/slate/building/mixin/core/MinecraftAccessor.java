package dev.fallingcloud.slate.building.mixin.core;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The right-click cooldown and the private use entry point, for {@code AccuratePlacement}. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {

    @Accessor("rightClickDelay")
    int slateBuilding$rightClickDelay();

    @Invoker("startUseItem")
    void slateBuilding$startUseItem();
}
