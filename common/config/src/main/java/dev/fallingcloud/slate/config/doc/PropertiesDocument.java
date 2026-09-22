package dev.fallingcloud.slate.config.doc;

import dev.fallingcloud.slate.config.SlateConfig;
import dev.fallingcloud.slate.config.file.PropertiesLines;
import dev.fallingcloud.slate.config.file.TextFile;
import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.Choice;
import dev.fallingcloud.slate.config.option.Humanize;
import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.option.OptionValues;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** A {@code .properties} / {@code .cfg} file; types are inferred from the current value text. */
public final class PropertiesDocument implements FileDocument {

    private final Path path;
    private final String modId;
    private final String idPrefix;
    private PropertiesLines props = PropertiesLines.parse("");
    private long loadedMtime;
    @Nullable private Component notice;
    private final Map<String, OptionBinding> bindings = new LinkedHashMap<>();

    public PropertiesDocument(final Path path, final String modId, final String fileRelativeToGame) {
        this.path = path;
        this.modId = modId == null ? "" : modId;
        this.idPrefix = "props:" + fileRelativeToGame.replace('\\', '/') + ":";
        reload();
    }

    @Override public Path path() { return path; }
    @Override public Component title() { return Component.literal(path.getFileName().toString()); }
    @Override public String kind() { return "properties"; }
    @Override public String modId() { return modId; }
    @Override public @Nullable Component notice() { return notice; }
    @Override public boolean editable() { return notice == null; }

    public void ensureFresh() {
        if (TextFile.mtime(path) != loadedMtime) reload();
    }

    @Override
    public void reload() {
        bindings.clear();
        try {
            props = Files.isRegularFile(path) ? PropertiesLines.parse(TextFile.read(path)) : PropertiesLines.parse("");
            loadedMtime = TextFile.mtime(path);
            notice = Files.isRegularFile(path) ? null : Component.translatable("slate_config.editor.missing_file");
        } catch (final Exception e) {
            SlateConfig.LOGGER.warn("[Slate Config] cannot read {}: {}", path, e.toString());
            notice = Component.translatable("slate_config.editor.parse_error", String.valueOf(e.getMessage()));
        }
    }

    private void save() {
        try {
            TextFile.writeAtomic(path, props.render());
            loadedMtime = TextFile.mtime(path);
        } catch (final IOException e) {
            SlateConfig.LOGGER.error("[Slate Config] cannot write {}", path, e);
        }
    }

    public Optional<OptionBinding> binding(final String key) {
        ensureFresh();
        final OptionBinding cached = bindings.get(key);
        if (cached != null) return Optional.of(cached);
        return props.find(key).map(e -> {
            final OptionBinding b = build(e);
            bindings.put(key, b);
            return b;
        });
    }

    @Override
    public List<DocSection> sections() {
        ensureFresh();
        final List<OptionBinding> all = new ArrayList<>();
        for (final PropertiesLines.Entry e : props.entries()) all.add(bindings.computeIfAbsent(e.key, k -> build(e)));
        return List.of(new DocSection("", Component.translatable("slate_config.editor.root"), null, 0, all));
    }

    private OptionBinding build(final PropertiesLines.Entry e) {
        final String id = idPrefix + e.key;
        final Component label = Component.literal(Humanize.key(e.key));
        final List<String> desc = e.description();
        final Component tip = desc.isEmpty() ? null : Component.literal(String.join("\n", desc));
        final String v = e.value.trim();
        final Binding b;
        if (e.allowed != null) {
            b = Binding.of(id, OptionType.CHOICE, label).choices(Choice.ofStrings(e.allowed))
                .getter(() -> current(e)).setter(nv -> write(e, OptionValues.asString(nv)));
        } else if (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("false")) {
            b = Binding.of(id, OptionType.BOOLEAN, label)
                .getter(() -> OptionValues.asBoolean(current(e), false))
                .setter(nv -> write(e, Boolean.toString(OptionValues.asBoolean(nv, false))));
        } else if (v.matches("[+-]?\\d+")) {
            b = Binding.of(id, OptionType.INT, label)
                .getter(() -> OptionValues.asLong(current(e), 0))
                .setter(nv -> write(e, Long.toString(OptionValues.asLong(nv, 0))));
        } else if (v.matches("[+-]?(\\d+\\.\\d*|\\.\\d+|\\d+)([eE][+-]?\\d+)?")) {
            b = Binding.of(id, OptionType.DOUBLE, label)
                .getter(() -> OptionValues.asDouble(current(e), 0))
                .setter(nv -> write(e, OptionValues.formatDouble(OptionValues.asDouble(nv, 0))));
        } else {
            b = Binding.of(id, OptionType.STRING, label)
                .getter(() -> current(e)).setter(nv -> write(e, OptionValues.asString(nv)));
        }
        return b.tooltip(tip).searchWords(path.getFileName() + " " + modId);
    }

    private String current(final PropertiesLines.Entry e) {
        ensureFresh();
        return props.find(e.key).map(x -> x.value).orElse(e.value);
    }

    private void write(final PropertiesLines.Entry e, final String value) {
        ensureFresh();
        if (props.set(e.key, value)) save();
    }
}
