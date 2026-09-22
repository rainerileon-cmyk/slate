package dev.fallingcloud.slate.multiplayer.client;

import dev.fallingcloud.slate.core.event.Event;
import dev.fallingcloud.slate.multiplayer.social.ChatMessage;
import dev.fallingcloud.slate.multiplayer.social.RequestInfo;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage;
import dev.fallingcloud.slate.multiplayer.social.StreamInfo;
import java.util.function.Consumer;

/**
 * Client-side social events, fired on the client main thread by {@link SocialClient}. The Friends hub UI,
 * the layout elements and the Chat module listen here instead of polling.
 */
public final class SocialEvents {

    /** Friends, requests, blocked, groups, invites, streams or the thread list changed. */
    public static final Event<Runnable> MODEL_CHANGED = new Event<>("MP_MODEL_CHANGED");
    /** The link state changed (see {@link SocialClient#linkState()} / {@link SocialClient#stateDetail()}). */
    public static final Event<Consumer<LinkState>> LINK_STATE = new Event<>("MP_LINK_STATE");
    /** A friend's presence changed. */
    public static final Event<Consumer<Friend>> FRIEND_PRESENCE = new Event<>("MP_FRIEND_PRESENCE");
    /** A message arrived in a thread (own echoes included, {@code own} = true). */
    public static final Event<MessageListener> MESSAGE = new Event<>("MP_MESSAGE");
    /** A thread changed in a way the message list cares about (typing, history page, unread, title). */
    public static final Event<Consumer<String>> THREAD_CHANGED = new Event<>("MP_THREAD_CHANGED");
    public static final Event<Consumer<RequestInfo>> REQUEST_RECEIVED = new Event<>("MP_REQUEST_RECEIVED");
    public static final Event<Consumer<SocialMessage.InviteIn>> INVITE_RECEIVED = new Event<>("MP_INVITE_RECEIVED");
    public static final Event<Consumer<StreamInfo>> STREAM_STARTED = new Event<>("MP_STREAM_STARTED");
    public static final Event<Consumer<String>> STREAM_ENDED = new Event<>("MP_STREAM_ENDED");

    @FunctionalInterface
    public interface MessageListener {
        void onMessage(String threadKey, ChatMessage message, boolean own);
    }

    private SocialEvents() {}
}
