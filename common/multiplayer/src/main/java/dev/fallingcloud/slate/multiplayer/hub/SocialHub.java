package dev.fallingcloud.slate.multiplayer.hub;

import dev.fallingcloud.slate.core.net.blob.BlobPayloads;
import dev.fallingcloud.slate.core.net.blob.BlobRelay;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.multiplayer.MultiplayerConfigs;
import dev.fallingcloud.slate.multiplayer.MultiplayerServerConfig;
import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;
import dev.fallingcloud.slate.multiplayer.hub.HubStore.GroupRecord;
import dev.fallingcloud.slate.multiplayer.hub.HubStore.HistoryLog;
import dev.fallingcloud.slate.multiplayer.hub.HubStore.InviteRecord;
import dev.fallingcloud.slate.multiplayer.hub.HubStore.PlayerRecord;
import dev.fallingcloud.slate.multiplayer.social.BlobFrames;
import dev.fallingcloud.slate.multiplayer.social.ChatMessage;
import dev.fallingcloud.slate.multiplayer.social.FriendInfo;
import dev.fallingcloud.slate.multiplayer.social.GroupInfo;
import dev.fallingcloud.slate.multiplayer.social.GroupInvite;
import dev.fallingcloud.slate.multiplayer.social.PlayerRef;
import dev.fallingcloud.slate.multiplayer.social.Presence;
import dev.fallingcloud.slate.multiplayer.social.RequestInfo;
import dev.fallingcloud.slate.multiplayer.social.SocialCodec;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage.*;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage.Error;
import dev.fallingcloud.slate.multiplayer.social.SocialPayload;
import dev.fallingcloud.slate.multiplayer.social.StreamInfo;
import dev.fallingcloud.slate.core.net.SlateNetwork;
import dev.fallingcloud.slate.core.net.blob.BlobRouter;
import dev.fallingcloud.slate.multiplayer.social.ThreadSummary;
import dev.fallingcloud.slate.multiplayer.social.Threads;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * The social hub: one instance per running server (integrated or dedicated). Owns the sessions, the
 * friend graph, presence fan-out, messaging, groups, invites, screen-share membership and the blob
 * routing for social targets. Every method runs on the server main thread; the TCP listener and the
 * payload handler marshal onto it.
 */
public final class SocialHub {

    private static volatile SocialHub current;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** A live screen share. */
    private static final class LiveStream {
        StreamInfo info;
        final Set<UUID> viewers = new LinkedHashSet<>();
        LiveStream(final StreamInfo info) { this.info = info; }
        StreamInfo snapshot() { return info = info.withViewers(viewers.size()); }
    }

    private final MinecraftServer server;
    private final MultiplayerServerConfig cfg;
    private final HubStore store;
    private final HubBlobRelay blobs;
    private final String hubName;
    private final Map<UUID, HubSession> sessions = new LinkedHashMap<>();
    private final Set<UUID> announced = new HashSet<>();
    private final Map<String, LiveStream> streams = new LinkedHashMap<>();
    @Nullable private HubListener listener;
    private long lastFlushMs = System.currentTimeMillis();
    private long lastHousekeepingMs = System.currentTimeMillis();
    private final Map<UUID, Long> lastRateWarn = new HashMap<>();

    private SocialHub(final MinecraftServer server, final MultiplayerServerConfig cfg) {
        this.server = server;
        this.cfg = cfg;
        this.store = new HubStore(server.getServerDirectory().toAbsolutePath().resolve("slate-hub"), cfg.historyCap);
        this.blobs = new HubBlobRelay(this, cfg);
        this.hubName = cfg.effectiveHubName(server.getMotd());
    }

    // ------------------------------------------------------------------ lifecycle (Core events)

    @Nullable public static SocialHub current() { return current; }

    public static void onServerStarted(final MinecraftServer server) {
        final MultiplayerServerConfig cfg = MultiplayerConfigs.server();
        if (!cfg.enabled) { SlateMultiplayer.LOGGER.info("[Slate Multiplayer] hub disabled by config"); return; }
        BlobRelay.maxBlobBytes = Math.max(64 * 1024, cfg.blobRelayMaxKb * 1024);
        BlobRelay.maxChunksPerSecond = Math.max(10, cfg.blobRelayChunksPerSecond);
        BlobRelay.maxConcurrentTransfers = Math.max(1, cfg.blobRelayConcurrent);
        final SocialHub hub = new SocialHub(server, cfg);
        current = hub;
        final boolean dedicated = SlatePlatform.get().isDedicatedServer() || server.isDedicatedServer();
        if (cfg.listen && (dedicated || cfg.listenOnIntegrated)) {
            hub.listener = new HubListener(hub, server, cfg);
            if (!hub.listener.start()) hub.listener = null;
        }
        SlateMultiplayer.LOGGER.info("[Slate Multiplayer] hub '{}' ready at {} ({})", hub.hubName, hub.store.root(),
            hub.listener != null ? "listening on " + cfg.bindAddress + ":" + cfg.port : "payload channel only");
    }

    public static void onServerStopping(final MinecraftServer server) {
        final SocialHub hub = current;
        if (hub == null) return;
        current = null;
        hub.shutdown();
    }

    public static void onServerTick(final MinecraftServer server) {
        final SocialHub hub = current;
        if (hub != null) hub.tick();
    }

    public static void onPlayerLeft(final ServerPlayer player) {
        final SocialHub hub = current;
        if (hub == null) return;
        final HubSession s = hub.sessions.get(player.getUUID());
        if (s instanceof PlayerSession ps && ps.player() == player) hub.closed(s);
    }

