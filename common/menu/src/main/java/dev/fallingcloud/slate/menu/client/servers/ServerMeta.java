package dev.fallingcloud.slate.menu.client.servers;

import dev.fallingcloud.slate.core.config.JsonConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code config/slate/menu/servers.json}: Slate's extra per-server data (favourite flag, group) keyed
 * by the address as saved in {@code servers.dat}, so the vanilla file stays untouched and compatible.
 */
public final class ServerMeta {

    public static final class Entry {
        public boolean favorite;
        public String group = "";
    }

    public static final class Data {
        public Map<String, Entry> servers = new LinkedHashMap<>();
        public List<String> groups = new ArrayList<>();
    }

    private static JsonConfig<Data> file;

    private static synchronized JsonConfig<Data> file() {
        if (file == null) file = JsonConfig.at(JsonConfig.dir().resolve("menu").resolve("servers.json"), Data.class, Data::new);
        return file;
    }

    private static Entry entry(final String address, final boolean create) {
        final Data d = file().get();
        final String key = ServerActions.normalize(address);
        Entry e = d.servers.get(key);
        if (e == null && create) { e = new Entry(); d.servers.put(key, e); }
        return e;
    }

    public static boolean isFavorite(final String address) {
        final Entry e = entry(address, false);
        return e != null && e.favorite;
    }

    public static void setFavorite(final String address, final boolean favorite) {
        file().update(d -> entry(address, true).favorite = favorite);
    }

    public static String group(final String address) {
        final Entry e = entry(address, false);
        return e == null || e.group == null ? "" : e.group;
    }

    public static void setGroup(final String address, final String group) {
        file().update(d -> {
            entry(address, true).group = group == null ? "" : group;
            if (group != null && !group.isBlank() && !d.groups.contains(group)) d.groups.add(group);
        });
    }

    public static List<String> groups() {
        return List.copyOf(file().get().groups);
    }

    public static void addGroup(final String group) {
        if (group == null || group.isBlank()) return;
        file().update(d -> { if (!d.groups.contains(group)) d.groups.add(group); });
    }

    public static void removeGroup(final String group) {
        file().update(d -> {
            d.groups.remove(group);
            for (final Entry e : d.servers.values()) if (group.equals(e.group)) e.group = "";
        });
    }

    /** Called when a server is removed from the list; drops its meta so the file does not grow forever. */
    public static void forget(final String address) {
        file().update(d -> d.servers.remove(ServerActions.normalize(address)));
    }

    private ServerMeta() {}
}
