package dev.fallingcloud.slate.menu.client.servers;

import dev.fallingcloud.slate.core.config.JsonConfig;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code config/slate/menu/community_servers.json}: a modpack-shipped list of servers shown in their
 * own section of the server screen. The file is written with an example entry on first run (Core's
 * JsonConfig materialises defaults) so pack authors can find and edit it.
 */
public final class CommunityServers {

    public static final class Entry {
        public String name = "";
        public String address = "";
        public String description = "";

        public Entry() {}

        public Entry(final String name, final String address, final String description) {
            this.name = name;
            this.address = address;
            this.description = description;
        }
    }

    public static final class Data {
        public String _readme = "Servers listed here appear under 'Community' on the Slate multiplayer screen. "
            + "Each entry needs a name and an address (host or host:port); description is optional and shown as a tooltip. "
            + "Players can join directly or add an entry to their own list. Delete the example entry when you add real ones.";
        public List<Entry> servers = new ArrayList<>(List.of(
            new Entry("Example community server", "play.example.com", "Replace me: this entry ships as an example.")));
    }

    private static JsonConfig<Data> file;

    private static synchronized JsonConfig<Data> file() {
        if (file == null) file = JsonConfig.at(JsonConfig.dir().resolve("menu").resolve("community_servers.json"), Data.class, Data::new);
        return file;
    }

    public static List<Entry> all() {
        final List<Entry> out = new ArrayList<>();
        final Data d = file().get();
        if (d.servers == null) return out;
        for (final Entry e : d.servers) if (e != null && e.address != null && !e.address.isBlank()) out.add(e);
        return out;
    }

    /** Re-read the file (the refresh button on the server screen). */
    public static void reload() {
        file().load();
    }

    private CommunityServers() {}
}
