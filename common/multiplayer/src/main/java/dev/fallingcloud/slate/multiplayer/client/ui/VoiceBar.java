package dev.fallingcloud.slate.multiplayer.client.ui;

import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateCard;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.multiplayer.client.SocialClient;
import dev.fallingcloud.slate.multiplayer.voice.VoiceStatus;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The voice strip at the bottom of the Friends page (only when Simple Voice Chat is installed): own
 * speaking/PTT indicator, mute and deafen toggles, the current voice group with a leave button.
 */
final class VoiceBar extends SlateCard {

    static final int HEIGHT = 26;

    private final SlateIconButton mute, deafen, leave;

    VoiceBar(final int x, final int y, final int w) {
        super(x, y, w, HEIGHT);
        flat();
        mute = add(new SlateIconButton(0, 0, 20, Icon.MIC, UiUtil.t("voice.mute"), () -> { VoiceStatus.toggleMuted(); refresh(); }), w - 70, 3);
        deafen = add(new SlateIconButton(0, 0, 20, Icon.HEADSET, UiUtil.t("voice.deafen"), () -> { VoiceStatus.toggleDeafened(); refresh(); }), w - 47, 3);
        leave = add(new SlateIconButton(0, 0, 20, Icon.EXIT, UiUtil.t("voice.leave_group"), () -> { VoiceStatus.leaveGroup(); refresh(); }), w - 24, 3);
        refresh();
    }

    void refresh() {
        mute.setIcon(VoiceStatus.isMuted() ? Icon.MIC_OFF : Icon.MIC).toggled(VoiceStatus.isMuted());
        deafen.setIcon(VoiceStatus.isDeafened() ? Icon.SPEAKER_OFF : Icon.HEADSET).toggled(VoiceStatus.isDeafened());
        leave.visible = VoiceStatus.currentGroup() != null;
    }

    @Override
    protected void renderContent(final GuiGraphics g, final int x, final int y, final int w, final int h, final int mouseX, final int mouseY, final float partialTick) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean connected = VoiceStatus.connected();
        final boolean talking = VoiceStatus.isSelfSpeaking() || VoiceStatus.isPttDown() || VoiceStatus.isSpeaking(SocialClient.get().selfUuid());
        final int dot = !connected ? p.textDim() : VoiceStatus.isDeafened() ? p.danger() : talking ? p.success() : p.textMuted();
        Icons.draw(g, connected ? Icon.SIGNAL : Icon.WIFI, x + 6, y + (h - 12) / 2, 12, dot);
        String line;
        if (!connected) line = "Voice chat not connected";
        else {
            final UUID gid = VoiceStatus.currentGroup();
            final String gname = gid == null ? "" : VoiceStatus.groupName(gid);
            line = gid == null ? (talking ? "Speaking" : VoiceStatus.isMuted() ? "Muted" : "Voice ready") : "In voice group " + (gname.isEmpty() ? "" : gname);
            if (VoiceStatus.isPttDown()) line += "  ·  PTT";
        }
        g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(line), w - 100), x + 22, y + (h - 9) / 2 + 1, Colors.withAlpha(connected ? p.text() : p.textDim(), 0xFF), t.isVanilla());
        if ((System.currentTimeMillis() / 500) % 4 == 0) refresh();
    }
}
