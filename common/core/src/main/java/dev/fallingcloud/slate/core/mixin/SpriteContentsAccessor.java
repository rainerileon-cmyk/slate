package dev.fallingcloud.slate.core.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The pixels a sprite was stitched from: {@code BetterInventoryPalette} repaints that mod's hotbar where it sits on the atlas. */
@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {

    @Accessor("originalImage")
    NativeImage slate$originalImage();
}
