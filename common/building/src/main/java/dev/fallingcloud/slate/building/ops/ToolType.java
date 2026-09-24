package dev.fallingcloud.slate.building.ops;

import dev.fallingcloud.slate.building.client.BuildingIcons;
import dev.fallingcloud.slate.core.gfx.Icon;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The six building tools. Each unlocks a family of {@link BuildMode}s; its tier (1..4, see
 * {@code toolbox.ToolTier}) decides which modes of the family and how large they may be. Item ids are
 * {@code slate_building:<tier>_<id>}, the toolbox has one slot per type. Lang: {@code slate_building.tool.<id>}.
 *
 * <p>Skeleton-declared (design §7). Never reorder or rename: ids are stored in toolboxes and configs.
 */
public enum ToolType {
    TROWEL, HAMMER, BRUSH, BLUEPRINT, SQUARE, CHISEL;

    private static final ToolType[] VALUES = values();

    private final String id = name().toLowerCase(Locale.ROOT);

    public String id() { return id; }

    public Component displayName() { return Component.translatable("slate_building.tool." + id); }

    public Icon icon() { return BuildingIcons.tool(this); }

    public static @Nullable ToolType byId(final @Nullable String id) {
        if (id == null) return null;
        final String lower = id.toLowerCase(Locale.ROOT);
        for (final ToolType t : VALUES) if (t.id.equals(lower)) return t;
        return null;
    }
}
