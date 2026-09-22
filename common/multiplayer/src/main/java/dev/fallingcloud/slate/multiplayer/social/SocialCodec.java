package dev.fallingcloud.slate.multiplayer.social;

import dev.fallingcloud.slate.multiplayer.social.SocialMessage.*;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage.Error;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * The one binary encoding of {@link SocialMessage}: a kind byte followed by the record's fields. Used by
 * the {@code slate:social} payload and by the TCP link's frames, so a hub can serve both without
 * translating. Every string is length-capped on read so a hostile peer cannot allocate freely; a bad kind
 * throws, which the receiving side turns into a dropped frame.
 */
public final class SocialCodec {

    /** As a StreamCodec over a plain FriendlyByteBuf (satisfies {@code ? super RegistryFriendlyByteBuf}). */
    public static final StreamCodec<FriendlyByteBuf, SocialMessage> STREAM = StreamCodec.of(SocialCodec::write, SocialCodec::read);

    private static final int NAME = 64, ID = 64, KEY = 96, SERVER = 256, DIM = 128, STATUS = 160, META = 512,
        LABEL = 128, TITLE = 128, ERR = 512, TEXT = ChatMessage.MAX_TEXT + 16, NOTE = 256;
    private static final int MAX_LIST = 4096;
    private static final int MAX_BLOB = 32 * 1024;

    // ------------------------------------------------------------------ convenience

    /** Encodes into a fresh heap buffer (TCP frames). The caller owns the returned buffer. */
    public static ByteBuf encode(final SocialMessage m) {
        final ByteBuf buf = Unpooled.buffer(64);
        write(new FriendlyByteBuf(buf), m);
        return buf;
    }

    public static SocialMessage decode(final ByteBuf buf) {
        return read(new FriendlyByteBuf(buf));
    }

    // ------------------------------------------------------------------ write

