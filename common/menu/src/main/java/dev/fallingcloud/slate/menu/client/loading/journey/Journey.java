package dev.fallingcloud.slate.menu.client.loading.journey;

import com.mojang.blaze3d.platform.NativeImage;
import dev.fallingcloud.slate.core.gfx.Fonts;
import dev.fallingcloud.slate.core.gfx.SlateDraw;
import dev.fallingcloud.slate.core.screen.slot.CoreSlots;
import dev.fallingcloud.slate.core.screen.slot.Layout;
import dev.fallingcloud.slate.core.screen.slot.MenuSlots;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Palette;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.menu.mixin.LevelLoadingScreenAccessor;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * The loading screens of the Overhaul layout: the way into a world and out of it, shown as one journey although the
 * game makes it in several screens (reading the world, preparing the spawn area, loading terrain; or connecting,
 * logging in, loading terrain). The scene is kept from one of those screens to the next, so what has come up stays
 * up and the camera goes on where it was.
 *
 * <ul>
 *   <li>A world of one's own is {@link LandScene}: its land rising as it loads, going down again when it is left.</li>
 *   <li>A server is {@link GatewayScene}: a gate that takes fire as the server answers.</li>
 *   <li>A wait inside a world (another dimension, a respawn) keeps the game's own backdrop, under the same words.</li>
 * </ul>
 * Over the scene stand the words: what is happening, to which world, how far it is, the chunk map while the spawn
 * area is prepared, and the screen's own buttons.
 */
public final class Journey {

    private enum Kind { LAND, GATEWAY, PLAIN }

    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(SlateMenu.MOD_ID, "journey/server_icon");
    private static final ResourceLocation NO_ICON = ResourceLocation.withDefaultNamespace("textures/misc/unknown_server.png");
    /** What the game says on its way into a world, before the world runs. */
    private static final java.util.Set<String> OPENING = java.util.Set.of("selectWorld.data_read", "selectWorld.resource_load", "createWorld.preparing",
        "slate_menu.loading.generic");
    /** A scene that nobody has drawn for this long is over, whatever the screen. */
    private static final long OVER_MS = 20_000L;

    @Nullable private static LandScene land;
    @Nullable private static GatewayScene gateway;
    private static long drawnAt;
    /**
     * A journey is on: loading screens have been following each other. It ends when the game shows anything else
     * ({@link #tick}), not when frames are far apart: a world that is slow to come in holds the picture for seconds.
     */
    private static boolean running;
    private static int elsewhere;
    /** The land of the world that is being left, taken the moment the leaving began. */
    @Nullable private static Land left;
    @Nullable private static String leftName;
    /** When the leaving began, and whether what is left is a world of one's own. */
    private static long leftAt;
    private static boolean leftLocal;
    /** The server that is being joined: what the list calls it, where it is, its picture. */
    @Nullable private static String serverName, serverAddress;
    private static boolean serverIcon;
    /** The progress shown: it follows the real one without jumping. */
    private static float shown;
    private static long shownAt;
    private static float wordsIn;

    private Journey() {}

    /** Whether the loading screens are the Overhaul layout's. */
    public static boolean active() {
        return MenuSlots.effective(CoreSlots.LEVEL_LOADING) == Layout.OVERHAUL;
    }

    // ------------------------------------------------------------------ what the game tells

    /** A connection to a server begins. */
    public static void joining(@Nullable final ServerData data, final String address) {
        serverName = data != null && data.name != null && !data.name.isBlank() ? data.name : address;
        serverAddress = address;
        serverIcon = false;
        final byte[] png = data == null ? null : data.getIconBytes();
        if (png == null || !active()) return;
        try {
            final NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
            Minecraft.getInstance().getTextureManager().register(ICON, new DynamicTexture(image));
            serverIcon = true;
        } catch (final Exception e) {
            SlateMenu.LOGGER.debug("[Slate Menu] the server's picture could not be read: {}", e.toString());
        }
    }

    /**
     * The world is about to be left (the game still holds it): its land is taken down for the scene of leaving, and
     * kept in the world's folder for the next time it is opened.
     */
    public static void leaving() {
        left = null;
        leftName = null;
        leftAt = 0L;
        final Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        final IntegratedServer server = mc.getSingleplayerServer();
        leftAt = Util.getMillis();
        leftLocal = server != null;
        final ServerData data = mc.getCurrentServer();
        leftName = server != null ? worldName(mc) : data == null ? null : data.name;
        if (!active() || server == null) return;
        final Land taken = LandSnapshot.take(mc);
        left = taken;
        if (taken == null) return;
        try {
            final Path file = server.getWorldPath(LevelResource.ROOT).resolve(LandSnapshot.FILE);
            LandSnapshot.write(taken, file, mc.level.dimension().location().toString());
        } catch (final Exception e) {
            SlateMenu.LOGGER.warn("[Slate Menu] the land could not be kept: {}", e.toString());
        }
    }

