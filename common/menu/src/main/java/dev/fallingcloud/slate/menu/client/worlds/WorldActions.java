package dev.fallingcloud.slate.menu.client.worlds;

import com.mojang.datafixers.util.Pair;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.widget.SlateToasts;
import dev.fallingcloud.slate.menu.SlateMenu;
import dev.fallingcloud.slate.menu.client.MenuIo;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.FileUtil;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.NoticeWithLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.world.level.validation.ContentValidationException;
import org.jetbrains.annotations.Nullable;

/**
 * Every world operation the Slate world screen offers, routed through the same vanilla code paths the
 * vanilla list entry uses ({@link net.minecraft.client.gui.screens.worldselection.WorldOpenFlows},
 * {@link EditWorldScreen}, {@link CreateWorldScreen}), so backups, symlink checks and version prompts
 * behave exactly as in vanilla.
 */
public final class WorldActions {

    private static final Component READING = Component.translatable("selectWorld.data_read");

    /** Lists every world like the vanilla screen: candidates on the pool, summaries through vanilla's loader. */
    public static CompletableFuture<List<LevelSummary>> loadAll() {
        final LevelStorageSource source = Minecraft.getInstance().getLevelSource();
        return CompletableFuture.supplyAsync(source::findLevelCandidates, MenuIo.POOL).thenCompose(source::loadLevelSummaries);
    }

    public static void play(final LevelSummary summary, @Nullable final Screen parent) {
        final Minecraft mc = Minecraft.getInstance();
        if (!summary.primaryActionActive()) return;
        if (summary instanceof LevelSummary.SymlinkLevelSummary) {
            mc.setScreen(NoticeWithLinkScreen.createWorldSymlinkWarningScreen(() -> mc.setScreen(parent)));
            return;
        }
        mc.forceSetScreen(new GenericMessageScreen(READING));
        mc.createWorldOpenFlows().openWorld(summary.getLevelId(), () -> mc.setScreen(parent));
    }

    /** Opens a world by folder name (continue card / play_last action) without a summary in hand. */
    public static void play(final String levelId, @Nullable final Screen parent) {
        final Minecraft mc = Minecraft.getInstance();
        if (levelId == null || levelId.isBlank() || !mc.getLevelSource().levelExists(levelId)) {
            SlateToasts.show(Component.translatable("slate_menu.worlds.missing"), Component.literal(levelId == null ? "" : levelId), Icon.WARNING);
            return;
        }
        mc.forceSetScreen(new GenericMessageScreen(READING));
        mc.createWorldOpenFlows().openWorld(levelId, () -> mc.setScreen(parent));
    }

    public static void edit(final LevelSummary summary, @Nullable final Screen parent, final Runnable onChanged) {
        final Minecraft mc = Minecraft.getInstance();
        final String id = summary.getLevelId();
        mc.forceSetScreen(new GenericMessageScreen(READING));
        try {
            final LevelStorageSource.LevelStorageAccess access = mc.getLevelSource().validateAndCreateAccess(id);
            mc.setScreen(EditWorldScreen.create(mc, access, changed -> {
                access.safeClose();
                if (changed) onChanged.run();
                mc.setScreen(parent);
            }));
        } catch (final IOException e) {
            SystemToast.onWorldAccessFailure(mc, id);
            SlateMenu.LOGGER.error("[Slate Menu] cannot open world {} for editing", id, e);
            mc.setScreen(parent);
        } catch (final ContentValidationException e) {
            SlateMenu.LOGGER.warn("[Slate Menu] {}", e.getMessage());
            mc.setScreen(NoticeWithLinkScreen.createWorldSymlinkWarningScreen(() -> mc.setScreen(parent)));
        }
    }

    /** Vanilla's backup (zip under {@code backups/}) with vanilla's toast. */
    public static boolean backup(final LevelSummary summary) {
        final Minecraft mc = Minecraft.getInstance();
        final String id = summary.getLevelId();
        try (LevelStorageSource.LevelStorageAccess access = mc.getLevelSource().createAccess(id)) {
            return EditWorldScreen.makeBackupAndShowToast(access);
        } catch (final IOException e) {
            SystemToast.onWorldAccessFailure(mc, id);
            SlateMenu.LOGGER.error("[Slate Menu] backup of {} failed", id, e);
            return false;
        }
    }

    /** Deletes without asking; the screen shows the confirm modal first. */
    public static boolean delete(final LevelSummary summary) {
        final Minecraft mc = Minecraft.getInstance();
        final String id = summary.getLevelId();
        try (LevelStorageSource.LevelStorageAccess access = mc.getLevelSource().createAccess(id)) {
            access.deleteLevel();
            WorldFavorites.forget(id);
            return true;
        } catch (final IOException e) {
            SystemToast.onWorldDeleteFailure(mc, id);
            SlateMenu.LOGGER.error("[Slate Menu] delete of {} failed", id, e);
            return false;
        }
    }

