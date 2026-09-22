package dev.fallingcloud.slate.config.option;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/** One selectable value of a CHOICE option: a stable id (what gets stored) and a display label. */
public record Choice(String id, Component label) {

    public static Choice of(final String id) {
        return new Choice(id, Component.literal(id));
    }

    public static Choice of(final String id, final String label) {
        return new Choice(id, Component.literal(label));
    }

    /** Choices for every constant of an enum class, labelled with a humanised name. */
    public static List<Choice> ofEnum(final Class<?> enumClass) {
        final List<Choice> out = new ArrayList<>();
        final Object[] constants = enumClass.getEnumConstants();
        if (constants == null) return out;
        for (final Object c : constants) {
            final String name = ((Enum<?>) c).name();
            out.add(new Choice(name, Component.literal(Humanize.enumName(name))));
        }
        return out;
    }

    public static List<Choice> ofStrings(final List<String> ids) {
        final List<Choice> out = new ArrayList<>();
        for (final String s : ids) out.add(new Choice(s, Component.literal(Humanize.enumName(s))));
        return out;
    }
}
