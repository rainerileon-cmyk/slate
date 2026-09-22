package dev.fallingcloud.slate.multiplayer.client;

import dev.fallingcloud.slate.multiplayer.social.FriendInfo;
import dev.fallingcloud.slate.multiplayer.social.PlayerRef;
import dev.fallingcloud.slate.multiplayer.social.Presence;
import java.util.UUID;

/** The client's mutable view of one friend. */
public final class Friend {

    public final UUID uuid;
    public String name;
    public String nick;
    public String note;
    public Presence presence;
    public long sinceMs;

    public Friend(final FriendInfo info) {
        this.uuid = info.ref().uuid();
        apply(info);
    }

    public void apply(final FriendInfo info) {
        if (!info.ref().name().isEmpty()) name = info.ref().name();
        if (name == null) name = "";
        nick = info.nick();
        note = info.note();
        presence = info.presence();
        sinceMs = info.sinceMs();
    }

    public PlayerRef ref() { return new PlayerRef(uuid, name); }

    public FriendInfo toInfo() { return new FriendInfo(ref(), nick, note, presence, sinceMs); }

    /** Nickname if set, else the name. */
    public String display() { return nick == null || nick.isEmpty() ? (name.isEmpty() ? uuid.toString().substring(0, 8) : name) : nick; }

    public boolean online() { return presence != null && presence.online(); }

    public Presence.State state() { return presence == null ? Presence.State.OFFLINE : presence.state(); }

    public boolean joinable() { return presence != null && presence.joinable(); }
}
