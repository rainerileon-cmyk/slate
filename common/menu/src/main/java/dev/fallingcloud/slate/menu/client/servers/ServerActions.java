package dev.fallingcloud.slate.menu.client.servers;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Joining and looking up servers through the vanilla paths ({@code servers.dat}, {@link ConnectScreen}). */
public final class ServerActions {

    /** Connects exactly like vanilla's server list does (keeps the entry's resource-pack setting). */
    public static void join(final ServerData data, @Nullable final Screen parent) {
        final Minecraft mc = Minecraft.getInstance();
        if (data == null || data.ip == null || data.ip.isBlank()) return;
        ConnectScreen.startConnecting(parent != null ? parent : new TitleScreen(), mc, ServerAddress.parseString(data.ip), data, false, null);
    }

    /** Joins by address, reusing the saved list entry when one matches (so its pack prompt setting applies). */
    public static void join(final String address, @Nullable final String name, @Nullable final Screen parent) {
        if (address == null || address.isBlank()) return;
        final ServerData data = find(address).orElseGet(() -> new ServerData(name == null || name.isBlank() ? address : name, address, ServerData.Type.OTHER));
        join(data, parent);
    }

    /** The saved entry with this address, if any (case-insensitive, whitespace-trimmed). */
    public static Optional<ServerData> find(final String address) {
        if (address == null) return Optional.empty();
        final String key = normalize(address);
        final ServerList list = new ServerList(Minecraft.getInstance());
        list.load();
        for (int i = 0; i < list.size(); i++) {
            final ServerData d = list.get(i);
            if (normalize(d.ip).equals(key)) return Optional.of(d);
        }
        return Optional.empty();
    }

    public static String normalize(final String address) {
        return address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
    }

    public static void copyAddress(final String address) {
        Minecraft.getInstance().keyboardHandler.setClipboard(address == null ? "" : address);
        SlateToasts.show(Component.translatable("slate.copied"), Component.literal(address == null ? "" : address), Icon.COPY);
    }

    private ServerActions() {}
}
