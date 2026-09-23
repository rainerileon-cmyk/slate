package dev.fallingcloud.slate.building.registry;

import dev.fallingcloud.slate.building.SlateBuilding;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Loader-neutral registration queue. Common code declares entries during mod construction ({@link #register});
 * each loader entry flushes them when its loader allows registration: NeoForge freezes vanilla registries outside
 * {@code RegisterEvent}, so the NeoForge entry registers every entry of the event's registry from that event;
 * Fabric registers everything with {@code Registry.register} right after {@code Modules.register}, in the order
 * BLOCK, ITEM, BLOCK_ENTITY_TYPE, DATA_COMPONENT_TYPE, MENU, CREATIVE_MODE_TAB, then the rest (insertion order).
 * The factory runs once, at flush time, and binds the returned {@link RegistryRef}.
 *
 * <p>Owners add their own entries from their own classes (e.g. a recipe serializer from {@code OpsSystem.init()}),
 * as long as that happens during {@code SlateModule.init()}; registering after the flush throws.
 */
public final class BuildingRegistry {

    /**
     * One queued entry.
     *
     * @param <R> the element type of the target registry (e.g. {@code Block})
     */
    public static final class Entry<R> {
        private final ResourceKey<? extends Registry<R>> registry;
        private final ResourceLocation id;
        private final Supplier<? extends R> creator;
        private @Nullable R created;

        private Entry(final ResourceKey<? extends Registry<R>> registry, final ResourceLocation id, final Supplier<? extends R> creator) {
            this.registry = registry;
            this.id = id;
            this.creator = creator;
        }

        public ResourceKey<? extends Registry<R>> registry() { return registry; }

        public ResourceLocation id() { return id; }

        /** Loader flush: builds the object (once), binds its {@link RegistryRef} and returns it for registration. */
        public synchronized R create() {
            if (created == null) created = creator.get();
            return created;
        }

        @Override
        public String toString() { return registry.location() + " " + id; }
    }

    private static final List<Entry<?>> ENTRIES = new ArrayList<>();
    private static boolean closed;
    private static boolean bootstrapped;

    /**
     * Queues {@code slate_building:<path>} in {@code registry}. The returned ref is typed with the concrete class the
     * factory produces, so callers get e.g. a {@code RegistryRef<ShapeStairBlock>} for a block.
     *
     * @param <R> element type of the registry
     * @param <T> concrete type of the object
     */
    public static synchronized <R, T extends R> RegistryRef<T> register(final ResourceKey<? extends Registry<R>> registry,
                                                                       final String path, final Supplier<T> factory) {
        if (closed) throw new IllegalStateException("Slate Building registry entry " + path + " queued after the registries were flushed");
        final ResourceLocation id = SlateBuilding.id(path);
        for (final Entry<?> e : ENTRIES) {
            if (e.registry.equals(registry) && e.id.equals(id)) throw new IllegalArgumentException("Duplicate Slate Building registry entry " + e);
        }
        final RegistryRef<T> ref = new RegistryRef<>(registry, id);
        ENTRIES.add(new Entry<R>(registry, id, () -> {
            final T object = factory.get();
            ref.bind(object);
            return object;
        }));
        return ref;
    }

    /** Every queued entry, in insertion order. */
    public static synchronized List<Entry<?>> entries() {
        return List.copyOf(ENTRIES);
    }

    /** The queued entries of one registry, in insertion order. */
    public static synchronized List<Entry<?>> entries(final ResourceKey<? extends Registry<?>> registry) {
        final List<Entry<?>> out = new ArrayList<>();
        for (final Entry<?> e : ENTRIES) if (e.registry.equals(registry)) out.add(e);
        return out;
    }

    /**
     * Loader layer: no more entries may be queued (called when the flush starts, so a late {@link #register} fails
     * loudly instead of silently never registering).
     */
    public static synchronized void close() {
        closed = true;
    }

    /**
     * Declares the skeleton's own content by initialising the holder classes in dependency order (components before
     * items so nothing ever needs a component that is not queued yet). Called once from {@link SlateBuilding#init()}.
     */
    public static synchronized void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;
        BuildingComponents.init();
        BuildingBlocks.init();
        BuildingItems.init();
        BuildingBlockEntities.init();
        BuildingMenus.init();
        BuildingTabs.init();
    }

    private BuildingRegistry() {}
}
