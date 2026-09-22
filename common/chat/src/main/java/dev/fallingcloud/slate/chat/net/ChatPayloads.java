package dev.fallingcloud.slate.chat.net;

import dev.fallingcloud.slate.chat.ChatServerConfig;
import dev.fallingcloud.slate.chat.SlateChat;
import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.net.Flow;
import dev.fallingcloud.slate.core.net.SlateNetwork;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Slate Chat's own payloads (media rides on Core's blob channel). One bidirectional payload,
 * {@code slate:chat_typing}: the client sends an empty sender plus the typing flag while the input changes (rate limited),
 * the server stamps the sender's name from its own profile and relays it to every other player that has
 * the channel. Registered from {@link SlateChat#init()} on both sides, optional on the wire.
 */
public final class ChatPayloads {

    public record Typing(String sender, boolean typing) implements CustomPacketPayload {
        public static final Type<Typing> TYPE = new Type<>(Slate.id("chat_typing"));
        public static final StreamCodec<ByteBuf, Typing> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), Typing::sender,
            ByteBufCodecs.BOOL, Typing::typing,
            Typing::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        SlateNetwork.get().register(Typing.TYPE, Typing.CODEC, Flow.BOTH, (p, ctx) -> {
            if (ctx.isClient()) {
                if (SlatePlatform.get().isClient()) ClientSide.typing(p);
            } else {
                relayTyping(p, ctx.sender());
            }
        });
    }

    private static void relayTyping(final Typing p, final ServerPlayer sender) {
        if (sender == null || !ChatServerConfig.get().typingRelay) return;
        final Typing stamped = new Typing(sender.getGameProfile().getName(), p.typing());
        final SlateNetwork net = SlateNetwork.get();
        for (final ServerPlayer other : sender.server.getPlayerList().getPlayers()) {
            if (other.getUUID().equals(sender.getUUID())) continue;
            net.sendToPlayer(other, stamped);
        }
    }

    /** Client only: sends our own typing state (no-op when the server lacks the channel). */
    public static void sendTyping(final boolean typing) {
        if (!SlateNetwork.get().serverHasChannel(Typing.TYPE)) return;
        SlateNetwork.get().sendToServer(new Typing("", typing));
    }

    /** Client-only references live here so a dedicated server never resolves them. */
    private static final class ClientSide {
        static void typing(final Typing p) {
            dev.fallingcloud.slate.chat.client.TypingIndicator.onRemote(p.sender(), p.typing());
        }
    }

    private ChatPayloads() {}
}
