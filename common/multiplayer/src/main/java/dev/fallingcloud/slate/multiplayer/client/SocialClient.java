package dev.fallingcloud.slate.multiplayer.client;

import dev.fallingcloud.slate.core.event.SlateEvents;
import dev.fallingcloud.slate.core.net.SlateNetwork;
import dev.fallingcloud.slate.core.net.blob.BlobPayloads;
import dev.fallingcloud.slate.core.net.blob.BlobReceiver;
import dev.fallingcloud.slate.core.net.blob.BlobSender;
import dev.fallingcloud.slate.multiplayer.MultiplayerConfig;
import dev.fallingcloud.slate.multiplayer.MultiplayerConfigs;
import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;
import dev.fallingcloud.slate.multiplayer.client.stream.ScreenShare;
import dev.fallingcloud.slate.multiplayer.client.stream.StreamViewer;
import dev.fallingcloud.slate.multiplayer.social.BlobFrames;
import dev.fallingcloud.slate.multiplayer.social.ChatMessage;
import dev.fallingcloud.slate.multiplayer.social.FriendInfo;
import dev.fallingcloud.slate.multiplayer.social.GroupInfo;
import dev.fallingcloud.slate.multiplayer.social.GroupInvite;
import dev.fallingcloud.slate.multiplayer.social.PlayerRef;
import dev.fallingcloud.slate.multiplayer.social.Presence;
import dev.fallingcloud.slate.multiplayer.social.RequestInfo;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage.*;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage.Error;
import dev.fallingcloud.slate.multiplayer.social.SocialPayload;
import dev.fallingcloud.slate.multiplayer.social.StreamInfo;
import dev.fallingcloud.slate.multiplayer.social.ThreadSummary;
import dev.fallingcloud.slate.multiplayer.social.Threads;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * The client's social state and its link to a hub. Picks the link (home hub over TCP when configured,
 * else the payload channel of the server played on), keeps the local model (friends, requests, blocked,
 * groups, threads, streams), applies every hub message, fires {@link SocialEvents}, sends presence, and
 * persists a per-hub cache so the title screen shows last-known state offline. Client main thread only,
 * except {@link #send}, which is safe from anywhere.
 */
public final class SocialClient {

    private static final SocialClient INSTANCE = new SocialClient();

    public static SocialClient get() { return INSTANCE; }

    // ---- model
    private PlayerRef self = new PlayerRef(PlayerRef.NIL, "");
    private final Map<UUID, Friend> friends = new LinkedHashMap<>();
    private final List<RequestInfo> requestsIn = new ArrayList<>();
    private final List<RequestInfo> requestsOut = new ArrayList<>();
    private final List<PlayerRef> blocked = new ArrayList<>();
    private final Map<String, GroupInfo> groups = new LinkedHashMap<>();
    private final Map<String, GroupInvite> groupInvites = new LinkedHashMap<>();
    private final Map<String, ThreadModel> threads = new LinkedHashMap<>();
    private final Map<String, StreamInfo> streams = new LinkedHashMap<>();
    private final List<InviteIn> invites = new ArrayList<>();
    private String hubName = "";
    private boolean hubOnlineMode;

    // ---- link
    @Nullable private SocialLink link;
    private LinkState state = LinkState.NONE;
    private String stateDetail = "";
    private String cacheKey = "";
    private boolean cacheDirty;
    private long cacheDirtySinceMs;
    private long lastErrorToastMs;

    // ---- presence / typing / viewing
    @Nullable private Presence lastSentPresence;
    private int presenceTick;
    private final Map<String, Long> typingSentMs = new HashMap<>();
    private String viewingThread = "";

    private SocialClient() {}

    // ------------------------------------------------------------------ bootstrap

    void init() {
        MediaStore.init();
        BlobReceiver.onStart(this::onBlobStart);
        SlateEvents.CLIENT_TICK_END.register(this::tick);
        SlateEvents.CLIENT_LEFT_SERVER.register(() -> { if (link instanceof PayloadLink) dropLink(); });
        // Identity and cache are picked up on the first tick: Minecraft may not be fully constructed yet.
    }

    private boolean started;

    private void start() {
        started = true;
        final Minecraft mc = Minecraft.getInstance();
        self = new PlayerRef(mc.getUser().getProfileId(), mc.getUser().getName());
        loadCache(MultiplayerConfigs.client().hubKey());
    }

    // ------------------------------------------------------------------ link management

    public LinkState linkState() { return state; }

    public String stateDetail() { return stateDetail; }

    public String hubName() { return hubName; }

    public boolean hubOnlineMode() { return hubOnlineMode; }

    public boolean connected() { return state == LinkState.CONNECTED && link != null && link.isConnected(); }

    public String linkDescription() { return link == null ? "" : link.describe(); }

    public long latencyMs() { return link == null ? -1 : link.latencyMs(); }

    public boolean usingHub() { return link instanceof HubLink; }

    /** Sends to the hub (no-op when there is no link). Safe from any thread. */
    public void send(final SocialMessage m) {
        final SocialLink l = link;
        if (l != null) l.send(m);
    }

    private void tick() {
        if (!started) start();
        ensureLink();
        final SocialLink l = link;
        if (l != null) l.tick();
        if (state == LinkState.CONNECTED && ++presenceTick >= 20) { presenceTick = 0; pushPresence(false); }
        if (cacheDirty && System.currentTimeMillis() - cacheDirtySinceMs > 2500) saveCache();
        if (!typingSentMs.isEmpty()) typingSentMs.values().removeIf(t -> System.currentTimeMillis() - t > 10_000);
    }

    private void ensureLink() {
        final MultiplayerConfig cfg = MultiplayerConfigs.client();
        final String host = cfg.hubHost();
        final int port = cfg.hubPort();
        if (host != null) {
            if (link instanceof HubLink h) {
                if (!h.host().equalsIgnoreCase(host) || h.port() != port) dropLink();
                else if (h.isDead()) return;            // refused for good (replaced elsewhere): wait for a manual Connect
            } else if (link instanceof PayloadLink) dropLink();
            if (link == null) {
                final HubLink h = new HubLink(host, port, this::onMessage, this::onLinkState);
                link = h;
                loadCache(cfg.hubKey());
                BlobSender.setLink(h.blobLink());
                setState(LinkState.CONNECTING, "");
            }
            return;
        }
        if (link instanceof HubLink) dropLink();
        final Minecraft mc = Minecraft.getInstance();
        final boolean inGame = mc.getConnection() != null && mc.level != null;
        if (link instanceof PayloadLink && !inGame) dropLink();
        if (link == null && inGame && SlateNetwork.get().serverHasChannel(SocialPayload.TYPE)) {
            final String address = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : (mc.hasSingleplayerServer() ? Presence.SINGLEPLAYER : "");
            final PayloadLink p = new PayloadLink(address);
            link = p;
            loadCache("server-" + address.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9.-]", "_"));
            BlobSender.setLink(p.blobLink());
            setState(LinkState.AUTHENTICATING, "");
            p.send(new Hello(self.uuid(), self.name(), SocialMessage.PROTOCOL, ""));
        }
    }

    private void dropLink() {
        final SocialLink l = link;
        link = null;
        BlobSender.setLink(null);
        if (l != null) l.close();
        if (cacheDirty) saveCache();
        goOffline();
        setState(LinkState.NONE, "");
    }

    /** Everything becomes "last known": presence offline, typing gone, streams over. */
    private void goOffline() {
        final long now = System.currentTimeMillis();
        for (final Friend f : friends.values()) if (f.online()) f.presence = Presence.offline(now);
        for (final ThreadModel t : threads.values()) t.typingUntil.clear();
        for (final String id : new ArrayList<>(streams.keySet())) StreamViewer.onEnded(id);
        streams.clear();
        ScreenShare.onLinkLost();
        lastSentPresence = null;
        SocialEvents.MODEL_CHANGED.invoke(Runnable::run);
    }

    /** Settings: reconnect to the (possibly changed) home hub right away. */
    public void reconnect() {
        if (link instanceof HubLink h) {
            if (h.isDead()) dropLink(); else h.connectNow();
        }
        ensureLink();
    }

    private void onLinkState(final LinkState s, final String detail) {
        setState(s, detail);
        if (s != LinkState.CONNECTED && state != LinkState.CONNECTED) {
            if (s == LinkState.FAILED || s == LinkState.NONE) goOffline();
        }
    }

    private void setState(final LinkState s, final String detail) {
        final boolean changed = s != state || !detail.equals(stateDetail);
        state = s;
        stateDetail = detail == null ? "" : detail;
        if (changed) SocialEvents.LINK_STATE.invoke(l -> l.accept(s));
    }

    // ------------------------------------------------------------------ inbound

    static void onPayload(final SocialMessage m) {
        INSTANCE.onMessage(m);
    }

    private void onMessage(final SocialMessage m) {
        try {
            dispatch(m);
        } catch (final Exception e) {
            SlateMultiplayer.LOGGER.error("[Slate Multiplayer] failed applying {}", m.getClass().getSimpleName(), e);
        }
    }

    private void dispatch(final SocialMessage m) {
        switch (m) {
            case HubInfo i -> {}
            case Welcome w -> {
                self = w.you();
                hubName = w.hubName();
                hubOnlineMode = w.onlineMode();
                setState(LinkState.CONNECTED, "");
                lastSentPresence = null;
                pushPresence(true);
            }
            case Error e -> onError(e);
            case Ack a -> onAck(a);
            case Ping p -> send(new Pong(p.sentMs()));
            case Pong p -> {}
            case Snapshot s -> onSnapshot(s);
            case FriendRequestIn r -> {
                requestsIn.removeIf(x -> x.ref().uuid().equals(r.request().ref().uuid()));
                requestsIn.add(r.request());
                Notifications.friendRequest(r.request());
                SocialEvents.REQUEST_RECEIVED.invoke(l -> l.accept(r.request()));
                modelChanged();
            }
            case FriendUpdate u -> {
                final Friend f = friends.get(u.friend().ref().uuid());
                if (f == null) {
                    friends.put(u.friend().ref().uuid(), new Friend(u.friend()));
                    Notifications.friendAdded(u.friend());
                } else f.apply(u.friend());
                modelChanged();
            }
            case FriendRemoved r -> {
                if (friends.remove(r.uuid()) != null) modelChanged();
            }
            case RequestsUpdate r -> {
                requestsIn.clear(); requestsIn.addAll(r.in());
                requestsOut.clear(); requestsOut.addAll(r.out());
                modelChanged();
            }
            case BlockedUpdate b -> { blocked.clear(); blocked.addAll(b.blocked()); modelChanged(); }
            case PresenceUpdate p -> onPresence(p);
            case Chat c -> onChat(c);
            case Typing t -> {
                final ThreadModel th = thread(t.threadKey());
                if (t.typing()) th.typingUntil.put(t.who(), System.currentTimeMillis() + 6000); else th.typingUntil.remove(t.who());
                SocialEvents.THREAD_CHANGED.invoke(l -> l.accept(t.threadKey()));
            }
            case History h -> {
                final ThreadModel th = thread(h.threadKey());
                th.addOlder(h.messages());
                th.more = h.more();
                th.historyPending = false;
                markCacheDirty();
                SocialEvents.THREAD_CHANGED.invoke(l -> l.accept(h.threadKey()));
            }
            case GroupInviteIn i -> {
                groupInvites.put(i.invite().group().id(), i.invite());
                Notifications.groupInvite(i.invite());
                modelChanged();
            }
            case GroupUpdate g -> {
                final boolean isNew = !groups.containsKey(g.group().id());
                groups.put(g.group().id(), g.group());
                groupInvites.remove(g.group().id());
                final ThreadModel th = thread(g.group().threadKey());
                th.title = g.group().name();
                if (isNew) Notifications.groupJoined(g.group());
                modelChanged();
            }
            case GroupRemoved g -> {
                groups.remove(g.groupId());
                groupInvites.remove(g.groupId());
                threads.remove(Threads.group(g.groupId()));
                markCacheDirty();
                modelChanged();
            }
            case InviteIn i -> {
                invites.add(0, i);
                if (invites.size() > 20) invites.remove(invites.size() - 1);
                Notifications.invite(i);
                SocialEvents.INVITE_RECEIVED.invoke(l -> l.accept(i));
                modelChanged();
            }
            case StreamUpdate s -> onStreamUpdate(s.stream());
            case StreamEnded s -> {
                final StreamInfo info = streams.remove(s.streamId());
                StreamViewer.onEnded(s.streamId());
                ScreenShare.onEnded(s.streamId());
                if (info != null) SocialEvents.STREAM_ENDED.invoke(l -> l.accept(s.streamId()));
                modelChanged();
            }
            case StreamFrame f -> StreamViewer.onFrameInfo(f);
            case BlobPassthrough b -> {
                final CustomPacketPayload p = BlobFrames.unwrap(b);
                if (p instanceof BlobPayloads.Start s) BlobReceiver.handleStart(s);
                else if (p instanceof BlobPayloads.Chunk c) BlobReceiver.handleChunk(c);
                else if (p instanceof BlobPayloads.End e) BlobReceiver.handleEnd(e);
            }
            default -> SlateMultiplayer.LOGGER.debug("[Slate Multiplayer] unexpected {} from hub", m.getClass().getSimpleName());
        }
    }

    private long lastHelloMs;

    private void onError(final Error e) {
        if ("hello".equals(e.context()) || "session".equals(e.context())) {
            // The link layer handles these on the TCP path. On the payload path "say hello first" means the
            // hub lost our session (server restart, hub re-enabled): introduce ourselves again.
            if (link instanceof PayloadLink p) {
                if (e.code() == Error.UNAUTHORIZED && System.currentTimeMillis() - lastHelloMs > 3000) {
                    lastHelloMs = System.currentTimeMillis();
                    setState(LinkState.AUTHENTICATING, "");
                    p.send(new Hello(self.uuid(), self.name(), SocialMessage.PROTOCOL, ""));
                } else if (e.code() != Error.UNAUTHORIZED) setState(LinkState.FAILED, e.message());
            }
            return;
        }
        final long now = System.currentTimeMillis();
        if (now - lastErrorToastMs > 1500) {
            lastErrorToastMs = now;
            Notifications.hubError(e);
        }
    }

    private void onAck(final Ack a) {
        switch (a.context()) {
            case "stream_start" -> ScreenShare.onStarted(a.value());
            case "group_create" -> Notifications.groupCreated(a.value());
            default -> {}
        }
    }

    private void onSnapshot(final Snapshot s) {
        self = s.self();
        final Map<UUID, Friend> old = new HashMap<>(friends);
        friends.clear();
        for (final FriendInfo fi : s.friends()) {
            final Friend f = old.get(fi.ref().uuid());
            if (f != null) { f.apply(fi); friends.put(f.uuid, f); } else friends.put(fi.ref().uuid(), new Friend(fi));
        }
        requestsIn.clear(); requestsIn.addAll(s.requestsIn());
        requestsOut.clear(); requestsOut.addAll(s.requestsOut());
        blocked.clear(); blocked.addAll(s.blocked());
        groups.clear();
        for (final GroupInfo g : s.groups()) { groups.put(g.id(), g); thread(g.threadKey()).title = g.name(); }
        groupInvites.clear();
        for (final GroupInvite gi : s.groupInvites()) groupInvites.put(gi.group().id(), gi);
        for (final ThreadSummary ts : s.threads()) {
            final ThreadModel th = thread(ts.key());
            if (!ts.title().isEmpty()) th.title = ts.title();
            th.unread = ts.key().equals(viewingThread) ? 0 : ts.unread();
            final ChatMessage before = th.last();
            if (ts.last() != null) th.add(ts.last());
            // Messages may have arrived while we were away: pull the newest page so the cache has no gap.
            if (ts.unread() > 0 || (ts.last() != null && before != null && !before.id().equals(ts.last().id()))) {
                th.historyPending = true;
                send(new HistoryRequest(ts.key(), 0L, 50));
            }
        }
        threads.keySet().removeIf(k -> Threads.isGroup(k) && !groups.containsKey(Threads.groupId(k)));
        for (final String id : new ArrayList<>(streams.keySet())) {
            if (s.streams().stream().noneMatch(x -> x.id().equals(id))) { streams.remove(id); StreamViewer.onEnded(id); }
        }
        for (final StreamInfo si : s.streams()) streams.put(si.id(), si);
        markCacheDirty();
        modelChanged();
    }

    private void onPresence(final PresenceUpdate p) {
        final Friend f = friends.get(p.uuid());
        if (f == null) return;
        final boolean wasOnline = f.online();
        f.presence = p.presence();
        if (!wasOnline && f.online()) Notifications.friendOnline(f);
        SocialEvents.FRIEND_PRESENCE.invoke(l -> l.accept(f));
        modelChanged();
    }

    private void onChat(final Chat c) {
        final ThreadModel th = thread(c.threadKey());
        final ChatMessage m = c.message();
        if (!th.add(m)) return;
        th.trim(Math.max(50, MultiplayerConfigs.client().cacheMessagesPerThread * 3));
        final boolean own = m.from().uuid().equals(self.uuid());
        if (!own) {
            if (c.threadKey().equals(viewingThread)) send(new MarkRead(c.threadKey(), m.atMs()));
            else { th.unread++; Notifications.message(th, m); }
        }
        if (th.title.isEmpty()) th.title = threadTitle(th);
        markCacheDirty();
        SocialEvents.MESSAGE.invoke(l -> l.onMessage(c.threadKey(), m, own));
        SocialEvents.THREAD_CHANGED.invoke(l -> l.accept(c.threadKey()));
        modelChanged();
    }

    private void onStreamUpdate(final StreamInfo info) {
        final boolean isNew = !streams.containsKey(info.id());
        streams.put(info.id(), info);
        if (info.owner().uuid().equals(self.uuid())) ScreenShare.onUpdate(info);
        else {
            StreamViewer.onUpdate(info);
            if (isNew) { Notifications.streamStarted(info); SocialEvents.STREAM_STARTED.invoke(l -> l.accept(info)); }
        }
        modelChanged();
    }

    private void onBlobStart(final BlobPayloads.Start start) {
        if ("frame".equals(start.kind())) StreamViewer.onFrameStart(start);
    }

    private void modelChanged() {
        SocialEvents.MODEL_CHANGED.invoke(Runnable::run);
    }

    // ------------------------------------------------------------------ presence

    private Presence computePresence() {
        final MultiplayerConfig cfg = MultiplayerConfigs.client();
        if (cfg.invisible) return new Presence(Presence.State.OFFLINE, "", "", "", 0);
        final Minecraft mc = Minecraft.getInstance();
        final boolean inGame = mc.level != null;
        Presence.State state = inGame ? Presence.State.IN_GAME : Presence.State.MENU;
        if (cfg.away) state = Presence.State.AWAY;
        String server = "";
        if (inGame && cfg.shareServer) {
            server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : (mc.hasSingleplayerServer() ? Presence.SINGLEPLAYER : "");
        }
        final String dim = inGame && cfg.shareDimension && mc.level != null ? mc.level.dimension().location().getPath() : "";
        return new Presence(state, server == null ? "" : server, dim, cfg.statusText == null ? "" : cfg.statusText.strip(), 0);
    }

    private void pushPresence(final boolean force) {
        if (!connected()) return;
        final Presence p = computePresence();
        if (!force && p.sameActivity(lastSentPresence)) return;
        lastSentPresence = p;
        send(new PresenceSet(p));
    }

    /** Settings changed (status text, privacy): resend presence. */
    public void presenceChanged() { pushPresence(true); }

    // ------------------------------------------------------------------ model accessors

    public PlayerRef self() { return self; }

    public UUID selfUuid() { return self.uuid(); }

    public Collection<Friend> friends() { return friends.values(); }

    @Nullable public Friend friend(final UUID uuid) { return friends.get(uuid); }

    public boolean isFriend(final UUID uuid) { return friends.containsKey(uuid); }

    public int onlineFriends() {
        int n = 0;
        for (final Friend f : friends.values()) if (f.online()) n++;
        return n;
    }

    /** Friends whose presence says they are on {@code address} (for Menu's "friends here" chip). */
    public List<Friend> friendsOn(final String address) {
        final List<Friend> out = new ArrayList<>();
        if (address == null || address.isBlank()) return out;
        final String a = address.trim().toLowerCase(java.util.Locale.ROOT);
        for (final Friend f : friends.values()) {
            final String s = f.presence == null ? "" : f.presence.server().toLowerCase(java.util.Locale.ROOT);
            if (!s.isEmpty() && (s.equals(a) || s.equals(a + ":25565") || (s + ":25565").equals(a))) out.add(f);
        }
        return out;
    }

    public List<RequestInfo> requestsIn() { return requestsIn; }

    public List<RequestInfo> requestsOut() { return requestsOut; }

    public List<PlayerRef> blocked() { return blocked; }

    public Collection<GroupInfo> groups() { return groups.values(); }

    @Nullable public GroupInfo group(final String id) { return groups.get(id); }

    public Collection<GroupInvite> groupInvites() { return groupInvites.values(); }

    /** Open threads (a List: the Chat module resolves this reflectively with a List return type). */
    public List<ThreadModel> threads() { return List.copyOf(threads.values()); }

    /** The thread for a key, created on first use. */
    public ThreadModel thread(final String key) {
        return threads.computeIfAbsent(key, k -> {
            final ThreadModel t = new ThreadModel(k);
            t.title = threadTitle(t);
            return t;
        });
    }

    public ThreadModel dmThread(final UUID other) { return thread(Threads.dm(self.uuid(), other)); }

    public String threadTitle(final ThreadModel t) {
        if (t.isGroup()) {
            final GroupInfo g = groups.get(t.groupId());
            return g != null ? g.name() : (t.title.isEmpty() ? "Group" : t.title);
        }
        final UUID other = t.other(self.uuid());
        final Friend f = other == null ? null : friends.get(other);
        if (f != null) return f.display();
        final ChatMessage last = t.last();
        if (other != null && last != null && last.from().uuid().equals(other)) return last.from().display();
        return t.title.isEmpty() ? "Direct message" : t.title;
    }

    public int unreadTotal() {
        int n = 0;
        for (final ThreadModel t : threads.values()) n += t.unread;
        return n;
    }

    public Collection<StreamInfo> streams() { return streams.values(); }

    @Nullable public StreamInfo stream(final String id) { return streams.get(id); }

    public List<InviteIn> invites() { return invites; }

    /** The thread currently open in a UI (its messages count as read, no toasts). */
    public void setViewing(@Nullable final String threadKey) {
        viewingThread = threadKey == null ? "" : threadKey;
        if (!viewingThread.isEmpty()) markRead(viewingThread);
    }

    public String viewing() { return viewingThread; }

    // ------------------------------------------------------------------ actions

    public void sendFriendRequest(final UUID target, final String name) {
        send(new FriendRequest(target == null ? PlayerRef.NIL : target, name == null ? "" : name));
    }

    /** Resolves a name (player list, Mojang, then the hub) and sends the request. */
    public void sendFriendRequestByName(final String name, final Consumer<String> feedback) {
        final String n = name == null ? "" : name.trim();
        if (!ProfileLookup.validName(n)) { feedback.accept("Enter a player name"); return; }
        if (n.equalsIgnoreCase(self.name())) { feedback.accept("That is you"); return; }
        ProfileLookup.resolve(n).thenAccept(opt -> Minecraft.getInstance().execute(() -> {
            if (opt.isPresent()) sendFriendRequest(opt.get().uuid(), opt.get().name());
            else sendFriendRequest(PlayerRef.NIL, n);
            feedback.accept("Request sent to " + (opt.map(PlayerRef::name).orElse(n)));
        }));
    }

    public void acceptRequest(final UUID who) { send(new FriendAccept(who)); }

    public void declineRequest(final UUID who) { send(new FriendDecline(who)); }

    public void cancelRequest(final UUID who) { send(new FriendCancel(who)); }

    public void removeFriend(final UUID who) { send(new FriendRemove(who)); }

    public void block(final UUID who, final String name) { send(new FriendBlock(who == null ? PlayerRef.NIL : who, name == null ? "" : name, true)); }

    public void unblock(final UUID who) { send(new FriendBlock(who, "", false)); }

    public void setNickname(final UUID who, final String nick, final String note) {
        send(new NicknameSet(who, nick == null ? "" : nick, note == null ? "" : note));
    }

    public void sendChat(final String threadKey, final String text) {
        sendChat(threadKey, text, "", "", "");
    }

    public void sendChat(final String threadKey, final String text, final String attachKind, final String attachId, final String attachMeta) {
        final String t = text == null ? "" : text.strip();
        if (t.isEmpty() && (attachId == null || attachId.isEmpty())) return;
        typingSentMs.remove(threadKey);
        send(new Chat(threadKey, new ChatMessage("", self, t.length() > ChatMessage.MAX_TEXT ? t.substring(0, ChatMessage.MAX_TEXT) : t,
            attachKind == null ? "" : attachKind, attachId == null ? "" : attachId, attachMeta == null ? "" : attachMeta, 0L)));
    }

    /**
     * Sends bytes as an attachment: the blob goes first (to the thread's target), the message referencing
     * it follows once the last chunk left. The bytes are cached locally so the echo renders at once.
     */
    public String sendAttachment(final String threadKey, final String kind, final byte[] bytes, final int durationMs, final String meta, final String text) {
        if (!BlobSender.ready()) { Notifications.plain("Not connected", "Attachments need a hub connection"); return ""; }
        final String target = Threads.blobTarget(threadKey, self.uuid());
        if (target.isEmpty()) return "";
        final String id = BlobSender.send(kind, bytes, durationMs, target, meta == null ? "" : meta,
            sentId -> sendChat(threadKey, text, kind, sentId, meta == null ? "" : meta));
        MediaStore.put(id, bytes);
        return id;
    }

    public void sendTyping(final String threadKey, final boolean typing) {
        final long now = System.currentTimeMillis();
        if (typing) {
            final Long last = typingSentMs.get(threadKey);
            if (last != null && now - last < 3000) return;
            typingSentMs.put(threadKey, now);
        } else {
            if (typingSentMs.remove(threadKey) == null) return;
        }
        send(new Typing(threadKey, self.uuid(), typing));
    }

    /** Fetch older messages of a thread (no-op while a page is pending or nothing older exists). */
    public void requestHistory(final String threadKey, final int limit) {
        final ThreadModel t = thread(threadKey);
        if (t.historyPending || !t.more || !connected()) return;
        t.historyPending = true;
        send(new HistoryRequest(threadKey, t.oldestMs(), Math.max(10, Math.min(100, limit))));
    }

    public void markRead(final String threadKey) {
        final ThreadModel t = threads.get(threadKey);
        if (t == null) return;
        if (t.unread != 0) { t.unread = 0; markCacheDirty(); SocialEvents.THREAD_CHANGED.invoke(l -> l.accept(threadKey)); modelChanged(); }
        final ChatMessage last = t.last();
        send(new MarkRead(threadKey, last == null ? System.currentTimeMillis() : Math.max(last.atMs(), 1)));
    }

    public void createGroup(final String name) { send(new GroupCreate(name)); }

    public void inviteToGroup(final String groupId, final UUID who) { send(new GroupInviteSend(groupId, who)); }

    public void answerGroupInvite(final String groupId, final boolean accept) {
        groupInvites.remove(groupId);
        send(new GroupJoin(groupId, accept));
        modelChanged();
    }

    public void leaveGroup(final String groupId) { send(new GroupLeave(groupId)); }

    public void renameGroup(final String groupId, final String name) { send(new GroupRename(groupId, name)); }

    public void kickFromGroup(final String groupId, final UUID who) { send(new GroupKick(groupId, who)); }

    public void setGroupVoice(final String groupId, final String voiceGroup) { send(new GroupVoiceSet(groupId, voiceGroup == null ? "" : voiceGroup)); }

    /** Invite a friend to the server you are on. */
    public boolean inviteToMyServer(final UUID who) {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() == null || mc.level == null) return false;
        final String label = mc.getCurrentServer().name == null || mc.getCurrentServer().name.isBlank() ? mc.getCurrentServer().ip : mc.getCurrentServer().name;
        send(new Invite(who, "server", mc.getCurrentServer().ip, label));
        return true;
    }

    public void inviteToVoice(final UUID who, final String voiceGroup, final String label) {
        send(new Invite(who, "voice", voiceGroup, label));
    }

    public void startStream(final String title) { send(new StreamStart(title == null ? "" : title)); }

    public void stopStream(final String id) { send(new StreamStop(id)); }

    public void watchStream(final String id, final boolean watch) { send(new StreamWatch(id, watch)); }

    // ------------------------------------------------------------------ cache

    private void loadCache(final String key) {
        if (key.equals(cacheKey) && !friends.isEmpty()) return;
        if (cacheDirty) saveCache();
        cacheKey = key;
        friends.clear(); groups.clear(); threads.clear(); streams.clear();
        requestsIn.clear(); requestsOut.clear(); blocked.clear(); groupInvites.clear();
        final ClientCache c = ClientCache.load(key);
        if (c != null) {
            if (!c.self.isNil()) self = c.self;
            for (final FriendInfo fi : c.friends) friends.put(fi.ref().uuid(), new Friend(fi.withPresence(Presence.offline(fi.presence().sinceMs()))));
            for (final GroupInfo g : c.groups) groups.put(g.id(), g);
            c.messages.forEach((k, msgs) -> {
                final ThreadModel t = thread(k);
                for (final ChatMessage m : msgs) t.add(m);
                t.unread = c.unread.getOrDefault(k, 0);
                final String title = c.titles.get(k);
                if (title != null && !title.isEmpty()) t.title = title;
            });
        }
        cacheDirty = false;
        modelChanged();
    }

    private void markCacheDirty() {
        if (!cacheDirty) cacheDirtySinceMs = System.currentTimeMillis();
        cacheDirty = true;
    }

    /** Writes the cache now (also called at shutdown). */
    public void saveCache() {
        cacheDirty = false;
        final ClientCache c = new ClientCache();
        c.savedMs = System.currentTimeMillis();
        c.hub = cacheKey;
        c.self = self;
        for (final Friend f : friends.values()) c.friends.add(f.toInfo());
        c.groups.addAll(groups.values());
        final int keep = Math.max(10, MultiplayerConfigs.client().cacheMessagesPerThread);
        for (final ThreadModel t : threads.values()) {
            if (t.messages.isEmpty()) continue;
            final List<ChatMessage> tail = t.messages.size() > keep ? new ArrayList<>(t.messages.subList(t.messages.size() - keep, t.messages.size())) : new ArrayList<>(t.messages);
            c.messages.put(t.key, tail);
            c.unread.put(t.key, t.unread);
            c.titles.put(t.key, threadTitle(t));
        }
        c.save(cacheKey);
    }

    void shutdown() {
        if (cacheDirty) saveCache();
        final SocialLink l = link;
        if (l != null) l.close();
    }
}
