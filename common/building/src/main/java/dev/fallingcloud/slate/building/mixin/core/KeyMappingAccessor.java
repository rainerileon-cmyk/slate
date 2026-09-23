package dev.fallingcloud.slate.building.mixin.core;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The bound key and click counter of a {@link KeyMapping} (private in vanilla; NeoForge adds a public getter but
 * Fabric does not), for {@code ExclusiveKeys}.
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

    @Accessor("key")
    InputConstants.Key slateBuilding$key();

    @Accessor("clickCount")
    int slateBuilding$clickCount();

    @Accessor("clickCount")
    void slateBuilding$setClickCount(int count);
}
