package dev.fallingcloud.slate.building.client.mode;

import dev.fallingcloud.slate.building.SlateBuilding;
import dev.fallingcloud.slate.building.config.HudSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * The small, distinct sounds of the selection flow, played as UI sounds (not positional, never a toast). Soft on
 * purpose: they confirm a click without competing with block sounds. Muted by {@code hud.sounds}.
 */
final class ModeSounds {

    /** A mode became active. */
    static void modeOn() { play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.35F, 0.55F); }

    /** Left building mode. */
    static void modeOff() { play(SoundEvents.UI_BUTTON_CLICK, 0.75F, 0.25F); }

    /** First corner / point placed. */
    static void anchor() { play(SoundEvents.NOTE_BLOCK_HAT, 1.6F, 0.35F); }

    /** The selection is complete (second corner, destination). */
    static void selected() { play(SoundEvents.NOTE_BLOCK_PLING, 1.9F, 0.3F); }

    /** Sent to the server. */
    static void apply() { play(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.1F, 0.6F); }

    /** The server finished an operation that changed something. */
    static void done() { play(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.6F, 0.35F); }

    /** A selection was cleared. */
    static void cancel() { play(SoundEvents.UI_BUTTON_CLICK, 0.6F, 0.2F); }

    /** A resize / nudge / parameter step. */
    static void step(final float pitch) { play(SoundEvents.UI_BUTTON_CLICK, pitch, 0.12F); }

    /** Something was refused (locked mode, nothing to apply, too large). */
    static void refused() { play(SoundEvents.NOTE_BLOCK_BASS, 0.7F, 0.45F); }

    /** Undo / redo sent. */
    static void history() { play(SoundEvents.BOOK_PAGE_TURN, 1.2F, 0.6F); }

    private static void play(final Holder<SoundEvent> sound, final float pitch, final float volume) {
        play(sound.value(), pitch, volume);
    }

    private static void play(final SoundEvent sound, final float pitch, final float volume) {
        final HudSettings hud = SlateBuilding.config().hud;
        if (hud != null && !hud.sounds) return;
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private ModeSounds() {}
}
