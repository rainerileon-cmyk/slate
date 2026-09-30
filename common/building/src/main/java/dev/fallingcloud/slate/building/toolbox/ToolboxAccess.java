package dev.fallingcloud.slate.building.toolbox;

import dev.fallingcloud.slate.building.compat.BetterInventoryBridge;
import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.config.ServerOps;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.Limits;
import dev.fallingcloud.slate.building.ops.ToolType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What a player's toolbox unlocks, on both sides (the client computes it from its synced inventory for the build
 * menu's lock icons; the server re-checks before running anything), plus the server-side hooks the ops economy uses:
 * tool wear, the pouch and the Supply Link container.
 *
 * <p>Which toolbox: BetterInventory's toolbox slot, then the offhand, the hotbar (left to right), the main inventory.
 * Creative players get everything at tier 4 while the server's {@code ops.creativeBypass} is on; servers with
 * {@code ops.requireToolbox = false} give every tool at tier 4 (upgrades still come from a carried toolbox).
 */
public final class ToolboxAccess {

    /** Slot id of BetterInventory's toolbox slot (payloads, {@link Located#slot()}). */
    public static final int SLOT_BETTER_INVENTORY = -1;
    /** {@code OpenToolbox} slot meaning "whichever toolbox {@link #find} picks". */
    public static final int SLOT_FIND = -2;

    /** Upgrade reach floor: each Reach level is worth at least this many blocks (tier 1 has no base bonus to double). */
    public static final int REACH_PER_LEVEL = 8;

    /**
     * Where a player's toolbox is.
     *
     * @param stack the live toolbox stack
     * @param slot  inventory index (0..35, 40 = offhand) or {@link #SLOT_BETTER_INVENTORY}
     */
    public record Located(ItemStack stack, int slot) {
        public boolean betterInventory() { return slot == SLOT_BETTER_INVENTORY; }
    }

    public static boolean isToolbox(final ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ToolboxItem;
    }

    /** The toolbox this player uses: BetterInventory toolbox slot → offhand → hotbar → main inventory; empty if none. */
    public static ItemStack find(final Player p) {
        final Located at = locate(p);
        return at == null ? ItemStack.EMPTY : at.stack();
    }

    /** {@link #find} with the toolbox's location; null without a toolbox. */
    public static @Nullable Located locate(final Player p) {
        final ItemStack bi = BetterInventoryBridge.toolbox(p);
        if (isToolbox(bi)) return new Located(bi, SLOT_BETTER_INVENTORY);
        final Inventory inv = p.getInventory();
        final ItemStack off = inv.offhand.get(0);
        if (isToolbox(off)) return new Located(off, Inventory.SLOT_OFFHAND);
        for (int i = 0; i < inv.items.size(); i++) {
            final ItemStack s = inv.items.get(i);
            if (isToolbox(s)) return new Located(s, i);
        }
        return null;
    }

    /** The stack at a {@link Located#slot()} id (empty for unknown ids). */
    public static ItemStack stackAt(final Player p, final int slot) {
        if (slot == SLOT_BETTER_INVENTORY) return BetterInventoryBridge.toolbox(p);
        final Inventory inv = p.getInventory();
        if (slot == Inventory.SLOT_OFFHAND) return inv.offhand.get(0);
        return slot >= 0 && slot < inv.items.size() ? inv.items.get(slot) : ItemStack.EMPTY;
    }

    /** A live view of the located toolbox's slots; writes to a BetterInventory-held toolbox are synced by BetterInventory. */
    public static ToolboxInventory inventory(final Player p, final Located at) {
        return new ToolboxInventory(at.stack(), at.betterInventory() ? () -> BetterInventoryBridge.markChanged(p, at.stack()) : null);
    }

    /** Tool tiers and upgrades available to {@code p} right now, under the rules of {@code p}'s side. */
    public static Capabilities of(final Player p) {
        final ServerOps ops = BuildingServerSettings.effective(p).ops();
        if (p.isCreative() && ops.creativeBypass) return Capabilities.everything(true);
        final Located at = locate(p);
        final List<ItemStack> items = at == null ? List.of() : ToolboxContents.read(at.stack());
        final Capabilities own = Capabilities.of(items);
        if (!ops.requireToolbox) return new Capabilities(Capabilities.everything(false).tiers(), own.upgrades(), false);
        return own;
    }

    /** What one toolbox stack unlocks by itself (no creative bypass, no server exceptions). */
    public static Capabilities of(final ItemStack toolbox) {
        return isToolbox(toolbox) ? Capabilities.of(ToolboxContents.read(toolbox)) : Capabilities.NONE;
    }

