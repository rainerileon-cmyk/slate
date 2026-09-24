package dev.fallingcloud.slate.building.client.wheel;

import dev.fallingcloud.slate.building.ops.ToolType;
import dev.fallingcloud.slate.building.toolbox.BuildingToolItem;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.building.variant.Shape;
import dev.fallingcloud.slate.building.variant.Variant;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * What a wheel acts on: the held stack (swap its shape / chisel its material, count kept) or, with a hammer and an
 * empty hand or a building tool, the variant block under the crosshair (reshape / chisel it in the world).
 *
 * @param source HELD or WORLD
 * @param material the material (always the full block)
 * @param shape    the current shape
 * @param count    held stack size (1 for a block in the world)
 * @param slot     inventory slot of the held stack (the selected hotbar slot), -1 for WORLD
 * @param pos      the targeted block for WORLD, else null
 */
public record WheelTarget(Source source, Block material, Shape shape, int count, int slot, @Nullable BlockPos pos) {

    public enum Source { HELD, WORLD }

    public Variant variant() {
        return new Variant(material, shape);
    }

    public boolean held() {
        return source == Source.HELD;
    }

    /** "Oak Stairs"-style name of the current variant. */
    public Component displayName() {
        return WheelPages.variantName(material, shape);
    }

    /** The held-stack target, or null when the main hand is not a variant. */
    public static @Nullable WheelTarget heldTarget() {
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        if (player == null) return null;
        final ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) return null;
        final Optional<Variant> v = WheelSources.get().identify(held);
        return v.map(variant -> new WheelTarget(Source.HELD, variant.material(), variant.shape(), held.getCount(),
            player.getInventory().selected, null)).orElse(null);
    }

    /**
     * The in-world reshape target: empty hand or a building tool, crosshair on a variant block, and a hammer in the
     * toolbox (creative bypass counts). Null otherwise.
     */
    public static @Nullable WheelTarget worldTarget() {
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return null;
        final ItemStack held = player.getMainHandItem();
        if (!held.isEmpty() && !(held.getItem() instanceof BuildingToolItem)) return null;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        if (ToolboxAccess.of(player).tier(ToolType.HAMMER) <= 0) return null;
        final BlockPos pos = hit.getBlockPos();
        final Optional<Variant> v = WheelSources.get().identify(mc.level, pos);
        return v.map(variant -> new WheelTarget(Source.WORLD, variant.material(), variant.shape(), 1, -1, pos.immutable())).orElse(null);
    }

    /** What the swap wheel would act on right now: the held stack first, else the looked-at block. */
    public static @Nullable WheelTarget resolve() {
        if (!WheelConfig.wheel().swapEnabled) return null;
        final WheelTarget held = heldTarget();
        return held != null ? held : worldTarget();
    }
}
