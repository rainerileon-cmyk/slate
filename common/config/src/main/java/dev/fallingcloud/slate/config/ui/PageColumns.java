package dev.fallingcloud.slate.config.ui;

/**
 * A screen that says in how many columns its option pages set their rows. The Custom layout's hub has one: a list.
 * The Overhaul layout's has two where the page is wide enough, as its sketch draws them.
 */
public interface PageColumns {

    /** How many columns of rows a page {@code pageWidth} wide shows (1 = the classic list). */
    int columns(int pageWidth);
}
