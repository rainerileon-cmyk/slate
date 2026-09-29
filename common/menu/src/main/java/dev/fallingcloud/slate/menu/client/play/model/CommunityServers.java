package dev.fallingcloud.slate.menu.client.play.model;

import dev.fallingcloud.slate.core.config.JsonConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code config/slate/menu/community_servers.json}: a modpack-shipped list of servers shown in their
 * own section of the server screen. The file is written with an example entry on first run (Core's
 * JsonConfig materialises defaults) so pack authors can find and edit it; entries on the reserved
 * example domains (example.com, .net, .org, .example) are never listed, so a pack that leaves the
 * example in does not show its players a dead server.
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
            + "Players can join directly or add an entry to their own list. The example entry is not shown (example.com "
            + "addresses never are); replace it with real ones.";
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
        for (final Entry e : d.servers) {
            if (e != null && e.address != null && !e.address.isBlank() && !isExample(e.address)) out.add(e);
        }
        return out;
    }

    /** An address on a domain reserved for examples (RFC 2606), which never resolves to a real server. */
    static boolean isExample(final String address) {
        String host = address.trim().toLowerCase(Locale.ROOT);
        final int port = host.lastIndexOf(':');
        if (port > 0 && host.indexOf(':') == port) host = host.substring(0, port);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        for (final String domain : List.of("example.com", "example.net", "example.org", "example")) {
            if (host.equals(domain) || host.endsWith("." + domain)) return true;
        }
        return false;
    }

    /** Re-read the file (the refresh button on the server screen). */
    public static void reload() {
        file().load();
    }

    private CommunityServers() {}
}
