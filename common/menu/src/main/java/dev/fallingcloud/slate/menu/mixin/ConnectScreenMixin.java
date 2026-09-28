package dev.fallingcloud.slate.menu.mixin;

import dev.fallingcloud.slate.menu.client.loading.LoadingScreens;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Joining a server: drawn by {@link LoadingScreens} as "Joining &lt;server&gt;" with vanilla's status ("Logging in...")
 * and its Cancel button in the card; vanilla's narration kept. The connection itself runs on this screen instance.
 */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin extends Screen {

    @Shadow private Component status;
    @Shadow private long lastNarration;

    /** The server's name from the list (its address for a direct connect), remembered when the connection starts. */
    @Unique @Nullable private Component slate$server;

    protected ConnectScreenMixin(final Component title) {
        super(title);
    }

    @Inject(method = "connect", at = @At("HEAD"))
    private void slate$remember(final Minecraft mc, final ServerAddress address, @Nullable final ServerData data, @Nullable final TransferState transfer,
                                final CallbackInfo ci) {
        this.slate$server = Component.literal(data != null && data.name != null && !data.name.isBlank() ? data.name : address.getHost());
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void slate$render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick, final CallbackInfo ci) {
        if (!LoadingScreens.active()) return;
        ci.cancel();
        final long now = Util.getMillis();
        if (now - this.lastNarration > 2000L) {
            this.lastNarration = now;
            this.minecraft.getNarrator().sayNow(Component.translatable("narrator.joining"));
        }
        final List<AbstractWidget> buttons = new ArrayList<>();
        for (final GuiEventListener c : this.children()) if (c instanceof AbstractWidget w) buttons.add(w);
        final Component title = this.slate$server != null ? Component.translatable("slate_menu.loading.joining", this.slate$server)
            : Component.translatable("slate_menu.loading.joining_server");
        LoadingScreens.render(this, g, mouseX, mouseY, partialTick, title, this.status, -1, null, buttons);
    }
}
