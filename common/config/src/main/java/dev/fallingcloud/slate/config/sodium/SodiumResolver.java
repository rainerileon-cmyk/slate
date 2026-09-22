package dev.fallingcloud.slate.config.sodium;

import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.config.option.OptionResolver;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.util.Optional;

/**
 * {@code sodium:<id>} where the id is a Sodium option ResourceLocation: {@code quality.weather} means
 * {@code sodium:quality.weather}; {@code iris:shader_pack}-style ids from other mods are given in full
 * ({@code sodium:iris:shader_pack}). Only touches Sodium classes after the mod-loaded check.
 */
public final class SodiumResolver implements OptionResolver {

    @Override public String prefix() { return "sodium"; }

    @Override
    public Optional<OptionBinding> resolve(final String rest) {
        if (!SlatePlatform.get().isModLoaded("sodium") || !SodiumBridge.available()) return Optional.empty();
        return SodiumBridge.binding(rest.contains(":") ? rest : "sodium:" + rest);
    }
}