    /** Every client tick: the journey is over once the game shows something that is not a loading screen. */
    public static void tick() {
        if (!running) return;
        final Screen s = Minecraft.getInstance().screen;
        final boolean loading = s instanceof LevelLoadingScreen || s instanceof ConnectScreen || s instanceof ReceivingLevelScreen
            || s instanceof GenericMessageScreen || s instanceof ProgressScreen;
        elsewhere = loading ? 0 : elsewhere + 1;
        if (elsewhere >= 2 || Util.getMillis() - drawnAt > OVER_MS) {
            running = false;
            end();
            left = null;
        }
    }

    private static void end() {
        if (land != null) { land.close(); land = null; }
        if (gateway != null) { gateway.close(); gateway = null; }
    }

    // ------------------------------------------------------------------ one frame

    private static String key(@Nullable final Component c) {
        return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
    }

    /** How far the way into a server is, from what the connection says it is doing. */
    private static int step(@Nullable final Component status) {
        return switch (key(status)) {
            case "connect.connecting", "connect.transferring" -> GatewayScene.LOOKING;
            case "connect.authorizing" -> GatewayScene.ANSWERED;
            case "connect.encrypting" -> GatewayScene.SECURED;
            case "connect.joining" -> GatewayScene.JOINING;
            default -> GatewayScene.SECURED;
        };
    }

    /** A scene has failed once: the loading screens are the Custom layout's for the rest of the session. */
    private static boolean broken;

    /**
     * One frame of a loading screen. False when the scenes cannot be drawn: the caller draws the screen its own way.
     * A loading screen that throws takes the game down with it, and these are the screens every player passes.
     *
     * @param progress 0..1, or negative while the length of the wait is unknown
     * @param chunks   the spawn area's chunk map (world loading only)
     * @param buttons  the screen's own widgets
     */
    public static boolean render(final Screen screen, final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick,
                                 final Component title, @Nullable final Component status, final float progress,
                                 @Nullable final StoringChunkProgressListener chunks, final List<? extends AbstractWidget> buttons) {
        if (broken) return false;
        try {
            frame(screen, g, mouseX, mouseY, partialTick, title, status, progress, chunks, buttons);
            return true;
        } catch (final Exception e) {
            broken = true;
            SlateMenu.LOGGER.error("[Slate Menu] the loading scene failed; the loading screens fall back to the Custom layout", e);
            try {
                end();
            } catch (final Exception ignored) {
                land = null;
                gateway = null;
            }
            return false;
        }
    }

