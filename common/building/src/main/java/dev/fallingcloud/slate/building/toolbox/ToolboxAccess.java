package dev.fallingcloud.slate.building.toolbox;

import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.config.ServerOps;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.Limits;
import dev.fallingcloud.slate.building.ops.ToolType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * What a player's toolbox unlocks, on both sides (the client computes it from its synced inventory for the build
 * menu's lock icons; the server re-checks before running anything).
 *
 * <p>Owner: E (toolbox). Skeleton: the final API with trivial behaviour: creative players (with
 * {@code creativeBypass}) and servers with {@code requireToolbox = false} get every tool at tier 4, everyone else
 * gets nothing until E reads real toolboxes. {@link Capabilities} is implemented from the tier/upgrade maps.
 */
public final class ToolboxAccess {

    /** The toolbox this player uses: BetterInventory toolbox slot → offhand → hotbar → main inventory; empty if none. */
    public static ItemStack find(final Player p) {
        return ItemStack.EMPTY;
    }

    /** Tool tiers and upgrades available to {@code p} right now, under the rules of {@code p}'s side. */
    public static Capabilities of(final Player p) {
        final ServerOps ops = BuildingServerSettings.effective(p).ops();
        if (p.isCreative() && ops.creativeBypass) return Capabilities.everything(true);
        if (!ops.requireToolbox) return Capabilities.everything(false);
        return Capabilities.NONE;
    }

    /**
     * What a player has.
     *
     * @param tiers    highest tier (1..4) per tool type present; absent = not owned
     * @param upgrades count per upgrade type installed; absent = none
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

        /** Tier of tool {@code t}, 0 when not owned. */
        public int tier(final ToolType t) {
            return tiers.getOrDefault(t, 0);
        }

        /** Installed count of upgrade {@code u}, 0 when none. */
        public int upgrade(final UpgradeType u) {
            return upgrades.getOrDefault(u, 0);
        }

        /** Whether mode {@code m} is unlocked (tool-less modes always are). Server-disabled modes are checked by ops. */
        public boolean unlocked(final BuildMode m) {
            return m.tool() == null || tier(m.tool()) >= m.minTier();
        }

        /** Why {@code m} is locked ("Needs: Iron Hammer"), or null when it is unlocked. */
        public @Nullable Component lockReason(final BuildMode m) {
            if (unlocked(m)) return null;
            final ToolType tool = m.tool();
            return Component.translatable("slate_building.lock.needs_tool",
                Component.translatable("slate_building.tool_tiered", ToolTier.byLevel(m.minTier()).displayName(), tool.displayName()));
        }

        /**
         * Limits for operations under server rules {@code s}: creative gets {@code creativeMaxVolume}, others the
         * per-tier values of their best tool; Memory upgrades add undo depth.
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
            return new Limits(ToolTier.index(ops.maxVolume, tier), ToolTier.index(ops.maxSpan, tier), ToolTier.index(ops.reachBonus, tier),
                ToolTier.index(ops.blocksPerTick, tier), undo);
        }
    }

    /** Wears the player's tool of type {@code t} for {@code blocks} changed blocks ({@code durabilityPerBlocks}, Efficiency). */
    public static void damageTool(final ServerPlayer p, final ToolType t, final int blocks) {
    }

    /** The toolbox pouch slots, first source of materials for operations; empty without a toolbox / pouch. */
    public static List<SlotRef> pouch(final ServerPlayer p) {
        return List.of();
    }

    /** The container linked by a Supply Link upgrade, when present, loaded and in range. */
    public static @Nullable LinkedContainer supplyLink(final ServerPlayer p) {
        return null;
    }

    private ToolboxAccess() {}
}
