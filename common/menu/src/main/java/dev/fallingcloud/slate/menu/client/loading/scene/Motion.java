package dev.fallingcloud.slate.menu.client.loading.scene;

import java.util.ArrayList;
import java.util.List;

/** Everything in the factory that moves, as it stands in the frame being drawn. */
final class Motion {

    /** A thing on its way along the belts. */
    static final class Item {
        /**
         * How far along the second belt it has come, in blocks; negative while it is still on the first, which it
         * leaves at 0.
         */
        float s;
        /** Whether the press has come down on it. */
        boolean pressed;
        /** Seconds since it left the end of the belt; negative while it is on it. */
        float leaving = -1f;
        /** A number of its own, for what differs from thing to thing: how it lies. */
        int id;
    }

    static final class Spark {
        float x, y, z, vx, vy, vz, life, span, size;
        int colour;
        boolean smoke;
    }

    /** Seconds since the scene began. */
    float time;
    /** The memory in use, all of the loading, and the task at hand as its dial shows it: 0 to 1, eased. */
    float memory = -1f, overall, needle;
    /** How far the task at hand has come, 0 to 1. */
    float lump;
    /** How far each belt has run, in blocks. */
    float beltA, beltB;
    /** The press: 0 up, 1 down; and how far into its stroke it is in seconds, negative at rest. */
    float pressDown, stroke = -1f;
    /** 0 when a thing has just reached the store, 1 a moment later. */
    float landed = 1f;
    final List<Item> items = new ArrayList<>();
    final List<Spark> sparks = new ArrayList<>();
}
