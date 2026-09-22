package dev.fallingcloud.slate.config.file;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * A line-preserving TOML editor. It keeps every byte it does not understand: comments, blank lines,
 * spacing, key order. Only the value part of an edited {@code key = value} line is rewritten. Good enough
 * for the flat-ish files mods write (NeoForge's ModConfigSpec, night-config, LambDynamicLights...).
 * Not a validating parser: dates, inline tables and nested arrays are kept as raw text.
 *
 * <p>NeoForge-style metadata comments ({@code # Default: 4}, {@code # Range: 1 ~ 20},
 * {@code #Allowed Values: A, B}) are parsed so the editor can offer sliders and dropdowns.</p>
 */
public final class TomlLines {

    /** A {@code key = value} line (or span of lines for multi-line arrays/strings). */
    public static final class Entry {
        public final String section;
        public final String key;
        public final List<String> comments;
        public String raw;
        public Object value;          // Boolean, Long, Double, String, List<Object> (scalars) or Raw
        public String prefix;         // text before the value on the first line ("\tkey = ")
        public String suffix;         // trailing comment on the last line ("  # note") or ""
        public int line, endLine;
        @Nullable public Object def;
        @Nullable public Double min, max;
        @Nullable public List<String> allowed;

        Entry(final String section, final String key, final List<String> comments) {
            this.section = section;
            this.key = key;
            this.comments = comments;
        }

        public String path() { return section.isEmpty() ? key : section + "." + key; }

        /** Comment lines that are not metadata, as tooltip text. */
        public List<String> description() {
            final List<String> out = new ArrayList<>();
            for (final String c : comments) if (!isMeta(c)) out.add(c);
            return out;
        }
    }

    /** A {@code [section]} header. */
    public record Section(String path, List<String> comments, int line) {}

    /** A value the parser did not understand (inline table, date, nested array); written back verbatim. */
    public record Raw(String text) {
        @Override public String toString() { return text; }
    }

    private static final Pattern KEY_VALUE = Pattern.compile("^(\\s*)((?:\"[^\"]*\"|'[^']*'|[A-Za-z0-9_\\-.]+))(\\s*=\\s*)(.*)$");
    private static final Pattern DEFAULT_META = Pattern.compile("^Default:\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RANGE_META = Pattern.compile("^Range:\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALLOWED_META = Pattern.compile("^Allowed Values:\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern INT = Pattern.compile("^[+-]?\\d[\\d_]*$");
    private static final Pattern FLOAT = Pattern.compile("^[+-]?(?:\\d[\\d_]*\\.\\d*(?:[eE][+-]?\\d+)?|\\d[\\d_]*[eE][+-]?\\d+|\\.\\d+(?:[eE][+-]?\\d+)?|inf|nan)$");

    private final List<String> lines;
    private final String newline;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Section> sections = new ArrayList<>();

    private TomlLines(final List<String> lines, final String newline) {
        this.lines = lines;
        this.newline = newline;
    }

    public static TomlLines parse(final String text) {
        final String nl = TextFile.lineSeparator(text);
        final List<String> lines = new ArrayList<>(List.of(text.split("\\r?\\n", -1)));
        final TomlLines t = new TomlLines(lines, nl);
        t.scan();
        return t;
    }

    public List<Entry> entries() { return entries; }

    public List<Section> sections() { return sections; }

    public Optional<Entry> find(final String path) {
        for (final Entry e : entries) if (e.path().equals(path)) return Optional.of(e);
        return Optional.empty();
    }

    public String render() {
        return String.join(newline, lines);
    }

    // ------------------------------------------------------------------ scanning

    private void scan() {
        entries.clear();
        sections.clear();
        String section = "";
        List<String> pending = new ArrayList<>();
        final java.util.Map<String, Integer> arrayTables = new java.util.HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            final String trimmed = line.trim();
            if (trimmed.isEmpty()) { pending = new ArrayList<>(); continue; }
            if (trimmed.startsWith("#")) { pending.add(stripComment(trimmed)); continue; }
            if (trimmed.startsWith("[")) {
                final boolean array = trimmed.startsWith("[[");
                final int close = trimmed.indexOf(array ? "]]" : "]");
                if (close > 0) {
                    String name = trimmed.substring(array ? 2 : 1, close).trim();
                    name = unquoteKey(name);
                    if (array) {
                        final int n = arrayTables.merge(name, 1, Integer::sum) - 1;
                        name = n == 0 ? name : name + "[" + n + "]";
                    }
                    section = name;
                    sections.add(new Section(section, pending, i));
                }
                pending = new ArrayList<>();
                continue;
            }
            final Matcher m = KEY_VALUE.matcher(line);
            if (!m.matches()) { pending = new ArrayList<>(); continue; }
            final Entry e = new Entry(section, unquoteKey(m.group(2)), pending);
            e.prefix = m.group(1) + m.group(2) + m.group(3);
            e.line = i;
            final int[] end = new int[] { i };
            final String[] parts = readValue(m.group(4), i, end);   // {raw, suffix}
            e.endLine = end[0];
            e.raw = parts[0];
            e.suffix = parts[1];
            e.value = parseValue(e.raw);
            applyMeta(e);
            entries.add(e);
            pending = new ArrayList<>();
            i = e.endLine;
        }
    }

    private static String stripComment(final String trimmed) {
        String s = trimmed.substring(1);
        if (s.startsWith(" ")) s = s.substring(1);
        return s;
    }

    private static String unquoteKey(final String k) {
        final String s = k.trim();
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) return s.substring(1, s.length() - 1);
        return s;
    }

    /** Reads the value starting at {@code first} (text after '='), possibly continuing on later lines. */
    private String[] readValue(final String first, final int startLine, final int[] endLineOut) {
        String text = first;
        int lineIdx = startLine;
        final String t = first.trim();
        if (t.startsWith("\"\"\"") || t.startsWith("'''")) {
            final String delim = t.substring(0, 3);
            int close = text.indexOf(delim, text.indexOf(delim) + 3);
            while (close < 0 && lineIdx + 1 < lines.size()) {
                lineIdx++;
                text = text + "\n" + lines.get(lineIdx);
                close = text.indexOf(delim, text.indexOf(delim) + 3);
            }
            endLineOut[0] = lineIdx;
            if (close < 0) return new String[] { text, "" };
            return new String[] { text.substring(0, close + 3), text.substring(close + 3) };
        }
        if (t.startsWith("[") || t.startsWith("{")) {
            int end = balancedEnd(text);
            while (end < 0 && lineIdx + 1 < lines.size()) {
                lineIdx++;
                text = text + "\n" + lines.get(lineIdx);
                end = balancedEnd(text);
            }
            endLineOut[0] = lineIdx;
            if (end < 0) return new String[] { text, "" };
            return new String[] { text.substring(0, end + 1), text.substring(end + 1) };
        }
        endLineOut[0] = lineIdx;
        // Scalar: up to a comment that is not inside quotes.
        final int end = scalarEnd(text);
        return new String[] { text.substring(0, end).trim(), text.substring(end) };
    }

    /** Index of the character that closes the bracket opened at the first non-space char, or -1. */
    private static int balancedEnd(final String s) {
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\' && quote == '"') { i++; continue; }
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') { quote = c; continue; }
            if (c == '#') { final int nl = s.indexOf('\n', i); if (nl < 0) return -1; i = nl; continue; }
            if (c == '[' || c == '{') depth++;
            else if (c == ']' || c == '}') { depth--; if (depth == 0) return i; }
        }
        return -1;
    }

    private static int scalarEnd(final String s) {
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\' && quote == '"') { i++; continue; }
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') { quote = c; continue; }
            if (c == '#') return i;
        }
        return s.length();
    }

    // ------------------------------------------------------------------ values

    public static Object parseValue(final String rawIn) {
        final String raw = rawIn.trim();
        if (raw.isEmpty()) return new Raw("");
        if (raw.equals("true")) return Boolean.TRUE;
        if (raw.equals("false")) return Boolean.FALSE;
        if (raw.startsWith("\"\"\"") && raw.endsWith("\"\"\"") && raw.length() >= 6) return unescape(raw.substring(3, raw.length() - 3).replaceFirst("^\\r?\\n", ""));
        if (raw.startsWith("'''") && raw.endsWith("'''") && raw.length() >= 6) return raw.substring(3, raw.length() - 3).replaceFirst("^\\r?\\n", "");
        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) return unescape(raw.substring(1, raw.length() - 1));
        if (raw.startsWith("'") && raw.endsWith("'") && raw.length() >= 2) return raw.substring(1, raw.length() - 1);
        if (INT.matcher(raw).matches()) {
            try { return Long.parseLong(raw.replace("_", "").replace("+", "")); } catch (final NumberFormatException e) { return new Raw(raw); }
        }
        if (FLOAT.matcher(raw).matches()) {
            try { return Double.parseDouble(raw.replace("_", "")); } catch (final NumberFormatException e) { return new Raw(raw); }
        }
        if (raw.startsWith("[") && raw.endsWith("]")) {
            final List<Object> out = new ArrayList<>();
            for (final String el : splitTopLevel(raw.substring(1, raw.length() - 1))) {
                final String e = el.trim();
                if (e.isEmpty()) continue;
                final Object v = parseValue(e);
                if (v instanceof List<?>) return new Raw(raw);      // nested arrays: keep verbatim
                out.add(v);
            }
            return out;
        }
        return new Raw(raw);
    }

    private static List<String> splitTopLevel(final String s) {
        final List<String> out = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        final StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (quote != 0) {
                cur.append(c);
                if (c == '\\' && quote == '"' && i + 1 < s.length()) { cur.append(s.charAt(++i)); continue; }
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '#') { final int nl = s.indexOf('\n', i); if (nl < 0) break; i = nl; continue; }
            if (c == '"' || c == '\'') { quote = c; cur.append(c); continue; }
            if (c == '[' || c == '{') depth++;
            if (c == ']' || c == '}') depth--;
            if (c == ',' && depth == 0) { out.add(cur.toString()); cur.setLength(0); continue; }
            cur.append(c);
        }
        if (!cur.toString().trim().isEmpty()) out.add(cur.toString());
        return out;
    }

    public static String unescape(final String s) {
        if (s.indexOf('\\') < 0) return s;
        final StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) { sb.append(c); continue; }
            final char n = s.charAt(++i);
            switch (n) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case 'u' -> {
                    if (i + 4 < s.length()) { sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16)); i += 4; }
                }
                default -> sb.append('\\').append(n);
            }
        }
        return sb.toString();
    }

    public static String escape(final String s) {
        final StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                case '\r' -> sb.append("\\r");
                default -> { if (c < 0x20) sb.append(String.format(Locale.ROOT, "\\u%04X", (int) c)); else sb.append(c); }
            }
        }
        return sb.append('"').toString();
    }

    /** TOML text for a value, matching the type of {@code like} where sensible (keeps 1.0 vs 1). */
    public static String format(final Object value, @Nullable final Object like) {
        if (value == null) return "\"\"";
        if (value instanceof Raw r) return r.text();
        if (value instanceof Boolean b) return b.toString();
        if (value instanceof Number n) {
            final boolean wantDouble = like instanceof Double || (like == null && n instanceof Double && n.doubleValue() != Math.rint(n.doubleValue()));
            if (wantDouble || (n instanceof Double d && d != Math.rint(d))) {
                final double d = n.doubleValue();
                if (Double.isNaN(d)) return "nan";
                if (Double.isInfinite(d)) return d > 0 ? "inf" : "-inf";
                String s = Double.toString(d);
                if (s.contains("E")) s = String.format(Locale.ROOT, "%.10f", d).replaceAll("0+$", "").replaceAll("\\.$", ".0");
                return s;
            }
            return Long.toString(n.longValue());
        }
        if (value instanceof List<?> l) {
            final StringBuilder sb = new StringBuilder("[");
            final Object sample = like instanceof List<?> ll && !ll.isEmpty() ? ll.get(0) : null;
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(", ");
                final Object el = l.get(i);
                if (sample != null && !(el instanceof Raw)) {
                    // Coerce strings to the list's element type where possible.
                    if (sample instanceof Boolean && el instanceof String s) { sb.append(Boolean.parseBoolean(s)); continue; }
                    if (sample instanceof Long && el instanceof String s) { try { sb.append(Long.parseLong(s.trim())); continue; } catch (final NumberFormatException ignored) {} }
                    if (sample instanceof Double && el instanceof String s) { try { sb.append(format(Double.parseDouble(s.trim()), sample)); continue; } catch (final NumberFormatException ignored) {} }
                }
                sb.append(format(el, sample));
            }
            return sb.append(']').toString();
        }
        return escape(String.valueOf(value));
    }

    // ------------------------------------------------------------------ editing

    /** Rewrite the value of {@code path}. Returns false when the key is not in the file. */
    public boolean set(final String path, final Object value) {
        final Entry e = find(path).orElse(null);
        if (e == null) return false;
        final String raw = format(value, e.value);
        final String newLine = e.prefix + raw + (e.suffix == null ? "" : e.suffix.replace("\n", ""));
        // Collapse a multi-line span into one line.
        for (int i = e.endLine; i > e.line; i--) lines.remove(i);
        lines.set(e.line, newLine);
        final int removed = e.endLine - e.line;
        e.endLine = e.line;
        e.raw = raw;
        e.value = parseValue(raw);
        if (removed > 0) reindex(e.line, -removed);
        return true;
    }

    private void reindex(final int afterLine, final int delta) {
        for (final Entry x : entries) {
            if (x.line > afterLine) { x.line += delta; x.endLine += delta; }
        }
        for (int i = 0; i < sections.size(); i++) {
            final Section s = sections.get(i);
            if (s.line() > afterLine) sections.set(i, new Section(s.path(), s.comments(), s.line() + delta));
        }
    }

    // ------------------------------------------------------------------ metadata

    static boolean isMeta(final String c) {
        return DEFAULT_META.matcher(c).matches() || RANGE_META.matcher(c).matches() || ALLOWED_META.matcher(c).matches();
    }

    private static void applyMeta(final Entry e) {
        for (final String c : e.comments) {
            Matcher m = DEFAULT_META.matcher(c);
            if (m.matches()) { final Object v = parseValue(m.group(1).trim()); e.def = v instanceof Raw ? m.group(1).trim() : v; continue; }
            m = RANGE_META.matcher(c);
            if (m.matches()) { parseRange(e, m.group(1).trim()); continue; }
            m = ALLOWED_META.matcher(c);
            if (m.matches()) {
                final List<String> vals = new ArrayList<>();
                for (final String v : m.group(1).split(",")) if (!v.trim().isEmpty()) vals.add(v.trim());
                e.allowed = vals;
            }
        }
    }

    private static void parseRange(final Entry e, final String spec) {
        try {
            if (spec.startsWith(">")) { e.min = Double.parseDouble(spec.substring(1).trim()); return; }
            if (spec.startsWith("<")) { e.max = Double.parseDouble(spec.substring(1).trim()); return; }
            final String[] parts = spec.split("~");
            if (parts.length == 2) {
                e.min = Double.parseDouble(parts[0].trim());
                e.max = Double.parseDouble(parts[1].trim());
            }
        } catch (final NumberFormatException ignored) {}
    }
}