    private static void frame(final Screen screen, final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick,
                              final Component title, @Nullable final Component status, final float progress,
                              @Nullable final StoringChunkProgressListener chunks, final List<? extends AbstractWidget> buttons) {
        final Minecraft mc = Minecraft.getInstance();
        final long now = Util.getMillis();
        final boolean fresh = !running || now - drawnAt > OVER_MS;
        running = true;
        elsewhere = 0;
        final boolean world = screen instanceof LevelLoadingScreen, connect = screen instanceof ConnectScreen;
        final boolean terrain = screen instanceof ReceivingLevelScreen;
        final boolean message = screen instanceof ProgressScreen || screen instanceof GenericMessageScreen;
        // The way out: the game still holds the world it has just been told to leave. A scene of leaving that goes
        // on stays one, also for the frame or two after the world has gone.
        final boolean out = message && (mc.level != null && leftAt != 0L && now - leftAt < 120_000L
            || !fresh && (land != null && land.leaving() || gateway != null && gateway.leaving()));

        final Kind kind;
        if (connect) kind = Kind.GATEWAY;
        else if (world) kind = Kind.LAND;
        else if (out) kind = leftLocal ? Kind.LAND : Kind.GATEWAY;
        else if (!fresh && gateway != null) kind = Kind.GATEWAY;
        else if (!fresh && land != null) kind = Kind.LAND;
        else if (mc.level != null) kind = Kind.PLAIN;                  // a wait inside a world
        else kind = Kind.LAND;                                         // a world is being read, before it runs

        if (fresh) {
            end();
            shown = 0f;
            wordsIn = 0f;
        }
        final float dt = fresh ? 0f : Math.min(0.25f, (now - shownAt) / 1000f);
        shownAt = now;
        drawnAt = now;
        wordsIn = Math.min(1f, wordsIn + dt * 2.6f);

        // How far the whole way is: what came before the spawn area is a tenth of it, what comes after it the rest.
        final float target;
        if (out) target = -1f;
        else if (kind == Kind.LAND) target = world ? 0.1f + 0.86f * Mth.clamp(progress, 0f, 1f) : terrain ? 1f : Math.max(shown, 0.06f);
        else target = progress;
        if (target < 0f) shown = 0f;
        else shown = fresh || Theme.current().motion() <= 0f ? target : shown + (target - shown) * Math.min(1f, dt * 7f);

        // What the game calls the step, unless it has no word for it.
        final String step = "slate_menu.loading.generic".equals(key(title)) ? null : title.getString();
        String kicker, heading = title.getString();
        @Nullable String under = status == null || status.getString().isBlank() ? null : status.getString();
        @Nullable String name = null;
        float fade = 1f;
        boolean icon = false;
        switch (kind) {
            case LAND -> {
                if (gateway != null) { gateway.close(); gateway = null; }
                if (land == null || land.leaving() != out) {
                    if (land != null) land.close();
                    // A world that is left without its land having been taken (the Nether) still gets its sky.
                    land = out ? new LandScene(left != null ? left : empty()) : new LandScene(null);
                }
                land.render(g, screen.width, screen.height, shown, partialTick);
                // Not every wait outside a world is the way into one: the game says the same way that it is deleting one.
                final boolean opening = world || terrain || mc.getSingleplayerServer() != null || OPENING.contains(key(title));
                kicker = Component.translatable(out ? "slate_menu.journey.leaving_world" : opening ? "slate_menu.journey.opening_world"
                    : "slate_menu.journey.waiting").getString();
                name = out ? leftName : land.name();
                if (out && step == null) under = Component.translatable("menu.savingLevel").getString();
            }
            case GATEWAY -> {
                if (land != null) { land.close(); land = null; }
                if (gateway == null || gateway.leaving() != out) {
                    if (gateway != null) gateway.close();
                    gateway = new GatewayScene(out);
                }
                gateway.render(g, screen.width, screen.height, terrain ? GatewayScene.THROUGH : step(status), partialTick);
                fade = 1f - Mth.clamp(gateway.through() * 1.6f, 0f, 1f);
                kicker = Component.translatable(out ? "slate_menu.journey.leaving_server" : "slate_menu.journey.joining_server").getString();
                name = out ? leftName : serverName;
                icon = name != null && !out;
                if (out && step == null) under = Component.translatable("slate_menu.journey.disconnecting").getString();
            }
            default -> {
                end();
                screen.renderBackground(g, mouseX, mouseY, partialTick);
                kicker = Component.translatable("slate_menu.journey.waiting").getString();
            }
        }
        if (name != null && !name.isBlank()) {
            // The name of the world is the heading; what the game calls the step goes under it.
            if (under == null) under = step;
            heading = name;
        } else if (heading.equalsIgnoreCase(kicker) && under != null) {
            // No name to show, and the step says what the line over it says already: the step's own words move up.
            heading = under;
            under = null;
        }
        words(screen, g, mouseX, mouseY, partialTick, kicker, heading, under, icon, target < 0f ? -1f : shown, world ? progress : -1f, chunks, buttons,
            fade * wordsIn * wordsIn * (3f - 2f * wordsIn));
        dev.fallingcloud.slate.core.client.DevHarness.loadingFrame(g, screen.getClass().getSimpleName());
    }

    /** A land with nothing on it: the ground and the sky of the scene, for a world whose land is not known. */
    private static Land empty() {
        final Land none = new Land(0, 0);
        none.dayTime = 12500;
        none.close();
        return none;
    }

