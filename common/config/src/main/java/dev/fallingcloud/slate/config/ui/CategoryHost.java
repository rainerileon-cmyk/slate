package dev.fallingcloud.slate.config.ui;

import dev.fallingcloud.slate.core.screen.SidebarPage;
import org.jetbrains.annotations.Nullable;

/** A sidebar page whose top tabs are other pages (a category): exposes the tab on screen. */
public interface CategoryHost {

    /** The page shown in the category's current tab. */
    @Nullable
    SidebarPage activeChild();
}
