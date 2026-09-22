package dev.fallingcloud.slate.config.file;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * Line-preserving editor for {@code .properties} / {@code .cfg} files ({@code key=value}, {@code key: value}).
 * Comments ({@code #}, {@code !}) before a key become its description; a "Valid values are 'A', 'B'"
 * comment (Simple Voice Chat style) becomes a dropdown.
 */
public final class PropertiesLines {

    public static final class Entry {
        public final String key;
        public final List<String> comments;
        public String value;
        public String prefix;      // "key=" / "key = " / "key: "
        public int line;
        @Nullable public List<String> allowed;

        Entry(final String key, final List<String> comments) {
            this.key = key;
            this.comments = comments;
        }

        public List<String> description() {
            final List<String> out = new ArrayList<>();
            for (final String c : comments) if (!VALID_VALUES.matcher(c).find()) out.add(c);
            return out;
        }
    }

    private static final Pattern KEY_VALUE = Pattern.compile("^(\\s*)((?:\\\\.|[^=:\\s])+)(\\s*[=:]\\s*|\\s+)(.*)$");
    private static final Pattern VALID_VALUES = Pattern.compile("Valid values are\\s+(.*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED = Pattern.compile("'([^']*)'");

    private final List<String> lines;
    private final String newline;
    private final List<Entry> entries = new ArrayList<>();

    private PropertiesLines(final List<String> lines, final String newline) {
        this.lines = lines;
        this.newline = newline;
    }

    public static PropertiesLines parse(final String text) {
        final PropertiesLines p = new PropertiesLines(new ArrayList<>(List.of(text.split("\\r?\\n", -1))), TextFile.lineSeparator(text));
        p.scan();
        return p;
    }

    public List<Entry> entries() { return entries; }

    public Optional<Entry> find(final String key) {
        for (final Entry e : entries) if (e.key.equals(key)) return Optional.of(e);
        return Optional.empty();
    }

    public String render() { return String.join(newline, lines); }

    private void scan() {
        entries.clear();
        List<String> pending = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            final String line = lines.get(i);
            final String trimmed = line.trim();
            if (trimmed.isEmpty()) { pending = new ArrayList<>(); continue; }
            if (trimmed.startsWith("#") || trimmed.startsWith("!")) {
                String c = trimmed.substring(1);
                if (c.startsWith(" ")) c = c.substring(1);
                pending.add(c);
                continue;
            }
            final Matcher m = KEY_VALUE.matcher(line);
            if (!m.matches()) { pending = new ArrayList<>(); continue; }
            final Entry e = new Entry(m.group(2).replace("\\ ", " ").replace("\\:", ":").replace("\\=", "="), pending);
            e.prefix = m.group(1) + m.group(2) + m.group(3);
            e.value = m.group(4);
            e.line = i;
            for (final String c : pending) {
                final Matcher vm = VALID_VALUES.matcher(c);
                if (vm.find()) {
                    final List<String> vals = new ArrayList<>();
                    final Matcher q = QUOTED.matcher(vm.group(1));
                    while (q.find()) vals.add(q.group(1));
                    if (!vals.isEmpty()) e.allowed = vals;
                }
            }
            entries.add(e);
            pending = new ArrayList<>();
        }
    }

    public boolean set(final String key, final String value) {
        final Entry e = find(key).orElse(null);
        if (e == null) return false;
        e.value = value == null ? "" : value;
        lines.set(e.line, e.prefix + e.value);
        return true;
    }
}
