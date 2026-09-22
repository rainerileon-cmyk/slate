package dev.fallingcloud.slate.config.neoforge;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import dev.fallingcloud.slate.config.SlateConfig;
import dev.fallingcloud.slate.config.doc.DocSection;
import dev.fallingcloud.slate.config.doc.FileDocument;
import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.Choice;
import dev.fallingcloud.slate.config.option.Humanize;
import dev.fallingcloud.slate.config.option.NumberRange;
import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.option.OptionValues;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import dev.fallingcloud.slate.core.theme.Colors;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

/**
 * A NeoForge {@link ModConfigSpec} as a document: the spec tree gives sections, comments, ranges,
 * defaults, enum classes and restart requirements; the value tree gives live {@code ConfigValue}s.
 * Edits go through {@code ConfigValue.set} + {@code spec.save()}, exactly what NeoForge's own
 * ConfigurationScreen does, so the mod sees the change immediately and its file is rewritten by FML.
 */
public final class NeoForgeSpecDocument implements FileDocument {

    private final ModConfig config;
    private final ModConfigSpec spec;
    private final String idPrefix;

    NeoForgeSpecDocument(final ModConfig config, final ModConfigSpec spec) {
        this.config = config;
        this.spec = spec;
        this.idPrefix = "toml:" + config.getModId() + ":" + config.getFileName() + ":";
    }

    @Override
    public Path path() {
        try {
            final Path p = config.getFullPath();
            if (p != null) return p;
        } catch (final Throwable ignored) {}
        return SlatePlatform.get().configDir().resolve(config.getFileName());
    }

    @Override public Component title() { return Component.literal(config.getFileName()); }
    @Override public String kind() { return "spec"; }
    @Override public String modId() { return config.getModId(); }
    @Override public void reload() {}
    @Override public boolean editable() { return spec.isLoaded(); }

    @Override
    public @Nullable Component notice() {
        return spec.isLoaded() ? null : Component.translatable("slate_config.editor.not_loaded", config.getType().name().toLowerCase(Locale.ROOT));
    }

    @Override
    public List<DocSection> sections() {
        final List<DocSection> out = new ArrayList<>();
        if (!spec.isLoaded()) return out;
        try {
            walk(spec.getSpec(), spec.getValues(), new ArrayList<>(), 0, out);
        } catch (final Throwable t) {
            SlateConfig.LOGGER.warn("[Slate Config] cannot walk {}: {}", config.getFileName(), t.toString());
        }
        return out;
    }

