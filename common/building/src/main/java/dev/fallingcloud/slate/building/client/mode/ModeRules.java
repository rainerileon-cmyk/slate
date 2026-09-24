package dev.fallingcloud.slate.building.client.mode;

import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.config.ServerOps;
import dev.fallingcloud.slate.building.net.ApplyOp;
import dev.fallingcloud.slate.building.net.SetReach;
import dev.fallingcloud.slate.building.net.SetSymmetry;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.ops.Limits;
import dev.fallingcloud.slate.building.ops.ModeKind;
import dev.fallingcloud.slate.building.ops.SymmetryMath;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.core.net.SlateNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * What the client may do with building modes right now: whether a mode can be activated (and why not), the
 * player's capabilities and limits under the effective server rules, and the reach for picking corners. Everything
 * here mirrors a check the server repeats, so the client explains a refusal before sending anything.
 */
final class ModeRules {

    /** Why {@code mode} cannot be activated now, null when it can (see {@link ClientModeState#unavailableReason}). */
    static @Nullable Component unavailableReason(final BuildMode mode) {
        final Minecraft mc = Minecraft.getInstance();
        final LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return Component.translatable("slate_building.notice.no_world");
        if (mode.kind() == ModeKind.MEASURE) return null;                 // client-only, never touches the world
        if (player.isSpectator()) return Component.translatable("slate_building.notice.spectator");
        if (!serverHasModule(mode)) return Component.translatable("slate_building.notice.no_server");
        final ServerOps ops = BuildingServerSettings.effective(mc.level).ops();
        if (!ops.enabled) return Component.translatable("slate_building.notice.ops_disabled");
        if (ops.disabledModes != null && ops.disabledModes.contains(mode.id())) {
            return Component.translatable("slate_building.notice.mode_disabled", mode.name());
        }
        if (mode == BuildModes.PASTE && ops.pasteOpLevel > 0 && !player.hasPermissions(ops.pasteOpLevel)) {
            return Component.translatable("slate_building.notice.paste_permission");
        }
        // Extended is only worth turning on with a reach bonus to add (the server refuses it the same way).
        if (mode.kind() == ModeKind.REACH && ToolboxAccess.of(player).limits(BuildingServerSettings.effective(player)).reachBonus() <= 0) {
            return Component.translatable("slate_building.notice.no_reach");
        }
        return ToolboxAccess.of(player).lockReason(mode);    // fresh: activation must not see a stale toolbox
    }

    /** Whether the server can take this mode's payloads (false on servers without Slate Building). */
    static boolean serverHasModule(final BuildMode mode) {
        if (Minecraft.getInstance().getConnection() == null) return false;
        return SlateNetwork.get().serverHasChannel(switch (mode.kind()) {
            case TOGGLE -> SetSymmetry.TYPE;
            case REACH -> SetReach.TYPE;
            default -> ApplyOp.TYPE;
        });
    }

    /** Whether undo / redo / cancel can reach the server. */
    static boolean serverHasOps() {
        return Minecraft.getInstance().getConnection() != null && SlateNetwork.get().serverHasChannel(ApplyOp.TYPE);
    }

    private static @Nullable Player cachedFor;
    private static int cachedTick = Integer.MIN_VALUE;
    private static long cachedGameMode = -1;
    private static ToolboxAccess.Capabilities cached = ToolboxAccess.Capabilities.NONE;

    /**
     * The player's capabilities, computed at most once per client tick (the preview and the raycast ask every frame;
     * the toolbox lookup walks the inventory). A game-mode switch refreshes at once.
     */
    static ToolboxAccess.Capabilities capabilities(final Player player) {
        final long mode = player.isCreative() ? 1 : player.isSpectator() ? 2 : 0;
        if (player != cachedFor || player.tickCount != cachedTick || mode != cachedGameMode) {
            cached = ToolboxAccess.of(player);
            cachedFor = player;
            cachedTick = player.tickCount;
            cachedGameMode = mode;
        }
        return cached;
    }

    /** The player's operation limits under the rules of the server they are on. */
    static Limits limits(final Player player) {
        return capabilities(player).limits(BuildingServerSettings.effective(player));
    }

    /** How far corners can be picked: vanilla block reach plus the tier reach bonus. */
    static double reach(final Player player) {
        return player.blockInteractionRange() + Math.max(0, limits(player).reachBonus());
    }

    /** Radius of the symmetry of a TOGGLE mode for this player (the server's symmetryRadius at the square tier). */
    static int symmetryRadius(final Player player) {
        return SymmetryMath.radius(player);
    }

    /** Shows {@code text} on the vanilla action bar (toast-free feedback that works with any HUD setup). */
    static void showActionBar(final Component text, final ClientModeState.Severity severity) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui == null) return;
        final ChatFormatting colour = switch (severity) {
            case INFO -> ChatFormatting.WHITE;
            case WARNING -> ChatFormatting.GOLD;
            case ERROR -> ChatFormatting.RED;
        };
        mc.gui.setOverlayMessage(text.copy().withStyle(colour), false);
    }

    private ModeRules() {}
}
