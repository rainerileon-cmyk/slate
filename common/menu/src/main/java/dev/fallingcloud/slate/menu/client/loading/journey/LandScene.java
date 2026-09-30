package dev.fallingcloud.slate.menu.client.loading.journey;

import dev.fallingcloud.slate.core.stage.Stage;
import dev.fallingcloud.slate.core.stage.StageFinish;
import dev.fallingcloud.slate.core.stage.anim.IdleMotion;
import dev.fallingcloud.slate.core.stage.node.FxNode;
import dev.fallingcloud.slate.core.stage.node.MotesNode;
import dev.fallingcloud.slate.core.theme.Colors;
import dev.fallingcloud.slate.core.theme.Theme;
import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.menu.client.overhaul.create.LandSampler;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * The world that is being opened, seen from the air while it loads: its land comes up out of the ground around the
 * place the player will stand on, ring by ring, as far as the loading has got, under the sky of the hour it is in
 * that world, with a shaft of light on the spot itself. The land is the one the player left ({@link LandSnapshot})
 * where there is one, and what the world's own generator makes of the rest ({@link Land#ask}).
 *
 * <p>The same scene shows a world being left, the other way round: the land as it was just seen goes down again,
 * from the rim inwards.</p>
 */
final class LandScene {

    /** Units of the scene a cell of the land is wide. */
    private static final float CELL = 0.1f;
    /** How long a land takes to go down when a world is left. */
    private static final float LEAVE_MS = 1700f;

    /** The light of an hour of the day. */
    private record Hour(int skyTop, int skyBottom, int fog, float fogFrom, float fogTo, float[] key, float[] ambient, float[] fill, float[] rim,
                        float[] sun, int ground, int beam, int motes, float clouds, boolean stars, float warmth) {

        /** The light between two hours: {@code t} of the way from {@code a} to {@code b}. */
        static Hour between(final Hour a, final Hour b, final float t) {
            if (t <= 0f) return a;
            if (t >= 1f) return b;
            return new Hour(Colors.lerp(a.skyTop, b.skyTop, t), Colors.lerp(a.skyBottom, b.skyBottom, t), Colors.lerp(a.fog, b.fog, t),
                Mth.lerp(t, a.fogFrom, b.fogFrom), Mth.lerp(t, a.fogTo, b.fogTo), mix(a.key, b.key, t), mix(a.ambient, b.ambient, t), mix(a.fill, b.fill, t),
                mix(a.rim, b.rim, t), mix(a.sun, b.sun, t), Colors.lerp(a.ground, b.ground, t), Colors.lerp(a.beam, b.beam, t), Colors.lerp(a.motes, b.motes, t),
                Mth.lerp(t, a.clouds, b.clouds), t < 0.5f ? a.stars : b.stars, Mth.lerp(t, a.warmth, b.warmth));
        }

        private static float[] mix(final float[] a, final float[] b, final float t) {
            final float[] out = new float[a.length];
            for (int i = 0; i < out.length; i++) out[i] = Mth.lerp(t, a[i], b[i]);
            return out;
        }

        static Hour of(final int dayTime) {
            final int t = Math.floorMod(dayTime, 24000);
            if (t >= 22300 || t < 1200) return DAWN;
            if (t < 10800) return DAY;
            if (t < 13600) return DUSK;
            return NIGHT;
        }

        static final Hour DAWN = new Hour(0xFF2B3C6B, 0xFFF3B9A0, 0xFFEEB49F, 13f, 25f, new float[] {0.80f, 0.60f, 0.52f}, new float[] {0.40f, 0.39f, 0.48f},
            new float[] {0.12f, 0.15f, 0.26f}, new float[] {1f, 0.78f, 0.70f, 0.16f}, new float[] {0.8f, 0.5f, 0.3f}, 0xFF3B3347, 0x58FFE3C4, 0x50FFD9C0, 0.85f, false, 0.014f);
        static final Hour DAY = new Hour(0xFF2F6FD0, 0xFFBFE0F7, 0xFFC4E1F5, 14f, 27f, new float[] {0.80f, 0.77f, 0.68f}, new float[] {0.47f, 0.49f, 0.54f},
            new float[] {0.14f, 0.18f, 0.26f}, new float[] {1f, 0.97f, 0.9f, 0.12f}, new float[] {0.5f, 0.85f, 0.35f}, 0xFF3A4652, 0x48FFFFFF, 0x38FFFFFF, 0.95f, false, 0.004f);
        static final Hour DUSK = new Hour(0xFF182747, 0xFFE9B384, 0xFFDFAA84, 13f, 25f, new float[] {0.78f, 0.66f, 0.50f}, new float[] {0.40f, 0.40f, 0.47f},
            new float[] {0.12f, 0.16f, 0.26f}, new float[] {1f, 0.85f, 0.65f, 0.16f}, new float[] {0.75f, 0.6f, 0.35f}, 0xFF33303F, 0x58FFE2B0, 0x50FFE2B0, 0.85f, false, 0.012f);
        static final Hour NIGHT = new Hour(0xFF04060E, 0xFF1B2745, 0xFF151E38, 11f, 23f, new float[] {0.34f, 0.40f, 0.62f}, new float[] {0.20f, 0.23f, 0.34f},
            new float[] {0.06f, 0.08f, 0.16f}, new float[] {0.6f, 0.7f, 1f, 0.2f}, new float[] {-0.4f, 0.8f, 0.45f}, 0xFF0E1222, 0x70CFE0FF, 0x40BFD4FF, 0.35f, true, 0f);
    }