    /** The {@code slate:social} payload handler (server side). */
    public static void onPayload(final SocialMessage m, final ServerPlayer sender) {
        final SocialHub hub = current;
        if (hub == null || sender == null) return;
        final HubSession existing = hub.sessions.get(sender.getUUID());
        if (existing instanceof PlayerSession ps && ps.player() == sender) {
            hub.handle(existing, m);
            return;
        }
        if (m instanceof Hello) {
            hub.open(new PlayerSession(sender, hub.cfg));
            return;
        }
        // Anything before Hello: tell the client to introduce itself.
        SlateNetwork.get().sendToPlayer(sender, new SocialPayload(new Error(Error.UNAUTHORIZED, "say hello first", "session")));
    }

    /** Core blob routers for social targets on the in-game channel: players on this server only. */
    public static void registerBlobRouters() {
        final BlobRouter router = (sender, start) -> {
            final SocialHub hub = current;
            if (hub == null) return List.of();
            final HubSession s = hub.sessions.get(sender.getUUID());
            if (s == null) return List.of();
            final List<ServerPlayer> out = new ArrayList<>();
            for (final HubSession r : hub.blobRecipients(s, start.target())) {
                if (r instanceof PlayerSession ps && ps.isOpen()) out.add(ps.player());
            }
            return out;
        };
        BlobRelay.registerRouter("p:", router);
        BlobRelay.registerRouter("g:", router);
        BlobRelay.registerRouter("s:", router);
    }

    // ------------------------------------------------------------------ accessors

    public MinecraftServer server() { return server; }

    public MultiplayerServerConfig config() { return cfg; }

    public HubStore store() { return store; }

    public String hubName() { return hubName; }

    public int sessionCount() { return sessions.size(); }

    private static long now() { return System.currentTimeMillis(); }

    static String newId() {
        return "%08x%08x".formatted(RANDOM.nextInt(), RANDOM.nextInt());
    }

    private void shutdown() {
        for (final HubSession s : new ArrayList<>(sessions.values())) {
            try { s.close("hub shutting down"); } catch (final Exception ignored) {}
        }
        sessions.clear();
        announced.clear();
        streams.clear();
        if (listener != null) { listener.stop(); listener = null; }
        store.flush();
    }

    private void tick() {
        final long now = now();
        blobs.tick(now);
        if (now - lastFlushMs > 5000) {
            lastFlushMs = now;
            if (store.isDirty()) store.flush();
        }
        if (now - lastHousekeepingMs > 30_000) {
            lastHousekeepingMs = now;
            store.evictIdle(now, 10 * 60_000L);
            for (final HubSession s : new ArrayList<>(sessions.values())) {
                if (!s.isOpen()) { closed(s); continue; }
                if (!s.isPayload() && now - s.lastActivityMs() > Math.max(30, cfg.idleTimeoutSeconds) * 1000L + 30_000L) {
                    s.close("idle");
                    closed(s);
                }
            }
        }
    }

    // ------------------------------------------------------------------ sessions

    /** An authenticated session is ready: replaces any older one for the same player and syncs it. */
    void open(final HubSession s) {
        final HubSession old = sessions.get(s.uuid());
        if (old != null && old != s) {
            sessions.remove(s.uuid());
            old.close("replaced by a newer connection");
        }
        sessions.put(s.uuid(), s);
        final PlayerRecord me = store.player(s.uuid());
        if (!s.name().isEmpty() && !s.name().equals(me.name)) { me.name = s.name(); store.dirty(me); }
        store.setName(s.uuid(), s.name());
        me.lastSeenMs = now();
        store.dirty(me);
        s.setPresence(new Presence(Presence.State.MENU, "", "", me.status, now()));
        s.send(new Welcome(hubName, s.ref(), cfg.onlineMode, now()));
        s.send(snapshot(me));
        SlateMultiplayer.LOGGER.info("[Slate Multiplayer] {} joined the hub ({})", s.name(), s.isPayload() ? "in-game" : "tcp");
    }

    void closed(final HubSession s) {
        if (sessions.get(s.uuid()) != s) return;
        sessions.remove(s.uuid());
        blobs.onSessionClosed(s.uuid());
        final PlayerRecord me = store.player(s.uuid());
        me.lastSeenMs = now();
        store.dirty(me);
        if (announced.remove(s.uuid())) fanoutPresence(me, Presence.offline(me.lastSeenMs));
        // Streams: stop what they own, leave what they watch.
        for (final LiveStream ls : new ArrayList<>(streams.values())) {
            if (ls.info.owner().uuid().equals(s.uuid())) endStream(ls);
            else if (ls.viewers.remove(s.uuid())) notifyStream(ls);
        }
        SlateMultiplayer.LOGGER.info("[Slate Multiplayer] {} left the hub", s.name());
    }

    @Nullable private HubSession session(final UUID uuid) {
        final HubSession s = sessions.get(uuid);
        return s != null && s.isOpen() ? s : null;
    }

    private void sendTo(final UUID uuid, final SocialMessage m) {
        final HubSession s = session(uuid);
        if (s != null) s.send(m);
    }

    // ------------------------------------------------------------------ view builders

    private PlayerRef ref(final UUID uuid) {
        final HubSession s = sessions.get(uuid);
        return new PlayerRef(uuid, s != null ? s.name() : store.name(uuid));
    }

