package dev.fallingcloud.slate.building.config;

/**
 * {@code building.json → placement}: the Accurate Block Placement behaviour built into Slate Building
 * ({@code client.place.AccuratePlacement}).
 */
public final class PlacementSettings {

    /** Holding use places a block every time the crosshair reaches a new spot, instead of every 4 ticks. */
    public boolean accurate = true;
    /** No 5-tick pause between blocks while holding attack. */
    public boolean fastBreaking = false;
    /** A chat line when a toggle key flips one of the two. */
    public boolean toggleMessage = true;
}
