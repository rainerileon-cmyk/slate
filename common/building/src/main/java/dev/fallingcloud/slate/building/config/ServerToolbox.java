package dev.fallingcloud.slate.building.config;

/**
 * {@code building-server.json → toolbox} (design §8): the rules for toolboxes and building tools. Synced to clients
 * with the rest of the server settings, so the toolbox screen shows what this server allows.
 *
 * <p>Owner: E (toolbox).
 */
public final class ServerToolbox {

    /**
     * Tool durability per tier (copper, iron, diamond, netherite). The registered max damage is the default; a
     * different value is applied to a tool (as its {@code max_damage} component) the first time an operation wears it.
     */
    public int[] durability = {250, 750, 2000, 5000};
    /**
     * The toolbox pouch: when false, operations never draw from it and its slots take no new items (what is already
     * inside can still be taken out).
     */
    public boolean allowPouch = true;
    /** Max distance in blocks from the player to a Supply Link container (same dimension, loaded chunk). */
    public int supplyLinkRange = 64;
}
