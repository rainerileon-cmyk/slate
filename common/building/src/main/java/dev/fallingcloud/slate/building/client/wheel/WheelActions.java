package dev.fallingcloud.slate.building.client.wheel;

import dev.fallingcloud.slate.building.net.ChiselHeld;
import dev.fallingcloud.slate.building.net.ChiselTarget;
import dev.fallingcloud.slate.building.net.ReshapeTarget;
import dev.fallingcloud.slate.building.net.SwapHeld;
import dev.fallingcloud.slate.core.net.SlateNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Applies a wheel choice: sends {@link SwapHeld} / {@link ChiselHeld} for the held stack or {@link ReshapeTarget} /
 * {@link ChiselTarget} for a block in the world (the server validates and pays, design §1), and plays the UI
 * feedback sounds ({@code hud.sounds}).
 */
public final class WheelActions {

    /** Sends the request for {@code slice} on {@code target}; false when nothing would change or it is locked. */
    public static boolean apply(final WheelTarget target, final WheelSlice slice) {
        if (!slice.actionable()) return false;
        final SlateNetwork net = SlateNetwork.get();
        if (target.held()) {
            if (slice.kind() == WheelSlice.Kind.SHAPE) net.sendToServer(new SwapHeld(target.slot(), slice.shape().id()));
            else net.sendToServer(new ChiselHeld(target.slot(), WheelPages.id(slice.block())));
        } else if (target.pos() != null) {
            if (slice.kind() == WheelSlice.Kind.SHAPE) net.sendToServer(new ReshapeTarget(target.pos(), slice.shape().id()));
            else net.sendToServer(new ChiselTarget(target.pos(), WheelPages.id(slice.block())));
        } else {
            return false;
        }
        applied(slice);
        return true;
    }

    /** Feedback for an applied choice: the material's own placing sound, quiet and a little higher. */
    private static void applied(final WheelSlice slice) {
        if (!WheelConfig.hud().sounds) return;
        final SoundEvent sound = slice.block().defaultBlockState().getSoundType().getPlaceSound();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.25f, 0.45f));
    }

    /** A soft tick when the hovered slice or the page changes. */
    public static void tick(final float pitch) {
        if (!WheelConfig.hud().sounds) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.12f));
    }

    /** A dull click for something that cannot be done (locked slice). */
    public static void deny() {
        if (!WheelConfig.hud().sounds) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f, 0.35f));
    }

    private WheelActions() {}
}
