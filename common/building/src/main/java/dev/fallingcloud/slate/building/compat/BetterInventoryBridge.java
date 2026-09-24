package dev.fallingcloud.slate.building.compat;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * BetterInventory's dedicated toolbox slot, reached by reflection so Slate Building has no compile dependency on it
 * (and runs on Fabric, where BetterInventory does not exist). Binds once to
 * {@code dev.fallingcloud.betterinventory.api.BetterInventoryApi}:
 * <ul>
 *   <li>{@code static ItemStack toolbox(Player)} - the live stack in the slot (both sides; the client copy is synced);</li>
 *   <li>{@code static boolean setToolbox(Player, ItemStack)} - server side; re-setting the same stack after changing
 *       its components makes BetterInventory send its next loadout sync.</li>
 * </ul>
 * Any failure (mod absent, older BetterInventory without the API, a signature change) disables the bridge with one log
 * line; every call then falls back to "no toolbox slot".
 */
public final class BetterInventoryBridge {

    private static final String API = "dev.fallingcloud.betterinventory.api.BetterInventoryApi";

    private static volatile boolean resolved;
    private static @Nullable MethodHandle toolbox;
    private static @Nullable MethodHandle setToolbox;

    /** Whether BetterInventory with the toolbox API is installed. */
    public static boolean available() {
        resolve();
        return toolbox != null;
    }

    /** The toolbox in the player's BetterInventory toolbox slot, or {@link ItemStack#EMPTY}. */
    public static ItemStack toolbox(final Player player) {
        resolve();
        final MethodHandle h = toolbox;
        if (h == null || player == null) return ItemStack.EMPTY;
        try {
            final ItemStack stack = (ItemStack) h.invokeExact(player);
            return stack == null ? ItemStack.EMPTY : stack;
        } catch (final Throwable t) {
            disable("toolbox(Player)", t);
            return ItemStack.EMPTY;
        }
    }

    /**
     * Server side: tells BetterInventory the toolbox stack in its slot changed (its contents or a tool's damage), so the
     * owning client gets it in the next sync. No-op without the API or on the client.
     */
    public static void markChanged(final Player player, final ItemStack stack) {
        resolve();
        final MethodHandle h = setToolbox;
        if (h == null || player == null || player.level().isClientSide()) return;
        try {
            final boolean ok = (boolean) h.invokeExact(player, stack);
            if (!ok) SlateBuilding.LOGGER.debug("[Slate Building] BetterInventory refused the toolbox slot update");
        } catch (final Throwable t) {
            disable("setToolbox(Player, ItemStack)", t);
        }
    }

    private static void resolve() {
        if (resolved) return;
        synchronized (BetterInventoryBridge.class) {
            if (resolved) return;
            try {
                if (SlatePlatform.get().isModLoaded("betterinventory")) {
                    final Class<?> api = Class.forName(API);
                    final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                    toolbox = lookup.findStatic(api, "toolbox", MethodType.methodType(ItemStack.class, Player.class));
                    try {
                        setToolbox = lookup.findStatic(api, "setToolbox", MethodType.methodType(boolean.class, Player.class, ItemStack.class));
                    } catch (final ReflectiveOperationException e) {
                        SlateBuilding.LOGGER.info("[Slate Building] BetterInventory has no setToolbox; toolbox slot changes sync with its own schedule");
                    }
                    SlateBuilding.LOGGER.info("[Slate Building] BetterInventory toolbox slot bridged");
                }
            } catch (final ReflectiveOperationException | LinkageError e) {
                SlateBuilding.LOGGER.info("[Slate Building] BetterInventory is installed but has no toolbox API ({}); toolboxes are found in the inventory only", e.toString());
                toolbox = null;
                setToolbox = null;
            } finally {
                resolved = true;
            }
        }
    }

    private static void disable(final String what, final Throwable t) {
        SlateBuilding.LOGGER.warn("[Slate Building] BetterInventory {} failed, toolbox slot bridge disabled", what, t);
        toolbox = null;
        setToolbox = null;
    }

    private BetterInventoryBridge() {}
}