    /**
     * What a player has.
     *
     * @param tiers    highest tier (1..4) per tool type present; absent = not owned
     * @param upgrades count per upgrade type installed (capped at {@link UpgradeType#maxLevel()}); absent = none
     * @param creative creative bypass (no cost, creative limits)
     */
    public record Capabilities(Map<ToolType, Integer> tiers, Map<UpgradeType, Integer> upgrades, boolean creative) {

        public static final Capabilities NONE = new Capabilities(Map.of(), Map.of(), false);

        public Capabilities {
            tiers = Map.copyOf(tiers);
            upgrades = Map.copyOf(upgrades);
        }

        /** Every tool at tier 4, no upgrades. */
        public static Capabilities everything(final boolean creative) {
            final Map<ToolType, Integer> all = new EnumMap<>(ToolType.class);
            for (final ToolType t : ToolType.values()) all.put(t, ToolTier.MAX);
            return new Capabilities(all, Map.of(), creative);
        }

        /** From a toolbox's slot list ({@link ToolboxContents} layout), e.g. the open toolbox menu's slots. */
        public static Capabilities of(final List<ItemStack> items) {
            if (items.isEmpty()) return NONE;
            return new Capabilities(ToolboxContents.tiers(items), ToolboxContents.upgrades(items), false);
        }

        /** Tier of tool {@code t}, 0 when not owned. */
        public int tier(final ToolType t) {
            return tiers.getOrDefault(t, 0);
        }

        /** Installed count of upgrade {@code u}, 0 when none. */
        public int upgrade(final UpgradeType u) {
            return upgrades.getOrDefault(u, 0);
        }

        /** Number of tool types owned. */
        public int toolCount() {
            return tiers.size();
        }

        /** Whether mode {@code m} is unlocked (tool-less modes always are). Server-disabled modes are checked by ops. */
        public boolean unlocked(final BuildMode m) {
            return m.tool() == null || tier(m.tool()) >= m.minTier();
        }

        /**
         * Why {@code m} is locked, or null when it is unlocked: "Needs: Iron Hammer", or "Needs: Iron Hammer (yours is
         * Copper)" when a lower tier of that tool is in the toolbox.
         */
        public @Nullable Component lockReason(final BuildMode m) {
            if (unlocked(m)) return null;
            final ToolType tool = m.tool();
            final Component needed = Component.translatable("slate_building.tool_tiered", ToolTier.byLevel(m.minTier()).displayName(), tool.displayName());
            final int have = tier(tool);
            if (have > 0) return Component.translatable("slate_building.lock.needs_tool_have", needed, ToolTier.byLevel(have).displayName());
            return Component.translatable("slate_building.lock.needs_tool", needed);
        }

        /**
         * Limits for operations under server rules {@code s}: creative gets {@code creativeMaxVolume}, others the
         * per-tier values of their best tool, raised by upgrades - Capacity doubles the volume (never past
         * {@code creativeMaxVolume}) and adds half the span per level, Reach doubles the reach bonus (at least
         * {@link #REACH_PER_LEVEL} blocks per level), Speed doubles blocks per tick; Memory adds
         * {@code undoPerMemory} undo steps per level.
         */
        public Limits limits(final BuildingServerSettings s) {
            final ServerOps ops = s.ops();
            int best = 0;
            for (final int t : tiers.values()) best = Math.max(best, t);
            if (best == 0 && !creative) return Limits.NONE;
            final int tier = Math.max(ToolTier.MIN, best);
            final int undo = ops.undoDepth + ops.undoPerMemory * upgrade(UpgradeType.MEMORY);
            if (creative) {
                return new Limits(ops.creativeMaxVolume, ToolTier.index(ops.maxSpan, ToolTier.MAX), ToolTier.index(ops.reachBonus, ToolTier.MAX),
                    ToolTier.index(ops.blocksPerTick, ToolTier.MAX), undo);
            }
            final int capacity = upgrade(UpgradeType.CAPACITY), reach = upgrade(UpgradeType.REACH), speed = upgrade(UpgradeType.SPEED);
            final int baseVolume = ToolTier.index(ops.maxVolume, tier);
            final long volume = Math.min((long) baseVolume << capacity, Math.max(baseVolume, ops.creativeMaxVolume));
            final int span = ToolTier.index(ops.maxSpan, tier) * (2 + capacity) / 2;
            final int reachBonus = Math.max(ToolTier.index(ops.reachBonus, tier) << reach, REACH_PER_LEVEL * reach);
            final int blocksPerTick = ToolTier.index(ops.blocksPerTick, tier) << speed;
            return new Limits((int) volume, span, reachBonus, blocksPerTick, undo);
        }
    }

    // ------------------------------------------------------------------ server: wear, pouch, supply link

