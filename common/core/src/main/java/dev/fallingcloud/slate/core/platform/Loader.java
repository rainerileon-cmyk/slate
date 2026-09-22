package dev.fallingcloud.slate.core.platform;

public enum Loader {
    NEOFORGE("NeoForge"), FABRIC("Fabric");

    public final String displayName;

    Loader(final String displayName) { this.displayName = displayName; }
}
