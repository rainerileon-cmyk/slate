package dev.fallingcloud.slate.building.config;

/**
 * {@code building-server.json → toolbox} (design §8).
 *
 * <p>Owner: E (toolbox). Skeleton declares the fields and defaults of design §10.
 */
public final class ServerToolbox {

    /** Tool durability per tier (copper, iron, diamond, netherite); the registered max damage is the default. */
    public int[] durability = {250, 750, 2000, 5000};
    /** Operations draw materials from the toolbox pouch first. */
    public boolean allowPouch = true;
    /** Max distance to a Supply-Link container. */
    public int supplyLinkRange = 64;
}