    /**
     * Wears the player's tool of type {@code t} for {@code blocks} changed blocks: one point per
     * {@code ops.durabilityPerBlocks} blocks (twice as many with Efficiency), the remainder rounded at random so small
     * operations wear tools at the same average rate. Unbreaking applies; a tool that breaks leaves the toolbox with the
     * vanilla break sound and a message. No wear in creative.
     */
    public static void damageTool(final ServerPlayer p, final ToolType t, final int blocks) {
        if (blocks <= 0 || p.getAbilities().instabuild) return;
        final Located at = locate(p);
        if (at == null) return;
        final ToolboxInventory inv = inventory(p, at);
        final int slot = ToolboxContents.toolSlot(t);
        final ItemStack tool = inv.getItem(slot);
        if (!(tool.getItem() instanceof BuildingToolItem item) || item.type() != t) return;

        final BuildingServerSettings settings = BuildingServerSettings.effective(p);
        int per = Math.max(1, settings.ops().durabilityPerBlocks);
        if (ToolboxContents.installed(inv.items(), UpgradeType.EFFICIENCY, -1) > 0) per *= 2;
        int points = blocks / per;
        final int rest = blocks % per;
        if (rest > 0 && p.getRandom().nextInt(per) < rest) points++;
        if (points <= 0) return;

        final ItemStack work = tool.copy();
        applyConfiguredDurability(work, item.tier(), settings);
        final boolean[] broke = {false};
        work.hurtAndBreak(points, p.serverLevel(), p, gone -> broke[0] = true);
        inv.setItem(slot, work.isEmpty() ? ItemStack.EMPTY : work);
        if (broke[0]) {
            p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8f, 0.9f + p.getRandom().nextFloat() * 0.2f);
            p.displayClientMessage(Component.translatable("slate_building.toolbox.tool_broke", tool.getHoverName()).withStyle(ChatFormatting.RED), true);
        }
    }

    /** Sets the server's configured durability ({@code toolbox.durability}) on a tool stack when it differs from the default. */
    public static void applyConfiguredDurability(final ItemStack tool, final ToolTier tier, final BuildingServerSettings settings) {
        final int configured = ToolTier.index(settings.toolbox().durability, tier.level());
        if (configured > 0 && tool.getMaxDamage() != configured) tool.set(DataComponents.MAX_DAMAGE, configured);
    }

    /**
     * The toolbox pouch slots, first source of materials for operations; empty without a toolbox or when the server
     * disabled the pouch ({@code toolbox.allowPouch}). The refs write straight to the toolbox stack; in-place changes to
     * a returned stack are flushed at the end of the tick at the latest.
     */
    public static List<SlotRef> pouch(final ServerPlayer p) {
        if (!BuildingServerSettings.effective(p).toolbox().allowPouch) return List.of();
        final Located at = locate(p);
        if (at == null) return List.of();
        final ToolboxInventory inv = inventory(p, at).flushAtTickEnd();
        final List<SlotRef> refs = new ArrayList<>(ToolboxContents.POUCH);
        for (int i = 0; i < ToolboxContents.POUCH; i++) refs.add(new SlotRef(inv, ToolboxContents.FIRST_POUCH + i));
        return List.copyOf(refs);
    }

    /**
     * The container linked by a Supply Link upgrade, when the toolbox has the upgrade and a link, and the container is
     * in the player's dimension, within {@code toolbox.supplyLinkRange} blocks, in a loaded chunk and still a container.
     */
    public static @Nullable LinkedContainer supplyLink(final ServerPlayer p) {
        final Located at = locate(p);
        if (at == null) return null;
        if (ToolboxContents.installed(ToolboxContents.read(at.stack()), UpgradeType.SUPPLY_LINK, -1) == 0) return null;
        final SupplyLink link = SupplyLink.of(at.stack());
        if (link == null) return null;
        final ServerLevel level = p.serverLevel();
        if (!link.pos().dimension().equals(level.dimension())) return null;
        final BlockPos pos = link.pos().pos();
        final double range = Math.max(0, BuildingServerSettings.effective(p).toolbox().supplyLinkRange);
        // The player's own measure, which Sable has follow a block that is on a sub-level.
        if (p.distanceToSqr(Vec3.atCenterOf(pos)) > range * range) return null;
        if (!level.isLoaded(pos)) return null;
        final Container container = linkableContainer(level, pos);
        return container == null ? null : new LinkedContainer(level.dimension(), pos, container);
    }

    /**
     * The container at {@code pos} a Supply Link may use: storage block entities (chests - both halves of a double
     * chest -, barrels, shulker boxes, hoppers, dispensers, modded chests), not furnaces, stands, jukeboxes or shelves.
     */
    public static @Nullable Container linkableContainer(final Level level, final BlockPos pos) {
        final BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof Container container)) return null;
        if (!(be instanceof RandomizableContainerBlockEntity) && container.getContainerSize() < 9) return null;
        final BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) {
            final Container both = ChestBlock.getContainer(chest, state, level, pos, true);
            if (both != null) return both;
        }
        return container;
    }

    private ToolboxAccess() {}
}
