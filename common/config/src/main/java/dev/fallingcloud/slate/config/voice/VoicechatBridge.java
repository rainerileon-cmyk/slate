package dev.fallingcloud.slate.config.voice;

import de.maxhenkel.voicechat.VoicechatClient;
import de.maxhenkel.voicechat.config.ClientConfig;
import de.maxhenkel.voicechat.configbuilder.entry.ConfigEntry;
import de.maxhenkel.voicechat.configbuilder.entry.RangedConfigEntry;
import dev.fallingcloud.slate.config.SlateConfig;
import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.Choice;
import dev.fallingcloud.slate.config.option.NumberRange;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.option.OptionValues;
import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Simple Voice Chat quick settings bound straight to its live {@code ClientConfig} entries (set + save),
 * plus a button to its own settings screen. Loaded only after {@code isModLoaded("voicechat")}.
 */
public final class VoicechatBridge {

    public static Section section() {
        final Section s = Section.of("voicechat", Component.translatable("slate_config.audio.voicechat"));
        try {
            final ClientConfig c = VoicechatClient.CLIENT_CONFIG;
            if (c == null) return s;
            s.add(ranged("voice_chat_volume", c.voiceChatVolume, 0, 2, 0.01, true));
            s.add(ranged("microphone_gain", c.microphoneGain, 0, 4, 0.01, false));
            s.add(enumEntry("microphone_activation_type", c.microphoneActivationType));
            s.add(ranged("voice_activation_threshold", c.voiceActivationThreshold, -127, 0, 1, false));
            s.add(bool("muted", c.muted));
            s.add(bool("disabled", c.disabled));
            s.add(bool("denoiser", c.denoiser));
            s.add(bool("automatic_gain_control", c.agc));
            s.add(enumEntry("audio_type", c.audioType));
            s.add(bool("show_hud_icons", c.showHudIcons));
            s.add(bool("show_nametag_icons", c.showNametagIcons));
            s.add(bool("show_group_hud", c.showGroupHud));
            s.add(bool("mute_on_join", c.muteOnJoin));
            s.add(Binding.of("voicechat:open", OptionType.ACTION, Component.translatable("slate_config.audio.voicechat_screen"))
                .action(Component.translatable("slate_config.row.open"), VoicechatBridge::openSettings)
                .searchWords("voice chat settings microphone"));
        } catch (final Throwable t) {
            SlateConfig.LOGGER.warn("[Slate Config] Simple Voice Chat config unavailable: {}", t.toString());
        }
        return s;
    }

    private static Component label(final String key) {
        return Component.translatable("slate_config.voice." + key);
    }

    private static Component tip(final ConfigEntry<?> e) {
        final String[] c = e.getComments();
        return c == null || c.length == 0 ? null : Component.literal(String.join("\n", c));
    }

    private static Binding bool(final String key, final ConfigEntry<Boolean> e) {
        return Binding.of("voicechat:" + key, OptionType.BOOLEAN, label(key)).tooltip(tip(e))
            .getter(e::get).setter(v -> e.set(OptionValues.asBoolean(v, false)).save()).def(e.getDefault()).searchWords("voice chat");
    }

    private static Binding ranged(final String key, final ConfigEntry<Double> e, final double min, final double max, final double step, final boolean percent) {
        double lo = min, hi = max;
        if (e instanceof RangedConfigEntry<?> r) {
            try { lo = ((Number) r.getMin()).doubleValue(); hi = ((Number) r.getMax()).doubleValue(); } catch (final Throwable ignored) {}
        }
        return Binding.of("voicechat:" + key, OptionType.DOUBLE, label(key)).tooltip(tip(e))
            .range(NumberRange.of(lo, hi, step))
            .format(v -> percent ? Component.literal(Math.round(OptionValues.asDouble(v, 0) * 100) + "%") : Component.literal(OptionValues.formatDouble(OptionValues.asDouble(v, 0))))
            .getter(e::get).setter(v -> e.set(OptionValues.asDouble(v, 1)).save()).def(e.getDefault()).searchWords("voice chat");
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Binding enumEntry(final String key, final ConfigEntry<? extends Enum> e) {
        final Enum cur = e.get();
        final List<Choice> choices = new ArrayList<>();
        for (final Object c : cur.getDeclaringClass().getEnumConstants()) choices.add(new Choice(((Enum) c).name(), Component.literal(dev.fallingcloud.slate.config.option.Humanize.enumName(((Enum) c).name()))));
        final ConfigEntry raw = e;
        return Binding.of("voicechat:" + key, OptionType.CHOICE, label(key)).tooltip(tip(e)).choices(choices)
            .getter(() -> ((Enum) raw.get()).name())
            .setter(v -> {
                final String name = OptionValues.asString(v);
                for (final Object c : cur.getDeclaringClass().getEnumConstants()) if (((Enum) c).name().equals(name)) { raw.set(c).save(); return; }
            })
            .def(((Enum) raw.getDefault()).name()).searchWords("voice chat");
    }

    private static void openSettings() {
        final Screen parent = Minecraft.getInstance().screen;
        final var own = SlatePlatform.get().otherModConfigScreen("voicechat");
        if (own.isPresent()) { Minecraft.getInstance().setScreen(own.get().apply(parent)); return; }
        try {
            final Class<?> cls = Class.forName("de.maxhenkel.voicechat.gui.VoiceChatSettingsScreen");
            Minecraft.getInstance().setScreen((Screen) cls.getConstructor(Screen.class).newInstance(parent));
        } catch (final Throwable t) {
            SlateConfig.LOGGER.warn("[Slate Config] cannot open the voice chat screen: {}", t.toString());
        }
    }

    private VoicechatBridge() {}
}
