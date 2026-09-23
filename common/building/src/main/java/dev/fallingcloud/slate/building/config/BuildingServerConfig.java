package dev.fallingcloud.slate.building.config;

/**
 * Server rules, {@code config/slate/building-server.json} ({@code SlateBuilding.serverConfig()} for the server in
 * this process). The whole file is synced to joining clients ({@code ServerSettingsSync}); code that must obey the
 * rules of the server the player is on reads {@link BuildingServerSettings#effective}. One section per owner, each
 * in its own class.
 */
public final class BuildingServerConfig {

    /** Variant unification, recipes, native-variant deletion. Owner: A. */
    public ServerVariants variants = new ServerVariants();
    /** Building-mode limits and rules. Owner: D1. */
    public ServerOps ops = new ServerOps();
    /** Toolbox and tool rules. Owner: E. */
    public ServerToolbox toolbox = new ServerToolbox();
    /** Chisel groups. Owner: I. */
    public ServerChisel chisel = new ServerChisel();
}