    private static @Nullable String worldName(final Minecraft mc) {
        final IntegratedServer server = mc.getSingleplayerServer();
        try {
            return server == null ? null : server.getWorldData().getLevelName();
        } catch (final Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ the words

    private static void words(final Screen screen, final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick,
                              final String kicker, final String heading, @Nullable final String under, final boolean icon, final float progress,
                              final float percent, @Nullable final StoringChunkProgressListener chunks, final List<? extends AbstractWidget> buttons,
                              final float alpha) {
        final Theme t = Theme.current();
        final Palette p = t.palette();
        final boolean van = t.isVanilla();
        final Font font = Minecraft.getInstance().font;
        final int w = screen.width, h = screen.height;
        if (alpha <= 0.01f) return;
        final int margin = Mth.clamp(w / 22, 12, 40);
        // Shade along the foot of the picture, so the words read on any land under any sky.
        SlateDraw.vgradient(g, 0, h - Math.round(h * 0.5f), w, Math.round(h * 0.5f), 0x00000000, Colors.withAlpha(0xFF000000, Math.round(0xC8 * alpha)));

        final int accent = van ? 0xFFFFFFFF : p.accent();
        final int bar = h - Mth.clamp(h / 14, 14, 26);
        final float big = h >= 300 ? 2f : 1.5f;
        final int headingH = Math.round(9 * big);
        int y = bar - 12 - (under == null ? 0 : 12) - headingH - 14;
        final int slide = Math.round((1f - alpha) * 6f);

        // What is happening.
        final String label = kicker.toUpperCase(Locale.ROOT);
        if (!van) SlateDraw.rect(g, margin, y + slide + 3, 10, 2, Colors.scaleAlpha(accent, alpha));
        g.drawString(font, label, margin + (van ? 0 : 15), y + slide, Colors.scaleAlpha(van ? 0xFFE0E0E0 : accent, alpha), true);
        y += 14;

        // To which world: its name, large, with the server's picture before it.
        int x = margin;
        if (icon) {
            final int side = headingH + 2;
            final float[] was = com.mojang.blaze3d.systems.RenderSystem.getShaderColor().clone();
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.setColor(1f, 1f, 1f, alpha);
            g.blit(serverIcon ? ICON : NO_ICON, x, y + slide - 1, side, side, 0f, 0f, 64, 64, 64, 64);
            g.setColor(was[0], was[1], was[2], was[3]);
            x += side + 7;
        }
        final int room = Math.round((w - x - margin - (chunks != null ? 70 : buttons.isEmpty() ? 0 : 120)) / big);
        final FormattedCharSequence name = SlateDraw.truncate(Fonts.heading(heading), Math.max(40, room));
        g.pose().pushPose();
        g.pose().translate(x, y + slide, 0f);
        g.pose().scale(big, big, 1f);
        g.drawString(font, name, 0, 0, Colors.scaleAlpha(van ? 0xFFFFFFFF : p.text(), alpha), true);
        g.pose().popPose();
        y += headingH + 5;

        if (under != null) {
            g.drawString(font, SlateDraw.truncate(Component.literal(under), w - margin * 2 - 60), margin, y + slide,
                Colors.scaleAlpha(van ? 0xFFC8C8C8 : p.textMuted(), alpha), true);
        }

        // How far it is: a line across the foot, the number at its end.
        final int barW = w - margin * 2;
        SlateDraw.rect(g, margin, bar, barW, 2, Colors.scaleAlpha(0x50FFFFFF, alpha));
        if (progress < 0f) {
            final int seg = Math.max(30, barW / 4);
            final float phase = (Util.getMillis() % 1500L) / 1500f;
            final int sx = margin - seg + Math.round((barW + seg) * phase);
            final int from = Math.max(margin, sx), to = Math.min(margin + barW, sx + seg);
            if (to > from) SlateDraw.rect(g, from, bar, to - from, 2, Colors.scaleAlpha(accent, alpha));
        } else {
            final int fill = Math.round(barW * Mth.clamp(progress, 0f, 1f));
            SlateDraw.rect(g, margin, bar, fill, 2, Colors.scaleAlpha(accent, alpha));
            // A bright head on the line: where the loading is now.
            if (fill > 2 && fill < barW) SlateDraw.rect(g, margin + fill - 2, bar - 1, 3, 4, Colors.scaleAlpha(van ? 0xFFFFFFFF : p.accentHover(), alpha));
        }
        if (percent >= 0f) {
            final String number = Math.round(Mth.clamp(percent, 0f, 1f) * 100f) + "%";
            g.drawString(font, number, margin + barW - font.width(number), bar - 12, Colors.scaleAlpha(van ? 0xFFFFFFFF : p.text(), alpha), true);
        }

        // The chunk map of the spawn area, small, in the corner over the line.
        if (chunks != null && chunks.getDiameter() > 0) {
            final int d = chunks.getDiameter();
            final int cell = Mth.clamp(56 / d, 1, 4);
            final int side = d * cell;
            final int mx = margin + barW - side, my = bar - 18 - side;
            SlateDraw.rect(g, mx - 3, my - 3, side + 6, side + 6, Colors.withAlpha(0xFF000000, Math.round(0x70 * alpha)));
            if (!van) SlateDraw.outline(g, mx - 3, my - 3, side + 6, side + 6, Colors.scaleAlpha(0x40FFFFFF, alpha), 0);
            final Object2IntMap<ChunkStatus> colors = LevelLoadingScreenAccessor.slate$colors();
            final int a = Math.round(0xFF * alpha);
            g.drawManaged(() -> {
                for (int r = 0; r < d; r++) {
                    for (int s = 0; s < d; s++) {
                        final ChunkStatus status = chunks.getStatus(r, s);
                        if (status == null) continue;
                        g.fill(mx + r * cell, my + s * cell, mx + r * cell + cell, my + s * cell + cell, colors.getInt(status) & 0xFFFFFF | a << 24);
                    }
                }
            });
        }

        // The screen's own buttons, in the corner over the line.
        int by = bar - 14 - 20;
        for (final AbstractWidget b : buttons) {
            b.setWidth(Math.min(120, Math.max(80, w / 5)));
            b.setX(margin + barW - b.getWidth());
            b.setY(by);
            b.setAlpha(alpha);
            b.render(g, mouseX, mouseY, partialTick);
            by -= 24;
        }
    }
}
