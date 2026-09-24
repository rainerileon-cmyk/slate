package dev.fallingcloud.slate.building.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.mixin.core.KeyMappingAccessor;
import dev.fallingcloud.slate.core.event.SlateEvents;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Lets one of our key mappings take its key for itself. The DF pack has six mods on Left Alt and two on R; a
 * mapping claimed here with {@link #claim} gets the key EXCLUSIVELY whenever its {@code wantsNow} condition is true
 * at press time: vanilla's {@code KeyMapping.set(key, true)} / {@code KeyMapping.click(key)} then only reach the
 * claimant (other mappings bound to that key are not set down and get no click). When the condition is false,
 * nothing changes and every mapping sees the key as usual. Releases always propagate to everyone, so no other
 * mapping can get stuck down.
 *
 * <p>Works whether or not the claimed mapping owns the key's lookup slot. Fabric (vanilla) keeps ONE mapping per key
 * in {@code KeyMapping.MAP}, and vanilla's {@code set(key, false)} only releases that one; when another mod's mapping
 * holds the slot, our claimant would never see its release. So a release releases every claimed mapping on the key
 * itself, and a per-tick check of the physical key state releases them too when a release event never arrived (a
 * claimant set down by {@code setAll()} after a screen closed, another mod swallowing the event). NeoForge looks up
 * every mapping of a key, where this is redundant but harmless.
 *
 * <p>Fed by {@code mixin.core.KeyMappingMixin} (static {@code KeyMapping.set} / {@code click} HEAD, plus
 * {@code setAll}/{@code releaseAll} TAIL to keep the state right across screens). Client only, render thread only.
 */
public final class ExclusiveKeys {

    private record Claim(KeyMapping mapping, BooleanSupplier wantsNow) {}

    private static final List<Claim> CLAIMS = new CopyOnWriteArrayList<>();
    /** Keys currently held exclusively → their owner (render thread only). */
    private static final Map<InputConstants.Key, KeyMapping> HELD = new HashMap<>();
    /** Physical state of each claimed key at the last tick (only a physical down → up edge releases). */
    private static final Map<InputConstants.Key, Boolean> PHYSICAL = new HashMap<>();
    private static boolean initialised;

    /** Starts the per-tick physical-key check. Idempotent; called from {@code KeyClaims.init()}. */
    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        SlateEvents.CLIENT_TICK_END.register(ExclusiveKeys::tick);
    }

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
            release(key);
            return false;                               // vanilla still releases every other mapping
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
     * closed). Keep exclusive holds exclusive, forget the ones whose key is up now, and treat a claimant that came back
     * down as a fresh press (exclusive when its claim wants the key now).
     */
    public static void afterSetAll() {
        HELD.entrySet().removeIf(e -> !e.getValue().isDown());
        for (final Claim c : CLAIMS) {
            if (!c.mapping.isDown() || c.mapping.isUnbound()) continue;
            final InputConstants.Key key = keyOf(c.mapping);
            if (!HELD.containsKey(key) && wants(c)) HELD.put(key, c.mapping);
        }
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

    // ---- release ----

    /** {@code key} went up: release its exclusive owner and every claimed mapping bound to it. */
    private static void release(final InputConstants.Key key) {
        final KeyMapping owner = HELD.remove(key);
        if (owner != null) owner.setDown(false);        // Fabric's set() only reaches MAP.get(key), maybe not the owner
        for (final Claim c : CLAIMS) {                  // also a claimant that setAll() put down without a claim
            if (c.mapping.isDown() && key.equals(keyOf(c.mapping))) c.mapping.setDown(false);
        }
    }

    /**
     * Once per client tick: a claimed key that was physically down last tick and is up now is released, whether or not
     * its release event reached {@code KeyMapping.set}. Only that edge counts, so presses fed in by code (the dev
     * harness) are never undone, and a key whose state cannot be read (scancode bindings) is left alone.
     */
    private static void tick() {
        final Minecraft mc = Minecraft.getInstance();
        if (CLAIMS.isEmpty() || mc.getWindow() == null) return;
        final long window = mc.getWindow().getWindow();
        final Set<InputConstants.Key> keys = new LinkedHashSet<>();
        for (final Claim c : CLAIMS) if (!c.mapping.isUnbound()) keys.add(keyOf(c.mapping));
        PHYSICAL.keySet().retainAll(keys);              // rebinding drops the old key
        for (final InputConstants.Key key : keys) {
            final Boolean now = physicallyDown(window, key);
            if (now == null) continue;
            final Boolean before = PHYSICAL.put(key, now);
            if (Boolean.TRUE.equals(before) && !now) release(key);
        }
    }

    private static @Nullable Boolean physicallyDown(final long window, final InputConstants.Key key) {
        final int code = key.getValue();
        if (code == InputConstants.UNKNOWN.getValue()) return null;
        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window, code);
            case MOUSE -> GLFW.glfwGetMouseButton(window, code) == GLFW.GLFW_PRESS;
            default -> null;
        };
    }

    // ---- helpers ----

    private static @Nullable KeyMapping resolve(final InputConstants.Key key) {
        for (final Claim c : CLAIMS) {
            if (c.mapping.isUnbound() || !key.equals(keyOf(c.mapping))) continue;
            if (wants(c)) return c.mapping;
        }
        return null;
    }

    private static boolean wants(final Claim c) {
        try {
            return c.wantsNow.getAsBoolean();
        } catch (final RuntimeException e) {
            SlateBuilding.LOGGER.error("[Slate Building] key claim of {} failed", c.mapping.getName(), e);
            return false;
        }
    }

    private static InputConstants.Key keyOf(final KeyMapping mapping) {
        return ((KeyMappingAccessor) mapping).slateBuilding$key();
    }

    private static KeyMapping[] allMappings() {
        return Minecraft.getInstance().options.keyMappings;
    }

    private ExclusiveKeys() {}
}
