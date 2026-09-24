package dev.fallingcloud.slate.building.variant;

import dev.fallingcloud.slate.building.client.BuildingIcons;
import dev.fallingcloud.slate.core.gfx.Icon;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Every shape a material can take. Declaration order is the default wheel order; {@link #id()} (the lower-case
 * name) is what configs, payloads and lang keys use ({@code slate_building.shape.<id>}), and also the registry path
 * of the shape block that realises it ({@code slate_building:<id>}).
 *
 * <p>Skeleton-declared (owner of the model: A). Adding constants at the END is allowed; never reorder or rename,
 * wheel configs and synced ids depend on the ids.
 */
public enum Shape {
    FULL, STAIRS, SLAB, VERTICAL_SLAB, VERTICAL_STAIRS, WALL, FENCE, STEP, PANEL,
    FENCE_GATE, VERTICAL_STEP, POST, LAYER, PANE;

    private static final Shape[] VALUES = values();

    private final String id = name().toLowerCase(Locale.ROOT);

    public String id() { return id; }

    public Component displayName() { return Component.translatable("slate_building.shape." + id); }

    /** Glyph for wheels, the build menu and tooltips. Safe on a dedicated server (plain enum constant). */
    public Icon icon() { return BuildingIcons.shape(this); }

    /** Whether Slate Building has its own block for this shape (every shape except {@link #FULL}, the material itself). */
    public boolean custom() { return this != FULL; }

    /**
     * Material units ONE item / one freshly placed block of this shape is worth: always 1 (economy rule, design §1).
     * States that hold more (double slabs, stacked layers) report it per state through {@code ShapeBlock.units}.
     */
    public int units() { return 1; }

    /** Lookup by {@link #id()} (case-insensitive); null when unknown (e.g. from an old config or a newer client). */
    public static @Nullable Shape byId(final @Nullable String id) {
        if (id == null) return null;
        final String lower = id.toLowerCase(Locale.ROOT);
        for (final Shape s : VALUES) if (s.id.equals(lower)) return s;
        return null;
    }
}
