package dev.fallingcloud.slate.multiplayer.voice;

import de.maxhenkel.voicechat.VoicechatClient;
import de.maxhenkel.voicechat.api.Group;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.net.CreateGroupPacket;
import de.maxhenkel.voicechat.net.JoinGroupPacket;
import de.maxhenkel.voicechat.net.LeaveGroupPacket;
import de.maxhenkel.voicechat.plugins.impl.VoicechatClientApiImpl;
import de.maxhenkel.voicechat.voice.client.ClientManager;
import de.maxhenkel.voicechat.voice.client.ClientPlayerStateManager;
import de.maxhenkel.voicechat.voice.client.ClientVoicechat;
import de.maxhenkel.voicechat.voice.client.MicThread;
import de.maxhenkel.voicechat.voice.common.ClientGroup;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

/**
 * The ONLY class that touches Simple Voice Chat's status/group/volume internals (the clip recorder has
 * its own adapter in {@code voice.clips}). Loaded lazily by {@link VoiceStatus} after the mod-presence
 * check. Verified against voicechat 2.6.x for 1.21.1 (see the javap notes in the module report):
 * <ul>
 *   <li>{@code VoicechatClientApiImpl.instance()} - {@code isTalking(UUID)}, {@code isWhispering(UUID)}</li>
 *   <li>{@code ClientManager.getPlayerStateManager()} - mute/disable (deafen) state, own group id, player states</li>
 *   <li>{@code ClientManager.getGroupManager().getGroup(UUID)} - group names</li>
 *   <li>{@code ClientManager.getPttKeyHandler().isPTTDown()} - push-to-talk state</li>
 *   <li>{@code VoicechatClient.PLAYER_VOLUME_CONFIG} - per-player volumes</li>
 *   <li>{@code CreateGroupPacket / JoinGroupPacket / LeaveGroupPacket} - sent as vanilla custom payloads on the
 *       play connection, which is exactly what voicechat's own NetManager does on either loader</li>
 * </ul>
 */
final class SvcAdapter {

    /** Resolving these is the whole test; a NoClassDefFoundError/NoSuchMethodError surfaces in the caller. */
    static void probe() {
        VoicechatClientApiImpl.instance();
        ClientManager.getPlayerStateManager();
        ClientManager.getGroupManager();
    }

    private static VoicechatClientApi api() { return VoicechatClientApiImpl.instance(); }

    private static ClientPlayerStateManager states() { return ClientManager.getPlayerStateManager(); }

    boolean connected() {
        final ClientVoicechat c = ClientManager.getClient();
        return c != null && c.getConnection() != null && c.getConnection().isConnected();
    }

    boolean isTalking(final UUID player) { return api().isTalking(player); }

    boolean isWhispering(final UUID player) { return api().isWhispering(player); }

    boolean selfTalking() {
        final ClientVoicechat c = ClientManager.getClient();
        final MicThread mic = c == null ? null : c.getMicThread();
        return mic != null && (mic.isTalking() || mic.isWhispering());
    }

    boolean hasState(final UUID player) {
        return states().getState(player) != null && !states().isPlayerDisconnected(player);
    }

    boolean isPlayerDisabled(final UUID player) { return states().isPlayerDisabled(player); }

    boolean isMuted() { return states().isMuted(); }

    void setMuted(final boolean muted) { states().setMuted(muted); }

    boolean isDisabled() { return states().isDisabled(); }

    void setDisabled(final boolean disabled) { states().setDisabled(disabled); }

    boolean isPttDown() { return ClientManager.getPttKeyHandler().isPTTDown(); }

    double volume(final UUID player) { return VoicechatClient.PLAYER_VOLUME_CONFIG.getVolume(player, 1.0D); }

    void setVolume(final UUID player, final double volume) {
        VoicechatClient.PLAYER_VOLUME_CONFIG.setVolume(player, volume);
        VoicechatClient.PLAYER_VOLUME_CONFIG.save();
    }

    @Nullable UUID groupId() { return states().getGroupID(); }

    String groupName(final UUID id) {
        final ClientGroup g = ClientManager.getGroupManager().getGroup(id);
        return g == null ? "" : g.getName();
    }

    void createGroup(final String name) {
        String n = name == null ? "" : name.strip().replaceAll("[^A-Za-z0-9 _-]", "");
        if (n.isEmpty()) n = "Friends";
        if (n.length() > 16) n = n.substring(0, 16);
        send(new CreateGroupPacket(n, null, Group.Type.NORMAL));
    }

    void joinGroup(final UUID id) { send(new JoinGroupPacket(id, null)); }

    void leaveGroup() { send(new LeaveGroupPacket()); }

    private static void send(final CustomPacketPayload packet) {
        final ClientPacketListener c = Minecraft.getInstance().getConnection();
        if (c != null) c.send(new ServerboundCustomPayloadPacket(packet));
    }
}
