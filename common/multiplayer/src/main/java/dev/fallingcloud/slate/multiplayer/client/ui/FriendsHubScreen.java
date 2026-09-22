package dev.fallingcloud.slate.multiplayer.client.ui;

import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.screen.ScreenIds;
import dev.fallingcloud.slate.core.screen.SidebarPage;
import dev.fallingcloud.slate.core.screen.SidebarScreen;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import dev.fallingcloud.slate.multiplayer.client.Friend;
import dev.fallingcloud.slate.multiplayer.client.LinkState;
import dev.fallingcloud.slate.multiplayer.client.SocialClient;
import dev.fallingcloud.slate.multiplayer.client.SocialEvents;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The Friends hub ({@code slate_multiplayer:hub}): Friends / Requests / Groups / Messages / Streams /
 * Settings. Pages refresh in place on {@link SocialEvents} while the screen is open. Static openers take
 * a page, a thread or a player so toasts and actions land in the right place.
 */
public final class FriendsHubScreen extends SidebarScreen {

    public static final List<String> PAGE_IDS = List.of("friends", "requests", "groups", "messages", "streams", "settings");

    @Nullable private static String pendingPage;
    @Nullable private static String pendingThread;
    @Nullable private static UUID pendingPlayer;
    @Nullable private static Path pendingShare;

    private final Runnable modelListener = this::onModelChanged;
    private final Consumer<LinkState> linkListener = s -> onModelChanged();
    private final Consumer<String> threadListener = this::onThreadChanged;
    private boolean listening;

    public FriendsHubScreen(@Nullable final Screen parent) {
        super(UiUtil.t("hub.title"), parent, "slate_multiplayer:hub");
    }

    static {
        ScreenIds.register(FriendsHubScreen.class, "slate_multiplayer:hub", "Friends hub");
    }

    // ------------------------------------------------------------------ openers

    public static void open(@Nullable final String page) {
        final Minecraft mc = Minecraft.getInstance();
        pendingPage = page;
        if (mc.screen instanceof FriendsHubScreen s) { s.applyPending(); return; }
        mc.setScreen(new FriendsHubScreen(mc.screen));
    }

    public static void openThread(final String threadKey) {
        pendingThread = threadKey;
        open("messages");
    }

    public static void openPlayer(final UUID uuid) {
        pendingPlayer = uuid;
        open("friends");
    }

    /** Menu's gallery: share a screenshot to friends (the Messages page attaches it to the chosen thread). */
    public static void shareImage(final Path file) {
        pendingShare = file;
        open("messages");
    }

    @Nullable static String takePendingThread() { final String t = pendingThread; pendingThread = null; return t; }

    @Nullable static Path takePendingShare() { final Path p = pendingShare; pendingShare = null; return p; }

    private void applyPending() {
        if (pendingPage != null) { showPage(pendingPage); pendingPage = null; }
        if (pendingPlayer != null) {
            final UUID u = pendingPlayer;
            pendingPlayer = null;
            final Friend f = SocialClient.get().friend(u);
            PlayerCardPopup.open(u, f == null ? "" : f.name, -1, -1);
        }
    }

    // ------------------------------------------------------------------ pages

    @Override
    protected void definePages(final List<SidebarPage> pages) {
        pages.add(new FriendsPage(this));
        pages.add(new RequestsPage(this));
        pages.add(new GroupsPage(this));
        pages.add(new MessagesPage(this));
        pages.add(new StreamsPage(this));
        pages.add(new SettingsPage(this));
    }

    @Override
    protected void build() {
        super.build();
        if (!listening) {
            listening = true;
            SocialEvents.MODEL_CHANGED.register(modelListener);
            SocialEvents.LINK_STATE.register(linkListener);
            SocialEvents.THREAD_CHANGED.register(threadListener);
        }
        applyPending();
    }

    @Override
    public void removed() {
        super.removed();
        if (listening) {
            listening = false;
            SocialEvents.MODEL_CHANGED.unregister(modelListener);
            SocialEvents.LINK_STATE.unregister(linkListener);
            SocialEvents.THREAD_CHANGED.unregister(threadListener);
        }
        Popups.closeAll();
    }

    private void onModelChanged() {
        if (currentPage() instanceof HubPage p) p.onModelChanged();
    }

    private void onThreadChanged(final String key) {
        if (currentPage() instanceof HubPage p) p.onThreadChanged(key);
    }

    /** A page that refreshes in place. */
    abstract static class HubPage extends SidebarPage {
        protected final FriendsHubScreen screen;

        HubPage(final FriendsHubScreen screen, final String id, final Component title, final dev.fallingcloud.slate.core.gfx.Icon icon) {
            super(id, title, icon);
            this.screen = screen;
        }

        void onModelChanged() {}

        void onThreadChanged(final String key) {}
    }

    // ------------------------------------------------------------------ header status

    @Override
    protected void renderContent(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        final SocialClient sc = SocialClient.get();
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final String text;
        final int dot;
        switch (sc.linkState()) {
            case CONNECTED -> { text = (sc.hubName().isEmpty() ? "Connected" : sc.hubName()) + (sc.latencyMs() >= 0 ? "  " + sc.latencyMs() + " ms" : ""); dot = p.success(); }
            case CONNECTING, AUTHENTICATING -> { text = "Connecting..."; dot = p.warning(); }
            case FAILED -> { text = "Offline"; dot = p.danger(); }
            default -> { text = "No hub"; dot = p.textDim(); }
        }
        final int w = SlateDraw.width(text);
        final int x = width - PAD - w, y = (HEADER_H - 9) / 2;
        g.fill(x - 9, y + 2, x - 5, y + 6, dot);
        g.drawString(font, text, x, y, Colors.withAlpha(p.textMuted(), 0xFF), t.isVanilla());
        if (sc.linkState() == LinkState.FAILED && !sc.stateDetail().isEmpty() && mouseX >= x - 9 && mouseX < width && mouseY < HEADER_H) {
            dev.fallingcloud.slate.core.widget.SlateTooltips.request(Component.literal(sc.stateDetail()), null);
        }
    }
}
