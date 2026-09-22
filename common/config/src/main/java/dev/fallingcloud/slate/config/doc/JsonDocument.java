package dev.fallingcloud.slate.config.doc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.fallingcloud.slate.config.SlateConfig;
import dev.fallingcloud.slate.config.file.JsonDoc;
import dev.fallingcloud.slate.config.file.TextFile;
import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.Humanize;
import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.option.OptionValues;
import dev.fallingcloud.slate.core.theme.Colors;
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

/**
 * A JSON config file as a tree: objects become sections, primitives become controls (type inferred),
 * arrays of primitives become list editors, arrays of objects become indexed sub-sections.
 */
public final class JsonDocument implements FileDocument {

    private final Path path;
    private final String modId;
    private final String idPrefix;
    private JsonDoc doc = JsonDoc.empty();
    private long loadedMtime;
    @Nullable private Component notice;
    private boolean editable = true;
    private final Map<String, OptionBinding> bindings = new LinkedHashMap<>();

    public JsonDocument(final Path path, final String modId, final String fileRelativeToGame) {
        this.path = path;
        this.modId = modId == null ? "" : modId;
        this.idPrefix = "json:" + fileRelativeToGame.replace('\\', '/') + ":";
        reload();
    }

    @Override public Path path() { return path; }
    @Override public Component title() { return Component.literal(path.getFileName().toString()); }
    @Override public String kind() { return "json"; }
    @Override public String modId() { return modId; }
    @Override public @Nullable Component notice() { return notice; }
    @Override public boolean editable() { return editable; }

    public JsonDoc doc() { return doc; }

    public void ensureFresh() {
        if (TextFile.mtime(path) != loadedMtime) reload();
    }

    @Override
    public void reload() {
        bindings.clear();
        editable = true;
        try {
            if (!Files.isRegularFile(path)) {
                doc = JsonDoc.empty();
                notice = Component.translatable("slate_config.editor.missing_file");
                editable = false;
            } else {
                doc = JsonDoc.parse(TextFile.read(path));
                notice = doc.hadComments() ? Component.translatable("slate_config.editor.comments_lost") : null;
            }
            loadedMtime = TextFile.mtime(path);
        } catch (final Exception e) {
            SlateConfig.LOGGER.warn("[Slate Config] cannot parse {}: {}", path, e.toString());
            doc = JsonDoc.empty();
            notice = Component.translatable("slate_config.editor.parse_error", String.valueOf(e.getMessage()));
            editable = false;
        }
    }

    private void save() {
        try {
            TextFile.writeAtomic(path, doc.render());
            loadedMtime = TextFile.mtime(path);
        } catch (final IOException e) {
            SlateConfig.LOGGER.error("[Slate Config] cannot write {}", path, e);
        }
    }

    /** Binding for the element at an RFC 6901 pointer (primitives and primitive arrays only). */
    public Optional<OptionBinding> binding(final String pointer) {
        ensureFresh();
        final OptionBinding cached = bindings.get(pointer);
        if (cached != null) return Optional.of(cached);
        final JsonElement el = doc.get(pointer);
        if (el == null || el.isJsonObject() || (el.isJsonArray() && !isPrimitiveArray(el.getAsJsonArray()))) return Optional.empty();
        final List<String> toks = JsonDoc.tokens(pointer);
        final String key = toks.isEmpty() ? "" : toks.get(toks.size() - 1);
        final OptionBinding b = build(pointer, key, el);
        bindings.put(pointer, b);
        return Optional.of(b);
    }

    @Override
    public List<DocSection> sections() {
        ensureFresh();
        final List<DocSection> out = new ArrayList<>();
        if (doc.root().isJsonObject()) walk("", Component.translatable("slate_config.editor.root"), doc.root().getAsJsonObject(), 0, out);
        else if (doc.root().isJsonArray()) walkArray("", Component.translatable("slate_config.editor.root"), doc.root().getAsJsonArray(), 0, out);
        return out;
    }

    private void walk(final String pointer, final Component title, final JsonObject obj, final int depth, final List<DocSection> out) {
        final List<OptionBinding> leaves = new ArrayList<>();
        final List<Runnable> nested = new ArrayList<>();
        for (final Map.Entry<String, JsonElement> e : obj.entrySet()) {
            final String p = pointer + "/" + e.getKey().replace("~", "~0").replace("/", "~1");
            final JsonElement v = e.getValue();
            if (v.isJsonObject()) nested.add(() -> walk(p, Component.literal(Humanize.key(e.getKey())), v.getAsJsonObject(), depth + 1, out));
            else if (v.isJsonArray() && !isPrimitiveArray(v.getAsJsonArray())) nested.add(() -> walkArray(p, Component.literal(Humanize.key(e.getKey())), v.getAsJsonArray(), depth + 1, out));
            else leaves.add(bindings.computeIfAbsent(p, k -> build(p, e.getKey(), v)));
        }
        if (!leaves.isEmpty() || depth == 0) out.add(new DocSection(pointer, title, null, depth, leaves));
        for (final Runnable r : nested) r.run();
    }

