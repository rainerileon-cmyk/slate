package dev.fallingcloud.slate.chat.client;

import java.util.List;
import net.minecraft.client.GuiMessage;

/**
 * What the renderer and channel logic need from vanilla's {@code ChatComponent}; implemented by the
 * mixin over its shadowed fields. Everything here is client main thread.
 */
public interface ChatAccess {

    /** Vanilla's trimmed line list: index 0 is the newest (bottom) row. */
    List<GuiMessage.Line> slate$lines();

    /** Every message kept, newest first. */
    List<GuiMessage> slate$all();

    /** Rows scrolled up from the bottom. */
    int slate$scroll();

    void slate$setScroll(int rows);

    boolean slate$newSinceScroll();

    void slate$setNewSinceScroll(boolean flag);

    /** Chat width in GUI px (config override or vanilla option). */
    int slate$width();

    /** Chat height in GUI px for the current focus state. */
    int slate$height();

    double slate$scale();

    int slate$lineHeight();

    boolean slate$focused();

    /** Re-wraps every message (vanilla's rescaleChat: also resets the scroll). */
    void slate$refresh();

    /** Adds one message to the display list only (used to inject DM thread lines). */
    void slate$addToDisplay(GuiMessage message);
}
