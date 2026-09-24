package dev.fallingcloud.slate.building.client.mode;

import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.config.ServerOps;
import dev.fallingcloud.slate.building.net.ApplyOp;
import dev.fallingcloud.slate.building.net.SetSymmetry;
import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.ops.Limits;
import dev.fallingcloud.slate.building.ops.ModeKind;
import dev.fallingcloud.slate.building.ops.ToolType;
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
        return ToolboxAccess.of(player).lockReason(mode);
    }

    /** Whether the server can take this mode's payloads (false on servers without Slate Building). */
    static boolean serverHasModule(final BuildMode mode) {
        if (Minecraft.getInstance().getConnection() == null) return false;
        return SlateNetwork.get().serverHasChannel(mode.kind() == ModeKind.TOGGLE ? SetSymmetry.TYPE : ApplyOp.TYPE);
    }

    /** Whether undo / redo / cancel can reach the server. */
    static boolean serverHasOps() {
        return Minecraft.getInstance().getConnection() != null && SlateNetwork.get().serverHasChannel(ApplyOp.TYPE);
    }

    static ToolboxAccess.Capabilities capabilities(final Player player) {
        return ToolboxAccess.of(player);
    }

    /** The player's operation limits under the rules of the server they are on. */
    static Limits limits(final Player player) {
        return capabilities(player).limits(BuildingServerSettings.effective(player));
    }

    /** How far corners can be picked: vanilla block reach plus the tier reach bonus. */
    static double reach(final Player player) {
        return player.blockInteractionRange() + Math.max(0, limits(player).reachBonus());
    }

    /** Radius of the symmetry of a TOGGLE mode for this player (square tier 1..4 → 16/32/64/128). */
    static int symmetryRadius(final Player player) {
        final int tier = Math.max(1, capabilities(player).tier(ToolType.SQUARE));
        return 16 << (Math.min(4, tier) - 1);
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
