package dev.fallingcloud.slate.building.ops.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.config.BuildingServerSettings;
import dev.fallingcloud.slate.building.ops.Limits;
import dev.fallingcloud.slate.building.ops.ToolType;
import dev.fallingcloud.slate.building.toolbox.ToolTier;
import dev.fallingcloud.slate.building.toolbox.ToolboxAccess;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /slatebuild undo | redo | cancel | limits | reload}. Registered by each loader's ops glue (NeoForge
 * {@code RegisterCommandsEvent}, Fabric {@code CommandRegistrationCallback}). {@code reload} (permission 2) re-reads
 * {@code building-server.json} and sends it to everyone.
 */
public final class OpsCommands {

    public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("slatebuild")
            .then(Commands.literal("undo").executes(c -> {
                OpsServer.undo(c.getSource().getPlayerOrException());
                return 1;
            }))
            .then(Commands.literal("redo").executes(c -> {
                OpsServer.redo(c.getSource().getPlayerOrException());
                return 1;
            }))
            .then(Commands.literal("cancel").executes(c -> {
                final ServerPlayer player = c.getSource().getPlayerOrException();
                if (!OpsServer.isBusy(player)) {
                    c.getSource().sendFailure(Component.translatable("slate_building.command.idle"));
                    return 0;
                }
                OpsServer.cancel(player);
                return 1;
            }))
            .then(Commands.literal("limits").executes(c -> limits(c.getSource())))
            .then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(c -> reload(c.getSource()))));
    }

    private static int limits(final CommandSourceStack source) throws CommandSyntaxException {
        final ServerPlayer player = source.getPlayerOrException();
        final ToolboxAccess.Capabilities caps = ToolboxAccess.of(player);
        final Limits l = caps.limits(BuildingServerSettings.local());
        source.sendSuccess(() -> Component.translatable("slate_building.command.limits.header").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable("slate_building.command.limits.values", l.maxVolume(), l.maxSpan(), l.reachBonus(),
            l.blocksPerTick(), l.undoDepth()), false);
        final MutableComponent tools = Component.translatable("slate_building.command.limits.tools");
        boolean any = false;
        for (final ToolType t : ToolType.values()) {
            final int tier = caps.tier(t);
            if (tier <= 0) continue;
            tools.append(any ? Component.literal(", ") : Component.literal(" "));
            tools.append(Component.translatable("slate_building.tool_tiered", ToolTier.byLevel(tier).displayName(), t.displayName()));
            any = true;
        }
        if (!any) tools.append(Component.literal(" ")).append(Component.translatable("slate_building.command.limits.none"));
        source.sendSuccess(() -> tools, false);
        if (caps.creative()) source.sendSuccess(() -> Component.translatable("slate_building.command.limits.creative").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int reload(final CommandSourceStack source) {
        SlateBuilding.serverConfigFile().load();
        VariantRegistry.invalidate();
        BuildingServerSettings.broadcast(source.getServer());
        source.sendSuccess(() -> Component.translatable("slate_building.command.reloaded"), true);
        return 1;
    }

    private OpsCommands() {}
}
