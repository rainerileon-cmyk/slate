package dev.fallingcloud.slate.building.ops;

import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * One adjustable parameter of a {@link BuildMode} (thickness, replace rule, palette, ...). Values live in
 * {@link ModeParams}. Lang: {@code slate_building.param.<id>} for the label, {@code slate_building.param.<id>.<option>}
 * (lower case) for CHOICE options and {@code slate_building.param.<id>.desc} for the tooltip.
 *
 * @param id      stable id (also the key in configs and payload tags)
 * @param type    BOOL, INT (inclusive {@code min}..{@code max}) or CHOICE (one of {@code options})
 * @param def     default: a Boolean, an Integer or one of the options (a String)
 * @param min     INT lower bound (0 otherwise)
 * @param max     INT upper bound (0 otherwise)
 * @param options CHOICE values in display order (empty otherwise)
 */
public record ModeParam(String id, Type type, Object def, int min, int max, List<String> options) {

    public enum Type { BOOL, INT, CHOICE }

    public ModeParam {
        options = List.copyOf(options);
    }

    public static ModeParam bool(final String id, final boolean def) {
        return new ModeParam(id, Type.BOOL, def, 0, 0, List.of());
    }

    public static ModeParam integer(final String id, final int def, final int min, final int max) {
        return new ModeParam(id, Type.INT, def, min, max, List.of());
    }

    public static ModeParam choice(final String id, final String def, final String... options) {
        if (!List.of(options).contains(def)) throw new IllegalArgumentException("default " + def + " is not an option of " + id);
        return new ModeParam(id, Type.CHOICE, def, 0, 0, List.of(options));
    }

    public Component displayName() {
        return Component.translatable("slate_building.param." + id);
    }

    public Component description() {
        return Component.translatable("slate_building.param." + id + ".desc");
    }

    /** Label of a CHOICE option. */
    public Component optionName(final String option) {
        return Component.translatable("slate_building.param." + id + "." + option.toLowerCase(Locale.ROOT));
    }

    /**
     * {@code value} coerced to this parameter's type and range (numbers clamped, unknown options → default,
     * wrong types → default), so a stale config or a hand-crafted payload never produces an invalid value.
     */
    public Object sanitize(final Object value) {
        return switch (type) {
            case BOOL -> value instanceof Boolean b ? b : def;
            case INT -> value instanceof Number n ? Math.max(min, Math.min(max, n.intValue())) : def;
            case CHOICE -> value instanceof String s && options.contains(s) ? s : def;
        };
    }
}
