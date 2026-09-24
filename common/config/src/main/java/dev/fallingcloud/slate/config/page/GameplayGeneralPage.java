package dev.fallingcloud.slate.config.page;

import dev.fallingcloud.slate.config.option.Binding;
import dev.fallingcloud.slate.config.option.Choice;
import dev.fallingcloud.slate.config.option.OptionType;
import dev.fallingcloud.slate.config.option.OptionValues;
import dev.fallingcloud.slate.config.resolver.VanillaOptions;
import dev.fallingcloud.slate.config.ui.ApplyQueue;
import dev.fallingcloud.slate.config.ui.OptionPageBase;
import dev.fallingcloud.slate.config.ui.Section;
import dev.fallingcloud.slate.core.gfx.Icon;
import dev.fallingcloud.slate.core.widget.SlateModal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ServerboundLockDifficultyPacket;
import net.minecraft.world.Difficulty;

/**
 * Gameplay › General: the world's difficulty with vanilla's lock (only while a world is open; changeable in
 * singleplayer until locked, never in hardcore), then the player options that change how the game plays.
 */
public final class GameplayGeneralPage extends OptionPageBase {

    private static final String TAB = "general";

    public GameplayGeneralPage() {
        super("general", Component.translatable("slate_config.page.general"), Icon.SLIDERS);
    }

    @Override
    protected boolean pills(final String tabKey) { return false; }

    private static boolean canChangeDifficulty() {
        final Minecraft mc = Minecraft.getInstance();
        final ClientLevel level = mc.level;
        return level != null && mc.hasSingleplayerServer() && !level.getLevelData().isDifficultyLocked() && !level.getLevelData().isHardcore();
    }

    @Override
    protected List<Section> sections() {
        final Minecraft mc = Minecraft.getInstance();
        final List<Section> out = new ArrayList<>();
        final ClientLevel level = mc.level;
        if (level != null) {
            final Section world = Section.of("world", Component.translatable("slate_config.gameplay.world")).fixed().tab(TAB, title());
            final boolean locked = level.getLevelData().isDifficultyLocked();
            final boolean hardcore = level.getLevelData().isHardcore();
            final boolean single = mc.hasSingleplayerServer();
            final List<Choice> choices = new ArrayList<>();
            for (final Difficulty d : Difficulty.values()) choices.add(new Choice(d.name(), d.getDisplayName()));
            final String why = hardcore ? "slate_config.gameplay.difficulty.hardcore" : locked ? "slate_config.gameplay.difficulty.locked"
                : !single ? "slate_config.gameplay.difficulty.server" : "slate_config.gameplay.difficulty.tip";
            world.add(Binding.of("gameplay:difficulty", OptionType.CHOICE, Component.translatable("options.difficulty"))
                .tooltip(Component.translatable(why))
                .choices(choices)
                .getter(() -> { final ClientLevel l = Minecraft.getInstance().level; return (l == null ? Difficulty.NORMAL : l.getDifficulty()).name(); })
                .setter(v -> {
                    if (!canChangeDifficulty() || mc.getConnection() == null) return;
                    final String name = OptionValues.asString(v);
                    for (final Difficulty d : Difficulty.values()) {
                        if (d.name().equals(name)) mc.getConnection().send(new ServerboundChangeDifficultyPacket(d));
                    }
                    ApplyQueue.later("gameplay:difficulty", 250, this::rebuild);       // the server echoes the change back
                })
                .enabledIf(GameplayGeneralPage::canChangeDifficulty)
                .searchWords("difficulty peaceful easy normal hard world"));
            if (single && !hardcore && !locked) {
                world.add(Binding.of("gameplay:lock_difficulty", OptionType.ACTION, Component.translatable("slate_config.gameplay.lock"))
                    .tooltip(Component.translatable("slate_config.gameplay.lock.tip"))
                    .actionIcon(Icon.LOCK)
                    .action(Component.translatable("slate_config.gameplay.lock.button"), () -> {
                        final ClientLevel l = Minecraft.getInstance().level;
                        if (l == null) return;
                        SlateModal.confirmDanger(Component.translatable("difficulty.lock.title"),
                            Component.translatable("difficulty.lock.question", l.getDifficulty().getDisplayName()),
                            Component.translatable("slate_config.gameplay.lock.button"), () -> {
                                if (mc.getConnection() != null) mc.getConnection().send(new ServerboundLockDifficultyPacket(true));
                                ApplyQueue.later("gameplay:difficulty", 300, this::rebuild);
                            });
                    })
                    .searchWords("difficulty lock world"));
            } else if (locked && !hardcore) {
                world.add(Binding.of("gameplay:difficulty_locked", OptionType.INFO, Component.translatable("slate_config.gameplay.locked"))
                    .getter(() -> Component.translatable("slate_config.gameplay.locked.value").getString())
                    .searchWords("difficulty lock world"));
            }
            out.add(world);
        }
        out.add(Section.of("player", Component.translatable("slate_config.gameplay.player"),
            VanillaOptions.all("mainHand", "attackIndicator", "autoJump", "toggleSprint", "toggleCrouch", "operatorItemsTab")).fixed().tab(TAB, title()));
        return out;
    }
}
