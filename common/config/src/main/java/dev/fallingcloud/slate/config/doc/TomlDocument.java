package dev.fallingcloud.slate.config.doc;

import dev.fallingcloud.slate.config.SlateConfig;
import dev.fallingcloud.slate.config.file.TextFile;
import dev.fallingcloud.slate.config.file.TomlLines;
import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.Choice;
import dev.fallingcloud.slate.config.option.Humanize;
import dev.fallingcloud.slate.config.option.NumberRange;
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

/** A TOML file edited through {@link TomlLines}; every binding writes the file back atomically on set. */
public final class TomlDocument implements FileDocument {

    private final Path path;
    private final String modId;
    private final String idPrefix;
    private TomlLines toml = TomlLines.parse("");
    private long loadedMtime;
    @Nullable private Component notice;
    private final Map<String, OptionBinding> bindings = new LinkedHashMap<>();

    public TomlDocument(final Path path, final String modId, final String fileRelativeToConfig) {
        this.path = path;
        this.modId = modId == null ? "" : modId;
        this.idPrefix = "toml:" + this.modId + ":" + fileRelativeToConfig.replace('\\', '/') + ":";
        reload();
    }

    @Override public Path path() { return path; }
    @Override public Component title() { return Component.literal(path.getFileName().toString()); }
    @Override public String kind() { return "toml"; }
    @Override public String modId() { return modId; }
    @Override public @Nullable Component notice() { return notice; }
    @Override public boolean editable() { return notice == null; }

    public TomlLines lines() { return toml; }

    /** Reload if the file changed on disk since we read it. */
    public void ensureFresh() {
        if (TextFile.mtime(path) != loadedMtime) reload();
    }

    @Override
    public void reload() {
        bindings.clear();
        try {
            toml = Files.isRegularFile(path) ? TomlLines.parse(TextFile.read(path)) : TomlLines.parse("");
            loadedMtime = TextFile.mtime(path);
            notice = Files.isRegularFile(path) ? null : Component.translatable("slate_config.editor.missing_file");
        } catch (final Exception e) {
            SlateConfig.LOGGER.warn("[Slate Config] cannot read {}: {}", path, e.toString());
            notice = Component.translatable("slate_config.editor.parse_error", e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    private void save() {
        try {
            TextFile.writeAtomic(path, toml.render());
            loadedMtime = TextFile.mtime(path);
        } catch (final IOException e) {
            SlateConfig.LOGGER.error("[Slate Config] cannot write {}", path, e);
        }
    }

    public Optional<OptionBinding> binding(final String dottedPath) {
        ensureFresh();
        final OptionBinding cached = bindings.get(dottedPath);
        if (cached != null) return Optional.of(cached);
        return toml.find(dottedPath).map(e -> {
            final OptionBinding b = build(e);
            bindings.put(dottedPath, b);
            return b;
        });
    }

    @Override
    public List<DocSection> sections() {
        ensureFresh();
        final Map<String, List<OptionBinding>> bySection = new LinkedHashMap<>();
        final Map<String, TomlLines.Section> headers = new LinkedHashMap<>();
        bySection.put("", new ArrayList<>());
        for (final TomlLines.Section s : toml.sections()) { headers.put(s.path(), s); bySection.computeIfAbsent(s.path(), k -> new ArrayList<>()); }
        for (final TomlLines.Entry e : toml.entries()) {
            bySection.computeIfAbsent(e.section, k -> new ArrayList<>()).add(bindings.computeIfAbsent(e.path(), k -> build(e)));
        }
        final List<DocSection> out = new ArrayList<>();
        for (final Map.Entry<String, List<OptionBinding>> en : bySection.entrySet()) {
            final String p = en.getKey();
            if (p.isEmpty() && en.getValue().isEmpty()) continue;
            final TomlLines.Section h = headers.get(p);
            final String last = p.contains(".") ? p.substring(p.lastIndexOf('.') + 1) : p;
            final Component title = p.isEmpty() ? Component.translatable("slate_config.editor.root") : Component.literal(Humanize.key(last));
            final Component comment = h == null || h.comments().isEmpty() ? null : Component.literal(String.join("\n", h.comments()));
            out.add(new DocSection(p, title, comment, p.isEmpty() ? 0 : p.split("\\.").length, en.getValue()));
        }
        return out;
    }

    private OptionBinding build(final TomlLines.Entry e) {
        final String id = idPrefix + e.path();
        final Component label = Component.literal(Humanize.key(e.key));
        final List<String> desc = e.description();
        final Component tip = desc.isEmpty() ? null : Component.literal(String.join("\n", desc));
        final Object v = e.value;
        final Binding b;
        if (e.allowed != null && !e.allowed.isEmpty() && (v instanceof String || v instanceof TomlLines.Raw)) {
            b = Binding.of(id, OptionType.CHOICE, label).choices(Choice.ofStrings(e.allowed))
                .getter(() -> OptionValues.asString(current(e)))
                .setter(nv -> write(e, OptionValues.asString(nv)));
        } else if (v instanceof Boolean) {
            b = Binding.of(id, OptionType.BOOLEAN, label)
                .getter(() -> OptionValues.asBoolean(current(e), false))
                .setter(nv -> write(e, OptionValues.asBoolean(nv, false)));
        } else if (v instanceof Long) {
            b = Binding.of(id, OptionType.INT, label)
                .getter(() -> OptionValues.asLong(current(e), 0))
                .setter(nv -> write(e, OptionValues.asLong(nv, 0)));
            if (e.min != null && e.max != null) b.range(NumberRange.of(e.min, e.max, 1));
        } else if (v instanceof Double) {
            b = Binding.of(id, OptionType.DOUBLE, label)
                .getter(() -> OptionValues.asDouble(current(e), 0))
                .setter(nv -> write(e, OptionValues.asDouble(nv, 0)));
            if (e.min != null && e.max != null && e.max - e.min <= 100000) b.range(NumberRange.of(e.min, e.max, 0));
        } else if (v instanceof List<?>) {
            b = Binding.of(id, OptionType.LIST, label)
                .getter(() -> OptionValues.asList(current(e)))
                .setter(nv -> write(e, OptionValues.asList(nv)));
        } else if (v instanceof String s && looksLikeColor(s)) {
            b = Binding.of(id, OptionType.COLOR, label)
                .getter(() -> OptionValues.asColor(current(e), 0xFFFFFFFF))
                .setter(nv -> write(e, dev.fallingcloud.slate.core.theme.Colors.toHex(OptionValues.asColor(nv, 0xFFFFFFFF))));
        } else {
            b = Binding.of(id, OptionType.STRING, label)
                .getter(() -> OptionValues.asString(current(e)))
                .setter(nv -> write(e, v instanceof TomlLines.Raw ? new TomlLines.Raw(OptionValues.asString(nv)) : OptionValues.asString(nv)));
        }
        b.tooltip(tip).def(e.def).searchWords(path.getFileName() + " " + modId + " " + e.section);
        return b;
    }

    private static boolean looksLikeColor(final String s) {
        return s.matches("#[0-9A-Fa-f]{6}") || s.matches("#[0-9A-Fa-f]{8}");
    }

    private Object current(final TomlLines.Entry e) {
        ensureFresh();
        final TomlLines.Entry live = toml.find(e.path()).orElse(e);
        return live.value;
    }

    private void write(final TomlLines.Entry e, final Object value) {
        ensureFresh();
        if (toml.set(e.path(), value)) save();
    }
}
