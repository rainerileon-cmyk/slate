package dev.fallingcloud.slate.multiplayer.client;

import dev.fallingcloud.slate.core.layout.action.ActionType;
import dev.fallingcloud.slate.core.module.SlateModule;
import dev.fallingcloud.slate.multiplayer.social.SocialMessage;
import java.util.List;

/** TEMPORARY stub: replaced by the full client bootstrap once the client classes exist. */
public final class MultiplayerClient {

    public static void init() {}

    public static void onPayload(final SocialMessage m) {}

    public static List<SlateModule.HubEntry> hubEntries() { return List.of(); }

    public static List<ActionType> actions() { return List.of(); }

    private MultiplayerClient() {}
}
