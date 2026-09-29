package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.iris.IrisBridge;
import dev.fallingcloud.slate.config.ui.ApplyQueue;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.OptionRow;
import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.gfx.Icons;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateBadge;
import dev.fallingcloud.slate.core.widget.SlateButton;
import dev.fallingcloud.slate.core.widget.SlateList;
import dev.fallingcloud.slate.core.media.FilePicker;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Customization › Shader Packs (only with Iris): the shaders on/off toggle, every pack in
 * {@code shaderpacks/} with the active one marked, Apply (switches through Iris' config and reloads; falls
 * back to Iris' own screen when that is not reachable), the folder, and Iris' screen for per-pack options.
 */
public final class ShaderPacksPage extends OptionPageBase {

    private static final String TAB = "shaders";

    @Nullable private String chosen;
    @Nullable private String active;
    @Nullable private SlateButton applyButton;
    private final Set<String> folders = new HashSet<>();

    public ShaderPacksPage() {
        super("shaders", Component.translatable("slate_config.page.shaders"), Icon.SHADER);
    }

    @Override
    protected boolean pills(final String tabKey) { return false; }

    @Override
    protected List<Section> sections() {
        active = IrisBridge.selectedPack();
        if (chosen == null) chosen = active;
        final Section s = Section.of("packs", Component.empty()).fixed().tab(TAB, title());
        s.add(IrisBridge.enabledBinding());
        s.custom(this::toolbar);
        s.custom(this::packList);
        return List.of(s);
    }

    private net.minecraft.client.gui.components.AbstractWidget toolbar(final int w) {
        final int bw = Math.max(56, (w - 24) / 4);
        final SlateButton apply = new SlateButton(0, 0, bw, Component.translatable("slate_config.shaders.apply"), this::applyChosen)
            .icon(Icon.CHECK).variant(SlateButton.Variant.PRIMARY);
        applyButton = apply;
        updateApply();
        final SlateButton imp = new SlateButton(0, 0, bw, Component.translatable("slate_config.shaders.import"), this::importPacks).icon(Icon.IMPORT);
        imp.tip(Component.translatable("slate_config.shaders.import.tip"));
        final SlateButton folder = new SlateButton(0, 0, bw, Component.translatable("slate_config.shaders.folder"),
            () -> net.minecraft.Util.getPlatform().openPath(IrisBridge.packsDir())).icon(Icon.FOLDER);
        final SlateButton iris = new SlateButton(0, 0, bw, Component.translatable("slate_config.shaders.iris_screen"),
            () -> IrisBridge.openIrisScreen(Minecraft.getInstance().screen)).icon(Icon.EXTERNAL);
        iris.tip(Component.translatable("slate_config.shaders.iris_screen.tip"));
        return new ControlsPage.Toolbar(w, List.of(apply, imp, folder, iris));
    }

    /** Native file dialog → copies into {@code shaderpacks/}; the first pack imported is selected, ready for Apply. */
    private void importPacks() {
        final Path dir = IrisBridge.packsDir();
        final List<Path> picked = FilePicker.openFiles(Component.translatable("slate_config.shaders.import"), dir, "Shader packs (*.zip)", "*.zip");
        if (picked.isEmpty()) return;
        final List<String> names = FilePicker.copyInto(picked, dir);
        if (names.isEmpty()) return;
        chosen = names.get(0);
        SlateToasts.show(Component.translatable("slate_config.shaders.imported", names.size()), Component.literal(String.join(", ", names)), Icon.SHADER);
        rebuild();
    }

    private net.minecraft.client.gui.components.AbstractWidget packList(final int w) {
        final List<String> packs = IrisBridge.packs();
        final Path dir = IrisBridge.packsDir();
        folders.clear();
        for (final String p : packs) if (Files.isDirectory(dir.resolve(p))) folders.add(p);
        final int h = Math.max(80, (area == null ? 300 : area.h()) - (OptionRow.HEIGHT + 2) - 26 - 16);
        final SlateList<String> l = new SlateList<String>(0, 0, w, h, 18, (g, name, index, x, y, rw, rh, hovered, sel, mx, my) -> {
            final Theme t = Theme.current();
            final Palette p = t.palette();
            final boolean vanilla = t.isVanilla();
            final boolean isActive = name.equals(active);
            final int fg = sel ? p.text() : vanilla ? 0xFFE0E0E0 : p.textMuted();
            Icons.draw(g, folders.contains(name) ? Icon.FOLDER : Icon.PACK, x + 6, y + (rh - 10) / 2, 10, isActive ? p.accent() : Colors.withAlpha(fg, 0xC0));
            final Component badge = isActive ? Component.translatable(IrisBridge.enabled() ? "slate_config.shaders.active" : "slate_config.shaders.active_off") : null;
            final int badgeW = badge == null ? 0 : SlateDraw.width(badge) + 12;
            g.drawString(SlateDraw.font(), SlateDraw.truncate(Component.literal(displayName(name)), rw - 30 - badgeW), x + 22, SlateDraw.textY(y, rh), fg, vanilla);
            if (badge != null) SlateBadge.draw(g, badge, x + rw - 6 - (badgeW - 4), y + (rh - 10) / 2, IrisBridge.enabled() ? p.accent() : p.textDim());
        }).gap(1).emptyText(Component.translatable("slate_config.shaders.empty"));
        l.items(packs);
        final int sel = chosen == null ? -1 : packs.indexOf(chosen);
        if (sel >= 0) { l.select(sel); l.ensureVisible(sel); }
        l.onSelect(n -> { chosen = n; updateApply(); });
        l.onActivate(n -> { chosen = n; applyChosen(); });
        return l;
    }

    @Override
    protected void onRowChanged(final OptionRow row) {
        super.onRowChanged(row);
        updateApply();                                          // shaders toggled: "Apply" re-enables the pack
    }

    private static String displayName(final String file) {
        return file.toLowerCase(Locale.ROOT).endsWith(".zip") ? file.substring(0, file.length() - 4) : file;
    }

    private void updateApply() {
        if (applyButton != null) applyButton.enabled(chosen != null && (!chosen.equals(active) || !IrisBridge.enabled()));
    }

    private void applyChosen() {
        final String name = chosen;
        if (name == null) return;
        // One tick later: the click finishes and the button releases before Iris compiles the pack.
        ApplyQueue.later("iris:apply", 30, () -> {
            if (IrisBridge.applyPack(name)) {
                SlateToasts.show(Component.translatable("slate_config.shaders.applied"), Component.literal(displayName(name)), Icon.SHADER);
                rebuild();
            } else {
                IrisBridge.openIrisScreen(Minecraft.getInstance().screen);
            }
        });
    }
}
