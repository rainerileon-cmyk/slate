package dev.fallingcloud.slate.config.curated;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import dev.fallingcloud.slate.config.ConfigSettings;
import dev.fallingcloud.slate.config.SlateConfig;
import dev.fallingcloud.slate.config.file.TextFile;
import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.Choice;
import dev.fallingcloud.slate.config.option.NumberRange;
import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.core.config.JsonConfig;
import dev.fallingcloud.slate.core.gfx.Icon;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Curated pages from {@code config/slate/config/pages/*.json}, shown as extra sidebar entries after Presets
 * (a modpack author ships a file per page):
 * <pre>{ "id": "mypack", "title": "My pack", "icon": "SPARKLE", "sections": [ { "title": "Visuals", "description": "...",
 *   "options": [ { "path": "optionsTxt:renderDistance", "label": "...", "tooltip": "...", "min": 2, "max": 32, "step": 1,
 *                  "choices": ["A","B"], "restart": false } ] } ] }</pre>
 * Every {@code path} resolves through the option resolvers; label/tooltip/range/choices override what
 * the source provides. Each section is a top tab of the page. Nothing ships in the folder.
 */
public final class CuratedPages {

    public record OptionDef(String path, @Nullable String label, @Nullable String tooltip, @Nullable NumberRange range,
                            @Nullable List<String> choices, @Nullable Boolean restart) {
        public OptionBinding apply(final OptionBinding base) {
            if (label == null && tooltip == null && range == null && choices == null && restart == null) return base;
            return Binding.override(base, label == null ? null : Component.literal(label), tooltip == null ? null : Component.literal(tooltip),
                range, choices == null ? null : Choice.ofStrings(choices), restart);
        }
    }

    public record SectionDef(String title, @Nullable String description, List<OptionDef> options) {}

    public record PageDef(String id, String title, Icon icon, List<SectionDef> sections, Path file) {}

    public static Path dir() {
        return JsonConfig.dir().resolve("config").resolve("pages");
    }

    /**
     * Create the folder for modpack authors' pages. Once per install, move aside the "DF pack" page earlier
     * versions copied in ({@code df.json} with id {@code df}; renamed to {@code df.json.retired}, which the
     * loader ignores), so the retired tab disappears without touching pages a pack author wrote.
     */
    public static void bootstrap() {
        try {
            Files.createDirectories(dir());
        } catch (final IOException e) {
            SlateConfig.LOGGER.warn("[Slate Config] cannot prepare the curated pages folder: {}", e.toString());
        }
        if (ConfigSettings.get().dfPageRetired) return;
        final Path df = dir().resolve("df.json");
        try {
            if (Files.exists(df) && "df".equals(idOf(df))) {
                Files.move(df, dir().resolve("df.json.retired"), StandardCopyOption.REPLACE_EXISTING);
                SlateConfig.LOGGER.info("[Slate Config] retired the old DF pack settings page (pages/df.json -> df.json.retired)");
            }
        } catch (final IOException e) {
            SlateConfig.LOGGER.warn("[Slate Config] cannot retire pages/df.json: {}", e.toString());
        }
        ConfigSettings.file().update(c -> c.dfPageRetired = true);
    }

    @Nullable
    private static String idOf(final Path file) {
        try {
            final JsonReader r = new JsonReader(new StringReader(TextFile.read(file)));
            r.setLenient(true);
            final JsonElement root = JsonParser.parseReader(r);
            return root != null && root.isJsonObject() ? str(root.getAsJsonObject(), "id", null) : null;
        } catch (final Exception e) {
            return null;
        }
    }

    public static List<PageDef> load() {
        final List<PageDef> out = new ArrayList<>();
        if (!Files.isDirectory(dir())) return out;
        try (Stream<Path> s = Files.list(dir())) {
            for (final Path p : s.sorted().toList()) {
                if (!p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) continue;
                try {
                    final JsonReader r = new JsonReader(new StringReader(TextFile.read(p)));
                    r.setLenient(true);
                    final JsonElement root = JsonParser.parseReader(r);
                    if (root != null && root.isJsonObject()) {
                        final PageDef def = parse(root.getAsJsonObject(), p);
                        if (def != null) out.add(def);
                    }
                } catch (final Exception e) {
                    SlateConfig.LOGGER.warn("[Slate Config] bad curated page {}: {}", p.getFileName(), e.toString());
                }
            }
        } catch (final IOException ignored) {}
        return out;
    }

    @Nullable
    private static PageDef parse(final JsonObject o, final Path file) {
        final String fallbackId = file.getFileName().toString().replaceAll("\\.json$", "");
        final String id = str(o, "id", fallbackId);
        final String title = str(o, "title", id);
        Icon icon = Icon.SPARKLE;
        try { icon = Icon.valueOf(str(o, "icon", "SPARKLE").toUpperCase(Locale.ROOT)); } catch (final IllegalArgumentException ignored) {}
        final List<SectionDef> sections = new ArrayList<>();
        final JsonElement secs = o.get("sections");
        if (secs != null && secs.isJsonArray()) {
            for (final JsonElement se : secs.getAsJsonArray()) {
                if (!se.isJsonObject()) continue;
                final JsonObject so = se.getAsJsonObject();
                final List<OptionDef> opts = new ArrayList<>();
                final JsonElement options = so.get("options");
                if (options != null && options.isJsonArray()) {
                    for (final JsonElement oe : options.getAsJsonArray()) {
                        if (oe.isJsonPrimitive()) { opts.add(new OptionDef(oe.getAsString(), null, null, null, null, null)); continue; }
                        if (!oe.isJsonObject()) continue;
                        final JsonObject oo = oe.getAsJsonObject();
                        final String path = str(oo, "path", null);
                        if (path == null) continue;
                        NumberRange range = null;
                        if (oo.has("min") && oo.has("max")) range = NumberRange.of(oo.get("min").getAsDouble(), oo.get("max").getAsDouble(), oo.has("step") ? oo.get("step").getAsDouble() : 0);
                        List<String> choices = null;
                        if (oo.has("choices") && oo.get("choices").isJsonArray()) {
                            choices = new ArrayList<>();
                            for (final JsonElement c : oo.getAsJsonArray("choices")) choices.add(c.getAsString());
                        }
                        opts.add(new OptionDef(path, str(oo, "label", null), str(oo, "tooltip", null), range, choices,
                            oo.has("restart") ? oo.get("restart").getAsBoolean() : null));
                    }
                }
                sections.add(new SectionDef(str(so, "title", ""), str(so, "description", null), opts));
            }
        }
        return new PageDef(id, title, icon, sections, file);
    }

    @Nullable
    private static String str(final JsonObject o, final String key, @Nullable final String def) {
        final JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }

    /** Write a page definition (used by "add current page's favourites as a curated page" in the future; kept for tooling). */
    public static void write(final PageDef def) throws IOException {
        final JsonObject o = new JsonObject();
        o.addProperty("id", def.id());
        o.addProperty("title", def.title());
        o.addProperty("icon", def.icon().name());
        final JsonArray secs = new JsonArray();
        for (final SectionDef s : def.sections()) {
            final JsonObject so = new JsonObject();
            so.addProperty("title", s.title());
            if (s.description() != null) so.addProperty("description", s.description());
            final JsonArray opts = new JsonArray();
            for (final OptionDef od : s.options()) {
                final JsonObject oo = new JsonObject();
                oo.addProperty("path", od.path());
                if (od.label() != null) oo.addProperty("label", od.label());
                if (od.tooltip() != null) oo.addProperty("tooltip", od.tooltip());
                opts.add(oo);
            }
            so.add("options", opts);
            secs.add(so);
        }
        o.add("sections", secs);
        TextFile.writeAtomic(def.file(), JsonConfig.GSON.toJson(o));
    }

    private CuratedPages() {}
}
