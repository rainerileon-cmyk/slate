package dev.fallingcloud.slate.menu.client.servers;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateLabel;
import dev.fallingcloud.slate.core.widget.SlateModal;
import dev.fallingcloud.slate.core.widget.SlateSegmented;
import dev.fallingcloud.slate.core.widget.SlateTextField;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** Add / edit server dialog: name, address, resource-pack prompt setting. Validates the address like vanilla. */
public final class ServerDialog {

    public static void open(final Component title, @Nullable final ServerData existing, final Consumer<ServerData> onOk) {
        open(title, existing == null ? I18n.get("selectServer.defaultName") : existing.name, existing == null ? "" : existing.ip,
            existing == null ? ServerData.ServerPackStatus.PROMPT : existing.getResourcePackStatus(), existing, onOk);
    }

    private static void open(final Component title, final String initialName, final String initialAddress, final ServerData.ServerPackStatus initialPack,
                             @Nullable final ServerData existing, final Consumer<ServerData> onOk) {
        final int w = SlateModal.WIDTH - 24;
        final SlateTextField name = new SlateTextField(0, 0, w, Component.translatable("addServer.enterName"));
        name.placeholder(Component.translatable("addServer.enterName")).text(initialName).maxLength(128);
        final SlateTextField address = new SlateTextField(0, 0, w, Component.translatable("addServer.enterIp"));
        address.placeholder(Component.translatable("addServer.enterIp")).text(initialAddress).maxLength(128);
        address.setInvalid(!initialAddress.isEmpty() && !ServerAddress.isValidAddress(initialAddress));
        address.onChange(s -> address.setInvalid(!s.isBlank() && !ServerAddress.isValidAddress(s)));
        final SlateSegmented<ServerData.ServerPackStatus> pack = new SlateSegmented<>(0, 0, w,
            List.of(ServerData.ServerPackStatus.ENABLED, ServerData.ServerPackStatus.PROMPT, ServerData.ServerPackStatus.DISABLED),
            initialPack, ServerData.ServerPackStatus::getName, null);
        final SlateModal modal = new SlateModal(title, null, Icon.SERVER)
            .extra(new SlateLabel(0, 0, w, Component.translatable("addServer.enterName")).style(SlateLabel.Style.CAPTION))
            .extra(name)
            .extra(new SlateLabel(0, 0, w, Component.translatable("addServer.enterIp")).style(SlateLabel.Style.CAPTION))
            .extra(address)
            .extra(new SlateLabel(0, 0, w, Component.translatable("addServer.resourcePack")).style(SlateLabel.Style.CAPTION))
            .extra(pack);
        final Runnable ok = () -> {
            dev.fallingcloud.slate.core.widget.popup.Popups.close(modal);       // Enter in a field lands here without the button wrapper
            final String n = name.getValue().trim(), a = address.getValue().trim();
            if (a.isEmpty() || !ServerAddress.isValidAddress(a)) {
                SlateToasts.show(Component.translatable("slate_menu.servers.invalid_address"), Component.literal(a), Icon.WARNING);
                open(title, n, a, pack.value(), existing, onOk);
                return;
            }
            final ServerData d = existing != null ? existing : new ServerData(n, a, ServerData.Type.OTHER);
            d.name = n.isEmpty() ? a : n;
            d.ip = a;
            d.setResourcePackStatus(pack.value());
            onOk.accept(d);
        };
        name.onEnter(ok);
        address.onEnter(ok);
        modal.button(Component.translatable("gui.cancel"), SlateButton.Variant.SECONDARY, null)
            .button(existing == null ? Component.translatable("addServer.add") : Component.translatable("gui.done"), SlateButton.Variant.PRIMARY, ok)
            .show();
    }

    private ServerDialog() {}
}
