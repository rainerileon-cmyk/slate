package dev.fallingcloud.slate.config.option;

import java.util.Optional;

/**
 * Turns a path such as {@code json:config/betterclouds-v1.json:/distance} into a live binding. One
 * resolver per prefix ({@code optionsTxt}, {@code sodium}, {@code toml}, {@code json}, {@code props},
 * {@code slate}, {@code key}); registered in {@link OptionResolvers}.
 */
public interface OptionResolver {

    /** The prefix before the first colon, e.g. {@code toml}. */
    String prefix();

    /** Resolve the remainder after {@code prefix:}; empty when the target does not exist. Never throws. */
    Optional<OptionBinding> resolve(String rest);
}
