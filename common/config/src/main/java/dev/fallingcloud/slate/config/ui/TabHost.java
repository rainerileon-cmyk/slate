package dev.fallingcloud.slate.config.ui;

/** A hub page with top tabs: lets the hub cycle them from the keyboard and style the title row. */
public interface TabHost {

    /**
     * Ctrl+PgUp/PgDn: step the top tabs by {@code dir}; {@code secondary} steps the small tabs under them
     * instead (Ctrl+Shift). @return true when a tab changed
     */
    boolean cycleTab(int dir, boolean secondary);

    /** True while a top tab strip sits directly under the page title (the title then drops its rule). */
    boolean hasTopTabs();

    /** Number of top tabs (1 when the page has none). */
    int tabCount();
}
