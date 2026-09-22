package dev.fallingcloud.slate.config.option;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import dev.fallingcloud.slate.core.theme.Colors;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Value coercion, comparison and JSON (preset) conversion shared by every binding and control. */
public final class OptionValues {

    /** Canonical form for equality: numbers as Double, lists as List of String, enums as names. */
    @Nullable
    public static Object normalize(@Nullable final Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Enum<?> e) return e.name();
        if (v instanceof List<?> l) {
            final List<String> out = new ArrayList<>(l.size());
            for (final Object o : l) out.add(String.valueOf(o));
            return out;
        }
        return v;
    }

    public static boolean asBoolean(@Nullable final Object v, final boolean fallback) {
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0;
        if (v instanceof String s) {
            if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("on") || s.equals("1")) return true;
            if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("off") || s.equals("0")) return false;
        }
        return fallback;
    }

    public static double asDouble(@Nullable final Object v, final double fallback) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof Boolean b) return b ? 1 : 0;
        if (v instanceof String s) {
            try { return Double.parseDouble(s.trim()); } catch (final NumberFormatException ignored) {}
        }
        return fallback;
    }

    public static int asInt(@Nullable final Object v, final int fallback) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof Boolean b) return b ? 1 : 0;
        if (v instanceof String s) {
            try { return (int) Math.round(Double.parseDouble(s.trim())); } catch (final NumberFormatException ignored) {}
        }
        return fallback;
    }

    public static long asLong(@Nullable final Object v, final long fallback) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Math.round(Double.parseDouble(s.trim())); } catch (final NumberFormatException ignored) {}
        }
        return fallback;
    }

    public static String asString(@Nullable final Object v) {
        if (v == null) return "";
        if (v instanceof Enum<?> e) return e.name();
        if (v instanceof Double d && d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString(d.longValue());
        return String.valueOf(v);
    }

    public static List<String> asList(@Nullable final Object v) {
        final List<String> out = new ArrayList<>();
        if (v instanceof List<?> l) { for (final Object o : l) out.add(String.valueOf(o)); }
        else if (v instanceof Object[] arr) { for (final Object o : arr) out.add(String.valueOf(o)); }
        else if (v instanceof String s && !s.isEmpty()) { for (final String p : s.split("\\s*,\\s*")) if (!p.isEmpty()) out.add(p); }
        return out;
    }

    /** ARGB int from an int, a "#RRGGBB" string or a decimal string. */
    public static int asColor(@Nullable final Object v, final int fallback) {
        if (v instanceof Number n) return n.intValue() | 0xFF000000;
        if (v instanceof String s) {
            final int c = Colors.fromHex(s, Integer.MIN_VALUE);
            if (c != Integer.MIN_VALUE) return c;
            try { return (int) Long.parseLong(s.trim()) | 0xFF000000; } catch (final NumberFormatException ignored) {}
        }
        return fallback;
    }

    /** Default human text for a value of a binding. */
    public static Component defaultText(final OptionBinding b, @Nullable final Object v) {
        if (v == null) return Component.literal("-");
        switch (b.type()) {
            case BOOLEAN -> { return Component.translatable(asBoolean(v, false) ? "options.on" : "options.off"); }
            case CHOICE -> {
                final String id = asString(v);
                for (final Choice c : b.choices()) if (c.id().equals(id)) return c.label();
                return Component.literal(Humanize.enumName(id));
            }
            case INT -> { return Component.literal(Long.toString(asLong(v, 0))); }
            case DOUBLE -> { return Component.literal(formatDouble(asDouble(v, 0))); }
            case COLOR -> { return Component.literal(Colors.toHex(asColor(v, 0))); }
            case LIST -> { return Component.literal(asList(v).size() + " items"); }
            default -> { return Component.literal(asString(v)); }
        }
    }

    public static String formatDouble(final double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e9) return String.format(Locale.ROOT, "%.1f", d);
        final String s = String.format(Locale.ROOT, "%.3f", d);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", ".0") : s;
    }

    // ------------------------------------------------------------------ JSON for presets

    public static JsonElement toJson(final OptionBinding b, @Nullable final Object v) {
        if (v == null) return JsonNull.INSTANCE;
        return switch (b.type()) {
            case BOOLEAN -> new JsonPrimitive(asBoolean(v, false));
            case INT -> new JsonPrimitive(asLong(v, 0));
            case DOUBLE -> new JsonPrimitive(asDouble(v, 0));
            case COLOR -> new JsonPrimitive(Colors.toHex(asColor(v, 0)));
            case LIST -> {
                final JsonArray arr = new JsonArray();
                for (final String s : asList(v)) arr.add(s);
                yield arr;
            }
            default -> new JsonPrimitive(asString(v));
        };
    }

    @Nullable
    public static Object fromJson(final OptionBinding b, @Nullable final JsonElement e) {
        if (e == null || e.isJsonNull()) return null;
        return switch (b.type()) {
            case BOOLEAN -> e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : asBoolean(e.getAsString(), false);
            case INT -> e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsNumber().longValue() : asLong(e.getAsString(), 0);
            case DOUBLE -> e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : asDouble(e.getAsString(), 0);
            case COLOR -> asColor(e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : e.getAsString(), 0xFFFFFFFF);
            case LIST -> {
                final List<String> out = new ArrayList<>();
                if (e.isJsonArray()) for (final JsonElement x : e.getAsJsonArray()) out.add(x.isJsonPrimitive() ? x.getAsString() : x.toString());
                else out.addAll(asList(e.getAsString()));
                yield out;
            }
            default -> e.isJsonPrimitive() ? e.getAsString() : e.toString();
        };
    }

    private OptionValues() {}
}
