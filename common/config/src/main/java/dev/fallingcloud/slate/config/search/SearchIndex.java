package dev.fallingcloud.slate.config.search;

import dev.fallingcloud.slate.config.option.OptionBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * The global option index: every binding of every page with its page and section, matched by
 * whitespace-separated tokens (all must appear). Built lazily by the hub on the first search and
 * rebuilt when pages change (the entries hold live bindings, so values are never stale).
 */
public final class SearchIndex {

    public record Entry(String pageId, Component pageTitle, Component sectionTitle, OptionBinding binding) {}

    public record Hit(Entry entry, int score) {}

    private final List<Entry> entries = new ArrayList<>();
    private final List<String> haystacks = new ArrayList<>();

    public void add(final Entry e) {
        entries.add(e);
        haystacks.add((e.binding.searchText() + " " + e.sectionTitle.getString() + " " + e.pageTitle.getString()).toLowerCase(Locale.ROOT));
    }

    public void addAll(final List<Entry> es) {
        for (final Entry e : es) add(e);
    }

    public int size() { return entries.size(); }

    /** Best matches first; {@code excludePage} drops the page the user is already on. */
    public List<Hit> query(final String text, final String excludePage, final int limit) {
        final List<Hit> hits = new ArrayList<>();
        final String q = text == null ? "" : text.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) return hits;
        final String[] toks = q.split("\\s+");
        for (int i = 0; i < entries.size(); i++) {
            final Entry e = entries.get(i);
            if (e.pageId.equals(excludePage)) continue;
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
