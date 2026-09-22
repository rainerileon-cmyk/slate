package dev.fallingcloud.slate.menu.client.worlds;

import dev.fallingcloud.slate.core.gfx.Textures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.level.storage.LevelSummary;
import org.jetbrains.annotations.Nullable;

/** One world on the Slate world screen: the vanilla summary plus Slate's favourite/tags and lazy extras. */
public final class WorldEntry {

    public final LevelSummary summary;
    public boolean favorite;
    public List<String> tags;
    /** Bytes on disk; -2 = not requested yet, -1 = failed/unknown, -3 = computing. */
    public long size = -2;
    @Nullable public Textures.Loaded icon;
    private boolean iconRequested;

    public WorldEntry(final LevelSummary summary) {
        this.summary = summary;
        this.favorite = WorldFavorites.isFavorite(summary.getLevelId());
        this.tags = WorldFavorites.tags(summary.getLevelId());
    }

    public String id() { return summary.getLevelId(); }

    public String name() {
        final String n = summary.getLevelName();
        return n == null || n.isBlank() ? summary.getLevelId() : n;
    }

    /** Text the search box matches against. */
    public boolean matches(final String query) {
        if (query == null || query.isBlank()) return true;
        final String q = query.toLowerCase(Locale.ROOT).trim();
        if (name().toLowerCase(Locale.ROOT).contains(q) || id().toLowerCase(Locale.ROOT).contains(q)) return true;
        for (final String t : tags) if (t.toLowerCase(Locale.ROOT).contains(q)) return true;
        return false;
    }

    public void requestSize() {
        if (size != -2) return;
        size = -3;
        WorldActions.sizeOf(id()).whenCompleteAsync((v, err) -> size = err != null || v == null ? -1 : v, net.minecraft.client.Minecraft.getInstance());
    }

    /** Loads the world icon once; safe to call every frame. */
    public void requestIcon() {
        if (iconRequested) return;
        iconRequested = true;
        final Path p = summary.getIcon();
        if (p != null && Files.isRegularFile(p) && !Files.isSymbolicLink(p)) Textures.load(p, l -> icon = l);
    }

    public void reloadMeta() {
        favorite = WorldFavorites.isFavorite(id());
        tags = WorldFavorites.tags(id());
    }
}
