package dev.fallingcloud.slate.core.mixin;

import dev.fallingcloud.slate.core.gfx.Anim;
import dev.fallingcloud.slate.core.gfx.Ease;
import dev.fallingcloud.slate.core.screen.reskin.ReskinState;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Gives every vanilla widget the two eased state values the reskin animates (hover lift, press). Lazily
 * created so widgets on screens outside the reskin scope pay nothing.
 */
@Mixin(AbstractWidget.class)
public abstract class AbstractWidgetMixin implements ReskinState {

    @Unique private Anim slate$hover;
    @Unique private Anim slate$press;

    @Override
    public Anim slate$hoverAnim() {
        if (slate$hover == null) slate$hover = new Anim(0, 140, Ease.OUT_CUBIC);
        return slate$hover;
    }

    @Override
    public Anim slate$pressAnim() {
        if (slate$press == null) slate$press = new Anim(0, 90, Ease.OUT_CUBIC);
        return slate$press;
    }
}
