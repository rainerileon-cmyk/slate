package dev.fallingcloud.slate.config.option;

import com.mojang.blaze3d.platform.InputConstants;
import dev.fallingcloud.slate.config.ConfigPlatform;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Finding key binds by what they do or by the key they are on. A query that names a key finds the binds on that key:
 * "r", "left alt", "space", "mouse 4", "lmb", "ctrl+r" (spaces around the plus do not matter). A single character is
 * only ever a key: as text it would sit in nearly every name. Anything else has to appear, word by word, in the bind's
 * name, category or key names.
 */
public final class KeySearch {

    /** Whether {@code m} matches {@code query}; {@code extraText} is more to search through in a word search. */
    public static boolean matches(final KeyMapping m, final String query, final String extraText) {
        final String q = normalize(query);
        if (q.isEmpty()) return true;
        if (keyNames(m).contains(q)) return true;
        if (q.codePointCount(0, q.length()) == 1) return false;
        final String hay = (extraText + " " + text(m)).toLowerCase(Locale.ROOT);
        for (final String tok : q.split(" ")) if (!tok.isEmpty() && !hay.contains(tok)) return false;
        return true;
    }

    /** What a word search looks through: the bind's name, its category and every name of its key (lower case). */
    public static String text(final KeyMapping m) {
        return (Component.translatable(m.getName()).getString() + " " + Component.translatable(m.getCategory()).getString() + " "
            + String.join(" ", keyNames(m))).toLowerCase(Locale.ROOT);
    }

    /** Lower case, single spaces, none around a plus ("Ctrl + R" is "ctrl+r"). */
    static String normalize(final String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ").replaceAll(" ?\\+ ?", "+");
    }

    /** Every exact name of the key {@code m} is on: its display name, the usual short names, and "ctrl+..." with a modifier. */
    static Set<String> keyNames(final KeyMapping m) {
        final Set<String> out = new LinkedHashSet<>();
        if (m.isUnbound()) return out;
        final InputConstants.Key key = KeyBinding.parse(m.saveString());
        out.add(normalize(key.getDisplayName().getString()));
        aliases(key, out);
        final String modifier = ConfigPlatform.get().keyModifier(m);
        if (!modifier.isEmpty()) {
            final List<String> plain = List.copyOf(out);
            for (final String mod : "ctrl".equals(modifier) ? List.of("ctrl", "control", "cmd") : List.of(modifier)) {
                out.add(mod);
                for (final String k : plain) out.add(mod + "+" + k);
            }
        }
        return out;
    }

    private static void aliases(final InputConstants.Key key, final Set<String> out) {
        final int v = key.getValue();
        if (key.getType() == InputConstants.Type.MOUSE) {
            final int n = v + 1;
            add(out, "mouse", "mouse " + n, "mouse" + n, "mb" + n, "m" + n, "button " + n);
            switch (v) {
                case GLFW.GLFW_MOUSE_BUTTON_LEFT -> add(out, "lmb", "left click");
                case GLFW.GLFW_MOUSE_BUTTON_RIGHT -> add(out, "rmb", "right click");
                case GLFW.GLFW_MOUSE_BUTTON_MIDDLE -> add(out, "mmb", "middle click");
                default -> { }
            }
            return;
        }
        if (key.getType() != InputConstants.Type.KEYSYM) return;
        switch (v) {
            case GLFW.GLFW_KEY_LEFT_ALT -> add(out, "alt", "lalt");
            case GLFW.GLFW_KEY_RIGHT_ALT -> add(out, "alt", "ralt", "alt gr");
            case GLFW.GLFW_KEY_LEFT_CONTROL -> add(out, "ctrl", "control", "lctrl");
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> add(out, "ctrl", "control", "rctrl");
            case GLFW.GLFW_KEY_LEFT_SHIFT -> add(out, "shift", "lshift");
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> add(out, "shift", "rshift");
            case GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER -> add(out, "win", "windows", "super", "cmd", "meta");
            case GLFW.GLFW_KEY_SPACE -> add(out, "space", "spacebar");
            case GLFW.GLFW_KEY_ESCAPE -> add(out, "esc", "escape");
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> add(out, "enter", "return");
            case GLFW.GLFW_KEY_BACKSPACE -> add(out, "backspace");
            case GLFW.GLFW_KEY_DELETE -> add(out, "del", "delete");
            case GLFW.GLFW_KEY_INSERT -> add(out, "ins", "insert");
            case GLFW.GLFW_KEY_PAGE_UP -> add(out, "pgup", "page up");
            case GLFW.GLFW_KEY_PAGE_DOWN -> add(out, "pgdn", "page down");
            case GLFW.GLFW_KEY_UP -> add(out, "up", "up arrow");
            case GLFW.GLFW_KEY_DOWN -> add(out, "down", "down arrow");
            case GLFW.GLFW_KEY_LEFT -> add(out, "left", "left arrow");
            case GLFW.GLFW_KEY_RIGHT -> add(out, "right", "right arrow");
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> add(out, "grave", "tilde");
            case GLFW.GLFW_KEY_CAPS_LOCK -> add(out, "caps", "caps lock", "capslock");
            default -> {
                if (v >= GLFW.GLFW_KEY_KP_0 && v <= GLFW.GLFW_KEY_KP_9) {
                    final int d = v - GLFW.GLFW_KEY_KP_0;
                    add(out, "numpad " + d, "numpad" + d, "num" + d);
                }
            }
        }
    }

    private static void add(final Set<String> out, final String... names) {
        for (final String n : names) out.add(n);
    }

    private KeySearch() {}
}