    private void walkArray(final String pointer, final Component title, final JsonArray arr, final int depth, final List<DocSection> out) {
        for (int i = 0; i < arr.size(); i++) {
            final int idx = i;
            final JsonElement v = arr.get(i);
            final String p = pointer + "/" + i;
            final Component t = Component.literal(title.getString() + " [" + i + "]");
            if (v.isJsonObject()) walk(p, t, v.getAsJsonObject(), depth + 1, out);
            else if (v.isJsonArray()) walkArray(p, t, v.getAsJsonArray(), depth + 1, out);
            else out.add(new DocSection(p, t, null, depth + 1, List.of(bindings.computeIfAbsent(p, k -> build(p, "[" + idx + "]", v)))));
        }
    }

    private static boolean isPrimitiveArray(final JsonArray arr) {
        for (final JsonElement e : arr) if (!e.isJsonPrimitive() && !e.isJsonNull()) return false;
        return true;
    }

    private OptionBinding build(final String pointer, final String key, final JsonElement el) {
        final String id = idPrefix + pointer;
        final Component label = Component.literal(Humanize.key(key));
        final Binding b;
        if (el.isJsonArray()) {
            b = Binding.of(id, OptionType.LIST, label)
                .getter(() -> OptionValues.asList(listOf(current(pointer))))
                .setter(nv -> {
                    final JsonArray a = new JsonArray();
                    final JsonElement cur = current(pointer);
                    final JsonElement sample = cur != null && cur.isJsonArray() && !cur.getAsJsonArray().isEmpty() ? cur.getAsJsonArray().get(0) : null;
                    for (final String s : OptionValues.asList(nv)) a.add(coerceLike(s, sample));
                    write(pointer, a);
                });
        } else if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) {
            b = Binding.of(id, OptionType.BOOLEAN, label)
                .getter(() -> { final JsonElement c = current(pointer); return c != null && c.isJsonPrimitive() && c.getAsJsonPrimitive().isBoolean() ? c.getAsBoolean() : OptionValues.asBoolean(str(c), false); })
                .setter(nv -> write(pointer, new JsonPrimitive(OptionValues.asBoolean(nv, false))));
        } else if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
            final boolean integer = isIntegerText(el.getAsJsonPrimitive().getAsString());
            if (integer) {
                b = Binding.of(id, OptionType.INT, label)
                    .getter(() -> OptionValues.asLong(str(current(pointer)), 0))
                    .setter(nv -> write(pointer, new JsonPrimitive(OptionValues.asLong(nv, 0))));
            } else {
                b = Binding.of(id, OptionType.DOUBLE, label)
                    .getter(() -> OptionValues.asDouble(str(current(pointer)), 0))
                    .setter(nv -> write(pointer, new JsonPrimitive(OptionValues.asDouble(nv, 0))));
            }
        } else if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString() && el.getAsString().matches("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")) {
            b = Binding.of(id, OptionType.COLOR, label)
                .getter(() -> OptionValues.asColor(str(current(pointer)), 0xFFFFFFFF))
                .setter(nv -> write(pointer, new JsonPrimitive(Colors.toHex(OptionValues.asColor(nv, 0xFFFFFFFF)))));
        } else {
            b = Binding.of(id, OptionType.STRING, label)
                .getter(() -> str(current(pointer)))
                .setter(nv -> write(pointer, new JsonPrimitive(OptionValues.asString(nv))));
        }
        return b.searchWords(path.getFileName() + " " + modId + " " + pointer.replace('/', ' '));
    }

    private static boolean isIntegerText(final String s) {
        return s != null && s.matches("[+-]?\\d+");
    }

    private static JsonElement coerceLike(final String s, @Nullable final JsonElement sample) {
        if (sample != null && sample.isJsonPrimitive()) {
            final JsonPrimitive p = sample.getAsJsonPrimitive();
            if (p.isBoolean()) return new JsonPrimitive(OptionValues.asBoolean(s, false));
            if (p.isNumber()) {
                try { return isIntegerText(s.trim()) ? new JsonPrimitive(Long.parseLong(s.trim())) : new JsonPrimitive(Double.parseDouble(s.trim())); }
                catch (final NumberFormatException ignored) {}
            }
        }
        return new JsonPrimitive(s);
    }

    private static List<String> listOf(@Nullable final JsonElement e) {
        final List<String> out = new ArrayList<>();
        if (e != null && e.isJsonArray()) for (final JsonElement x : e.getAsJsonArray()) out.add(x.isJsonPrimitive() ? x.getAsString() : x.toString());
        return out;
    }

    @Nullable
    private static String str(@Nullable final JsonElement e) {
        if (e == null || e.isJsonNull()) return null;
        return e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    @Nullable
    private JsonElement current(final String pointer) {
        ensureFresh();
        return doc.get(pointer);
    }

    private void write(final String pointer, final JsonElement value) {
        ensureFresh();
        if (doc.set(pointer, value)) save();
    }
}
