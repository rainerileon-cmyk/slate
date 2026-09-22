package dev.fallingcloud.slate.core.module;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.action.ActionType;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * A Slate module (one of the suite's mods). Implemented once per module and handed to
 * {@link Modules#register(SlateModule)} from the loader entry point on both sides.
 */
public interface SlateModule {

    /** The mod id, e.g. {@code slate_menu}. */
    String id();

    Component displayName();

    Icon icon();

    /** Both sides, during mod construction. Register payloads, configs and server hooks here. */
    default void init() {}

    /** Client only, after {@link #init()}. Register screens, keys, layout element types here. */
    default void initClient() {}

    /** Buttons this module contributes to the Slate hub screen. Client only. */
    default List<HubEntry> hubEntries() { return List.of(); }

    /** Dev-mode actions this module contributes (e.g. {@code slate_menu:open_screenshots}). */
    default List<ActionType> actions() { return List.of(); }

    /** One hub button: label, icon, and what it opens. */
    record HubEntry(Component label, Icon icon, Runnable onClick) {}
}
