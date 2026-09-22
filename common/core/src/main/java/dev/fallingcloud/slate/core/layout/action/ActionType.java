package dev.fallingcloud.slate.core.layout.action;

import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;

/**
 * One kind of action a layout element can perform (open a screen, join a server, ...). The
 * {@link #args()} schema lets the editor render a form for it; {@link #run(Map)} executes with the
 * element's saved argument values (all strings; parse yourself).
 *
 * @param id       namespaced id, e.g. {@code slate:open_url}, {@code slate_menu:open_screenshots}
 * @param label    what the editor shows
 * @param args     argument schema, in form order
 * @param runner   the implementation
 */
public record ActionType(String id, Component label, List<Arg> args, Runner runner) {

    public void run(final Map<String, String> values) {
        runner.run(values);
    }

    @FunctionalInterface
    public interface Runner {
        void run(Map<String, String> args);
    }

    /** One form field. {@code kind}: TEXT, MULTILINE, NUMBER, BOOL, SCREEN_ID, ACTION_LIST, CHOICE. */
    public record Arg(String key, Component label, Kind kind, String defaultValue, List<String> choices) {
        public enum Kind { TEXT, MULTILINE, NUMBER, BOOL, SCREEN_ID, ACTION_LIST, CHOICE }

        public static Arg text(final String key, final Component label, final String def) {
            return new Arg(key, label, Kind.TEXT, def, List.of());
        }

        public static Arg multiline(final String key, final Component label, final String def) {
            return new Arg(key, label, Kind.MULTILINE, def, List.of());
        }

        public static Arg bool(final String key, final Component label, final boolean def) {
            return new Arg(key, label, Kind.BOOL, Boolean.toString(def), List.of());
        }

        public static Arg number(final String key, final Component label, final String def) {
            return new Arg(key, label, Kind.NUMBER, def, List.of());
        }

        public static Arg choice(final String key, final Component label, final String def, final List<String> choices) {
            return new Arg(key, label, Kind.CHOICE, def, choices);
        }

        public static Arg screen(final String key, final Component label) {
            return new Arg(key, label, Kind.SCREEN_ID, "minecraft:title", List.of());
        }
    }
}