    /** How long the light takes to turn into that of the world's hour, once that is known. */
    private static final float TURN_MS = 700f;

    private final Stage stage = new Stage();
    private final boolean leaving;
    private final FxNode ground, clouds, stars, beam, pool;
    private final MotesNode motes;
    private final StageFinish finish = StageFinish.cinematic().vignette(0.3f);
    @Nullable private Land land;
    @Nullable private LandNode node;
    @Nullable private CompletableFuture<LandSnapshot.Kept> kept;
    @Nullable private String dimension;
    @Nullable private String name;
    private Hour was, hour;
    private boolean asked, nothing;
    /**
     * A world that is new has no place for the player until it has found where life begins, which takes it a while:
     * meanwhile the land shown is the one around the world's origin, where that place nearly always lies.
     */
    private boolean guessed;
    /** The cell the player will stand on, once that is known. */
    private int spotX = Land.WIDTH / 2, spotZ = Land.DEPTH / 2;
    private boolean spotKnown;
    /** How near the rim of the land the player's place may lie before the land is laid out again around it, in cells. */
    private static final int RIM = 14;
    private float reach, since, spot, turnedAt, leftAt = -1f;
    private int tries;

    /** @param left the land of a world that is being left, or null for one that is being opened */
    LandScene(@Nullable final Land left) {
        this.leaving = left != null;
        final Minecraft mc = Minecraft.getInstance();
        hour = was = left != null ? Hour.of(left.dayTime) : Hour.DUSK;
        stage.soft(true);
        stage.finish(finish);
        stage.resolutionScale(mc.getWindow().getWidth() <= 1600 ? 2f : 1.5f);
        stage.camera().clip(0.1f, 90f);
        stage.camera().idle(IdleMotion.sway(24000f, 9f, 0.5f, 0.05f));
        // The ground everything comes up out of: dark, and lost in the haze before it ends.
        ground = (FxNode) stage.add(FxNode.glow(30f, hour.ground).fade(1.7f)).at(0f, 0.005f, -1.5f);
        clouds = (FxNode) stage.add(FxNode.clouds(12, 22f, 15f, 6.6f, 0.5f, 23L, 0.1f));
        motes = (MotesNode) stage.add(new MotesNode(34, 13f, 3.2f, 8f, 0.035f, hour.motes, 5L)).at(0f, 3.6f, 3f);
        stars = (FxNode) stage.add(FxNode.sparkles(70, 44f, 9f, 2f, 0.09f, 0xFFDDE8FF, 11L)).at(0f, 15f, -24f);
        pool = (FxNode) stage.add(FxNode.glow(0.9f, hour.beam)).at(0f, 0.02f, 0f);
        beam = (FxNode) stage.add(FxNode.cone(0.035f, 0.17f, 8f, hour.beam)).at(0f, 0f, 0f);
        pool.opacity(0f);
        beam.opacity(0f);
        light(hour);
        if (left != null) show(left);
        spotKnown = left != null;
    }

    Stage stage() { return stage; }

    /** The land shown, once there is one. */
    @Nullable Land land() { return land; }

    /** The world's name, once it is known. */
    @Nullable String name() { return name; }

    boolean leaving() { return leaving; }

    // ------------------------------------------------------------------ the light

    /** The world's hour is known: the light turns into it. */
    private void turn(final Hour to) {
        if (to == hour) return;
        was = Hour.between(was, hour, turned());
        hour = to;
        turnedAt = stage.timeMs();
    }

    private float turned() {
        return was == hour ? 1f : Mth.clamp((stage.timeMs() - turnedAt) / TURN_MS, 0f, 1f);
    }