    public static void write(final FriendlyByteBuf b, final SocialMessage m) {
        switch (m) {
            case HubInfo x -> { b.writeByte(1); b.writeUtf(x.hubName(), NAME); b.writeVarInt(x.protocol()); b.writeBoolean(x.onlineMode()); }
            case Hello x -> { b.writeByte(2); b.writeUUID(x.uuid()); b.writeUtf(x.name(), NAME); b.writeVarInt(x.protocol()); b.writeUtf(x.serverId(), NAME); }
            case Welcome x -> { b.writeByte(3); b.writeUtf(x.hubName(), NAME); ref(b, x.you()); b.writeBoolean(x.onlineMode()); b.writeLong(x.serverTimeMs()); }
            case Error x -> { b.writeByte(4); b.writeVarInt(x.code()); b.writeUtf(x.message(), ERR); b.writeUtf(x.context(), NAME); }
            case Ack x -> { b.writeByte(5); b.writeUtf(x.context(), NAME); b.writeUtf(x.value(), META); }
            case Ping x -> { b.writeByte(6); b.writeLong(x.sentMs()); }
            case Pong x -> { b.writeByte(7); b.writeLong(x.sentMs()); }
            case Snapshot x -> {
                b.writeByte(8);
                ref(b, x.self());
                b.writeCollection(x.friends(), SocialCodec::friend);
                b.writeCollection(x.requestsIn(), SocialCodec::request);
                b.writeCollection(x.requestsOut(), SocialCodec::request);
                b.writeCollection(x.blocked(), SocialCodec::ref);
                b.writeCollection(x.groups(), SocialCodec::group);
                b.writeCollection(x.groupInvites(), SocialCodec::groupInvite);
                b.writeCollection(x.threads(), SocialCodec::thread);
                b.writeCollection(x.streams(), SocialCodec::stream);
            }
            case FriendRequest x -> { b.writeByte(10); b.writeUUID(x.target()); b.writeUtf(x.targetName(), NAME); }
            case FriendRequestIn x -> { b.writeByte(11); request(b, x.request()); }
            case FriendAccept x -> { b.writeByte(12); b.writeUUID(x.target()); }
            case FriendDecline x -> { b.writeByte(13); b.writeUUID(x.target()); }
            case FriendCancel x -> { b.writeByte(14); b.writeUUID(x.target()); }
            case FriendRemove x -> { b.writeByte(15); b.writeUUID(x.target()); }
            case FriendBlock x -> { b.writeByte(16); b.writeUUID(x.target()); b.writeUtf(x.name(), NAME); b.writeBoolean(x.block()); }
            case FriendUpdate x -> { b.writeByte(17); friend(b, x.friend()); }
            case FriendRemoved x -> { b.writeByte(18); b.writeUUID(x.uuid()); b.writeUtf(x.reason(), NAME); }
            case RequestsUpdate x -> { b.writeByte(19); b.writeCollection(x.in(), SocialCodec::request); b.writeCollection(x.out(), SocialCodec::request); }
            case BlockedUpdate x -> { b.writeByte(20); b.writeCollection(x.blocked(), SocialCodec::ref); }
            case NicknameSet x -> { b.writeByte(21); b.writeUUID(x.target()); b.writeUtf(x.nick(), NAME); b.writeUtf(x.note(), NOTE); }
            case PresenceSet x -> { b.writeByte(25); presence(b, x.presence()); }
            case PresenceUpdate x -> { b.writeByte(26); b.writeUUID(x.uuid()); presence(b, x.presence()); }
            case Chat x -> { b.writeByte(30); b.writeUtf(x.threadKey(), KEY); message(b, x.message()); }
            case Typing x -> { b.writeByte(31); b.writeUtf(x.threadKey(), KEY); b.writeUUID(x.who()); b.writeBoolean(x.typing()); }
            case HistoryRequest x -> { b.writeByte(32); b.writeUtf(x.threadKey(), KEY); b.writeLong(x.beforeMs()); b.writeVarInt(x.limit()); }
            case History x -> { b.writeByte(33); b.writeUtf(x.threadKey(), KEY); b.writeCollection(x.messages(), SocialCodec::message); b.writeBoolean(x.more()); }
            case MarkRead x -> { b.writeByte(34); b.writeUtf(x.threadKey(), KEY); b.writeLong(x.upToMs()); }
            case MediaRequest x -> { b.writeByte(35); b.writeUtf(x.blobId(), ID); }
            case GroupCreate x -> { b.writeByte(40); b.writeUtf(x.name(), NAME); }
            case GroupInviteSend x -> { b.writeByte(41); b.writeUtf(x.groupId(), ID); b.writeUUID(x.target()); }
            case GroupInviteIn x -> { b.writeByte(42); groupInvite(b, x.invite()); }
            case GroupJoin x -> { b.writeByte(43); b.writeUtf(x.groupId(), ID); b.writeBoolean(x.accept()); }
            case GroupLeave x -> { b.writeByte(44); b.writeUtf(x.groupId(), ID); }
            case GroupRename x -> { b.writeByte(45); b.writeUtf(x.groupId(), ID); b.writeUtf(x.name(), NAME); }
            case GroupKick x -> { b.writeByte(46); b.writeUtf(x.groupId(), ID); b.writeUUID(x.target()); }
            case GroupVoiceSet x -> { b.writeByte(47); b.writeUtf(x.groupId(), ID); b.writeUtf(x.voiceGroup(), NAME); }
            case GroupUpdate x -> { b.writeByte(48); group(b, x.group()); }
            case GroupRemoved x -> { b.writeByte(49); b.writeUtf(x.groupId(), ID); }
            case Invite x -> { b.writeByte(50); b.writeUUID(x.target()); b.writeUtf(x.kind(), NAME); b.writeUtf(x.address(), SERVER); b.writeUtf(x.label(), LABEL); }
            case InviteIn x -> { b.writeByte(51); ref(b, x.from()); b.writeUtf(x.kind(), NAME); b.writeUtf(x.address(), SERVER); b.writeUtf(x.label(), LABEL); b.writeLong(x.atMs()); }
            case StreamStart x -> { b.writeByte(55); b.writeUtf(x.title(), TITLE); }
            case StreamStop x -> { b.writeByte(56); b.writeUtf(x.streamId(), ID); }
            case StreamWatch x -> { b.writeByte(57); b.writeUtf(x.streamId(), ID); b.writeBoolean(x.watch()); }
            case StreamUpdate x -> { b.writeByte(58); stream(b, x.stream()); }
            case StreamEnded x -> { b.writeByte(59); b.writeUtf(x.streamId(), ID); }
            case StreamFrame x -> { b.writeByte(60); b.writeUtf(x.streamId(), ID); b.writeVarInt(x.seq()); b.writeVarInt(x.width()); b.writeVarInt(x.height()); b.writeLong(x.sentMs()); b.writeVarInt(x.bytes()); }
            case BlobPassthrough x -> { b.writeByte(70); b.writeByte(x.kind()); b.writeByteArray(x.data()); }
        }
    }

    // ------------------------------------------------------------------ read

