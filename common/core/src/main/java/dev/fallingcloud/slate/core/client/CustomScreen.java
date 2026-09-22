package dev.fallingcloud.slate.core.client;

import dev.fallingcloud.slate.core.layout.LayoutIdProvider;
import dev.fallingcloud.slate.core.screen.ScreenIds;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * A blank screen created in the dev mode ("new custom screen"), identified by {@code custom:<id>} so its
 * layout file holds everything on it. Opened through the {@code slate:open_screen} action.
 */
public final class CustomScreen extends SlateScreen implements LayoutIdProvider {

    private final String id;

    public CustomScreen(final String id, @Nullable final Screen parent) {
        super(Component.literal(id), parent);
        this.id = id;
        this.showHeader = false;
    }

    public String id() { return id; }

    @Override
    protected void build() {}

    /** The layout id is per custom screen, not per class. */
    @Override
    public String layoutId() { return "custom:" + id; }

    static {
        ScreenIds.register(CustomScreen.class, "slate:custom", "Custom screen");
    }
}
