package dev.fallingcloud.slate.chat.client;

import dev.fallingcloud.slate.chat.ChatConfig;
import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Clock;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.media.Attachment;
import dev.fallingcloud.slate.core.theme.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.GuiMessage;
import org.jetbrains.annotations.Nullable;

/**
 * Frame-to-frame hand-off between the chat renderer and the input side (the ChatScreen mixin), plus the
 * slide-in clock and the unread counter.
 *
 * <p>The renderer rebuilds the rect lists every frame in SCREEN space (GUI px, the slide baked in), so the
 * click side needs none of the chat's scale/scroll maths: whatever transform the renderer applied is
 * already in the rect. Clicks hit-test against the previous frame - by the time a click arrives the player
 * has been looking at that exact frame. Render thread only; plain fields.</p>
 */
public final class ChatRenderState {

    /** A clickable attachment card. */
    public record ClickRect(int x0, int y0, int x1, int y1, Attachment attachment, GuiMessage message) {
        public boolean contains(final double mx, final double my) { return mx >= x0 && mx < x1 && my >= y0 && my < y1; }
    }

    /** A hover-action button on a message, or the "new messages" pill (jump to bottom). */
    public enum Action { REPLY, COPY, LINK, SCROLL_BOTTOM }

    public record ActionRect(int x0, int y0, int x1, int y1, Action action, GuiMessage message) {
        public boolean contains(final double mx, final double my) { return mx >= x0 && mx < x1 && my >= y0 && my < y1; }
    }

    /** One drawn row: screen rect + the text origin (for style hit-testing) in chat units and the scale. */
    public record RowHit(int index, GuiMessage.Line line, @Nullable GuiMessage message, int x0, int y0, int x1, int y1, int textX, float scale, boolean textRow) {
        public boolean contains(final double mx, final double my) { return mx >= x0 && mx < x1 && my >= y0 && my < y1; }
    }

    /** The scrollbar thumb/track in screen space (only when the chat is open and overflows). */
    public record Scrollbar(int x0, int y0, int x1, int y1, int thumbY0, int thumbY1, int maxScroll) {
        public boolean contains(final double mx, final double my) { return mx >= x0 - 2 && mx < x1 + 2 && my >= y0 && my < y1; }
    }

    public static final List<ClickRect> clickRects = new ArrayList<>();
    public static final List<ActionRect> actionRects = new ArrayList<>();
    public static final List<RowHit> rowHits = new ArrayList<>();
    @Nullable public static Scrollbar scrollbar;
    @Nullable public static GuiMessage hovered;
    /** Bottom-left of the panel (screen space), for the typing line and unread badge to sit under. */
    public static int panelBottom, panelLeft, panelRight;

    private static long lastMessageMs;
    private static int unread;
    private static final Anim unreadAnim = new Anim(0, 220, Ease.OUT_BACK);

    public static void beginFrame() {
        clickRects.clear();
        actionRects.clear();
        rowHits.clear();
        scrollbar = null;
        hovered = null;
    }

    // ------------------------------------------------------------------ animation

    public static void onNewMessage() {
        lastMessageMs = Clock.nowMs();
    }

    /**
     * How far the newest message still has to slide, in fractions of a line height: 1 = a full line below
     * its resting place, 0 = settled. Cubic ease-out on the remaining distance.
     */
    public static float slideOffset() {
        final ChatConfig cfg = ChatConfig.get();
        if (!cfg.animation || Theme.current().motion() <= 0) return 0;
        final float dur = Math.max(30, Theme.current().ms(Math.max(50, cfg.animationMs)));
        final float t = (Clock.nowMs() - lastMessageMs) / dur;
        if (t >= 1 || t < 0) return 0;
        final float inv = 1 - t;
        return inv * inv * inv;
    }

    /**
     * The slide distance in GUI pixels, for anything that must move WITH the chat but does not go through
     * the pose matrix - {@code GuiGraphics.enableScissor} converts its rectangle straight to window
     * coordinates and never consults the pose.
     */
    public static int slidePixels(final int lineHeight, final float scale) {
        return Math.round(slideOffset() * lineHeight * scale);
    }

    // ------------------------------------------------------------------ unread

    public static void noteUnread() {
        unread++;
        unreadAnim.set(1f);
    }

    public static void markRead() {
        unread = 0;
        unreadAnim.set(0f);
    }

    public static int unread() { return unread; }

    /** 0..1 appearance of the badge. */
    public static float unreadVisibility() { return unreadAnim.get(); }

    public static void reset() {
        beginFrame();
        markRead();
        unreadAnim.snap(0);
    }

    // ------------------------------------------------------------------ hit tests (screen space)

    @Nullable
    public static ClickRect cardAt(final double mx, final double my) {
        for (final ClickRect r : clickRects) if (r.contains(mx, my)) return r;
        return null;
    }

    @Nullable
    public static ActionRect actionAt(final double mx, final double my) {
        for (final ActionRect r : actionRects) if (r.contains(mx, my)) return r;
        return null;
    }

    @Nullable
    public static RowHit rowAt(final double mx, final double my) {
        for (final RowHit r : rowHits) if (r.contains(mx, my)) return r;
        return null;
    }

    private ChatRenderState() {}
}
