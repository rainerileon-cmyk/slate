package dev.fallingcloud.slate.building.toolbox;

import dev.fallingcloud.slate.building.ops.BuildMode;
import dev.fallingcloud.slate.building.ops.BuildModes;
import dev.fallingcloud.slate.building.ops.ToolType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Everything a tool unlocks, by tier: its building modes (from {@link BuildModes}, so the list follows the mode table)
 * plus the abilities that are not modes - the hammer's in-world reshape wheel, the chisel wheel and in-world chiselling
 * (design §5, §9). Feeds the tool tooltips and the toolbox screen's capability panel.
 */
public final class ToolUnlocks {

    /**
     * One unlock.
     *
     * @param id   mode id, or ability id ({@code slate_building.ability.<id>})
     * @param mode the building mode, null for an ability
     * @param tool the tool it needs
     * @param tier the lowest tier (1..4)
     */
    public record Unlock(String id, @Nullable BuildMode mode, ToolType tool, int tier) {
        public Component name() {
            return mode != null ? mode.name() : Component.translatable("slate_building.ability." + id);
        }

        public Component description() {
            return mode != null ? mode.description() : Component.translatable("slate_building.ability." + id + ".desc");
        }

        public boolean unlockedBy(final ToolboxAccess.Capabilities caps) {
            return mode != null ? caps.unlocked(mode) : caps.tier(tool) >= tier;
        }
    }

    private static final Map<ToolType, List<Unlock>> CACHE = new EnumMap<>(ToolType.class);

    /** Unlocks of {@code tool}, lowest tier first (modes before abilities within a tier). */
    public static synchronized List<Unlock> of(final ToolType tool) {
        return CACHE.computeIfAbsent(tool, ToolUnlocks::build);
    }

    /** Unlocks of {@code tool} that need exactly {@code tier}. */
    public static List<Unlock> at(final ToolType tool, final int tier) {
        final List<Unlock> out = new ArrayList<>();
        for (final Unlock u : of(tool)) if (u.tier() == tier) out.add(u);
        return out;
    }

    /** Unlocks of {@code tool} available at {@code tier} (that tier and below). */
    public static List<Unlock> upTo(final ToolType tool, final int tier) {
        final List<Unlock> out = new ArrayList<>();
        for (final Unlock u : of(tool)) if (u.tier() <= tier) out.add(u);
        return out;
    }

    private static List<Unlock> build(final ToolType tool) {
        final List<Unlock> out = new ArrayList<>();
        for (final BuildMode m : BuildModes.all()) {
            if (m.tool() == tool) out.add(new Unlock(m.id(), m, tool, Math.max(1, m.minTier())));
        }
        switch (tool) {
            case HAMMER -> out.add(new Unlock("reshape_in_world", null, tool, 1));
            case CHISEL -> {
                out.add(new Unlock("chisel_wheel", null, tool, 1));
                out.add(new Unlock("chisel_in_world", null, tool, 2));
            }
            default -> { }
        }
        out.sort(Comparator.comparingInt(Unlock::tier));   // stable: keeps the table order inside a tier
        return List.copyOf(out);
    }

    private ToolUnlocks() {}
}