    private Presence presenceOf(final UUID uuid) {
        final HubSession s = session(uuid);
        if (s != null && announced.contains(uuid)) return s.presence();
        return Presence.offline(store.playerExists(uuid) ? store.player(uuid).lastSeenMs : 0L);
    }

    private FriendInfo friendInfo(final PlayerRecord viewer, final UUID target) {
        final String key = target.toString();
        return new FriendInfo(ref(target), viewer.nicknames.getOrDefault(key, ""), viewer.notes.getOrDefault(key, ""),
            presenceOf(target), viewer.friendsSince.getOrDefault(key, 0L));
    }

    private List<RequestInfo> requests(final Map<String, Long> map) {
        final List<RequestInfo> out = new ArrayList<>(map.size());
        map.forEach((u, at) -> { try { out.add(new RequestInfo(ref(UUID.fromString(u)), at)); } catch (final IllegalArgumentException ignored) {} });
        return out;
    }

    private GroupInfo groupInfo(final GroupRecord g) {
        final List<PlayerRef> members = new ArrayList<>(g.members.size());
        for (final String m : g.members) { try { members.add(ref(UUID.fromString(m))); } catch (final IllegalArgumentException ignored) {} }
        UUID owner = PlayerRef.NIL;
        try { owner = UUID.fromString(g.owner); } catch (final IllegalArgumentException ignored) {}
        return new GroupInfo(g.id, g.name, owner, members, g.voiceGroup, g.createdMs);
    }

    private Snapshot snapshot(final PlayerRecord me) {
        final UUID self = me.id();
        final List<FriendInfo> friends = new ArrayList<>();
        final List<ThreadSummary> threads = new ArrayList<>();
        for (final String f : me.friends) {
            final UUID fu;
            try { fu = UUID.fromString(f); } catch (final IllegalArgumentException e) { continue; }
            final FriendInfo info = friendInfo(me, fu);
            friends.add(info);
            final String key = Threads.dm(self, fu);
            if (store.hasHistory(key)) {
                final HistoryLog h = store.history(key);
                threads.add(new ThreadSummary(key, info.display(), h.last(), h.unreadSince(me.readMarkers.getOrDefault(key, 0L), self)));
            }
        }
        final List<PlayerRef> blocked = new ArrayList<>();
        for (final String b : me.blocked) { try { blocked.add(ref(UUID.fromString(b))); } catch (final IllegalArgumentException ignored) {} }
        final List<GroupInfo> groups = new ArrayList<>();
        for (final String gid : new ArrayList<>(me.groups)) {
            final GroupRecord g = store.group(gid);
            if (g == null || !g.members.contains(me.uuid)) { me.groups.remove(gid); store.dirty(me); continue; }
            groups.add(groupInfo(g));
            final String key = Threads.group(gid);
            final HistoryLog h = store.hasHistory(key) ? store.history(key) : null;
            threads.add(new ThreadSummary(key, g.name, h == null ? null : h.last(), h == null ? 0 : h.unreadSince(me.readMarkers.getOrDefault(key, 0L), self)));
        }
        final List<GroupInvite> invites = new ArrayList<>();
        for (final Map.Entry<String, InviteRecord> e : new ArrayList<>(me.groupInvites.entrySet())) {
            final GroupRecord g = store.group(e.getKey());
            if (g == null) { me.groupInvites.remove(e.getKey()); store.dirty(me); continue; }
            UUID from = PlayerRef.NIL;
            try { from = UUID.fromString(e.getValue().from); } catch (final IllegalArgumentException ignored) {}
            invites.add(new GroupInvite(groupInfo(g), ref(from), e.getValue().atMs));
        }
        final List<StreamInfo> live = new ArrayList<>();
        for (final LiveStream ls : streams.values()) {
            if (me.isFriend(ls.info.owner().uuid()) || ls.info.owner().uuid().equals(self)) live.add(ls.snapshot());
        }
        return new Snapshot(new PlayerRef(self, me.name), friends, requests(me.requestsIn), requests(me.requestsOut), blocked, groups, invites, threads, live);
    }

    // ------------------------------------------------------------------ dispatch

    void handle(final HubSession s, final SocialMessage m) {
        s.touch(now());
        if (!(m instanceof Ping) && !(m instanceof BlobPassthrough) && !(m instanceof StreamFrame) && !(m instanceof Typing) && !s.requests().tryAcquire()) {
            rateWarn(s, "requests");
            return;
        }
        try {
            dispatch(s, m);
        } catch (final Exception e) {
            SlateMultiplayer.LOGGER.error("[Slate Multiplayer] hub failed handling {} from {}", m.getClass().getSimpleName(), s.name(), e);
            s.send(new Error(Error.INTERNAL, "hub error: " + e.getClass().getSimpleName(), m.getClass().getSimpleName()));
        }
    }

    private void rateWarn(final HubSession s, final String what) {
        final long now = now();
        final Long last = lastRateWarn.get(s.uuid());
        if (last != null && now - last < 1000) return;
        lastRateWarn.put(s.uuid(), now);
        s.send(new Error(Error.TOO_MANY, "slow down", what));
    }

