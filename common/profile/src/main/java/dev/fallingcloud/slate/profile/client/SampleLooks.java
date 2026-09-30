package dev.fallingcloud.slate.profile.client;

import dev.fallingcloud.slate.profile.Slot;

/**
 * A made-up wardrobe for looking at the profile screen ({@code slate.sampleData}, the development harness only):
 * five looks on the game's own skins, between them wearing everything there is. Kept in the run folder, never in
 * the player's real profile.
 */
final class SampleLooks {

    static void ensure(final ProfileStore.Account account) {
        if (!account.data().looks.isEmpty()) return;
        final ProfileStore.Look traveller = look(account, "Traveller", "ari", false, "hat_straw", "back_pack", "pants_jeans", "shirt_striped", "leg_stocking_left");
        look(account, "Tinkerer", "kai", false, "face_glasses", "arm_iron_right", "pet_bee", "pants_shorts");
        look(account, "Royal", "makena", true, "hat_crown", "shirt_hoodie", "back_wings");
        look(account, "Old salt", "steve", false, "hat_top", "leg_wood_right", "arm_bandage_left", "face_bandana");
        look(account, "Swamp friend", "sunny", true, "pet_slime", "pants_jeans", "leg_stocking_right");
        account.bio("Builds by day, digs by night. Will trade cake for redstone.");
        account.wear(traveller);
    }

    private static ProfileStore.Look look(final ProfileStore.Account account, final String name, final String skin, final boolean slim, final String... items) {
        final ProfileStore.Look look = account.create(name, "default:" + skin, slim);
        for (final String id : items) {
            final Cosmetic c = Cosmetics.get(id);
            if (c != null) look.put(c.slot(), c);
        }
        return look;
    }

    /** What every slot is called when nothing is in it, by slot. */
    static String none(final Slot slot) { return "slate_profile.slot." + slot.key() + ".none"; }

    private SampleLooks() {}
}
