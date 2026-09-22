package dev.fallingcloud.slate.config.editor;

import dev.fallingcloud.slate.config.doc.DocSection;
import dev.fallingcloud.slate.config.doc.FileDocument;
import dev.fallingcloud.slate.config.option.OptionBinding;
import dev.fallingcloud.slate.config.option.OptionResolvers;
import dev.fallingcloud.slate.config.ui.ApplyQueue;
import dev.fallingcloud.slate.config.ui.OptionRow;
import dev.fallingcloud.slate.config.ui.SectionHeader;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.layout.ui.Rect;
import dev.fallingcloud.slate.core.screen.SlateScreen;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.SlateIconButton;
import dev.fallingcloud.slate.core.widget.SlateLabel;
import dev.fallingcloud.slate.core.widget.SlateScrollPanel;
import dev.fallingcloud.slate.core.widget.SlateSearchField;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The generic config-file editor: a document's sections as collapsible groups of rows, a search filter,
 * the file path, and buttons to reload, open the file in the system editor, or open its folder. Every
 * row writes through; there is no separate save step.
 */
public final class FileEditorScreen extends SlateScreen {

    private final FileDocument doc;
    private String filter = "";
    private final Set<String> collapsed = new HashSet<>();
    private final List<OptionRow> rows = new ArrayList<>();
    @Nullable private SlateScrollPanel panel;
    private double keepScroll = -1;

    public FileEditorScreen(@Nullable final Screen parent, final FileDocument doc) {
        super(doc.title(), parent);
        this.doc = doc;
    }

    public FileDocument document() { return doc; }

    @Override
    protected void build() {
        rows.clear();
        addHeaderAction(new SlateIconButton(0, 0, 20, Icon.FOLDER, Component.translatable("slate_config.editor.folder"), () -> {
            final var parent = doc.path().getParent();
            if (parent != null) Util.getPlatform().openPath(parent);
        }));
        addHeaderAction(new SlateIconButton(0, 0, 20, Icon.EXTERNAL, Component.translatable("slate_config.editor.external"), () -> Util.getPlatform().openPath(doc.path())));
        addHeaderAction(new SlateIconButton(0, 0, 20, Icon.REFRESH, Component.translatable("slate_config.editor.reload"), () -> {
            doc.reload();
            OptionResolvers.invalidate(doc.kind());
            rebuild();
        }));
        final SlateSearchField search = new SlateSearchField(0, 0, 140, t -> { if (!t.equals(filter)) { filter = t; keepScroll = 0; rebuild(); } });
        search.setValue(filter);
        addHeaderAction(search);

        final Rect c = contentRect();
        int top = c.y() + 2;
        final SlateLabel path = add(new SlateLabel(c.x(), top, c.w(), Component.literal(doc.path().toString())).style(SlateLabel.Style.CAPTION));
        top += path.getHeight() + 4;
        if (doc.notice() != null) {
            final SlateLabel notice = add(new SlateLabel(c.x(), top, c.w(), doc.notice()).style(SlateLabel.Style.BODY).color(Theme.current().palette().warning()).wrap(true));
            top += notice.getHeight() + 6;
        }
        final SlateScrollPanel p = add(new SlateScrollPanel(c.x(), top, c.w(), c.bottom() - top));
        panel = p;
        final int w = c.w() - 8;
        final String q = filter.toLowerCase(Locale.ROOT).trim();
        int y = 0;
        final List<DocSection> sections = doc.editable() ? doc.sections() : List.of();
        for (final DocSection s : sections) {
            final List<OptionBinding> items = new ArrayList<>();
            for (final OptionBinding b : s.options()) {
                if (q.isEmpty() || (b.searchText() + " " + s.title().getString() + " " + s.path()).toLowerCase(Locale.ROOT).contains(q)) items.add(b);
            }
            final boolean hasHeader = !s.path().isEmpty() || sections.size() > 1;
            if (items.isEmpty() && (q.isEmpty() ? s.options().isEmpty() && !hasHeader : true)) {
                if (!q.isEmpty() || s.options().isEmpty() && s.path().isEmpty()) continue;
            }
            final boolean isCollapsed = q.isEmpty() && collapsed.contains(s.path());
            if (hasHeader) {
                p.add(new SectionHeader(0, y, w, s.title(), s.options().size(), isCollapsed,
                    () -> { if (!collapsed.remove(s.path())) collapsed.add(s.path()); rebuild(); }, Math.max(0, s.depth() - 1)), 0, y);
                y += SectionHeader.HEIGHT + 2;
                if (s.comment() != null && !isCollapsed) {
                    final SlateLabel d = new SlateLabel(0, y, w - 8 - s.depth() * 8, s.comment()).style(SlateLabel.Style.MUTED).wrap(true);
                    p.add(d, 4 + Math.max(0, s.depth() - 1) * 8, y);
                    y += d.getHeight() + 4;
                }
            }
            if (isCollapsed) { y += 4; continue; }
            for (final OptionBinding b : items) {
                OptionResolvers.publish(b);
                final OptionRow row = new OptionRow(0, y, w, b, Math.max(0, s.depth() - 1)).onChanged(r -> { for (final OptionRow o : rows) if (o != r) o.refresh(); });
                rows.add(row);
                p.add(row, 0, y);
                y += OptionRow.HEIGHT + 2;
            }
            y += 6;
        }
        if (rows.isEmpty() && doc.editable()) {
            p.add(new SlateLabel(4, y + 4, w - 8, Component.translatable(q.isEmpty() ? "slate_config.editor.empty" : "slate_config.search.no_matches")).style(SlateLabel.Style.MUTED), 4, y + 4);
            y += 20;
        }
        p.setContentHeight(y + 4);
        if (keepScroll >= 0) { p.snapScroll(keepScroll); keepScroll = -1; }
    }

    private void rebuild() {
        keepScroll = panel != null ? panel.scrollAmount() : -1;
        clearWidgets();
        init(minecraft, width, height);
    }

    @Override
    public boolean keyPressed(final int keyCode, final int scanCode, final int modifiers) {
        if ((keyCode == 47 || (keyCode == 70 && hasControlDown())) && !(getFocused() instanceof net.minecraft.client.gui.components.EditBox)) {
            for (final var r : renderableList()) if (r instanceof SlateSearchField f) { setFocused(f); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() {
        ApplyQueue.flush();
        super.removed();
    }
}