    private void dispatch(final HubSession s, final SocialMessage m) {
        final PlayerRecord me = store.player(s.uuid());
        switch (m) {
            case Hello h -> s.send(new Ack("hello", hubName));
            case Ping p -> s.send(new Pong(p.sentMs()));
            case Pong p -> {}
            case PresenceSet p -> onPresence(s, me, p.presence());
            case FriendRequest r -> onFriendRequest(s, me, r);
            case FriendAccept a -> onFriendAccept(s, me, a.target());
            case FriendDecline d -> onFriendDecline(s, me, d.target());
            case FriendCancel c -> onFriendCancel(s, me, c.target());
            case FriendRemove r -> onFriendRemove(s, me, r.target());
            case FriendBlock b -> onFriendBlock(s, me, b);
            case NicknameSet n -> onNickname(s, me, n);
            case Chat c -> onChat(s, me, c);
            case Typing t -> onTyping(s, me, t);
            case HistoryRequest h -> onHistory(s, me, h);
            case MarkRead r -> onMarkRead(me, r);
            case MediaRequest r -> onMediaRequest(s, me, r);
            case GroupCreate g -> onGroupCreate(s, me, g.name());
            case GroupInviteSend g -> onGroupInvite(s, me, g);
            case GroupJoin g -> onGroupJoin(s, me, g);
            case GroupLeave g -> onGroupLeave(s, me, g.groupId());
            case GroupRename g -> onGroupRename(s, me, g);
            case GroupKick g -> onGroupKick(s, me, g);
            case GroupVoiceSet g -> onGroupVoice(s, me, g);
            case Invite i -> onInvite(s, me, i);
            case StreamStart st -> onStreamStart(s, me, st.title());
            case StreamStop st -> onStreamStop(s, st.streamId());
            case StreamWatch w -> onStreamWatch(s, me, w);
            case StreamFrame f -> onStreamFrame(s, f);
            case BlobPassthrough b -> {
                final CustomPacketPayload p = BlobFrames.unwrap(b);
                if (p != null) blobs.onPayload(s, p);
            }
            default -> s.send(new Error(Error.BAD_REQUEST, "not a client message", m.getClass().getSimpleName()));
        }
    }

    // ------------------------------------------------------------------ presence

    private void onPresence(final HubSession s, final PlayerRecord me, final Presence wanted) {
        final Presence p = new Presence(wanted.state(), wanted.server(), wanted.dimension(), limit(wanted.status(), 160), now());
        final boolean first = announced.add(s.uuid());
        final boolean changed = !p.sameActivity(s.presence());
        s.setPresence(changed ? p : s.presence());
        if (!p.status().equals(me.status)) { me.status = p.status(); store.dirty(me); }
        if (first || changed) fanoutPresence(me, s.presence());
    }

    private void fanoutPresence(final PlayerRecord who, final Presence p) {
        final PresenceUpdate update = new PresenceUpdate(who.id(), p);
        for (final String f : who.friends) {
            try { sendTo(UUID.fromString(f), update); } catch (final IllegalArgumentException ignored) {}
        }
    }

    // ------------------------------------------------------------------ friends

