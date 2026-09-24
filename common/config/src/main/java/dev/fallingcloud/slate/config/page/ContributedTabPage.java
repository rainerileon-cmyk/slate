package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.SlateConfig;
import dev.fallingcloud.slate.config.api.SettingsTabs;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.Section;
import java.util.List;

/**
 * A tab another module added to a category through {@link SettingsTabs}: its sections, rebuilt on every show, all
 * on one scrolling tab under collapsible headers (the category strip already names the tab; a second strip of
 * pills for its groups was one strip too many).
 */
public final class ContributedTabPage extends OptionPageBase {

    private final SettingsTabs.Tab tab;

    public ContributedTabPage(final SettingsTabs.Tab tab) {
        super(tab.id(), tab.title(), tab.icon());
        this.tab = tab;
    }

    public SettingsTabs.Tab tab() { return tab; }

    @Override
    protected List<Section> sections() {
        try {
            final List<Section> s = tab.sections().get();
            return s == null ? List.of() : s;
        } catch (final Exception e) {
            SlateConfig.LOGGER.warn("[Slate Config] settings tab {} failed to build: {}", tab.id(), e.toString());
            return List.of();
        }
    }
}
