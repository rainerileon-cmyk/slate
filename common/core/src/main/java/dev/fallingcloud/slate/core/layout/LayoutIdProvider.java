package dev.fallingcloud.slate.core.layout;

/**
 * A screen whose layout id is per instance rather than per class. {@code ScreenIds.of} keys layouts by
 * class, which is right for every vanilla and module screen but not for {@code CustomScreen}: every
 * custom screen is the same class and must own its own layout file ({@code custom:<id>}).
 * {@link LayoutApplier#layoutId(net.minecraft.client.gui.screens.Screen)} prefers this over the class id.
 */
public interface LayoutIdProvider {

    /** The layout id, e.g. {@code custom:main_hub}. Never null or blank. */
    String layoutId();
}
