package dev.fallingcloud.slate.config.search;

import dev.fallingcloud.slate.config.option.OptionBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The global option index: every binding of every page and tab with where it lives, matched by
 * whitespace-separated tokens (all must appear). Built lazily by the hub on the first search and
 * rebuilt when pages change (the entries hold live bindings, so values are never stale).
 */
public final class SearchIndex {

    /**
     * One option and where it lives. {@code path} is what the hub opens: a sidebar page id, or
     * {@code category/tab} for a tab of a category page. {@code tabTitle} is the top tab (null when the page
     * has none), {@code sectionTitle} the secondary tab or header inside it.
     */
    public record Entry(String path, Component pageTitle, @Nullable Component tabTitle, Component sectionTitle, OptionBinding binding) {

        public Entry(final String path, final Component pageTitle, final Component sectionTitle, final OptionBinding binding) {
            this(path, pageTitle, null, sectionTitle, binding);
        }

        /** The sidebar page this entry is on. */
        public String pageId() {
            final int i = path.indexOf('/');
            return i < 0 ? path : path.substring(0, i);
        }

        /** "Category › Tab", plus the section when it names something the tab does not. */
        public Component crumb() {
            final List<String> parts = new ArrayList<>();
            add(parts, pageTitle);
            if (tabTitle != null) add(parts, tabTitle);
            add(parts, sectionTitle);
            return Component.literal(String.join(" › ", parts));
        }

        private static void add(final List<String> parts, final Component c) {
            final String s = c.getString().trim();
            if (!s.isEmpty() && (parts.isEmpty() || !parts.get(parts.size() - 1).equalsIgnoreCase(s))) parts.add(s);
        }
    }

    public record Hit(Entry entry, int score) {}

    private final List<Entry> entries = new ArrayList<>();
    private final List<String> haystacks = new ArrayList<>();

    public void add(final Entry e) {
        entries.add(e);
        haystacks.add((e.binding.searchText() + " " + e.sectionTitle.getString() + " " + (e.tabTitle == null ? "" : e.tabTitle.getString())
            + " " + e.pageTitle.getString()).toLowerCase(Locale.ROOT));
    }

    public void addAll(final List<Entry> es) {
        for (final Entry e : es) add(e);
    }

    public int size() { return entries.size(); }

    /** Best matches first; {@code excludePath} drops the page/tab the user is already looking at (its rows are filtered in place). */
    public List<Hit> query(final String text, final String excludePath, final int limit) {
        final List<Hit> hits = new ArrayList<>();
        final String q = text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) return hits;
        final String[] toks = q.split("\s+");
        for (int i = 0; i < entries.size(); i++) {
            final Entry e = entries.get(i);
            if (e.path.equals(excludePath)) continue;
            final String hay = haystacks.get(i);
            boolean all = true;
            for (final String t : toks) if (!hay.contains(t)) { all = false; break; }
            if (!all) continue;
            final String label = e.binding.label().getString().toLowerCase(Locale.ROOT);
            int score = 1;
            if (label.equals(q)) score = 100;
            else if (label.startsWith(q)) score = 60;
            else if (label.contains(q)) score = 40;
            else if (label.contains(toks[0])) score = 20;
            hits.add(new Hit(e, score));
        }
        hits.sort((a, b) -> Integer.compare(b.score, a.score));
        return hits.size() > limit ? new ArrayList<>(hits.subList(0, limit)) : hits;
    }
}
