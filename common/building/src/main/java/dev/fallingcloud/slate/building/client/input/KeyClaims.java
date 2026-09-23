package dev.fallingcloud.slate.building.client.input;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.client.mode.ClientModeState;
import dev.fallingcloud.slate.building.config.WheelSettings;
import dev.fallingcloud.slate.building.ops.ToolType;
import dev.fallingcloud.slate.building.toolbox.BuildingToolItem;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.building.toolbox.ToolboxItem;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * When Slate Building takes its shared default keys for itself (design §4), through {@link ExclusiveKeys}:
 * <ul>
 *   <li>{@link BuildKeys#SWAP} (Left Alt) while the wheel would open: the main-hand stack identifies as a variant,
 *       or the hand is empty / holds a building tool, the crosshair is on a variant block and the player has a
 *       hammer (in-world reshape wheel). Off when {@code wheel.swapEnabled} or {@code wheel.exclusiveSwapKey} is off.</li>
 *   <li>{@link BuildKeys#BUILD_MENU} (R): {@code wheel.menuKeyContext = ALWAYS}, or ({@code SMART}) while holding a
 *       block, a variant, the toolbox or a building tool, or while a building mode is active.</li>
 * </ul>
 * Everything else pressed on those keys behaves exactly as without Slate Building. The wheel owner (C) may replace
 * a claim with {@code ExclusiveKeys.claim(BuildKeys.SWAP, ...)} if the wheel's open rules change; keep them equal.
 */
public final class KeyClaims {

    public static void init() {
        ExclusiveKeys.claim(BuildKeys.SWAP, KeyClaims::swapWanted);
        ExclusiveKeys.claim(BuildKeys.BUILD_MENU, KeyClaims::menuWanted);
    }

    /** Whether pressing the swap key right now would open a wheel. */
    public static boolean swapWanted() {
        final WheelSettings wheel = SlateBuilding.config().wheel;
        if (!wheel.swapEnabled || !wheel.exclusiveSwapKey) return false;
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return false;
        final ItemStack held = player.getMainHandItem();
        if (!held.isEmpty() && VariantRegistry.get().identify(held).isPresent()) return true;
        if (!held.isEmpty() && !(held.getItem() instanceof BuildingToolItem)) return false;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return false;
        return VariantRegistry.get().identify(mc.level, hit.getBlockPos()).isPresent()
            && ToolboxAccess.of(player).tier(ToolType.HAMMER) > 0;
    }

    /** Whether pressing the build-menu key right now should open the build menu. */
    public static boolean menuWanted() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if ("ALWAYS".equalsIgnoreCase(SlateBuilding.config().wheel.menuKeyContext)) return true;
        if (ClientModeState.isActive()) return true;
        final ItemStack held = mc.player.getMainHandItem();
        return held.getItem() instanceof BlockItem
            || held.getItem() instanceof ToolboxItem
            || held.getItem() instanceof BuildingToolItem
            || (!held.isEmpty() && VariantRegistry.get().identify(held).isPresent());
    }

    private KeyClaims() {}
}