    private void resolveName(final String name, final Consumer<UUID> then) {
        final String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 16) { then.accept(null); return; }
        final UUID known = store.findByName(n);
        if (known != null) { then.accept(known); return; }
        final ServerPlayer online = server.getPlayerList().getPlayerByName(n);
        if (online != null) { then.accept(online.getUUID()); return; }
        try {
            server.getProfileCache().getAsync(n).whenComplete((opt, err) -> server.execute(() -> {
                if (current != this) return;
                then.accept(opt != null && opt.isPresent() ? opt.get().getId() : null);
            }));
        } catch (final Exception e) {
            then.accept(null);
        }
    }

    private void onFriendRequest(final HubSession s, final PlayerRecord me, final FriendRequest r) {
        if (SocialCodec.isNil(r.target())) {
            resolveName(r.targetName(), uuid -> {
                if (uuid == null) { s.send(new Error(Error.NOT_FOUND, "no player called " + limit(r.targetName(), 16), "friend_request")); return; }
                if (store.name(uuid).isEmpty() && !r.targetName().isBlank()) store.setName(uuid, limit(r.targetName().trim(), 16));
                friendRequest(s, me, uuid);
            });
            return;
        }
        if (!r.targetName().isBlank() && store.name(r.target()).isEmpty()) store.setName(r.target(), limit(r.targetName().trim(), 16));
        friendRequest(s, me, r.target());
    }

    private void friendRequest(final HubSession s, final PlayerRecord me, final UUID target) {
        if (target.equals(me.id())) { s.send(new Error(Error.BAD_REQUEST, "that is you", "friend_request")); return; }
        final PlayerRecord them = store.player(target);
        if (me.hasBlocked(target)) { s.send(new Error(Error.FORBIDDEN, "you blocked this player", "friend_request")); return; }
        if (them.hasBlocked(me.id())) { s.send(new Error(Error.FORBIDDEN, "request not possible", "friend_request")); return; }
        if (me.isFriend(target)) { s.send(new Error(Error.CONFLICT, "already friends", "friend_request")); return; }
        if (me.requestsOut.containsKey(them.uuid)) { s.send(new Error(Error.CONFLICT, "request already sent", "friend_request")); return; }
        if (me.friends.size() >= cfg.maxFriends) { s.send(new Error(Error.FORBIDDEN, "friend list full", "friend_request")); return; }
        if (me.requestsIn.containsKey(them.uuid)) { befriend(me, them); return; }     // mutual: they asked first
        if (me.requestsOut.size() >= cfg.maxPendingRequests || them.requestsIn.size() >= cfg.maxPendingRequests) {
            s.send(new Error(Error.TOO_MANY, "too many pending requests", "friend_request"));
            return;
        }
        final long at = now();
        me.requestsOut.put(them.uuid, at);
        them.requestsIn.put(me.uuid, at);
        store.dirty(me);
        store.dirty(them);
        s.send(new RequestsUpdate(requests(me.requestsIn), requests(me.requestsOut)));
        s.send(new Ack("friend_request", them.uuid));
        sendTo(target, new FriendRequestIn(new RequestInfo(ref(me.id()), at)));
        sendTo(target, new RequestsUpdate(requests(them.requestsIn), requests(them.requestsOut)));
    }

    private void befriend(final PlayerRecord a, final PlayerRecord b) {
        final long at = now();
        a.requestsIn.remove(b.uuid); a.requestsOut.remove(b.uuid);
        b.requestsIn.remove(a.uuid); b.requestsOut.remove(a.uuid);
        if (!a.friends.contains(b.uuid)) { a.friends.add(b.uuid); a.friendsSince.put(b.uuid, at); }
        if (!b.friends.contains(a.uuid)) { b.friends.add(a.uuid); b.friendsSince.put(a.uuid, at); }
        store.dirty(a);
        store.dirty(b);
        sendTo(a.id(), new RequestsUpdate(requests(a.requestsIn), requests(a.requestsOut)));
        sendTo(a.id(), new FriendUpdate(friendInfo(a, b.id())));
        sendTo(b.id(), new RequestsUpdate(requests(b.requestsIn), requests(b.requestsOut)));
        sendTo(b.id(), new FriendUpdate(friendInfo(b, a.id())));
        // Their streams become visible to each other.
        for (final LiveStream ls : streams.values()) {
            if (ls.info.owner().uuid().equals(a.id())) sendTo(b.id(), new StreamUpdate(ls.snapshot()));
            if (ls.info.owner().uuid().equals(b.id())) sendTo(a.id(), new StreamUpdate(ls.snapshot()));
        }
    }

    private void onFriendAccept(final HubSession s, final PlayerRecord me, final UUID target) {
        if (!me.requestsIn.containsKey(target.toString())) { s.send(new Error(Error.NOT_FOUND, "no such request", "friend_accept")); return; }
        if (me.friends.size() >= cfg.maxFriends) { s.send(new Error(Error.FORBIDDEN, "friend list full", "friend_accept")); return; }
        befriend(me, store.player(target));
    }

    private void onFriendDecline(final HubSession s, final PlayerRecord me, final UUID target) {
        final PlayerRecord them = store.player(target);
        me.requestsIn.remove(them.uuid);
        them.requestsOut.remove(me.uuid);
        store.dirty(me);
        store.dirty(them);
        s.send(new RequestsUpdate(requests(me.requestsIn), requests(me.requestsOut)));
        sendTo(target, new RequestsUpdate(requests(them.requestsIn), requests(them.requestsOut)));
    }

    private void onFriendCancel(final HubSession s, final PlayerRecord me, final UUID target) {
        final PlayerRecord them = store.player(target);
        me.requestsOut.remove(them.uuid);
        them.requestsIn.remove(me.uuid);
        store.dirty(me);
        store.dirty(them);
        s.send(new RequestsUpdate(requests(me.requestsIn), requests(me.requestsOut)));
        sendTo(target, new RequestsUpdate(requests(them.requestsIn), requests(them.requestsOut)));
    }

    private void unfriend(final PlayerRecord a, final PlayerRecord b, final String reason) {
        final boolean were = a.friends.remove(b.uuid) | b.friends.remove(a.uuid);
        a.friendsSince.remove(b.uuid); b.friendsSince.remove(a.uuid);
        a.requestsIn.remove(b.uuid); a.requestsOut.remove(b.uuid);
        b.requestsIn.remove(a.uuid); b.requestsOut.remove(a.uuid);
        store.dirty(a);
        store.dirty(b);
        if (were) {
            sendTo(a.id(), new FriendRemoved(b.id(), reason));
            sendTo(b.id(), new FriendRemoved(a.id(), "removed"));
        }
        sendTo(a.id(), new RequestsUpdate(requests(a.requestsIn), requests(a.requestsOut)));
        sendTo(b.id(), new RequestsUpdate(requests(b.requestsIn), requests(b.requestsOut)));
    }

    private void onFriendRemove(final HubSession s, final PlayerRecord me, final UUID target) {
        unfriend(me, store.player(target), "removed");
    }

    private void onFriendBlock(final HubSession s, final PlayerRecord me, final FriendBlock b) {
        if (!b.block()) {
            if (me.blocked.remove(b.target().toString())) store.dirty(me);
            s.send(new BlockedUpdate(blockedOf(me)));
            return;
        }
        final Consumer<UUID> block = target -> {
            if (target == null) { s.send(new Error(Error.NOT_FOUND, "no player called " + limit(b.name(), 16), "block")); return; }
            if (target.equals(me.id())) return;
            if (me.blocked.size() >= cfg.maxBlocked) { s.send(new Error(Error.FORBIDDEN, "block list full", "block")); return; }
            if (!b.name().isBlank() && store.name(target).isEmpty()) store.setName(target, limit(b.name().trim(), 16));
            unfriend(me, store.player(target), "removed");
            if (!me.blocked.contains(target.toString())) me.blocked.add(target.toString());
            store.dirty(me);
            s.send(new BlockedUpdate(blockedOf(me)));
        };
        if (SocialCodec.isNil(b.target())) resolveName(b.name(), block); else block.accept(b.target());
    }

    private List<PlayerRef> blockedOf(final PlayerRecord me) {
        final List<PlayerRef> out = new ArrayList<>();
        for (final String u : me.blocked) { try { out.add(ref(UUID.fromString(u))); } catch (final IllegalArgumentException ignored) {} }
        return out;
    }

    private void onNickname(final HubSession s, final PlayerRecord me, final NicknameSet n) {
        if (!me.isFriend(n.target())) { s.send(new Error(Error.NOT_FOUND, "not a friend", "nickname")); return; }
        final String key = n.target().toString();
        final String nick = limit(n.nick().trim(), 32), note = limit(n.note().trim(), 200);
        if (nick.isEmpty()) me.nicknames.remove(key); else me.nicknames.put(key, nick);
        if (note.isEmpty()) me.notes.remove(key); else me.notes.put(key, note);
        store.dirty(me);
        s.send(new FriendUpdate(friendInfo(me, n.target())));
    }

    // ------------------------------------------------------------------ messaging

    /** Members of a thread this player may use; empty when the key is invalid or they are not a member. */
    private List<UUID> threadMembers(final PlayerRecord me, final String key) {
        if (Threads.isDm(key)) {
            final UUID other = Threads.dmOther(key, me.id());
            if (other == null || !me.isFriend(other)) return List.of();
            return List.of(me.id(), other);
        }
        final String gid = Threads.groupId(key);
        if (gid == null) return List.of();
        final GroupRecord g = store.group(gid);
        if (g == null || !g.members.contains(me.uuid)) return List.of();
        final List<UUID> out = new ArrayList<>(g.members.size());
        for (final String u : g.members) { try { out.add(UUID.fromString(u)); } catch (final IllegalArgumentException ignored) {} }
        return out;
    }

    private void onChat(final HubSession s, final PlayerRecord me, final Chat c) {
        if (!s.chat().tryAcquire()) { rateWarn(s, "chat"); return; }
        final List<UUID> members = threadMembers(me, c.threadKey());
        if (members.isEmpty()) { s.send(new Error(Error.FORBIDDEN, "not your thread", "chat")); return; }
        final ChatMessage in = c.message();
        final String text = limit(in.text().strip(), ChatMessage.MAX_TEXT);
        final boolean attach = !in.attachKind().isEmpty() && BlobPayloads.validId(in.attachId()) && isAttachKind(in.attachKind());
        if (text.isEmpty() && !attach) { s.send(new Error(Error.BAD_REQUEST, "empty message", "chat")); return; }
        final ChatMessage msg = new ChatMessage(newId(), s.ref(), text, attach ? in.attachKind() : "", attach ? in.attachId() : "",
            attach ? limit(in.attachMeta(), 500) : "", now());
        store.history(c.threadKey()).append(msg);
        me.readMarkers.put(c.threadKey(), msg.atMs());
        store.dirty(me);
        final Chat out = new Chat(c.threadKey(), msg);
        for (final UUID u : members) sendTo(u, out);
    }

    private static boolean isAttachKind(final String k) {
        return "image".equals(k) || "gif".equals(k) || "voice".equals(k) || "video".equals(k);
    }

    private void onTyping(final HubSession s, final PlayerRecord me, final Typing t) {
        final List<UUID> members = threadMembers(me, t.threadKey());
        final Typing out = new Typing(t.threadKey(), me.id(), t.typing());
        for (final UUID u : members) if (!u.equals(me.id())) sendTo(u, out);
    }

    private void onHistory(final HubSession s, final PlayerRecord me, final HistoryRequest h) {
        if (threadMembers(me, h.threadKey()).isEmpty()) { s.send(new Error(Error.FORBIDDEN, "not your thread", "history")); return; }
        if (!store.hasHistory(h.threadKey())) { s.send(new History(h.threadKey(), List.of(), false)); return; }
        final boolean[] more = new boolean[1];
        final List<ChatMessage> page = store.history(h.threadKey()).page(h.beforeMs(), Math.max(1, Math.min(100, h.limit())), more);
        s.send(new History(h.threadKey(), page, more[0]));
    }

    private void onMarkRead(final PlayerRecord me, final MarkRead r) {
        if (threadMembers(me, r.threadKey()).isEmpty()) return;
        final long upTo = Math.min(r.upToMs(), now());
        final Long old = me.readMarkers.get(r.threadKey());
        if (old == null || old < upTo) { me.readMarkers.put(r.threadKey(), upTo); store.dirty(me); }
    }

    private void onMediaRequest(final HubSession s, final PlayerRecord me, final MediaRequest r) {
        if (!BlobPayloads.validId(r.blobId())) return;
        final HubStore.MediaMeta meta = store.mediaMeta(r.blobId());
        if (meta == null) { s.send(new Error(Error.NOT_FOUND, "media expired", "media")); return; }
        // Only the sender, the DM partner or a group member may fetch it.
        boolean allowed = me.uuid.equals(meta.sender);
        if (!allowed && meta.target.startsWith("p:")) allowed = meta.target.substring(2).equals(me.uuid);
        if (!allowed && meta.target.startsWith("g:")) {
            final GroupRecord g = store.group(meta.target.substring(2));
            allowed = g != null && g.members.contains(me.uuid);
        }
        if (!allowed) { s.send(new Error(Error.FORBIDDEN, "not yours", "media")); return; }
        final byte[] bytes = store.mediaBytes(r.blobId());
        if (bytes == null) { s.send(new Error(Error.NOT_FOUND, "media expired", "media")); return; }
        blobs.sendStored(s, meta, bytes);
    }

    // ------------------------------------------------------------------ groups

    private void notifyGroup(final GroupRecord g) {
        final GroupUpdate u = new GroupUpdate(groupInfo(g));
        for (final String m : g.members) { try { sendTo(UUID.fromString(m), u); } catch (final IllegalArgumentException ignored) {} }
    }

    private void onGroupCreate(final HubSession s, final PlayerRecord me, final String rawName) {
        final String name = limit(rawName.strip(), 32);
        if (name.isEmpty()) { s.send(new Error(Error.BAD_REQUEST, "name required", "group_create")); return; }
        if (me.groups.size() >= cfg.maxGroupsPerPlayer) { s.send(new Error(Error.FORBIDDEN, "too many groups", "group_create")); return; }
        final GroupRecord g = store.createGroup(newId(), name, me.id(), now());
        me.groups.add(g.id);
        store.dirty(me);
        s.send(new Ack("group_create", g.id));
        notifyGroup(g);
    }

    private void onGroupInvite(final HubSession s, final PlayerRecord me, final GroupInviteSend i) {
        final GroupRecord g = store.group(i.groupId());
        if (g == null || !g.members.contains(me.uuid)) { s.send(new Error(Error.NOT_FOUND, "no such group", "group_invite")); return; }
        if (!me.isFriend(i.target())) { s.send(new Error(Error.FORBIDDEN, "invite friends only", "group_invite")); return; }
        if (g.members.contains(i.target().toString())) { s.send(new Error(Error.CONFLICT, "already a member", "group_invite")); return; }
        if (g.members.size() >= cfg.maxGroupMembers) { s.send(new Error(Error.FORBIDDEN, "group is full", "group_invite")); return; }
        final PlayerRecord them = store.player(i.target());
        if (them.hasBlocked(me.id())) { s.send(new Error(Error.FORBIDDEN, "invite not possible", "group_invite")); return; }
        final InviteRecord rec = new InviteRecord();
        rec.from = me.uuid;
        rec.atMs = now();
        them.groupInvites.put(g.id, rec);
        store.dirty(them);
        s.send(new Ack("group_invite", g.id));
        sendTo(i.target(), new GroupInviteIn(new GroupInvite(groupInfo(g), ref(me.id()), rec.atMs)));
    }

    private void onGroupJoin(final HubSession s, final PlayerRecord me, final GroupJoin j) {
        final InviteRecord inv = me.groupInvites.remove(j.groupId());
        store.dirty(me);
        final GroupRecord g = store.group(j.groupId());
        if (!j.accept() || inv == null || g == null) {
            if (inv == null) s.send(new Error(Error.NOT_FOUND, "no such invite", "group_join"));
            else s.send(new Ack("group_join", ""));
            return;
        }
        if (g.members.size() >= cfg.maxGroupMembers || me.groups.size() >= cfg.maxGroupsPerPlayer) {
            s.send(new Error(Error.FORBIDDEN, "group is full", "group_join"));
            return;
        }
        if (!g.members.contains(me.uuid)) g.members.add(me.uuid);
        if (!me.groups.contains(g.id)) me.groups.add(g.id);
        store.dirty(g);
        store.dirty(me);
        notifyGroup(g);
    }

    private void onGroupLeave(final HubSession s, final PlayerRecord me, final String groupId) {
        final GroupRecord g = store.group(groupId);
        me.groups.remove(groupId);
        store.dirty(me);
        s.send(new GroupRemoved(groupId));
        if (g == null) return;
        g.members.remove(me.uuid);
        if (g.members.isEmpty()) { store.deleteGroup(groupId); return; }
        if (g.owner.equals(me.uuid)) g.owner = g.members.get(0);
        store.dirty(g);
        notifyGroup(g);
    }

    private void onGroupRename(final HubSession s, final PlayerRecord me, final GroupRename r) {
        final GroupRecord g = store.group(r.groupId());
        if (g == null || !g.owner.equals(me.uuid)) { s.send(new Error(Error.FORBIDDEN, "owner only", "group_rename")); return; }
        final String name = limit(r.name().strip(), 32);
        if (name.isEmpty()) { s.send(new Error(Error.BAD_REQUEST, "name required", "group_rename")); return; }
        g.name = name;
        store.dirty(g);
        notifyGroup(g);
    }

    private void onGroupKick(final HubSession s, final PlayerRecord me, final GroupKick k) {
        final GroupRecord g = store.group(k.groupId());
        if (g == null || !g.owner.equals(me.uuid)) { s.send(new Error(Error.FORBIDDEN, "owner only", "group_kick")); return; }
        if (k.target().equals(me.id())) return;
        if (!g.members.remove(k.target().toString())) return;
        final PlayerRecord them = store.player(k.target());
        them.groups.remove(g.id);
        store.dirty(them);
        store.dirty(g);
        sendTo(k.target(), new GroupRemoved(g.id));
        notifyGroup(g);
    }

    private void onGroupVoice(final HubSession s, final PlayerRecord me, final GroupVoiceSet v) {
        final GroupRecord g = store.group(v.groupId());
        if (g == null || !g.members.contains(me.uuid)) { s.send(new Error(Error.NOT_FOUND, "no such group", "group_voice")); return; }
        g.voiceGroup = limit(v.voiceGroup().strip(), 40);
        store.dirty(g);
        notifyGroup(g);
    }

    // ------------------------------------------------------------------ invites

    private void onInvite(final HubSession s, final PlayerRecord me, final Invite i) {
        if (!me.isFriend(i.target())) { s.send(new Error(Error.FORBIDDEN, "friends only", "invite")); return; }
        final HubSession target = session(i.target());
        if (target == null) { s.send(new Error(Error.NOT_FOUND, "friend is offline", "invite")); return; }
        final String kind = "voice".equals(i.kind()) ? "voice" : "server";
        target.send(new InviteIn(ref(me.id()), kind, limit(i.address(), 200), limit(i.label(), 100), now()));
        s.send(new Ack("invite", i.target().toString()));
    }

    // ------------------------------------------------------------------ streams

    private void notifyStream(final LiveStream ls) {
        final StreamUpdate u = new StreamUpdate(ls.snapshot());
        sendTo(ls.info.owner().uuid(), u);
        for (final UUID v : ls.viewers) sendTo(v, u);
    }

    private void announceStream(final LiveStream ls, final SocialMessage m) {
        final PlayerRecord owner = store.player(ls.info.owner().uuid());
        sendTo(owner.id(), m);
        for (final String f : owner.friends) { try { sendTo(UUID.fromString(f), m); } catch (final IllegalArgumentException ignored) {} }
    }

    private void endStream(final LiveStream ls) {
        streams.remove(ls.info.id());
        final StreamEnded ended = new StreamEnded(ls.info.id());
        final PlayerRecord owner = store.player(ls.info.owner().uuid());
        final Set<UUID> notified = new HashSet<>();
        notified.add(owner.id());
        for (final String f : owner.friends) { try { notified.add(UUID.fromString(f)); } catch (final IllegalArgumentException ignored) {} }
        notified.addAll(ls.viewers);
        for (final UUID u : notified) sendTo(u, ended);
    }

    private void onStreamStart(final HubSession s, final PlayerRecord me, final String rawTitle) {
        for (final LiveStream ls : new ArrayList<>(streams.values())) {
            if (ls.info.owner().uuid().equals(me.id())) endStream(ls);
        }
        final String title = limit(rawTitle.strip(), 64);
        final LiveStream ls = new LiveStream(new StreamInfo(newId(), s.ref(), title.isEmpty() ? s.name() + "'s screen" : title, 0, now()));
        streams.put(ls.info.id(), ls);
        s.send(new Ack("stream_start", ls.info.id()));
        announceStream(ls, new StreamUpdate(ls.snapshot()));
    }

    private void onStreamStop(final HubSession s, final String id) {
        final LiveStream ls = streams.get(id);
        if (ls == null || !ls.info.owner().uuid().equals(s.uuid())) return;
        endStream(ls);
    }

    private void onStreamWatch(final HubSession s, final PlayerRecord me, final StreamWatch w) {
        final LiveStream ls = streams.get(w.streamId());
        if (ls == null) { s.send(new Error(Error.NOT_FOUND, "stream ended", "stream_watch")); return; }
        if (!w.watch()) {
            if (ls.viewers.remove(me.id())) notifyStream(ls);
            return;
        }
        if (!me.isFriend(ls.info.owner().uuid())) { s.send(new Error(Error.FORBIDDEN, "friends only", "stream_watch")); return; }
        if (ls.viewers.add(me.id())) notifyStream(ls); else s.send(new StreamUpdate(ls.snapshot()));
    }

    private void onStreamFrame(final HubSession s, final StreamFrame f) {
        final LiveStream ls = streams.get(f.streamId());
        if (ls == null || !ls.info.owner().uuid().equals(s.uuid())) return;
        for (final UUID v : ls.viewers) {
            final HubSession vs = session(v);
            if (vs != null && vs.canTakeMore()) vs.send(f);
        }
    }

    // ------------------------------------------------------------------ blob routing

    /** Who receives a blob a session sends to {@code target}; empty when it is not allowed. */
    List<HubSession> blobRecipients(final HubSession sender, final String target) {
        if (target == null || target.length() < 3) return List.of();
        final PlayerRecord me = store.player(sender.uuid());
        final String rest = target.substring(2);
        if (target.startsWith("p:")) {
            final UUID other;
            try { other = UUID.fromString(rest); } catch (final IllegalArgumentException e) { return List.of(); }
            if (!me.isFriend(other) && !sharesGroup(me, other)) return List.of();
            if (store.player(other).hasBlocked(me.id())) return List.of();
            final HubSession s = session(other);
            return s == null ? List.of() : List.of(s);
        }
        if (target.startsWith("g:")) {
            final GroupRecord g = store.group(rest);
            if (g == null || !g.members.contains(me.uuid)) return List.of();
            final List<HubSession> out = new ArrayList<>();
            for (final String m : g.members) {
                if (m.equals(me.uuid)) continue;
                try { final HubSession s = session(UUID.fromString(m)); if (s != null) out.add(s); } catch (final IllegalArgumentException ignored) {}
            }
            return out;
        }
        if (target.startsWith("s:")) {
            final LiveStream ls = streams.get(rest);
            if (ls == null || !ls.info.owner().uuid().equals(sender.uuid())) return List.of();
            final List<HubSession> out = new ArrayList<>();
            for (final UUID v : ls.viewers) { final HubSession s = session(v); if (s != null) out.add(s); }
            return out;
        }
        return List.of();
    }

    private boolean sharesGroup(final PlayerRecord me, final UUID other) {
        final String o = other.toString();
        for (final String gid : me.groups) {
            final GroupRecord g = store.group(gid);
            if (g != null && g.members.contains(o)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ misc

    private static String limit(final String s, final int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
