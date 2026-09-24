package dev.fallingcloud.slate.building.client.wheel;

import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * One page of the wheel: a configured wheel (or a part of one, when it has more entries than {@code maxSlices}), or
 * one chisel group.
 *
 * @param key    stable identity across rebuilds ("wheel:0:1", "chisel:rechiseled:0"), to keep the page on refresh
 * @param name   shown above the wheel
 * @param kind   SHAPE or CHISEL page
 * @param slices the ring, clockwise from the top
 */
public record WheelPage(String key, Component name, WheelSlice.Kind kind, List<WheelSlice> slices) {

    public WheelPage {
        slices = List.copyOf(slices);
    }

    /** Index of the slice that is the current shape/material, or -1. */
    public int currentIndex() {
        for (int i = 0; i < slices.size(); i++) if (slices.get(i).current()) return i;
        return -1;
    }
}
