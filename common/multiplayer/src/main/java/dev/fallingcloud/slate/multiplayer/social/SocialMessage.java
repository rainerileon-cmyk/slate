package dev.fallingcloud.slate.multiplayer.social;

import java.util.List;
import java.util.UUID;

/**
 * Every message of the social protocol, one sealed set. The same records travel as the
 * {@code slate:social} custom payload on the in-game channel and as length-prefixed frames on the hub's
 * TCP link; {@link SocialCodec} is the single binary encoding for both. Direction is noted per record:
 * C&gt;H = client to hub, H&gt;C = hub to client.
 *
 * <p>Identity is never trusted from a client: the hub stamps sender fields ({@code from}, {@code who},
 * message ids and timestamps) from the session it received the message on.</p>
 */
public sealed interface SocialMessage permits
    SocialMessage.HubInfo, SocialMessage.Hello, SocialMessage.Welcome, SocialMessage.Error, SocialMessage.Ack,
    SocialMessage.Ping, SocialMessage.Pong, SocialMessage.Snapshot,
    SocialMessage.FriendRequest, SocialMessage.FriendRequestIn, SocialMessage.FriendAccept, SocialMessage.FriendDecline,
    SocialMessage.FriendCancel, SocialMessage.FriendRemove, SocialMessage.FriendBlock, SocialMessage.FriendUpdate,
    SocialMessage.FriendRemoved, SocialMessage.RequestsUpdate, SocialMessage.BlockedUpdate, SocialMessage.NicknameSet,
    SocialMessage.PresenceSet, SocialMessage.PresenceUpdate,
    SocialMessage.Chat, SocialMessage.Typing, SocialMessage.HistoryRequest, SocialMessage.History, SocialMessage.MarkRead,
    SocialMessage.MediaRequest,
    SocialMessage.GroupCreate, SocialMessage.GroupInviteSend, SocialMessage.GroupInviteIn, SocialMessage.GroupJoin,
    SocialMessage.GroupLeave, SocialMessage.GroupRename, SocialMessage.GroupKick, SocialMessage.GroupVoiceSet,
    SocialMessage.GroupUpdate, SocialMessage.GroupRemoved,
    SocialMessage.Invite, SocialMessage.InviteIn,
    SocialMessage.StreamStart, SocialMessage.StreamStop, SocialMessage.StreamWatch, SocialMessage.StreamUpdate,
    SocialMessage.StreamEnded, SocialMessage.StreamFrame,
    SocialMessage.BlobPassthrough {

    /** Bumped when the wire format changes; a mismatch is refused at Hello. */
    int PROTOCOL = 1;

    // ------------------------------------------------------------------ link & auth

    /** H&gt;C, first frame on a TCP link: what this hub is and whether it verifies Mojang sessions. */
    record HubInfo(String hubName, int protocol, boolean onlineMode) implements SocialMessage {}

    /**
     * C&gt;H: who I am. On the TCP link with {@code onlineMode} the client has already called
     * {@code joinServer(uuid, token, serverId)} and the hub verifies with {@code hasJoinedServer};
     * on the payload link identity comes from the ServerPlayer and these fields are informational.
     */
    record Hello(UUID uuid, String name, int protocol, String serverId) implements SocialMessage {}

    /** H&gt;C: authenticated; a {@link Snapshot} follows. */
    record Welcome(String hubName, PlayerRef you, boolean onlineMode, long serverTimeMs) implements SocialMessage {}

    /** H&gt;C: a request failed or the link is being refused. {@code context} names the request kind. */
    record Error(int code, String message, String context) implements SocialMessage {
        public static final int BAD_REQUEST = 400, UNAUTHORIZED = 401, FORBIDDEN = 403, NOT_FOUND = 404,
            CONFLICT = 409, TOO_MANY = 429, REPLACED = 440, INTERNAL = 500, PROTOCOL = 505;
    }

    /** H&gt;C: a request succeeded and produced a value (e.g. the id of a created group). */
    record Ack(String context, String value) implements SocialMessage {}

    record Ping(long sentMs) implements SocialMessage {}

    record Pong(long sentMs) implements SocialMessage {}

    // ------------------------------------------------------------------ full sync

    /** H&gt;C after Welcome and whenever the hub prefers a full resync: the player's whole social state. */
    record Snapshot(PlayerRef self, List<FriendInfo> friends, List<RequestInfo> requestsIn, List<RequestInfo> requestsOut,
                    List<PlayerRef> blocked, List<GroupInfo> groups, List<GroupInvite> groupInvites,
                    List<ThreadSummary> threads, List<StreamInfo> streams) implements SocialMessage {}

    // ------------------------------------------------------------------ friends

    /** C&gt;H: ask {@code target} (or, when NIL, the player called {@code targetName}) to be friends. */
    record FriendRequest(UUID target, String targetName) implements SocialMessage {}

    /** H&gt;C: someone asked you. */
    record FriendRequestIn(RequestInfo request) implements SocialMessage {}

    record FriendAccept(UUID target) implements SocialMessage {}

    record FriendDecline(UUID target) implements SocialMessage {}

    /** C&gt;H: withdraw my outgoing request. */
    record FriendCancel(UUID target) implements SocialMessage {}

    record FriendRemove(UUID target) implements SocialMessage {}

    /** C&gt;H: block ({@code block}) or unblock; {@code name} is used when the target is not yet known to the hub. */
    record FriendBlock(UUID target, String name, boolean block) implements SocialMessage {}

    /** H&gt;C: a friend was added or changed (nick/note/name). */
    record FriendUpdate(FriendInfo friend) implements SocialMessage {}

    record FriendRemoved(UUID uuid, String reason) implements SocialMessage {}

    /** H&gt;C: the complete request lists after any change. */
    record RequestsUpdate(List<RequestInfo> in, List<RequestInfo> out) implements SocialMessage {}

    record BlockedUpdate(List<PlayerRef> blocked) implements SocialMessage {}

    /** C&gt;H: my private nickname/note for a friend (empty clears). */
    record NicknameSet(UUID target, String nick, String note) implements SocialMessage {}

    // ------------------------------------------------------------------ presence

    /** C&gt;H: what I am doing (already filtered by the client's privacy settings). */
    record PresenceSet(Presence presence) implements SocialMessage {}

    /** H&gt;C: a friend's presence changed. */
    record PresenceUpdate(UUID uuid, Presence presence) implements SocialMessage {}

    // ------------------------------------------------------------------ messaging

    /** C&gt;H (id/time/from ignored) and H&gt;C (stamped): a message in a thread. */
    record Chat(String threadKey, ChatMessage message) implements SocialMessage {}

    /** C&gt;H ({@code who} ignored) and H&gt;C: typing state in a thread. */
    record Typing(String threadKey, UUID who, boolean typing) implements SocialMessage {}

    /** C&gt;H: messages older than {@code beforeMs} (0 = newest), at most {@code limit}. */
    record HistoryRequest(String threadKey, long beforeMs, int limit) implements SocialMessage {}

    /** H&gt;C: a page of history, oldest first; {@code more} when older messages exist. */
    record History(String threadKey, List<ChatMessage> messages, boolean more) implements SocialMessage {}

    /** C&gt;H: I have read everything up to {@code upToMs} in this thread. */
    record MarkRead(String threadKey, long upToMs) implements SocialMessage {}

    /** C&gt;H: please (re)send the stored attachment blob with this id to me. */
    record MediaRequest(String blobId) implements SocialMessage {}

    // ------------------------------------------------------------------ groups

    record GroupCreate(String name) implements SocialMessage {}

    record GroupInviteSend(String groupId, UUID target) implements SocialMessage {}

    /** H&gt;C: you were invited into a group. */
    record GroupInviteIn(GroupInvite invite) implements SocialMessage {}

    /** C&gt;H: accept ({@code accept}) or decline a pending invite. */
    record GroupJoin(String groupId, boolean accept) implements SocialMessage {}

    record GroupLeave(String groupId) implements SocialMessage {}

    record GroupRename(String groupId, String name) implements SocialMessage {}

    record GroupKick(String groupId, UUID target) implements SocialMessage {}

    /** C&gt;H: this group's voice chat group id (a Simple Voice Chat group uuid; empty clears). */
    record GroupVoiceSet(String groupId, String voiceGroup) implements SocialMessage {}

    /** H&gt;C: a group you belong to was created or changed. */
    record GroupUpdate(GroupInfo group) implements SocialMessage {}

    /** H&gt;C: you are no longer in this group (left, kicked, or it was deleted). */
    record GroupRemoved(String groupId) implements SocialMessage {}

    // ------------------------------------------------------------------ invites

    /** C&gt;H: invite a friend. {@code kind} = {@code server} (address = host:port) or {@code voice} (address = SVC group uuid). */
    record Invite(UUID target, String kind, String address, String label) implements SocialMessage {}

    record InviteIn(PlayerRef from, String kind, String address, String label, long atMs) implements SocialMessage {}

    // ------------------------------------------------------------------ screen share

    record StreamStart(String title) implements SocialMessage {}

    record StreamStop(String streamId) implements SocialMessage {}

    /** C&gt;H: start ({@code watch}) or stop receiving a stream's frames. */
    record StreamWatch(String streamId, boolean watch) implements SocialMessage {}

    /** H&gt;C: a friend's stream started or its viewer count changed. */
    record StreamUpdate(StreamInfo stream) implements SocialMessage {}

    record StreamEnded(String streamId) implements SocialMessage {}

    /** Both ways: announces the frame blob that follows (sequence, size, capture time for latency). */
    record StreamFrame(String streamId, int seq, int width, int height, long sentMs, int bytes) implements SocialMessage {}

    // ------------------------------------------------------------------ blobs over the TCP link

    /**
     * Both ways: one blob payload (Start/Chunk/End) carried inside a social frame, for links that have no
     * payload channel. {@code kind} 0 = Start, 1 = Chunk, 2 = End; {@code data} is that payload's own
     * StreamCodec encoding, so the bytes are identical to the in-game channel's.
     */
    record BlobPassthrough(int kind, byte[] data) implements SocialMessage {
        public static final int START = 0, CHUNK = 1, END = 2;
    }
}
