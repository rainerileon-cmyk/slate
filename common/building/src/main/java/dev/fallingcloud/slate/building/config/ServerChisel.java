package dev.fallingcloud.slate.building.config;

/**
 * {@code building-server.json → chisel} (design §9).
 *
 * <p>Owner: I (chisel). Skeleton declares the fields and defaults of design §10.
 */
public final class ServerChisel {

    /** Master switch for chisel groups. */
    public boolean enabled = true;
    /** Build groups from 1:1 stonecutter recipes between full blocks. */
    public boolean stonecutterGroups = true;
    /** Build groups from vanilla block families (chiseled / cracked / cut / mosaic / polished). */
    public boolean blockFamilies = true;
    /** Read Rechiseled / Chipped / Chisel group data when those mods are present. */
    public boolean modCompat = true;
    /** Allow chiselling blocks in the world (Chisel tier 2). */
    public boolean inWorld = true;
}
