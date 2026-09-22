package dev.fallingcloud.slate.config.preset;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.fallingcloud.slate.config.ConfigSettings;
import dev.fallingcloud.slate.config.SlateConfig;
import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.config.option.OptionResolvers;
import dev.fallingcloud.slate.config.option.OptionValues;
import dev.fallingcloud.slate.config.ui.ApplyQueue;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Presets: named snapshots of option values by id. "Performance" and "Quality" ship as resources
 * ({@code slate_config/presets/*.json}); user presets live in {@code config/slate/config.json}. Applying
 * resolves each id live, so entries for absent mods (Sodium on a vanilla install) are simply skipped.
 */
public final class Presets {

    public record ApplyResult(int applied, int skipped) {}

    private static List<ConfigSettings.Preset> builtins;

    public static synchronized List<ConfigSettings.Preset> builtins() {
        if (builtins != null) return builtins;
        builtins = new ArrayList<>();
        for (final String name : List.of("performance", "quality")) {
            try (InputStream in = Presets.class.getResourceAsStream("/slate_config/presets/" + name + ".json")) {
                if (in == null) continue;
                final JsonObject o = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                final ConfigSettings.Preset p = new ConfigSettings.Preset(o.get("name").getAsString(), o.getAsJsonObject("values"), true);
                p.description = o.has("description") ? o.get("description").getAsString() : "";
                builtins.add(p);
            } catch (final Exception e) {
                SlateConfig.LOGGER.warn("[Slate Config] bad built-in preset {}: {}", name, e.toString());
            }
        }
        return builtins;
    }

    public static List<ConfigSettings.Preset> all() {
        final List<ConfigSettings.Preset> out = new ArrayList<>(builtins());
        out.addAll(ConfigSettings.get().presets);
        return out;
    }

    public static ApplyResult apply(final ConfigSettings.Preset preset) {
        int applied = 0, skipped = 0;
        for (final Map.Entry<String, JsonElement> e : preset.values.entrySet()) {
            final OptionBinding b = OptionResolvers.resolve(e.getKey()).orElse(null);
            if (b == null || !b.type().snapshotable()) { skipped++; continue; }
            try {
                b.set(OptionValues.fromJson(b, e.getValue()));
                applied++;
            } catch (final Exception ex) {
                SlateConfig.LOGGER.warn("[Slate Config] preset value {} rejected: {}", e.getKey(), ex.toString());
                skipped++;
            }
        }
        ApplyQueue.flush();
        return new ApplyResult(applied, skipped);
    }

    public static JsonObject snapshot(final List<? extends OptionBinding> bindings) {
        final JsonObject o = new JsonObject();
        for (final OptionBinding b : bindings) {
            if (!b.type().snapshotable()) continue;
            try { o.add(b.id(), OptionValues.toJson(b, b.get())); } catch (final Exception ignored) {}
        }
        return o;
    }

    public static void saveUser(final String name, final String description, final JsonObject values) {
        ConfigSettings.file().update(c -> {
            c.presets.removeIf(p -> p.name.equalsIgnoreCase(name));
            final ConfigSettings.Preset p = new ConfigSettings.Preset(name, values, false);
            p.description = description == null ? "" : description;
            c.presets.add(p);
        });
    }

    public static void deleteUser(final ConfigSettings.Preset preset) {
        ConfigSettings.file().update(c -> c.presets.removeIf(p -> p == preset || p.name.equals(preset.name) && !preset.builtin));
    }

    private Presets() {}
}
