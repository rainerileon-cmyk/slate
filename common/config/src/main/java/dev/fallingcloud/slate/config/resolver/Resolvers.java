package dev.fallingcloud.slate.config.resolver;

import dev.fallingcloud.slate.config.ConfigPlatform;
import dev.fallingcloud.slate.config.SlateConfigApi;
import dev.fallingcloud.slate.config.doc.DocSection;
import dev.fallingcloud.slate.config.doc.Documents;
import dev.fallingcloud.slate.config.doc.FileDocument;
import dev.fallingcloud.slate.config.doc.JsonDocument;
import dev.fallingcloud.slate.config.doc.PropertiesDocument;
import dev.fallingcloud.slate.config.doc.TomlDocument;
import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.KeyBinding;
import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.config.option.OptionResolver;
import dev.fallingcloud.slate.config.option.OptionResolvers;
import dev.fallingcloud.slate.config.sodium.SodiumResolver;
import dev.fallingcloud.slate.core.config.JsonConfig;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * The built-in path resolvers:
 * <ul>
 * <li>{@code optionsTxt:<key>} - vanilla options ({@link VanillaOptions})</li>
 * <li>{@code key:<mapping name>} - a key bind</li>
 * <li>{@code json:<file>:<pointer>} - a JSON file relative to the game dir, RFC 6901 pointer</li>
 * <li>{@code toml:<modid>:<file>:<section.key>} - a TOML file relative to the config dir (native spec on NeoForge when tracked)</li>
 * <li>{@code props:<file>:<key>} - a .properties/.cfg file relative to the game dir</li>
 * <li>{@code slate:<module>:<key>} - a Slate module's {@code config/slate/<module>.json} (core edits apply live)</li>
 * <li>{@code sodium:<id>} - a Sodium option by ResourceLocation (when Sodium is loaded)</li>
 * </ul>
 */
public final class Resolvers {

    public static void registerAll() {
        OptionResolvers.register(new OptionResolver() {
            @Override public String prefix() { return "optionsTxt"; }
            @Override public Optional<OptionBinding> resolve(final String rest) { return VanillaOptions.get(rest); }
        });
        OptionResolvers.register(new OptionResolver() {
            @Override public String prefix() { return "key"; }
            @Override public Optional<OptionBinding> resolve(final String rest) {
                for (final KeyMapping m : Minecraft.getInstance().options.keyMappings) if (m.getName().equals(rest)) return Optional.of(new KeyBinding(m));
                return Optional.empty();
            }
        });
        OptionResolvers.register(new OptionResolver() {
            @Override public String prefix() { return "json"; }
            @Override public Optional<OptionBinding> resolve(final String rest) {
                final int i = rest.lastIndexOf(':');
                if (i <= 0) return Optional.empty();
                final Path file = Documents.locate(rest.substring(0, i));
                if (!Files.isRegularFile(file)) return Optional.empty();
                final JsonDocument doc = Documents.json(file, "");
                return doc.binding(rest.substring(i + 1));
            }
        });
        OptionResolvers.register(new OptionResolver() {
            @Override public String prefix() { return "toml"; }
            @Override public Optional<OptionBinding> resolve(final String rest) {
                final String[] parts = rest.split(":", 3);
                final String modId, file, path;
                if (parts.length == 3) { modId = parts[0]; file = parts[1]; path = parts[2]; }
                else if (parts.length == 2) { modId = ""; file = parts[0]; path = parts[1]; }
                else return Optional.empty();
                // Loader-tracked spec first: values apply in memory immediately and the mod's own save writes the file.
                final String fileName = Path.of(file).getFileName().toString();
                final Optional<FileDocument> nativeDoc = ConfigPlatform.get().nativeConfig(fileName);
                if (nativeDoc.isPresent() && nativeDoc.get().editable()) {
                    for (final DocSection s : nativeDoc.get().sections()) {
                        for (final OptionBinding b : s.options()) if (b.id().endsWith(":" + path)) return Optional.of(b);
                    }
                }
                Path p = SlatePlatform.get().configDir().resolve(file);
                if (!Files.isRegularFile(p)) p = Documents.locate(file);
                if (!Files.isRegularFile(p)) return Optional.empty();
                final TomlDocument doc = Documents.toml(p, modId);
                return doc.binding(path);
            }
        });
        OptionResolvers.register(new OptionResolver() {
            @Override public String prefix() { return "props"; }
            @Override public Optional<OptionBinding> resolve(final String rest) {
                final int i = rest.lastIndexOf(':');
                if (i <= 0) return Optional.empty();
                final Path file = Documents.locate(rest.substring(0, i));
                if (!Files.isRegularFile(file)) return Optional.empty();
                final PropertiesDocument doc = Documents.properties(file, "");
                return doc.binding(rest.substring(i + 1));
            }
        });
        OptionResolvers.register(new OptionResolver() {
            @Override public String prefix() { return "slate"; }
            @Override public Optional<OptionBinding> resolve(final String rest) {
                final int i = rest.indexOf(':');
                if (i <= 0) return Optional.empty();
                final String module = rest.substring(0, i), key = rest.substring(i + 1);
                if (module.equals("core")) return CoreBindings.get(key);
                return slateModuleBinding(module, key);
            }
        });
        OptionResolvers.register(new SodiumResolver());
        OptionResolvers.register(new OptionResolver() {
            @Override public String prefix() { return "iris"; }
            @Override public Optional<OptionBinding> resolve(final String rest) {
                if (!SlatePlatform.get().isModLoaded("iris")) return Optional.empty();
                for (final OptionBinding b : dev.fallingcloud.slate.config.iris.IrisBridge.bindings()) if (b.id().equals("iris:" + rest)) return Optional.of(b);
                return Optional.empty();
            }
        });
        OptionResolvers.register(new OptionResolver() {
            @Override public String prefix() { return "voicechat"; }
            @Override public Optional<OptionBinding> resolve(final String rest) {
                if (!SlatePlatform.get().isModLoaded("voicechat")) return Optional.empty();
                for (final OptionBinding b : dev.fallingcloud.slate.config.voice.VoicechatBridge.section().bindings()) if (b.id().equals("voicechat:" + rest)) return Optional.of(b);
                return Optional.empty();
            }
        });
    }

