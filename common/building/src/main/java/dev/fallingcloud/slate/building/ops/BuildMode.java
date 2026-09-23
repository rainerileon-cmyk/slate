package dev.fallingcloud.slate.building.ops;

import dev.fallingcloud.slate.building.client.BuildingIcons;
import dev.fallingcloud.slate.core.gfx.Icon;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A building mode (fill, walls, copy, mirror, ...). All of them are declared in {@link BuildModes}; the planner
 * that turns a selection into block changes is {@code Planners.of(mode)}.
 *
 * @param id      stable id: lang ({@code slate_building.mode.<id>} / {@code .desc}), keybind
 *                ({@code key.slate_building.mode.<id>}), configs and payloads
 * @param kind    how the selection works in the world
 * @param tool    the tool that unlocks it, null when always available (measure)
 * @param minTier the lowest tool tier (1..4) that unlocks it; 0 with no tool
 * @param params  adjustable parameters, in display order
 * @param places  whether it can put blocks into the world
 * @param breaks  whether it can remove or replace existing blocks (protection checks, hammer tier for drops)
 */
public record BuildMode(String id, ModeKind kind, @Nullable ToolType tool, int minTier, List<ModeParam> params,
                        boolean places, boolean breaks) {

    public BuildMode {
        params = List.copyOf(params);
    }

    public Component name() {
        return Component.translatable("slate_building.mode." + id);
    }

    public Component description() {
        return Component.translatable("slate_building.mode." + id + ".desc");
    }

    public Icon icon() {
        return BuildingIcons.mode(id);
    }

    /** The parameter with {@code paramId}, or null. */
    public @Nullable ModeParam param(final String paramId) {
        for (final ModeParam p : params) if (p.id().equals(paramId)) return p;
        return null;
    }

    /** Whether the mode changes the world at all (false for measure and copy). */
    public boolean changesWorld() {
        return places || breaks;
    }

    /** Name of this mode's keybind ({@code key.slate_building.mode.<id>}). */
    public String keyName() {
        return "key.slate_building.mode." + id;
    }
}
