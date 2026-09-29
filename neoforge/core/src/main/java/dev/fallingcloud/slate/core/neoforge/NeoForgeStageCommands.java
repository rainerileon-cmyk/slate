package dev.fallingcloud.slate.core.neoforge;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.fallingcloud.slate.core.stage.platform.StageCommands;
import dev.fallingcloud.slate.core.stage.scene.SceneCommandHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** NeoForge: {@code /slate scene capture|list|reload} as a client command. Found through ServiceLoader. */
public final class NeoForgeStageCommands implements StageCommands {

    @Override
    public void register(final SceneCommandHandler handler) {
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, e -> e.getDispatcher().register(
            Commands.literal("slate").then(Commands.literal("scene")
                .then(Commands.literal("capture")
                    .then(Commands.argument("template", StringArgumentType.string())
                        .then(Commands.argument("scene", StringArgumentType.string())
                            .executes(c -> reply(c, handler.capture(StringArgumentType.getString(c, "template"), StringArgumentType.getString(c, "scene"), null)))
                            .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                    .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(c -> reply(c, handler.capture(StringArgumentType.getString(c, "template"), StringArgumentType.getString(c, "scene"),
                                            new BlockPos(IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "y"), IntegerArgumentType.getInteger(c, "z")))))))))))
                .then(Commands.literal("list").executes(c -> reply(c, handler.list())))
                .then(Commands.literal("reload").executes(c -> reply(c, handler.reload()))))));
    }

    private static int reply(final CommandContext<CommandSourceStack> c, final Component message) {
        c.getSource().sendSuccess(() -> message, false);
        return 1;
    }
}
