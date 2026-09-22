package dev.fallingcloud.slate.multiplayer.client;

import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.multiplayer.SlateMultiplayer;
import dev.fallingcloud.slate.multiplayer.client.ui.FriendsHubScreen;
import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Hooks into the Menu module's SPIs without compiling against it: {@code SlateMenuApi.setPresenceProvider}
 * (which friends are on a server address, for the "friends here" chip) and
 * {@code setScreenshotShareProvider} (share a screenshot to friends from the gallery). Both are installed
 * as dynamic proxies of whatever interface the setter takes, answering by method shape, so a differently
 * named or typed SPI degrades to "no integration" with one log line rather than an error.
 */
public final class MenuBridge {

    private static boolean installed;

    public static void install() {
        if (installed || !SlatePlatform.get().isModLoaded("slate_menu")) return;
        installed = true;
        try {
            final Class<?> api = Class.forName("dev.fallingcloud.slate.menu.api.SlateMenuApi");
            int hooked = 0;
            for (final Method m : api.getMethods()) {
                if (m.getParameterCount() != 1 || !m.getParameterTypes()[0].isInterface()) continue;
                if (m.getName().equals("setPresenceProvider")) { if (bind(api, m, new PresenceHandler())) hooked++; }
                else if (m.getName().equals("setScreenshotShareProvider")) { if (bind(api, m, new ShareHandler())) hooked++; }
            }
            SlateMultiplayer.LOGGER.info("[Slate Multiplayer] Menu integration: {} provider(s) installed", hooked);
        } catch (final ClassNotFoundException e) {
            SlateMultiplayer.LOGGER.info("[Slate Multiplayer] Menu present but no SlateMenuApi - skipping integration");
        } catch (final Throwable t) {
            SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] Menu integration failed: {}", t.toString());
        }
    }

    private static boolean bind(final Class<?> api, final Method setter, final InvocationHandler handler) {
        try {
            final Class<?> spi = setter.getParameterTypes()[0];
            final Object proxy = Proxy.newProxyInstance(spi.getClassLoader(), new Class<?>[] { spi }, handler);
            Object target = null;
            if (!Modifier.isStatic(setter.getModifiers())) {
                target = instanceOf(api);
                if (target == null) { SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] cannot find an instance of {}", api.getName()); return false; }
            }
            setter.invoke(target, proxy);
            return true;
        } catch (final Throwable t) {
            SlateMultiplayer.LOGGER.warn("[Slate Multiplayer] cannot bind {}: {}", setter.getName(), t.toString());
            return false;
        }
    }

    private static Object instanceOf(final Class<?> api) throws Exception {
        for (final String name : new String[] { "get", "instance", "getInstance" }) {
            try {
                final Method m = api.getMethod(name);
                if (Modifier.isStatic(m.getModifiers())) return m.invoke(null);
            } catch (final NoSuchMethodException ignored) {}
        }
        for (final String name : new String[] { "INSTANCE", "API" }) {
            try {
                return api.getField(name).get(null);
            } catch (final NoSuchFieldException ignored) {}
        }
        return null;
    }

    private static Object defaultFor(final Class<?> r) {
        if (r == boolean.class) return false;
        if (r == int.class) return 0;
        if (r == long.class) return 0L;
        if (r == double.class) return 0d;
        if (r == float.class) return 0f;
        if (List.class.isAssignableFrom(r) || Collection.class.isAssignableFrom(r)) return List.of();
        if (r == String.class) return "";
        return null;
    }

    private static boolean handleObjectMethod(final Object proxy, final Method m, final Object[] args, final Object[] out) {
        switch (m.getName()) {
            case "toString" -> { out[0] = "SlateMultiplayer:" + proxy.getClass().getSimpleName(); return true; }
            case "hashCode" -> { out[0] = System.identityHashCode(proxy); return true; }
            case "equals" -> { out[0] = args != null && args.length == 1 && args[0] == proxy; return true; }
            default -> { return false; }
        }
    }

    /** Answers "who is on this server" by method shape: count, boolean, names, uuids or a joined string. */
    private static final class PresenceHandler implements InvocationHandler {
        @Override
        public Object invoke(final Object proxy, final Method m, final Object[] args) {
            final Object[] out = new Object[1];
            if (handleObjectMethod(proxy, m, args, out)) return out[0];
            String address = null;
            if (args != null) for (final Object a : args) if (a instanceof String s) { address = s; break; }
            final List<Friend> here = address == null ? new ArrayList<>(SocialClient.get().friends().stream().filter(Friend::online).toList())
                : SocialClient.get().friendsOn(address);
            final Class<?> r = m.getReturnType();
            if (r == int.class || r == Integer.class) return here.size();
            if (r == boolean.class || r == Boolean.class) return !here.isEmpty();
            if (r == String.class) return String.join(", ", here.stream().map(Friend::display).toList());
            if (Collection.class.isAssignableFrom(r)) {
                final Type g = m.getGenericReturnType();
                if (g instanceof ParameterizedType pt && pt.getActualTypeArguments().length == 1 && pt.getActualTypeArguments()[0] == UUID.class) {
                    return here.stream().map(f -> f.uuid).toList();
                }
                return here.stream().map(Friend::display).toList();
            }
            return defaultFor(r);
        }
    }

    /** "Share this screenshot": opens the Friends hub with the image attached to a thread of the player's choice. */
    private static final class ShareHandler implements InvocationHandler {
        @Override
        public Object invoke(final Object proxy, final Method m, final Object[] args) {
            final Object[] out = new Object[1];
            if (handleObjectMethod(proxy, m, args, out)) return out[0];
            Path path = null;
            if (args != null) for (final Object a : args) {
                if (a instanceof Path p) { path = p; break; }
                if (a instanceof File f) { path = f.toPath(); break; }
                if (a instanceof String s && (s.endsWith(".png") || s.endsWith(".jpg") || s.endsWith(".jpeg"))) { path = Path.of(s); break; }
            }
            final Class<?> r = m.getReturnType();
            if (path == null) return r == boolean.class || r == Boolean.class ? false : defaultFor(r);
            FriendsHubScreen.shareImage(path);
            return r == boolean.class || r == Boolean.class ? true : defaultFor(r);
        }
    }

    private MenuBridge() {}
}
