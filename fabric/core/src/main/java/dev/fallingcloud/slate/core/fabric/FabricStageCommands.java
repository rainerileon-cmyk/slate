package dev.fallingcloud.slate.core.fabric;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.fallingcloud.slate.core.stage.platform.StageCommands;
import dev.fallingcloud.slate.core.stage.scene.SceneCommandHandler;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Fabric: {@code /slate scene capture|list|reload} as a client command. Found through ServiceLoader. */
public final class FabricStageCommands implements StageCommands {

    @Override
    public void register(final SceneCommandHandler handler) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
            ClientCommandManager.literal("slate").then(ClientCommandManager.literal("scene")
                .then(ClientCommandManager.literal("capture")
                    .then(ClientCommandManager.argument("template", StringArgumentType.string())
                        .then(ClientCommandManager.argument("scene", StringArgumentType.string())
                            .executes(c -> reply(c, handler.capture(StringArgumentType.getString(c, "template"), StringArgumentType.getString(c, "scene"), null)))
                            .then(ClientCommandManager.argument("x", IntegerArgumentType.integer())
                                .then(ClientCommandManager.argument("y", IntegerArgumentType.integer())
                                    .then(ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                        .executes(c -> reply(c, handler.capture(StringArgumentType.getString(c, "template"), StringArgumentType.getString(c, "scene"),
                                            new BlockPos(IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "y"), IntegerArgumentType.getInteger(c, "z")))))))))))
                .then(ClientCommandManager.literal("list").executes(c -> reply(c, handler.list())))
                .then(ClientCommandManager.literal("reload").executes(c -> reply(c, handler.reload()))))));
    }

    private static int reply(final CommandContext<FabricClientCommandSource> c, final Component message) {
        c.getSource().sendFeedback(message);
        return 1;
    }
}
