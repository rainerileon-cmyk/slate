package dev.fallingcloud.slate.multiplayer.client.ui;

import dev.fallingcloud.slate.multiplayer.client.Friend;
import dev.fallingcloud.slate.multiplayer.client.SocialClient;
import dev.fallingcloud.slate.multiplayer.social.FriendInfo;
import dev.fallingcloud.slate.multiplayer.social.GroupInfo;
import dev.fallingcloud.slate.multiplayer.social.PlayerRef;
import dev.fallingcloud.slate.multiplayer.social.Presence;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A made-up circle of friends, for looking at the friends screen without a hub to talk to ({@code slate.sampleData},
 * set by the development harness only). The people are offline-mode players, so they wear the default skins and
 * nothing is asked of anybody's servers. Nothing here is ever sent anywhere or saved.
 */
final class SampleSocial {

    private static final long HOUR = 3_600_000L, DAY = 24 * HOUR;
    private static List<Friend> friends;

    private static UUID id(final String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static Friend friend(final String name, final String nick, final String note, final Presence presence, final long since) {
        return new Friend(new FriendInfo(new PlayerRef(id(name), name), nick, note, presence, since));
    }

    static synchronized List<Friend> friends() {
        if (friends == null) {
            final long now = System.currentTimeMillis();
            final List<Friend> out = new ArrayList<>();
            out.add(friend("Mossback", "", "Builds the railways", new Presence(Presence.State.IN_GAME, "play.hollowpine.net", "overworld", "Laying track to the coast", now - 42 * 60_000L), now - 210 * DAY));
            out.add(friend("Quillfeather", "Quill", "", new Presence(Presence.State.IN_GAME, Presence.SINGLEPLAYER, "the_nether", "", now - 2 * HOUR), now - 96 * DAY));
            out.add(friend("TinLantern", "", "", new Presence(Presence.State.MENU, "", "", "", now - 5 * 60_000L), now - 31 * DAY));
            out.add(friend("Brackenfoot", "", "Owes me a beacon", new Presence(Presence.State.AWAY, "play.hollowpine.net", "overworld", "Back in ten", now - 25 * 60_000L), now - 400 * DAY));
            out.add(friend("Saltwick", "", "", Presence.offline(now - 3 * HOUR), now - 12 * DAY));
            out.add(friend("OldHarrow", "Harrow", "", Presence.offline(now - 2 * DAY), now - 150 * DAY));
            out.add(friend("Fennelseed", "", "", new Presence(Presence.State.IN_GAME, "mc.emberfall.org", "the_end", "", now - 11 * 60_000L), now - 61 * DAY));
            out.add(friend("Cindermoth", "", "", Presence.offline(now - 9 * DAY), now - 75 * DAY));
            out.add(friend("Waxwing", "", "", Presence.offline(now - 40 * DAY), now - 300 * DAY));
            friends = out;
        }
        return friends;
    }

    static boolean online(final UUID uuid) {
        for (final Friend f : friends()) if (f.uuid.equals(uuid)) return f.online();
        return false;
    }

    static List<GroupInfo> groups() {
        final PlayerRef me = SocialClient.get().self();
        final List<Friend> f = friends();
        final long now = System.currentTimeMillis();
        final List<GroupInfo> out = new ArrayList<>();
        out.add(new GroupInfo("sample-rail", "Railway crew", me.uuid(), List.of(me, f.get(0).ref(), f.get(3).ref(), f.get(4).ref(), f.get(6).ref()), "", now - 80 * DAY));
        out.add(new GroupInfo("sample-end", "End busters", f.get(1).uuid, List.of(f.get(1).ref(), me, f.get(2).ref(), f.get(5).ref(), f.get(6).ref(), f.get(7).ref(), f.get(8).ref()), "", now - 20 * DAY));
        out.add(new GroupInfo("sample-farm", "Sunday farmers", f.get(5).uuid, List.of(f.get(5).ref(), me, f.get(2).ref()), "", now - 200 * DAY));
        return out;
    }

    private SampleSocial() {}
}
