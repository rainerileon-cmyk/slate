package dev.fallingcloud.slate.building.registry;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A handle to one queued registry entry, bound when the loader flushes {@link BuildingRegistry}. Hold it in a
 * {@code static final} field and call {@link #get()} whenever the object is needed, never during mod init: the
 * object does not exist until NeoForge's {@code RegisterEvent} / the Fabric entry's flush has run.
 *
 * @param <T> the registered object type (e.g. {@code Block}, a concrete block class, {@code Item})
 */
public final class RegistryRef<T> implements Supplier<T> {

    private final ResourceKey<? extends Registry<? super T>> registry;
    private final ResourceLocation id;
    private volatile @Nullable T value;

    RegistryRef(final ResourceKey<? extends Registry<? super T>> registry, final ResourceLocation id) {
        this.registry = registry;
        this.id = id;
    }

    /** The registered object. Throws if called before the loader flushed the registry queue (e.g. during init). */
    @Override
    public T get() {
        final T v = value;
        if (v == null) {
            throw new IllegalStateException("Slate Building registry entry " + id + " (" + registry.location()
                + ") used before registration - call RegistryRef.get() lazily, not during mod init");
        }
        return v;
    }

    /** Whether the loader has registered this entry yet. */
    public boolean isBound() { return value != null; }

    public ResourceLocation id() { return id; }

    /** The registry this entry lives in, e.g. {@code Registries.BLOCK}. */
    public ResourceKey<? extends Registry<? super T>> registry() { return registry; }

    void bind(final T object) {
        if (value != null && value != object) throw new IllegalStateException("Slate Building registry entry " + id + " bound twice");
        value = object;
    }

    @Override
    public String toString() { return "RegistryRef[" + id + (value == null ? ", unbound]" : "]"); }
}