    /** A field of {@code config/slate/<module>.json}; the module's reload hook runs after each write. */
    public static Optional<OptionBinding> slateModuleBinding(final String module, final String key) {
        final Path file = JsonConfig.dir().resolve(module + ".json");
        if (!Files.isRegularFile(file)) return Optional.empty();
        final JsonDocument doc = Documents.json(file, "slate_" + module);
        final String pointer = key.startsWith("/") ? key : "/" + key.replace('.', '/');
        return doc.binding(pointer).map(b -> wrapSlateModule(module, b));
    }

    /** All top-level bindings of a Slate module's json, for the module pages. */
    public static java.util.List<OptionBinding> slateModuleBindings(final String module) {
        final java.util.List<OptionBinding> out = new java.util.ArrayList<>();
        final Path file = JsonConfig.dir().resolve(module + ".json");
        if (!Files.isRegularFile(file)) return out;
        final JsonDocument doc = Documents.json(file, "slate_" + module);
        for (final DocSection s : doc.sections()) {
            if (!s.path().isEmpty()) continue;             // top level only: nested objects are the module's own business
            for (final OptionBinding b : s.options()) out.add(wrapSlateModule(module, b));
        }
        return out;
    }

    private static OptionBinding wrapSlateModule(final String module, final OptionBinding b) {
        final boolean hasHook = SlateConfigApi.hasReloadHook(module);
        final String id = "slate:" + module + ":" + b.id().substring(b.id().lastIndexOf(':') + 2).replace('/', '.');
        return new Binding(id, b.type(), b.label())
            .tooltip(b.tooltip())
            .getter(b::get)
            .setter(v -> { b.set(v); SlateConfigApi.runReloadHook(module); })
            .def(b.defaultValue())
            .range(b.range())
            .choices(b.choices())
            .restart(!hasHook)
            .searchWords("slate " + module);
    }

    private Resolvers() {}
}
