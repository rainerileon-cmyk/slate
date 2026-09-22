package dev.fallingcloud.slate.core.widget;

import dev.fallingcloud.slate.core.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvents;

/** UI feedback sounds. The dark skin uses a slightly lower, softer click than vanilla. */
public final class SlateSounds {

    public static void click(final SoundManager sounds) {
        if (!Theme.current().uiSounds()) return;
        final boolean vanilla = Theme.current().isVanilla();
        sounds.play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), vanilla ? 1.0f : 0.85f, vanilla ? 1f : 0.6f));
    }

    public static void click() {
        click(Minecraft.getInstance().getSoundManager());
    }

    /** A quieter tick for toggles, tabs and list selection. */
    public static void tick() {
        if (!Theme.current().uiSounds()) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.25f, 0.3f));
    }

    /** Notification chime (toasts, messages). */
    public static void chime() {
        if (!Theme.current().uiSounds()) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4f, 0.5f));
    }

    private SlateSounds() {}
}
