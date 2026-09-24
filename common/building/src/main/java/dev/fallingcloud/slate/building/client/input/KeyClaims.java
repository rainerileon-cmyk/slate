package dev.fallingcloud.slate.building.client.input;

import dev.fallingcloud.slate.building.client.mode.ClientModeState;
import dev.fallingcloud.slate.building.client.wheel.WheelConfig;
import dev.fallingcloud.slate.building.client.wheel.WheelTarget;
import dev.fallingcloud.slate.building.toolbox.BuildingToolItem;
import dev.fallingcloud.slate.building.toolbox.ToolboxItem;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * When Slate Building takes its shared default keys for itself (design §4), through {@link ExclusiveKeys}:
 * <ul>
 *   <li>{@link BuildKeys#SWAP} (Left Alt) exactly while the wheel would open ({@link WheelTarget#resolve()}): the
 *       main-hand stack identifies as a variant, or the hand holds the Builder's Toolbox / a building tool, the
 *       crosshair is on a variant block and the player has a hammer (in-world reshape wheel). Never with an empty hand
 *       (Shoulder Surfing's free look, Relics and Create keep Alt there), never in spectator, with a screen open, or
 *       when {@code wheel.swapEnabled} or {@code wheel.exclusiveSwapKey} is off.</li>
 *   <li>{@link BuildKeys#BUILD_MENU} (R): {@code wheel.menuKeyContext = ALWAYS}, or ({@code SMART}) while holding a
 *       block, a variant, the toolbox or a building tool, or while a building mode is active. In spectator (no mode
 *       but Measure can run there) only while a mode is active, unless {@code ALWAYS}.</li>
 *   <li>{@link BuildKeys#CANCEL} (Q, vanilla's drop key) only while a building mode has a pending selection or a
 *       running operation to cancel; dropping items works as usual otherwise.</li>
 * </ul>
 * Everything else pressed on those keys behaves exactly as without Slate Building. The wheel overlay claims SWAP
 * with {@link #swapWanted()} itself, so both always agree.
 */
public final class KeyClaims {

    public static void init() {
        ExclusiveKeys.init();
        ExclusiveKeys.claim(BuildKeys.SWAP, KeyClaims::swapWanted);
        ExclusiveKeys.claim(BuildKeys.BUILD_MENU, KeyClaims::menuWanted);
        ExclusiveKeys.claim(BuildKeys.CANCEL, KeyClaims::cancelWanted);
    }

    /** Whether the cancel key (Q, vanilla's drop) has something to cancel: a pending selection or a running operation. */
    public static boolean cancelWanted() {
        return Minecraft.getInstance().screen == null && ClientModeState.isActive()
            && (ClientModeState.selectionPending() || ClientModeState.pending() == ClientModeState.Pending.APPLYING || ClientModeState.progress() != null);
    }

    /** Whether pressing the swap key right now would open a wheel (and exclusivity is wanted). */
    public static boolean swapWanted() {
        if (!WheelConfig.wheel().exclusiveSwapKey) return false;
        final Minecraft mc = Minecraft.getInstance();
        return mc.screen == null && WheelTarget.resolve() != null;   // resolve(): swapEnabled, not spectator, a target
    }

    /** Whether pressing the build-menu key right now should open the build menu. */
    public static boolean menuWanted() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if ("ALWAYS".equalsIgnoreCase(WheelConfig.wheel().menuKeyContext)) return true;
        if (ClientModeState.isActive()) return true;
        // A spectator keeps their inventory, but no building mode except Measure runs there: leave R to Iris & co.
        if (mc.player.isSpectator()) return false;
        final ItemStack held = mc.player.getMainHandItem();
        return held.getItem() instanceof BlockItem
            || held.getItem() instanceof ToolboxItem
            || held.getItem() instanceof BuildingToolItem
            || (!held.isEmpty() && VariantRegistry.get().identify(held).isPresent());
    }

    private KeyClaims() {}
}
