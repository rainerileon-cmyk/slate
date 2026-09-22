package dev.fallingcloud.slate.config.doc;

import java.nio.file.Path;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * An editable config file presented as sections of {@link dev.fallingcloud.slate.config.option.OptionBinding}s.
 * Implementations: JSON, TOML and .properties line editors in common, and NeoForge's ModConfigSpec through
 * the {@link dev.fallingcloud.slate.config.ConfigPlatform} SPI. Every binding writes through immediately.
 */
public interface FileDocument {

    Path path();

    /** Short name shown in lists (the file name, or "Client config"). */
    Component title();

    /** {@code json}, {@code toml}, {@code properties} or {@code spec}. */
    String kind();

    /** Owning mod id when known, else "". */
    default String modId() { return ""; }

    /** Sections in display order; the root section first (may be empty). */
    List<DocSection> sections();

    /** Re-read from disk (external edits). */
    void reload();

    /** A message instead of/above the sections (parse error, server config not loaded, comments lost on save). */
    @Nullable
    default Component notice() { return null; }

    /** False when the notice means "cannot edit". */
    default boolean editable() { return true; }
}
