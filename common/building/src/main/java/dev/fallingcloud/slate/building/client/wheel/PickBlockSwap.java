package dev.fallingcloud.slate.building.client.wheel;

import dev.fallingcloud.slate.building.client.input.BuildInput;
import dev.fallingcloud.slate.building.net.SwapHeld;
import dev.fallingcloud.slate.building.ops.ToolType;
import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.building.variant.Variant;
import dev.fallingcloud.slate.core.net.SlateNetwork;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Survival pick-block for variants (design §5, {@code wheel.pickBlockSwaps}): middle-clicking a variant selects the
 * hotbar slot that holds the same MATERIAL in any shape, preferring one already in the picked shape; when the shape
 * differs it asks the server to swap that stack to the picked shape ({@code SwapHeld}, count kept). Without a
 * matching hotbar stack (or in creative, where vanilla hands out the variant itself) vanilla's pick runs as usual.
 * {@code BuildInput} priority 10.
 */
public final class PickBlockSwap implements BuildInput.Handler {

    public static final PickBlockSwap INSTANCE = new PickBlockSwap();

    private PickBlockSwap() {}

    public static void init() {
        BuildInput.register(INSTANCE);
    }

    @Override
    public int priority() { return 10; }

    @Override
    public boolean onPickBlock() {
        if (!WheelConfig.wheel().pickBlockSwaps) return false;
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.getAbilities().instabuild) return false;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return false;
        final Optional<Variant> picked = WheelSources.get().identify(mc.level, hit.getBlockPos());
        if (picked.isEmpty()) return false;
        final Variant want = picked.get();
        final Inventory inv = player.getInventory();

        int exact = -1, sameMaterial = -1;
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            final ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            final Optional<Variant> v = WheelSources.get().identify(stack);
            if (v.isEmpty() || v.get().material() != want.material()) continue;
            if (v.get().shape() == want.shape()) {
                if (exact < 0 || i == inv.selected) exact = i;
            } else if (sameMaterial < 0 || i == inv.selected) {
                sameMaterial = i;
            }
        }
        if (exact >= 0) {
            inv.selected = exact;
            return true;
        }
        if (sameMaterial < 0) return false;
        inv.selected = sameMaterial;
        if (canSwap(player, want)) SlateNetwork.get().sendToServer(new SwapHeld(sameMaterial, want.shape().id()));
        return true;
    }

    private static boolean canSwap(final LocalPlayer player, final Variant want) {
        if (!WheelSources.get().isAvailable(want.material(), want.shape())) return false;
        if (!BuildingServerSettings.effective(player).variants().swapNeedsTool) return true;
        return ToolboxAccess.of(player).tier(ToolType.HAMMER) > 0;
    }
}