    public static SocialMessage read(final FriendlyByteBuf b) {
        final int kind = b.readByte();
        return switch (kind) {
            case 1 -> new HubInfo(b.readUtf(NAME), b.readVarInt(), b.readBoolean());
            case 2 -> new Hello(b.readUUID(), b.readUtf(NAME), b.readVarInt(), b.readUtf(NAME));
            case 3 -> new Welcome(b.readUtf(NAME), ref(b), b.readBoolean(), b.readLong());
            case 4 -> new Error(b.readVarInt(), b.readUtf(ERR), b.readUtf(NAME));
            case 5 -> new Ack(b.readUtf(NAME), b.readUtf(META));
            case 6 -> new Ping(b.readLong());
            case 7 -> new Pong(b.readLong());
            case 8 -> new Snapshot(ref(b), list(b, SocialCodec::friend), list(b, SocialCodec::request), list(b, SocialCodec::request),
                list(b, SocialCodec::ref), list(b, SocialCodec::group), list(b, SocialCodec::groupInvite), list(b, SocialCodec::thread), list(b, SocialCodec::stream));
            case 10 -> new FriendRequest(b.readUUID(), b.readUtf(NAME));
            case 11 -> new FriendRequestIn(request(b));
            case 12 -> new FriendAccept(b.readUUID());
            case 13 -> new FriendDecline(b.readUUID());
            case 14 -> new FriendCancel(b.readUUID());
            case 15 -> new FriendRemove(b.readUUID());
            case 16 -> new FriendBlock(b.readUUID(), b.readUtf(NAME), b.readBoolean());
            case 17 -> new FriendUpdate(friend(b));
            case 18 -> new FriendRemoved(b.readUUID(), b.readUtf(NAME));
            case 19 -> new RequestsUpdate(list(b, SocialCodec::request), list(b, SocialCodec::request));
            case 20 -> new BlockedUpdate(list(b, SocialCodec::ref));
            case 21 -> new NicknameSet(b.readUUID(), b.readUtf(NAME), b.readUtf(NOTE));
            case 25 -> new PresenceSet(presence(b));
            case 26 -> new PresenceUpdate(b.readUUID(), presence(b));
            case 30 -> new Chat(b.readUtf(KEY), message(b));
            case 31 -> new Typing(b.readUtf(KEY), b.readUUID(), b.readBoolean());
            case 32 -> new HistoryRequest(b.readUtf(KEY), b.readLong(), b.readVarInt());
            case 33 -> new History(b.readUtf(KEY), list(b, SocialCodec::message), b.readBoolean());
            case 34 -> new MarkRead(b.readUtf(KEY), b.readLong());
            case 35 -> new MediaRequest(b.readUtf(ID));
            case 40 -> new GroupCreate(b.readUtf(NAME));
            case 41 -> new GroupInviteSend(b.readUtf(ID), b.readUUID());
            case 42 -> new GroupInviteIn(groupInvite(b));
            case 43 -> new GroupJoin(b.readUtf(ID), b.readBoolean());
            case 44 -> new GroupLeave(b.readUtf(ID));
            case 45 -> new GroupRename(b.readUtf(ID), b.readUtf(NAME));
            case 46 -> new GroupKick(b.readUtf(ID), b.readUUID());
            case 47 -> new GroupVoiceSet(b.readUtf(ID), b.readUtf(NAME));
            case 48 -> new GroupUpdate(group(b));
            case 49 -> new GroupRemoved(b.readUtf(ID));
            case 50 -> new Invite(b.readUUID(), b.readUtf(NAME), b.readUtf(SERVER), b.readUtf(LABEL));
            case 51 -> new InviteIn(ref(b), b.readUtf(NAME), b.readUtf(SERVER), b.readUtf(LABEL), b.readLong());
            case 55 -> new StreamStart(b.readUtf(TITLE));
            case 56 -> new StreamStop(b.readUtf(ID));
            case 57 -> new StreamWatch(b.readUtf(ID), b.readBoolean());
            case 58 -> new StreamUpdate(stream(b));
            case 59 -> new StreamEnded(b.readUtf(ID));
            case 60 -> new StreamFrame(b.readUtf(ID), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readLong(), b.readVarInt());
            case 70 -> new BlobPassthrough(b.readByte(), b.readByteArray(MAX_BLOB));
            default -> throw new IllegalArgumentException("unknown social message kind " + kind);
        };
    }

    // ------------------------------------------------------------------ sub-records

    private static <T> List<T> list(final FriendlyByteBuf b, final java.util.function.Function<FriendlyByteBuf, T> reader) {
        final int n = b.readVarInt();
        if (n < 0 || n > MAX_LIST) throw new IllegalArgumentException("list too long: " + n);
        final java.util.ArrayList<T> out = new java.util.ArrayList<>(Math.min(n, 256));
        for (int i = 0; i < n; i++) out.add(reader.apply(b));
        return out;
    }

    private static void ref(final FriendlyByteBuf b, final PlayerRef r) {
        b.writeUUID(r.uuid());
        b.writeUtf(r.name(), NAME);
    }

