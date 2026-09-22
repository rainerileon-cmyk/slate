package dev.fallingcloud.slate.core.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;

/**
 * Key mapping registration that works on both loaders: modules add mappings any time during init; the
 * loader layer drains the queue into the real registration (NeoForge's RegisterKeyMappingsEvent,
 * Fabric's KeyBindingHelper) and everything added later is registered immediately. Client only.
 */
public final class SlateKeys {

    public static final String CATEGORY = "key.categories.slate";

    private static final List<KeyMapping> PENDING = new ArrayList<>();
    private static Consumer<KeyMapping> sink;

    public static synchronized void register(final KeyMapping mapping) {
        if (sink != null) sink.accept(mapping);
        else PENDING.add(mapping);
    }

    /** Loader layer: install the real registrar and flush what was queued. */
    public static synchronized void install(final Consumer<KeyMapping> registrar) {
        sink = registrar;
        for (final KeyMapping m : PENDING) registrar.accept(m);
        PENDING.clear();
    }

    /** Loader layer (NeoForge): the queued mappings, for a one-shot event registration. */
    public static synchronized List<KeyMapping> drain() {
        final List<KeyMapping> out = List.copyOf(PENDING);
        PENDING.clear();
        return out;
    }

    private SlateKeys() {}
}