    private void walk(final UnmodifiableConfig specNode, final UnmodifiableConfig valuesNode, final List<String> path, final int depth, final List<DocSection> out) {
        final List<OptionBinding> leaves = new ArrayList<>();
        final List<Runnable> nested = new ArrayList<>();
        for (final UnmodifiableConfig.Entry e : specNode.entrySet()) {
            final String key = e.getKey();
            final Object raw = e.getRawValue();
            final List<String> childPath = new ArrayList<>(path);
            childPath.add(key);
            if (raw instanceof UnmodifiableConfig sub) {
                final Object vals = valuesNode.get(List.of(key));
                if (vals instanceof UnmodifiableConfig subVals) nested.add(() -> walk(sub, subVals, childPath, depth + 1, out));
            } else if (raw instanceof ModConfigSpec.ValueSpec vs) {
                final Object cvRaw = valuesNode.get(List.of(key));
                if (cvRaw instanceof ModConfigSpec.ConfigValue<?> cv) {
                    final OptionBinding b = binding(childPath, key, vs, cv);
                    if (b != null) leaves.add(b);
                }
            }
        }
        final Component title;
        Component comment = null;
        if (path.isEmpty()) title = Component.translatable("slate_config.editor.root");
        else {
            final String tk = spec.getLevelTranslationKey(path);
            title = tk != null && I18n.exists(tk) ? Component.translatable(tk) : Component.literal(Humanize.key(path.get(path.size() - 1)));
            final String c = spec.getLevelComment(path);
            if (c != null && !c.isBlank()) comment = Component.literal(c);
        }
        if (!leaves.isEmpty() || depth == 0) out.add(new DocSection(String.join(".", path), title, comment, depth, leaves));
        for (final Runnable r : nested) r.run();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Nullable
    private OptionBinding binding(final List<String> path, final String key, final ModConfigSpec.ValueSpec vs, final ModConfigSpec.ConfigValue<?> cv) {
        final String id = idPrefix + String.join(".", path);
        final String tk = vs.getTranslationKey();
        final Component label = tk != null && I18n.exists(tk) ? Component.translatable(tk) : Component.literal(Humanize.key(key));
        Component tip = null;
        if (tk != null && I18n.exists(tk + ".tooltip")) tip = Component.translatable(tk + ".tooltip");
        else if (vs.getComment() != null && !vs.getComment().isBlank()) tip = Component.literal(vs.getComment());
        final boolean restart = vs.restartType() != ModConfigSpec.RestartType.NONE;
        final ModConfigSpec.Range<?> range = safeRange(vs);
        final ModConfigSpec.ConfigValue raw = cv;
        final Binding b;
        if (cv instanceof ModConfigSpec.BooleanValue bv) {
            b = Binding.of(id, OptionType.BOOLEAN, label).getter(bv::get).setter(v -> write(raw, OptionValues.asBoolean(v, false)));
        } else if (cv instanceof ModConfigSpec.IntValue iv) {
            b = Binding.of(id, OptionType.INT, label).getter(iv::get).setter(v -> write(raw, OptionValues.asInt(v, 0)));
            if (range != null && range.getMin() instanceof Number lo && range.getMax() instanceof Number hi) b.range(NumberRange.of(lo.doubleValue(), hi.doubleValue(), 1));
        } else if (cv instanceof ModConfigSpec.LongValue lv) {
            b = Binding.of(id, OptionType.INT, label).getter(lv::get).setter(v -> write(raw, OptionValues.asLong(v, 0)));
            if (range != null && range.getMin() instanceof Number lo && range.getMax() instanceof Number hi) b.range(NumberRange.of(lo.doubleValue(), hi.doubleValue(), 1));
        } else if (cv instanceof ModConfigSpec.DoubleValue dv) {
            b = Binding.of(id, OptionType.DOUBLE, label).getter(dv::get).setter(v -> write(raw, OptionValues.asDouble(v, 0)));
            if (range != null && range.getMin() instanceof Number lo && range.getMax() instanceof Number hi) b.range(NumberRange.of(lo.doubleValue(), hi.doubleValue(), 0));
        } else if (cv instanceof ModConfigSpec.EnumValue<?> ev) {
            final Class<?> clazz = vs.getClazz() != null && vs.getClazz().isEnum() ? vs.getClazz() : ev.get() instanceof Enum<?> en ? en.getDeclaringClass() : null;
            if (clazz == null) return null;
            b = Binding.of(id, OptionType.CHOICE, label).choices(Choice.ofEnum(clazz))
                .getter(() -> { final Object v = raw.get(); return v instanceof Enum<?> en ? en.name() : String.valueOf(v); })
                .setter(v -> {
                    final String name = OptionValues.asString(v);
                    for (final Object c : clazz.getEnumConstants()) if (((Enum<?>) c).name().equals(name)) { write(raw, c); return; }
                });
        } else {
            final Object cur = cv.get();
            if (cur instanceof List<?>) {
                b = Binding.of(id, OptionType.LIST, label)
                    .getter(() -> OptionValues.asList(raw.get()))
                    .setter(v -> {
                        final Object sample = sampleElement(vs, raw);
                        final List<Object> out = new ArrayList<>();
                        for (final String s : OptionValues.asList(v)) out.add(coerce(s, sample));
                        write(raw, out);
                    });
            } else if (cur instanceof Boolean) {
                b = Binding.of(id, OptionType.BOOLEAN, label).getter(raw::get).setter(v -> write(raw, OptionValues.asBoolean(v, false)));
            } else if (cur instanceof Integer || cur instanceof Long) {
                b = Binding.of(id, OptionType.INT, label).getter(raw::get).setter(v -> write(raw, cur instanceof Integer ? (Object) OptionValues.asInt(v, 0) : (Object) OptionValues.asLong(v, 0)));
            } else if (cur instanceof Number) {
                b = Binding.of(id, OptionType.DOUBLE, label).getter(raw::get).setter(v -> write(raw, OptionValues.asDouble(v, 0)));
            } else if (cur instanceof String s && (s.matches("#[0-9A-Fa-f]{6}") || s.matches("#[0-9A-Fa-f]{8}"))) {
                b = Binding.of(id, OptionType.COLOR, label).getter(() -> OptionValues.asColor(raw.get(), 0xFFFFFFFF)).setter(v -> write(raw, Colors.toHex(OptionValues.asColor(v, 0xFFFFFFFF))));
            } else if (cur instanceof String) {
                b = Binding.of(id, OptionType.STRING, label).getter(() -> OptionValues.asString(raw.get())).setter(v -> write(raw, OptionValues.asString(v)));
            } else {
                b = Binding.of(id, OptionType.INFO, label).getter(() -> String.valueOf(raw.get()));
            }
        }
        Object def = null;
        try { def = cv.getDefault(); } catch (final Throwable ignored) {}
        if (def instanceof Enum<?> en) def = en.name();
        return b.tooltip(tip).def(def).restart(restart).searchWords(config.getFileName() + " " + config.getModId() + " " + String.join(" ", path));
    }

    @Nullable
    private static ModConfigSpec.Range<?> safeRange(final ModConfigSpec.ValueSpec vs) {
        try { return vs.getRange(); } catch (final Throwable t) { return null; }
    }

    @Nullable
    private static Object sampleElement(final ModConfigSpec.ValueSpec vs, final ModConfigSpec.ConfigValue<?> cv) {
        try {
            final Object cur = cv.get();
            if (cur instanceof List<?> l && !l.isEmpty()) return l.get(0);
            final Object def = cv.getDefault();
            if (def instanceof List<?> l && !l.isEmpty()) return l.get(0);
            if (vs instanceof ModConfigSpec.ListValueSpec lvs && lvs.getNewElementSupplier() != null) return lvs.getNewElementSupplier().get();
        } catch (final Throwable ignored) {}
        return null;
    }

    private static Object coerce(final String s, @Nullable final Object sample) {
        try {
            if (sample instanceof Boolean) return Boolean.parseBoolean(s.trim());
            if (sample instanceof Integer) return Integer.parseInt(s.trim());
            if (sample instanceof Long) return Long.parseLong(s.trim());
            if (sample instanceof Double || sample instanceof Float) return Double.parseDouble(s.trim());
            if (sample instanceof Enum<?> en) {
                for (final Object c : en.getDeclaringClass().getEnumConstants()) if (((Enum<?>) c).name().equalsIgnoreCase(s.trim())) return c;
            }
        } catch (final NumberFormatException ignored) {}
        return s;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void write(final ModConfigSpec.ConfigValue raw, final Object value) {
        raw.set(value);
        try {
            spec.save();
        } catch (final Throwable t) {
            SlateConfig.LOGGER.warn("[Slate Config] {} could not be saved: {}", config.getFileName(), t.toString());
        }
    }
}