    private void light(final Hour h) {
        stage.gradient(h.skyTop, h.skyBottom);
        stage.fog(true, h.fogFrom, h.fogTo, h.fog);
        finish.warmth(h.warmth);
        stage.lighting().key(9f, 9f, 5f).keyColor(h.key[0], h.key[1], h.key[2]).ambientColor(h.ambient[0], h.ambient[1], h.ambient[2])
            .fill(-1f, 0.3f, 0.2f, h.fill[0], h.fill[1], h.fill[2]).rim(h.rim[0], h.rim[1], h.rim[2], h.rim[3]).sun(h.sun[0], h.sun[1], h.sun[2]);
        ground.color(h.ground);
        clouds.alpha(h.clouds);
        motes.color(h.motes);
        stars.opacity(h.stars ? 1f : 0f);
        beam.color(h.beam);
        pool.color(h.beam);
    }

    private void show(final Land shown) {
        land = shown;
        node = stage.add(new LandNode(shown));
        node.scale(CELL);
        node.at(0f, 0f, 0f);
        if (leaving) {
            node.reach(1f);
            reach = 1f;
        }
    }

    // ------------------------------------------------------------------ finding the land

    /** The place the player was last, if the world remembers one in the overworld; null otherwise. */
    private static @Nullable BlockPos lastPlace(final IntegratedServer server) {
        final CompoundTag player = server.getWorldData().getLoadedPlayerTag();
        if (player == null || !player.contains("Pos", Tag.TAG_LIST)) return null;
        if (player.contains("Dimension", Tag.TAG_STRING) && !Level.OVERWORLD.location().toString().equals(player.getString("Dimension"))) return null;
        final ListTag pos = player.getList("Pos", Tag.TAG_DOUBLE);
        return pos.size() < 3 ? null : BlockPos.containing(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
    }

    private static @Nullable ServerLevel level(final IntegratedServer server, @Nullable final String dimension) {
        try {
            if (dimension != null) {
                final ResourceLocation id = ResourceLocation.tryParse(dimension);
                if (id != null) {
                    final ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
                    if (level != null) return level;
                }
            }
            return server.getLevel(Level.OVERWORLD);
        } catch (final Exception e) {
            return null;
        }
    }

    /** Looks for the world that is being opened, and what is known of its land. Cheap: asked every frame until it is settled. */
    private void find() {
        if (leaving || nothing || (land != null && asked && !guessed)) return;
        final IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return;
        try {
            if (name == null) name = server.getWorldData().getLevelName();
            if (kept == null) {
                final Path file = server.getWorldPath(LevelResource.ROOT).resolve(LandSnapshot.FILE);
                kept = LandSnapshot.read(file);
            }
            if (!kept.isDone()) return;
            if (land == null) {
                final LandSnapshot.Kept k = kept.getNow(null);
                if (k != null) {
                    dimension = k.dimension();
                    turn(Hour.of(k.land().dayTime));
                    show(k.land());
                } else {
                    final BlockPos last = lastPlace(server);
                    BlockPos at = last;
                    if (at == null) {
                        final ServerLevel overworld = server.getLevel(Level.OVERWORLD);
                        if (overworld == null) return;
                        if (server.getWorldData().overworldData().isInitialized()) at = overworld.getSharedSpawnPos();
                        else {
                            at = BlockPos.ZERO;
                            guessed = true;
                        }
                    }
                    final Land fresh = new Land(at.getX(), at.getZ());
                    fresh.dayTime = (int) Math.floorMod(server.getWorldData().overworldData().getDayTime(), 24000L);
                    turn(Hour.of(fresh.dayTime));
                    show(fresh);
                }
                spotKnown = !guessed;
            }
            if (guessed && server.getWorldData().overworldData().isInitialized()) {
                // The world has found its spawn: on the land shown, if it lies well inside it; else the land is laid
                // out again around it.
                guessed = false;
                final ServerLevel overworld = server.getLevel(Level.OVERWORLD);
                final BlockPos spawn = overworld == null ? BlockPos.ZERO : overworld.getSharedSpawnPos();
                final int x = Land.WIDTH / 2 + Math.floorDiv(spawn.getX() - land.centerX, Land.STEP);
                final int z = Land.DEPTH / 2 + Math.floorDiv(spawn.getZ() - land.centerZ, Land.STEP);
                if (x >= RIM && z >= RIM && x < Land.WIDTH - RIM && z < Land.DEPTH - RIM) {
                    spotX = x;
                    spotZ = z;
                } else {
                    land.drop();
                    if (node != null) stage.remove(node);
                    final Land fresh = new Land(spawn.getX(), spawn.getZ());
                    fresh.dayTime = land.dayTime;
                    show(fresh);
                    reach = 0f;
                    asked = false;
                }
                spotKnown = true;
            }
            if (!asked) {
                final ServerLevel level = level(server, dimension);
                if (level == null) return;
                asked = true;
                if (level.dimensionType().hasCeiling()) {
                    land.close();
                    return;
                }
                land.ask(LandSampler.of(level.getChunkSource().getGenerator(), level.getChunkSource().randomState(), level, level.getSeed()));
            }
        } catch (final Exception e) {
            // A server that is only half there: tried again a few frames later, then given up.
            if (++tries > 600) {
                nothing = true;
                if (land != null) land.close();
                SlateMenu.LOGGER.warn("[Slate Menu] the land of the world could not be found: {}", e.toString());
            }
        }
    }

    // ------------------------------------------------------------------ living

    /**
     * One frame.
     *
     * @param progress how far the loading is, 0..1 (a world being left: ignored)
     */
    void render(final GuiGraphics g, final int width, final int height, final float progress, final float partialTick) {
        find();
        final float motion = Theme.current().motion();
        final float now = stage.timeMs();
        final float dt = Math.min(0.25f, Math.max(0f, now - since) / 1000f);
        since = now;
        if (was != hour) {
            final float t = turned();
            light(Hour.between(was, hour, t * t * (3f - 2f * t)));
            if (t >= 1f) was = hour;
        }
        if (node != null) {
            if (leaving) {
                if (leftAt < 0f) {
                    leftAt = now;
                    node.settle(now);
                }
                // A breath to see the land once more, then it goes.
                node.leave(Mth.clamp((now - leftAt - 380f) / LEAVE_MS, 0f, 1f));
            } else {
                // The land follows the loading, but never in a jump: a world that is there at once still comes up ring by
                // ring. And it never waits long: a loading that says nothing for a while (a new world looking for its
                // spawn, an old one read in one go) still sees the land grow, slowly.
                final float to = Mth.clamp(progress, 0f, 1f);
                reach = motion <= 0f ? 1f : Math.min(1f, Math.max(reach + dt * 0.3f, Math.min(to, reach + dt * 1.1f)));
                node.reach(reach);
                if (motion <= 0f) node.settle(now);
            }
        }
        // The light on the spot: there once the middle of the land is up.
        final float wanted = node != null && node.up() > 0 && !leaving && spotKnown ? 1f : 0f;
        spot += (wanted - spot) * Math.min(1f, dt * 2.4f);
        if (land != null) {
            final Land.Cell there = land.at(spotX, spotZ);
            final float x = (spotX - Land.WIDTH / 2 + 0.5f) * CELL, z = (spotZ - Land.DEPTH / 2 + 0.5f) * CELL;
            final float y = (there == null ? land.sea : there.level() + (there.tree() ? 2 : 0)) * CELL;
            beam.at(x, y, z);
            pool.at(x, y + 0.02f, z);
        }
        beam.opacity(spot * ((hour.beam >>> 24) / 255f));
        pool.opacity(spot * ((hour.beam >>> 24) / 255f));
        aim(width / (float) Math.max(1, height), leaving ? 0f : reach, motion);
        stage.render(g, 0, 0, width, height, -1, -1, partialTick);
    }

    /** The camera comes in over the land as it rises: from far and high to near and low. */
    private void aim(final float aspect, final float in, final float motion) {
        final float across = (float) Math.toRadians(54.0);
        final float fov = (float) Math.toDegrees(2.0 * Math.atan(Math.tan(across / 2.0) / Math.max(0.6f, aspect)));
        final float t = motion <= 0f ? 1f : in * in * (3f - 2f * in);
        final float sea = (land == null ? 9f : land.sea) * CELL;
        stage.camera().at(0f, Mth.lerp(t, 9.4f, 8.0f), Mth.lerp(t, 13.6f, 11.8f)).lookAt(0f, sea + Mth.lerp(t, 0.1f, 0.4f), -1.1f)
            .fov(Mth.clamp(fov, 8f, 60f));
    }

    void close() {
        if (land != null && !leaving) land.drop();
        if (dev.fallingcloud.slate.core.client.DevHarness.active()) {
            SlateMenu.LOGGER.info("[Slate Menu] land scene: {} frames, {} ms a frame, {}x{}", stage.frames(), "%.2f".formatted(stage.frameMs()),
                stage.targetWidth(), stage.targetHeight());
        }
        stage.close();
    }
}
