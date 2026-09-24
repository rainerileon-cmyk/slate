package dev.fallingcloud.slate.building.config;

/**
 * {@code building-server.json → chisel} (design §9). The groups themselves (the server's own and the shipped
 * defaults, plus an exclude list) live in {@code config/slate/building-chisel.json}; changes to either apply on
 * {@code /reload}.
 *
 * <p>Owner: I (chisel).
 */
public final class ServerChisel {

    /** Master switch for chisel groups (off: no chisel pages anywhere, chisel requests are refused). */
    public boolean enabled = true;
    /** Build groups from 1:1 stonecutter recipes between full blocks (covers modded stone palettes, e.g. Create's). */
    public boolean stonecutterGroups = true;
    /** Build groups from vanilla block families (chiseled / cracked / cut / mosaic / polished). */
    public boolean blockFamilies = true;
    /** Read Rechiseled / Chipped / Chisel group data when those mods are present. */
    public boolean modCompat = true;
    /** Allow chiselling blocks in the world (Chisel tier 2); blocks whose loot is not themselves are always refused. */
    public boolean inWorld = true;
}
