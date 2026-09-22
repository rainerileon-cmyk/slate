package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.ConfigSettings;
import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.resolver.VanillaOptions;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.config.voice.VoicechatBridge;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;

/** Audio: every sound category, output device and audio flags, Simple Voice Chat quick settings when present. */
public final class AudioPage extends OptionPageBase {

    public AudioPage() {
        super("audio", Component.translatable("slate_config.page.audio"), Icon.VOLUME);
    }

    @Override
    protected List<Section> sections() {
        final List<Section> out = new ArrayList<>();
        final Section vol = Section.of("volume", Component.translatable("slate_config.audio.volume"));
        vol.add(Binding.of("audio:music_enabled", OptionType.BOOLEAN, Component.translatable("slate_config.audio.music_enabled"))
            .tooltip(Component.translatable("slate_config.audio.music_enabled.tip"))
            .getter(() -> music().get() > 0.0)
            .setter(v -> {
                final OptionInstance<Double> m = music();
                if (Boolean.TRUE.equals(v)) {
                    final double restore = ConfigSettings.get().musicVolumeBeforeMute;
                    m.set(restore > 0 ? restore : 1.0);
                } else {
                    final double cur = m.get();
                    if (cur > 0) ConfigSettings.file().update(c -> c.musicVolumeBeforeMute = cur);
                    m.set(0.0);
                }
                Minecraft.getInstance().options.save();
            })
            .def(Boolean.TRUE)
            .searchWords("music mute"));
        for (final SoundSource src : SoundSource.values()) VanillaOptions.get("soundCategory_" + src.getName()).ifPresent(vol::add);
        out.add(vol);
        out.add(Section.of("output", Component.translatable("slate_config.audio.output"), VanillaOptions.all("soundDevice", "directionalAudio", "showSubtitles")));
        if (SlatePlatform.get().isModLoaded("voicechat")) out.add(VoicechatBridge.section());
        return out;
    }

    private static OptionInstance<Double> music() {
        return Minecraft.getInstance().options.getSoundSourceOptionInstance(SoundSource.MUSIC);
    }
}
