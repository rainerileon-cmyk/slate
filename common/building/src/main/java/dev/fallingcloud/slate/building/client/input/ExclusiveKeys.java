package dev.fallingcloud.slate.building.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.mixin.core.KeyMappingAccessor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import net.minecraft.client.KeyMapping;
import org.jetbrains.annotations.Nullable;

/**
 * Lets one of our key mappings take its key for itself. The DF pack has six mods on Left Alt and two on R; a
 * mapping claimed here with {@link #claim} gets the key EXCLUSIVELY whenever its {@code wantsNow} condition is true
 * at press time: vanilla's {@code KeyMapping.set(key, true)} / {@code KeyMapping.click(key)} then only reach the
 * claimant (other mappings bound to that key are not set down and get no click). When the condition is false,
 * nothing changes and every mapping sees the key as usual. Releases always propagate to everyone, so no other
 * mapping can get stuck down.
 *
 * <p>Fed by {@code mixin.core.KeyMappingMixin} (static {@code KeyMapping.set} / {@code click} HEAD, plus
 * {@code setAll}/{@code releaseAll} TAIL to keep the state right across screens). Client only.
 */
public final class ExclusiveKeys {

    private record Claim(KeyMapping mapping, BooleanSupplier wantsNow) {}

    private static final List<Claim> CLAIMS = new CopyOnWriteArrayList<>();
    /** Keys currently held exclusively → their owner (render thread only). */
    private static final Map<InputConstants.Key, KeyMapping> HELD = new HashMap<>();

    /**
     * Claims {@code mapping}'s key (whatever it is currently bound to) while {@code wantsNow} is true at press time.
     * Claiming the same mapping again replaces its condition.
     */
    public static void claim(final KeyMapping mapping, final BooleanSupplier wantsNow) {
        CLAIMS.removeIf(c -> c.mapping == mapping);
        CLAIMS.add(new Claim(mapping, wantsNow));
    }

    public static void unclaim(final KeyMapping mapping) {
        CLAIMS.removeIf(c -> c.mapping == mapping);
    }

    /** Whether {@code mapping} currently holds its key exclusively (pressed while its claim wanted it). */
    public static boolean isHeldExclusively(final KeyMapping mapping) {
        return HELD.containsValue(mapping);
    }

    // ---- called by KeyMappingMixin ----

    /** {@code KeyMapping.set(key, down)} HEAD; true = cancel vanilla (we set the owner ourselves). */
    public static boolean onSet(final InputConstants.Key key, final boolean down) {
        if (!down) {
            HELD.remove(key);
            return false;                               // releases reach every mapping
        }
        KeyMapping owner = HELD.get(key);               // key repeat: keep the owner of this hold
        if (owner == null) owner = resolve(key);
        if (owner == null) return false;
        HELD.put(key, owner);
        owner.setDown(true);
        return true;
    }

    /** {@code KeyMapping.click(key)} HEAD; true = cancel vanilla (we counted the owner's click ourselves). */
    public static boolean onClick(final InputConstants.Key key) {
        final KeyMapping owner = HELD.get(key);
        if (owner == null) return false;
        final KeyMappingAccessor acc = (KeyMappingAccessor) owner;
        acc.slateBuilding$setClickCount(acc.slateBuilding$clickCount() + 1);
        return true;
    }

    /**
     * {@code KeyMapping.setAll()} TAIL: vanilla just re-synced every mapping with the physical key state (a screen
     * closed). Keep exclusive holds exclusive and forget the ones whose key is up now.
     */
    public static void afterSetAll() {
        HELD.entrySet().removeIf(e -> !e.getValue().isDown());
        for (final Map.Entry<InputConstants.Key, KeyMapping> e : HELD.entrySet()) {
            for (final KeyMapping other : allMappings()) {
                if (other != e.getValue() && e.getKey().equals(keyOf(other))) other.setDown(false);
            }
        }
    }

    /** {@code KeyMapping.releaseAll()} TAIL: every mapping was released (a screen opened). */
    public static void afterReleaseAll() {
        HELD.clear();
    }

    private static @Nullable KeyMapping resolve(final InputConstants.Key key) {
        for (final Claim c : CLAIMS) {
            if (c.mapping.isUnbound() || !key.equals(keyOf(c.mapping))) continue;
            try {
                if (c.wantsNow.getAsBoolean()) return c.mapping;
            } catch (final RuntimeException e) {
                SlateBuilding.LOGGER.error("[Slate Building] key claim of {} failed", c.mapping.getName(), e);
            }
        }
        return null;
    }

    private static InputConstants.Key keyOf(final KeyMapping mapping) {
        return ((KeyMappingAccessor) mapping).slateBuilding$key();
    }

    private static KeyMapping[] allMappings() {
        return net.minecraft.client.Minecraft.getInstance().options.keyMappings;
    }

    private ExclusiveKeys() {}
}