    /**
     * Copies the world folder under a free name (skipping the session lock) on the pool, then sets the
     * new level name through vanilla's rename so the copy shows up with its own name.
     */
    public static void duplicate(final LevelSummary summary, final String newName, final Runnable onDone) {
        final Minecraft mc = Minecraft.getInstance();
        final LevelStorageSource source = mc.getLevelSource();
        final String name = newName == null || newName.isBlank() ? summary.getLevelName() + " copy" : newName.trim();
        final Path from = source.getLevelPath(summary.getLevelId());
        final Path base = source.getBaseDir();
        final String folder;
        try {
            folder = FileUtil.findAvailableName(base, FileUtil.sanitizeName(name), "");
        } catch (final IOException e) {
            SlateToasts.show(Component.translatable("slate_menu.worlds.duplicate_failed"), Component.literal(e.getMessage() == null ? "" : e.getMessage()), Icon.ERROR);
            return;
        }
        final Path to = base.resolve(folder);
        CompletableFuture.runAsync(() -> {
            try { copyTree(from, to); } catch (final IOException e) { throw new UncheckedIOException(e); }
        }, MenuIo.POOL).whenCompleteAsync((v, err) -> {
            if (err != null) {
                SlateMenu.LOGGER.error("[Slate Menu] duplicate of {} failed", summary.getLevelId(), err);
                SlateToasts.show(Component.translatable("slate_menu.worlds.duplicate_failed"), Component.literal(String.valueOf(err.getMessage())), Icon.ERROR);
            } else {
                try (LevelStorageSource.LevelStorageAccess access = source.createAccess(folder)) {
                    access.renameLevel(name);
                } catch (final Exception e) {
                    SlateMenu.LOGGER.warn("[Slate Menu] copied {} but could not rename it: {}", folder, e.toString());
                }
                SlateToasts.show(Component.translatable("slate_menu.worlds.duplicated"), Component.literal(name), Icon.DUPLICATE);
            }
            onDone.run();
        }, mc);
    }

    private static void copyTree(final Path from, final Path to) throws IOException {
        try (Stream<Path> s = Files.walk(from)) {
            for (final Path p : (Iterable<Path>) s::iterator) {
                final Path rel = from.relativize(p);
                if (rel.getFileName() != null && rel.getFileName().toString().equals("session.lock")) continue;
                final Path target = to.resolve(rel.toString());
                if (Files.isDirectory(p)) Files.createDirectories(target);
                else {
                    Files.createDirectories(target.getParent());
                    Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    public static void openFolder(final LevelSummary summary) {
        Util.getPlatform().openPath(Minecraft.getInstance().getLevelSource().getLevelPath(summary.getLevelId()));
    }

    public static void openSavesFolder() {
        Util.getPlatform().openPath(Minecraft.getInstance().getLevelSource().getBaseDir());
    }

    /** Vanilla's "Re-create": same settings/seed into the create-world screen. */
    public static void recreate(final LevelSummary summary, @Nullable final Screen parent) {
        final Minecraft mc = Minecraft.getInstance();
        mc.forceSetScreen(new GenericMessageScreen(READING));
        try (LevelStorageSource.LevelStorageAccess access = mc.getLevelSource().validateAndCreateAccess(summary.getLevelId())) {
            final Pair<LevelSettings, WorldCreationContext> pair = mc.createWorldOpenFlows().recreateWorldData(access);
            final LevelSettings settings = pair.getFirst();
            final WorldCreationContext ctx = pair.getSecond();
            final Path temp = CreateWorldScreen.createTempDataPackDirFromExistingWorld(access.getLevelPath(LevelResource.DATAPACK_DIR), mc);
            if (ctx.options().isOldCustomizedWorld()) {
                mc.setScreen(new ConfirmScreen(ok -> mc.setScreen(ok ? CreateWorldScreen.createFromExisting(mc, parent, settings, ctx, temp) : parent),
                    Component.translatable("selectWorld.recreate.customized.title"), Component.translatable("selectWorld.recreate.customized.text"),
                    CommonComponents.GUI_PROCEED, CommonComponents.GUI_CANCEL));
            } else {
                mc.setScreen(CreateWorldScreen.createFromExisting(mc, parent, settings, ctx, temp));
            }
        } catch (final ContentValidationException e) {
            SlateMenu.LOGGER.warn("[Slate Menu] {}", e.getMessage());
            mc.setScreen(NoticeWithLinkScreen.createWorldSymlinkWarningScreen(() -> mc.setScreen(parent)));
        } catch (final Exception e) {
            SlateMenu.LOGGER.error("[Slate Menu] cannot recreate world {}", summary.getLevelId(), e);
            mc.setScreen(new AlertScreen(() -> mc.setScreen(parent), Component.translatable("selectWorld.recreate.error.title"), Component.translatable("selectWorld.recreate.error.text")));
        }
    }

    public static void createNew(@Nullable final Screen parent) {
        CreateWorldScreen.openFresh(Minecraft.getInstance(), parent);
    }

    /** Folder size in bytes, computed on the pool. */
    public static CompletableFuture<Long> sizeOf(final String levelId) {
        final Path dir = Minecraft.getInstance().getLevelSource().getLevelPath(levelId);
        return CompletableFuture.supplyAsync(() -> {
            try (Stream<Path> s = Files.walk(dir)) {
                return s.filter(Files::isRegularFile).mapToLong(p -> {
                    try { return Files.size(p); } catch (final IOException e) { return 0L; }
                }).sum();
            } catch (final IOException e) {
                return -1L;
            }
        }, MenuIo.POOL);
    }

    private WorldActions() {}
}
