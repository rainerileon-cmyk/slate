package dev.fallingcloud.slate.building.variant.client;

import dev.fallingcloud.slate.building.client.ServerSettingsClient;
import dev.fallingcloud.slate.building.mixin.variant.CreativeModeTabsAccessor;
import dev.fallingcloud.slate.building.variant.VariantRegistry;
import dev.fallingcloud.slate.core.event.SlateEvents;

/**
 * Client init of the variant system: when the rules in effect may have changed (joined a server, the server's rules
 * arrived or changed, left), the registry snapshot and the creative tabs are refreshed, so "delete native variants"
 * and the other rules show at once. Also registers the {@code variants} dev-harness scenario.
 */
public final class VariantClient {

    private static boolean initialised;

    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        SlateEvents.CLIENT_JOINED_SERVER.register(VariantClient::rulesMayHaveChanged);
        SlateEvents.CLIENT_LEFT_SERVER.register(VariantClient::rulesMayHaveChanged);
        ServerSettingsClient.onChange(VariantClient::rulesMayHaveChanged);
        VariantHarness.register();
    }

    private static void rulesMayHaveChanged() {
        VariantRegistry.invalidate();
        // The next time the creative inventory opens, every tab is rebuilt (and stripped of natives if deleted).
        CreativeModeTabsAccessor.slateBuilding$setCachedParameters(null);
    }

    private VariantClient() {}
}
