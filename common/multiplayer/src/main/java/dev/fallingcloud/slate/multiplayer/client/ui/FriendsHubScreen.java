package dev.fallingcloud.slate.multiplayer.client.ui;

import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.screen.ScreenIds;
import dev.fallingcloud.slate.core.screen.SidebarPage;
import dev.fallingcloud.slate.core.screen.SidebarScreen;
import dev.fallingcloud.slate.core.screen.slot.CoreSlots;
import dev.fallingcloud.slate.core.screen.slot.Layout;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.core.widget.popup.Popups;
import dev.fallingcloud.slate.multiplayer.MultiplayerConfigs;
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
 * The Friends hub ({@code slate_multiplayer:hub}): Friends / Requests / Groups / Messages / Settings, and Streams
 * when screen sharing is turned on in the module's config. Pages refresh in place on {@link SocialEvents} while
 * the screen is open. Static openers take a page, a thread or a player so toasts and actions land in the right
 * place.
 *
 * <p>Two presentations. The Custom layout's is lists. The Overhaul layout's (docs/LAYOUTS.md, 6) shows the friends
 * in a piece of Minecraft: standing in a meadow, or sitting when they are offline ({@link FriendsScenePage}); a
 * group sits round a campfire ({@link GroupsScenePage}). Its tabs are listed down the left in the order Friends,
 * Messages, Groups, Requests, Screenshots; the search, the order and the plus stand in the header.</p>
 */
public final class FriendsHubScreen extends SidebarScreen {

    public static final List<String> PAGE_IDS = List.of("friends", "requests", "groups", "messages", "screenshots", "streams", "settings");

    /** How the hub shows itself: the Custom layout's lists, or the Overhaul layout's scenes. */
    public enum Presentation { LISTS, SCENES }

    @Nullable private static String pendingPage;
    @Nullable private static String pendingThread;
    @Nullable private static UUID pendingPlayer;
    @Nullable private static Path pendingShare;

    private final Runnable modelListener = this::onModelChanged;
    private final Consumer<LinkState> linkListener = s -> onModelChanged();
    private final Consumer<String> threadListener = this::onThreadChanged;
    private boolean listening;
    private final Presentation presentation;

    /** The hub in the presentation the friends menu's effective layout asks for. */
    public FriendsHubScreen(@Nullable final Screen parent) {
        this(parent, MenuSlots.effective(CoreSlots.FRIENDS) == Layout.OVERHAUL ? Presentation.SCENES : Presentation.LISTS);
    }

    public FriendsHubScreen(@Nullable final Screen parent, final Presentation presentation) {
        super(UiUtil.t("hub.title"), parent, "slate_multiplayer:hub");
        this.presentation = presentation;
    }

    public Presentation presentation() { return presentation; }

    private boolean scenes() { return presentation == Presentation.SCENES; }

    @Override
    protected boolean overhaulNav() { return scenes(); }

    /** The Overhaul header names the page ("Friends – Groups"): the page area starts right under it. */
    @Override
    protected boolean showPageTitle() { return !scenes(); }

    @Override
    public Component getTitle() {
        final SidebarPage page = pages().isEmpty() ? null : currentPage();
        if (!scenes() || page == null || page.title().getString().equals(super.getTitle().getString())) return super.getTitle();
        return Component.literal(super.getTitle().getString() + " – " + page.title().getString());
    }

    @Override
    public void renderBackground(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        if (!scenes() || Theme.current().isVanilla() || (minecraft != null && minecraft.level != null)) return;
        SlateDraw.vgradient(g, 0, HEADER_H, width, height - HEADER_H, 0xFF0B0C0F, 0xFF1A1816);
        SlateDraw.vignette(g, 0, HEADER_H, width, height - HEADER_H, 0.4f);
    }

    static {
        ScreenIds.register(FriendsHubScreen.class, "slate_multiplayer:hub", "Friends hub");
    }

    // ------------------------------------------------------------------ openers

    /** A hub that opens on {@code page} (the screenshot harness: {@code slate_multiplayer:hub/settings}). */
    public static FriendsHubScreen create(@Nullable final Screen parent, @Nullable final String page) {
        pendingPage = page;
        return new FriendsHubScreen(parent);
    }

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
        if (scenes()) {
            pages.add(new FriendsScenePage(this));
            pages.add(new MessagesPage(this));
            pages.add(new GroupsScenePage(this));
            pages.add(new RequestsPage(this));
            pages.add(new ShotsPage(this));
        } else {
            pages.add(new FriendsPage(this));
            pages.add(new RequestsPage(this));
            pages.add(new GroupsPage(this));
            pages.add(new MessagesPage(this));
        }
        // Screen sharing is off unless the config asks for it.
        if (MultiplayerConfigs.client().streams) pages.add(new StreamsPage(this));
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
        // With scenes the header's right end belongs to the search, the order and the plus: the hub says how it is
        // doing beside the title instead.
        final int x = scenes() ? headerTitleX() + font.width(dev.fallingcloud.slate.core.gfx.Fonts.heading(getTitle())) + 22 : width - PAD - w;
        final int y = (HEADER_H - 9) / 2;
        if (scenes() && x + w > width - PAD - 250) return;
        g.fill(x - 9, y + 2, x - 5, y + 6, dot);
        g.drawString(font, text, x, y, Colors.withAlpha(p.textMuted(), 0xFF), t.isVanilla());
        if (sc.linkState() == LinkState.FAILED && !sc.stateDetail().isEmpty() && mouseX >= x - 9 && mouseX < x + w && mouseY < HEADER_H) {
            dev.fallingcloud.slate.core.widget.SlateTooltips.request(Component.literal(sc.stateDetail()), null);
        }
    }
}
