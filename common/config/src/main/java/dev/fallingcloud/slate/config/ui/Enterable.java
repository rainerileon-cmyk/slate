package dev.fallingcloud.slate.config.ui;

/** A page that animates its content in on its next build (a category switched to its tab). */
public interface Enterable {

    /** {@code dir} &gt; 0: the tab came from the right. Applies to the next build only. */
    void enterFrom(int dir);
}
