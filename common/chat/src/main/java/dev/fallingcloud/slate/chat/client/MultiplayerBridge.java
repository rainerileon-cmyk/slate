package dev.fallingcloud.slate.chat.client;

import dev.fallingcloud.slate.chat.SlateChat;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The ONLY class that names Slate Multiplayer's client API, and it does so reflectively: the two modules are
 * developed in parallel and Chat must build - and run - whether or not those classes exist yet. Every call
 * degrades to "unavailable" (null / false / empty) on any failure.
 *
 * <p>Contract this codes against (exact names):</p>
 * <ul>
 *   <li>{@code dev.fallingcloud.slate.multiplayer.voice.clips.VoiceSupport}: {@code available()},
 *       {@code startRecording(int maxSeconds) -> boolean}, {@code stopRecording() -> VoiceClip},
 *       {@code decode(VoiceClip) -> short[]}, {@code lastError() -> String}</li>
 *   <li>{@code ...voice.clips.VoicePlayer}: {@code toggle(String id, short[] pcm)}, {@code stop()},
 *       {@code playingId() -> String}, {@code progress() -> float}</li>
 *   <li>{@code ...voice.clips.VoiceClip}: {@code serialize() -> byte[]}, {@code static deserialize(byte[]) -> VoiceClip},
 *       {@code durationMs() -> int}</li>
 *   <li>{@code dev.fallingcloud.slate.multiplayer.client.SocialClient}: {@code static get()}, {@code threads()}
 *       returning a list whose elements answer {@code id()}, {@code name()} (or {@code title()}), {@code unread()},
 *       {@code messages()} (elements: {@code sender()}, {@code senderId()}/{@code uuid()}, {@code text()},
 *       {@code time()}), and {@code send(String)}; optionally {@code blobTarget()} for the blob routing string.
 *       Everything in this bullet is optional: a missing method just hides the feature.</li>
 * </ul>
 */
public final class MultiplayerBridge {

    private static final String VOICE_SUPPORT = "dev.fallingcloud.slate.multiplayer.voice.clips.VoiceSupport";
    private static final String VOICE_PLAYER = "dev.fallingcloud.slate.multiplayer.voice.clips.VoicePlayer";
    private static final String VOICE_CLIP = "dev.fallingcloud.slate.multiplayer.voice.clips.VoiceClip";
    private static final String SOCIAL_CLIENT = "dev.fallingcloud.slate.multiplayer.client.SocialClient";

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();

    private static boolean resolved;
    private static MethodHandle vsAvailable, vsStart, vsStop, vsDecode, vsLastError;
    private static MethodHandle vpToggle, vpStop, vpPlayingId, vpProgress;
    private static MethodHandle vcSerialize, vcDeserialize, vcDuration;
    private static MethodHandle scGet, scThreads;
    private static Class<?> voiceClipClass;

    /** One DM / group conversation as the chat tabs see it. */
    public record Thread(String id, Component label, int unread, Object handle) {}

