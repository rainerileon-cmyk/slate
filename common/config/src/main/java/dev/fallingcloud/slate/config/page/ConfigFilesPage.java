package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.ConfigPlatform;
import dev.fallingcloud.slate.config.doc.ConfigFiles;
import dev.fallingcloud.slate.config.doc.Documents;
import dev.fallingcloud.slate.config.doc.FileDocument;
import dev.fallingcloud.slate.config.editor.FileEditorScreen;
import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.platform.ModInfo;
import dev.fallingcloud.slate.core.platform.SlatePlatform;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Advanced › Config files: every config file of every installed mod, one collapsible group per mod, each row opening
 * the raw editor ({@link FileEditorScreen}). The loader's native configs come first (typed rows), then whatever the
 * config folder holds under the mod's id. The header search matches file and mod names.
 */
public final class ConfigFilesPage extends OptionPageBase {

    public ConfigFilesPage() {
        super("files", Component.translatable("slate_config.page.files"), Icon.CODE);
        level(Level.ALL);
        emptyText(Component.translatable("slate_config.files.none"));
    }

    @Override
    protected boolean pills(final String tabKey) { return false; }

    @Override
    protected List<Section> sections() {
        final Minecraft mc = Minecraft.getInstance();
        final List<ModInfo> mods = new ArrayList<>(SlatePlatform.get().allMods());
        mods.sort(Comparator.comparing(m -> m.name().toLowerCase(Locale.ROOT)));
        final List<Section> out = new ArrayList<>();
        final Path configDir = SlatePlatform.get().configDir();
        for (final ModInfo m : mods) {
            final Section s = Section.of("mod." + m.id(), Component.literal(m.name()));
            final Set<String> covered = new HashSet<>();
            int i = 0;
            for (final FileDocument doc : ConfigPlatform.get().nativeConfigs(m.id())) {
                covered.add(key(doc.path()));
                s.add(Binding.of("file:" + m.id() + ":" + i++, OptionType.ACTION, doc.title())
                    .tooltip(Component.literal(relative(configDir, doc.path())))
                    .actionIcon(Icon.SLIDERS)
                    .action(Component.translatable("slate_config.files.edit"), () -> mc.setScreen(new FileEditorScreen(mc.screen, doc)))
                    .searchWords(m.id() + " " + m.name() + " config file"));
            }
            for (final Path p : ConfigFiles.forMod(m.id())) {
                if (covered.contains(key(p))) continue;
                s.add(Binding.of("file:" + m.id() + ":" + i++, OptionType.ACTION, Component.literal(p.getFileName().toString()))
                    .tooltip(Component.literal(relative(configDir, p)))
                    .actionIcon(Icon.CODE)
                    // Read when opened: parsing every file of a 300-mod pack up front would freeze the page.
                    .action(Component.translatable("slate_config.files.edit"), () -> Documents.open(p, m.id()).ifPresent(doc -> mc.setScreen(new FileEditorScreen(mc.screen, doc))))
                    .searchWords(m.id() + " " + m.name() + " config file"));
            }
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private static String relative(final Path configDir, final Path p) {
        try {
            return "config/" + configDir.toAbsolutePath().normalize().relativize(p.toAbsolutePath().normalize()).toString().replace('\\', '/');
        } catch (final Exception e) {
            return String.valueOf(p);
        }
    }

    private static String key(final Path p) {
        try { return p.toAbsolutePath().normalize().toString(); } catch (final Exception e) { return String.valueOf(p); }
    }
}
