package dev.fallingcloud.slate.multiplayer.social;

import java.util.UUID;

/** A player identity as the hub knows it: uuid + last known name. */
public record PlayerRef(UUID uuid, String name) {

    public static final UUID NIL = new UUID(0L, 0L);

    public PlayerRef {
        if (uuid == null) uuid = NIL;
        if (name == null) name = "";
    }

    public boolean isNil() { return NIL.equals(uuid); }

    /** Name if known, else a short uuid. */
    public String display() {
        return name.isEmpty() ? uuid.toString().substring(0, 8) : name;
    }
}
