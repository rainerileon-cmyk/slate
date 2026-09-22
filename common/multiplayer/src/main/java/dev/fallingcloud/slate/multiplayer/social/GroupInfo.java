package dev.fallingcloud.slate.multiplayer.social;

import java.util.List;
import java.util.UUID;

/**
 * A friend group: a named set of players with its own chat channel ({@code g:<id>}) and, optionally,
 * a Simple Voice Chat group id its members can join ({@code voiceGroup}, empty when none).
 */
public record GroupInfo(String id, String name, UUID owner, List<PlayerRef> members, String voiceGroup, long createdMs) {

    public GroupInfo {
        if (id == null) id = "";
        if (name == null) name = "";
        if (owner == null) owner = PlayerRef.NIL;
        members = members == null ? List.of() : List.copyOf(members);
        if (voiceGroup == null) voiceGroup = "";
    }

    public boolean isMember(final UUID uuid) {
        for (final PlayerRef m : members) if (m.uuid().equals(uuid)) return true;
        return false;
    }

    public String threadKey() { return Threads.group(id); }
}