    private static PlayerRef ref(final FriendlyByteBuf b) {
        return new PlayerRef(b.readUUID(), b.readUtf(NAME));
    }

    private static void presence(final FriendlyByteBuf b, final Presence p) {
        b.writeEnum(p.state());
        b.writeUtf(p.server(), SERVER);
        b.writeUtf(p.dimension(), DIM);
        b.writeUtf(p.status(), STATUS);
        b.writeLong(p.sinceMs());
    }

    private static Presence presence(final FriendlyByteBuf b) {
        return new Presence(b.readEnum(Presence.State.class), b.readUtf(SERVER), b.readUtf(DIM), b.readUtf(STATUS), b.readLong());
    }

    private static void friend(final FriendlyByteBuf b, final FriendInfo f) {
        ref(b, f.ref());
        b.writeUtf(f.nick(), NAME);
        b.writeUtf(f.note(), NOTE);
        presence(b, f.presence());
        b.writeLong(f.sinceMs());
    }

    private static FriendInfo friend(final FriendlyByteBuf b) {
        return new FriendInfo(ref(b), b.readUtf(NAME), b.readUtf(NOTE), presence(b), b.readLong());
    }

    private static void request(final FriendlyByteBuf b, final RequestInfo r) {
        ref(b, r.ref());
        b.writeLong(r.atMs());
    }

    private static RequestInfo request(final FriendlyByteBuf b) {
        return new RequestInfo(ref(b), b.readLong());
    }

    private static void group(final FriendlyByteBuf b, final GroupInfo g) {
        b.writeUtf(g.id(), ID);
        b.writeUtf(g.name(), NAME);
        b.writeUUID(g.owner());
        b.writeCollection(g.members(), SocialCodec::ref);
        b.writeUtf(g.voiceGroup(), NAME);
        b.writeLong(g.createdMs());
    }

    private static GroupInfo group(final FriendlyByteBuf b) {
        return new GroupInfo(b.readUtf(ID), b.readUtf(NAME), b.readUUID(), list(b, SocialCodec::ref), b.readUtf(NAME), b.readLong());
    }

    private static void groupInvite(final FriendlyByteBuf b, final GroupInvite i) {
        group(b, i.group());
        ref(b, i.from());
        b.writeLong(i.atMs());
    }

    private static GroupInvite groupInvite(final FriendlyByteBuf b) {
        return new GroupInvite(group(b), ref(b), b.readLong());
    }

    private static void message(final FriendlyByteBuf b, final ChatMessage m) {
        b.writeUtf(m.id(), ID);
        ref(b, m.from());
        b.writeUtf(m.text(), TEXT);
        b.writeUtf(m.attachKind(), NAME);
        b.writeUtf(m.attachId(), ID);
        b.writeUtf(m.attachMeta(), META);
        b.writeLong(m.atMs());
    }

    private static ChatMessage message(final FriendlyByteBuf b) {
        return new ChatMessage(b.readUtf(ID), ref(b), b.readUtf(TEXT), b.readUtf(NAME), b.readUtf(ID), b.readUtf(META), b.readLong());
    }

    private static void thread(final FriendlyByteBuf b, final ThreadSummary t) {
        b.writeUtf(t.key(), KEY);
        b.writeUtf(t.title(), NAME);
        b.writeBoolean(t.last() != null);
        if (t.last() != null) message(b, t.last());
        b.writeVarInt(t.unread());
    }

    private static ThreadSummary thread(final FriendlyByteBuf b) {
        final String key = b.readUtf(KEY);
        final String title = b.readUtf(NAME);
        final ChatMessage last = b.readBoolean() ? message(b) : null;
        return new ThreadSummary(key, title, last, b.readVarInt());
    }

    private static void stream(final FriendlyByteBuf b, final StreamInfo s) {
        b.writeUtf(s.id(), ID);
        ref(b, s.owner());
        b.writeUtf(s.title(), TITLE);
        b.writeVarInt(s.viewers());
        b.writeLong(s.startedMs());
    }

    private static StreamInfo stream(final FriendlyByteBuf b) {
        return new StreamInfo(b.readUtf(ID), ref(b), b.readUtf(TITLE), b.readVarInt(), b.readLong());
    }

    /** Sanity for ids coming off the wire (uuids are structural; string ids must be short and plain). */
    public static boolean validId(final String id) {
        if (id == null || id.isEmpty() || id.length() > ID) return false;
        for (int i = 0; i < id.length(); i++) {
            final char c = id.charAt(i);
            if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '-' || c == '_')) return false;
        }
        return true;
    }

    public static boolean isNil(final UUID u) { return u == null || PlayerRef.NIL.equals(u); }

    private SocialCodec() {}
}
