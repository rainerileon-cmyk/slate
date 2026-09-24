package dev.fallingcloud.slate.building.ops.server;

import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.config.ServerOps;
import dev.fallingcloud.slate.building.net.ReachState;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.ops.OpMessages;
import dev.fallingcloud.slate.building.platform.BuildingPlatform;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.core.Slate;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

/**
 * The Extended mode (kind REACH): normal placing and breaking, as far away as the building modes pick their corners.
 * While it is on, the player's {@code block_interaction_range} carries a transient modifier worth the toolbox reach
 * bonus ({@code Limits.reachBonus}); vanilla's own raycast, placement and mining do the rest, on both sides (the
 * attribute is synced), so every block and every other mod's interaction works exactly as it does up close.
 *
 * <p>Transient means never written to the player's NBT: a crash cannot leave anyone with a long arm. The mode is
 * re-checked once a second ({@link #tick}: rules, game mode, toolbox), the bonus follows toolbox changes, a respawned
 * player (a new entity, without the modifier) gets it back, and the client hears every change as a {@link ReachState}.
 */
public final class Reach {

    /** The modifier on {@code minecraft:player.block_interaction_range}. */
    public static final ResourceLocation MODIFIER_ID = Slate.id("building/extended_reach");
    private static final int RECHECK_TICKS = 20;

    private Reach() {}

    /** {@code SetReach}: on (refused with a reason the client shows) or off; always answered with a {@link ReachState}. */
    public static void set(final ServerPlayer player, final boolean on) {
        final OpsServer.Session s = OpsServer.session(player);
        if (!on) {
            off(player, s);
            return;
        }
        final Component refused = refusal(player);
        if (refused != null) {
            OpsServer.send(player, OpMessages.error(0, BuildModes.EXTENDED.id(), refused));
            off(player, s);
            return;
        }
        s.reachOn = true;
        s.reachBonus = apply(player);
        OpsServer.send(player, new ReachState(s.reachBonus));
    }

    /** Why {@code player} may not use the extended reach right now (server switch, disabled mode, game mode, no reach bonus). */
    private static @Nullable Component refusal(final ServerPlayer player) {
        final ServerOps rules = BuildingServerSettings.local().ops();
        if (!rules.enabled) return Component.translatable("slate_building.plan.disabled");
        if (rules.disabledModes != null && rules.disabledModes.contains(BuildModes.EXTENDED.id())) return Component.translatable("slate_building.plan.mode_disabled");
        if (player.isSpectator() || !player.mayBuild()) return Component.translatable("slate_building.error.game_mode");
        if (BuildingPlatform.get().isFakePlayer(player)) return Component.translatable("slate_building.error.game_mode");
        if (bonus(player) <= 0) return Component.translatable("slate_building.error.no_reach");
        return null;
    }

    /** The reach bonus of the player's toolbox under the local rules (creative: the top tier's). */
    private static int bonus(final ServerPlayer player) {
        return Math.max(0, ToolboxAccess.of(player).limits(BuildingServerSettings.local()).reachBonus());
    }

    /** Puts the modifier on the live player at the current bonus (replacing an older value) and returns it. */
    private static int apply(final ServerPlayer player) {
        final int bonus = bonus(player);
        final AttributeInstance range = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (range != null) range.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
        return bonus;
    }

    private static void off(final ServerPlayer player, final OpsServer.Session s) {
        s.reachOn = false;
        s.reachBonus = 0;
        final AttributeInstance range = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (range != null) range.removeModifier(MODIFIER_ID);
        OpsServer.send(player, new ReachState(0));
    }

    /**
     * Once a second, for every player with the mode on: the rules are checked again (off when they no longer allow it),
     * the modifier is (re)applied at the current bonus, and the client is told when the bonus changed.
     */
    public static void tick(final MinecraftServer server) {
        if (server.getTickCount() % RECHECK_TICKS != 0) return;
        for (final ServerPlayer p : server.getPlayerList().getPlayers()) {
            final OpsServer.Session s = OpsServer.existingSession(p);
            if (s == null || !s.reachOn) continue;
            if (refusal(p) != null) {
                off(p, s);
                continue;
            }
            final int bonus = apply(p);
            if (bonus != s.reachBonus) {
                s.reachBonus = bonus;
                OpsServer.send(p, new ReachState(bonus));
            }
        }
    }
}