    /** One message inside a thread. */
    public record ThreadMessage(String sender, @Nullable UUID senderId, String text, long timeMs) {}

    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            final Class<?> vs = Class.forName(VOICE_SUPPORT);
            voiceClipClass = Class.forName(VOICE_CLIP);
            vsAvailable = LOOKUP.findStatic(vs, "available", MethodType.methodType(boolean.class));
            vsStart = LOOKUP.findStatic(vs, "startRecording", MethodType.methodType(boolean.class, int.class));
            vsStop = LOOKUP.findStatic(vs, "stopRecording", MethodType.methodType(voiceClipClass));
            vsDecode = LOOKUP.findStatic(vs, "decode", MethodType.methodType(short[].class, voiceClipClass));
            vsLastError = LOOKUP.findStatic(vs, "lastError", MethodType.methodType(String.class));
            vcSerialize = LOOKUP.findVirtual(voiceClipClass, "serialize", MethodType.methodType(byte[].class));
            vcDeserialize = LOOKUP.findStatic(voiceClipClass, "deserialize", MethodType.methodType(voiceClipClass, byte[].class));
            vcDuration = LOOKUP.findVirtual(voiceClipClass, "durationMs", MethodType.methodType(int.class));
        } catch (final Throwable t) {
            SlateChat.LOGGER.info("[Slate Chat] voice clips unavailable (Multiplayer voice API not present: {})", t.toString());
            vsAvailable = null;
        }
        try {
            final Class<?> vp = Class.forName(VOICE_PLAYER);
            vpToggle = LOOKUP.findStatic(vp, "toggle", MethodType.methodType(void.class, String.class, short[].class));
            vpStop = LOOKUP.findStatic(vp, "stop", MethodType.methodType(void.class));
            vpPlayingId = LOOKUP.findStatic(vp, "playingId", MethodType.methodType(String.class));
            vpProgress = LOOKUP.findStatic(vp, "progress", MethodType.methodType(float.class));
        } catch (final Throwable t) {
            vpToggle = null;
        }
        try {
            final Class<?> sc = Class.forName(SOCIAL_CLIENT);
            scGet = LOOKUP.findStatic(sc, "get", MethodType.methodType(sc));
            scThreads = LOOKUP.findVirtual(sc, "threads", MethodType.methodType(List.class));
        } catch (final Throwable t) {
            scGet = null;
        }
    }

    // ------------------------------------------------------------------ voice

    public static boolean voiceAvailable() {
        resolve();
        if (vsAvailable == null) return false;
        try { return (boolean) vsAvailable.invoke(); } catch (final Throwable t) { return false; }
    }

    public static boolean startRecording(final int maxSeconds) {
        resolve();
        if (vsStart == null) return false;
        try { return (boolean) vsStart.invoke(maxSeconds); } catch (final Throwable t) { return false; }
    }

    /** The finished clip (opaque), or null. */
    @Nullable
    public static Object stopRecording() {
        resolve();
        if (vsStop == null) return null;
        try { return vsStop.invoke(); } catch (final Throwable t) { return null; }
    }

    @Nullable
    public static short[] decode(@Nullable final Object clip) {
        resolve();
        if (vsDecode == null || clip == null) return null;
        try { return (short[]) vsDecode.invoke(clip); } catch (final Throwable t) { return null; }
    }

    @Nullable
    public static String lastError() {
        resolve();
        if (vsLastError == null) return null;
        try { return (String) vsLastError.invoke(); } catch (final Throwable t) { return null; }
    }

    @Nullable
    public static byte[] serialize(@Nullable final Object clip) {
        resolve();
        if (vcSerialize == null || clip == null) return null;
        try { return (byte[]) vcSerialize.invoke(clip); } catch (final Throwable t) { return null; }
    }

    @Nullable
    public static Object deserialize(@Nullable final byte[] bytes) {
        resolve();
        if (vcDeserialize == null || bytes == null) return null;
        try { return vcDeserialize.invoke(bytes); } catch (final Throwable t) { return null; }
    }

    public static int durationMs(@Nullable final Object clip) {
        resolve();
        if (vcDuration == null || clip == null) return 0;
        try { return (int) vcDuration.invoke(clip); } catch (final Throwable t) { return 0; }
    }

    public static boolean playerAvailable() {
        resolve();
        return vpToggle != null;
    }

    public static void togglePlay(final String id, final short[] pcm) {
        resolve();
        if (vpToggle == null) return;
        try { vpToggle.invoke(id, pcm); } catch (final Throwable t) { SlateChat.LOGGER.debug("[Slate Chat] voice play failed: {}", t.toString()); }
    }

    public static void stopPlay() {
        resolve();
        if (vpStop == null) return;
        try { vpStop.invoke(); } catch (final Throwable ignored) {}
    }

    @Nullable
    public static String playingId() {
        resolve();
        if (vpPlayingId == null) return null;
        try { return (String) vpPlayingId.invoke(); } catch (final Throwable t) { return null; }
    }

    public static float progress() {
        resolve();
        if (vpProgress == null) return 0f;
        try { return (float) vpProgress.invoke(); } catch (final Throwable t) { return 0f; }
    }

    // ------------------------------------------------------------------ social (DM / group threads)

    public static boolean socialAvailable() {
        resolve();
        if (scGet == null) return false;
        try { return scGet.invoke() != null; } catch (final Throwable t) { return false; }
    }

    /** Open DM/group threads, or empty when the social client is absent. */
    public static List<Thread> threads() {
        resolve();
        final List<Thread> out = new ArrayList<>();
        if (scGet == null || scThreads == null) return out;
        try {
            final Object client = scGet.invoke();
            if (client == null) return out;
            final Object list = scThreads.invoke(client);
            if (!(list instanceof List<?> l)) return out;
            for (final Object t : l) {
                if (t == null) continue;
                final Object id = call(t, "id");
                if (id == null) continue;
                Object name = call(t, "name");
                if (name == null) name = call(t, "title");
                final Object unread = call(t, "unread");
                final Component label = name instanceof Component c ? c : Component.literal(String.valueOf(name == null ? id : name));
                out.add(new Thread(String.valueOf(id), label, unread instanceof Number n ? n.intValue() : 0, t));
            }
        } catch (final Throwable t) {
            SlateChat.LOGGER.debug("[Slate Chat] social threads unavailable: {}", t.toString());
        }
        return out;
    }

    /** Messages of a thread, oldest first, or empty. */
    public static List<ThreadMessage> messages(final Thread thread) {
        final List<ThreadMessage> out = new ArrayList<>();
        try {
            final Object list = call(thread.handle(), "messages");
            if (!(list instanceof List<?> l)) return out;
            for (final Object m : l) {
                if (m == null) continue;
                final Object sender = call(m, "sender");
                Object id = call(m, "senderId");
                if (id == null) id = call(m, "uuid");
                final Object text = call(m, "text");
                final Object time = call(m, "time");
                final UUID uuid = id instanceof UUID u ? u : null;
                final String senderName = sender instanceof Component c ? c.getString() : String.valueOf(sender == null ? "" : sender);
                out.add(new ThreadMessage(senderName, uuid, text instanceof Component c ? c.getString() : String.valueOf(text == null ? "" : text),
                    time instanceof Number n ? n.longValue() : time instanceof java.time.Instant i ? i.toEpochMilli() : System.currentTimeMillis()));
            }
        } catch (final Throwable t) {
            SlateChat.LOGGER.debug("[Slate Chat] thread messages unavailable: {}", t.toString());
        }
        return out;
    }

    /** Sends a line into a thread; false when unsupported. */
    public static boolean send(final Thread thread, final String text) {
        try {
            final java.lang.reflect.Method m = thread.handle().getClass().getMethod("send", String.class);
            m.invoke(thread.handle(), text);
            return true;
        } catch (final Throwable t) {
            SlateChat.LOGGER.debug("[Slate Chat] thread send unavailable: {}", t.toString());
            return false;
        }
    }

    /** The blob routing target for a thread ({@code p:<uuid>} / {@code g:<id>}), or {@code ""} when unknown. */
    public static String blobTarget(final Thread thread) {
        final Object t = call(thread.handle(), "blobTarget");
        return t == null ? "" : String.valueOf(t);
    }

    @Nullable
    private static Object call(final Object target, final String method) {
        try {
            final java.lang.reflect.Method m = target.getClass().getMethod(method);
            return m.invoke(target);
        } catch (final Throwable t) {
            return null;
        }
    }

    private MultiplayerBridge() {}
}
