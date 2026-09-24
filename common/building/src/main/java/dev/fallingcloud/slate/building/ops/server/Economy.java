package dev.fallingcloud.slate.building.ops.server;

import dev.fallingcloud.slate.building.toolbox.LinkedContainer;
import dev.fallingcloud.slate.building.toolbox.SlotRef;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.building.toolbox.ToolboxItem;
import dev.fallingcloud.slate.building.toolbox.UpgradeType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The material account of one operation (design §1). Charges come from the op's own pool first (what it already
 * refunded or lifted: a move pays its destination with its source), then from the player: toolbox pouch → inventory
 * (the paying hotbar slot, the rest of the hotbar, the main inventory, the off hand; never the toolbox) → the Supply
 * Link container. Refunds and drops collect in the pool and are handed out by {@link #settle}: pouch / linked
 * container first with the Magnet upgrade, then the inventory, the rest dropped at the player's feet.
 *
 * <p>A free account (creative) charges and pays nothing.
 */
public final class Economy {

    /** A number of units of one key. */
    public record Cost(CostKey key, int units) {}

    private final ServerPlayer player;
    private final boolean free;
    private final int paySlot;
    private final Map<CostKey, Integer> pool = new LinkedHashMap<>();

    public Economy(final ServerPlayer player, final boolean free, final int paySlot) {
        this.player = player;
        this.free = free;
        this.paySlot = paySlot;
    }

    public boolean isFree() {
        return free;
    }

    /** Units of {@code key} this account can pay right now (pool + every source). */
    public int available(final CostKey key) {
        if (free) return Integer.MAX_VALUE;
        int n = pool.getOrDefault(key, 0);
        for (final SlotRef ref : sources()) {
            final ItemStack s = ref.get();
            if (key.matches(s)) n += s.getCount();
        }
        return n;
    }

    /**
     * Takes {@code units} of {@code key}, preferring stacks equal to {@code prefer} (the held item keeps its shape
     * over other shapes of the material). All or nothing: false (and nothing taken) when the player cannot pay.
     */
    public boolean charge(final CostKey key, final int units, final @Nullable ItemStack prefer) {
        if (free || units <= 0) return true;
        final int inPool = pool.getOrDefault(key, 0);
        if (inPool >= units) {
            put(key, inPool - units);
            return true;
        }
        final List<SlotRef> sources = sources();
        int have = inPool;
        for (final SlotRef ref : sources) {
            final ItemStack s = ref.get();
            if (key.matches(s)) have += s.getCount();
            if (have >= units) break;
        }
        if (have < units) return false;
        put(key, 0);
        int need = units - inPool;
        if (prefer != null && !prefer.isEmpty()) need = take(sources, key, need, prefer);
        take(sources, key, need, null);
        return true;
    }

    /** Charges every cost, all or nothing (undoing a break charges back each kind of drop it gave). */
    public boolean chargeAll(final List<Cost> costs) {
        if (free || costs.isEmpty()) return true;
        final Map<CostKey, Integer> total = new LinkedHashMap<>();
        for (final Cost c : costs) total.merge(c.key(), c.units(), Integer::sum);
        for (final Map.Entry<CostKey, Integer> e : total.entrySet()) if (available(e.getKey()) < e.getValue()) return false;
        for (final Map.Entry<CostKey, Integer> e : total.entrySet()) charge(e.getKey(), e.getValue(), null);
        return true;
    }

    /** Credits {@code units} of {@code key} to the pool (paid out by {@link #settle}, or spent by later charges). */
    public void refund(final CostKey key, final int units) {
        if (free || units <= 0) return;
        pool.merge(key, units, Integer::sum);
    }

    public void refundAll(final List<Cost> costs) {
        for (final Cost c : costs) refund(c.key(), c.units());
    }

    /** Hands everything in the pool to the player (Magnet → pouch / linked container, inventory, feet). */
    public void settle() {
        if (free || pool.isEmpty()) return;
        final boolean magnet = ToolboxAccess.of(player).upgrade(UpgradeType.MAGNET) > 0;
        final List<SlotRef> magnetTargets = magnet ? magnetTargets() : List.of();
        for (final Map.Entry<CostKey, Integer> e : pool.entrySet()) {
            int left = e.getValue();
            while (left > 0) {
                final ItemStack proto = e.getKey().stack(1);
                if (proto.isEmpty()) break;
                final int n = Math.min(left, proto.getMaxStackSize());
                left -= n;
                give(e.getKey().stack(n), magnetTargets);
            }
        }
        pool.clear();
    }

    private void give(final ItemStack stack, final List<SlotRef> magnetTargets) {
        if (stack.getItem() instanceof BlockItem) insert(magnetTargets, stack);
        if (!stack.isEmpty()) player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            final ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack);
            drop.setDeltaMovement(0, 0, 0);
            drop.setNoPickUpDelay();
            player.level().addFreshEntity(drop);
        }
    }

    /** Merges {@code stack} into matching stacks of {@code slots}, then empty ones; shrinks it by what fit. */
    private static void insert(final List<SlotRef> slots, final ItemStack stack) {
        for (final SlotRef ref : slots) {
            if (stack.isEmpty()) return;
            final ItemStack s = ref.get();
            if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, stack)) continue;
            final int room = Math.min(s.getMaxStackSize(), ref.container().getMaxStackSize()) - s.getCount();
            if (room <= 0) continue;
            final int n = Math.min(room, stack.getCount());
            final ItemStack grown = s.copyWithCount(s.getCount() + n);
            ref.set(grown);
            stack.shrink(n);
        }
        for (final SlotRef ref : slots) {
            if (stack.isEmpty()) return;
            if (!ref.get().isEmpty() || !ref.container().canPlaceItem(ref.slot(), stack)) continue;
            final int n = Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), ref.container().getMaxStackSize()));
            ref.set(stack.copyWithCount(n));
            stack.shrink(n);
        }
    }

    /** Takes up to {@code need} matching items (only those equal to {@code prefer} when given); returns what is still needed. */
    private static int take(final List<SlotRef> sources, final CostKey key, int need, final @Nullable ItemStack prefer) {
        for (final SlotRef ref : sources) {
            if (need <= 0) break;
            final ItemStack s = ref.get();
            if (!key.matches(s) || (prefer != null && !ItemStack.isSameItemSameComponents(s, prefer))) continue;
            final int n = Math.min(need, s.getCount());
            final ItemStack rest = s.copyWithCount(s.getCount() - n);
            ref.set(rest.isEmpty() ? ItemStack.EMPTY : rest);
            need -= n;
        }
        return need;
    }

    private void put(final CostKey key, final int units) {
        if (units <= 0) pool.remove(key);
        else pool.put(key, units);
    }

    /** Where materials come from, in order. */
    private List<SlotRef> sources() {
        final List<SlotRef> out = new ArrayList<>(ToolboxAccess.pouch(player));
        final Inventory inv = player.getInventory();
        if (Inventory.isHotbarSlot(paySlot)) out.add(new SlotRef(inv, paySlot));
        for (int i = 0; i < inv.items.size(); i++) {
            if (i == paySlot) continue;
            out.add(new SlotRef(inv, i));
        }
        out.add(new SlotRef(inv, Inventory.SLOT_OFFHAND));
        final LinkedContainer link = ToolboxAccess.supplyLink(player);
        if (link != null) out.addAll(link.slots());
        out.removeIf(ref -> ref.get().getItem() instanceof ToolboxItem);
        return out;
    }

    private List<SlotRef> magnetTargets() {
        final List<SlotRef> out = new ArrayList<>(ToolboxAccess.pouch(player));
        final LinkedContainer link = ToolboxAccess.supplyLink(player);
        if (link != null) out.addAll(link.slots());
        return out;
    }
}
