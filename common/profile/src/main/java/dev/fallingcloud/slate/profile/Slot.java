package dev.fallingcloud.slate.profile;

import java.util.List;

/**
 * Where on a player something can be changed. The first six are what the player is made of: the skin itself, a
 * limb that is swapped for another, a pet that comes along. The rest is what the player wears. The edit view of
 * the profile screen lists the first group down its left side and the second down its right.
 */
public enum Slot {
    SKIN(false), RIGHT_ARM(false), LEFT_ARM(false), RIGHT_LEG(false), LEFT_LEG(false), PET(false),
    HAT(true), FACE(true), SHIRT(true), PANTS(true), BACK(true);

    private final boolean worn;

    Slot(final boolean worn) { this.worn = worn; }

    /** Something worn (the right side of the edit view), not a part of the body (the left side). */
    public boolean worn() { return worn; }

    /** The key under which a look keeps what is in this slot, and the last part of its language key. */
    public String key() { return name().toLowerCase(java.util.Locale.ROOT); }

    public static List<Slot> body() { return List.of(SKIN, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG, PET); }

    public static List<Slot> wear() { return List.of(HAT, FACE, SHIRT, PANTS, BACK); }
}
