package dev.fallingcloud.slate.profile.net;

import dev.fallingcloud.slate.core.Slate;
import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.net.Flow;
import dev.fallingcloud.slate.core.net.SlateNetwork;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.profile.SlateProfile;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * How a look gets to the other players. A client tells the server what its player looks like
 * ({@code slate:profile_look}); the server checks it, keeps it while the player is on, and tells every other
 * player who has the module ({@code slate:profile_look_of}), and whoever joins later. When a player leaves, the
 * others are told to forget the look. Both channels are optional on the wire: without the module on the server
 * nothing is sent, and a player without the module is never sent anything.
 *
 * <p>A look on the wire is the finished skin (a PNG of 64 × 64, what the player wears in cloth already painted
 * onto it) and the ids of what is worn in 3D. The server never decodes the picture: it checks the size, the
 * signature and the dimensions in its header, and passes the bytes on.</p>
 */
public final class ProfileNet {

    /** What a player looks like, as it travels. */
    public record LookData(byte[] skin, boolean slim, List<String> models) {

        /** A skin of 64 × 64 is a few kilobytes; nothing sane comes near this. */
        public static final int MAX_SKIN_BYTES = 48 * 1024;
        public static final int MAX_MODELS = 16, MAX_ID = 64;

        public static final StreamCodec<ByteBuf, LookData> CODEC = StreamCodec.composite(
            ByteBufCodecs.byteArray(MAX_SKIN_BYTES), LookData::skin,
            ByteBufCodecs.BOOL, LookData::slim,
            ByteBufCodecs.stringUtf8(MAX_ID).apply(ByteBufCodecs.list(MAX_MODELS)), LookData::models,
            LookData::new);

        public LookData {
            if (skin == null) skin = new byte[0];
            models = models == null ? List.of() : List.copyOf(models);
        }

        /** A PNG of 64 × 64, by its signature and header; ids that are ids. */
        public boolean valid() {
            if (skin.length < 33 || skin.length > MAX_SKIN_BYTES || models.size() > MAX_MODELS) return false;
            final int[] signature = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
            for (int i = 0; i < signature.length; i++) if ((skin[i] & 0xFF) != signature[i]) return false;
            // The header chunk comes first: its length, "IHDR", then the width and the height.
            if (skin[12] != 'I' || skin[13] != 'H' || skin[14] != 'D' || skin[15] != 'R') return false;
            final int width = (skin[16] & 0xFF) << 24 | (skin[17] & 0xFF) << 16 | (skin[18] & 0xFF) << 8 | (skin[19] & 0xFF);
            final int height = (skin[20] & 0xFF) << 24 | (skin[21] & 0xFF) << 16 | (skin[22] & 0xFF) << 8 | (skin[23] & 0xFF);
            if (width != 64 || height != 64) return false;
            for (final String id : models) {
                if (id == null || id.isEmpty() || id.length() > MAX_ID) return false;
                for (int i = 0; i < id.length(); i++) {
                    final char c = id.charAt(i);
                    if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '_' && c != ':' && c != '/' && c != '.' && c != '-') return false;
                }
            }
            return true;
        }
    }

    /** Client to server: this is what I look like. */
    public record LookUpdate(LookData look) implements CustomPacketPayload {
        public static final Type<LookUpdate> TYPE = new Type<>(Slate.id("profile_look"));
        public static final StreamCodec<ByteBuf, LookUpdate> CODEC = LookData.CODEC.map(LookUpdate::new, LookUpdate::look);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server to client: this is what that player looks like; no look means the player has none (any more). */
    public record LookOf(UUID player, Optional<LookData> look) implements CustomPacketPayload {
        public static final Type<LookOf> TYPE = new Type<>(Slate.id("profile_look_of"));
        public static final StreamCodec<ByteBuf, LookOf> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, LookOf::player,
            ByteBufCodecs.optional(LookData.CODEC), LookOf::look,
            LookOf::new);

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** A player may change clothes, not flood the server with them. */
    private static final long MIN_INTERVAL_MS = 750;

    private static final Map<UUID, LookData> LOOKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();
    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        final SlateNetwork net = SlateNetwork.get();
        net.register(LookUpdate.TYPE, LookUpdate.CODEC, Flow.C2S, (p, ctx) -> { if (ctx.sender() != null) received(ctx.sender(), p.look()); });
        net.register(LookOf.TYPE, LookOf.CODEC, Flow.S2C, (p, ctx) -> { if (SlatePlatform.get().isClient()) ClientSide.lookOf(p); });
        SlateEvents.PLAYER_JOINED.register(ProfileNet::joined);
        SlateEvents.PLAYER_LEFT.register(ProfileNet::left);
        SlateEvents.SERVER_STOPPING.register(server -> { LOOKS.clear(); LAST.clear(); });
    }

    /** How many looks the server holds right now (tests, the debug screen). */
    public static int held() { return LOOKS.size(); }

    private static void received(final ServerPlayer sender, final LookData look) {
        final UUID id = sender.getUUID();
        final long now = System.currentTimeMillis();
        final Long before = LAST.get(id);
        if (before != null && now - before < MIN_INTERVAL_MS) return;
        LAST.put(id, now);
        if (!look.valid()) {
            SlateProfile.LOGGER.warn("[Slate Profile] {} sent a look that is not one ({} bytes, {} models): dropped", sender.getGameProfile().getName(),
                look.skin().length, look.models().size());
            return;
        }
        if (LOOKS.put(id, look) == null) {
            SlateProfile.LOGGER.info("[Slate Profile] {} wears a look ({} bytes of skin, {} thing(s) in 3D)", sender.getGameProfile().getName(), look.skin().length, look.models().size());
        }
        tellOthers(sender, new LookOf(id, Optional.of(look)));
    }

    private static void tellOthers(final ServerPlayer about, final LookOf message) {
        final SlateNetwork net = SlateNetwork.get();
        for (final ServerPlayer other : new ArrayList<>(about.server.getPlayerList().getPlayers())) {
            if (other.getUUID().equals(about.getUUID())) continue;
            if (net.canSendToPlayer(other, LookOf.TYPE)) net.sendToPlayer(other, message);
        }
    }

    private static void joined(final ServerPlayer player) {
        final SlateNetwork net = SlateNetwork.get();
        if (!net.canSendToPlayer(player, LookOf.TYPE)) return;
        for (final ServerPlayer other : new ArrayList<>(player.server.getPlayerList().getPlayers())) {
            if (other.getUUID().equals(player.getUUID())) continue;
            final LookData look = LOOKS.get(other.getUUID());
            if (look != null) net.sendToPlayer(player, new LookOf(other.getUUID(), Optional.of(look)));
        }
    }

    private static void left(final ServerPlayer player) {
        LAST.remove(player.getUUID());
        if (LOOKS.remove(player.getUUID()) != null) tellOthers(player, new LookOf(player.getUUID(), Optional.empty()));
    }

    /** Client-only references live here so a dedicated server never resolves them. */
    private static final class ClientSide {
        static void lookOf(final LookOf message) { dev.fallingcloud.slate.profile.client.ProfileClient.lookOf(message); }
    }

    private ProfileNet() {}
}
