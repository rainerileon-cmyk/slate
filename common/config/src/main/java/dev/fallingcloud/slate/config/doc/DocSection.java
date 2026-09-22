package dev.fallingcloud.slate.config.doc;

import dev.fallingcloud.slate.config.option.OptionBinding;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * One group of options in a {@link FileDocument}: a TOML table, a JSON object, or the root.
 *
 * @param path    dotted path ("" for the root)
 * @param depth   nesting level, for indentation
 * @param comment the section's own comment/description, if any
 */
public record DocSection(String path, Component title, @Nullable Component comment, int depth, List<OptionBinding> options) {}
